#!/usr/bin/env python3
"""Lightweight In-Container Sandbox Self-Test Suite for Spotter Verification.

Executed against the virtual environment to verify import sanity,
socket/credential permissions, and event loop integrity. Any failure
triggers immediate rollback.
"""

import importlib
import os
import socket
import sys
import tempfile
import unittest
from edge.spotter.src.agent import SpotterDiscoveryManager
from edge.spotter.src.agent import build_endpoint_config


class TestSpotterSelfTest(unittest.TestCase):
  """Self-test cases verifying imports, permissions, and basic agent sanity."""

  def test_imports(self):
    """Verify all essential modules and Spotter dependencies resolve."""
    modules = [
        "ssl",
        "hashlib",
        "base64",
        "subprocess",
        "udmi.schema",
        "udmi.core.factory",
        "edge.spotter.src.agent",
        "edge.spotter.src.pcap",
    ]
    for mod in modules:
      try:
        importlib.import_module(mod)
      except ImportError as e:
        self.fail(f"Staged import dependency failure for {mod}: {e}")

  def test_permissions(self):
    """Verify socket creation capability and read/write filesystem access."""
    try:
      with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.bind(("127.0.0.1", 0))
    except OSError as e:
      self.fail(f"Staged network socket permission failure: {e}")

    staging_dir = os.environ.get("SPOTTER_STAGING_DIR", tempfile.gettempdir())
    try:
      os.makedirs(staging_dir, exist_ok=True)
      test_file = os.path.join(staging_dir, ".self_test_probe")
      with open(test_file, "w", encoding="utf-8") as f:
        f.write("probe")
      os.remove(test_file)
    except OSError as e:
      self.fail(f"Staged filesystem permission failure in {staging_dir}: {e}")

  def test_mock_loop(self):
    """Verify Spotter endpoint config mapping and manager initialization."""
    config = {
        "mqtt": {
            "device_id": "SN-SELFTEST",
            "host": "localhost",
            "port": 18883,
            "registry_id": "ZZ-TRI-FECTA",
            "region": "us-central1",
            "project_id": "test-project",
            "authentication_mechanism": "jwt_gcp",
        }
    }
    try:
      endpoint = build_endpoint_config(config)
      manager = SpotterDiscoveryManager()
      expected_client_id = (
          "projects/test-project/locations/us-central1/registries/ZZ-TRI-FECTA/"
          "devices/SN-SELFTEST"
      )
      self.assertEqual(endpoint.client_id, expected_client_id)
      self.assertIsNotNone(manager)
    except (TypeError, ValueError, KeyError, AttributeError) as e:
      self.fail(f"Staged mock loop initialization crash: {e}")


if __name__ == "__main__":
  result = unittest.main(exit=False)
  sys.exit(0 if result.result.wasSuccessful() else 1)

