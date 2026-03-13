"""
Interface for Cryptographic Algorithm strategies.

This module defines the strategy pattern for generating and handling
different types of cryptographic keys (e.g., RSA, ECC).
"""
import abc
from typing import Any


class CryptoAlgorithm(abc.ABC):
    """
    Abstract strategy for cryptographic key operations.
    """

    @abc.abstractmethod
    def generate_private_key(self) -> Any:
        """
        Generates a private key object.

        Returns:
            An opaque private key object (e.g., rsa.RSAPrivateKey).
            The specific type depends on the implementation.
        """

    @abc.abstractmethod
    def serialize_private_key(self, key: Any) -> bytes:
        """
        Converts a private key object to storage-ready bytes (PEM).

        Args:
            key: The private key object to serialize.

        Returns:
            The PEM-encoded private key as bytes.
        """

    @abc.abstractmethod
    def get_jwt_algorithm_name(self) -> str:
        """
        Returns the algorithm identifier for JWTs (e.g., 'RS256', 'ES256').
        """
