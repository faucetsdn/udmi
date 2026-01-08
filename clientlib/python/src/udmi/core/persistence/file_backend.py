"""
File-based implementation of the PersistenceBackend.

This module provides a robust JSON-based storage mechanism that uses atomic
writes to ensure data integrity and handles file corruption by backing up
malformed files.
"""
import json
import logging
import os
import shutil
import threading
from typing import Any
from typing import Dict
from typing import Optional

from udmi.core.persistence.backend import PersistenceBackend
from udmi.core.utils.file_ops import atomic_write

LOGGER = logging.getLogger(__name__)


class FilePersistenceBackend(PersistenceBackend):
    """
    PersistenceBackend implementation that stores data in a local JSON file.

    Thread-safe and uses atomic writes to prevent partial file corruption.
    """

    def __init__(self, file_path: str):
        """
        Initializes the file persistence backend.

        Args:
            file_path: The full path to the JSON file storage.
        """
        self.file_path = file_path
        self._lock = threading.RLock()
        self._cache: Dict[str, Any] = {}
        self._load_from_disk()

    def _load_from_disk(self) -> None:
        """
        Reads the JSON file into the memory cache.

        Handles corruption by backing up the invalid file before resetting.
        """
        with self._lock:
            if not os.path.exists(self.file_path):
                self._cache = {}
                return

            try:
                with open(self.file_path, 'r', encoding='utf-8') as f:
                    content = f.read().strip()
                    if content:
                        self._cache = json.loads(content)
                    else:
                        self._cache = {}
            except json.JSONDecodeError as e:
                LOGGER.error(
                    "CORRUPTION DETECTED: Failed to parse persistence file %s: %s",
                    self.file_path, e)
                self._handle_corruption()
                self._cache = {}
            except OSError as e:
                LOGGER.error("IO Error loading persistence file %s: %s",
                             self.file_path, e)
                self._cache = {}

    def _handle_corruption(self) -> None:
        """Renames the corrupt file to preserve it for analysis."""
        try:
            corrupt_path = f"{self.file_path}.corrupt"
            LOGGER.warning("Backing up corrupt persistence file to %s",
                           corrupt_path)
            shutil.copy2(self.file_path, corrupt_path)
        except Exception as e:
            LOGGER.error("Failed to backup corrupt file: %s", e)

    def _flush_to_disk(self) -> None:
        """Serializes cache and writes atomically to disk."""
        with self._lock:
            try:
                data = json.dumps(self._cache, indent=2)
                atomic_write(self.file_path, data)
            except Exception as e:
                LOGGER.critical("FATAL: Failed to save persistence file: %s", e)

    def load(self, key: str) -> Optional[Any]:
        with self._lock:
            return self._cache.get(key)

    def save(self, key: str, value: Any) -> None:
        with self._lock:
            self._cache[key] = value
            self._flush_to_disk()

    def delete(self, key: str) -> None:
        with self._lock:
            if key in self._cache:
                del self._cache[key]
                self._flush_to_disk()

    def load_all(self) -> Dict[str, Any]:
        with self._lock:
            return self._cache.copy()
