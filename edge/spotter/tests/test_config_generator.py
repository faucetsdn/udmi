import json
import os
import shutil
import tempfile
import unittest

from edge.spotter.src.config_generator import generate_spotter_config, parse_project_spec


class TestConfigGenerator(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.site_path = os.path.join(self.test_dir, "sites", "test_site")
        os.makedirs(self.site_path, exist_ok=True)

        site_config = {
            "project_id": "test-project",
            "registry_id": "ZZ-TRI-FECTA",
            "cloud_region": "us-central1",
        }
        with open(os.path.join(self.site_path, "cloud_iot_config.json"), "w") as f:
            json.dump(site_config, f)

        device_dir = os.path.join(self.site_path, "devices", "DN-1")
        os.makedirs(device_dir, exist_ok=True)
        with open(os.path.join(device_dir, "rsa_private.pem"), "w") as f:
            f.write("mock-key")
        with open(os.path.join(device_dir, "rsa_private.crt"), "w") as f:
            f.write("mock-cert")

    def tearDown(self):
        shutil.rmtree(self.test_dir)

    def test_parse_project_spec_local_mqtt(self):
        site_config = {"registry_id": "ZZ-TRI-FECTA", "cloud_region": "us-central1"}
        parsed = parse_project_spec("//mqtt/localhost:46432", site_config)
        self.assertEqual(parsed["iot_provider"], "mqtt")
        self.assertEqual(parsed["host"], "localhost")
        self.assertEqual(parsed["port"], 46432)
        self.assertEqual(parsed["auth_mechanism"], "udmi_local")

    def test_parse_project_spec_clearblade(self):
        site_config = {"registry_id": "ZZ-TRI-FECTA", "cloud_region": "us-central1"}
        parsed = parse_project_spec("//clearblade/bos-platform-dev/my-namespace", site_config)
        self.assertEqual(parsed["iot_provider"], "clearblade")
        self.assertEqual(parsed["host"], "us-central1-mqtt.clearblade.com")
        self.assertEqual(parsed["port"], 8883)
        self.assertEqual(parsed["auth_mechanism"], "jwt_gcp")
        self.assertEqual(parsed["registry_id"], "my-namespace~ZZ-TRI-FECTA")

    def test_generate_spotter_config(self):
        out_path = os.path.join(self.test_dir, "out", "spotter_config.json")
        config = generate_spotter_config(
            site_path=self.site_path,
            project_spec="//mqtt/localhost:18883",
            device_id="DN-1",
            out_path=out_path,
        )

        self.assertEqual(config["mqtt"]["device_id"], "DN-1")
        self.assertEqual(config["mqtt"]["port"], 18883)
        self.assertTrue(os.path.isfile(out_path))


if __name__ == "__main__":
    unittest.main()
