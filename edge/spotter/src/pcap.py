"""PCAP packet capture wrapper for Spotter."""

import logging
import os
import select
import subprocess
import time
from typing import Iterator

try:
  from host_telemetry import check_safety_circuit_breaker
except ImportError:
  try:
    from edge.spotter.src.host_telemetry import check_safety_circuit_breaker
  except ImportError:
    check_safety_circuit_breaker = None

LOGGER = logging.getLogger("spotter_pcap")


def capture_packets(
    interface: str,
    filter_str: str,
    max_duration_sec: int,
    max_bytes: int,
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
        elapsed = time.time() - start_time
        if elapsed >= max_duration_sec:
          LOGGER.info(
              "Capture duration limit reached (%ss). Stopping tcpdump...",
              max_duration_sec,
          )
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
              break
          elif chunk == b"":
            rlist.remove(proc.stdout)

        if proc.poll() is not None and not readable:
          break

    else:
      # Fallback path for mocked execution environments
      while True:
        elapsed = time.time() - start_time
        if elapsed >= max_duration_sec:
          LOGGER.info(
              "Capture duration limit reached (%ss). Stopping tcpdump...",
              max_duration_sec,
          )
          break

        if check_safety_circuit_breaker and check_safety_circuit_breaker():
          LOGGER.warning(
              "Safety circuit breaker triggered: Throttling packet capture"
              " fallback loop under memory pressure."
          )
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
          break

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
    except Exception:  # pylint: disable=broad-exception-caught
      pass

    try:
      if proc.stdout and not proc.stdout.closed:
        proc.stdout.close()
      if proc.stderr and not proc.stderr.closed:
        proc.stderr.close()
    except Exception:  # pylint: disable=broad-exception-caught
      pass

    full_stderr = b"".join(stderr_chunks)
    if full_stderr:
      LOGGER.debug("tcpdump stderr: %s", full_stderr.decode(errors="replace"))

    LOGGER.info(
        "Packet capture worker stopped. Total bytes captured: %s",
        total_bytes,
    )

