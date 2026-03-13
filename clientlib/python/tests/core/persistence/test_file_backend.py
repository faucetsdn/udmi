"""
Unit tests for the `FilePersistenceBackend` class.

This module verifies the JSON storage engine, including corruption handling.
"""

import json
from unittest.mock import mock_open
from unittest.mock import patch

import pytest

from src.udmi.core.persistence.file_backend import FilePersistenceBackend


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def mock_atomic_write():
    with patch("src.udmi.core.persistence.file_backend.atomic_write") as m:
        yield m


@pytest.fixture
def backend(mock_atomic_write):
    """Returns a backend initialized with an empty file."""
    with patch("os.path.exists", return_value=False):
        return FilePersistenceBackend("/tmp/state.json")


def test_load_valid_json():
    """Verifies that valid JSON is loaded into the cache."""
    valid_data = json.dumps({"key": "value"})

    with patch("os.path.exists", return_value=True):
        with patch("builtins.open", mock_open(read_data=valid_data)):
            backend = FilePersistenceBackend("/tmp/state.json")

    assert backend.load("key") == "value"


def test_load_missing_file_starts_empty():
    """Verifies that a missing file results in an empty cache."""
    with patch("os.path.exists", return_value=False):
        backend = FilePersistenceBackend("/tmp/missing.json")

    assert backend._cache == {}
    assert backend.load("any") is None


def test_load_corrupt_file_triggers_backup(caplog):
    """
    Verifies that malformed JSON triggers a backup and resets the cache.
    """
    corrupt_data = "{ invalid_json: "

    with patch("os.path.exists", return_value=True):
        with patch("builtins.open", mock_open(read_data=corrupt_data)):
            with patch("shutil.copy2") as mock_copy:
                backend = FilePersistenceBackend("/tmp/corrupt.json")

                mock_copy.assert_called_once_with(
                    "/tmp/corrupt.json",
                    "/tmp/corrupt.json.corrupt"
                )

    assert backend._cache == {}
    assert "CORRUPTION DETECTED" in caplog.text


def test_save_writes_atomically(backend, mock_atomic_write):
    """Verifies that save serializes data and uses atomic_write."""
    backend.save("new_key", 123)

    expected_json = json.dumps({"new_key": 123}, indent=2)
    mock_atomic_write.assert_called_once_with("/tmp/state.json", expected_json)


def test_delete_removes_key_and_flushes(backend, mock_atomic_write):
    """Verifies that delete updates cache and writes to disk."""
    backend._cache = {"key1": "val1", "key2": "val2"}

    backend.delete("key1")

    assert "key1" not in backend._cache
    assert backend.load("key2") == "val2"

    expected_json = json.dumps({"key2": "val2"}, indent=2)
    mock_atomic_write.assert_called_once_with("/tmp/state.json", expected_json)


def test_load_all_returns_copy(backend):
    """Verifies load_all returns the full dict."""
    backend._cache = {"a": 1, "b": 2}
    result = backend.load_all()
    assert result == {"a": 1, "b": 2}

    result["a"] = 999
    assert backend.load("a") == 1
