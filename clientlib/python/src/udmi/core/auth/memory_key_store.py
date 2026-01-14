"""
In-memory implementation of the KeyStore interface.

This module provides a volatile storage backend, primarily used for testing
or for devices that handle keys in memory only (ephemeral).
"""
from typing import Optional
from udmi.core.auth.intf.key_store import KeyStore


class MemoryKeyStore(KeyStore):
    """
    Volatile, in-memory implementation of KeyStore.
    Data is lost when the instance is destroyed.
    """

    def __init__(self, initial_data: Optional[bytes] = None):
        """
        Initializes the MemoryKeyStore.

        Args:
            initial_data: Optional bytes to pre-populate the store.
        """
        self._data: Optional[bytes] = initial_data
        self._backups: dict[str, bytes] = {}

    def save(self, data: bytes) -> None:
        """
        Saves the key data to memory.
        """
        self._data = data

    def load(self) -> bytes:
        """
        Retrieves the key data from memory.

        Raises:
            FileNotFoundError: If no data has been saved.
        """
        if self._data is None:
            raise FileNotFoundError("No key data present in MemoryKeyStore.")
        return self._data

    def exists(self) -> bool:
        """
        Checks if data is currently stored.
        """
        return self._data is not None

    def backup(self, backup_name: Optional[str] = None) -> str:
        """
        Creates an in-memory backup copy.

        Args:
            backup_name: Optional backup name.

        Returns:
            Identifier of the backup.
        """
        if self._data is None:
            raise FileNotFoundError("Cannot backup empty store.")
        if backup_name is None:
            backup_name = ".bak"

        self._backups[backup_name] = self._data
        return f"memory:{backup_name}"

    def restore_from_backup(self, backup_name: str) -> None:
        """
        Restores data from the in-memory backup.
        """
        if not backup_name or backup_name not in self._backups:
            raise FileNotFoundError(f"Backup {backup_name} not found in memory.")

        self._data = self._backups[backup_name]
