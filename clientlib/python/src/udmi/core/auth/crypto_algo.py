"""
Concrete implementations of Cryptographic Algorithms.

This module provides the actual logic for generating and serializing keys
using the `cryptography` library (RSA and Elliptic Curve).
"""
import abc
from typing import Any
from typing import Optional

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric import rsa

from udmi.core.auth.intf.crypto_algo import CryptoAlgorithm


class BaseCryptoAlgorithm(CryptoAlgorithm, abc.ABC):
    """
    Shared logic for cryptographic algorithm implementations.
    """

    def serialize_private_key(self, key: Any) -> bytes:
        """
        Serializes a private key object to PEM format.

        Args:
            key: The opaque private key object from the cryptography library.

        Returns:
            The PEM-encoded private key as bytes.
        """
        # We rely on duck-typing here as the key objects from the cryptography
        # library (RSAPrivateKey, EllipticCurvePrivateKey) share this method signature
        # but do not share a convenient common ancestor for type checking.
        return key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        )


class RsaAlgorithm(BaseCryptoAlgorithm):
    """
    Standard RSA 2048 strategy (RS256).
    """

    def generate_private_key(self) -> Any:
        """Generates a standard 2048-bit RSA private key."""
        return rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048
        )

    def get_jwt_algorithm_name(self) -> str:
        return "RS256"


class EcAlgorithm(BaseCryptoAlgorithm):
    """
    Elliptic Curve strategy (ES256).
    """

    def generate_private_key(self) -> Any:
        """Generates a SECP256R1 (Prime256v1) private key."""
        return ec.generate_private_key(ec.SECP256R1())

    def get_jwt_algorithm_name(self) -> str:
        return "ES256"


def get_algorithm_strategy(algo_name: Optional[str]) -> CryptoAlgorithm:
    """
    Factory to map configuration strings to Algorithm strategies.

    Args:
        algo_name: The algorithm name (e.g., "RS256", "ES256").
                   If None or empty, defaults to RSA.

    Returns:
        An instance of a CryptoAlgorithm subclass.

    Raises:
        ValueError: If a non-empty algo_name is provided but not supported.
    """
    if not algo_name:
        return RsaAlgorithm()

    algo_map = {
        "ES256": EcAlgorithm,
        "RS256": RsaAlgorithm,
    }

    if algo_name not in algo_map:
        raise ValueError(
            f"Unsupported algorithm '{algo_name}'. "
            f"Supported: {list(algo_map.keys())}"
        )

    return algo_map[algo_name]()
