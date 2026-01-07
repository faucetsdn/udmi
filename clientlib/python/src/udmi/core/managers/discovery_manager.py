"""
Provides the concrete implementation for the DiscoveryManager.
"""

import logging
import threading
from datetime import datetime
from datetime import timezone

from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.localnet_manager import LocalnetManager
from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import Config
from udmi.schema import DiscoveryEvents
from udmi.schema import DiscoveryState
from udmi.schema import FamilyDiscoveryState
from udmi.schema import State

LOGGER = logging.getLogger(__name__)


class DiscoveryManager(BaseManager):
    """
    Manages device discovery operations.
    Orchestrates scanning via FamilyProviders registered in the LocalnetManager.
    """

    @property
    def model_field_name(self) -> str:
        return "discovery"

    def __init__(self):
        super().__init__()
        self._discovery_state = DiscoveryState(families={})
        self._config_families = {}  # Cache of family configs
        LOGGER.info("DiscoveryManager initialized.")

    def handle_config(self, config: Config) -> None:
        """
        Handles 'discovery' config block.
        """
        if not config.discovery:
            return

        # Store family configs for use during scans
        if config.discovery.families:
            self._config_families = config.discovery.families

    def handle_command(self, command_name: str, payload: dict) -> None:
        if command_name == "discovery":
            self._handle_discovery_command(payload)

    def update_state(self, state: State) -> None:
        if self._discovery_state:
            state.discovery = self._discovery_state

    def _handle_discovery_command(self, payload: dict) -> None:
        """
        Orchestrates a scan using providers registered in LocalnetManager.
        """
        localnet_manager = self._device.get_manager(LocalnetManager)
        if not localnet_manager:
            LOGGER.error("Cannot perform discovery: LocalnetManager not found.")
            return

        families_to_scan = payload.get("families", [])
        if not families_to_scan:
            families_to_scan = localnet_manager.get_registered_families()

        LOGGER.info("Starting discovery scan for: %s", families_to_scan)

        for family in families_to_scan:
            provider = localnet_manager.get_provider(family)
            if not provider:
                LOGGER.warning("No provider registered for family '%s'.",
                               family)
                continue

            self._update_family_state(family, True)
            self.trigger_state_update()

            thread = threading.Thread(
                target=self._run_scan,
                args=(family, provider),
                name=f"Discovery-{family}",
                daemon=True
            )
            thread.start()

    def _run_scan(self, family: str, provider: FamilyProvider):
        """
        Executes the provider's start_scan method.
        """
        LOGGER.info("Scanning family '%s'...", family)
        try:
            fam_config = self._config_families.get(family)

            provider.start_scan(fam_config, self._handle_scan_result)

            LOGGER.info("Scan finished for '%s'.", family)

        except Exception as e:
            LOGGER.error("Scan failed for '%s': %s", family, e, exc_info=True)
        finally:
            self._update_family_state(family, False)
            self.trigger_state_update()

    def _handle_scan_result(self, device_id: str,
        event: DiscoveryEvents) -> None:
        """
        Callback passed to FamilyProvider.
        Called whenever a device/entity is discovered.
        """
        LOGGER.info("Discovery event received for device: %s", device_id)

        if not event.timestamp:
            event.timestamp = datetime.now(timezone.utc).isoformat()

        self.publish_event(event, "discovery", device_id=device_id)

    def _update_family_state(self, family: str, active: bool) -> None:
        if family not in self._discovery_state.families:
            self._discovery_state.families[family] = FamilyDiscoveryState()

        f_state = self._discovery_state.families[family]
        f_state.active = active
        if active:
            f_state.generation = datetime.now(timezone.utc).isoformat()
