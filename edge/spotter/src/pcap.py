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
from typing import Callable, Iterator, List, Optional, Set, Tuple

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
_MAX_BVLL_LEN = 1500
_MAX_TAIL_BYTES = _MAX_BVLL_LEN + 28


def validate_trace_interface(interface: Optional[str]) -> str:
  """Validates that a trace capture interface is an explicit host interface."""
  if not interface or not str(interface).strip():
    raise ValueError(
        "Explicit host network interface is required for trace capture"
    )
  iface = str(interface).strip()
  if iface.lower() == "any":
    raise ValueError(
        "Interface 'any' is not permitted for trace capture; specify an"
        " explicit host interface"
    )
  return iface


def build_trace_bpf_filter(
    base_filter: str,
    exclude_capture_host: bool = False,
    gateway_ip: Optional[str] = None,
) -> str:
  """Constructs the effective tcpdump BPF filter, optionally excluding the capture host."""
  base = (base_filter or "").strip()
  if not exclude_capture_host:
    return base
  if not gateway_ip:
    raise ValueError(
        "exclude_capture_host requires a resolved capture host IPv4 address"
    )
  try:
    socket.inet_aton(gateway_ip)
  except OSError as err:
    raise ValueError(
        f"Invalid capture host IPv4 address for exclude_capture_host: {gateway_ip!r}"
    ) from err
  exclude_clause = f"not host {gateway_ip}"
  if base:
    return f"({base}) and {exclude_clause}"
  return exclude_clause


def build_whois_router_to_network_bvll(unicast: bool = False) -> bytes:
  """Builds an unconfirmed BACnet/IP BVLL Who-Is-Router-To-Network frame."""
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
  """Resolves the local IPv4 address for the explicit target capture interface."""
  if interface:
    iface = validate_trace_interface(interface)
    try:
      with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        ifreq = struct.pack("256s", iface[:15].encode("utf-8"))
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


def check_raw_socket_permission() -> None:
  """Verifies CAP_NET_RAW availability by opening and closing a raw UDP socket."""
  with socket.socket(socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_UDP):
    pass


def send_raw_bvll(
    pdu: bytes,
    dst_ip: str = "255.255.255.255",
    src_ip: str = "0.0.0.0",
    src_port: int = BACNET_UDP_PORT,
    dst_port: int = BACNET_UDP_PORT,
    interface: Optional[str] = None,
) -> bool:
  """Transmits a BACnet BVLL frame with source port 47808 via raw socket without binding UDP 47808.

  Raises:
    OSError: If CAP_NET_RAW is unavailable or socket transmission fails.
  """
  iface = validate_trace_interface(interface) if interface is not None else None
  udp_len = 8 + len(pdu)
  header_zero_csum = struct.pack("!HHHH", src_port, dst_port, udp_len, 0)
  checksum = _compute_udp_checksum(src_ip, dst_ip, header_zero_csum + pdu)
  udp_frame = (
      struct.pack("!HHHH", src_port, dst_port, udp_len, checksum) + pdu
  )

  with socket.socket(
      socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_UDP
  ) as raw_sock:
    raw_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    if iface and hasattr(socket, "SO_BINDTODEVICE"):
      raw_sock.setsockopt(
          socket.SOL_SOCKET,
          socket.SO_BINDTODEVICE,
          iface.encode("utf-8") + b"\x00",
      )
    raw_sock.sendto(udp_frame, (dst_ip, dst_port))
  return True


