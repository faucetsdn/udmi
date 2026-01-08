"""
Interface for Signing capabilities.

This module defines the contract for any component capable of cryptographic
signing, primarily used for generating JWT signatures.
"""
import abc


class Signer(abc.ABC):
    """
    Protocol for any object that can sign data.
    """

    @abc.abstractmethod
    def sign(self, payload: bytes) -> bytes:
        """
        Cryptographically signs the payload.

        Args:
            payload: The raw bytes to sign (e.g., the JWT header+payload).

        Returns:
            The signature as raw bytes.
        """

    @abc.abstractmethod
    def get_algorithm_name(self) -> str:
        """
        Returns the JWT algorithm identifier.

        Returns:
            A string representing the algorithm (e.g., 'RS256', 'ES256').
        """
