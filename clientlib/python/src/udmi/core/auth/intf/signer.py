from abc import ABC
from abc import abstractmethod


class Signer(ABC):
    """Protocol for any object that can sign data."""

    @abstractmethod
    def sign(self, payload: bytes) -> bytes:
        """Sign the payload and return the signature bytes."""

    @abstractmethod
    def get_algorithm_name(self) -> str:
        """Returns the JWT algorithm name (e.g., 'RS256', 'ES256')."""