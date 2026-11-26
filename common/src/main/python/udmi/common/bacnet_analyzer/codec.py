"""Pure-stdlib BACnet/IP PCAP/PCAPNG reader, protocol decoder, and reassembler.

Zero external dependencies: reads classic PCAP (microsecond and nanosecond,
little/big endian) and PCAPNG captures, decodes link/IPv4/UDP/BVLL/NPDU/APDU
headers and key service payloads, and reassembles segmented APDUs.
"""

from dataclasses import dataclass, field
import ipaddress
import socket
import struct
from typing import Dict, Iterator, List, Optional, Tuple

# Link-layer header types (pcap/pcapng)
LINKTYPE_ETHERNET = 1
LINKTYPE_RAW = 101
LINKTYPE_LINUX_SLL = 113
LINKTYPE_LINUX_SLL2 = 276

LINKTYPE_NAMES = {
    LINKTYPE_ETHERNET: "LINKTYPE_ETHERNET",
    LINKTYPE_RAW: "LINKTYPE_RAW",
    LINKTYPE_LINUX_SLL: "LINKTYPE_LINUX_SLL",
    LINKTYPE_LINUX_SLL2: "LINKTYPE_LINUX_SLL2",
}

# BVLL function codes (ASHRAE 135 Annex J)
BVLL_TYPE_BACNET_IP = 0x81
BVLC_RESULT = 0x00
BVLC_WRITE_BDT = 0x01
BVLC_READ_BDT = 0x02
BVLC_READ_BDT_ACK = 0x03
BVLC_FORWARDED_NPDU = 0x04
BVLC_REGISTER_FOREIGN_DEVICE = 0x05
BVLC_READ_FDT = 0x06
BVLC_READ_FDT_ACK = 0x07
BVLC_DELETE_FDT_ENTRY = 0x08
BVLC_DISTRIBUTE_BROADCAST = 0x09
BVLC_ORIGINAL_UNICAST = 0x0A
BVLC_ORIGINAL_BROADCAST = 0x0B

BVLC_NAMES = {
    BVLC_RESULT: "BVLC-Result",
    BVLC_WRITE_BDT: "Write-BDT",
    BVLC_READ_BDT: "Read-BDT",
    BVLC_READ_BDT_ACK: "Read-BDT-Ack",
    BVLC_FORWARDED_NPDU: "Forwarded-NPDU",
    BVLC_REGISTER_FOREIGN_DEVICE: "Register-Foreign-Device",
    BVLC_READ_FDT: "Read-FDT",
    BVLC_READ_FDT_ACK: "Read-FDT-Ack",
    BVLC_DELETE_FDT_ENTRY: "Delete-FDT-Entry",
    BVLC_DISTRIBUTE_BROADCAST: "Distribute-Broadcast-To-Network",
    BVLC_ORIGINAL_UNICAST: "Original-Unicast-NPDU",
    BVLC_ORIGINAL_BROADCAST: "Original-Broadcast-NPDU",
}

# Network layer message types
NET_WHO_IS_ROUTER = 0x00
NET_I_AM_ROUTER = 0x01
NET_I_COULD_BE_ROUTER = 0x02
NET_REJECT_MESSAGE = 0x03
NET_ROUTER_BUSY = 0x04
NET_ROUTER_AVAILABLE = 0x05
NET_INIT_ROUTING_TABLE = 0x06
NET_INIT_ROUTING_TABLE_ACK = 0x07
NET_WHAT_IS_NETWORK_NUMBER = 0x12
NET_NETWORK_NUMBER_IS = 0x13

# APDU PDU types (upper nibble of byte 0)
PDU_CONFIRMED_REQ = 0
PDU_UNCONFIRMED_REQ = 1
PDU_SIMPLE_ACK = 2
PDU_COMPLEX_ACK = 3
PDU_SEGMENT_ACK = 4
PDU_ERROR = 5
PDU_REJECT = 6
PDU_ABORT = 7

