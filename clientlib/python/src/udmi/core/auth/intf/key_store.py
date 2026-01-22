"""
Interface for Key Store implementations.

This module defines the contract for storage backends that persist and retrieve
cryptographic keys. It supports both read and write operations to facilitate
key rotation and lifecycle management.
"""
import abc
from typing import Optional


class KeyStore(abc.ABC):
    """
    Abstract base class for persisting and retrieving cryptographic keys.
    """

    @abc.abstractmethod
    def save(self, data: bytes) -> None:
        """
        Persists key data to the storage backend.

        Args:
            data: The raw key bytes to store.
        """

    @abc.abstractmethod
    def load(self) -> bytes:
        """
        Retrieves key data from the storage backend.

        Returns:
            The raw key bytes.

        Raises:
            FileNotFoundError (or similar): If the key does not exist.
        """

    @abc.abstractmethod
    def exists(self) -> bool:
        """
        Checks if the key currently exists in storage.

        Returns:
            True if the key exists, False otherwise.
        """

    def backup(self, backup_identifier: Optional[str] = None) -> str:
        """
        Creates a backup of the current key.

        Args:
            backup_identifier: Identifier for the backup.

        Returns:
            The identifier/path of the created backup.

        Raises:
            NotImplementedError: If the backend does not support backups.
        """
        raise NotImplementedError("Backup not supported by this KeyStore.")

    def restore_from_backup(self, backup_identifier: str) -> None:
        """
        Restores the key from a backup.

        Args:
            backup_identifier: The identifier for the backup to restore from.

        Raises:
            NotImplementedError: If the backend does not support restoration.
        """
        raise NotImplementedError("Restore not supported by this KeyStore.")
