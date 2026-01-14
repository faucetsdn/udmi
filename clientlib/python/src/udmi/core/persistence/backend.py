"""
Defines the abstract interface for persistence backends.

This module provides the contract for storage strategies (e.g., File, Memory, Redis)
that persist the device's state and configuration.
"""
from abc import ABC
from abc import abstractmethod
from typing import Any
from typing import Dict
from typing import Optional


class PersistenceBackend(ABC):
    """
    Abstract strategy for storing and retrieving key-value data.
    """

    @abstractmethod
    def load(self, key: str) -> Optional[Any]:
        """
        Retrieves a value by key.

        Args:
            key: The identifier for the data.

        Returns:
            The stored value, or None if the key does not exist.
        """
        pass

    @abstractmethod
    def save(self, key: str, value: Any) -> None:
        """
        Saves a value associated with a key.

        Args:
            key: The identifier for the data.
            value: The data to store. Must be serializable by the backend
                   (typically JSON-serializable).
        """
        pass

    @abstractmethod
    def delete(self, key: str) -> None:
        """
        Removes a key and its value.

        Args:
            key: The identifier for the data to remove.
        """
        pass

    def load_all(self) -> Dict[str, Any]:
        """
        Loads all persisted data as a dictionary.

        Returns:
            A dictionary containing all key-value pairs in the store.

        Raises:
            NotImplementedError: If the backend does not support bulk retrieval.
        """
        raise NotImplementedError("load_all not implemented for this backend.")
