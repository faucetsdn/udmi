"""
Provides a JWT (JSON Web Token) based authentication provider.
"""

import logging
from dataclasses import dataclass
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from typing import Optional

import jwt
from jwt.exceptions import PyJWTError

from udmi.core.auth.auth_provider import AuthProvider

LOGGER = logging.getLogger(__name__)
DEFAULT_TOKEN_LIFETIME_MINUTES = 60
DEFAULT_TOKEN_REFRESH_BUFFER_MINUTES = 5


@dataclass
class JwtTokenConfig:
    """Configuration for JWT token lifetime and refresh."""
    lifetime_minutes: int = DEFAULT_TOKEN_LIFETIME_MINUTES
    refresh_buffer_minutes: int = DEFAULT_TOKEN_REFRESH_BUFFER_MINUTES


class JwtAuthProvider(AuthProvider):
    """
    An AuthProvider for JWT-based authentication, common for cloud platforms.
    Caches the token and handles proactive refresh.
    """

    def __init__(
        self,
        project_id: str,
        private_key_file: str,
        algorithm: str,
        token_config: JwtTokenConfig = JwtTokenConfig()
    ):
        self.audience = project_id
        self.algorithm = algorithm
        self.token_lifetime_minutes = token_config.lifetime_minutes
        self.token_refresh_buffer_minutes = token_config.refresh_buffer_minutes
        self._cached_token: Optional[str] = None
        self._token_expiry: Optional[datetime] = None
        try:
            with open(private_key_file, "r", encoding="utf-8") as f:
                self._private_key = f.read()
        except FileNotFoundError:
            LOGGER.error("Private key file not found at: %s", private_key_file)
            raise

    def get_username(self) -> str:
        """For JWT auth, the username field is typically not used."""
        return "unused"

    def _is_token_expiring_soon(self) -> bool:
        """Checks if the cached token is expired or expiring soon."""
        if not self._token_expiry:
            return True

        now_utc = datetime.now(tz=timezone.utc)
        refresh_threshold = self._token_expiry - timedelta(
            minutes=self.token_refresh_buffer_minutes
        )

        return now_utc >= refresh_threshold

    def get_password(self) -> str:
        """
        Generates a new, time-limited JWT if the cached one is expiring soon,
        otherwise returns the cached token.
        """
        if self._is_token_expiring_soon():
            LOGGER.info("Generating new JWT...")
            try:
                token_iat = datetime.now(tz=timezone.utc)
                token_exp = token_iat + timedelta(
                    minutes=self.token_lifetime_minutes
                )
                payload = {"iat": token_iat, "exp": token_exp,
                           "aud": self.audience}

                self._cached_token = jwt.encode(
                    payload, self._private_key, algorithm=self.algorithm
                )
                self._token_expiry = token_exp
                LOGGER.info("Generated new JWT, valid until %s UTC", token_exp)
            except (PyJWTError, TypeError) as e:
                LOGGER.error("Failed to generate new JWT: %s", e)
                raise
        else:
            LOGGER.debug("Reusing cached JWT.")

        if self._cached_token is None:
            raise RuntimeError("No valid JWT token available.")

        return self._cached_token

    def needs_refresh(self) -> bool:
        """
        Returns True if the auth token is expired or will expire soon.
        """
        return self._is_token_expiring_soon()
