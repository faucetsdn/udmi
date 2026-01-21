"""
Unit tests for the `FileKeyStore` class.

This module tests the file-based key storage backend.
"""

from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from src.udmi.core.auth.file_key_store import FileKeyStore


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def store():
    """Returns a FileKeyStore instance pointing to a dummy path."""
    return FileKeyStore("/tmp/device_key.pem")


@patch("src.udmi.core.auth.file_key_store.atomic_write")
def test_save_uses_atomic_write(mock_atomic_write, store):
    """
    Verifies that save delegates to atomic_write with secure permissions.
    """
    data = b"secret_key_data"
    store.save(data)

    mock_atomic_write.assert_called_once_with(
        "/tmp/device_key.pem",
        data,
        mode=0o600
    )


@patch("builtins.open", new_callable=MagicMock)
def test_load_reads_file(mock_open, store):
    """
    Verifies that load reads bytes from the file.
    """
    mock_file = MagicMock()
    mock_file.read.return_value = b"loaded_data"
    mock_open.return_value.__enter__.return_value = mock_file

    data = store.load()

    assert data == b"loaded_data"
    mock_open.assert_called_once_with("/tmp/device_key.pem", "rb")


@patch("os.path.exists")
def test_exists_checks_path(mock_exists, store):
    """
    Verifies exists() delegates to os.path.exists.
    """
    mock_exists.return_value = True
    assert store.exists() is True
    mock_exists.assert_called_once_with("/tmp/device_key.pem")


@patch("shutil.copy2")
@patch("os.path.exists")
def test_backup_creates_copy(mock_exists, mock_copy, store):
    """
    Verifies backup creates a copy of the key file.
    """
    mock_exists.return_value = True

    with patch("src.udmi.core.auth.file_key_store.datetime") as mock_dt:
        mock_dt.now.return_value.strftime.return_value = "20250101"

        backup_path = store.backup()

        assert backup_path == "/tmp/device_key.pem20250101.bak"
        mock_copy.assert_called_with("/tmp/device_key.pem", backup_path)

    store.backup("custom.bak")
    mock_copy.assert_called_with("/tmp/device_key.pem", "custom.bak")


@patch("os.path.exists")
def test_backup_raises_if_source_missing(mock_exists, store):
    """
    Verifies backup raises FileNotFoundError if key doesn't exist.
    """
    mock_exists.return_value = False

    with pytest.raises(FileNotFoundError, match="Cannot backup missing key"):
        store.backup()


@patch("src.udmi.core.auth.file_key_store.atomic_write")
@patch("builtins.open", new_callable=MagicMock)
@patch("os.path.exists")
def test_restore_overwrites_key(mock_exists, mock_open, mock_atomic_write,
    store):
    """
    Verifies restore reads backup and overwrites current key.
    """
    mock_exists.return_value = True  # Backup exists

    mock_file = MagicMock()
    mock_file.read.return_value = b"restored_data"
    mock_open.return_value.__enter__.return_value = mock_file

    store.restore_from_backup("backup.pem")

    mock_open.assert_called_once_with("backup.pem", "rb")

    mock_atomic_write.assert_called_once_with(
        "/tmp/device_key.pem",
        b"restored_data",
        mode=0o600
    )


@patch("os.path.exists")
def test_restore_raises_if_backup_missing(mock_exists, store):
    """
    Verifies restore raises FileNotFoundError if backup doesn't exist.
    """
    mock_exists.return_value = False

    with pytest.raises(FileNotFoundError, match="Backup file not found"):
        store.restore_from_backup("missing.bak")
