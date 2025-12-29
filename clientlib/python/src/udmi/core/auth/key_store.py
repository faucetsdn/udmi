"""
KeyStore implementations for different storage backends.
"""
import os
import platform
import stat
import logging
import shutil
from typing import Optional

from udmi.core.auth.intf.key_store import KeyStore
from udmi.core.utils.file_ops import atomic_write

LOGGER = logging.getLogger(__name__)


class FileKeyStore(KeyStore):
    """
    Stores keys on the filesystem.
    Features:
    - Atomic writes (prevents corruption during power loss).
    - Secure permissions (Owner-only Read/Write).
    - Backup and Restore capabilities.
    """

    def __init__(self, file_path: str):
        self.file_path = file_path

    def save(self, data: bytes) -> None:
        """
        Saves data atomically using the write-temp-then-rename pattern.
        """
        atomic_write(self.file_path, data, mode=0o600)

    def load(self) -> bytes:
        if not self.exists():
            raise FileNotFoundError(f"Key file not found at {self.file_path}")
        with open(self.file_path, "rb") as f:
            return f.read()

    def exists(self) -> bool:
        return os.path.exists(self.file_path)

    def backup(self, suffix: str = ".bak") -> str:
        """Creates a copy of the current key file."""
        if not self.exists():
            raise FileNotFoundError("Cannot backup: Key file does not exist.")

        backup_path = f"{self.file_path}{suffix}"
        shutil.copy2(self.file_path, backup_path)
        LOGGER.info("Backed up key to %s", backup_path)
        return backup_path

    def restore_from_backup(self, suffix: str = ".bak") -> None:
        """Restores the key from the backup file."""
        backup_path = f"{self.file_path}{suffix}"
        if not os.path.exists(backup_path):
            raise FileNotFoundError(f"Backup file missing: {backup_path}")

        LOGGER.warning("Restoring key from backup: %s", backup_path)
        with open(backup_path, "rb") as f:
            backup_data = f.read()
        self.save(backup_data)


class MemoryKeyStore(KeyStore):
    """
    Stores keys in memory.
    Useful for testing or ephemeral instances (e.g., Cloud Functions).
    """

    def __init__(self):
        self._storage: Optional[bytes] = None
        self._backup_storage: Optional[bytes] = None

    def save(self, data: bytes) -> None:
        self._storage = data

    def load(self) -> bytes:
        if self._storage is None:
            raise RuntimeError("No key saved in MemoryKeyStore.")
        return self._storage

    def exists(self) -> bool:
        return self._storage is not None

    def backup(self, suffix: str = ".bak") -> str:
        """Simulates a backup in memory."""
        if self._storage is None:
            raise RuntimeError("Cannot backup: Memory store is empty.")
        self._backup_storage = self._storage
        return "memory:backup"

    def restore_from_backup(self, suffix: str = ".bak") -> None:
        """Restores from memory backup."""
        if self._backup_storage is None:
            raise RuntimeError("Backup missing in MemoryStore.")
        self._storage = self._backup_storage
