import json
import os
import tempfile
import unittest
from unittest.mock import patch, mock_open

from edge.spotter.src.agent import build_endpoint_config, calculate_local_password
from udmi.schema import Protocol

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
                "device_id": "GAT-1",
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
        self.assertEqual(endpoint.client_id, "/r/ZZ-TRI-FECTA/d/GAT-1-spotter")
        self.assertEqual(endpoint.topic_prefix, "/r/ZZ-TRI-FECTA/d/")
        self.assertEqual(endpoint.algorithm, "RS256")
        self.assertEqual(endpoint.key_file, self.key_file)
        self.assertEqual(endpoint.cert_file, "/path/to/cert.crt")
        self.assertEqual(endpoint.ca_file, "/path/to/ca.crt")
        self.assertEqual(endpoint.protocol, Protocol.mqtt)
        
        # Check basic auth
        self.assertIsNotNone(endpoint.auth_provider)
        self.assertIsNotNone(endpoint.auth_provider.basic)
        self.assertEqual(endpoint.auth_provider.basic.username, "/r/ZZ-TRI-FECTA/d/GAT-1")
        
        # Password should match pkcs8 sha256 prefix
        import hashlib
        h = hashlib.sha256(b"MOCK PKCS8 KEY DATA").hexdigest()[:8]
        self.assertEqual(endpoint.auth_provider.basic.password, h)

    def test_build_endpoint_config_jwt_gcp(self):
        config = {
            "mqtt": {
                "device_id": "GAT-1",
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
        self.assertEqual(endpoint.client_id, "projects/my-project/locations/us-central1/registries/ZZ-TRI-FECTA/devices/GAT-1")
        self.assertEqual(endpoint.topic_prefix, "/devices/")
        self.assertEqual(endpoint.algorithm, "RS256")
        self.assertIsNone(endpoint.auth_provider) # Clientlib JwtAuthProvider is created inside messaging layer, not endpoint_config

    def test_build_endpoint_config_custom_client_id(self):
        config = {
            "mqtt": {
                "device_id": "GAT-1",
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

if __name__ == "__main__":
    unittest.main()