# Confirmed service choices
SVC_ACK_ALARM = 0
SVC_CONFIRMED_COV = 1
SVC_CONFIRMED_EVENT = 2
SVC_SUBSCRIBE_COV = 5
SVC_ATOMIC_READ_FILE = 6
SVC_ATOMIC_WRITE_FILE = 7
SVC_READ_PROPERTY = 12
SVC_READ_PROPERTY_MULTIPLE = 14
SVC_WRITE_PROPERTY = 15
SVC_WRITE_PROPERTY_MULTIPLE = 16
SVC_DEVICE_COMM_CONTROL = 17
SVC_REINITIALIZE_DEVICE = 20
SVC_SUBSCRIBE_COV_PROPERTY = 28

CONFIRMED_SERVICE_NAMES = {
    0: "AcknowledgeAlarm",
    1: "ConfirmedCOVNotification",
    2: "ConfirmedEventNotification",
    3: "GetAlarmSummary",
    4: "GetEnrollmentSummary",
    5: "SubscribeCOV",
    6: "AtomicReadFile",
    7: "AtomicWriteFile",
    12: "ReadProperty",
    14: "ReadPropertyMultiple",
    15: "WriteProperty",
    16: "WritePropertyMultiple",
    17: "DeviceCommunicationControl",
    20: "ReinitializeDevice",
    26: "ReadRange",
    28: "SubscribeCOVProperty",
}

# Unconfirmed service choices
UNCONF_I_AM = 0
UNCONF_I_HAVE = 1
UNCONF_COV = 2
UNCONF_EVENT = 3
UNCONF_PRIVATE_TRANSFER = 4
UNCONF_TEXT_MESSAGE = 5
UNCONF_TIME_SYNC = 6
UNCONF_WHO_HAS = 7
UNCONF_WHO_IS = 8
UNCONF_UTC_TIME_SYNC = 9

UNCONFIRMED_SERVICE_NAMES = {
    0: "I-Am",
    1: "I-Have",
    2: "UnconfirmedCOVNotification",
    3: "UnconfirmedEventNotification",
    4: "UnconfirmedPrivateTransfer",
    5: "UnconfirmedTextMessage",
    6: "TimeSynchronization",
    7: "Who-Has",
    8: "Who-Is",
    9: "UTCTimeSynchronization",
}

ERROR_CLASS_NAMES = {
    0: "device",
    1: "object",
    2: "property",
    3: "resources",
    4: "security",
    5: "services",
    6: "vt",
    7: "communication",
}

ERROR_CODE_NAMES = {
    0: "other",
    2: "configuration-in-progress",
    3: "device-busy",
    9: "inconsistent-parameters",
    12: "invalid-data-type",
    18: "missing-required-parameter",
    25: "operational-problem",
    27: "read-access-denied",
    29: "service-request-denied",
    30: "timeout",
    31: "unknown-object",
    32: "unknown-property",
    37: "value-out-of-range",
    40: "write-access-denied",
    42: "invalid-array-index",
    43: "cov-subscription-failed",
    132: "communication-disabled",
}

OBJECT_TYPE_NAMES = {
    0: "analog-input",
    1: "analog-output",
    2: "analog-value",
    3: "binary-input",
    4: "binary-output",
    5: "binary-value",
    8: "device",
    13: "multi-state-input",
    14: "multi-state-output",
    17: "schedule",
    19: "multi-state-value",
    20: "trend-log",
}

BACNET_PORT_MIN = 47808
BACNET_PORT_MAX = 47823


@dataclass
class RawPacket:
  """Raw packet yielded by the PCAP/PCAPNG reader."""

  frame_no: int
  timestamp: float
  caplen: int
  origlen: int
  linktype: int
  data: bytes


