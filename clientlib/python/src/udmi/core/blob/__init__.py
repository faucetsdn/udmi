"""
Utilities for fetching and verifying blobs (binary large objects).

This module provides high-level functions to retrieve blob data referenced
in UDMI configurations and verify their integrity using SHA256 checksums.
"""

import hashlib
import json
import logging
import os
from typing import Tuple
from typing import Type
from typing import TypeVar

from udmi.core.blob.fetcher import BlobFetchError
from udmi.core.blob.registry import BlobFetcherRegistry
from udmi.schema import BlobBlobsetConfig
from udmi.schema import BlobsetConfig
from udmi.schema import DataModel

T = TypeVar("T", bound=DataModel)
LOGGER = logging.getLogger(__name__)


def get_verified_blob_bytes(blob_config: BlobBlobsetConfig) -> bytes:
    """
    Fetches blob data from the URL and validates it against the SHA256 hash.

    Args:
        blob_config: The configuration object containing 'url' and 'sha256'.

    Returns:
        The raw bytes of the fetched blob.

    Raises:
        ValueError: If the SHA256 hash does not match (Integrity Error).
        BlobFetchError: If the network/file fetch operation fails.
    """
    url = blob_config.url
    sha256_expected = blob_config.sha256

    if not url or not sha256_expected:
        raise ValueError("Blob config missing required 'url' or 'sha256'.")

    fetcher = BlobFetcherRegistry.get_fetcher(url)
    data_bytes = fetcher.fetch(url)

    sha256_actual = hashlib.sha256(data_bytes).hexdigest()
    if sha256_actual.lower() != sha256_expected.lower():
        raise ValueError(
            f"Blob hash mismatch for {url}! "
            f"Expected {sha256_expected}, got {sha256_actual}"
        )

    return data_bytes


def get_verified_blob_file(blob_config: BlobBlobsetConfig,
    dest_path: str) -> None:
    """
    Downloads blob to a file and validates SHA256 without loading into RAM.

    Args:
        blob_config: The blob configuration.
        dest_path: The target file path on disk.

    Raises:
        ValueError: If integrity check fails.
        BlobFetchError: If download fails.
    """
    url = blob_config.url
    sha256_expected = blob_config.sha256

    if not url or not sha256_expected:
        raise ValueError("Blob config missing required 'url' or 'sha256'.")

    try:
        fetcher = BlobFetcherRegistry.get_fetcher(url)
        LOGGER.info("Downloading blob to %s...", dest_path)

        fetcher.download_to_file(url, dest_path)

        LOGGER.debug("Verifying SHA256 hash...")
        sha256_actual = _calculate_file_hash(dest_path)

        if sha256_actual.lower() != sha256_expected.lower():
            raise ValueError(
                f"Blob hash mismatch! Expected {sha256_expected}, "
                f"got {sha256_actual}"
            )

        LOGGER.info("Blob verified successfully.")

    except Exception:
        if os.path.exists(dest_path):
            os.remove(dest_path)
        raise


def _calculate_file_hash(filepath: str) -> str:
    """Helper to calculate SHA256 of a file in chunks."""
    sha256 = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(65536):  # 64KB chunks
            sha256.update(chunk)
    return sha256.hexdigest()


def parse_blob_as_object(
    blobset_config: BlobsetConfig,
    blob_key: str,
    object_type: Type[T]
) -> Tuple[T, str]:
    """
    Fetches, verifies, and parses a JSON blob into a UDMI Schema object.

    Args:
        blobset_config: The parent blobset configuration.
        blob_key: The specific key in 'blobs' to fetch.
        object_type: The target Schema Class (e.g. IotEndpointConfig).

    Returns:
        A tuple of (ParsedObject, GenerationString).
    """
    if not blobset_config.blobs:
        raise ValueError("blobset configuration has no blobs.")

    if blob_key not in blobset_config.blobs:
        raise KeyError(
            f"Blob key '{blob_key}' not found in blobset config."
        )

    blob_config = blobset_config.blobs[blob_key]

    try:
        data_bytes = get_verified_blob_bytes(blob_config)
        json_str = data_bytes.decode("utf-8")
        obj_dict = json.loads(json_str)

        return object_type.from_dict(obj_dict), blob_config.generation

    except (json.JSONDecodeError, UnicodeDecodeError) as e:
        raise ValueError(f"Failed to parse blob JSON: {e}") from e
