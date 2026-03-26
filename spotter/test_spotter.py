import hashlib
import json
import requests
import pytest
from unittest.mock import MagicMock, patch

from udmi.core.blob.fetcher import BlobFetchError, HttpFetcher
from udmi.core.blob.registry import BlobFetcherRegistry
from udmi.core.blob import get_verified_blob_bytes
from udmi.schema import BlobBlobsetConfig, BlobsetConfig

from spotter.spotter import SpotterDevice

# Create a dummy endpoint configuration
class DummyEndpoint:
    pass

@pytest.fixture
def spotter():
    endpoint = DummyEndpoint()
    # Mocking create_device so we don't try to connect
    with patch("spotter.spotter.create_device") as mock_create_device:
        mock_device = MagicMock()
        mock_sys_manager = MagicMock()
        mock_device.get_manager.return_value = mock_sys_manager
        mock_create_device.return_value = mock_device

        device = SpotterDevice(endpoint)
        return device

def test_1_happy_path(spotter):
    """1. Happy Path: Successful download, hash match, and version update."""
    valid_data = b"Some valid binary data COMMIT:newhash123"

    # 1. Test get_verified_blob_bytes logic
    mock_config = BlobBlobsetConfig(
        phase="apply",
        url="http://example.com/fw.bin",
        sha256=hashlib.sha256(valid_data).hexdigest(),
        generation="123"
    )

    mock_fetcher = MagicMock()
    mock_fetcher.fetch.return_value = valid_data

    with patch.object(BlobFetcherRegistry, 'get_fetcher', return_value=mock_fetcher):
        fetched_data = get_verified_blob_bytes(mock_config)
        assert fetched_data == valid_data

    # 2. Test Spotter process_update
    commit = spotter.process_update("firmware", valid_data)
    assert commit == "newhash123"

    # 3. Test Spotter apply_update (should raise SystemExit)
    with pytest.raises(SystemExit) as exc_info:
        spotter.apply_update("firmware", commit)
    assert exc_info.value.code == 0

def test_2_hash_mismatch():
    """2. Hash Mismatch: Detection of corrupted SHA256."""
    data = b"valid data but wrong hash"
    wrong_hash = hashlib.sha256(b"something else").hexdigest()

    mock_config = BlobBlobsetConfig(
        phase="apply",
        url="http://example.com/fw.bin",
        sha256=wrong_hash,
        generation="123"
    )

    mock_fetcher = MagicMock()
    mock_fetcher.fetch.return_value = data

    with patch.object(BlobFetcherRegistry, 'get_fetcher', return_value=mock_fetcher):
        with pytest.raises(ValueError, match="Blob hash mismatch"):
            get_verified_blob_bytes(mock_config)

def test_3_invalid_url():
    """3. Invalid URL: Handling of 403/404 errors."""
    mock_config = BlobBlobsetConfig(
        phase="apply",
        url="http://example.com/fw.bin",
        sha256=hashlib.sha256(b"abc").hexdigest(),
        generation="123"
    )

    fetcher = HttpFetcher()
    with patch('requests.get') as mock_get:
        mock_get.side_effect = requests.RequestException("404 Not Found")

        with patch.object(BlobFetcherRegistry, 'get_fetcher', return_value=fetcher):
            with pytest.raises(BlobFetchError, match="HTTP fetch failed"):
                get_verified_blob_bytes(mock_config)

def test_4_hardware_mismatch(spotter):
    """4. Hardware Mismatch: Rejection of incorrect bundles."""
    invalid_data = b"Some random data with WRONG_HARDWARE flag"

    with pytest.raises(ValueError, match="Hardware mismatch"):
        spotter.process_update("firmware", invalid_data)

def test_5_corrupted_payload(spotter):
    """5. Corrupted Payload: Trapping OS-level execution exceptions for malformed binaries."""
    corrupted_data = b"garbage data CORRUPTED_PAYLOAD more garbage"

    with pytest.raises(RuntimeError, match="OS execution exception"):
        spotter.process_update("firmware", corrupted_data)

def test_6_dependency_mismatch(spotter):
    """6. Dependency Mismatch: Validating that new modules are compatible with existing local dependencies."""
    invalid_data = b"Data with WRONG_DEPENDENCY inside"

    with pytest.raises(ValueError, match="Dependency mismatch"):
        spotter.process_update("firmware", invalid_data)
