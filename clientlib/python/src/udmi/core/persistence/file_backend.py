"""
File-based implementation of the PersistenceBackend.
Stores data as a JSON file using atomic writes.
"""
import json
import logging
import os
import threading
from typing import Any
from typing import Dict
from typing import Optional

from udmi.core.persistence.backend import PersistenceBackend
from udmi.core.utils.file_ops import atomic_write

LOGGER = logging.getLogger(__name__)


class FilePersistenceBackend(PersistenceBackend):
    def __init__(self, file_path: str):
        self.file_path = file_path
        self._lock = threading.RLock()
        self._cache: Dict[str, Any] = {}
        self._load_from_disk()

    def _load_from_disk(self):
        """Reads the JSON file into the memory cache."""
        with self._lock:
            if not os.path.exists(self.file_path):
                self._cache = {}
                return

            try:
                with open(self.file_path, 'r') as f:
                    content = f.read().strip()
                    if content:
                        self._cache = json.loads(content)
                    else:
                        self._cache = {}
            except (json.JSONDecodeError, OSError) as e:
                LOGGER.error("Failed to load persistence file %s: %s",
                             self.file_path, e)
                self._cache = {}

    def _flush_to_disk(self):
        """Serializes cache and writes atomically to disk."""
        with self._lock:
            try:
                data = json.dumps(self._cache, indent=2)

                atomic_write(self.file_path, data)

            except Exception as e:
                LOGGER.error("Failed to save persistence file: %s", e)

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
