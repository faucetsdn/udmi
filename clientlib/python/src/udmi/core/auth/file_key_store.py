"""
Concrete implementation of KeyStore using the local filesystem.

This module provides a file-based backend for persisting and retrieving
cryptographic keys, ensuring atomic writes and proper file permissions.
"""
import os
import shutil
from datetime import datetime
from typing import Optional

from udmi.core.auth.intf.key_store import KeyStore
from udmi.core.utils.file_ops import atomic_write


class FileKeyStore(KeyStore):
    """
    Filesystem-based implementation of the KeyStore interface.
    Stores keys as individual files on disk.
    """

    def __init__(self, file_path: str):
        """
        Initializes the FileKeyStore.

        Args:
            file_path: The full path to the file where the key is stored.
        """
        self._file_path = file_path

    def save(self, data: bytes) -> None:
        """
        Persists key data to disk atomically.

        Args:
            data: The bytes to write.
        """
        # secure_mode 0o600 is handled by atomic_write default or explicit arg
        atomic_write(self._file_path, data, mode=0o600)

    def load(self) -> bytes:
        """
        Retrieves key data from disk.

        Returns:
            The content of the key file as bytes.

        Raises:
            FileNotFoundError: If the key file does not exist.
            IOError: If the file cannot be read.
        """
        with open(self._file_path, "rb") as f:
            return f.read()

    def exists(self) -> bool:
        """
        Checks if the key file exists.
        """
        return os.path.exists(self._file_path)

    def backup(self, backup_identifier: Optional[str] = None) -> str:
        """
        Creates a backup of the current key file.

        Args:
            backup_identifier: Path to create the backup.

        Returns:
            The path to the backup file.

        Raises:
            FileNotFoundError: If the source key does not exist.
        """
        if not self.exists():
            raise FileNotFoundError(f"Cannot backup missing key: {self._file_path}")
        if not backup_identifier:
            ts = datetime.now().strftime("%Y%m%d%H%M%S")
            backup_identifier = f"{self._file_path}{ts}.bak"
        shutil.copy2(self._file_path, backup_identifier)
        return backup_identifier

    def restore_from_backup(self, backup_identifier: str) -> None:
        """
        Restores the key from a backup file.

        Args:
            backup_identifier: The path of the backup to restore from.

        Raises:
            FileNotFoundError: If the backup file does not exist.
        """
        if not os.path.exists(backup_identifier):
            raise FileNotFoundError(f"Backup file not found: {backup_identifier}")

        with open(backup_identifier, "rb") as f:
            content = f.read()

        self.save(content)
