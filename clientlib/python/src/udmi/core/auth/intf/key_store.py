from abc import ABC
from abc import abstractmethod


class KeyStore(ABC):
    """Interface for persisting and retrieving cryptographic keys."""

    @abstractmethod
    def save(self, data: bytes) -> None:
        """Persist key data."""
        pass

    @abstractmethod
    def load(self) -> bytes:
        """Retrieve key data."""
        pass

    @abstractmethod
    def exists(self) -> bool:
        """Check if the key exists in storage."""
        pass

    def backup(self, suffix: str = ".bak") -> str:
        """
        Creates a backup of the current key.
        Optional operation: subclasses may raise NotImplementedError.
        """
        raise NotImplementedError("Backup not supported by this KeyStore.")

    def restore_from_backup(self, suffix: str = ".bak") -> None:
        """
        Restores the key from a backup.
        Optional operation: subclasses may raise NotImplementedError.
        """
        raise NotImplementedError("Restore not supported by this KeyStore.")