@dataclass
class BacnetFrame:
  """Decoded BACnet/IP frame with link, IP/UDP, BVLL, NPDU, and APDU fields."""

  frame_no: int
  timestamp: float
  caplen: int
  origlen: int
  linktype: int
  src_ip: str
  dst_ip: str
  src_port: int
  dst_port: int
  ip_id: int = 0
  bvlc_function: int = -1
  bvlc_result_code: Optional[int] = None
  orig_ip: Optional[str] = None
  orig_port: Optional[int] = None
  fdt_ttl: Optional[int] = None
  is_network_msg: bool = False
  network_msg_type: Optional[int] = None
  network_numbers: List[int] = field(default_factory=list)
  reject_reason: Optional[int] = None
  dnet: Optional[int] = None
  dadr: Optional[str] = None
  snet: Optional[int] = None
  sadr: Optional[str] = None
  hop_count: Optional[int] = None
  pdu_type: Optional[int] = None
  segmented: bool = False
  more_follows: bool = False
  invoke_id: Optional[int] = None
  seq_num: Optional[int] = None
  service_choice: Optional[int] = None
  error_class: Optional[int] = None
  error_code: Optional[int] = None
  abort_or_reject_reason: Optional[int] = None
  device_instance: Optional[int] = None
  who_is_low: Optional[int] = None
  who_is_high: Optional[int] = None
  object_id: Optional[str] = None
  apdu_payload: bytes = b""
  raw_udp_payload: bytes = b""

  @property
  def src_endpoint(self) -> str:
    """Canonical source device identifier (routed snet/sadr or ip:port)."""
    if self.snet is not None and self.sadr:
      return f"{self.snet}/{self.sadr}"
    if self.orig_ip and self.orig_port:
      return f"{self.orig_ip}:{self.orig_port}"
    return f"{self.src_ip}:{self.src_port}"

  @property
  def dst_endpoint(self) -> str:
    """Canonical destination identifier (routed dnet/dadr or ip:port)."""
    if self.dnet is not None and self.dadr:
      return f"{self.dnet}/{self.dadr}"
    return f"{self.dst_ip}:{self.dst_port}"

  @property
  def ip_src(self) -> str:
    """Direct IP:port transport source."""
    return f"{self.src_ip}:{self.src_port}"

  @property
  def ip_dst(self) -> str:
    """Direct IP:port transport destination."""
    return f"{self.dst_ip}:{self.dst_port}"

  @property
  def is_broadcast(self) -> bool:
    """True if frame is a BACnet/IP broadcast (BVLC or global/remote DNET)."""
    if self.bvlc_function in (
        BVLC_ORIGINAL_BROADCAST,
        BVLC_FORWARDED_NPDU,
        BVLC_DISTRIBUTE_BROADCAST,
    ):
      return True
    if self.dnet == 65535 or (self.dnet is not None and not self.dadr):
      return True
    try:
      addr = ipaddress.IPv4Address(self.dst_ip)
      if addr.is_multicast or self.dst_ip.endswith(".255"):
        return True
    except ValueError:
      pass
    return False

  @property
  def service_name(self) -> str:
    """Human-readable BACnet service name if decoded."""
    if self.service_choice is None:
      return ""
    if self.pdu_type == PDU_UNCONFIRMED_REQ:
      return UNCONFIRMED_SERVICE_NAMES.get(
          self.service_choice, f"unconfirmed-{self.service_choice}"
      )
    return CONFIRMED_SERVICE_NAMES.get(
        self.service_choice, f"confirmed-{self.service_choice}"
    )

  @property
  def error_label(self) -> str:
    """Human-readable error class:code label for Error APDUs."""
    if self.error_class is None or self.error_code is None:
      return ""
    cls_name = ERROR_CLASS_NAMES.get(
        self.error_class, f"class-{self.error_class}"
    )
    code_name = ERROR_CODE_NAMES.get(
        self.error_code, f"code-{self.error_code}"
    )
    return f"{cls_name}:{code_name}"


