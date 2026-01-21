"""
Provides a username/password based authentication provider.

This module implements the AuthProvider interface for standard
Basic Authentication schemes.
"""
from typing import Final

from udmi.core.auth.intf.auth_provider import AuthProvider


class BasicAuthProvider(AuthProvider):
    """
    An AuthProvider for basic username and password authentication.

    This provider holds static credentials in memory. It assumes credentials
    do not expire or require refreshing during the application lifecycle.
    """

    def __init__(self, username: str, password: str):
        """
        Initializes the BasicAuthProvider.

        Args:
            username: The username string.
            password: The password or secret string.
        """
        self._username: Final[str] = username
        self._password: Final[str] = password

    def get_username(self) -> str:
        return self._username

    def get_password(self) -> str:
        return self._password

    def needs_refresh(self) -> bool:
        """
        Always returns False for Basic Auth as credentials are static.
        """
        return False

    def __repr__(self) -> str:
        """
        Returns a string representation of the provider, masking the password.
        """
        return f"BasicAuthProvider(username='{self._username}', password='***')"
