"""
Utilities for fetching and verifying blobs (binary large objects)
used in UDMI configuration.
"""

import hashlib

from udmi.core.blob.fetcher import BlobFetchError
from udmi.core.blob.registry import BlobFetcherRegistry
from udmi.schema import BlobBlobsetConfig


def get_verified_blob_bytes(blob_config: BlobBlobsetConfig) -> bytes:
    """
    Fetches blob data from the URL specified in the config and validates
    it against the provided SHA256 hash.

    Args:
        blob_config: The configuration object containing the URL and expected hash.

    Returns:
        The raw bytes of the fetched blob.

    Raises:
        ValueError: If fetching fails or if the SHA256 hash does not match.
    """
    url = blob_config.url
    sha256_expected = blob_config.sha256

    try:
        fetcher = BlobFetcherRegistry.get_fetcher(url)
        data_bytes = fetcher.fetch(url)
    except (ValueError, BlobFetchError) as e:
        raise ValueError(f"Blob fetch failed for {url}: {e}") from e

    sha256_actual = hashlib.sha256(data_bytes).hexdigest()
    if sha256_actual.lower() != sha256_expected.lower():
        raise ValueError(
            f"Blob hash mismatch! Expected {sha256_expected}, got {sha256_actual}")

    return data_bytes