class SegmentReassembler:
  """Reassembles segmented BACnet Confirmed-Request and Complex-ACK APDUs."""

  def __init__(self, timeout_sec: float = 30.0) -> None:
    self._timeout_sec = timeout_sec
    self._streams: Dict[
        Tuple[str, str, int, int], Dict[str, object]
    ] = {}
    self.reassembled_count: int = 0
    self.incomplete_count: int = 0
    self.duplicate_segment_count: int = 0
    self.incomplete_frames: List[int] = []

  def feed(self, frame: BacnetFrame) -> Optional[bytes]:
    """Feeds a segmented frame; returns reassembled payload when complete."""
    if (
        not frame.segmented
        or frame.invoke_id is None
        or frame.seq_num is None
        or frame.pdu_type not in (PDU_CONFIRMED_REQ, PDU_COMPLEX_ACK)
    ):
      return None

    key = (
        frame.src_endpoint,
        frame.dst_endpoint,
        frame.invoke_id,
        frame.pdu_type,
    )
    stream = self._streams.get(key)
    if stream is None or (
        frame.timestamp - float(stream["last_ts"]) > self._timeout_sec
    ):
      if stream is not None:
        self.incomplete_count += 1
        self.incomplete_frames.append(int(stream["first_frame"]))
      stream = {
          "first_frame": frame.frame_no,
          "last_ts": frame.timestamp,
          "segments": {},
      }
      self._streams[key] = stream

    segments: Dict[int, bytes] = stream["segments"]  # type: ignore[assignment]
    if frame.seq_num in segments:
      self.duplicate_segment_count += 1
    else:
      segments[frame.seq_num] = frame.apdu_payload
    stream["last_ts"] = frame.timestamp

    if not frame.more_follows:
      expected = frame.seq_num + 1
      self._streams.pop(key, None)
      if len(segments) == expected and all(
          i in segments for i in range(expected)
      ):
        self.reassembled_count += 1
        return b"".join(segments[i] for i in range(expected))
      self.incomplete_count += 1
      self.incomplete_frames.append(int(stream["first_frame"]))
    return None

  def flush_incomplete(self) -> None:
    """Marks any remaining open segmented streams as incomplete at EOF."""
    for stream in self._streams.values():
      self.incomplete_count += 1
      self.incomplete_frames.append(int(stream["first_frame"]))
    self._streams.clear()


def _iter_classic_pcap(data: bytes) -> Iterator[RawPacket]:
  """Iterates packets from a classic libpcap byte buffer."""
  magic = data[:4]
  if magic == b"\xd4\xc3\xb2\xa1":
    endian, ts_div = "<", 1_000_000.0
  elif magic == b"\xa1\xb2\xc3\xd4":
    endian, ts_div = ">", 1_000_000.0
  elif magic == b"\x4d\x3c\xb2\xa1":
    endian, ts_div = "<", 1_000_000_000.0
  elif magic == b"\xa1\xb2\x3c\x4d":
    endian, ts_div = ">", 1_000_000_000.0
  else:
    raise ValueError(f"Unsupported PCAP magic: {magic.hex()}")

  if len(data) < 24:
    raise ValueError("Truncated PCAP global header")
  _, _, _, _, _, linktype = struct.unpack(endian + "HHiiii", data[4:24])
  linktype &= 0x0FFFFFFF

  offset = 24
  frame_no = 0
  total_len = len(data)
  while offset + 16 <= total_len:
    ts_sec, ts_sub, caplen, origlen = struct.unpack(
        endian + "IIII", data[offset : offset + 16]
    )
    offset += 16
    if offset + caplen > total_len:
      break
    frame_no += 1
    pkt_bytes = data[offset : offset + caplen]
    offset += caplen
    yield RawPacket(
        frame_no=frame_no,
        timestamp=ts_sec + ts_sub / ts_div,
        caplen=caplen,
        origlen=origlen,
        linktype=linktype,
        data=pkt_bytes,
    )


def _parse_pcapng_idb_tsresol(body: bytes) -> float:
  """Parses if_tsresol option (code 9) from a PCAPNG IDB block body."""
  offset = 8
  while offset + 4 <= len(body):
    opt_code, opt_len = struct.unpack("<HH", body[offset : offset + 4])
    offset += 4
    if opt_code == 0:
      break
    if offset + opt_len > len(body):
      break
    if opt_code == 9 and opt_len >= 1:
      val = body[offset]
      if val & 0x80:
        return float(2 ** (val & 0x7F))
      return float(10 ** (val & 0x7F))
    offset += (opt_len + 3) & ~3
  return 1_000_000.0