def extract_bacnet_trace_observations(
    chunk: bytes,
    tail: bytes = b"",
) -> Tuple[Set[int], List[Tuple[int, str, bool]], bytes]:
  """Extracts I-Am-Router-To-Network DNETs and I-Am device observations from a PCAP stream chunk.

  Carries incomplete trailing BVLL frames in ``tail`` across chunk boundaries.

  Returns:
    Tuple of (routed_dnets, iam_observations, next_tail) where each entry in
    ``iam_observations`` is ``(device_id, address_key, is_routed_mstp)``.
  """
  discovered_dnets: Set[int] = set()
  iam_observations: List[Tuple[int, str, bool]] = []
  if decode_bacnet_bvll is None:
    return discovered_dnets, iam_observations, b""

  buf = (tail or b"") + (chunk or b"")
  if not buf:
    return discovered_dnets, iam_observations, b""

  next_tail = b""
  idx = 0
  buf_len = len(buf)
  while idx < buf_len:
    pos = buf.find(b"\x81", idx)
    if pos < 0:
      break
    if pos + 4 > buf_len:
      # Incomplete BVLL header at end of chunk; preserve with preceding IPv4/UDP header if any.
      keep_from = max(0, pos - 28)
      next_tail = buf[keep_from:]
      break

    bvlc_func = buf[pos + 1]
    if bvlc_func in (0x04, 0x0A, 0x0B):
      bvlc_len = int.from_bytes(buf[pos + 2 : pos + 4], "big")
      if 7 <= bvlc_len <= _MAX_BVLL_LEN:
        if pos + bvlc_len > buf_len:
          keep_from = max(0, pos - 28)
          next_tail = buf[keep_from:]
          break

        outer_ip: Optional[str] = None
        outer_port: int = BACNET_UDP_PORT
        if (
            pos >= 28
            and (buf[pos - 28] >> 4) == 4
            and buf[pos - 19] == socket.IPPROTO_UDP
        ):
          try:
            outer_ip = socket.inet_ntoa(buf[pos - 16 : pos - 12])
            outer_port = int.from_bytes(buf[pos - 8 : pos - 6], "big")
          except OSError:
            outer_ip = None

        decoded = decode_bacnet_bvll(
            buf[pos : pos + bvlc_len],
            outer_ip=outer_ip,
            outer_port=outer_port,
        )
        if decoded:
          if decoded.iam_router_networks:
            discovered_dnets.update(decoded.iam_router_networks)
          if decoded.device_id is not None:
            is_routed = decoded.snet is not None
            if is_routed:
              addr_key = f"{decoded.snet}:{decoded.sadr or ''}"
            else:
              ip_str = decoded.orig_ip or outer_ip or "unknown"
              port_num = decoded.orig_port or outer_port or BACNET_UDP_PORT
              addr_key = f"{ip_str}:{port_num}"
            iam_observations.append((decoded.device_id, addr_key, is_routed))
        idx = pos + bvlc_len
        continue
    idx = pos + 1

  if len(next_tail) > _MAX_TAIL_BYTES:
    next_tail = next_tail[-_MAX_TAIL_BYTES:]
  return discovered_dnets, iam_observations, next_tail


def extract_routed_networks_from_pcap_chunk(chunk: bytes) -> Set[int]:
  """Extracts BACnet DNETs from I-Am-Router-To-Network frames in a PCAP chunk."""
  dnets, _, _ = extract_bacnet_trace_observations(chunk)
  return dnets


