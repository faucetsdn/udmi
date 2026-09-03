#!/usr/bin/env python3
"""Unit tests for Spotter Agent configuration, managers, and discovery."""

import hashlib
import os
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from edge.spotter.src.agent import build_endpoint_config
from edge.spotter.src.agent import calculate_local_password
from edge.spotter.src.agent import wait_for_broker_readiness
from edge.spotter.src.manager.discovery import SpotterDiscoveryManager
from edge.spotter.src.manager.system import SpotterSystemManager
from udmi.schema import Config
from udmi.schema import Depth
from udmi.schema import DiscoveryConfig
from udmi.schema import FamilyDiscoveryConfig
from udmi.schema import FamilyDiscoveryState
from udmi.schema import Protocol
from udmi.schema import SystemConfig
from udmi.schema import TraceDiscoveryConfig
from udmi.schema.state_discovery_family import Phase as DiscoveryPhase


class TestAgentConfig(unittest.TestCase):
  """Tests for agent configuration parsing and password calculation."""

  def setUp(self):
    # pylint: disable=consider-using-with
    self.test_dir = tempfile.TemporaryDirectory()

    # Create a mock private key
    self.key_file = os.path.join(self.test_dir.name, "rsa_private.pem")
    with open(self.key_file, "wb") as f:
      f.write(b"MOCK PRIVATE KEY DATA")

    # Create pkcs8 version as well
    self.pkcs8_file = os.path.join(self.test_dir.name, "rsa_private.pkcs8")
    with open(self.pkcs8_file, "wb") as f:
      f.write(b"MOCK PKCS8 KEY DATA")

  def tearDown(self):
    self.test_dir.cleanup()

  def test_calculate_local_password_from_pkcs8(self):
    """Verifies password derivation from pkcs8 key file."""
    h = hashlib.sha256(b"MOCK PKCS8 KEY DATA").hexdigest()[:8]
    pwd = calculate_local_password(self.key_file)
    self.assertEqual(pwd, h)

  def test_calculate_local_password_fallback(self):
    """Verifies password derivation falls back to PEM when pkcs8 missing."""
    os.remove(self.pkcs8_file)
    h = hashlib.sha256(b"MOCK PRIVATE KEY DATA").hexdigest()[:8]
    pwd = calculate_local_password(self.key_file)
    self.assertEqual(pwd, h)

  def test_build_endpoint_config_udmi_local(self):
    """Verifies endpoint configuration under udmi_local authentication."""
    config = {
        "mqtt": {
            "device_id": "AHU-1",
            "host": "127.0.0.1",
            "port": "8883",
            "registry_id": "ZZ-TRI-FECTA",
            "key_file": self.key_file,
            "cert_file": "/path/to/cert.crt",
            "ca_file": "/path/to/ca.crt",
            "algorithm": "RS256",
            "authentication_mechanism": "udmi_local",
        }
    }

    endpoint = build_endpoint_config(config)

    self.assertEqual(endpoint.hostname, "127.0.0.1")
    self.assertEqual(endpoint.port, 8883)
    self.assertEqual(endpoint.client_id, "/r/ZZ-TRI-FECTA/d/AHU-1")
    self.assertEqual(endpoint.topic_prefix, "/r/ZZ-TRI-FECTA/d/")
    self.assertEqual(endpoint.algorithm, "RS256")
    self.assertEqual(endpoint.key_file, self.key_file)
    self.assertEqual(endpoint.cert_file, "/path/to/cert.crt")
    self.assertEqual(endpoint.ca_file, "/path/to/ca.crt")
    self.assertEqual(endpoint.protocol, Protocol.mqtt)

    self.assertIsNotNone(endpoint.auth_provider)
    self.assertIsNotNone(endpoint.auth_provider.basic)
    self.assertEqual(
        endpoint.auth_provider.basic.username, "/r/ZZ-TRI-FECTA/d/AHU-1"
    )

    h = hashlib.sha256(b"MOCK PKCS8 KEY DATA").hexdigest()[:8]
    self.assertEqual(endpoint.auth_provider.basic.password, h)

  def test_build_endpoint_config_relative_paths(self):
    """Verifies relative path resolution against base directory."""
    config = {
        "mqtt": {
            "device_id": "AHU-1",
            "host": "127.0.0.1",
            "port": "8883",
            "registry_id": "ZZ-TRI-FECTA",
            "key_file": "rsa_private.pem",
            "cert_file": "certs/rsa_private.crt",
            "ca_file": "../reflector/ca.crt",
            "algorithm": "RS256",
            "authentication_mechanism": "udmi_local",
        }
    }
    endpoint = build_endpoint_config(config, base_dir=self.test_dir.name)
    self.assertEqual(
        endpoint.key_file, os.path.join(self.test_dir.name, "rsa_private.pem")
    )
    self.assertEqual(
        endpoint.cert_file,
        os.path.join(self.test_dir.name, "certs", "rsa_private.crt"),
    )
    expected_ca = os.path.normpath(
        os.path.join(self.test_dir.name, "..", "reflector", "ca.crt")
    )
    self.assertEqual(endpoint.ca_file, expected_ca)

  def test_build_endpoint_config_jwt_gcp(self):
    """Verifies endpoint configuration under jwt_gcp authentication."""
    config = {
        "mqtt": {
            "device_id": "AHU-1",
            "host": "mqtt.googleapis.com",
            "port": "443",
            "registry_id": "ZZ-TRI-FECTA",
            "region": "us-central1",
            "project_id": "my-project",
            "key_file": self.key_file,
            "algorithm": "RS256",
            "authentication_mechanism": "jwt_gcp",
        }
    }

    endpoint = build_endpoint_config(config)

    self.assertEqual(endpoint.hostname, "mqtt.googleapis.com")
    self.assertEqual(endpoint.port, 443)
    expected_id = (
        "projects/my-project/locations/us-central1/registries/ZZ-TRI-FECTA/"
        "devices/AHU-1"
    )
    self.assertEqual(endpoint.client_id, expected_id)
    self.assertEqual(endpoint.topic_prefix, "/devices/")
    self.assertEqual(endpoint.algorithm, "RS256")
    self.assertIsNotNone(endpoint.auth_provider)
    self.assertEqual(endpoint.auth_provider.jwt.audience, "my-project")

  def test_build_endpoint_config_custom_client_id(self):
    """Verifies explicit client_id config override."""
    config = {
        "mqtt": {
            "device_id": "AHU-1",
            "host": "localhost",
            "port": 1883,
            "registry_id": "ZZ-TRI-FECTA",
            "key_file": self.key_file,
            "authentication_mechanism": "udmi_local",
            "client_id": "/r/ZZ-TRI-FECTA/d/custom-spotter-id",
        }
    }

    endpoint = build_endpoint_config(config)
    self.assertEqual(endpoint.client_id, "/r/ZZ-TRI-FECTA/d/custom-spotter-id")

  def test_metrics_rate_sec_default_and_override(self):
    """Verifies system metrics rate default and dynamic update."""
    # pylint: disable=protected-access
    # 1. Default initialization (300s, 85% memory threshold)
    mgr_default = SpotterSystemManager()
    self.assertEqual(mgr_default._metrics_rate_sec, 300)
    self.assertEqual(mgr_default.max_mem_pct, 85.0)

    # 2. Custom initialization override
    mgr_custom = SpotterSystemManager(max_mem_pct=75.0, metrics_rate_sec=15)
    self.assertEqual(mgr_custom._metrics_rate_sec, 15)
    self.assertEqual(mgr_custom.max_mem_pct, 75.0)

    # 3. Dynamic update via UDMI Config message
    config_update = Config(system=SystemConfig(metrics_rate_sec=60))
    mgr_custom.handle_config(config_update)
    self.assertEqual(mgr_custom._metrics_rate_sec, 60)

  @patch("edge.spotter.src.manager.system.get_cpu_and_memory_metrics")
  def test_spotter_system_manager_publish_metrics(self, mock_metrics):
    """Verifies metrics publishing with host resource metrics."""
    mock_metrics.return_value = {
        "mem_total_mb": 4096.0,
        "mem_free_mb": 2048.0,
        "mem_used_pct": 50.0,
        "load_1m": 0.25,
        "load_5m": 0.50,
        "load_15m": 0.75,
    }
    mgr = SpotterSystemManager(max_mem_pct=80.0)
    mock_publish = MagicMock()
    mgr.publish_event = mock_publish

    mgr.publish_metrics()

    self.assertTrue(mock_publish.called)
    event, category = mock_publish.call_args[0]
    self.assertEqual(category, "system")
    self.assertEqual(event.metrics.mem_total_mb, 4096.0)
    self.assertEqual(event.metrics.mem_free_mb, 2048.0)
    self.assertEqual(event.metrics.system_load, 0.25)

  @patch("edge.spotter.src.manager.system.check_safety_circuit_breaker")
  @patch("edge.spotter.src.manager.system.get_cpu_and_memory_metrics")
  def test_spotter_system_manager_circuit_breaker_alert(
      self, mock_metrics, mock_cb
  ):
    """Verifies safety circuit breaker evaluation during metrics publish."""
    mock_metrics.return_value = {
        "mem_total_mb": 4096.0,
        "mem_free_mb": 200.0,
        "load_1m": 1.5,
    }
    mock_cb.return_value = True
    mgr = SpotterSystemManager(max_mem_pct=80.0)
    mgr.publish_event = MagicMock()

    mgr.publish_metrics()
    mock_cb.assert_called_once_with(80.0)

  @patch("socket.create_connection")
  def test_wait_for_broker_readiness_immediate_success(self, mock_create_conn):
    """Verifies immediate readiness check when port is open."""
    mock_sock = MagicMock()
    mock_create_conn.return_value = mock_sock
    res = wait_for_broker_readiness("localhost", 18883, timeout_sec=5)
    self.assertTrue(res)
    mock_create_conn.assert_called_once_with(("localhost", 18883), timeout=2.0)

  @patch("time.sleep")
  @patch("socket.create_connection")
  def test_wait_for_broker_readiness_retries_and_succeeds(
      self, mock_create_conn, mock_sleep
  ):
    """Verifies broker retry loop until socket connection succeeds."""
    mock_sock = MagicMock()
    mock_create_conn.side_effect = [
        ConnectionRefusedError("Port closed"),
        mock_sock,
    ]
    res = wait_for_broker_readiness("localhost", 18883, timeout_sec=5)
    self.assertTrue(res)
    self.assertEqual(mock_create_conn.call_count, 2)
    mock_sleep.assert_called_once_with(1)

  @patch("time.sleep")
  @patch("socket.create_connection")
  def test_wait_for_broker_readiness_timeout_failure(
      self, mock_create_conn, mock_sleep
  ):
    """Verifies false result when broker readiness check times out."""
    # pylint: disable=unused-argument
    mock_create_conn.side_effect = ConnectionRefusedError("Unreachable")
    with patch("time.time", side_effect=[100.0, 101.0, 102.5]):
      res = wait_for_broker_readiness("localhost", 18883, timeout_sec=2)
      self.assertFalse(res)


