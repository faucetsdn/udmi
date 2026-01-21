"""
Provides the concrete implementation for the DiscoveryManager.
"""

import logging
import threading
import time
from datetime import datetime
from datetime import timezone
from typing import Dict
from typing import List
from typing import Optional
from typing import TYPE_CHECKING

from udmi.constants import UDMI_VERSION
from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import Config
from udmi.schema import DiscoveryConfig
from udmi.schema import DiscoveryEvents
from udmi.schema import DiscoveryState
from udmi.schema import FamilyDiscovery
from udmi.schema import FamilyDiscoveryConfig
from udmi.schema import FamilyDiscoveryState
from udmi.schema import State

if TYPE_CHECKING:
    from udmi.core.managers.localnet_manager import LocalnetManager

LOGGER = logging.getLogger(__name__)

# Default check interval for the scheduler loop
SCHEDULER_INTERVAL_SEC = 10


class DiscoveryManager(BaseManager):
    """
    Manages device discovery operations.
    Orchestrates scanning via FamilyProviders based on configuration schedules
    or on-demand commands.
    """

    @property
    def model_field_name(self) -> str:
        return "discovery"

    def __init__(self) -> None:
        super().__init__()
        self._discovery_state = DiscoveryState(families={})
        self._config: Optional[DiscoveryConfig] = None
        self._active_providers: List[FamilyProvider] = []
        self._last_scan_times: Dict[str, float] = {}
        self._scan_wake_event: Optional[threading.Event] = None

        LOGGER.info("DiscoveryManager initialized.")

    def start(self) -> None:
        """Starts the periodic scheduler for discovery scans."""
        LOGGER.info("Starting Discovery scheduler...")
        self._scan_wake_event = self.start_periodic_task(
            interval_getter=lambda: SCHEDULER_INTERVAL_SEC,
            task=self._check_scheduled_scans,
            name="DiscoveryScheduler"
        )

    def handle_config(self, config: Config) -> None:
        """
        Handles 'discovery' config block.
        Updates internal config and triggers enumeration if requested.
        """
        if not config.discovery:
            return

        self._config = config.discovery

        if self._config.enumerations:
            self._enumerate_capabilities()

        if self._scan_wake_event:
            self._scan_wake_event.set()

    def _enumerate_capabilities(self) -> None:
        """
        Publishes a DiscoveryEvent containing static capabilities of this device.
        """
        from udmi.core.managers.localnet_manager import LocalnetManager
        localnet: Optional[LocalnetManager] = None
        if self._device:
            localnet = self._device.get_manager(LocalnetManager)

        families = localnet.get_registered_families() if localnet else []

        event = DiscoveryEvents(
            timestamp=datetime.now(timezone.utc).isoformat(),
            version=UDMI_VERSION,
            families={f: FamilyDiscovery() for f in families}
        )
        LOGGER.info("Publishing discovery enumeration: %s", families)
        self.publish_event(event, "discovery")

    def _check_scheduled_scans(self) -> None:
        """
        Periodic task: Checks if any family is due for a scan based on
        interval or generation change.
        """
        if not self._config or not self._config.families:
            return

        for family, fam_config in self._config.families.items():
            if self._should_scan(family, fam_config):
                LOGGER.info("Scheduled scan triggered for family: %s", family)
                self._trigger_scan(family)

    def _should_scan(self, family: str, config: FamilyDiscoveryConfig) -> bool:
        """Determines if a scan is due."""
        state_gen = None
        if family in self._discovery_state.families:
            state_gen = self._discovery_state.families[family].generation

        if config.generation and config.generation != state_gen:
            return True

        if config.scan_interval_sec:
            last_run = self._last_scan_times.get(family, 0)
            if (time.time() - last_run) >= config.scan_interval_sec:
                return True

        return False

    def handle_command(self, command_name: str, payload: dict) -> None:
        if command_name == "discovery":
            self._handle_discovery_command(payload)

    def update_state(self, state: State) -> None:
        if self._discovery_state:
            state.discovery = self._discovery_state

    def stop(self) -> None:
        """Stops all active scans."""
        super().stop()
        for provider in self._active_providers:
            try:
                provider.stop_scan()
            except Exception as e:
                LOGGER.error("Error stopping scan provider: %s", e)
        self._active_providers.clear()

    def _handle_discovery_command(self, payload: dict) -> None:
        """Manual trigger via command."""
        from udmi.core.managers.localnet_manager import LocalnetManager
        localnet_manager = self._device.get_manager(LocalnetManager)

        if not localnet_manager:
            LOGGER.error("Cannot perform discovery: LocalnetManager not found.")
            return

        families_to_scan = payload.get("families", [])
        if not families_to_scan:
            families_to_scan = localnet_manager.get_registered_families()

        for family in families_to_scan:
            self._trigger_scan(family)

    def _trigger_scan(self, family: str) -> None:
        """Helper to initiate the thread for a specific family scan."""
        from udmi.core.managers.localnet_manager import LocalnetManager
        localnet_manager = self._device.get_manager(LocalnetManager)

        if not localnet_manager:
            return

        provider = localnet_manager.get_provider(family)
        if not provider:
            LOGGER.warning("No provider registered for family '%s'.", family)
            return

        self._active_providers.append(provider)
        self._update_family_state(family, True)
        self.trigger_state_update()

        self._last_scan_times[family] = time.time()

        thread = threading.Thread(
            target=self._run_scan,
            args=(family, provider),
            name=f"Discovery-{family}",
            daemon=True
        )
        thread.start()

    def _run_scan(self, family: str, provider: FamilyProvider) -> None:
        """
        Executes the provider's start_scan method.
        """
        LOGGER.info("Scanning family '%s'...", family)
        try:
            fam_config = None
            if self._config and self._config.families:
                fam_config = self._config.families.get(family)

            provider.start_scan(fam_config, self._handle_scan_result)
            LOGGER.info("Scan finished for '%s'.", family)

        except Exception as e:
            LOGGER.error("Scan failed for '%s': %s", family, e, exc_info=True)
        finally:
            if provider in self._active_providers:
                self._active_providers.remove(provider)
            self._update_family_state(family, False)
            self.trigger_state_update()

    def _handle_scan_result(self, device_id: str, event: DiscoveryEvents) -> None:
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

        if not active and self._config and self._config.families:
             fam_config = self._config.families.get(family)
             if fam_config and fam_config.generation:
                 f_state.generation = fam_config.generation
