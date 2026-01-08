"""
Provides a JWT (JSON Web Token) based authentication provider.

This module implements the AuthProvider interface for Cloud IoT Core and other
JWT-based authentication schemes. It handles automatic token generation,
signing (via the Signer interface), and expiration-based rotation.
"""

import base64
import json
import logging
import threading
from dataclasses import dataclass
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from typing import Final
from typing import Optional

from udmi.core.auth.intf.auth_provider import AuthProvider
from udmi.core.auth.intf.signer import Signer

LOGGER = logging.getLogger(__name__)

DEFAULT_TOKEN_LIFETIME_MINUTES = 60
DEFAULT_TOKEN_REFRESH_BUFFER_MINUTES = 5
DEFAULT_CLOCK_SKEW_SECONDS = 60


@dataclass
class JwtTokenConfig:
    """
    Configuration for JWT token lifetime and refresh behavior.

    Attributes:
        lifetime_minutes: Total validity duration of the token.
        refresh_buffer_minutes: How long before expiration to trigger a refresh.
        clock_skew_seconds: Back-dating tolerance for device/cloud clock drift.
    """
    lifetime_minutes: int = DEFAULT_TOKEN_LIFETIME_MINUTES
    refresh_buffer_minutes: int = DEFAULT_TOKEN_REFRESH_BUFFER_MINUTES
    clock_skew_seconds: int = DEFAULT_CLOCK_SKEW_SECONDS


class JwtAuthProvider(AuthProvider):
    """
    An AuthProvider for JWT-based authentication.

    This provider generates signed JSON Web Tokens (JWTs) using a provided Signer.
    It proactively refreshes tokens before they expire to ensure uninterrupted
    connectivity.
    """

    def __init__(
        self,
        project_id: str,
        signer: Signer,
        algorithm: str,
        token_config: Optional[JwtTokenConfig] = None
    ):
        """
        Initializes the JwtAuthProvider.

        Args:
            project_id: The GCP project ID (used as the JWT 'aud' claim).
            signer: The component responsible for cryptographic signing.
            algorithm: The JWT algorithm identifier (e.g., 'RS256').
            token_config: Configuration for token lifetime/refresh.
        """
        config = token_config or JwtTokenConfig()

        self._audience: Final[str] = project_id
        self._signer: Final[Signer] = signer
        self._algorithm: Final[str] = algorithm
        self._token_lifetime_minutes: Final[int] = config.lifetime_minutes
        self._token_refresh_buffer_minutes: Final[
            int] = config.refresh_buffer_minutes
        self._clock_skew_seconds: Final[int] = config.clock_skew_seconds

        self._cached_token: Optional[str] = None
        self._token_expiry: Optional[datetime] = None
        self._lock = threading.Lock()

    def get_username(self) -> str:
        """For JWT auth, the username is typically ignored or fixed."""
        return "unused"

    def get_password(self) -> str:
        """
        Returns the current valid JWT, refreshing it if necessary.
        """
        if self._is_token_expiring_soon():
            with self._lock:
                if self._is_token_expiring_soon():
                    self._refresh_token()

        if self._cached_token is None:
            raise RuntimeError("Failed to obtain a valid JWT token.")

        return self._cached_token

    def needs_refresh(self) -> bool:
        """
        Returns True if the auth token is expired or will expire soon.
        """
        return self._is_token_expiring_soon()

    def _is_token_expiring_soon(self) -> bool:
        """Checks if the cached token is missing, expired, or expiring soon."""
        expiry = self._token_expiry
        if expiry is None:
            return True

        now_utc = datetime.now(tz=timezone.utc)
        refresh_threshold = expiry - timedelta(
            minutes=self._token_refresh_buffer_minutes
        )

        return now_utc >= refresh_threshold

    def _refresh_token(self) -> None:
        """Generates a new token and updates the cache. Must be called under lock."""
        LOGGER.info("Generating new JWT...")
        try:
            self._cached_token = self._generate_jwt()
            LOGGER.info("Generated new JWT, valid until %s UTC",
                        self._token_expiry)
        except Exception as e:
            LOGGER.error("Failed to generate new JWT: %s", e)
            raise

    def _generate_jwt(self) -> str:
        """
        Constructs and signs the JWT.
        """
        now_utc = datetime.now(tz=timezone.utc)
        token_iat = now_utc - timedelta(seconds=self._clock_skew_seconds)
        token_exp = token_iat + timedelta(minutes=self._token_lifetime_minutes)

        new_expiry = token_exp

        header = {
            "alg": self._algorithm,
            "typ": "JWT"
        }

        payload = {
            "iat": int(token_iat.timestamp()),
            "exp": int(token_exp.timestamp()),
            "aud": self._audience
        }

        header_b64 = self._base64url_encode(json.dumps(header).encode("utf-8"))
        payload_b64 = self._base64url_encode(
            json.dumps(payload).encode("utf-8"))

        signing_input = f"{header_b64}.{payload_b64}".encode("ascii")
        signature_bytes = self._signer.sign(signing_input)
        signature_b64 = self._base64url_encode(signature_bytes)

        self._token_expiry = new_expiry
        return f"{header_b64}.{payload_b64}.{signature_b64}"

    @staticmethod
    def _base64url_encode(data: bytes) -> str:
        """Helper to perform Base64URL encoding without padding."""
        return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")
