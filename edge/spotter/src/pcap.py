"""PCAP packet capture wrapper and raw-socket BACnet trace scanner for Spotter."""

from datetime import datetime, timezone
import fcntl
import logging
import os
import select
import socket
import struct
import subprocess
import threading
import time
from typing import Iterator, List, Optional, Set

try:
  from host_telemetry import check_safety_circuit_breaker
except ImportError:
  try:
    from edge.spotter.src.host_telemetry import check_safety_circuit_breaker
  except ImportError:
    check_safety_circuit_breaker = None

try:
  from providers.passive import decode_bacnet_bvll
except ImportError:
  try:
    from edge.spotter.src.providers.passive import decode_bacnet_bvll
  except ImportError:
    decode_bacnet_bvll = None

LOGGER = logging.getLogger("spotter_pcap")

BACNET_UDP_PORT = 47808
GLOBAL_BACNET_DNET = 0xFFFF


def build_whois_router_to_network_bvll(unicast: bool = False) -> bytes:
  """Builds an unconfirmed BACnet/IP BVLL Who-Is-Router-To-Network frame."""
  # BVLL Type 0x81, Function 0x0a (Unicast) or 0x0b (Broadcast), Length 7
  # NPDU Version 0x01, Control 0x80 (Network Layer Message), MsgType 0x00 (Who-Is-Router-To-Network)
  bvlc_func = 0x0A if unicast else 0x0B
  return bytes([0x81, bvlc_func, 0x00, 0x07, 0x01, 0x80, 0x00])


def build_whois_bvll(dnet: Optional[int] = None, unicast: bool = False) -> bytes:
  """Builds an unconfirmed BACnet/IP BVLL Who-Is frame (local, per-DNET, or global)."""
  apdu = b"\x10\x08"  # Unconfirmed-Request PDU (0x10), Service Choice 8 (Who-Is)
  if dnet is None:
    npdu = b"\x01\x00"
  else:
    if not (1 <= int(dnet) <= 0xFFFF):
      raise ValueError(f"Invalid BACnet DNET {dnet!r}: expected 1..65535")
    # Control 0x20 (Destination Specifier present), DNET (2B), DLEN=0 (broadcast), HopCount=255
    npdu = b"\x01\x20" + int(dnet).to_bytes(2, "big") + b"\x00\xff"
  total_len = 4 + len(npdu) + len(apdu)
  bvlc_func = 0x0A if unicast else 0x0B
  return bytes([0x81, bvlc_func]) + total_len.to_bytes(2, "big") + npdu + apdu


def _compute_udp_checksum(
    src_ip: str, dst_ip: str, udp_header_and_payload: bytes
) -> int:
  """Computes the RFC 768 16-bit one's complement UDP checksum over IPv4 pseudo-header."""
  if src_ip in ("0.0.0.0", "") or (
      src_ip == "127.0.0.1" and dst_ip != "127.0.0.1"
  ):
    return 0
  try:
    src_packed = socket.inet_aton(src_ip)
    dst_packed = socket.inet_aton(dst_ip)
  except OSError:
    return 0
  if src_packed == b"\x00\x00\x00\x00":
    return 0

  udp_len = len(udp_header_and_payload)
  pseudo_header = struct.pack(
      "!4s4sBBH", src_packed, dst_packed, 0, socket.IPPROTO_UDP, udp_len
  )
  data = pseudo_header + udp_header_and_payload
  if len(data) % 2 == 1:
    data += b"\x00"

  total = 0
  for i in range(0, len(data), 2):
    total += (data[i] << 8) + data[i + 1]
  while total >> 16:
    total = (total & 0xFFFF) + (total >> 16)
  checksum = ~total & 0xFFFF
  return 0xFFFF if checksum == 0 else checksum


def resolve_gateway_ip(interface: Optional[str] = None) -> str:
  """Resolves the local IPv4 address for the gateway or target capture interface."""
  if interface and interface != "any":
    try:
      with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        ifreq = struct.pack("256s", interface[:15].encode("utf-8"))
        res = fcntl.ioctl(s.fileno(), 0x8915, ifreq)  # SIOCGIFADDR
        ip = socket.inet_ntoa(res[20:24])
        if ip and ip != "0.0.0.0":
          return ip
    except OSError:
      pass
  try:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
      s.connect(("10.255.255.255", 1))
      return s.getsockname()[0]
  except OSError:
    return "127.0.0.1"


