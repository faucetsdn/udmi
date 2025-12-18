"""
Certificate Manager for UDMI.
Handles the generation and management of cryptographic keys and certificates.
"""

import logging
import os
import datetime
import ssl
from typing import Optional

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes

LOGGER = logging.getLogger(__name__)

DEFAULT_CERT_VALIDITY_DAYS = 365


class CertManager:
    """
    Manages cryptographic material (keys and certificates).
    Can generate RSA key pairs and self-signed certificates.
    """

    def __init__(self,
        key_file: str,
        cert_file: Optional[str] = None,
        ca_file: Optional[str] = None
    ):
        self.key_file = key_file
        self.cert_file = cert_file
        self.ca_file = ca_file

    def ensure_keys_exist(self, algorithm: str = "RS256") -> None:
        """
        Checks if the private key exists. If not, generates it.
        If a cert file is specified and missing, generates a self-signed cert.
        """
        if algorithm != "RS256":
            raise ValueError(
                f"Unsupported algorithm '{algorithm}'. "
                f"Only 'RS256' is currently supported.")
        if not os.path.exists(self.key_file):
            LOGGER.info(
                "Key file not found at %s. Generating new %s key pair...",
                self.key_file, algorithm)
            self._generate_private_key()
        else:
            LOGGER.debug("Key file found at %s.", self.key_file)

        if self.cert_file and not os.path.exists(self.cert_file):
            LOGGER.info(
                "Certificate file not found at %s. Generating self-signed certificate...",
                self.cert_file)
            self._generate_self_signed_cert()

    def get_ssl_context(self) -> ssl.SSLContext:
        """
        Creates an SSLContext for mTLS.
        Raises: FileNotFoundError if cert or key files are missing.
        """
        context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH)

        if self.ca_file:
            context.load_verify_locations(cafile=self.ca_file)

        if self.cert_file and self.key_file:
            if os.path.exists(self.cert_file) and os.path.exists(self.key_file):
                context.load_cert_chain(certfile=self.cert_file,
                                        keyfile=self.key_file)
                LOGGER.info("Loaded mTLS certificate chain.")
            else:
                raise FileNotFoundError(
                    f"Certificate or Key file missing. Cannot create mTLS context. "
                    f"Cert: {self.cert_file}, Key: {self.key_file}"
                )
        elif self.cert_file or self.key_file:
            raise ValueError(
                "Both cert_file and key_file must be provided for mTLS.")

        return context

    def _generate_private_key(self) -> None:
        """Generates an RSA private key and saves it to disk."""
        try:
            key = rsa.generate_private_key(
                public_exponent=65537,
                key_size=2048,
            )
            key_dir = os.path.dirname(self.key_file)
            if key_dir:
                os.makedirs(key_dir, exist_ok=True)

            with open(self.key_file, "wb") as f:
                f.write(key.private_bytes(
                    encoding=serialization.Encoding.PEM,
                    format=serialization.PrivateFormat.PKCS8,
                    encryption_algorithm=serialization.NoEncryption()
                ))
            LOGGER.info("Generated private key at %s", self.key_file)
        except Exception as e:
            LOGGER.error("Failed to generate private key: %s", e)
            raise

    def _generate_self_signed_cert(self) -> None:
        """Generates a self-signed X.509 certificate using the existing private key."""
        try:
            with open(self.key_file, "rb") as f:
                private_key = serialization.load_pem_private_key(f.read(),
                                                                 password=None)

            subject = issuer = x509.Name([
                x509.NameAttribute(NameOID.COMMON_NAME, "UDMI Device"),
            ])

            cert = x509.CertificateBuilder().subject_name(
                subject
            ).issuer_name(
                issuer
            ).public_key(
                private_key.public_key()
            ).serial_number(
                x509.random_serial_number()
            ).not_valid_before(
                datetime.datetime.utcnow()
            ).not_valid_after(
                datetime.datetime.utcnow() + datetime.timedelta(
                    days=DEFAULT_CERT_VALIDITY_DAYS)
            ).add_extension(
                x509.BasicConstraints(ca=True, path_length=None), critical=True,
            ).sign(private_key, hashes.SHA256())

            cert_dir = os.path.dirname(
                self.cert_file) if self.cert_file else None
            if cert_dir:
                os.makedirs(cert_dir, exist_ok=True)

            if self.cert_file:
                with open(self.cert_file, "wb") as f:
                    f.write(cert.public_bytes(serialization.Encoding.PEM))
                LOGGER.info("Generated self-signed certificate at %s",
                            self.cert_file)
        except Exception as e:
            LOGGER.error("Failed to generate certificate: %s", e)
            raise
