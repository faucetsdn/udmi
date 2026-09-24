#!/usr/bin/env python3
"""Tests for Spotter site-model configuration generator utility."""

import json
import os
import shutil
import tempfile
import unittest

from edge.spotter.src.config_generator import generate_spotter_config
from edge.spotter.src.config_generator import parse_project_spec


class TestConfigGenerator(unittest.TestCase):
  """Unit tests for configuration generator functions."""

  def setUp(self):
    self.test_dir = tempfile.mkdtemp()
    self.site_path = os.path.join(self.test_dir, "sites", "test_site")
    os.makedirs(self.site_path, exist_ok=True)

    site_config = {
        "project_id": "test-project",
        "registry_id": "ZZ-TRI-FECTA",
        "cloud_region": "us-central1",
    }
    with open(
        os.path.join(self.site_path, "cloud_iot_config.json"),
        "w",
        encoding="utf-8",
    ) as f:
      json.dump(site_config, f)

    device_dir = os.path.join(self.site_path, "devices", "DSN-1")
    os.makedirs(device_dir, exist_ok=True)
    with open(
        os.path.join(device_dir, "rsa_private.pem"), "w", encoding="utf-8"
    ) as f:
      f.write("mock-key")
    with open(
        os.path.join(device_dir, "rsa_private.crt"), "w", encoding="utf-8"
    ) as f:
      f.write("mock-cert")

  def tearDown(self):
    shutil.rmtree(self.test_dir)

  def test_parse_project_spec_local_mqtt(self):
    """Verifies parsing of local mqtt project spec."""
    site_config = {"registry_id": "ZZ-TRI-FECTA", "cloud_region": "us-central1"}
    parsed = parse_project_spec("//mqtt/localhost:46432", site_config)
    self.assertEqual(parsed["iot_provider"], "mqtt")
    self.assertEqual(parsed["host"], "localhost")
    self.assertEqual(parsed["port"], 46432)
    self.assertEqual(parsed["auth_mechanism"], "udmi_local")

  def test_parse_project_spec_clearblade(self):
    """Verifies parsing of ClearBlade GCP IoT project spec."""
    site_config = {"registry_id": "ZZ-TRI-FECTA", "cloud_region": "us-central1"}
    parsed = parse_project_spec(
        "//clearblade/bos-platform-dev/my-namespace", site_config
    )
    self.assertEqual(parsed["iot_provider"], "clearblade")
    self.assertEqual(parsed["host"], "us-central1-mqtt.clearblade.com")
    self.assertEqual(parsed["port"], 8883)
    self.assertEqual(parsed["auth_mechanism"], "jwt_gcp")
    self.assertEqual(parsed["registry_id"], "my-namespace~ZZ-TRI-FECTA")

  def test_generate_spotter_config(self):
    """Verifies configuration file generation from site model files."""
    out_path = os.path.join(self.test_dir, "out", "spotter_config.json")
    config = generate_spotter_config(
        site_path=self.site_path,
        project_spec="//mqtt/localhost:18883",
        device_id="DSN-1",
        out_path=out_path,
    )

    self.assertEqual(config["mqtt"]["device_id"], "DSN-1")
    self.assertEqual(config["mqtt"]["port"], 18883)
    self.assertEqual(config["system"]["metrics_rate_sec"], 300)
    self.assertTrue(os.path.isfile(out_path))

    # Test custom metrics_rate_sec override
    config_override = generate_spotter_config(
        site_path=self.site_path,
        project_spec="//mqtt/localhost:18883",
        device_id="DSN-1",
        metrics_rate_sec=10,
    )
    self.assertEqual(config_override["system"]["metrics_rate_sec"], 10)
    self.assertEqual(config_override["mqtt"]["algorithm"], "RS256")
    self.assertEqual(config_override["bacnet"], {})

  def test_generate_spotter_config_ec_key(self):
    """Verifies ES256 algorithm selection when EC key is found."""
    ec_device_dir = os.path.join(self.site_path, "devices", "EC-1")
    os.makedirs(ec_device_dir, exist_ok=True)
    with open(
        os.path.join(ec_device_dir, "ec_private.pem"), "w", encoding="utf-8"
    ) as f:
      f.write("mock-ec-key")

    config = generate_spotter_config(
        site_path=self.site_path,
        project_spec="//mqtt/localhost:18883",
        device_id="EC-1",
    )
    self.assertEqual(config["mqtt"]["algorithm"], "ES256")

  def test_generate_spotter_config_bacnet_ip_override(self):
    """Verifies explicit bacnet_ip argument override in config."""
    config = generate_spotter_config(
        site_path=self.site_path,
        project_spec="//mqtt/localhost:18883",
        device_id="DSN-1",
        bacnet_ip="192.168.1.5",
    )
    self.assertEqual(config["bacnet"]["ip"], "192.168.1.5")

  def test_generate_spotter_config_bacnet_ip_from_metadata(self):
    """Verifies bacnet IP extraction from device metadata.json."""
    device_dir = os.path.join(self.site_path, "devices", "DSN-1")
    metadata = {
        "localnet": {
            "families": {
                "bacnet": {
                    "ip": "10.0.0.42"
                }
            }
        }
    }
    with open(
        os.path.join(device_dir, "metadata.json"), "w", encoding="utf-8"
    ) as f:
      json.dump(metadata, f)

    config = generate_spotter_config(
        site_path=self.site_path,
        project_spec="//mqtt/localhost:18883",
        device_id="DSN-1",
    )
    self.assertEqual(config["bacnet"]["ip"], "10.0.0.42")


if __name__ == "__main__":
  unittest.main()

