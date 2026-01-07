"""
Robust LocalnetManager Implementation.
Adds validation, error reporting, and command handling to the baseline.
"""
import logging
from datetime import datetime, timezone
from typing import Any, Dict, Optional
from typing import List

from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import Config, State, Entry
from udmi.schema import LocalnetConfig, LocalnetState, FamilyLocalnetState

LOGGER = logging.getLogger(__name__)


class LocalnetManager(BaseManager):
    """
    Manages the 'localnet' functional block with enhanced validation and status reporting.
    """

    @property
    def model_field_name(self) -> str:
        return "localnet"

    def __init__(self):
        super().__init__()
        # Routing Table: family -> { device_id -> physical_address }
        self._routing_table: Dict[str, Dict[str, str]] = {}
        # Provider Registry: family -> FamilyProvider instance
        self._providers: Dict[str, FamilyProvider] = {}

        self._localnet_config: Optional[LocalnetConfig] = None
        self._localnet_state: LocalnetState = LocalnetState(families={})

        LOGGER.info("LocalnetManager initialized.")

    # --- Registration API ---

    def register_provider(self, family: str, provider: FamilyProvider) -> None:
        """
        Registers a protocol driver (Provider) for a specific family.
        Example: register_provider('bacnet', BacnetProvider())
        """
        self._providers[family] = provider
        LOGGER.info("Registered FamilyProvider for '%s'", family)

    def get_provider(self, family: str) -> Optional[FamilyProvider]:
        return self._providers.get(family)

    # --- Core Manager Hooks ---

    def handle_config(self, config: Config) -> None:
        """
        Parses 'localnet' config, validates against registered providers,
        and updates the routing table.
        """
        if not config.localnet:
            return

        LOGGER.info("Processing localnet configuration...")
        self._localnet_config = config.localnet

        # Reset tables
        self._routing_table.clear()

        # Prepare new state entries
        new_family_states = {}

        if self._localnet_config.families:
            for family, family_config in self._localnet_config.families.items():
                # Process and Validate each family configuration
                family_state = self._process_family_config(family,
                                                           family_config)
                new_family_states[family] = family_state

        self._localnet_state.families = new_family_states

        LOGGER.info("Localnet routing table updated with %d families.",
                    len(new_family_states))

        # Trigger state update to report status back to the cloud
        self.trigger_state_update()

    def update_state(self, state: State) -> None:
        """
        Publishes the validation results and status of the local network.
        """
        if self._localnet_state and self._localnet_state.families:
            state.localnet = self._localnet_state

    def get_registered_families(self) -> List[str]:
        return list(self._providers.keys())

    def handle_command(self, command_name: str, payload: dict) -> None:
        pass

    # --- Robust Processing Logic ---

    def _process_family_config(self, family: str,
        family_config: Any) -> FamilyLocalnetState:
        """
        Validates configuration and builds the routing table for a single family.
        Returns the FamilyLocalnetState with appropriate Status (OK or Error).
        """
        family_state = FamilyLocalnetState()
        family_state.addr = family_config.addr

        # 1. Provider Verification
        provider = self.get_provider(family)
        if not provider:
            LOGGER.error("Configuration received for unknown family: '%s'",
                         family)
            family_state.status = Entry(
                message=f"No provider registered for family '{family}'",
                level=500,  # 500 = Failure/Error
                timestamp=datetime.now(timezone.utc).isoformat()
            )
            return family_state

        # 2. Build Routing Table
        if family not in self._routing_table:
            self._routing_table[family] = {}

        if hasattr(family_config, 'devices') and family_config.devices:
            for device_id, phys_addr in family_config.devices.items():
                # 3. Address Validation (Delegated to Provider)
                if hasattr(provider, "validate_address"):
                    is_valid = provider.validate_address(phys_addr)
                    if not is_valid:
                        LOGGER.warning("Invalid address format for %s: %s",
                                       family, phys_addr)
                        # Report a warning but typically we still load the table
                        family_state.status = Entry(
                            message=f"Invalid address format detected: {phys_addr}",
                            level=300,  # 300 = Warning
                            timestamp=datetime.now(timezone.utc).isoformat()
                        )

                self._routing_table[family][device_id] = phys_addr

        # 4. Success Status (if no errors encountered)
        if not family_state.status:
            family_state.status = Entry(
                message="Active",
                level=200,  # 200 = Info/OK
                timestamp=datetime.now(timezone.utc).isoformat()
            )

        return family_state

    # --- Public API ---

    def get_physical_address(self, family: str, device_id: str) -> Optional[
        str]:
        """Resolves logical ID to physical address."""
        return self._routing_table.get(family, {}).get(device_id)