def send_raw_bvll(
    pdu: bytes,
    dst_ip: str = "255.255.255.255",
    src_ip: str = "0.0.0.0",
    src_port: int = BACNET_UDP_PORT,
    dst_port: int = BACNET_UDP_PORT,
    interface: Optional[str] = None,
) -> bool:
  """Transmits a BACnet BVLL frame with source port 47808 via raw socket without binding UDP 47808."""
  udp_len = 8 + len(pdu)
  header_zero_csum = struct.pack("!HHHH", src_port, dst_port, udp_len, 0)
  checksum = _compute_udp_checksum(src_ip, dst_ip, header_zero_csum + pdu)
  udp_frame = (
      struct.pack("!HHHH", src_port, dst_port, udp_len, checksum) + pdu
  )

  try:
    with socket.socket(
        socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_UDP
    ) as raw_sock:
      raw_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
      if interface and interface != "any" and hasattr(socket, "SO_BINDTODEVICE"):
        raw_sock.setsockopt(
            socket.SOL_SOCKET,
            socket.SO_BINDTODEVICE,
            interface.encode("utf-8") + b"\x00",
        )
      raw_sock.sendto(udp_frame, (dst_ip, dst_port))
    return True
  except OSError as err:
    LOGGER.debug("Raw UDP transmit skipped/failed: %s", err)
    return False


def extract_routed_networks_from_pcap_chunk(
    chunk: bytes,
    discovered_routers: Optional[Set[str]] = None,
) -> Set[int]:
  """Extracts BACnet DNETs and router IPs from I-Am-Router-To-Network and NPDU SNET frames."""
  discovered: Set[int] = set()
  if not chunk or decode_bacnet_bvll is None:
    return discovered

  idx = 0
  while True:
    pos = chunk.find(b"\x81", idx)
    if pos < 0 or pos + 7 > len(chunk):
      break
    bvlc_func = chunk[pos + 1]
    if bvlc_func in (0x04, 0x0A, 0x0B):
      bvlc_len = int.from_bytes(chunk[pos + 2 : pos + 4], "big")
      if 7 <= bvlc_len <= 1500 and pos + bvlc_len <= len(chunk):
        outer_ip: Optional[str] = None
        outer_port: int = BACNET_UDP_PORT
        if (
            pos >= 28
            and (chunk[pos - 28] >> 4) == 4
            and chunk[pos - 19] == socket.IPPROTO_UDP
        ):
          try:
            outer_ip = socket.inet_ntoa(chunk[pos - 16 : pos - 12])
            outer_port = int.from_bytes(chunk[pos - 8 : pos - 6], "big")
          except OSError:
            outer_ip = None
        decoded = decode_bacnet_bvll(
            chunk[pos : pos + bvlc_len],
            outer_ip=outer_ip,
            outer_port=outer_port,
        )
        if decoded:
          if decoded.iam_router_networks:
            discovered.update(decoded.iam_router_networks)
            if discovered_routers is not None and decoded.orig_ip:
              discovered_routers.add(decoded.orig_ip)
          if decoded.snet is not None:
            discovered.add(decoded.snet)
            if discovered_routers is not None and decoded.orig_ip:
              discovered_routers.add(decoded.orig_ip)
    idx = pos + 1
  return discovered


