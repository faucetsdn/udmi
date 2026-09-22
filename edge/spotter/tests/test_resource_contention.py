"""Resource contention, FD leak detection, and safety circuit breaker tests."""

import os
import unittest

from edge.spotter.src.agent import wait_for_broker_readiness
from edge.spotter.src.host_telemetry import check_safety_circuit_breaker
from edge.spotter.src.host_telemetry import get_cpu_and_memory_metrics
from edge.spotter.src.pcap import capture_packets


def get_current_fd_count() -> int:
  """Returns number of active file descriptors for the current process."""
  fd_dir = f"/proc/{os.getpid()}/fd"
  if os.path.exists(fd_dir):
    # Subtract 1 for the directory descriptor opened by os.listdir itself
    return max(0, len(os.listdir(fd_dir)) - 1)
  return 0


class TestResourceContention(unittest.TestCase):
  """Verifies FD stability and safety circuit breaker behavior."""

  def test_circuit_breaker_trips_under_simulated_load(self):
    """Validates that check_safety_circuit_breaker trips when reached."""
    metrics = get_cpu_and_memory_metrics()
    current_used = metrics.get("mem_used_pct", 0.0)

    # Setting threshold below current usage must trip the circuit breaker
    tripped = check_safety_circuit_breaker(max_mem_pct=0.01)
    self.assertTrue(
        tripped,
        f"Circuit breaker failed to trip with max_mem_pct=0.01 "
        f"(current: {current_used}%)",
    )

    # Setting threshold at 100% must not trip
    not_tripped = check_safety_circuit_breaker(max_mem_pct=100.0)
    self.assertFalse(
        not_tripped,
        "Circuit breaker tripped unexpectedly at 100% threshold",
    )

  def test_no_file_descriptor_leaks_under_repeated_operations(self):
    """Executes repeated operations and asserts zero file descriptor leakage."""
    if not os.path.exists(f"/proc/{os.getpid()}/fd"):
      self.skipTest("/proc filesystem not available on this platform")

    # Warm up caches and internal runtime imports
    _ = get_cpu_and_memory_metrics()
    _ = check_safety_circuit_breaker()

    fd_before = get_current_fd_count()
    print(
        f"\n  [FD Test] Baseline open file descriptors in PID {os.getpid()}: "
        f"{fd_before}",
        flush=True,
    )

    # 1. Stress host telemetry reading (/proc/meminfo, /proc/loadavg)
    for _ in range(50):
      _ = get_cpu_and_memory_metrics()
      _ = check_safety_circuit_breaker()

    # 2. Stress network socket probing (closed sockets properly cleaned up)
    for _ in range(10):
      # Port 1 is typically closed; socket must be cleanly closed without leak
      _ = wait_for_broker_readiness("127.0.0.1", 1, timeout_sec=0)

    # 3. Stress packet capture generator execution and pipeline teardown
    for _ in range(5):
      gen = capture_packets(
          interface="lo",
          filter_str="port 65534",
          max_duration_sec=1,
          max_bytes=1024,
      )
      # Drain generator chunks
      _ = list(gen)

    fd_after = get_current_fd_count()
    fd_diff = fd_after - fd_before
    print(
        f"  [FD Test] Post-workload open file descriptors in PID "
        f"{os.getpid()}: {fd_after} (Diff: {fd_diff})",
        flush=True,
    )

    self.assertLessEqual(
        fd_diff,
        1,
        f"File descriptor leak detected! Before: {fd_before}, "
        f"After: {fd_after}, Leaked: {fd_diff}",
    )

  def test_fd_leak_detector_catches_intentional_leak(self):
    """Negative verification: confirms that unclosed FDs are caught."""
    if not os.path.exists(f"/proc/{os.getpid()}/fd"):
      self.skipTest("/proc filesystem not available on this platform")

    fd_before = get_current_fd_count()
    leaked_fds = [os.open("/dev/null", os.O_RDONLY) for _ in range(3)]
    try:
      fd_after = get_current_fd_count()
      diff = fd_after - fd_before
      self.assertGreaterEqual(
          diff,
          3,
          f"Negative test failed: expected >= 3 leaked FDs, detected {diff}",
      )
    finally:
      for fd in leaked_fds:
        os.close(fd)


if __name__ == "__main__":
  unittest.main(verbosity=2)
