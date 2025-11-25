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

LOGGER = logging.getLogger(__name__)


class DevicePersistence:
    """
    Manages persistent data storage with thread safety and atomic writes.
    If filepath is provided, saves to disk.
    If filepath is None, operates in-memory only (for testing/read-only).
    """

    def __init__(self, filepath: Optional[str] = ".udmi_persistence.json"):
        self.filepath = filepath
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

    def get(self, key: str, default: Any = None) -> Any:
        """Retrieves a value by key."""
        with self._lock:
            return self._data.get(key, default)

    def set(self, key: str, value: Any) -> None:
        """Sets a value and immediately triggers a save."""
        with self._lock:
            self._data[key] = value
            self.save()
