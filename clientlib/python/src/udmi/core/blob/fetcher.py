"""
Defines the interface and concrete implementations for fetching blobs
from various sources (Data URIs, HTTP/HTTPS).
"""

import abc
import base64
import logging
import shutil

import requests

LOGGER = logging.getLogger(__name__)


class BlobFetchError(Exception):
    """
    Exception raised when a blob cannot be fetched or decoded.
    """


class AbstractBlobFetcher(abc.ABC):
    """
    Abstract interface for fetching blob data from a URL.
    """

    # pylint: disable=too-few-public-methods

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
        Downloads content to a specific file path using streaming.
        Default implementation for non-streaming fetchers (like DataUri)
        calls fetch() and writes to disk. Subclasses should override
        for efficiency.
        """
        data = self.fetch(url)
        with open(dest_path, "wb") as f:
            f.write(data)


class DataUriFetcher(AbstractBlobFetcher):
    """
    Fetcher implementation for handling 'data:' URIs (base64 encoded).
    """

    # pylint: disable=too-few-public-methods

    def fetch(self, url: str) -> bytes:
        """
        Parses and decodes a base64 data URI.

        Args:
            url: The data URI string (e.g., 'data:text/plain;base64,...').
        """
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

    # pylint: disable=too-few-public-methods

    def __init__(self, timeout_sec: int = 30):
        """
        Initializes the HTTP fetcher.

        Args:
            timeout_sec: The request timeout in seconds.
        """
        self.timeout = timeout_sec

    def fetch(self, url: str) -> bytes:
        """
        Performs an HTTP GET request to retrieve the content.
        """
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
        try:
            LOGGER.info("Streaming blob to file: %s", url)
            headers = {'User-Agent': 'udmi-python-device/1.0'}
            with requests.get(url, stream=True, timeout=(10, self.timeout),
                              headers=headers) as r:
                r.raise_for_status()
                with open(dest_path, 'wb') as f:
                    shutil.copyfileobj(r.raw, f)
        except Exception as e:
            raise BlobFetchError(f"HTTP stream failed: {e}") from e


class FileFetcher(AbstractBlobFetcher):
    """
    Fetcher for local file:// URLs.
    """
    def _parse_path(self, url: str) -> str:
        return url.replace("file://", "")

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
            shutil.copy(src_path, dest_path)
        except Exception as e:
            raise BlobFetchError(f"File copy failed: {e}") from e