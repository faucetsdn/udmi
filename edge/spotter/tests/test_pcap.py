import subprocess
import time
import unittest
from unittest.mock import patch, MagicMock, call

from edge.spotter.src.pcap import capture_packets

class TestPcapCapture(unittest.TestCase):

    @patch("subprocess.Popen")
    def test_capture_packets_success(self, mock_popen):
        mock_proc = MagicMock()
        mock_popen.return_value = mock_proc
        mock_proc.poll.return_value = 0
        
        # Simulate stdout returning two chunks then EOF
        mock_proc.stdout.read.side_effect = [b"HEADER", b"DATA", b""]
        mock_proc.stderr.read.return_value = b"8 packets captured"

        generator = capture_packets(
            interface="eth0",
            filter_str="udp port 47808",
            max_duration_sec=60,
            max_bytes=100000
        )
        
        chunks = list(generator)
        self.assertEqual(chunks, [b"HEADER", b"DATA"])
        
        # Verify Popen called with correct arguments
        mock_popen.assert_called_once_with(
            ["tcpdump", "-i", "eth0", "-w", "-", "-U", "udp port 47808"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0
        )
        
        # Ensure process cleanup was handled
        self.assertTrue(mock_proc.stderr.read.called)

    @patch("time.time")
    @patch("subprocess.Popen")
    def test_capture_packets_duration_limit(self, mock_popen, mock_time):
        mock_proc = MagicMock()
        mock_popen.return_value = mock_proc
        mock_proc.poll.return_value = None
        mock_proc.stdout.read.return_value = b"DATA_CHUNK"
        mock_proc.stderr.read.return_value = b""
        
        # Simulate start time 0, then jump past max_duration_sec after first iteration
        mock_time.side_effect = [0.0, 5.0, 65.0]

        generator = capture_packets(
            interface="any",
            filter_str="",
            max_duration_sec=60,
            max_bytes=1000000
        )
        
        chunks = list(generator)
        self.assertEqual(len(chunks), 1)
        
        # Verify terminate called since duration exceeded while process was still running
        mock_proc.terminate.assert_called_once()
        mock_proc.wait.assert_called_once()

    @patch("subprocess.Popen")
    def test_capture_packets_size_limit_exceeded(self, mock_popen):
        mock_proc = MagicMock()
        mock_popen.return_value = mock_proc
        mock_proc.poll.return_value = None
        mock_proc.stdout.read.return_value = b"B" * 500
        mock_proc.stderr.read.return_value = b""

        generator = capture_packets(
            interface="lo",
            filter_str="port 80",
            max_duration_sec=60,
            max_bytes=400  # Less than one 500-byte chunk
        )
        
        chunks = list(generator)
        self.assertEqual(len(chunks), 1)
        self.assertEqual(len(chunks[0]), 500)
        
        mock_proc.terminate.assert_called_once()
        mock_proc.wait.assert_called_once()

    @patch("subprocess.Popen")
    def test_capture_packets_kill_on_timeout_expired(self, mock_popen):
        mock_proc = MagicMock()
        mock_popen.return_value = mock_proc
        mock_proc.poll.return_value = None
        mock_proc.stdout.read.return_value = b""
        
        # Force wait() to raise TimeoutExpired on terminate()
        mock_proc.poll.side_effect = None
        mock_proc.poll.return_value = None
        mock_proc.stdout.read.side_effect = [b"A" * 10, b""]
        mock_proc.wait.side_effect = [subprocess.TimeoutExpired(cmd="tcpdump", timeout=3), None]
        mock_proc.stderr.read.return_value = b""

        generator = capture_packets(
            interface="any",
            filter_str="",
            max_duration_sec=1,
            max_bytes=100
        )
        
        # Iterate once then simulate loop termination to trigger finally block
        with patch("time.time", side_effect=[0.0, 2.0, 3.0]):
            list(generator)
            
        mock_proc.terminate.assert_called_once()
        mock_proc.kill.assert_called_once()
        self.assertEqual(mock_proc.wait.call_count, 2)

if __name__ == "__main__":
    unittest.main()
