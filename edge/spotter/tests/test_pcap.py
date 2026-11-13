#!/usr/bin/env python3
"""Unit tests for tcpdump packet capture generator and buffer draining."""

import subprocess
import time
import unittest
from unittest.mock import MagicMock, patch

from edge.spotter.src.pcap import capture_packets

_RealPopen = subprocess.Popen


class TestPcapCapture(unittest.TestCase):
  """Tests verifying pcap capture streaming, timeouts, and resource cleanup."""

  @patch("subprocess.Popen")
  def test_capture_packets_success(self, mock_popen):
    """Verifies standard packet chunk capture and clean EOF completion."""
    mock_proc = MagicMock()
    mock_popen.return_value = mock_proc
    mock_proc.poll.return_value = 0

    mock_proc.stdout.read.side_effect = [b"HEADER", b"DATA", b""]
    mock_proc.stderr.read.return_value = b"8 packets captured"

    generator = capture_packets(
        interface="eth0",
        filter_str="udp port 47808",
        max_duration_sec=60,
        max_bytes=100000,
    )

    chunks = list(generator)
    self.assertEqual(chunks, [b"HEADER", b"DATA"])

    mock_popen.assert_called_once_with(
        ["tcpdump", "-i", "eth0", "-w", "-", "-U", "udp port 47808"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0,
    )

    self.assertTrue(mock_proc.stderr.read.called)

  @patch("time.time")
  @patch("subprocess.Popen")
  def test_capture_packets_duration_limit(self, mock_popen, mock_time):
    """Verifies bounded execution when duration exceeds max_duration_sec."""
    mock_proc = MagicMock()
    mock_popen.return_value = mock_proc
    mock_proc.poll.return_value = None
    mock_proc.stdout.read.return_value = b"DATA_CHUNK"
    mock_proc.stderr.read.return_value = b""

    # Simulate start time 0, then jump past max_duration_sec after 1st iter
    mock_time.side_effect = [0.0, 5.0, 65.0]

    generator = capture_packets(
        interface="any", filter_str="", max_duration_sec=60, max_bytes=1000000
    )

    chunks = list(generator)
    self.assertEqual(len(chunks), 1)

    mock_proc.terminate.assert_called_once()
    mock_proc.wait.assert_called_once()

  @patch("subprocess.Popen")
  def test_capture_packets_size_limit_exceeded(self, mock_popen):
    """Verifies termination when captured byte count exceeds max_bytes."""
    mock_proc = MagicMock()
    mock_popen.return_value = mock_proc
    mock_proc.poll.return_value = None
    mock_proc.stdout.read.return_value = b"B" * 500
    mock_proc.stderr.read.return_value = b""

    generator = capture_packets(
        interface="lo",
        filter_str="port 80",
        max_duration_sec=60,
        max_bytes=400,  # Less than one 500-byte chunk
    )

    chunks = list(generator)
    self.assertEqual(len(chunks), 1)
    self.assertEqual(len(chunks[0]), 500)

    mock_proc.terminate.assert_called_once()
    mock_proc.wait.assert_called_once()

  @patch("subprocess.Popen")
  def test_capture_packets_kill_on_timeout_expired(self, mock_popen):
    """Verifies SIGKILL escalation when SIGTERM fails to stop process."""
    mock_proc = MagicMock()
    mock_popen.return_value = mock_proc
    mock_proc.poll.return_value = None
    mock_proc.stdout.read.return_value = b""

    mock_proc.poll.side_effect = None
    mock_proc.poll.return_value = None
    mock_proc.stdout.read.side_effect = [b"A" * 10, b""]
    mock_proc.wait.side_effect = [
        subprocess.TimeoutExpired(cmd="tcpdump", timeout=3),
        None,
    ]
    mock_proc.stderr.read.return_value = b""

    generator = capture_packets(
        interface="any", filter_str="", max_duration_sec=1, max_bytes=100
    )

    with patch("time.time", side_effect=[0.0, 2.0, 3.0]):
      list(generator)

    mock_proc.terminate.assert_called_once()
    mock_proc.kill.assert_called_once()
    self.assertEqual(mock_proc.wait.call_count, 2)

  def test_capture_packets_idle_timeout_nonblocking(self):
    """Verifies capture generator timeout on completely idle interfaces."""
    # pylint: disable=consider-using-with
    with patch(
        "subprocess.Popen",
        side_effect=lambda *args, **kwargs: _RealPopen(
            ["python3", "-c", "import time; time.sleep(10)"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
        ),
    ):
      t0 = time.time()
      gen = capture_packets(
          interface="any", filter_str="", max_duration_sec=1, max_bytes=1000
      )
      chunks = list(gen)
      elapsed = time.time() - t0
      self.assertEqual(chunks, [])
      self.assertLess(
          elapsed, 2.5, "Idle capture did not terminate within limit"
      )

  def test_capture_packets_drains_large_stderr_no_deadlock(self):
    """Verifies that large stderr output does not block stdout pipe."""
    # pylint: disable=consider-using-with
    script = (
        "import sys, time; "
        "sys.stderr.write('E' * 131072); sys.stderr.flush(); "
        "sys.stdout.write('PACKET_DATA'); sys.stdout.flush()"
    )
    with patch(
        "subprocess.Popen",
        side_effect=lambda *args, **kwargs: _RealPopen(
            ["python3", "-c", script],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
        ),
    ):
      gen = capture_packets(
          interface="any", filter_str="", max_duration_sec=5, max_bytes=1000
      )
      chunks = list(gen)
      self.assertEqual(chunks, [b"PACKET_DATA"])

  @patch("edge.spotter.src.pcap.check_safety_circuit_breaker")
  @patch("subprocess.Popen")
  def test_capture_packets_circuit_breaker_throttles(
      self, mock_popen, mock_cb
  ):
    """Verifies circuit breaker blocks capture launch before spawning."""
    mock_cb.return_value = True
    gen = capture_packets(
        interface="any", filter_str="", max_duration_sec=5, max_bytes=1000
    )
    chunks = list(gen)
    self.assertEqual(chunks, [])
    mock_popen.assert_not_called()

  def test_build_whois_and_router_bvll_frames(self):
    """Verifies binary encoding of BACnet BVLL Who-Is-Router-To-Network and Who-Is frames."""
    from edge.spotter.src.pcap import (
        GLOBAL_BACNET_DNET,
        build_whois_bvll,
        build_whois_router_to_network_bvll,
    )

    router_pdu = build_whois_router_to_network_bvll()
    self.assertEqual(router_pdu, b"\x81\x0b\x00\x07\x01\x80\x00")

    local_whois = build_whois_bvll(dnet=None)
    self.assertEqual(local_whois, b"\x81\x0b\x00\x08\x01\x00\x10\x08")

    dnet_whois = build_whois_bvll(dnet=6100)
    self.assertEqual(
        dnet_whois,
        b"\x81\x0b\x00\x0c\x01\x20" + (6100).to_bytes(2, "big") + b"\x00\xff\x10\x08",
    )

    global_whois = build_whois_bvll(dnet=GLOBAL_BACNET_DNET)
    self.assertEqual(
        global_whois, b"\x81\x0b\x00\x0c\x01\x20\xff\xff\x00\xff\x10\x08"
    )

  @patch("socket.socket")
  def test_send_raw_bvll_uses_raw_socket_without_binding_47808(
      self, mock_socket_cls
  ):
    """Verifies send_raw_bvll sends UDP source port 47808 via SOCK_RAW without binding."""
    import socket as py_socket
    import struct
    from edge.spotter.src.pcap import (
        BACNET_UDP_PORT,
        build_whois_bvll,
        send_raw_bvll,
    )

    mock_raw_sock = MagicMock()
    mock_socket_cls.return_value.__enter__.return_value = mock_raw_sock

    pdu = build_whois_bvll(dnet=None)
    ok = send_raw_bvll(
        pdu=pdu,
        dst_ip="255.255.255.255",
        src_ip="192.168.1.50",
        src_port=BACNET_UDP_PORT,
        interface="eth0",
    )
    self.assertTrue(ok)
    mock_socket_cls.assert_called_once_with(
        py_socket.AF_INET, py_socket.SOCK_RAW, py_socket.IPPROTO_UDP
    )
    mock_raw_sock.bind.assert_not_called()
    mock_raw_sock.sendto.assert_called_once()
    udp_frame, addr = mock_raw_sock.sendto.call_args[0]
    self.assertEqual(addr, ("255.255.255.255", 47808))
    sport, dport, ulen, csum = struct.unpack("!HHHH", udp_frame[:8])
    self.assertEqual(sport, 47808)
    self.assertEqual(dport, 47808)
    self.assertEqual(ulen, 8 + len(pdu))
    self.assertNotEqual(csum, 0)
    self.assertEqual(udp_frame[8:], pdu)

  @patch("edge.spotter.src.pcap.send_raw_bvll", return_value=True)
  def test_run_bacnet_trace_whois_rounds_and_routed_extraction(self, mock_send):
    """Verifies 3 paced rounds of raw-socket broadcast/unicast Who-Is and I-Am-Router / SNET extraction."""
    import threading
    from edge.spotter.src.pcap import (
        extract_routed_networks_from_pcap_chunk,
        run_bacnet_trace_whois_rounds,
    )

    # 1. BVLL Original-Broadcast-NPDU carrying I-Am-Router-To-Network for DNETs 6100 and 9288
    iam_router_frame = (
        b"\x81\x0b\x00\x0b\x01\x80\x01"
        + (6100).to_bytes(2, "big")
        + (9288).to_bytes(2, "big")
    )
    # 2. BVLL Forwarded-NPDU (0x04) from router 192.168.50.12 with NPDU SNET=7700, SLEN=1, SADR=5
    routed_snet_frame = (
        b"\x81\x04\x00\x16"
        + bytes([192, 168, 50, 12])
        + (47808).to_bytes(2, "big")
        + b"\x01\x08"
        + (7700).to_bytes(2, "big")
        + b"\x01\x05"
        + b"\x00\x05\x01\x0c\x0c"
    )
    routers = set()
    routed = extract_routed_networks_from_pcap_chunk(
        b"PCAP_HDR" + iam_router_frame + routed_snet_frame + b"TRAILING",
        discovered_routers=routers,
    )
    self.assertEqual(routed, {6100, 9288, 7700})
    self.assertEqual(routers, {"192.168.50.12"})

    timestamps = []
    stop_evt = threading.Event()
    run_bacnet_trace_whois_rounds(
        discovered_routed_networks={6100, 9288},
        scan_round_timestamps=timestamps,
        stop_event=stop_evt,
        max_duration_sec=1,
        interface="eth0",
        src_ip="192.168.1.50",
        rounds=3,
        discovered_routers=routers,
    )
    self.assertEqual(len(timestamps), 3)
    # Per round (with 1 router and 2 DNETs):
    # - Who-Is-Router: 1 bcast + 1 unicast = 2
    # - Local Who-Is: 1 bcast + 1 unicast = 2
    # - 2 DNETs: 2 * (1 bcast + 1 unicast) = 4
    # - Global Who-Is: 1 bcast + 1 unicast = 2
    # Total per round = 10 * 3 rounds = 30
    self.assertEqual(mock_send.call_count, 30)

  @patch("subprocess.Popen")
  def test_capture_packets_raises_on_tcpdump_nonzero_exit(self, mock_popen):
    """Verifies capture_packets raises RuntimeError if tcpdump exits with an error."""
    mock_proc = MagicMock()
    mock_proc.stdout.fileno.side_effect = AttributeError("mock pipe")
    mock_proc.stdout.read.return_value = b""
    mock_proc.stderr.read.return_value = b"tcpdump: syntax error in filter expression"
    mock_proc.poll.return_value = 1
    mock_proc.returncode = 1
    mock_popen.return_value = mock_proc

    with self.assertRaises(RuntimeError) as ctx:
      list(
          capture_packets(
              interface="eth0",
              filter_str="invalid_bpf_syntax",
              max_duration_sec=5,
              max_bytes=10000,
          )
      )
    self.assertIn("tcpdump failed (exit code 1)", str(ctx.exception))


if __name__ == "__main__":
  unittest.main()