def _iter_pcapng(data: bytes) -> Iterator[RawPacket]:
  """Iterates packets from a PCAPNG byte buffer."""
  offset = 0
  total_len = len(data)
  endian = "<"
  interfaces: List[Tuple[int, float]] = []
  frame_no = 0

  while offset + 12 <= total_len:
    block_type = struct.unpack("<I", data[offset : offset + 4])[0]
    if block_type == 0x0A0D0D0A:
      bom = data[offset + 8 : offset + 12]
      endian = "<" if bom == b"\x4d\x3c\x2b\x1a" else ">"
      block_len = struct.unpack(endian + "I", data[offset + 4 : offset + 8])[0]
      if block_len < 28 or offset + block_len > total_len:
        break
      interfaces = []
      offset += block_len
      continue

    block_type, block_len = struct.unpack(
        endian + "II", data[offset : offset + 8]
    )
    if block_len < 12 or offset + block_len > total_len:
      break
    body = data[offset + 8 : offset + block_len - 4]
    offset += block_len

    if block_type == 0x00000001 and len(body) >= 8:
      linktype = struct.unpack(endian + "H", body[0:2])[0]
      ts_div = _parse_pcapng_idb_tsresol(body) if endian == "<" else 1_000_000.0
      interfaces.append((linktype, ts_div))
    elif block_type == 0x00000006 and len(body) >= 20:
      iface_id, ts_hi, ts_lo, caplen, origlen = struct.unpack(
          endian + "IIIII", body[0:20]
      )
      if 20 + caplen > len(body):
        continue
      linktype, ts_div = (
          interfaces[iface_id]
          if iface_id < len(interfaces)
          else (LINKTYPE_ETHERNET, 1_000_000.0)
      )
      frame_no += 1
      ts_raw = (ts_hi << 32) | ts_lo
      yield RawPacket(
          frame_no=frame_no,
          timestamp=ts_raw / ts_div,
          caplen=caplen,
          origlen=origlen,
          linktype=linktype,
          data=body[20 : 20 + caplen],
      )
    elif block_type == 0x00000003 and len(body) >= 4:
      origlen = struct.unpack(endian + "I", body[0:4])[0]
      pkt_bytes = body[4:]
      linktype = interfaces[0][0] if interfaces else LINKTYPE_ETHERNET
      frame_no += 1
      yield RawPacket(
          frame_no=frame_no,
          timestamp=0.0,
          caplen=len(pkt_bytes),
          origlen=origlen,
          linktype=linktype,
          data=pkt_bytes,
      )


def iter_raw_packets(pcap_path: str) -> Iterator[RawPacket]:
  """Reads a PCAP or PCAPNG file and yields RawPacket entries."""
  with open(pcap_path, "rb") as fh:
    data = fh.read()
  if len(data) < 4:
    raise ValueError(f"Empty or truncated capture file: {pcap_path}")
  if data[:4] == b"\x0a\x0d\x0d\x0a":
    yield from _iter_pcapng(data)
  else:
    yield from _iter_classic_pcap(data)


def _extract_ipv4(packet: RawPacket) -> Optional[bytes]:
  """Strips the link-layer header and returns the IPv4 payload if present."""
  buf = packet.data
  if packet.linktype == LINKTYPE_ETHERNET:
    if len(buf) < 14:
      return None
    ethertype = struct.unpack("!H", buf[12:14])[0]
    offset = 14
    while ethertype in (0x8100, 0x88A8) and offset + 4 <= len(buf):
      ethertype = struct.unpack("!H", buf[offset + 2 : offset + 4])[0]
      offset += 4
    return buf[offset:] if ethertype == 0x0800 else None
  if packet.linktype == LINKTYPE_RAW:
    return buf if buf and (buf[0] >> 4) == 4 else None
  if packet.linktype == LINKTYPE_LINUX_SLL:
    if len(buf) < 16:
      return None
    proto = struct.unpack("!H", buf[14:16])[0]
    return buf[16:] if proto == 0x0800 else None
  if packet.linktype == LINKTYPE_LINUX_SLL2:
    if len(buf) < 20:
      return None
    proto = struct.unpack("!H", buf[0:2])[0]
    return buf[20:] if proto == 0x0800 else None
  return None


def _decode_uint(raw: bytes) -> int:
  """Decodes a big-endian unsigned integer of 1..8 bytes."""
  val = 0
  for b in raw:
    val = (val << 8) | b
  return val


def _read_tag(buf: bytes, offset: int) -> Optional[Tuple[int, bool, int, int]]:
  """Reads a BACnet ASN.1 tag -> (tag_num, is_context, length, next_offset)."""
  if offset >= len(buf):
    return None
  first = buf[offset]
  offset += 1
  tag_num = (first >> 4) & 0x0F
  is_context = bool(first & 0x08)
  lvt = first & 0x07
  if tag_num == 15:
    if offset >= len(buf):
      return None
    tag_num = buf[offset]
    offset += 1
  if is_context and lvt in (6, 7):
    return (tag_num, is_context, lvt, offset)
  if lvt == 5:
    if offset >= len(buf):
      return None
    length = buf[offset]
    offset += 1
    if length == 254 and offset + 2 <= len(buf):
      length = struct.unpack("!H", buf[offset : offset + 2])[0]
      offset += 2
    elif length == 255 and offset + 4 <= len(buf):
      length = struct.unpack("!I", buf[offset : offset + 4])[0]
      offset += 4
  else:
    length = lvt
  return (tag_num, is_context, length, offset)


