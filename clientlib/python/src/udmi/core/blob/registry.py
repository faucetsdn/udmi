"""
Provides a registry for managing blob fetchers based on URL schemes.
"""
from typing import Dict
from urllib.parse import urlparse

from udmi.core.blob.fetcher import AbstractBlobFetcher
from udmi.core.blob.fetcher import DataUriFetcher
from udmi.core.blob.fetcher import HttpFetcher


class BlobFetcherRegistry:
    """
    Registry to map URL schemes (http, data, etc.) to specific fetcher implementations.
    """
    _registry: Dict[str, AbstractBlobFetcher] = {}

    @classmethod
    def register(cls, scheme: str, fetcher: AbstractBlobFetcher) -> None:
        """
        Registers a fetcher instance for a specific scheme.

        Args:
            scheme: The URI scheme (e.g., 'http', 'data').
            fetcher: The fetcher instance to handle this scheme.
        """
        cls._registry[scheme.lower()] = fetcher

    @classmethod
    def get_fetcher(cls, url: str) -> AbstractBlobFetcher:
        """
        Retrieves the appropriate fetcher for a given URL based on its scheme.

        Args:
            url: The full URL (e.g., 'http://example.com/file').

        Returns:
            The registered AbstractBlobFetcher for the URL's scheme.

        Raises:
            ValueError: If no fetcher is registered for the scheme.
        """
        if url.startswith("data:"):
            scheme = "data"
        else:
            parsed = urlparse(url)
            scheme = parsed.scheme.lower()

        fetcher = cls._registry.get(scheme)
        if not fetcher:
            raise ValueError(f"No fetcher registered for scheme: '{scheme}'")
        return fetcher

    @classmethod
    def initialize_defaults(cls):
        """
        Registers the default supported fetchers (data, http, https).
        """
        cls.register("data", DataUriFetcher())
        cls.register("http", HttpFetcher())
        cls.register("https", HttpFetcher())


BlobFetcherRegistry.initialize_defaults()
