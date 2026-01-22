"""
Authentication Interfaces Module.

This package defines the core abstract base classes and interfaces for the
authentication subsystem. It establishes the contracts for:
- Authentication Providers (AuthProvider)
- Cryptographic Algorithms (CryptoAlgorithm)
- Key Storage (KeyStore)
- Digital Signing (Signer)

These interfaces allow for modularity and dependency injection, enabling
different implementations (e.g., File-based vs. HSM-based storage, RSA vs. ECC)
to be swapped seamlessly.
"""
from .auth_provider import AuthProvider
from .crypto_algo import CryptoAlgorithm
from .key_store import KeyStore
from .signer import Signer