def _format_object_id(raw4: bytes) -> str:
  """Formats a 4-byte BACnet Object Identifier as '<type>-<instance>'."""
  val = struct.unpack("!I", raw4)[0]
  obj_type = (val >> 22) & 0x3FF
  obj_inst = val & 0x3FFFFF
  type_name = OBJECT_TYPE_NAMES.get(obj_type, f"type{obj_type}")
  return f"{type_name}-{obj_inst}"


def _decode_service_fields(frame: BacnetFrame, payload: bytes) -> None:
  """Extracts key fields from I-Am, Who-Is, COV, Error, and Read/Write APDUs."""
  if frame.pdu_type == PDU_ERROR:
    t1 = _read_tag(payload, 0)
    if t1 and t1[3] + t1[2] <= len(payload):
      frame.error_class = _decode_uint(payload[t1[3] : t1[3] + t1[2]])
      off2 = t1[3] + t1[2]
      t2 = _read_tag(payload, off2)
      if t2 and t2[3] + t2[2] <= len(payload):
        frame.error_code = _decode_uint(payload[t2[3] : t2[3] + t2[2]])
    return

  if frame.pdu_type == PDU_UNCONFIRMED_REQ:
    if frame.service_choice == UNCONF_I_AM:
      tag = _read_tag(payload, 0)
      if tag and tag[2] == 4 and tag[3] + 4 <= len(payload):
        val = struct.unpack("!I", payload[tag[3] : tag[3] + 4])[0]
        if ((val >> 22) & 0x3FF) == 8:
          frame.device_instance = val & 0x3FFFFF
          frame.object_id = f"device-{frame.device_instance}"
    elif frame.service_choice == UNCONF_WHO_IS:
      t1 = _read_tag(payload, 0)
      if t1 and t1[1] and t1[0] == 0 and t1[3] + t1[2] <= len(payload):
        frame.who_is_low = _decode_uint(payload[t1[3] : t1[3] + t1[2]])
        off2 = t1[3] + t1[2]
        t2 = _read_tag(payload, off2)
        if t2 and t2[1] and t2[0] == 1 and t2[3] + t2[2] <= len(payload):
          frame.who_is_high = _decode_uint(payload[t2[3] : t2[3] + t2[2]])
    elif frame.service_choice == UNCONF_COV:
      # Context tags: [0] process-id, [1] initiating-device, [2] monitored-obj
      offset = 0
      while offset < len(payload):
        tag = _read_tag(payload, offset)
        if not tag or (tag[1] and tag[2] in (6, 7)):
          break
        val_end = tag[3] + tag[2]
        if val_end > len(payload):
          break
        if tag[1] and tag[0] == 1 and tag[2] == 4:
          val = struct.unpack("!I", payload[tag[3] : val_end])[0]
          frame.device_instance = val & 0x3FFFFF
        elif tag[1] and tag[0] == 2 and tag[2] == 4:
          frame.object_id = _format_object_id(payload[tag[3] : val_end])
          break
        offset = val_end
    return

  if frame.pdu_type == PDU_CONFIRMED_REQ and frame.service_choice in (
      SVC_READ_PROPERTY,
      SVC_READ_PROPERTY_MULTIPLE,
      SVC_WRITE_PROPERTY,
      SVC_SUBSCRIBE_COV,
  ):
    tag = _read_tag(payload, 0)
    if tag and tag[1] and tag[0] == 0 and tag[2] == 4 and tag[3] + 4 <= len(
        payload
    ):
      frame.object_id = _format_object_id(payload[tag[3] : tag[3] + 4])


