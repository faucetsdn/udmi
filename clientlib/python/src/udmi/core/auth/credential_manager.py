import logging
import datetime
import os
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import rsa, ec

from udmi.core.auth.intf import KeyStore, CryptoAlgorithm, Signer

LOGGER = logging.getLogger(__name__)
DEFAULT_CERT_VALIDITY_DAYS = 365


class CredentialManager(Signer):
    """
    Manages the lifecycle of the private key, acts as a Signer,
    and handles certificate generation.
    """

    def __init__(self, key_store: KeyStore, algorithm: CryptoAlgorithm):
        self.store = key_store
        self.algorithm = algorithm

    def ensure_credentials(self) -> None:
        if not self.store.exists():
            LOGGER.info("No credentials found. Generating new keys...")
            private_key = self.algorithm.generate_private_key()
            pem_bytes = self.algorithm.serialize_private_key(private_key)
            self.store.save(pem_bytes)
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

        private_key = self._load_private_key_obj()

        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, "UDMI Device"),
        ])

        builder = x509.CertificateBuilder().subject_name(
            subject
        ).issuer_name(
            issuer
        ).public_key(
            private_key.public_key()
        ).serial_number(
            x509.random_serial_number()
        ).not_valid_before(
            datetime.datetime.now(datetime.timezone.utc)
        ).not_valid_after(
            datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(
                days=DEFAULT_CERT_VALIDITY_DAYS)
        )

        builder = builder.add_extension(
            x509.BasicConstraints(ca=True, path_length=None), critical=True,
        )

        cert = builder.sign(private_key, hashes.SHA256())

        cert_dir = os.path.dirname(cert_file)
        if cert_dir:
            os.makedirs(cert_dir, exist_ok=True)

        with open(cert_file, "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))

        LOGGER.info("Generated self-signed certificate.")

    def _load_private_key_obj(self):
        """Helper to load and deserialize the key object from bytes."""
        pem_bytes = self.store.load()
        return serialization.load_pem_private_key(pem_bytes, password=None)

    def get_algorithm_name(self) -> str:
        return self.algorithm.get_jwt_algorithm_name()

    def sign(self, payload: bytes) -> bytes:
        private_key = self._load_private_key_obj()
        if isinstance(private_key, rsa.RSAPrivateKey):
            from cryptography.hazmat.primitives.asymmetric import padding
            return private_key.sign(payload, padding.PKCS1v15(),
                                    hashes.SHA256())
        elif isinstance(private_key, ec.EllipticCurvePrivateKey):
            return private_key.sign(payload, ec.ECDSA(hashes.SHA256()))
        else:
            raise ValueError(f"Unsupported key type: {type(private_key)}")

    def get_public_key_pem(self) -> str:
        """
        Reads the private key from storage and derives the Public Key in PEM format.

        Returns:
            The public key as a PEM-encoded string, ready for upload to
            Cloud IoT Core or ClearBlade.
        """
        private_key = self._load_private_key_obj()

        public_key = private_key.public_key()

        pem_bytes = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )

        return pem_bytes.decode('utf-8')

    def rotate_credentials(self, backup: bool = True) -> str:
        """
        Generates a new private key and replaces the existing one.
        Args:
            backup: If True, backs up the existing key before overwriting.
                    Defaults to True for safety.
        Returns:
            The public key PEM of the newly generated key.
        """
        LOGGER.info("Initiating credential rotation.")

        if backup:
            try:
                LOGGER.info("Backing up existing credentials...")
                self.store.backup()
            except Exception as e:
                LOGGER.error(
                    f"Backup failed: {e}. Aborting rotation to prevent data loss.")
                raise RuntimeError(
                    "Credential rotation aborted due to backup failure.") from e

        LOGGER.debug("Generating new private key...")
        new_private_key = self.algorithm.generate_private_key()
        pem_bytes = self.algorithm.serialize_private_key(new_private_key)

        LOGGER.info("Saving new credentials to storage...")
        self.store.save(pem_bytes)

        try:
            verification_bytes = self.store.load()
            if not verification_bytes:
                raise ValueError("Key verification failed: Read empty bytes.")
            serialization.load_pem_private_key(verification_bytes,
                                               password=None)
        except Exception as e:
            LOGGER.critical(
                "FATAL: New key saved but could not be verified/loaded: %s", e)
            raise RuntimeError("Key rotation verification failed") from e

        return self.get_public_key_pem()

    def create_backup(self) -> None:
        """Delegates to store to create a backup."""
        if hasattr(self.store, "backup"):
            self.store.backup()

    def restore_backup(self) -> None:
        """Delegates to store to restore backup."""
        if hasattr(self.store, "restore_from_backup"):
            self.store.restore_from_backup()

    def rotate_certificate(self, cert_file: str) -> None:
        """
        Generates a NEW self-signed certificate using the CURRENT private key
        and overwrites the existing certificate file.
        """
        LOGGER.info("Rotating certificate at %s...", cert_file)

        if not self.store.exists():
            raise RuntimeError(
                "Cannot rotate certificate: No private key found.")

        private_key = self._load_private_key_obj()

        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, "UDMI Device"),
        ])

        builder = x509.CertificateBuilder().subject_name(
            subject
        ).issuer_name(
            issuer
        ).public_key(
            private_key.public_key()
        ).serial_number(
            x509.random_serial_number()
        ).not_valid_before(
            datetime.datetime.now(datetime.timezone.utc)
        ).not_valid_after(
            datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(
                days=DEFAULT_CERT_VALIDITY_DAYS)
        )

        builder = builder.add_extension(
            x509.BasicConstraints(ca=True, path_length=None), critical=True,
        )

        cert = builder.sign(private_key, hashes.SHA256())

        cert_dir = os.path.dirname(cert_file)
        if cert_dir:
            os.makedirs(cert_dir, exist_ok=True)

        with open(cert_file, "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))

        LOGGER.info("Certificate rotated successfully.")