def run_bacnet_trace_whois_rounds(
    discovered_routed_networks: Set[int],
    scan_round_timestamps: List[str],
    stop_event: threading.Event,
    max_duration_sec: int = 7200,
    interface: Optional[str] = None,
    dst_ip: str = "255.255.255.255",
    src_ip: str = "0.0.0.0",
    rounds: int = 3,
    discovered_routers: Optional[Set[str]] = None,
) -> None:
  """Executes paced rounds of raw-socket broadcast and unicast Who-Is-Router-To-Network and Who-Is."""
  scale = min(1.0, max(0.02, float(max_duration_sec) / 60.0))
  router_wait_sec = 2.0 * scale
  dnet_pace_sec = 1.0 * scale
  global_wait_sec = 15.0 * scale

  # Brief initial startup sync so tcpdump opens its capture socket before Round 1
  if stop_event.wait(timeout=min(0.1, 0.02 * scale)):
    return

  for _ in range(rounds):
    if stop_event.is_set():
      break
    round_ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    scan_round_timestamps.append(round_ts)

    # 1. Local broadcast + unicast Who-Is-Router-To-Network
    send_raw_bvll(
        pdu=build_whois_router_to_network_bvll(unicast=False),
        dst_ip=dst_ip,
        src_ip=src_ip,
        src_port=BACNET_UDP_PORT,
        interface=interface,
    )
    for router_ip in sorted(list(discovered_routers or ())):
      send_raw_bvll(
          pdu=build_whois_router_to_network_bvll(unicast=True),
          dst_ip=router_ip,
          src_ip=src_ip,
          src_port=BACNET_UDP_PORT,
          interface=interface,
      )
    if stop_event.wait(timeout=router_wait_sec):
      break

    # 2. Local Who-Is + paced per-DNET Who-Is (broadcast and unicast to routers)
    send_raw_bvll(
        pdu=build_whois_bvll(dnet=None, unicast=False),
        dst_ip=dst_ip,
        src_ip=src_ip,
        src_port=BACNET_UDP_PORT,
        interface=interface,
    )
    for router_ip in sorted(list(discovered_routers or ())):
      send_raw_bvll(
          pdu=build_whois_bvll(dnet=None, unicast=True),
          dst_ip=router_ip,
          src_ip=src_ip,
          src_port=BACNET_UDP_PORT,
          interface=interface,
      )

    for dnet in sorted(list(discovered_routed_networks)):
      if stop_event.is_set():
        break
      send_raw_bvll(
          pdu=build_whois_bvll(dnet=dnet, unicast=False),
          dst_ip=dst_ip,
          src_ip=src_ip,
          src_port=BACNET_UDP_PORT,
          interface=interface,
      )
      for router_ip in sorted(list(discovered_routers or ())):
        send_raw_bvll(
            pdu=build_whois_bvll(dnet=dnet, unicast=True),
            dst_ip=router_ip,
            src_ip=src_ip,
            src_port=BACNET_UDP_PORT,
            interface=interface,
        )
      if stop_event.wait(timeout=dnet_pace_sec):
        break

    if stop_event.is_set():
      break

    # 3. Global Who-Is (broadcast and unicast to routers)
    send_raw_bvll(
        pdu=build_whois_bvll(dnet=GLOBAL_BACNET_DNET, unicast=False),
        dst_ip=dst_ip,
        src_ip=src_ip,
        src_port=BACNET_UDP_PORT,
        interface=interface,
    )
    for router_ip in sorted(list(discovered_routers or ())):
      send_raw_bvll(
          pdu=build_whois_bvll(dnet=GLOBAL_BACNET_DNET, unicast=True),
          dst_ip=router_ip,
          src_ip=src_ip,
          src_port=BACNET_UDP_PORT,
          interface=interface,
      )
    if stop_event.wait(timeout=global_wait_sec):
      break


