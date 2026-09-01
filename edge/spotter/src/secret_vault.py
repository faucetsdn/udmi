"""In-memory Ephemeral Secret Vault for UDMI Spotter."""

import base64
import logging
import threading
from typing import Any, Dict, Optional, Union

LOGGER = logging.getLogger(__name__)


class InMemorySecretVault:
    """Thread-safe, strictly in-memory secret vault.

    Credentials and authentication tokens delivered via config.blobset are
    stored exclusively in memory and securely wiped when requested or invalidated.
    """

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._secrets: Dict[str, bytes] = {}

    def set_secret(self, key: str, secret_data: Union[bytes, str]) -> None:
        """Stores a secret in memory."""
        with self._lock:
            if isinstance(secret_data, str):
                try:
                    self._secrets[key] = base64.b64decode(secret_data.encode("utf-8"))
                except Exception:
                    self._secrets[key] = secret_data.encode("utf-8")
            else:
                self._secrets[key] = bytes(secret_data)
            LOGGER.info(
                "Ephemeral secret '%s' securely stored in memory (%d bytes).",
                key,
                len(self._secrets[key]),
            )

    def get_secret(self, key: str) -> Optional[bytes]:
        """Retrieves a secret from memory."""
        with self._lock:
            return self._secrets.get(key)

    def get_secret_str(self, key: str) -> Optional[str]:
        """Retrieves a secret as a string if UTF-8 decodable."""
        data = self.get_secret(key)
        if data is None:
            return None
        try:
            return data.decode("utf-8")
        except UnicodeDecodeError:
            return base64.b64encode(data).decode("ascii")

    def clear_secret(self, key: str) -> None:
        """Securely wipes a secret from memory."""
        with self._lock:
            if key in self._secrets:
                del self._secrets[key]
                LOGGER.info("Ephemeral secret '%s' wiped from memory.", key)

    def clear_all(self) -> None:
        """Securely wipes all stored secrets."""
        with self._lock:
            self._secrets.clear()
            LOGGER.info("All ephemeral secrets wiped from memory.")


# Global singleton vault instance for agent access
GLOBAL_SECRET_VAULT = InMemorySecretVault()
