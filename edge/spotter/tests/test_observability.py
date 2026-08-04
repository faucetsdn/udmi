import json
import re
import unittest
import logging
from io import StringIO

from edge.spotter.src.metrics import SpotterMetrics
from edge.spotter.src.logger import StructuredJsonFormatter, generate_w3c_traceparent

class TestObservability(unittest.TestCase):

    def test_prometheus_metrics_export(self):
        metrics = SpotterMetrics(port=9099)
        metrics.record_pcap_transfer(10, 1, 5000, 2.5)
        metrics.record_ota_event("success")
        
        prom_payload = metrics.generate_prometheus_payload()
        self.assertIn('spotter_cpu_usage_ratio{process="spotter"}', prom_payload)
        self.assertIn('spotter_memory_bytes{type="rss"}', prom_payload)
        self.assertIn('spotter_pcap_packets_total{status="captured"} 10', prom_payload)
        self.assertIn('spotter_pcap_bytes_transferred_total{transport="mqtt"} 5000', prom_payload)
        self.assertIn('spotter_ota_events_total{result="success"} 1', prom_payload)
        self.assertIn('spotter_mqtt_connection_status 1', prom_payload)

    def test_udmi_metrics_payload(self):
        metrics = SpotterMetrics()
        udmi_data = metrics.generate_udmi_payload()
        self.assertIn("timestamp", udmi_data)
        self.assertIn("metrics", udmi_data)
        self.assertIn("cpu_ratio", udmi_data["metrics"])

    def test_safety_circuit_breaker(self):
        metrics = SpotterMetrics()
        # Normal bounds should be safe (False)
        self.assertFalse(metrics.check_safety_circuit_breaker(max_mem_ratio=0.85, max_cpu_ratio=0.85))
        
        # Simulate cgroup memory threshold exceeded
        metrics.memory_bytes["cgroup_limit"] = 1000
        metrics.memory_bytes["rss"] = 900
        self.assertTrue(metrics.check_safety_circuit_breaker(max_mem_ratio=0.85))

    def test_w3c_traceparent(self):
        traceparent = generate_w3c_traceparent()
        # Ensure strict compliance with 00-<32 hex>-<16 hex>-01 format
        pattern = r"^00-[0-9a-f]{32}-[0-9a-f]{16}-01$"
        self.assertIsNotNone(re.match(pattern, traceparent), f"Invalid traceparent: {traceparent}")

    def test_structured_json_logging(self):
        formatter = StructuredJsonFormatter()
        record = logging.LogRecord(
            name="test_logger",
            level=logging.WARNING,
            pathname="test.py",
            lineno=10,
            msg="Simulated warning event",
            args=(),
            exc_info=None
        )
        record.trace_id = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        
        formatted_str = formatter.format(record)
        log_json = json.loads(formatted_str)
        
        self.assertEqual(log_json["severity"], "WARNING")
        self.assertEqual(log_json["component"], "test_logger")
        self.assertEqual(log_json["message"], "Simulated warning event")
        self.assertEqual(log_json["trace_id"], "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
        self.assertIn("timestamp", log_json)

if __name__ == "__main__":
    unittest.main()
