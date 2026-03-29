"""
Unit tests for the BlobFetcherRegistry.
"""

from unittest.mock import MagicMock

import pytest

from src.udmi.core.blob.registry import BlobFetcherRegistry
from src.udmi.core.blob.fetcher import AbstractBlobFetcher


# pylint: disable=protected-access

@pytest.fixture(autouse=True)
def reset_registry():
    """Resets the registry before and after each test."""
    BlobFetcherRegistry.reset()
    yield
    BlobFetcherRegistry.reset()


def test_register_and_get_custom_fetcher():
    """Verifies manual registration."""
    mock_fetcher = MagicMock(spec=AbstractBlobFetcher)
    BlobFetcherRegistry.register("custom", mock_fetcher)

    fetcher = BlobFetcherRegistry.get_fetcher("custom://resource")
    assert fetcher == mock_fetcher


def test_get_fetcher_initializes_defaults():
    """
    Verifies that calling get_fetcher on an empty registry triggers
    default initialization (lazy load).
    """
    assert not BlobFetcherRegistry._initialized

    fetcher = BlobFetcherRegistry.get_fetcher("http://google.com")

    assert BlobFetcherRegistry._initialized
    assert fetcher is not None
    from udmi.core.blob.fetcher import HttpFetcher
    assert isinstance(fetcher, HttpFetcher)


def test_get_fetcher_case_insensitive():
    """Verifies that scheme lookups ignore case."""
    mock_fetcher = MagicMock(spec=AbstractBlobFetcher)
    BlobFetcherRegistry.register("ftp", mock_fetcher)

    # Upper case URL scheme
    fetcher = BlobFetcherRegistry.get_fetcher("FTP://server/file")
    assert fetcher == mock_fetcher


def test_get_fetcher_handles_data_uri():
    """Verifies special handling for data: URIs (no double slash)."""
    BlobFetcherRegistry.initialize_defaults()

    fetcher = BlobFetcherRegistry.get_fetcher("data:text/plain;base64,YWJj")

    from udmi.core.blob.fetcher import DataUriFetcher
    assert isinstance(fetcher, DataUriFetcher)


def test_get_fetcher_unknown_scheme_raises_error():
    """Verifies that unknown schemes raise ValueError."""
    BlobFetcherRegistry.initialize_defaults()

    with pytest.raises(ValueError, match="No fetcher registered"):
        BlobFetcherRegistry.get_fetcher("unknown://resource")