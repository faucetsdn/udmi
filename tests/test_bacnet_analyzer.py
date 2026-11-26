"""Comprehensive synthetic-PCAP unit tests for udmi.common.bacnet_analyzer."""

import json
from pathlib import Path
import socket
import struct
import subprocess
import sys
import tempfile
import unittest

ROOT_DIR = Path(__file__).resolve().parent.parent
COMMON_PY = ROOT_DIR / "common" / "src" / "main" / "python"
if str(COMMON_PY) not in sys.path:
  sys.path.insert(0, str(COMMON_PY))

from udmi.common.bacnet_analyzer import analyze_pcap, decode_pcap
from udmi.common.bacnet_analyzer.codec import (
    BVLC_FORWARDED_NPDU,
    BVLC_ORIGINAL_BROADCAST,
    BVLC_ORIGINAL_UNICAST,
    BVLC_RESULT,
    LINKTYPE_ETHERNET,
    LINKTYPE_LINUX_SLL2,
    NET_I_AM_ROUTER,
    NET_NETWORK_NUMBER_IS,
    NET_REJECT_MESSAGE,
    NET_ROUTER_BUSY,
    PDU_CONFIRMED_REQ,
    PDU_ERROR,
    PDU_SIMPLE_ACK,
    PDU_UNCONFIRMED_REQ,
    SVC_READ_PROPERTY,
    UNCONF_COV,
    UNCONF_I_AM,
    UNCONF_TIME_SYNC,
    UNCONF_WHO_IS,
    SegmentReassembler,
)


def _ipv4_udp_frame(
    src_ip: str,
    dst_ip: str,
    src_port: int,
    dst_port: int,
    payload: bytes,
    ip_id: int = 1,
    linktype: int = LINKTYPE_ETHERNET,
    vlan_id: int | None = None,
) -> bytes:
  udp_len = 8 + len(payload)
  udp = struct.pack("!HHHH", src_port, dst_port, udp_len, 0) + payload
  ip_len = 20 + len(udp)
  ip_hdr = struct.pack(
      "!BBHHHBBH4s4s",
      0x45,
      0,
      ip_len,
      ip_id,
      0,
      64,
      17,
      0,
      socket.inet_aton(src_ip),
      socket.inet_aton(dst_ip),
  )
  if linktype == LINKTYPE_LINUX_SLL2:
    return struct.pack("!H18s", 0x0800, b"\x00" * 18) + ip_hdr + udp
  eth = b"\x00\x11\x22\x33\x44\x55\x66\x77\x88\x99\xaa\xbb"
  if vlan_id is not None:
    eth += struct.pack("!HHH", 0x8100, vlan_id, 0x0800)
  else:
    eth += struct.pack("!H", 0x0800)
  return eth + ip_hdr + udp


def _bvlc(fn: int, body: bytes) -> bytes:
  return struct.pack("!BBH", 0x81, fn, 4 + len(body)) + body


def _npdu(
    apdu: bytes = b"",
    dnet: int | None = None,
    dadr: bytes = b"",
    snet: int | None = None,
    sadr: bytes = b"",
    net_msg_type: int | None = None,
    net_msg_body: bytes = b"",
) -> bytes:
  ctrl = 0
  if net_msg_type is not None:
    ctrl |= 0x80
  if dnet is not None:
    ctrl |= 0x20
  if snet is not None:
    ctrl |= 0x08
  out = bytearray([0x01, ctrl])
  if dnet is not None:
    out += struct.pack("!HB", dnet, len(dadr)) + dadr
  if snet is not None:
    out += struct.pack("!HB", snet, len(sadr)) + sadr
  if dnet is not None:
    out.append(255)
  if net_msg_type is not None:
    out.append(net_msg_type)
    out += net_msg_body
  else:
    out += apdu
  return bytes(out)


def _i_am_apdu(device_inst: int) -> bytes:
  obj_id = (8 << 22) | (device_inst & 0x3FFFFF)
  # Application tag 12 (Object Identifier, len 4)
  return bytes([0x10, UNCONF_I_AM, 0xC4]) + struct.pack("!I", obj_id)


