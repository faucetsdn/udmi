"""
Core persistence logic for the UDMI Device.
Handles high-level state management (Restart counts, Endpoint Configs)
delegating storage to a backend.
"""
import logging
from typing import Any
from typing import Optional

from udmi.core.persistence.backend import PersistenceBackend
from udmi.schema import EndpointConfiguration

LOGGER = logging.getLogger(__name__)


class DevicePersistence:
    """
    High-level manager for persistent device data.

    Implements the UDMI Endpoint Hierarchy:
    1. Active: The most recently received and validated config.
    2. Backup: The last known "good" config (successfully connected).
    3. Site: The factory/provisioned default (read-only).
    """
    ACTIVE_KEY = "active_endpoint"
    BACKUP_KEY = "backup_endpoint"
    GENERATION_KEY = "active_endpoint_generation"

    def __init__(self, backend: PersistenceBackend,
        default_endpoint: Optional[EndpointConfiguration] = None):
        """
        Args:
            backend: The storage strategy (File, DB, etc.)
            default_endpoint: The hardcoded/provisioned endpoint config to fallback to (Site Config).
        """
        self.backend = backend
        self.default_endpoint = default_endpoint

    def get(self, key: str, default: Any = None) -> Any:
        val = self.backend.load(key)
        return val if val is not None else default

    def set(self, key: str, value: Any) -> None:
        self.backend.save(key, value)

    # --- Endpoint Management ---

    def get_active_endpoint(self) -> Optional[EndpointConfiguration]:
        """Returns the saved Active endpoint, or None if not set."""
        saved_dict = self.backend.load(self.ACTIVE_KEY)
        if saved_dict:
            try:
                return EndpointConfiguration.from_dict(saved_dict)
            except Exception as e:
                LOGGER.error("Failed to parse saved Active endpoint: %s", e)
        return None

    def get_backup_endpoint(self) -> Optional[EndpointConfiguration]:
        """Returns the saved Backup endpoint, or None if not set."""
        saved_dict = self.backend.load(self.BACKUP_KEY)
        if saved_dict:
            try:
                return EndpointConfiguration.from_dict(saved_dict)
            except Exception as e:
                LOGGER.error("Failed to parse saved Backup endpoint: %s", e)
        return None

    def get_effective_endpoint(self) -> EndpointConfiguration:
        """
        Determines the endpoint to use based on the hierarchy:
        Active -> Backup -> Site (Default).
        """
        # 1. Try Active
        active = self.get_active_endpoint()
        if active:
            LOGGER.info("Using ACTIVE endpoint configuration from persistence.")
            return active

        # 2. Try Backup
        backup = self.get_backup_endpoint()
        if backup:
            LOGGER.warning(
                "Active endpoint missing/invalid. Using BACKUP endpoint configuration.")
            return backup

        # 3. Fallback to Site (Default)
        if self.default_endpoint:
            LOGGER.warning("Using SITE endpoint configuration (Baseline).")
            return self.default_endpoint

        raise ValueError(
            "No effective endpoint available (No Active, Backup, or Site config).")

    def save_active_endpoint(self, endpoint: EndpointConfiguration,
        generation: str) -> None:
        """
        Persists a new Active Endpoint Configuration.
        This is usually called when a new 'blobset' is received and validated.
        """
        LOGGER.info("Saving new Active Endpoint (Generation: %s).", generation)
        self.backend.save(self.ACTIVE_KEY, endpoint.to_dict())
        self.backend.save(self.GENERATION_KEY, generation)

    def save_backup_endpoint(self, endpoint: EndpointConfiguration) -> None:
        """
        Persists a Backup Endpoint Configuration.
        This should be called after a successful connection to an Active endpoint,
        "promoting" it to Backup status.
        """
        LOGGER.info("Promoting current endpoint to Backup.")
        self.backend.save(self.BACKUP_KEY, endpoint.to_dict())

    def clear_active_endpoint(self) -> None:
        """
        Removes the Active endpoint.
        This is called when the Active endpoint fails to connect, forcing a fallback
        to Backup or Site on the next run.
        """
        LOGGER.warning("Clearing Active Endpoint due to failure.")
        self.backend.delete(self.ACTIVE_KEY)
        self.backend.delete(self.GENERATION_KEY)

    def get_active_generation(self) -> Optional[str]:
        return self.backend.load(self.GENERATION_KEY)
