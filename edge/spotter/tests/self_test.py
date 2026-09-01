#!/usr/bin/env python3
"""Lightweight In-Container Sandbox Self-Test Suite for Spotter OTA Verification.

Executed by the supervisor post-OTA staging against the staged virtual environment
to verify import sanity, socket/credential permissions, and event loop integrity before
promoting the active symlink. Any failure triggers immediate rollback.
"""

import os
import sys
import socket
import tempfile
import unittest

class TestSpotterSelfTest(unittest.TestCase):

    def test_imports(self):
        """Verify all essential python modules and Spotter dependencies resolve cleanly."""
        try:
            import ssl
            import hashlib
            import base64
            import subprocess
            from udmi.schema import Config, State, StreamEvents
            from udmi.core.factory import create_device
            from edge.spotter.src import agent, pcap
        except ImportError as e:
            self.fail(f"Staged import dependency failure: {e}")

    def test_permissions(self):
        """Verify socket creation capability and read/write access to staging filesystem."""
        # Check basic networking socket creation
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.bind(("127.0.0.1", 0))
        except OSError as e:
            self.fail(f"Staged network socket permission failure: {e}")

        # Check write access to staging / temporary directories
        staging_dir = os.environ.get("SPOTTER_STAGING_DIR", tempfile.gettempdir())
        try:
            os.makedirs(staging_dir, exist_ok=True)
            test_file = os.path.join(staging_dir, ".self_test_probe")
            with open(test_file, "w") as f:
                f.write("probe")
            os.remove(test_file)
        except OSError as e:
            self.fail(f"Staged filesystem permission failure in {staging_dir}: {e}")

    def test_mock_loop(self):
        """Verify Spotter endpoint config mapping and manager initialization without crash."""
        from edge.spotter.src.agent import build_endpoint_config, SpotterDiscoveryManager
        config = {
            "mqtt": {
                "device_id": "SN-SELFTEST",
                "host": "localhost",
                "port": 18883,
                "registry_id": "ZZ-TRI-FECTA",
                "region": "us-central1",
                "project_id": "test-project",
                "authentication_mechanism": "jwt_gcp"
            }
        }
        try:
            endpoint = build_endpoint_config(config)
            manager = SpotterDiscoveryManager()
            self.assertEqual(endpoint.client_id, "projects/test-project/locations/us-central1/registries/ZZ-TRI-FECTA/devices/SN-SELFTEST")
            self.assertIsNotNone(manager)
        except Exception as e:
            self.fail(f"Staged mock loop initialization crash: {e}")

if __name__ == "__main__":
    result = unittest.main(exit=False)
    sys.exit(0 if result.result.wasSuccessful() else 1)
