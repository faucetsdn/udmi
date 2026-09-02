"""Unit tests for host telemetry and OS detection."""

import unittest
from unittest.mock import mock_open, patch

from edge.spotter.src.host_telemetry import (
    check_safety_circuit_breaker,
    get_cpu_and_memory_metrics,
    get_host_os_info,
)


class TestHostTelemetry(unittest.TestCase):
    """Tests for host metric reading and OS distribution parsing."""

    @patch("os.path.exists")
    def test_get_host_os_info(self, mock_exists):
        mock_exists.side_effect = lambda p: p == "/host/etc/os-release"
        os_release_data = """
NAME="Debian GNU/Linux"
VERSION_ID="12"
VERSION="12 (bookworm)"
PRETTY_NAME="Debian GNU/Linux 12 (bookworm)"
ID=debian
"""
        with patch("builtins.open", mock_open(read_data=os_release_data)):
            info = get_host_os_info()
            self.assertEqual(info.get("ID"), "debian")
            self.assertEqual(info.get("VERSION_ID"), "12")
            self.assertEqual(info.get("PRETTY_NAME"), "Debian GNU/Linux 12 (bookworm)")

    @patch("os.path.exists")
    def test_get_cpu_and_memory_metrics(self, mock_exists):
        mock_exists.return_value = True
        meminfo_data = """
MemTotal:       16384000 kB
MemFree:         4096000 kB
MemAvailable:    8192000 kB
"""
        loadavg_data = "0.45 0.30 0.20 1/800 12345\n"

        def custom_open(path, *args, **kwargs):
            if "meminfo" in path:
                return mock_open(read_data=meminfo_data)()
            elif "loadavg" in path:
                return mock_open(read_data=loadavg_data)()
            return mock_open()()

        with patch("builtins.open", side_effect=custom_open):
            metrics = get_cpu_and_memory_metrics()
            self.assertEqual(metrics.get("mem_total_mb"), 16000.0)
            self.assertEqual(metrics.get("mem_free_mb"), 8000.0)
            self.assertEqual(metrics.get("mem_used_pct"), 50.0)
            self.assertEqual(metrics.get("load_1m"), 0.45)
            self.assertEqual(metrics.get("load_5m"), 0.30)
            self.assertEqual(metrics.get("load_15m"), 0.20)

    @patch("edge.spotter.src.host_telemetry.get_cpu_and_memory_metrics")
    def test_safety_circuit_breaker(self, mock_metrics):
        # Normal memory usage (50% <= 85%) -> Safe (False)
        mock_metrics.return_value = {"mem_used_pct": 50.0}
        self.assertFalse(check_safety_circuit_breaker(max_mem_pct=85.0))

        # High memory usage (90% >= 85%) -> Tripped (True)
        mock_metrics.return_value = {"mem_used_pct": 90.0}
        self.assertTrue(check_safety_circuit_breaker(max_mem_pct=85.0))


if __name__ == "__main__":
    unittest.main()
