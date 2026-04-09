"""
Defines the interface and concrete implementations for fetching blobs.

This module provides a strategy pattern for retrieving binary data from
various sources (Data URIs, HTTP/HTTPS, local filesystem).
"""

import abc
import base64
import logging
import os
import shutil
import time
from urllib.parse import urlparse

import requests

from udmi.core.utils.file_ops import atomic_write
from udmi.core.utils.file_ops import atomic_file_context

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
    Supports HTTP Range requests, exponential backoff for retryable errors (e.g. 503),
    and immediate aborts for fatal errors (e.g. 403, 404).
    """

    def __init__(self, timeout_sec: int = 30, max_retries: int = 5, backoff_sec: float = 1.0):
        self.timeout = timeout_sec
        self.max_retries = max_retries
        self.backoff_sec = backoff_sec

    def fetch(self, url: str) -> bytes:
        """
        Fetches the raw bytes from the given URL entirely into memory.
        Uses a resumable streaming approach under the hood if partially complete.
        """
        import tempfile
        tmp_fd, tmp_path = tempfile.mkstemp()
        os.close(tmp_fd)
        try:
            self.download_to_file(url, tmp_path)
            with open(tmp_path, 'rb') as f:
                return f.read()
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def download_to_file(self, url: str, dest_path: str) -> None:
        """
        Streams content to a temporary file, supporting resumes via Range headers.
        Uses exponential backoff for retryable errors (e.g. 503, connection drops).
        Raises BlobFetchError immediately for fatal errors (e.g. 403, 404).
        """
        headers = {'User-Agent': 'udmi-python-device/1.0'}

        retries = 0
        backoff_sec = self.backoff_sec

        with atomic_file_context(dest_path) as f:
            tmp_path = f.name
            while retries <= self.max_retries:
                try:
                    # Check if we have partially downloaded the file
                    downloaded_bytes = 0
                    if os.path.exists(tmp_path):
                        downloaded_bytes = os.path.getsize(tmp_path)

                    if downloaded_bytes > 0:
                        LOGGER.info("Resuming download from byte %d for %s", downloaded_bytes, url)
                        headers['Range'] = f'bytes={downloaded_bytes}-'
                    else:
                        LOGGER.info("Starting new download for %s", url)
                        headers.pop('Range', None)

                    with requests.get(url, stream=True, timeout=(10, self.timeout), headers=headers) as r:
                        if r.status_code in (401, 403, 404):
                            # Fatal Auth/Net error
                            LOGGER.error("Fatal HTTP Error %d for %s. Aborting.", r.status_code, url)
                            raise BlobFetchError(f"HTTP fetch failed: {r.status_code}")

                        if r.status_code == 416:
                            # Range Not Satisfiable - already fully downloaded or invalid range
                            LOGGER.info("Range not satisfiable (already downloaded or invalid) for %s", url)
                            # Check content length to verify
                            head_r = requests.head(url, timeout=(10, self.timeout), headers={'User-Agent': 'udmi-python-device/1.0'})
                            total_size = int(head_r.headers.get('content-length', 0))
                            if total_size > 0 and downloaded_bytes >= total_size:
                                LOGGER.info("File already fully downloaded.")
                                break
                            else:
                                # Start over
                                downloaded_bytes = 0
                                # Clear file content for retry
                                f.seek(0)
                                f.truncate()
                                continue

                        r.raise_for_status()

                        # Check if the server respected the Range request. If not (returns 200 OK instead of 206 Partial Content),
                        # we must start over from the beginning, otherwise we will append the entire file again, causing corruption.
                        if r.status_code == 200 and downloaded_bytes > 0:
                            LOGGER.warning("Server ignored Range request and returned 200 OK. Downloading from scratch.")
                            downloaded_bytes = 0

                        # we are inside atomic_file_context which opens the temp file for us, we can just write to it
                        if downloaded_bytes == 0:
                            f.seek(0)
                            f.truncate()
                        else:
                            f.seek(downloaded_bytes)

                        for chunk in r.iter_content(chunk_size=8192):
                            if chunk:
                                f.write(chunk)

                    # Download completed successfully
                    break

                except requests.exceptions.HTTPError as e:
                    status = e.response.status_code
                    if status in (401, 403, 404):
                        raise BlobFetchError(f"HTTP fetch failed: {status}") from e
                    elif status == 503:
                        LOGGER.warning("HTTP 503 Service Unavailable for %s. Retrying...", url)
                    else:
                        LOGGER.warning("HTTP Error %d for %s. Retrying...", status, url)

                    self._handle_retry(retries, backoff_sec, e)
                    retries += 1
                    backoff_sec *= 2

                except requests.exceptions.RequestException as e:
                    LOGGER.warning("Network error during fetch of %s: %s. Retrying...", url, e)
                    self._handle_retry(retries, backoff_sec, e)
                    retries += 1
                    backoff_sec *= 2

            if retries > self.max_retries:
                raise BlobFetchError(f"HTTP fetch failed: Max retries exceeded")


    def _handle_retry(self, retries: int, backoff_sec: float, exc: Exception):
        if retries >= self.max_retries:
            LOGGER.error("Max retries (%d) reached. Aborting.", self.max_retries)
            raise BlobFetchError("HTTP fetch failed: Max retries exceeded") from exc
        LOGGER.info("Backing off for %f seconds...", backoff_sec)
        time.sleep(backoff_sec)


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
