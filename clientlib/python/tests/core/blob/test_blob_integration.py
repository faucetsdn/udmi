"""
Integration tests for Blob operations (Fetch + Verify + Parse).
"""

import hashlib
import json
from unittest.mock import MagicMock, patch

import pytest

from src.udmi.core.blob import get_verified_blob_bytes
from src.udmi.core.blob import parse_blob_as_object
from src.udmi.schema import BlobBlobsetConfig, BlobsetConfig, EndpointConfiguration


@pytest.fixture
def mock_fetcher():
    """Mocks the fetcher registry to return a controllable fetcher."""
    fetcher = MagicMock()

    with patch("udmi.core.blob.registry.BlobFetcherRegistry.get_fetcher", return_value=fetcher):
        yield fetcher


def test_get_verified_blob_bytes_success(mock_fetcher):
    """
    Verifies that if the hash matches, the bytes are returned.
    """
    content = b'{"status": "ok"}'
    valid_hash = hashlib.sha256(content).hexdigest()

    mock_fetcher.fetch.return_value = content

    config = BlobBlobsetConfig(url="http://test/blob", sha256=valid_hash)

    result = get_verified_blob_bytes(config)

    assert result == content


def test_get_verified_blob_bytes_mismatch_raises_error(mock_fetcher):
    """
    Verifies that if the hash does not match, ValueError is raised.
    """
    content = b"actual_content"
    bad_hash = hashlib.sha256(b"other").hexdigest()

    mock_fetcher.fetch.return_value = content

    config = BlobBlobsetConfig(url="http://test/blob", sha256=bad_hash)

    with pytest.raises(ValueError, match="Blob hash mismatch"):
        get_verified_blob_bytes(config)


def test_parse_blob_as_object_success(mock_fetcher):
    """
    Verifies parsing a blob into a specific UDMI schema class.
    """
    endpoint_data = {
        "hostname": "mqtt.googleapis.com",
        "client_id": "my_device"
    }
    content = json.dumps(endpoint_data).encode('utf-8')
    valid_hash = hashlib.sha256(content).hexdigest()

    mock_fetcher.fetch.return_value = content

    blob_config = BlobBlobsetConfig(url="data:...", sha256=valid_hash, generation="gen1")
    blobset = BlobsetConfig(blobs={"endpoint": blob_config})

    obj, generation = parse_blob_as_object(
        blobset_config=blobset,
        blob_key="endpoint",
        object_type=EndpointConfiguration
    )

    assert isinstance(obj, EndpointConfiguration)
    assert obj.hostname == "mqtt.googleapis.com"
    assert generation == "gen1"


def test_parse_blob_as_object_missing_key():
    """Verifies error if the requested key isn't in the blobset."""
    blobset = BlobsetConfig(blobs={"other_key": BlobBlobsetConfig()})

    with pytest.raises(KeyError, match="Blob key 'missing' not found"):
        parse_blob_as_object(blobset, "missing", EndpointConfiguration)


def test_parse_blob_as_object_json_error(mock_fetcher):
    """Verifies error handling for malformed JSON."""
    content = b"{ not valid json }"
    valid_hash = hashlib.sha256(content).hexdigest()
    mock_fetcher.fetch.return_value = content

    blob_config = BlobBlobsetConfig(url="http://x", sha256=valid_hash)
    blobset = BlobsetConfig(blobs={"bad_json": blob_config})

    with pytest.raises(ValueError, match="Failed to parse blob JSON"):
        parse_blob_as_object(blobset, "bad_json", EndpointConfiguration)