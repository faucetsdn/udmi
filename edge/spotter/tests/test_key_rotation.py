"""Unit tests for Spotter key rotation and rollback capabilities."""

import unittest
from unittest.mock import MagicMock

from udmi.core.auth.credential_manager import CredentialManager
from udmi.core.auth.crypto_algo import RsaAlgorithm
from udmi.core.auth.intf.key_store import KeyStore
from udmi.core.managers.system_manager import SystemManager


class MockKeyStore(KeyStore):
    def __init__(self):
        self.data = b"old_private_key_pem"
        self.backup_data = None

    def load(self) -> bytes:
        return self.data

    def save(self, key_bytes: bytes) -> None:
        self.data = key_bytes

    def exists(self) -> bool:
        return bool(self.data)

    def backup(self) -> str:
        self.backup_data = self.data
        return "backup_id_001"

    def restore_from_backup(self, identifier: str) -> None:
        if self.backup_data:
            self.data = self.backup_data


class TestSpotterKeyRotation(unittest.TestCase):
    """Verifies automated key rotation and rollback on Spotter edge nodes."""

    def setUp(self):
        self.system_manager = SystemManager()
        self.mock_device = MagicMock()
        self.key_store = MockKeyStore()
        self.crypto_algo = RsaAlgorithm()
        self.cred_manager = CredentialManager(self.key_store, self.crypto_algo)
        self.mock_device.credential_manager = self.cred_manager
        self.system_manager._device = self.mock_device

    def test_successful_key_rotation(self):
        callback_called = []

        def mock_callback(new_public_pem: str, backup_id: str) -> bool:
            callback_called.append((new_public_pem, backup_id))
            return True

        self.system_manager.register_key_rotation_callback(mock_callback)
        self.system_manager.trigger_key_rotation({})

        # Private key should be updated to real RSA PEM bytes in store
        self.assertTrue(self.key_store.data.startswith(b"-----BEGIN PRIVATE KEY-----"))
        self.assertNotEqual(self.key_store.data, b"old_private_key_pem")
        # Callback should have received new public key pem string and backup id
        self.assertEqual(len(callback_called), 1)
        self.assertTrue(callback_called[0][0].startswith("-----BEGIN PUBLIC KEY-----"))
        self.assertEqual(callback_called[0][1], "backup_id_001")
        # Connection reset requested
        self.mock_device.request_connection_reset.assert_called_once_with("Key Rotation")

    def test_key_rotation_failure_triggers_rollback(self):
        def failing_callback(new_public_pem: str, backup_id: str) -> bool:
            raise RuntimeError("Cloud registration failed")

        self.system_manager.register_key_rotation_callback(failing_callback)
        self.system_manager.trigger_key_rotation({})

        # Key should be rolled back to old key
        self.assertEqual(self.key_store.data, b"old_private_key_pem")
        # Connection reset should not be called on failure
        self.mock_device.request_connection_reset.assert_not_called()


if __name__ == "__main__":
    unittest.main()
