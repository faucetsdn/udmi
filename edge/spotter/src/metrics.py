"""Multi-Provider Observability Metrics Exporter for Spotter.

Provides an open-source Prometheus HTTP scrape server on port 9090 and serializers
for native UDMI MQTT event reporting (events/metrics). Tracks CPU quotas, memory
cgroups, file descriptors, PCAP transfer volumes, and OTA rollbacks.
"""

import os
import time
import json
import logging
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Dict, Any

LOGGER = logging.getLogger("spotter_metrics")

class SpotterMetrics:
    def __init__(self, port: int = 9090):
        self._lock = threading.Lock()
        self.port = port
        self.http_server = None
        self._server_thread = None
        
        # Metric catalog counters and gauges
        self.cpu_usage_ratio: Dict[str, float] = {"spotter": 0.0}
        self.memory_bytes: Dict[str, int] = {"rss": 0, "cgroup_limit": 0}
        self.open_fds: int = 0
        self.pcap_packets_total: Dict[str, int] = {"captured": 0, "dropped": 0}
        self.pcap_bytes_transferred: int = 0
        self.ota_events: Dict[str, int] = {"success": 0, "rollback": 0}
        self.mqtt_connected: int = 1
        self.upload_duration_ms: float = 0.0

    def update_resource_usage(self):
        with self._lock:
            try:
                # Count open FDs
                fd_dir = f"/proc/{os.getpid()}/fd"
                if os.path.exists(fd_dir):
                    self.open_fds = len(os.listdir(fd_dir))
            except Exception: # pylint: disable=broad-exception-caught
                pass
            
            try:
                import psutil
                process = psutil.Process()
                mem_info = process.memory_info()
                self.memory_bytes["rss"] = mem_info.rss
                self.cpu_usage_ratio["spotter"] = round(process.cpu_percent() / 100.0, 4)
            except ImportError:
                pass

    def check_safety_circuit_breaker(self, max_mem_ratio: float = 0.85, max_cpu_ratio: float = 0.85) -> bool:
        """Evaluates container cgroup safety limits. Returns True if unsafe/throttled."""
        self.update_resource_usage()
        with self._lock:
            if self.memory_bytes["cgroup_limit"] > 0:
                ratio = self.memory_bytes["rss"] / float(self.memory_bytes["cgroup_limit"])
                if ratio >= max_mem_ratio:
                    LOGGER.warning("Safety Circuit Breaker: Memory ratio %.2f exceeds %.2f threshold!", ratio, max_mem_ratio)
                    return True
            if self.cpu_usage_ratio.get("spotter", 0.0) >= max_cpu_ratio:
                LOGGER.warning("Safety Circuit Breaker: CPU ratio %.2f exceeds %.2f threshold!", self.cpu_usage_ratio["spotter"], max_cpu_ratio)
                return True
        return False

    def record_pcap_transfer(self, captured_packets: int, dropped_packets: int, bytes_sent: int, duration_sec: float):
        with self._lock:
            self.pcap_packets_total["captured"] += captured_packets
            self.pcap_packets_total["dropped"] += dropped_packets
            self.pcap_bytes_transferred += bytes_sent
            self.upload_duration_ms = round(duration_sec * 1000.0, 2)

    def record_ota_event(self, result: str):
        with self._lock:
            if result in self.ota_events:
                self.ota_events[result] += 1

    def set_mqtt_status(self, connected: bool):
        with self._lock:
            self.mqtt_connected = 1 if connected else 0

    def generate_prometheus_payload(self) -> str:
        self.update_resource_usage()
        lines = []
        with self._lock:
            lines.append("# HELP spotter_cpu_usage_ratio CPU utilization ratio vs allocated quota.")
            lines.append("# TYPE spotter_cpu_usage_ratio gauge")
            for proc, val in self.cpu_usage_ratio.items():
                lines.append(f'spotter_cpu_usage_ratio{{process="{proc}"}} {val}')
                
            lines.append("# HELP spotter_memory_bytes Memory consumption vs cgroup memory bounds.")
            lines.append("# TYPE spotter_memory_bytes gauge")
            for mtype, val in self.memory_bytes.items():
                lines.append(f'spotter_memory_bytes{{type="{mtype}"}} {val}')
                
            lines.append("# HELP spotter_open_fds Count of open file descriptors.")
            lines.append("# TYPE spotter_open_fds gauge")
            lines.append(f"spotter_open_fds {self.open_fds}")
            
            lines.append("# HELP spotter_pcap_packets_total Count of network packets captured.")
            lines.append("# TYPE spotter_pcap_packets_total counter")
            for stat, val in self.pcap_packets_total.items():
                lines.append(f'spotter_pcap_packets_total{{status="{stat}"}} {val}')
                
            lines.append("# HELP spotter_pcap_bytes_transferred_total Diagnostic stream volume uploaded via MQTT.")
            lines.append("# TYPE spotter_pcap_bytes_transferred_total counter")
            lines.append(f'spotter_pcap_bytes_transferred_total{{transport="mqtt"}} {self.pcap_bytes_transferred}')
            
            lines.append("# HELP spotter_ota_events_total Outcome counters for staged OTA packages.")
            lines.append("# TYPE spotter_ota_events_total counter")
            for res, val in self.ota_events.items():
                lines.append(f'spotter_ota_events_total{{result="{res}"}} {val}')
                
            lines.append("# HELP spotter_mqtt_connection_status Broker connectivity indicator.")
            lines.append("# TYPE spotter_mqtt_connection_status gauge")
            lines.append(f"spotter_mqtt_connection_status {self.mqtt_connected}")
        return "\n".join(lines) + "\n"

    def generate_udmi_payload(self) -> Dict[str, Any]:
        self.update_resource_usage()
        with self._lock:
            return {
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "metrics": {
                    "cpu_ratio": self.cpu_usage_ratio,
                    "memory_bytes": self.memory_bytes,
                    "open_fds": self.open_fds,
                    "pcap_packets_total": self.pcap_packets_total,
                    "pcap_bytes_transferred_mqtt": self.pcap_bytes_transferred,
                    "ota_events_total": self.ota_events,
                    "mqtt_connected": self.mqtt_connected
                }
            }

    def start_http_server(self):
        try:
            handler_cls = _create_handler(self)
            self.http_server = HTTPServer(("0.0.0.0", self.port), handler_cls)
            self._server_thread = threading.Thread(target=self.http_server.serve_forever, name="MetricsHTTP", daemon=True)
            self._server_thread.start()
            LOGGER.info("Prometheus metrics HTTP scrape server started on port %d", self.port)
        except OSError as e:
            LOGGER.warning("Could not bind metrics server to port %d: %s", self.port, e)

    def stop_http_server(self):
        if self.http_server:
            self.http_server.shutdown()
            self.http_server.server_close()
            self.http_server = None

def _create_handler(metrics_instance: SpotterMetrics):
    class MetricsHTTPRequestHandler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == "/metrics":
                payload = metrics_instance.generate_prometheus_payload().encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            else:
                self.send_response(404)
                self.end_headers()
        def log_message(self, format, *args):
            pass  # Suppress request stdout chatter
    return MetricsHTTPRequestHandler
