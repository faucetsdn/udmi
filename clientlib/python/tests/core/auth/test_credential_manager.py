"""
Unit tests for the `CredentialManager` class.

This module tests the credential lifecycle management, including key generation,
rotation, and certificate creation.
"""

import ssl
from unittest.mock import MagicMock, patch, ANY

import pytest
from cryptography.hazmat.primitives.asymmetric import rsa, ec

from src.udmi.core.auth.credential_manager import CredentialManager
from src.udmi.core.auth.intf.crypto_algo import CryptoAlgorithm
from src.udmi.core.auth.intf.key_store import KeyStore


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def mock_store():
    """Mocks the KeyStore."""
    store = MagicMock(spec=KeyStore)
    store.exists.return_value = False
    return store


@pytest.fixture
def mock_algo():
    """Mocks the CryptoAlgorithm."""
    algo = MagicMock(spec=CryptoAlgorithm)
    algo.generate_private_key.return_value = MagicMock()
    algo.serialize_private_key.return_value = b"mock_pem_bytes"
    algo.get_jwt_algorithm_name.return_value = "RS256"
    return algo


@pytest.fixture
def manager(mock_store, mock_algo):
    """Returns an initialized CredentialManager."""
    return CredentialManager(mock_store, mock_algo)


def test_ensure_credentials_generates_if_missing(manager, mock_store,
    mock_algo):
    """
    Verifies that if the store is empty, new keys are generated and saved.
    """
    mock_store.exists.return_value = False

    manager.ensure_credentials()

    mock_algo.generate_private_key.assert_called_once()
    mock_algo.serialize_private_key.assert_called_once()
    mock_store.save.assert_called_once_with(b"mock_pem_bytes")
    assert manager._cached_key is not None


def test_ensure_credentials_skips_if_exists(manager, mock_store, mock_algo):
    """
    Verifies that if the store has data, no new keys are generated.
    """
    mock_store.exists.return_value = True

    manager.ensure_credentials()

    mock_algo.generate_private_key.assert_not_called()
    mock_store.save.assert_not_called()


@patch("src.udmi.core.auth.credential_manager.atomic_write")
@patch("os.path.exists")
@patch("src.udmi.core.auth.credential_manager.x509")
def test_ensure_certificate_generates_if_missing(
    mock_x509, mock_exists, mock_atomic_write, manager
):
    """
    Verifies that if the cert file is missing, a new self-signed cert is
    generated and written to disk.
    """
    mock_exists.return_value = False
    manager._get_private_key_obj = MagicMock()

    mock_builder = mock_x509.CertificateBuilder.return_value
    mock_builder.subject_name.return_value = mock_builder
    mock_builder.issuer_name.return_value = mock_builder
    mock_builder.public_key.return_value = mock_builder
    mock_builder.serial_number.return_value = mock_builder
    mock_builder.not_valid_before.return_value = mock_builder
    mock_builder.not_valid_after.return_value = mock_builder
    mock_builder.add_extension.return_value = mock_builder

    mock_cert = mock_builder.sign.return_value
    mock_cert.public_bytes.return_value = b"cert_bytes"

    manager.ensure_certificate("device.crt")

    mock_atomic_write.assert_called_once_with("device.crt", b"cert_bytes")


def test_rotate_credentials_flow(manager, mock_store, mock_algo):
    """
    Verifies the happy path for key rotation:
    1. Backup existing.
    2. Generate new.
    3. Save new.
    4. Verify read-back.
    """
    mock_store.load.return_value = b"mock_pem_bytes"

    mock_key = MagicMock()
    mock_key.public_key.return_value.public_bytes.return_value = b"public_pem"
    manager._get_private_key_obj = MagicMock(return_value=mock_key)

    pub_key = manager.rotate_credentials(backup=True)

    mock_store.backup.assert_called_once()
    mock_algo.generate_private_key.assert_called_once()
    mock_store.save.assert_called_once_with(b"mock_pem_bytes")
    mock_store.load.assert_called()
    assert pub_key == "public_pem"


def test_rotate_credentials_backup_failure(manager, mock_store):
    """
    Verifies that if backup fails, rotation aborts and no new key is saved.
    """
    mock_store.backup.side_effect = IOError("Disk full")

    with pytest.raises(RuntimeError, match="Credential rotation aborted"):
        manager.rotate_credentials(backup=True)

    mock_store.save.assert_not_called()


def test_rotate_credentials_verification_failure(manager, mock_store):
    """
    Verifies that if the new key cannot be read back, the operation raises error.
    """
    mock_store.load.return_value = b""

    with pytest.raises(RuntimeError, match="verification failed"):
        manager.rotate_credentials(backup=False)


def test_sign_rsa(manager):
    """Verifies RSA signing delegation."""
    mock_rsa_key = MagicMock(spec=rsa.RSAPrivateKey)
    mock_rsa_key.sign.return_value = b"signature"
    manager._get_private_key_obj = MagicMock(return_value=mock_rsa_key)

    sig = manager.sign(b"payload")

    assert sig == b"signature"
    mock_rsa_key.sign.assert_called_once()


def test_sign_ec(manager):
    """Verifies EC signing delegation."""
    mock_ec_key = MagicMock(spec=ec.EllipticCurvePrivateKey)
    mock_ec_key.sign.return_value = b"signature"
    manager._get_private_key_obj = MagicMock(return_value=mock_ec_key)

    sig = manager.sign(b"payload")

    assert sig == b"signature"
    mock_ec_key.sign.assert_called_once()


@patch("ssl.create_default_context")
@patch("os.path.exists")
def test_get_ssl_context_success(mock_exists, mock_ssl_create, manager,
    mock_store):
    """
    Verifies that get_ssl_context correctly configures the context using the
    store's file path.
    """
    mock_store._file_path = "/tmp/private.key"
    mock_exists.return_value = True

    mock_context = MagicMock()
    mock_ssl_create.return_value = mock_context

    ctx = manager.get_ssl_context(cert_file="cert.crt", ca_file="ca.crt")

    assert ctx == mock_context
    mock_context.load_verify_locations.assert_called_once_with(cafile="ca.crt")
    mock_context.load_cert_chain.assert_called_once_with(
        certfile="cert.crt",
        keyfile="/tmp/private.key"
    )


def test_get_ssl_context_requires_file_store(manager):
    """
    Verifies that get_ssl_context raises ValueError if the KeyStore
    doesn't expose a file path (e.g. MemoryKeyStore).
    """
    with pytest.raises(ValueError, match="requires a FileKeyStore"):
        manager.get_ssl_context("cert.crt")
