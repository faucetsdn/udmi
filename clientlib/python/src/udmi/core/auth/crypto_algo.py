from abc import ABC

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa, ec
from udmi.core.auth.intf import CryptoAlgorithm


def get_algorithm_strategy(algo_name: str) -> CryptoAlgorithm:
    """Helper to map config strings to Algorithm classes."""
    algo_map = {
        "ES256": EcAlgorithm,
        "RS256": RsaAlgorithm,
    }

    return algo_map.get(algo_name, RsaAlgorithm)()


class BaseCryptoAlgorithm(CryptoAlgorithm, ABC):
    """Shared logic for serialization."""

    def serialize_private_key(self, key: object) -> bytes:
        return key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        )


class RsaAlgorithm(BaseCryptoAlgorithm):
    """Standard RSA 2048 strategy (RS256)."""

    def generate_private_key(self) -> object:
        return rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048
        )

    def get_jwt_algorithm_name(self) -> str:
        return "RS256"


class EcAlgorithm(BaseCryptoAlgorithm):
    """Elliptic Curve strategy (ES256)."""

    def generate_private_key(self) -> object:
        return ec.generate_private_key(ec.SECP256R1())

    def get_jwt_algorithm_name(self) -> str:
        return "ES256"