def capture_packets(
    interface: str,
    filter_str: str,
    max_duration_sec: int,
    max_bytes: int,
    stop_event: Optional[threading.Event] = None,
) -> Iterator[bytes]:
  """Spawns tcpdump to capture packets, yielding chunks of bytes.

  Guarantees resource safety by:
  - Bounded polling with non-blocking I/O so idle networks do not block.
  - Concurrent stderr draining to eliminate pipe buffer deadlocks.
  - Safety circuit breaker evaluation to prevent kernel OOM termination.
  - Strict caps on duration and byte volume.
  """
  if check_safety_circuit_breaker and check_safety_circuit_breaker():
    LOGGER.warning(
        "Safety circuit breaker active: Throttling packet capture before"
        " launch."
    )
    return

  LOGGER.info(
      "Starting packet capture. Interface: %s, Filter: '%s', Duration Limit:"
      " %ss, Size Limit: %s bytes",
      interface,
      filter_str,
      max_duration_sec,
      max_bytes,
  )

  cmd = ["tcpdump", "-i", interface, "-w", "-", "-U"]
  if filter_str:
    cmd.append(filter_str)

  LOGGER.info("Executing cmd: %s", " ".join(cmd))
  proc = subprocess.Popen(  # pylint: disable=consider-using-with
      cmd,
      stdout=subprocess.PIPE,
      stderr=subprocess.PIPE,
      bufsize=0,
  )

  start_time = time.time()
  total_bytes = 0
  stderr_chunks = []
  stopped_by_limit = False

  # Configure non-blocking I/O if running with real OS file descriptors
  is_real_fd = False
  try:
    if proc.stdout and hasattr(proc.stdout, "fileno"):
      out_fd = proc.stdout.fileno()
      if isinstance(out_fd, int):
        os.set_blocking(out_fd, False)
        if proc.stderr and hasattr(proc.stderr, "fileno"):
          err_fd = proc.stderr.fileno()
          if isinstance(err_fd, int):
            os.set_blocking(err_fd, False)
        is_real_fd = True
  except Exception as err:  # pylint: disable=broad-exception-caught
    LOGGER.debug("Could not configure non-blocking mode on pipe fds: %s", err)
    is_real_fd = False

  try:
    if is_real_fd:
      rlist = []
      if proc.stdout and not proc.stdout.closed:
        rlist.append(proc.stdout)
      if proc.stderr and not proc.stderr.closed:
        rlist.append(proc.stderr)

      while rlist:
        if stop_event is not None and stop_event.is_set():
          stopped_by_limit = True
          break
        elapsed = time.time() - start_time
        if elapsed >= max_duration_sec:
          LOGGER.info(
              "Capture duration limit reached (%ss). Stopping tcpdump...",
              max_duration_sec,
          )
          stopped_by_limit = True
          break

        remaining_time = max(0.0, max_duration_sec - elapsed)
        poll_timeout = min(0.2, remaining_time)

        readable, _, _ = select.select(rlist, [], [], poll_timeout)

        # Check circuit breaker dynamically under load
        if check_safety_circuit_breaker and check_safety_circuit_breaker():
          LOGGER.warning(
              "Safety circuit breaker triggered: Throttling packet capture loop"
              " under memory pressure."
          )
          stopped_by_limit = True
          break

        # 1. Drain stderr to prevent pipe buffer deadlock (>64KB)
        if proc.stderr in readable:
          try:
            err_chunk = proc.stderr.read(65536)
            if err_chunk:
              if len(stderr_chunks) < 16:
                stderr_chunks.append(err_chunk)
            elif err_chunk == b"":
              rlist.remove(proc.stderr)
          except (BlockingIOError, IOError):
            pass

        # 2. Read stdout chunks safely
        if proc.stdout in readable:
          try:
            chunk = proc.stdout.read(65536)
          except (BlockingIOError, IOError):
            chunk = None

          if chunk:
            total_bytes += len(chunk)
            yield chunk
            if total_bytes >= max_bytes:
              LOGGER.warning(
                  "Capture size limit reached (%s bytes). Stopping tcpdump...",
                  max_bytes,
              )
              stopped_by_limit = True
              break
          elif chunk == b"":
            rlist.remove(proc.stdout)

        if proc.poll() is not None and not readable:
          break

    else:
      # Fallback path for mocked execution environments
      while True:
        if stop_event is not None and stop_event.is_set():
          stopped_by_limit = True
          break
        elapsed = time.time() - start_time
        if elapsed >= max_duration_sec:
          LOGGER.info(
              "Capture duration limit reached (%ss). Stopping tcpdump...",
              max_duration_sec,
          )
          stopped_by_limit = True
          break

        if check_safety_circuit_breaker and check_safety_circuit_breaker():
          LOGGER.warning(
              "Safety circuit breaker triggered: Throttling packet capture"
              " fallback loop under memory pressure."
          )
          stopped_by_limit = True
          break

        chunk = proc.stdout.read(65536)
        if not chunk:
          if proc.poll() is not None:
            break
          time.sleep(0.05)
          continue

        total_bytes += len(chunk)
        yield chunk

        if total_bytes >= max_bytes:
          LOGGER.warning(
              "Capture size limit reached (%s bytes). Stopping tcpdump...",
              max_bytes,
          )
          stopped_by_limit = True
          break

    if not stopped_by_limit:
      rc = proc.poll()
      if rc is None:
        try:
          rc = proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
          rc = None
      if isinstance(rc, int) and rc != 0:
        try:
          rem_err = proc.stderr.read()
          if rem_err and len(stderr_chunks) < 16:
            stderr_chunks.append(rem_err)
        except Exception as err:  # pylint: disable=broad-exception-caught
          LOGGER.debug("Error reading remaining tcpdump stderr: %s", err)
        full_stderr = b"".join(stderr_chunks)
        err_detail = (
            full_stderr.decode(errors="replace").strip()
            if isinstance(full_stderr, bytes) and full_stderr
            else f"exit code {rc}"
        )
        raise RuntimeError(f"tcpdump failed (exit code {rc}): {err_detail}")

  finally:
    # Clean shutdown of subprocess
    if proc.poll() is None:
      LOGGER.info("Terminating tcpdump process...")
      proc.terminate()
      try:
        proc.wait(timeout=3)
      except subprocess.TimeoutExpired:
        LOGGER.warning("tcpdump did not exit on SIGTERM. Killing...")
        proc.kill()
        proc.wait()

    # Check for remaining error logs in stderr
    try:
      rem_err = proc.stderr.read()
      if rem_err and len(stderr_chunks) < 16:
        stderr_chunks.append(rem_err)
    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.debug("Error reading remaining tcpdump stderr: %s", err)

    try:
      if proc.stdout:
        proc.stdout.close()
      if proc.stderr:
        proc.stderr.close()
    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.debug("Error closing tcpdump pipes: %s", err)

    full_stderr = b"".join(stderr_chunks)
    if full_stderr:
      LOGGER.debug("tcpdump stderr: %s", full_stderr.decode(errors="replace"))

    LOGGER.info(
        "Packet capture worker stopped. Total bytes captured: %s",
        total_bytes,
    )

