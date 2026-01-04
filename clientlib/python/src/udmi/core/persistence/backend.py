"""
Defines the abstract interface for persistence backends.
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
        Retrieves a value by key. Returns None if not found.
        """
        pass

    @abstractmethod
    def save(self, key: str, value: Any) -> None:
        """
        Saves a value associated with a key.
        """
        pass

    @abstractmethod
    def delete(self, key: str) -> None:
        """
        Removes a key and its value.
        """
        pass

    def load_all(self) -> Dict[str, Any]:
        """
        Optional: Load all persisted data as a dictionary.
        Useful for migration or debugging.
        """
        return {}