def run_bacnet_trace_whois_rounds(
    discovered_routed_networks: Set[int],
    scan_round_timestamps: List[str],
    stop_event: threading.Event,
    max_duration_sec: int = 7200,
    interface: Optional[str] = None,
    dst_ip: str = "255.255.255.255",
    src_ip: str = "0.0.0.0",
    rounds: int = 3,
    router_wait_sec: float = 2.0,
    dnet_pace_sec: float = 1.0,
    global_wait_sec: float = 15.0,
    monotonic_fn: Callable[[], float] = time.monotonic,
    error_out: Optional[List[BaseException]] = None,
) -> None:
  """Executes broadcast-only Who-Is rounds spaced at t = 0, T/3, 2T/3 across the capture window."""
  if rounds <= 0:
    return

  window_start = monotonic_fn()
  round_spacing_sec = float(max_duration_sec) / float(rounds)

  try:
    for round_idx in range(rounds):
      if stop_event.is_set():
        break

      if round_idx > 0:
        target_time = window_start + round_idx * round_spacing_sec
        wait_sec = target_time - monotonic_fn()
        if wait_sec > 0 and stop_event.wait(timeout=wait_sec):
          break
        if stop_event.is_set():
          break

      # 1. Broadcast Who-Is-Router-To-Network (record round timestamp only after send succeeds)
      send_raw_bvll(
          pdu=build_whois_router_to_network_bvll(unicast=False),
          dst_ip=dst_ip,
          src_ip=src_ip,
          src_port=BACNET_UDP_PORT,
          interface=interface,
      )
      round_ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
      scan_round_timestamps.append(round_ts)

      if router_wait_sec > 0 and stop_event.wait(timeout=router_wait_sec):
        break

      # 2. Local broadcast Who-Is + paced per-DNET broadcast Who-Is
      send_raw_bvll(
          pdu=build_whois_bvll(dnet=None, unicast=False),
          dst_ip=dst_ip,
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
        if dnet_pace_sec > 0 and stop_event.wait(timeout=dnet_pace_sec):
          break

      if stop_event.is_set():
        break

      # 3. Global broadcast Who-Is
      send_raw_bvll(
          pdu=build_whois_bvll(dnet=GLOBAL_BACNET_DNET, unicast=False),
          dst_ip=dst_ip,
          src_ip=src_ip,
          src_port=BACNET_UDP_PORT,
          interface=interface,
      )
      if global_wait_sec > 0 and stop_event.wait(timeout=global_wait_sec):
        break
  except BaseException as err:  # pylint: disable=broad-exception-caught
    if error_out is not None:
      error_out.append(err)
      stop_event.set()
    else:
      raise


def capture_packets(
    interface: str,
    filter_str: str,
    max_duration_sec: int,
    max_bytes: int,
    stop_event: Optional[threading.Event] = None,
    on_ready: Optional[Callable[[], None]] = None,
    heartbeat_sec: Optional[float] = None,
) -> Iterator[bytes]:
  """Spawns tcpdump to capture packets, yielding chunks of bytes.

  Guarantees resource safety by:
  - Requiring an explicit host network interface (never 'any').
  - Invoking ``on_ready`` only after tcpdump starts listening so active probes
    never race ahead of capture initialization.
  - Bounded polling with non-blocking I/O so idle networks do not block.
  - Concurrent stderr draining to eliminate pipe buffer deadlocks.
  - Safety circuit breaker evaluation to prevent kernel OOM termination.
  - Strict caps on duration and byte volume.
  """
  iface = validate_trace_interface(interface)

  if check_safety_circuit_breaker and check_safety_circuit_breaker():
    LOGGER.warning(
        "Safety circuit breaker active: Throttling packet capture before"
        " launch."
    )
    return

  LOGGER.info(
      "Starting packet capture. Interface: %s, Filter: '%s', Duration Limit:"
      " %ss, Size Limit: %s bytes",
      iface,
      filter_str,
      max_duration_sec,
      max_bytes,
  )

  cmd = ["tcpdump", "-i", iface, "-w", "-", "-U"]
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
  last_yield_time = start_time
  total_bytes = 0
  stderr_chunks: List[bytes] = []
  stopped_by_limit = False
  ready_notified = False

  def _notify_ready() -> None:
    nonlocal ready_notified
    if not ready_notified:
      ready_notified = True
      if on_ready is not None:
        on_ready()

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

      # Wait briefly for tcpdump's "listening on" banner on stderr before signaling ready
      ready_deadline = start_time + min(2.0, float(max_duration_sec))

      while rlist:
        if stop_event is not None and stop_event.is_set():
          stopped_by_limit = True
          break
        now = time.time()
        elapsed = now - start_time
        if elapsed >= max_duration_sec:
          LOGGER.info(
              "Capture duration limit reached (%ss). Stopping tcpdump...",
              max_duration_sec,
          )
          stopped_by_limit = True
          break

        if not ready_notified and now >= ready_deadline:
          _notify_ready()

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

        # 1. Drain stderr to prevent pipe buffer deadlock (>64KB) and detect readiness
        if proc.stderr in readable:
          try:
            err_chunk = proc.stderr.read(65536)
            if err_chunk:
              if len(stderr_chunks) < 16:
                stderr_chunks.append(err_chunk)
              if not ready_notified and b"listening on" in err_chunk.lower():
                _notify_ready()
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
            if not ready_notified:
              _notify_ready()
            total_bytes += len(chunk)
            last_yield_time = time.time()
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

        if (
            heartbeat_sec is not None
            and heartbeat_sec > 0
            and (time.time() - last_yield_time) >= heartbeat_sec
        ):
          last_yield_time = time.time()
          yield b""

        if proc.poll() is not None and not readable:
          break

    else:
      # Fallback path for mocked execution environments: Popen has already been invoked
      _notify_ready()
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