# pylint: disable=protected-access
class TestSpotterDiscoveryManager(unittest.TestCase):
  """Tests for SpotterDiscoveryManager execution and state tracking."""

  def setUp(self):
    self.manager = SpotterDiscoveryManager()
    self.mock_device = MagicMock()
    self.mock_dispatcher = MagicMock()
    self.mock_device.dispatcher = self.mock_dispatcher
    self.manager._device = self.mock_device
    self.manager._dispatcher = self.mock_dispatcher

  @patch("edge.spotter.src.manager.discovery.capture_packets")
  def test_run_trace_worker_streaming(self, mock_capture):
    """Verifies packet streaming chunk emission during trace capture."""
    mock_capture.return_value = [b"MOCK_PACKET_HEADER", b"MOCK_PACKET_BODY"]

    fam_config = FamilyDiscoveryConfig(
        generation="2026-07-08T14:35:00Z",
        depth=Depth.trace,
        trace=TraceDiscoveryConfig(
            interface="any",
            filter="udp port 47808",
        ),
        scan_duration_sec=2,
    )

    self.manager._discovery_state.families["ether"] = MagicMock()
    self.manager._run_trace_capture("ether", fam_config)

    self.assertTrue(self.mock_dispatcher.publish_event.called)
    call_args = self.mock_dispatcher.publish_event.call_args[0]
    channel = call_args[0]
    event_model = call_args[1]

    self.assertEqual(channel, "events/stream")
    self.assertEqual(event_model.event_no, 0)
    self.assertEqual(event_model.chunk_index, 0)
    self.assertEqual(event_model.total_chunks, 1)
    self.assertIn("trace-ether-", event_model.session_id)

    f_state = self.manager._discovery_state.families["ether"]
    self.assertEqual(f_state.phase, DiscoveryPhase.stopped)
    self.assertEqual(f_state.status.level, 200)

  @patch("edge.spotter.src.manager.discovery.capture_packets")
  def test_run_trace_worker_failure(self, mock_capture):
    """Verifies state error propagation on trace capture failure."""
    mock_capture.side_effect = RuntimeError("Network interface down")

    fam_config = FamilyDiscoveryConfig(
        generation="2026-07-08T14:35:00Z",
        depth=Depth.trace,
        trace=TraceDiscoveryConfig(
            interface="eth0",
            filter="udp port 47808",
        ),
        scan_duration_sec=2,
    )

    self.manager._discovery_state.families["ether"] = MagicMock()
    self.manager._run_trace_capture("ether", fam_config)

    f_state = self.manager._discovery_state.families["ether"]
    self.assertEqual(f_state.phase, DiscoveryPhase.stopped)
    self.assertEqual(f_state.status.level, 500)
    self.assertIn("Network interface down", f_state.status.message)

  @patch("edge.spotter.src.manager.discovery.capture_packets")
  def test_run_scan_trace_cleans_up_active_providers(self, mock_capture):
    """Verifies provider eviction and active state reset after trace scan."""
    mock_capture.return_value = [b"TRACE_DATA"]
    mock_provider = MagicMock()
    self.manager._active_providers.append(mock_provider)

    old_state = FamilyDiscoveryState(generation="old-gen")
    old_state.active = True
    self.manager._discovery_state.families["ether"] = old_state

    fam_config = FamilyDiscoveryConfig(
        generation="new-gen-2026",
        depth=Depth.trace,
        trace=TraceDiscoveryConfig(interface="any", filter=""),
        scan_duration_sec=1,
    )
    self.manager._config = DiscoveryConfig(families={"ether": fam_config})

    self.manager._run_scan("ether", mock_provider)

    self.assertNotIn(mock_provider, self.manager._active_providers)
    self.manager.stop()
    mock_provider.stop_scan.assert_not_called()

    f_state = self.manager._discovery_state.families["ether"]
    self.assertFalse(f_state.active)
    self.assertEqual(f_state.generation, "new-gen-2026")
    self.assertEqual(f_state.phase, DiscoveryPhase.stopped)

  @patch("edge.spotter.src.manager.discovery.check_safety_circuit_breaker")
  def test_run_scan_circuit_breaker_throttles_execution(self, mock_cb):
    """Verifies scan throttling when memory safety circuit breaker trips."""
    mock_cb.return_value = True
    mock_provider = MagicMock()
    self.manager._active_providers.append(mock_provider)

    self.manager._run_scan("bacnet", mock_provider)

    mock_provider.start_scan.assert_not_called()
    self.assertNotIn(mock_provider, self.manager._active_providers)

    f_state = self.manager._discovery_state.families["bacnet"]
    self.assertFalse(f_state.active)
    self.assertEqual(f_state.phase, DiscoveryPhase.stopped)
    self.assertEqual(f_state.status.level, 400)
    self.assertIn(
        "Scan throttled by safety circuit breaker", f_state.status.message
    )

  @patch("edge.spotter.src.manager.discovery.check_safety_circuit_breaker")
  @patch("edge.spotter.src.manager.discovery.capture_packets")
  def test_run_trace_capture_circuit_breaker_throttles(
      self, mock_capture, mock_cb
  ):
    """Verifies trace capture throttling when circuit breaker trips."""
    mock_cb.return_value = True
    fam_config = FamilyDiscoveryConfig(
        generation="gen-trace", depth=Depth.trace, scan_duration_sec=5
    )

    self.manager._run_trace_capture("bacnet", fam_config)

    mock_capture.assert_not_called()
    f_state = self.manager._discovery_state.families["bacnet"]
    self.assertFalse(f_state.active)
    self.assertEqual(f_state.phase, DiscoveryPhase.stopped)
    self.assertEqual(f_state.status.level, 400)
    self.assertIn("throttled by safety circuit breaker", f_state.status.message)


if __name__ == "__main__":
  unittest.main()


