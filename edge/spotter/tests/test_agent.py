import json
import os
import tempfile
import unittest
from unittest.mock import patch, mock_open, MagicMock

from edge.spotter.src.agent import build_endpoint_config, calculate_local_password, TraceDiscoveryManager
from udmi.schema import Protocol, Config, DiscoveryConfig, FamilyDiscoveryConfig, Depth, State
from udmi.schema.state_discovery_family import Phase as DiscoveryPhase

class TestAgentConfig(unittest.TestCase):
    
    def setUp(self):
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
        # Should read the pkcs8 file and return first 8 chars of its sha256
        import hashlib
        h = hashlib.sha256(b"MOCK PKCS8 KEY DATA").hexdigest()[:8]
        
        pwd = calculate_local_password(self.key_file)
        self.assertEqual(pwd, h)

    def test_calculate_local_password_fallback(self):
        # Remove pkcs8 file, should fall back to pem file
        os.remove(self.pkcs8_file)
        
        import hashlib
        h = hashlib.sha256(b"MOCK PRIVATE KEY DATA").hexdigest()[:8]
        
        pwd = calculate_local_password(self.key_file)
        self.assertEqual(pwd, h)

    def test_build_endpoint_config_udmi_local(self):
        config = {
            "mqtt": {
                "device_id": "DN-1",
                "spotter_device_id": "SN-1",
                "host": "127.0.0.1",
                "port": "8883",
                "registry_id": "ZZ-TRI-FECTA",
                "key_file": self.key_file,
                "cert_file": "/path/to/cert.crt",
                "ca_file": "/path/to/ca.crt",
                "algorithm": "RS256",
                "authentication_mechanism": "udmi_local"
            }
        }
        
        endpoint = build_endpoint_config(config)
        
        self.assertEqual(endpoint.hostname, "127.0.0.1")
        self.assertEqual(endpoint.port, 8883)
        self.assertEqual(endpoint.client_id, "/r/ZZ-TRI-FECTA/d/SN-1")
        self.assertEqual(endpoint.topic_prefix, "/r/ZZ-TRI-FECTA/d/")
        self.assertEqual(endpoint.algorithm, "RS256")
        self.assertEqual(endpoint.key_file, self.key_file)
        self.assertEqual(endpoint.cert_file, "/path/to/cert.crt")
        self.assertEqual(endpoint.ca_file, "/path/to/ca.crt")
        self.assertEqual(endpoint.protocol, Protocol.mqtt)
        
        # Check basic auth
        self.assertIsNotNone(endpoint.auth_provider)
        self.assertIsNotNone(endpoint.auth_provider.basic)
        self.assertEqual(endpoint.auth_provider.basic.username, "/r/ZZ-TRI-FECTA/d/SN-1")
        
        # Password should match pkcs8 sha256 prefix
        import hashlib
        h = hashlib.sha256(b"MOCK PKCS8 KEY DATA").hexdigest()[:8]
        self.assertEqual(endpoint.auth_provider.basic.password, h)

    def test_build_endpoint_config_jwt_gcp(self):
        config = {
            "mqtt": {
                "device_id": "DN-1",
                "spotter_device_id": "SN-1",
                "host": "mqtt.googleapis.com",
                "port": "443",
                "registry_id": "ZZ-TRI-FECTA",
                "region": "us-central1",
                "project_id": "my-project",
                "key_file": self.key_file,
                "algorithm": "RS256",
                "authentication_mechanism": "jwt_gcp"
            }
        }
        
        endpoint = build_endpoint_config(config)
        
        self.assertEqual(endpoint.hostname, "mqtt.googleapis.com")
        self.assertEqual(endpoint.port, 443)
        self.assertEqual(endpoint.client_id, "projects/my-project/locations/us-central1/registries/ZZ-TRI-FECTA/devices/SN-1")
        self.assertEqual(endpoint.topic_prefix, "/devices/")
        self.assertEqual(endpoint.algorithm, "RS256")
        self.assertIsNotNone(endpoint.auth_provider)
        self.assertEqual(endpoint.auth_provider.jwt.audience, "my-project")

    def test_build_endpoint_config_custom_client_id(self):
        config = {
            "mqtt": {
                "device_id": "DN-1",
                "spotter_device_id": "SN-1",
                "host": "localhost",
                "port": 1883,
                "registry_id": "ZZ-TRI-FECTA",
                "key_file": self.key_file,
                "authentication_mechanism": "udmi_local",
                "spotter_client_id": "/r/ZZ-TRI-FECTA/d/custom-spotter-id"
            }
        }
        
        endpoint = build_endpoint_config(config)
        self.assertEqual(endpoint.client_id, "/r/ZZ-TRI-FECTA/d/custom-spotter-id")

    def test_build_endpoint_config_spotter_device_id(self):
        config = {
            "mqtt": {
                "device_id": "DN-1",
                "spotter_device_id": "SN-1",
                "host": "localhost",
                "port": 18883,
                "registry_id": "ZZ-TRI-FECTA",
                "key_file": self.key_file,
                "authentication_mechanism": "udmi_local"
            }
        }
        
        endpoint = build_endpoint_config(config)
        self.assertEqual(endpoint.client_id, "/r/ZZ-TRI-FECTA/d/SN-1")
        self.assertEqual(endpoint.auth_provider.basic.username, "/r/ZZ-TRI-FECTA/d/SN-1")


