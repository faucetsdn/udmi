"""
Interface for Key Store implementations.

This module defines the contract for storage backends that persist and retrieve
cryptographic keys. It supports both read and write operations to facilitate
key rotation and lifecycle management.
"""
import abc


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

    def backup(self, suffix: str = ".bak") -> str:
        """
        Creates a backup of the current key.

        Args:
            suffix: An identifier to append for the backup (e.g., file extension).

        Returns:
            The identifier/path of the created backup.

        Raises:
            NotImplementedError: If the backend does not support backups.
        """
        raise NotImplementedError("Backup not supported by this KeyStore.")

    def restore_from_backup(self, suffix: str = ".bak") -> None:
        """
        Restores the key from a backup.

        Args:
            suffix: The identifier used to find the backup.

        Raises:
            NotImplementedError: If the backend does not support restoration.
        """
        raise NotImplementedError("Restore not supported by this KeyStore.")