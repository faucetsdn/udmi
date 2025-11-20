"""
Provides a username/password based authentication provider.
"""

from udmi.core.auth.auth_provider import AuthProvider


class BasicAuthProvider(AuthProvider):
    """
    An AuthProvider for basic username and password authentication.
    """

    def __init__(self, username: str, password: str):
        self._username = username
        self._password = password

    def get_username(self) -> str:
        return self._username

    def get_password(self) -> str:
        return self._password

    def needs_refresh(self) -> bool:
        return False
