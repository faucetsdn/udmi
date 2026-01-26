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
        """
        Returns the username for the MQTT connection.

        Returns:
            A string username. If the specific authentication strategy
            does not use a username (e.g., some token-based schemes),
            this should return a valid placeholder string (e.g., 'unused').
        """

    @abstractmethod
    def get_password(self) -> str:
        """
        Returns the password for the MQTT connection.

        Returns:
            A string representing the password, token, or secret.
        """

    @abstractmethod
    def needs_refresh(self) -> bool:
        """
        Checks if the credentials need to be refreshed.

        Returns:
            True if the auth credentials (e.g., a JWT token) are
            expired or will expire soon and require a client reconnect/refresh.
            False otherwise.
        """
