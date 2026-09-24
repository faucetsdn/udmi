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


if __name__ == "__main__":
  unittest.main()


