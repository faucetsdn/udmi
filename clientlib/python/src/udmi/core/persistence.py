"""
Provides persistence mechanisms for the device.

This module handles saving and loading device state to a JSON file
atomically to ensure data integrity across restarts.
"""
import json
import logging
import os
import tempfile
import threading
from typing import Any, Dict, Optional

from udmi.constants import PERSISTENT_STORE_PATH
from udmi.schema import EndpointConfiguration

LOGGER = logging.getLogger(__name__)


class DevicePersistence:
    """
    Manages persistent data storage with thread safety and atomic writes, and
    the hierarchy of endpoint configurations: Active -> Backup -> Site.
    If filepath is provided, saves to disk.
    If filepath is None, operates in-memory only (for testing/read-only).
    """
    ACTIVE_KEY = "active_endpoint"
    BACKUP_KEY = "backup_endpoint"
    GENERATION_KEY = "active_generation"

    def __init__(self,
        filepath: Optional[str] = PERSISTENT_STORE_PATH,
        site_config: Optional[EndpointConfiguration] = None
    ):
        self.filepath = filepath
        self.site_config = site_config
        self._data: Dict[str, Any] = {}
        self._lock = threading.RLock()

        if self.filepath:
            self.load()
        else:
            LOGGER.info(
                "Persistence initialized in-memory only (no file storage).")

    def load(self) -> None:
        """Loads data from the persistence file."""
        if not self.filepath:
            return

        with self._lock:
            if not os.path.exists(self.filepath):
                LOGGER.debug("No persistence file found at %s. Starting fresh.",
                             self.filepath)
                self._data = {}
                return

            try:
                with open(self.filepath, 'r', encoding='utf-8') as f:
                    self._data = json.load(f)
                LOGGER.debug("Persistence data loaded from %s.", self.filepath)
            except (json.JSONDecodeError, IOError) as e:
                LOGGER.error("Failed to load persistence file: %s", e)
                self._data = {}

    def save(self) -> None:
        """Saves current data to the persistence file atomically."""
        if not self.filepath:
            return

        with self._lock:
            try:
                dirname = os.path.dirname(self.filepath) or "."
                fd, temp_path = tempfile.mkstemp(dir=dirname, text=True)

                try:
                    with os.fdopen(fd, 'w', encoding='utf-8') as f:
                        json.dump(self._data, f, indent=2)
                        f.flush()
                        os.fsync(f.fileno())

                    os.replace(temp_path, self.filepath)
                    LOGGER.debug("Persistence data saved to %s.", self.filepath)

                except Exception:
                    if os.path.exists(temp_path):
                        os.remove(temp_path)
                    raise

            except (IOError, OSError) as e:
                LOGGER.error("Failed to save persistence file: %s", e)

    # --- Endpoint Management ---

    def get_active_endpoint(self) -> Optional[EndpointConfiguration]:
        """Returns the Active Endpoint Blob if valid, else None."""
        with self._lock:
            data = self._data.get(self.ACTIVE_KEY)
            if data:
                return EndpointConfiguration.from_dict(data)
            return None

    def get_backup_endpoint(self) -> Optional[EndpointConfiguration]:
        """Returns the Backup Endpoint Blob if valid, else None."""
        with self._lock:
            data = self._data.get(self.BACKUP_KEY)
            if data:
                return EndpointConfiguration.from_dict(data)
            return None

    def get_effective_endpoint(self) -> EndpointConfiguration:
        """
        Resolution hierarchy: Active -> Backup -> Site.
        """
        active = self.get_active_endpoint()
        if active:
            LOGGER.info("Using ACTIVE endpoint configuration from persistence.")
            return active

        backup = self.get_backup_endpoint()
        if backup:
            LOGGER.warning(
                "Using BACKUP endpoint configuration (Active missing or invalid).")
            return backup

        if self.site_config:
            LOGGER.warning("Using SITE endpoint configuration (Baseline).")
            return self.site_config

        raise ValueError(
            "No Active, Backup, or Site endpoint configuration available.")

    def save_active_endpoint(self, config: EndpointConfiguration,
        generation: str) -> None:
        """Saves the new Active Endpoint Blob."""
        with self._lock:
            self._data[self.ACTIVE_KEY] = config.to_dict()
            self._data[self.GENERATION_KEY] = generation
            self.save()

    def save_backup_endpoint(self, config: EndpointConfiguration) -> None:
        """Saves the Backup Endpoint Blob."""
        with self._lock:
            self._data[self.BACKUP_KEY] = config.to_dict()
            self.save()

    def get_active_generation(self) -> Optional[str]:
        """Get the active generation."""
        with self._lock:
            return self._data.get(self.GENERATION_KEY)

    def clear_active_endpoint(self) -> None:
        """Wipes active endpoint (e.g. after repeated failures)."""
        with self._lock:
            if self.ACTIVE_KEY in self._data:
                del self._data[self.ACTIVE_KEY]
            if self.GENERATION_KEY in self._data:
                del self._data[self.GENERATION_KEY]
            self.save()

    # --- Generic Get/Set ---

    def get(self, key: str, default: Any = None) -> Any:
        """Retrieves a value by key."""
        with self._lock:
            return self._data.get(key, default)

    def set(self, key: str, value: Any) -> None:
        """Sets a value and immediately triggers a save."""
        with self._lock:
            self._data[key] = value
            self.save()
