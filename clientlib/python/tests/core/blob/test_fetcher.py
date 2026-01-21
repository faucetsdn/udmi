"""
Unit tests for Blob Fetcher implementations.

This module verifies the behavior of DataUriFetcher, HttpFetcher, and FileFetcher.
"""

import base64
import os
from unittest.mock import MagicMock, patch, mock_open, ANY

import pytest
import requests

from udmi.core.blob.fetcher import BlobFetchError
from udmi.core.blob.fetcher import DataUriFetcher
from udmi.core.blob.fetcher import FileFetcher
from udmi.core.blob.fetcher import HttpFetcher


# --- DataUriFetcher Tests ---

@pytest.fixture
def data_fetcher():
    return DataUriFetcher()


def test_data_fetcher_decodes_base64(data_fetcher):
    """Verifies standard base64 decoding."""
    payload = b"Hello World"
    b64_str = base64.b64encode(payload).decode('utf-8')
    uri = f"data:text/plain;base64,{b64_str}"

    result = data_fetcher.fetch(uri)
    assert result == payload


def test_data_fetcher_raises_invalid_format(data_fetcher):
    """Verifies that missing comma or header raises BlobFetchError."""
    with pytest.raises(BlobFetchError, match="Invalid data URI"):
        data_fetcher.fetch("data:text/plain;base64")


def test_data_fetcher_raises_not_base64(data_fetcher):
    """Verifies that non-base64 encoding raises BlobFetchError."""
    with pytest.raises(BlobFetchError, match="must be base64"):
        data_fetcher.fetch("data:text/plain,HelloWorld")


def test_data_fetcher_raises_decode_error(data_fetcher):
    """Verifies that invalid base64 characters raise BlobFetchError."""
    with pytest.raises(BlobFetchError, match="Failed to decode"):
        data_fetcher.fetch("data:text/plain;base64,abc")


# --- HttpFetcher Tests ---

@pytest.fixture
def http_fetcher():
    return HttpFetcher()


@patch("requests.get")
def test_http_fetch_success(mock_get, http_fetcher):
    """Verifies successful HTTP GET."""
    mock_response = MagicMock()
    mock_response.content = b"http_data"
    mock_response.status_code = 200
    mock_get.return_value = mock_response

    result = http_fetcher.fetch("http://example.com/blob")

    assert result == b"http_data"
    mock_get.assert_called_once()


@patch("requests.get")
def test_http_fetch_http_error(mock_get, http_fetcher):
    """Verifies 404/500 errors raise BlobFetchError."""
    mock_response = MagicMock()
    mock_response.raise_for_status.side_effect = requests.HTTPError("404 Not Found")
    mock_get.return_value = mock_response

    with pytest.raises(BlobFetchError, match="HTTP fetch failed"):
        http_fetcher.fetch("http://example.com/missing")


@patch("requests.get")
def test_http_fetch_connection_error(mock_get, http_fetcher):
    """Verifies connection issues raise BlobFetchError."""
    mock_get.side_effect = requests.ConnectionError("Name resolution failure")

    with pytest.raises(BlobFetchError, match="HTTP fetch failed"):
        http_fetcher.fetch("http://bad-host.com")


@patch("os.fsync")
@patch("shutil.copyfileobj")
@patch("tempfile.NamedTemporaryFile")
@patch("requests.get")
@patch("os.replace")
@patch("os.chmod")
def test_http_download_to_file_streaming(
    mock_chmod, mock_replace, mock_get, mock_tempfile, mock_copy, mock_fsync, http_fetcher
):
    """
    Verifies that download_to_file streams data to a temp file and renames it.
    """
    mock_response = MagicMock()
    mock_response.raw = MagicMock()
    mock_get.return_value.__enter__.return_value = mock_response

    mock_tmp = MagicMock()
    mock_tmp.name = "/tmp/random_tmp_file"
    mock_tmp.fileno.return_value = 123
    mock_tempfile.return_value.__enter__.return_value = mock_tmp

    http_fetcher.download_to_file("http://site.com/large.bin", "/var/lib/final.bin")

    mock_get.assert_called_with("http://site.com/large.bin", stream=True, timeout=ANY, headers=ANY)

    mock_copy.assert_called_with(mock_response.raw, mock_tmp)

    mock_tmp.flush.assert_called_once()
    mock_fsync.assert_called_once_with(123)

    mock_replace.assert_called_with("/tmp/random_tmp_file", "/var/lib/final.bin")


# --- FileFetcher Tests ---

@pytest.fixture
def file_fetcher():
    return FileFetcher()


def test_file_fetch_reads_content(file_fetcher):
    """Verifies reading a local file."""
    with patch("builtins.open", mock_open(read_data=b"local_content")) as mock_file:
        result = file_fetcher.fetch("file:///etc/config.json")

        assert result == b"local_content"
        mock_file.assert_called_with("/etc/config.json", "rb")


def test_file_fetch_missing_file(file_fetcher):
    """Verifies FileNotFoundError is wrapped in BlobFetchError."""
    with patch("builtins.open", side_effect=FileNotFoundError("No entry")):
        with pytest.raises(BlobFetchError, match="File fetch failed"):
            file_fetcher.fetch("file:///missing.txt")