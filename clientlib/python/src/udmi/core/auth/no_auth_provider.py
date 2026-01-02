"""
Provides a Null Object implementation for Authentication.
Used when the device connects to a broker without auth (e.g. local dev).
"""
import logging
from udmi.core.auth.intf import AuthProvider

LOGGER = logging.getLogger(__name__)


class NoAuthProvider(AuthProvider):
    """
    An explicit 'No Authentication' strategy.

    Using this provider signals to the messaging client that it should
    connect without setting a username or password, and suppress
    'missing auth' warnings.
    """

    def get_username(self) -> str:
        return ""

    def get_password(self) -> str:
        return ""

    def needs_refresh(self) -> bool:
        return False