def _who_is_apdu(low: int | None = None, high: int | None = None) -> bytes:
  if low is None:
    return bytes([0x10, UNCONF_WHO_IS])
  low_b = low.to_bytes(max(1, (low.bit_length() + 7) // 8), "big")
  high_b = high.to_bytes(max(1, (high.bit_length() + 7) // 8), "big")
  return (
      bytes([0x10, UNCONF_WHO_IS, 0x08 | len(low_b)])
      + low_b
      + bytes([0x18 | len(high_b)])
      + high_b
  )


def _read_prop_req(invoke_id: int, obj_type: int, obj_inst: int) -> bytes:
  obj_id = ((obj_type & 0x3FF) << 22) | (obj_inst & 0x3FFFFF)
  return bytes([0x00, 0x05, invoke_id, SVC_READ_PROPERTY, 0x0C]) + struct.pack(
      "!I", obj_id
  )


def _simple_ack(invoke_id: int, service: int = SVC_READ_PROPERTY) -> bytes:
  return bytes([0x20, invoke_id, service])


def _error_apdu(
    invoke_id: int, service: int, err_class: int, err_code: int
) -> bytes:
  return bytes([0x50, invoke_id, service, 0x91, err_class, 0x91, err_code])


def _write_pcap(
    path: Path,
    packets: list[tuple[float, bytes]],
    linktype: int = LINKTYPE_ETHERNET,
) -> None:
  buf = bytearray(
      struct.pack("<IHHiIII", 0xA1B2C3D4, 2, 4, 0, 0, 65535, linktype)
  )
  for ts, raw in packets:
    sec = int(ts)
    usec = int(round((ts - sec) * 1_000_000))
    buf += struct.pack("<IIII", sec, usec, len(raw), len(raw))
    buf += raw
  path.write_bytes(bytes(buf))


def _write_pcapng(
    path: Path,
    packets: list[tuple[float, bytes]],
    linktype: int = LINKTYPE_ETHERNET,
) -> None:
  def _block(btype: int, body: bytes) -> bytes:
    pad = (-len(body)) % 4
    total = 12 + len(body) + pad
    return (
        struct.pack("<II", btype, total)
        + body
        + (b"\x00" * pad)
        + struct.pack("<I", total)
    )

  out = bytearray()
  out += _block(
      0x0A0D0D0A, struct.pack("<IHHq", 0x1A2B3C4D, 1, 0, -1)
  )
  out += _block(0x00000001, struct.pack("<HHI", linktype, 0, 65535))
  for ts, raw in packets:
    ts_us = int(round(ts * 1_000_000))
    body = (
        struct.pack(
            "<IIIII", 0, (ts_us >> 32) & 0xFFFFFFFF, ts_us & 0xFFFFFFFF,
            len(raw), len(raw)
        )
        + raw
    )
    out += _block(0x00000006, body)
  path.write_bytes(bytes(out))


class BacnetAnalyzerTest(unittest.TestCase):
  """Tests for codec, metrics, findings, segmentation, and CLI."""

  def test_healthy_capture_pcap_and_pcapng_parity(self):
    base = 1700000000.0
    pkts = []
    for i in range(10):
      t = base + i * 10.0
      req = _bvlc(BVLC_ORIGINAL_UNICAST, _npdu(_read_prop_req(i, 0, 1)))
      ack = _bvlc(BVLC_ORIGINAL_UNICAST, _npdu(_simple_ack(i)))
      pkts.append((
          t,
          _ipv4_udp_frame(
              "10.0.0.10", "10.0.0.20", 47808, 47808, req, ip_id=i * 2 + 1
          ),
      ))
      pkts.append((
          t + 0.025,
          _ipv4_udp_frame(
              "10.0.0.20", "10.0.0.10", 47808, 47808, ack, ip_id=i * 2 + 2
          ),
      ))

    with tempfile.TemporaryDirectory() as tmp:
      p_pcap = Path(tmp) / "healthy.pcap"
      p_ng = Path(tmp) / "healthy.pcapng"
      _write_pcap(p_pcap, pkts)
      _write_pcapng(p_ng, pkts)

      res_pcap = analyze_pcap(str(p_pcap))
      res_ng = analyze_pcap(str(p_ng))

      self.assertEqual(res_pcap.metrics.health_score, 100.0)
      self.assertEqual(res_pcap.metrics.health_band, "healthy")
      self.assertEqual(res_pcap.findings, [])
      self.assertEqual(res_pcap.metrics.confirmed_requests, 10)
      self.assertEqual(res_pcap.metrics.completed_transactions, 10)
      self.assertEqual(
          res_pcap.metrics.confirmed_requests,
          res_ng.metrics.confirmed_requests,
      )
      self.assertEqual(
          res_pcap.metrics.completed_transactions,
          res_ng.metrics.completed_transactions,
      )

  def test_vlan_and_linux_sll2_and_forwarded_npdu_decoding(self):
    orig = socket.inet_aton("10.1.2.3") + struct.pack("!H", 47808)
    fwd = _bvlc(BVLC_FORWARDED_NPDU, orig + _npdu(_i_am_apdu(1234)))
    p1 = _ipv4_udp_frame(
        "10.1.2.1", "10.1.2.255", 47808, 47808, fwd, vlan_id=42
    )
    p2 = _ipv4_udp_frame(
        "10.1.2.1",
        "10.1.2.255",
        47808,
        47808,
        fwd,
        linktype=LINKTYPE_LINUX_SLL2,
    )
    with tempfile.TemporaryDirectory() as tmp:
      f1 = Path(tmp) / "vlan.pcap"
      f2 = Path(tmp) / "sll2.pcap"
      _write_pcap(f1, [(1700000000.0, p1)], linktype=LINKTYPE_ETHERNET)
      _write_pcap(f2, [(1700000000.0, p2)], linktype=LINKTYPE_LINUX_SLL2)
      frames1, _, _ = decode_pcap(str(f1))
      frames2, _, _ = decode_pcap(str(f2))
      self.assertEqual(len(frames1), 1)
      self.assertEqual(frames1[0].src_endpoint, "10.1.2.3:47808")
      self.assertEqual(frames1[0].device_instance, 1234)
      self.assertEqual(len(frames2), 1)
      self.assertEqual(frames2[0].device_instance, 1234)

  def test_detects_duplicate_device_id_and_slow_routed_trunk(self):
    base = 1700000000.0
    pkts = [
        # Duplicate device instance 2600008 from two different IPs
        (
            base + 1.0,
            _ipv4_udp_frame(
                "10.0.0.10",
                "10.0.0.255",
                47808,
                47808,
                _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(_i_am_apdu(2600008))),
                ip_id=1,
            ),
        ),
        (
            base + 2.0,
            _ipv4_udp_frame(
                "10.0.0.11",
                "10.0.0.255",
                47808,
                47808,
                _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(_i_am_apdu(2600008))),
                ip_id=2,
            ),
        ),
    ]
    # Slow routed trunk behind router 10.0.0.6:47808 (1.5s RTT)
    for idx in range(3):
      t = base + 10.0 + idx * 5.0
      req = _bvlc(
          BVLC_ORIGINAL_UNICAST,
          _npdu(_read_prop_req(idx + 1, 0, 1), dnet=26006, dadr=bytes([40])),
      )
      ack = _bvlc(
          BVLC_ORIGINAL_UNICAST,
          _npdu(_simple_ack(idx + 1), snet=26006, sadr=bytes([40])),
      )
      pkts.append((
          t,
          _ipv4_udp_frame(
              "10.0.0.1", "10.0.0.6", 47808, 47808, req, ip_id=10 + idx * 2
          ),
      ))
      pkts.append((
          t + 1.5,
          _ipv4_udp_frame(
              "10.0.0.6", "10.0.0.1", 47808, 47808, ack, ip_id=11 + idx * 2
          ),
      ))

    with tempfile.TemporaryDirectory() as tmp:
      pcap = Path(tmp) / "issues.pcap"
      _write_pcap(pcap, pkts)
      res = analyze_pcap(str(pcap))
      causes = {f.cause_key: f for f in res.findings}
      self.assertIn("duplicate_device_instance:2600008", causes)
      self.assertEqual(
          causes["duplicate_device_instance:2600008"].severity, "CRITICAL"
      )
      self.assertEqual(
          causes["duplicate_device_instance:2600008"].frame_numbers, [1, 2]
      )
      self.assertIn("slow_routed_trunk:10.0.0.6:47808", causes)
      self.assertEqual(
          causes["slow_routed_trunk:10.0.0.6:47808"].severity, "HIGH"
      )
      self.assertLess(res.metrics.health_score, 85.0)

  def test_detects_retries_errors_broadcast_cov_bbmd_router_and_timesync(self):
    base = 1700000000.0
    pkts = []
    ip_id = 1

    # Retries + APDU Error (unknown-object)
    for attempt in range(3):
      req = _bvlc(BVLC_ORIGINAL_UNICAST, _npdu(_read_prop_req(10, 0, 99)))
      pkts.append((
          base + attempt * 0.5,
          _ipv4_udp_frame("10.0.0.1", "10.0.0.2", 47808, 47808, req, ip_id),
      ))
      ip_id += 1
    for err_i in range(2):
      req = _bvlc(
          BVLC_ORIGINAL_UNICAST, _npdu(_read_prop_req(20 + err_i, 0, 99))
      )
      err = _bvlc(
          BVLC_ORIGINAL_UNICAST,
          _npdu(_error_apdu(20 + err_i, SVC_READ_PROPERTY, 1, 31)),
      )
      pkts.append((
          base + 5.0 + err_i,
          _ipv4_udp_frame("10.0.0.1", "10.0.0.2", 47808, 47808, req, ip_id),
      ))
      ip_id += 1
      pkts.append((
          base + 5.1 + err_i,
          _ipv4_udp_frame("10.0.0.2", "10.0.0.1", 47808, 47808, err, ip_id),
      ))
      ip_id += 1

    # Broadcast COV storm (6 frames)
    cov_apdu = bytes([0x10, UNCONF_COV])
    for i in range(6):
      cov = _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(cov_apdu))
      pkts.append((
          base + 10.0 + i * 0.1,
          _ipv4_udp_frame("10.0.0.3", "10.0.0.255", 47808, 47808, cov, ip_id),
      ))
      ip_id += 1

    # Unbounded Who-Is (5 frames)
    for i in range(5):
      wi = _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(_who_is_apdu()))
      pkts.append((
          base + 12.0 + i * 0.2,
          _ipv4_udp_frame("10.0.0.4", "10.0.0.255", 47808, 47808, wi, ip_id),
      ))
      ip_id += 1

    # BBMD BVLC-Result NAK (code 0x0030)
    nak = _bvlc(BVLC_RESULT, struct.pack("!H", 0x0030))
    pkts.append((
        base + 15.0,
        _ipv4_udp_frame("10.0.0.5", "10.0.0.1", 47808, 47808, nak, ip_id),
    ))
    ip_id += 1

    # Router Reject, Busy, and Network-Number-Is disagreement
    rej = _bvlc(
        BVLC_ORIGINAL_UNICAST,
        _npdu(net_msg_type=NET_REJECT_MESSAGE, net_msg_body=struct.pack("!BH", 1, 500)),
    )
    busy = _bvlc(
        BVLC_ORIGINAL_BROADCAST,
        _npdu(net_msg_type=NET_ROUTER_BUSY, net_msg_body=struct.pack("!H", 500)),
    )
    nni1 = _bvlc(
        BVLC_ORIGINAL_BROADCAST,
        _npdu(net_msg_type=NET_NETWORK_NUMBER_IS, net_msg_body=struct.pack("!HB", 100, 1)),
    )
    nni2 = _bvlc(
        BVLC_ORIGINAL_BROADCAST,
        _npdu(net_msg_type=NET_NETWORK_NUMBER_IS, net_msg_body=struct.pack("!HB", 200, 1)),
    )
    for src, dst, payload in (
        ("10.0.0.6", "10.0.0.1", rej),
        ("10.0.0.6", "10.0.0.255", busy),
        ("10.0.0.6", "10.0.0.255", nni1),
        ("10.0.0.7", "10.0.0.255", nni2),
    ):
      pkts.append((
          base + 20.0,
          _ipv4_udp_frame(src, dst, 47808, 47808, payload, ip_id),
      ))
      ip_id += 1

    # Multiple TimeSynchronization masters
    ts_apdu = bytes([0x10, UNCONF_TIME_SYNC])
    for src in ("10.0.0.8", "10.0.0.9"):
      pkts.append((
          base + 25.0,
          _ipv4_udp_frame(
              src,
              "10.0.0.255",
              47808,
              47808,
              _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(ts_apdu)),
              ip_id,
          ),
      ))
      ip_id += 1

    with tempfile.TemporaryDirectory() as tmp:
      pcap = Path(tmp) / "multi.pcap"
      _write_pcap(pcap, pkts)
      res = analyze_pcap(str(pcap))
      causes = {f.cause_key for f in res.findings}
      self.assertIn("confirmed_retries:10.0.0.2:47808", causes)
      self.assertIn(
          "apdu_error:10.0.0.2:47808|ReadProperty|object:unknown-object", causes
      )
      self.assertIn("excessive_broadcast_or_cov", causes)
      self.assertIn("excessive_discovery_who_is", causes)
      self.assertIn("bbmd_bvlc_nak", causes)
      self.assertIn("router_reject:10.0.0.6:47808", causes)
      self.assertIn("router_busy:10.0.0.6:47808", causes)
      self.assertIn("router_network_number_disagreement", causes)
      self.assertIn("multiple_time_sync_masters", causes)

  def test_segmented_apdu_reassembly_complete_and_incomplete(self):
    # Complete 2-segment Complex-ACK (pdu_type=3, seg=1, more=1 then more=0)
    seg0 = _bvlc(
        BVLC_ORIGINAL_UNICAST,
        _npdu(bytes([0x3C, 7, 0, 4, SVC_READ_PROPERTY]) + b"HELLO "),
    )
    seg1 = _bvlc(
        BVLC_ORIGINAL_UNICAST,
        _npdu(bytes([0x38, 7, 1, 4, SVC_READ_PROPERTY]) + b"WORLD"),
    )
    # Incomplete 2-segment Complex-ACK (seq 0 only, never finished)
    inc0 = _bvlc(
        BVLC_ORIGINAL_UNICAST,
        _npdu(bytes([0x3C, 8, 0, 4, SVC_READ_PROPERTY]) + b"PARTIAL"),
    )
    pkts = [
        (1700000000.0, _ipv4_udp_frame("10.0.0.2", "10.0.0.1", 47808, 47808, seg0, 1)),
        (1700000000.1, _ipv4_udp_frame("10.0.0.2", "10.0.0.1", 47808, 47808, seg1, 2)),
        (1700000001.0, _ipv4_udp_frame("10.0.0.2", "10.0.0.1", 47808, 47808, inc0, 3)),
    ]
    with tempfile.TemporaryDirectory() as tmp:
      pcap = Path(tmp) / "seg.pcap"
      _write_pcap(pcap, pkts)
      frames, _, _ = decode_pcap(str(pcap))
      reasm = SegmentReassembler()
      out0 = reasm.feed(frames[0])
      out1 = reasm.feed(frames[1])
      self.assertIsNone(out0)
      self.assertEqual(out1, b"HELLO WORLD")

      res = analyze_pcap(str(pcap))
      self.assertEqual(res.metrics.segmented_reassembled, 1)
      self.assertEqual(res.metrics.segmented_incomplete, 1)
      self.assertIn(
          "incomplete_segmented_apdu", {f.cause_key for f in res.findings}
      )

  def test_cli_bin_analyze_pcap(self):
    pkt = _ipv4_udp_frame(
        "10.0.0.1",
        "10.0.0.255",
        47808,
        47808,
        _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(_i_am_apdu(555))),
        ip_id=1,
    )
    with tempfile.TemporaryDirectory() as tmp:
      pcap = Path(tmp) / "cli.pcap"
      out_json = Path(tmp) / "analysis.json"
      _write_pcap(pcap, [(1700000000.0, pkt)])
      proc = subprocess.run(
          [str(ROOT_DIR / "bin/analyze_pcap"), str(pcap), "-o", str(out_json)],
          capture_output=True,
          text=True,
          check=False,
      )
      self.assertEqual(proc.returncode, 0, proc.stderr)
      parsed = json.loads(out_json.read_text(encoding="utf-8"))
      self.assertEqual(parsed["schema_version"], "1.0")
      self.assertEqual(parsed["metrics"]["capture"]["bacnet_frames"], 1)
      self.assertIn("555", parsed["metrics"]["device_instance_map"])
      self.assertIn("verdict", parsed)
      self.assertIn("prioritized_actions", parsed)

  def test_report_markdown_and_bundle_generation(self):
    base = 1700000000.0
    pkts = [
        (
            base + 1.0,
            _ipv4_udp_frame(
                "10.0.0.10",
                "10.0.0.255",
                47808,
                47808,
                _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(_i_am_apdu(9001))),
                ip_id=1,
            ),
        ),
        (
            base + 2.0,
            _ipv4_udp_frame(
                "10.0.0.11",
                "10.0.0.255",
                47808,
                47808,
                _bvlc(BVLC_ORIGINAL_BROADCAST, _npdu(_i_am_apdu(9001))),
                ip_id=2,
            ),
        ),
    ]
    with tempfile.TemporaryDirectory() as tmp:
      pcap = Path(tmp) / "dup.pcap"
      rep_dir = Path(tmp) / "bundle"
      _write_pcap(pcap, pkts)
      proc = subprocess.run(
          [
              str(ROOT_DIR / "bin/analyze_pcap"),
              str(pcap),
              "--report-dir",
              str(rep_dir),
              "--capture-id",
              "site-a-test",
          ],
          capture_output=True,
          text=True,
          check=False,
      )
      self.assertEqual(proc.returncode, 0, proc.stderr)
      analysis = json.loads((rep_dir / "analysis.json").read_text(encoding="utf-8"))
      md = (rep_dir / "report.md").read_text(encoding="utf-8")
      self.assertEqual(analysis["capture_id"], "site-a-test")
      self.assertEqual(analysis["verdict"]["finding_counts"]["CRITICAL"], 1)
      self.assertIn("## Prioritized Actions to Fix", md)
      self.assertIn("Duplicate BACnet device instance 9001", md)
      self.assertIn("Frames: 1, 2", md)
      self.assertIn("### Capture Recommendations", md)


if __name__ == "__main__":
  unittest.main()