def _decode_apdu(frame: BacnetFrame, apdu: bytes) -> None:
  """Decodes the BACnet APDU header and invokes service-field extraction."""
  if not apdu:
    return
  b0 = apdu[0]
  pdu_type = (b0 >> 4) & 0x0F
  frame.pdu_type = pdu_type

  if pdu_type == PDU_CONFIRMED_REQ:
    frame.segmented = bool(b0 & 0x08)
    frame.more_follows = bool(b0 & 0x04)
    min_len = 6 if frame.segmented else 4
    if len(apdu) < min_len:
      return
    frame.invoke_id = apdu[2]
    if frame.segmented:
      frame.seq_num = apdu[3]
      frame.service_choice = apdu[5]
      frame.apdu_payload = apdu[6:]
    else:
      frame.service_choice = apdu[3]
      frame.apdu_payload = apdu[4:]
    if not frame.segmented or frame.seq_num == 0:
      _decode_service_fields(frame, frame.apdu_payload)
  elif pdu_type == PDU_UNCONFIRMED_REQ:
    if len(apdu) < 2:
      return
    frame.service_choice = apdu[1]
    frame.apdu_payload = apdu[2:]
    _decode_service_fields(frame, frame.apdu_payload)
  elif pdu_type == PDU_SIMPLE_ACK:
    if len(apdu) >= 3:
      frame.invoke_id = apdu[1]
      frame.service_choice = apdu[2]
  elif pdu_type == PDU_COMPLEX_ACK:
    frame.segmented = bool(b0 & 0x08)
    frame.more_follows = bool(b0 & 0x04)
    min_len = 5 if frame.segmented else 3
    if len(apdu) < min_len:
      return
    frame.invoke_id = apdu[1]
    if frame.segmented:
      frame.seq_num = apdu[2]
      frame.service_choice = apdu[4]
      frame.apdu_payload = apdu[5:]
    else:
      frame.service_choice = apdu[2]
      frame.apdu_payload = apdu[3:]
  elif pdu_type == PDU_SEGMENT_ACK:
    if len(apdu) >= 4:
      frame.invoke_id = apdu[1]
      frame.seq_num = apdu[2]
  elif pdu_type == PDU_ERROR:
    if len(apdu) >= 3:
      frame.invoke_id = apdu[1]
      frame.service_choice = apdu[2]
      frame.apdu_payload = apdu[3:]
      _decode_service_fields(frame, frame.apdu_payload)
  elif pdu_type in (PDU_REJECT, PDU_ABORT):
    if len(apdu) >= 3:
      frame.invoke_id = apdu[1]
      frame.abort_or_reject_reason = apdu[2]


def _decode_npdu(frame: BacnetFrame, npdu: bytes) -> None:
  """Decodes the BACnet NPDU header and routes to network-msg or APDU."""
  if len(npdu) < 2 or npdu[0] != 0x01:
    return
  ctrl = npdu[1]
  offset = 2
  frame.is_network_msg = bool(ctrl & 0x80)
  has_dspec = bool(ctrl & 0x20)
  has_sspec = bool(ctrl & 0x08)

  if has_dspec:
    if offset + 3 > len(npdu):
      return
    frame.dnet = struct.unpack("!H", npdu[offset : offset + 2])[0]
    dlen = npdu[offset + 2]
    offset += 3
    if offset + dlen > len(npdu):
      return
    if dlen > 0:
      frame.dadr = ":".join(f"{b:02x}" for b in npdu[offset : offset + dlen])
    offset += dlen

  if has_sspec:
    if offset + 3 > len(npdu):
      return
    frame.snet = struct.unpack("!H", npdu[offset : offset + 2])[0]
    slen = npdu[offset + 2]
    offset += 3
    if offset + slen > len(npdu):
      return
    if slen > 0:
      frame.sadr = ":".join(f"{b:02x}" for b in npdu[offset : offset + slen])
    offset += slen

  if has_dspec:
    if offset >= len(npdu):
      return
    frame.hop_count = npdu[offset]
    offset += 1

  if frame.is_network_msg:
    if offset >= len(npdu):
      return
    msg_type = npdu[offset]
    frame.network_msg_type = msg_type
    offset += 1
    if msg_type in (NET_I_AM_ROUTER, NET_ROUTER_BUSY, NET_ROUTER_AVAILABLE):
      while offset + 2 <= len(npdu):
        frame.network_numbers.append(
            struct.unpack("!H", npdu[offset : offset + 2])[0]
        )
        offset += 2
    elif msg_type == NET_REJECT_MESSAGE and offset + 3 <= len(npdu):
      frame.reject_reason = npdu[offset]
      frame.network_numbers.append(
          struct.unpack("!H", npdu[offset + 1 : offset + 3])[0]
      )
    elif msg_type == NET_NETWORK_NUMBER_IS and offset + 2 <= len(npdu):
      frame.network_numbers.append(
          struct.unpack("!H", npdu[offset : offset + 2])[0]
      )
    return

  if offset < len(npdu):
    _decode_apdu(frame, npdu[offset:])


