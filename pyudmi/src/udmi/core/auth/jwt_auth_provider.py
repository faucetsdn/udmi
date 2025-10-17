import datetime
import logging

import jwt

from .auth_provider import AuthProvider

LOGGER = logging.getLogger(__name__)


class JwtAuthProvider(AuthProvider):
    """
    An AuthProvider for JWT-based authentication, common for cloud platforms.
    Caches the token and handles proactive refresh.
    """
    DEFAULT_TOKEN_LIFETIME_MINUTES = 60 * 24
    TOKEN_REFRESH_BUFFER_MINUTES = 5

    def __init__(self, project_id: str, private_key_file: str, algorithm: str):
        self.audience = project_id
        self.algorithm = algorithm
        self._cached_token: str | None = None
        self._token_expiry: datetime.datetime | None = None
        try:
            with open(private_key_file, "r") as f:
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

        now_utc = datetime.datetime.now(tz=datetime.timezone.utc)
        refresh_threshold = self._token_expiry - datetime.timedelta(
            minutes=self.TOKEN_REFRESH_BUFFER_MINUTES
        )

        return now_utc >= refresh_threshold

    def get_password(self) -> str:
        """
        Generates a new, time-limited JWT if the cached one is expiring soon,
        otherwise returns the cached token.
        """
        if self._is_token_expiring_soon():
            LOGGER.info("Generating new JWT...")
            token_iat = datetime.datetime.now(tz=datetime.timezone.utc)
            token_exp = token_iat + datetime.timedelta(
                minutes=self.DEFAULT_TOKEN_LIFETIME_MINUTES
            )
            payload = {"iat": token_iat, "exp": token_exp, "aud": self.audience}

            self._cached_token = jwt.encode(
                payload, self._private_key, algorithm=self.algorithm
            )
            self._token_expiry = token_exp
            LOGGER.info("Generated new JWT, valid until %s UTC", token_exp)
        else:
            LOGGER.debug("Reusing cached JWT.")

        return self._cached_token

    def needs_refresh(self) -> bool:
        """
        Returns True if the auth token is expired or will expire soon.
        """
        return self._is_token_expiring_soon()
