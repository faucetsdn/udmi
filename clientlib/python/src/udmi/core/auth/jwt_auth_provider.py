"""
Provides a JWT (JSON Web Token) based authentication provider.
"""

import logging
import base64
import json
import threading
from dataclasses import dataclass
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from typing import Optional

from udmi.core.auth.intf import Signer
from udmi.core.auth.intf import AuthProvider

LOGGER = logging.getLogger(__name__)

DEFAULT_TOKEN_LIFETIME_MINUTES = 60
DEFAULT_TOKEN_REFRESH_BUFFER_MINUTES = 5
DEFAULT_CLOCK_SKEW_SECONDS = 60


@dataclass
class JwtTokenConfig:
    """Configuration for JWT token lifetime and refresh."""
    lifetime_minutes: int = DEFAULT_TOKEN_LIFETIME_MINUTES
    refresh_buffer_minutes: int = DEFAULT_TOKEN_REFRESH_BUFFER_MINUTES
    clock_skew_seconds: int = DEFAULT_CLOCK_SKEW_SECONDS


class JwtAuthProvider(AuthProvider):
    """
    An AuthProvider for JWT-based authentication, common for cloud platforms.
    Caches the token and handles proactive refresh.
    """

    def __init__(
        self,
        project_id: str,
        signer: Signer,
        algorithm: str,
        token_config: JwtTokenConfig = None
    ):
        token_config = token_config or JwtTokenConfig()

        self.audience = project_id
        self.signer = signer
        self.algorithm = algorithm
        self.token_lifetime_minutes = token_config.lifetime_minutes
        self.token_refresh_buffer_minutes = token_config.refresh_buffer_minutes
        self.clock_skew_seconds = token_config.clock_skew_seconds

        self._cached_token: Optional[str] = None
        self._token_expiry: Optional[datetime] = None
        self._lock = threading.Lock()

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
            with self._lock:
                if self._is_token_expiring_soon():
                    LOGGER.info("Generating new JWT...")
                    try:
                        self._cached_token = self._generate_jwt()
                        LOGGER.info("Generated new JWT, valid until %s UTC",
                                    self._token_expiry)
                    except Exception as e:
                        LOGGER.error("Failed to generate new JWT: %s", e)
                        raise
        return self._cached_token

    def needs_refresh(self) -> bool:
        """
        Returns True if the auth token is expired or will expire soon.
        """
        return self._is_token_expiring_soon()

    def _generate_jwt(self) -> str:
        """
        Manually constructs the JWT and delegates signing to the Signer interface.
        """
        now_utc = datetime.now(tz=timezone.utc)
        token_iat = now_utc - timedelta(seconds=self.clock_skew_seconds)
        token_exp = token_iat + timedelta(minutes=self.token_lifetime_minutes)

        self._token_expiry = token_exp

        header = {
            "alg": self.algorithm,
            "typ": "JWT"
        }

        payload = {
            "iat": int(token_iat.timestamp()),
            "exp": int(token_exp.timestamp()),
            "aud": self.audience
        }

        header_b64 = self._base64url_encode(json.dumps(header).encode("utf-8"))
        payload_b64 = self._base64url_encode(
            json.dumps(payload).encode("utf-8"))

        signing_input = f"{header_b64}.{payload_b64}".encode("ascii")

        signature_bytes = self.signer.sign(signing_input)

        signature_b64 = self._base64url_encode(signature_bytes)

        return f"{header_b64}.{payload_b64}.{signature_b64}"

    @staticmethod
    def _base64url_encode(data: bytes) -> str:
        """Helper to perform Base64URL encoding without padding."""
        return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")
