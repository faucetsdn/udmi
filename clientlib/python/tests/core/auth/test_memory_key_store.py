"""
Unit tests for the `MemoryKeyStore` class.

This module tests the in-memory key storage backend.
"""

import pytest

from src.udmi.core.auth.memory_key_store import MemoryKeyStore


# pylint: disable=redefined-outer-name

@pytest.fixture
def store():
    """Returns an empty MemoryKeyStore."""
    return MemoryKeyStore()


def test_init_empty(store):
    """Verifies initialization without data."""
    assert store.exists() is False
    with pytest.raises(FileNotFoundError, match="No key data"):
        store.load()


def test_init_with_data():
    """Verifies initialization with pre-populated data."""
    data = b"initial_secret"
    store = MemoryKeyStore(initial_data=data)

    assert store.exists() is True
    assert store.load() == data


def test_save_and_load(store):
    """Verifies saving data makes it available for loading."""
    data = b"new_secret"
    store.save(data)

    assert store.exists() is True
    assert store.load() == data


def test_backup_creates_snapshot(store):
    """
    Verifies backup saves a copy of the current data.
    """
    original_data = b"original"
    store.save(original_data)

    identifier = store.backup("snap1")
    assert identifier == "memory:snap1"

    store.save(b"modified")
    assert store.load() == b"modified"

    store.restore_from_backup("snap1")
    assert store.load() == original_data


def test_backup_default_name(store):
    """Verifies backup works with default naming."""
    store.save(b"data")
    identifier = store.backup()

    assert identifier == "memory:.bak"

    store.save(b"changed")
    store.restore_from_backup(".bak")
    assert store.load() == b"data"


def test_backup_raises_if_empty(store):
    """Verifies backup cannot be created if store is empty."""
    assert store.exists() is False
    with pytest.raises(FileNotFoundError, match="Cannot backup empty store"):
        store.backup("fail_snap")


def test_restore_raises_if_missing(store):
    """Verifies restore fails if backup ID doesn't exist."""
    store.save(b"data")

    with pytest.raises(FileNotFoundError, match="Backup missing not found"):
        store.restore_from_backup("missing")


def test_restore_raises_if_name_empty(store):
    """Verifies restore fails if name provided is falsy."""
    store.save(b"data")

    with pytest.raises(FileNotFoundError):
        store.restore_from_backup("")