def decode_bacnet_packet(packet: RawPacket) -> Optional[BacnetFrame]:
  """Decodes a RawPacket into a BacnetFrame if it is valid BACnet/IP."""
  ip_buf = _extract_ipv4(packet)
  if ip_buf is None or len(ip_buf) < 20:
    return None
  ihl = (ip_buf[0] & 0x0F) * 4
  if ihl < 20 or len(ip_buf) < ihl + 8:
    return None
  proto = ip_buf[9]
  if proto != 17:  # UDP
    return None
  ip_id = struct.unpack("!H", ip_buf[4:6])[0]
  src_ip = socket.inet_ntoa(ip_buf[12:16])
  dst_ip = socket.inet_ntoa(ip_buf[16:20])

  udp_buf = ip_buf[ihl:]
  src_port, dst_port, udp_len = struct.unpack("!HHH", udp_buf[0:6])
  payload = udp_buf[8 : min(len(udp_buf), udp_len)]
  if len(payload) < 4 or payload[0] != BVLL_TYPE_BACNET_IP:
    return None

  bvlc_fn = payload[1]
  bvlc_len = struct.unpack("!H", payload[2:4])[0]
  if bvlc_len < 4 or bvlc_len > len(payload):
    return None

  is_bacnet_port = (
      BACNET_PORT_MIN <= src_port <= BACNET_PORT_MAX
      or BACNET_PORT_MIN <= dst_port <= BACNET_PORT_MAX
  )
  if not is_bacnet_port and bvlc_fn not in BVLC_NAMES:
    return None

  frame = BacnetFrame(
      frame_no=packet.frame_no,
      timestamp=packet.timestamp,
      caplen=packet.caplen,
      origlen=packet.origlen,
      linktype=packet.linktype,
      src_ip=src_ip,
      dst_ip=dst_ip,
      src_port=src_port,
      dst_port=dst_port,
      ip_id=ip_id,
      bvlc_function=bvlc_fn,
      raw_udp_payload=payload[:bvlc_len],
  )

  bvlc_body = payload[4:bvlc_len]
  if bvlc_fn == BVLC_RESULT and len(bvlc_body) >= 2:
    frame.bvlc_result_code = struct.unpack("!H", bvlc_body[0:2])[0]
  elif bvlc_fn == BVLC_REGISTER_FOREIGN_DEVICE and len(bvlc_body) >= 2:
    frame.fdt_ttl = struct.unpack("!H", bvlc_body[0:2])[0]
  elif bvlc_fn == BVLC_FORWARDED_NPDU and len(bvlc_body) >= 6:
    frame.orig_ip = socket.inet_ntoa(bvlc_body[0:4])
    frame.orig_port = struct.unpack("!H", bvlc_body[4:6])[0]
    _decode_npdu(frame, bvlc_body[6:])
  elif bvlc_fn in (
      BVLC_ORIGINAL_UNICAST,
      BVLC_ORIGINAL_BROADCAST,
      BVLC_DISTRIBUTE_BROADCAST,
  ):
    _decode_npdu(frame, bvlc_body)

  return frame


def decode_pcap(pcap_path: str) -> Tuple[List[BacnetFrame], int, int]:
  """Decodes all BACnet/IP frames in a PCAP/PCAPNG file.

  Returns:
    (bacnet_frames, total_packets, truncated_packets)
  """
  frames: List[BacnetFrame] = []
  total_packets = 0
  truncated_packets = 0
  for pkt in iter_raw_packets(pcap_path):
    total_packets += 1
    if pkt.caplen < pkt.origlen:
      truncated_packets += 1
    decoded = decode_bacnet_packet(pkt)
    if decoded is not None:
      frames.append(decoded)
  return frames, total_packets, truncated_packets
