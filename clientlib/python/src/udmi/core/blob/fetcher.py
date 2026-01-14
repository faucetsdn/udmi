"""
Defines the interface and concrete implementations for fetching blobs.

This module provides a strategy pattern for retrieving binary data from
various sources (Data URIs, HTTP/HTTPS, local filesystem).
"""

import abc
import base64
import logging
import shutil
from urllib.parse import urlparse

import requests

from udmi.core.utils.file_ops import atomic_write

LOGGER = logging.getLogger(__name__)


class BlobFetchError(Exception):
    """
    Exception raised when a blob cannot be fetched or decoded.
    """


class AbstractBlobFetcher(abc.ABC):
    """
    Abstract interface for fetching blob data from a URL.
    """

    @abc.abstractmethod
    def fetch(self, url: str) -> bytes:
        """
        Fetches the raw bytes from the given URL.

        Args:
            url: The source URL string.

        Returns:
            The raw bytes of the fetched content.

        Raises:
            BlobFetchError: If the content cannot be retrieved.
        """

    def download_to_file(self, url: str, dest_path: str) -> None:
        """
        Downloads content to a specific file path.

        This default implementation fetches all bytes into memory and then
        atomically writes them to disk. Subclasses should override this
        if streaming is possible to avoid high memory usage.
        """
        try:
            data = self.fetch(url)
            atomic_write(dest_path, data)
        except Exception as e:
            if not isinstance(e, BlobFetchError):
                raise BlobFetchError(f"Download failed: {e}") from e
            raise


class DataUriFetcher(AbstractBlobFetcher):
    """
    Fetcher implementation for handling 'data:' URIs (base64 encoded).
    """

    def fetch(self, url: str) -> bytes:
        try:
            if "," not in url:
                raise ValueError("Invalid data URI format")
            header, base64_str = url.split(",", 1)
            if "base64" not in header:
                raise ValueError("Data URI must be base64 encoded")
            return base64.b64decode(base64_str)
        except Exception as e:
            raise BlobFetchError(f"Failed to decode data URI: {e}") from e


class HttpFetcher(AbstractBlobFetcher):
    """
    Fetcher implementation for handling standard HTTP/HTTPS URLs.
    """

    def __init__(self, timeout_sec: int = 30):
        self.timeout = timeout_sec

    def fetch(self, url: str) -> bytes:
        try:
            LOGGER.info("Fetching blob via HTTP: %s", url)
            headers = {'User-Agent': 'udmi-python-device/1.0'}
            response = requests.get(url, timeout=(10, self.timeout),
                                    headers=headers)
            response.raise_for_status()
            return response.content
        except requests.RequestException as e:
            raise BlobFetchError(f"HTTP fetch failed: {e}") from e

    def download_to_file(self, url: str, dest_path: str) -> None:
        """
        Streams content to a temporary file and atomically renames it to dest.
        """
        try:
            LOGGER.info("Streaming blob to file: %s", url)
            headers = {'User-Agent': 'udmi-python-device/1.0'}

            import os
            import tempfile

            dest_dir = os.path.dirname(dest_path) or "."
            os.makedirs(dest_dir, exist_ok=True)

            with requests.get(url, stream=True, timeout=(10, self.timeout),
                              headers=headers) as r:
                r.raise_for_status()
                with tempfile.NamedTemporaryFile(mode='wb', dir=dest_dir,
                                                 delete=False) as tmp_file:
                    tmp_name = tmp_file.name
                    try:
                        shutil.copyfileobj(r.raw, tmp_file)
                        tmp_file.flush()
                        os.fsync(tmp_file.fileno())
                        tmp_file.close()

                        os.chmod(tmp_name, 0o600)

                        os.replace(tmp_name, dest_path)
                    except Exception:
                        tmp_file.close()
                        if os.path.exists(tmp_name):
                            os.unlink(tmp_name)
                        raise

        except Exception as e:
            raise BlobFetchError(f"HTTP stream failed: {e}") from e


class FileFetcher(AbstractBlobFetcher):
    """
    Fetcher for local file:// URLs.
    """

    def _parse_path(self, url: str) -> str:
        parsed = urlparse(url)
        return parsed.path

    def fetch(self, url: str) -> bytes:
        path = self._parse_path(url)
        try:
            with open(path, "rb") as f:
                return f.read()
        except Exception as e:
            raise BlobFetchError(f"File fetch failed: {e}") from e

    def download_to_file(self, url: str, dest_path: str) -> None:
        src_path = self._parse_path(url)
        try:
            shutil.copy2(src_path, dest_path)
        except Exception as e:
            raise BlobFetchError(f"File copy failed: {e}") from e
