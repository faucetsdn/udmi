"""Unit tests for InMemorySecretVault."""

import base64
import unittest

from edge.spotter.src.secret_vault import InMemorySecretVault


class TestInMemorySecretVault(unittest.TestCase):
    """Tests for ephemeral in-memory secret handling."""

    def setUp(self):
        self.vault = InMemorySecretVault()

    def test_set_and_get_raw_bytes(self):
        self.vault.set_secret("token_a", b"secret_bytes_123")
        self.assertEqual(self.vault.get_secret("token_a"), b"secret_bytes_123")
        self.assertEqual(self.vault.get_secret_str("token_a"), "secret_bytes_123")

    def test_set_and_get_base64_string(self):
        raw = b"qualification_api_key_xyz"
        b64 = base64.b64encode(raw).decode("ascii")
        self.vault.set_secret("api_key", b64)
        self.assertEqual(self.vault.get_secret("api_key"), raw)
        self.assertEqual(self.vault.get_secret_str("api_key"), "qualification_api_key_xyz")

    def test_clear_secret(self):
        self.vault.set_secret("temp_token", b"data")
        self.assertIsNotNone(self.vault.get_secret("temp_token"))
        self.vault.clear_secret("temp_token")
        self.assertIsNone(self.vault.get_secret("temp_token"))

    def test_clear_all(self):
        self.vault.set_secret("k1", b"v1")
        self.vault.set_secret("k2", b"v2")
        self.vault.clear_all()
        self.assertIsNone(self.vault.get_secret("k1"))
        self.assertIsNone(self.vault.get_secret("k2"))


if __name__ == "__main__":
    unittest.main()