class TestTraceDiscoveryManager(unittest.TestCase):

    def setUp(self):
        self.manager = TraceDiscoveryManager()
        self.mock_device = MagicMock()
        self.mock_dispatcher = MagicMock()
        self.mock_device.dispatcher = self.mock_dispatcher
        self.manager._device = self.mock_device
        self.manager._dispatcher = self.mock_dispatcher

    def test_ignore_non_trace_discovery_family(self):
        # Config with standard discovery scan (depth='entries')
        fam_config = FamilyDiscoveryConfig(generation="2026-07-08T14:35:00Z", depth=Depth.entries)
        config = Config(discovery=DiscoveryConfig(families={"bacnet": fam_config}))
        
        self.manager.handle_config(config)
        
        # Should be ignored by Spotter (no threads started, state untouched)
        self.assertEqual(len(self.manager._active_threads), 0)
        self.assertEqual(len(self.manager._discovery_state.families), 0)

    @patch("edge.spotter.src.agent.TraceDiscoveryManager._run_trace_worker")
    def test_trigger_trace_discovery_family(self, mock_worker):
        # Config with TRACE discovery (depth='trace')
        fam_config = FamilyDiscoveryConfig(
            generation="2026-07-08T14:35:00Z",
            depth=Depth.trace,
            interface="eth0",
            filter="udp port 47808",
            scan_duration_sec=5
        )
        config = Config(discovery=DiscoveryConfig(families={"ether": fam_config}))
        
        self.manager.handle_config(config)
        
        # State should immediately transition to active
        self.assertIn("ether", self.manager._discovery_state.families)
        f_state = self.manager._discovery_state.families["ether"]
        self.assertEqual(f_state.phase, DiscoveryPhase.active)
        self.assertEqual(f_state.generation, "2026-07-08T14:35:00Z")

    @patch("edge.spotter.src.pcap.capture_packets")
    def test_run_trace_worker_streaming(self, mock_capture):
        # Simulate packet capture yielding binary chunks
        mock_capture.return_value = [b"MOCK_PACKET_HEADER", b"MOCK_PACKET_BODY"]
        
        fam_config = FamilyDiscoveryConfig(
            generation="2026-07-08T14:35:00Z",
            depth=Depth.trace,
            interface="any",
            filter="udp port 47808",
            scan_duration_sec=2
        )
        
        # Prepare initial state
        self.manager._discovery_state.families["ether"] = MagicMock()
        self.manager._run_trace_worker("ether", fam_config)
        
        # Verify publish_event was called to emit StreamEvents on channel 'stream' (prefixed as 'events/stream' by BaseManager)
        self.assertTrue(self.mock_dispatcher.publish_event.called)
        call_args = self.mock_dispatcher.publish_event.call_args[0]
        channel = call_args[0]
        event_model = call_args[1]
        
        self.assertEqual(channel, "events/stream")
        self.assertEqual(event_model.event_no, 0)
        self.assertEqual(event_model.chunk_index, 0)
        self.assertEqual(event_model.total_chunks, 1)
        self.assertIn("trace-ether-", event_model.session_id)
        
        # State should terminate in stopped phase with success Level 200
        f_state = self.manager._discovery_state.families["ether"]
        self.assertEqual(f_state.phase, DiscoveryPhase.stopped)
        self.assertEqual(f_state.status.level, 200)

    @patch("edge.spotter.src.pcap.capture_packets")
    def test_run_trace_worker_failure(self, mock_capture):
        # Simulate packet capture raising an exception
        mock_capture.side_effect = RuntimeError("Network interface down")
        
        fam_config = FamilyDiscoveryConfig(
            generation="2026-07-08T14:35:00Z",
            depth=Depth.trace,
            interface="eth0",
            filter="udp port 47808",
            scan_duration_sec=2
        )
        
        self.manager._discovery_state.families["ether"] = MagicMock()
        self.manager._run_trace_worker("ether", fam_config)
        
        # State should terminate in stopped phase with error Level 500
        f_state = self.manager._discovery_state.families["ether"]
        self.assertEqual(f_state.phase, DiscoveryPhase.stopped)
        self.assertEqual(f_state.status.level, 500)
        self.assertIn("Network interface down", f_state.status.message)


class TestOtaHandlers(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.test_dir.cleanup()

    def test_process_ota_package_staging(self):
        from edge.spotter.src.agent import process_ota_package
        dummy_whl = os.path.join(self.test_dir.name, "update-1.0.whl")
        with open(dummy_whl, "wb") as f:
            f.write(b"MOCK WHEEL DATA")
            
        with patch.dict("os.environ", {"SPOTTER_STAGING_DIR": self.test_dir.name}):
            res = process_ota_package("ota_package", dummy_whl)
            self.assertEqual(res, "staged")
            staged_path = os.path.join(self.test_dir.name, "update-1.0.whl")
            self.assertTrue(os.path.exists(staged_path))
            marker = os.path.join(self.test_dir.name, "OTA_STAGED")
            self.assertTrue(os.path.exists(marker))

    def test_process_discovery_rules_hot_reload(self):
        from edge.spotter.src.agent import process_discovery_rules
        rules_file = os.path.join(self.test_dir.name, "rules.json")
        with open(rules_file, "w") as f:
            f.write('{"bacnet_vendor_ids": [10, 25]}')
            
        res = process_discovery_rules("discovery_rules", rules_file)
        self.assertEqual(res, "reloaded")

if __name__ == "__main__":
    unittest.main()
