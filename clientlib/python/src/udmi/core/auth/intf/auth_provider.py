"""
Defines the abstract base class (ABC) for authentication providers.

This module provides the core `AuthProvider` interface that all
authentication strategies (e.g., Basic Auth, JWT) must implement
to be used by messaging clients.
"""

from abc import ABC
from abc import abstractmethod


class AuthProvider(ABC):
    """
    Abstract base class for all authentication providers.
    Defines an interface for message clients to retrieve credentials.
    """

    @abstractmethod
    def get_username(self) -> str:
        """Returns the username for the MQTT connection."""

    @abstractmethod
    def get_password(self) -> str:
        """Returns the password for the MQTT connection."""

    @abstractmethod
    def needs_refresh(self) -> bool:
        """
        Returns True if the auth credentials (e.g., token) are
        expired or will expire soon and require a client refresh.
        """
