from abc import ABC
from abc import abstractmethod


class CryptoAlgorithm(ABC):
    """Interface for cryptographic strategies (RSA, ECC, etc.)."""

    @abstractmethod
    def generate_private_key(self) -> object:
        """Generates a private key object (RSA, Elliptic Curve, etc.)."""
        pass

    @abstractmethod
    def serialize_private_key(self, key: object) -> bytes:
        """Converts a private key object to storage-ready bytes (PEM)."""
        pass

    @abstractmethod
    def get_jwt_algorithm_name(self) -> str:
        """Returns the algorithm identifier for JWTs (e.g., RS256)."""
        pass