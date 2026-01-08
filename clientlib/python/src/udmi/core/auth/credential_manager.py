"""
Manages the lifecycle of device credentials and acts as a Signer.

This module handles key generation, storage (via KeyStore), and certificate
management. It implements the Signer interface to provide cryptographic
signatures for JWTs using the managed private key.
"""
import datetime
import logging
import os
import ssl
from typing import Any
from typing import Optional

from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

from udmi.core.auth.intf.crypto_algo import CryptoAlgorithm
from udmi.core.auth.intf.key_store import KeyStore
from udmi.core.auth.intf.signer import Signer
from udmi.core.utils.file_ops import atomic_write

LOGGER = logging.getLogger(__name__)
DEFAULT_CERT_VALIDITY_DAYS = 365


class CredentialManager(Signer):
    """
    Manages the lifecycle of the private key and handles certificate generation.
    """

    def __init__(self, key_store: KeyStore, algorithm: CryptoAlgorithm):
        self.store = key_store
        self.algorithm = algorithm
        self._cached_key: Optional[Any] = None

    def ensure_credentials(self) -> None:
        """
        Ensures a private key exists in the store. Generates one if missing.
        """
        if not self.store.exists():
            LOGGER.info("No credentials found. Generating new keys...")
            private_key = self.algorithm.generate_private_key()
            pem_bytes = self.algorithm.serialize_private_key(private_key)
            self.store.save(pem_bytes)
            self._cached_key = private_key
        else:
            LOGGER.debug("Credentials found in store.")

    def ensure_certificate(self, cert_file: str) -> None:
        """
        Checks if the certificate file exists. If not, generates a
        self-signed certificate using the MANAGED private key.
        """
        if os.path.exists(cert_file):
            LOGGER.debug("Certificate file found at %s.", cert_file)
            return

        LOGGER.info(
            "Certificate not found. Generating self-signed cert at %s...",
            cert_file)
        self._write_new_certificate(cert_file)

    def rotate_certificate(self, cert_file: str) -> None:
        """
        Generates a NEW self-signed certificate using the CURRENT private key
        and overwrites the existing certificate file.
        """
        LOGGER.info("Rotating certificate at %s...", cert_file)
        if not self.store.exists():
            raise RuntimeError(
                "Cannot rotate certificate: No private key found.")

        self._write_new_certificate(cert_file)
        LOGGER.info("Certificate rotated successfully.")

    def _write_new_certificate(self, cert_file: str) -> None:
        """Helper to generate and atomically write a cert to disk."""
        private_key = self._get_private_key_obj()
        cert_bytes = self._generate_self_signed_cert(private_key)

        cert_dir = os.path.dirname(cert_file)
        if cert_dir:
            os.makedirs(cert_dir, exist_ok=True)

        atomic_write(cert_file, cert_bytes)

    def _generate_self_signed_cert(self, private_key: Any) -> bytes:
        """Generates a self-signed X.509 certificate bytes."""
        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, "UDMI Device"),
        ])

        now = datetime.datetime.now(datetime.timezone.utc)

        # We rely on duck-typing for the private_key object here as it comes
        # from the cryptography library without a unified base class.
        builder = x509.CertificateBuilder().subject_name(
            subject
        ).issuer_name(
            issuer
        ).public_key(
            private_key.public_key()
        ).serial_number(
            x509.random_serial_number()
        ).not_valid_before(
            now
        ).not_valid_after(
            now + datetime.timedelta(days=DEFAULT_CERT_VALIDITY_DAYS)
        )

        builder = builder.add_extension(
            x509.BasicConstraints(ca=True, path_length=None), critical=True,
        )

        cert = builder.sign(private_key, hashes.SHA256())
        return cert.public_bytes(serialization.Encoding.PEM)

    def _get_private_key_obj(self) -> Any:
        """
        Returns the deserialized private key object, using cache if available.
        """
        if self._cached_key is None:
            pem_bytes = self.store.load()
            self._cached_key = serialization.load_pem_private_key(
                pem_bytes, password=None
            )
        return self._cached_key

    def get_algorithm_name(self) -> str:
        return self.algorithm.get_jwt_algorithm_name()

    def sign(self, payload: bytes) -> bytes:
        """Cryptographically signs the payload using the managed key."""
        private_key = self._get_private_key_obj()

        if isinstance(private_key, rsa.RSAPrivateKey):
            from cryptography.hazmat.primitives.asymmetric import padding
            return private_key.sign(
                payload,
                padding.PKCS1v15(),
                hashes.SHA256()
            )
        elif isinstance(private_key, ec.EllipticCurvePrivateKey):
            return private_key.sign(
                payload,
                ec.ECDSA(hashes.SHA256())
            )
        else:
            raise ValueError(f"Unsupported key type: {type(private_key)}")

    def get_public_key_pem(self) -> str:
        """
        Returns the public key as a PEM-encoded string.
        """
        private_key = self._get_private_key_obj()
        public_key = private_key.public_key()

        pem_bytes = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        return pem_bytes.decode('utf-8')

    def rotate_credentials(self, backup: bool = True) -> str:
        """
        Rotates the private key.
        """
        LOGGER.info("Initiating credential rotation.")

        if backup:
            try:
                LOGGER.info("Backing up existing credentials...")
                self.store.backup()
            except Exception as e:
                LOGGER.error(f"Backup failed: {e}. Aborting rotation.")
                raise RuntimeError(
                    "Credential rotation aborted due to backup failure.") from e

        LOGGER.debug("Generating new private key...")
        new_private_key = self.algorithm.generate_private_key()
        pem_bytes = self.algorithm.serialize_private_key(new_private_key)

        LOGGER.info("Saving new credentials to storage...")
        self.store.save(pem_bytes)

        # Update cache immediately
        self._cached_key = new_private_key

        # Verification step: ensure we can read it back if we had to
        try:
            verification_bytes = self.store.load()
            if not verification_bytes:
                raise ValueError("Key verification failed: Read empty bytes.")
        except Exception as e:
            LOGGER.critical(
                "FATAL: New key saved but could not be verified: %s", e)
            self._cached_key = None  # Invalidate cache on failure
            raise RuntimeError("Key rotation verification failed") from e

        return self.get_public_key_pem()

    def create_backup(self) -> None:
        """Delegates to store to create a backup."""
        self.store.backup()

    def restore_backup(self) -> None:
        """Delegates to store to restore backup."""
        self.store.restore_from_backup()
        self._cached_key = None  # Invalidate cache after restore

    def get_ssl_context(self, cert_file: str,
        ca_file: Optional[str] = None) -> ssl.SSLContext:
        """
        Creates an SSLContext for mTLS, compatible with Python's ssl module.

        Args:
            cert_file: Path to the public certificate file.
            ca_file: Optional path to the CA certificate file.

        Returns:
            A configured ssl.SSLContext.

        Raises:
            FileNotFoundError: If keys or certs are missing.
            ValueError: If the underlying KeyStore is not file-based (ssl module requirement).
        """
        # Python's ssl.load_cert_chain REQUIRES a file path for the private key.
        # We must ensure our KeyStore is actually a FileKeyStore.
        if not hasattr(self.store, "_file_path"):
            raise ValueError(
                "SSL Context requires a FileKeyStore to load keys from disk.")

        key_file = getattr(self.store, "_file_path")

        if not os.path.exists(key_file):
            raise FileNotFoundError(f"Private key file not found at {key_file}")

        if not os.path.exists(cert_file):
            raise FileNotFoundError(
                f"Certificate file not found at {cert_file}")

        context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH)

        if ca_file:
            if not os.path.exists(ca_file):
                raise FileNotFoundError(f"CA file not found at {ca_file}")
            context.load_verify_locations(cafile=ca_file)

        context.load_cert_chain(certfile=cert_file, keyfile=key_file)
        LOGGER.info("Loaded mTLS certificate chain.")

        return context
