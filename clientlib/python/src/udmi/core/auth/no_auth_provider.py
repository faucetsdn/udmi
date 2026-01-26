"""
Provides a Null Object implementation for Authentication.

This module is used when the device connects to a broker without authentication
(e.g., a local development broker or a test harness).
"""
from udmi.core.auth.intf.auth_provider import AuthProvider


class NoAuthProvider(AuthProvider):
    """
    An explicit 'No Authentication' strategy.

    Using this provider signals to the messaging client that it should
    connect without setting a username or password.
    """

    def get_username(self) -> str:
        """
        Returns an empty string for the username.
        """
        return ""

    def get_password(self) -> str:
        """
        Returns an empty string for the password.
        """
        return ""

    def needs_refresh(self) -> bool:
        """
        No Auth never expires.
        """
        return False
