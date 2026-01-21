"""
Robust LocalnetManager Implementation.

This manager handles the 'localnet' functional block, responsible for
mapping logical UDMI device IDs to physical network addresses (e.g.,
Modbus Slave ID, BACnet Instance ID) and coordinating protocol drivers.
"""
import logging
from datetime import datetime
from datetime import timezone
from typing import Any
from typing import Dict
from typing import List
from typing import Optional

from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import FamilyLocalnetState
from udmi.schema import LocalnetConfig
from udmi.schema import LocalnetState
from udmi.schema import State

LOGGER = logging.getLogger(__name__)


class LocalnetManager(BaseManager):
    """
    Manages the 'localnet' block with enhanced validation and status reporting.

    This manager acts as the translation layer between logical UDMI device IDs
    and physical fieldbus addresses.
    """

    @property
    def model_field_name(self) -> str:
        return "localnet"

    def __init__(self) -> None:
        """
        Initializes the LocalnetManager.

        Sets up the internal routing tables (Device ID -> Address) and the
        provider registry for different protocol families.
        """
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

        Args:
            family: The protocol family string (e.g., 'bacnet', 'modbus').
            provider: The concrete FamilyProvider implementation used to
                      validate addresses and handle family-specific logic.
        """
        self._providers[family] = provider
        LOGGER.info("Registered FamilyProvider for '%s'", family)

    def get_provider(self, family: str) -> Optional[FamilyProvider]:
        """
        Retrieves the registered provider for a given family.

        Args:
            family: The protocol family identifier.

        Returns:
            The registered FamilyProvider instance, or None if not found.
        """
        return self._providers.get(family)

    # --- Core Manager Hooks ---

    def handle_config(self, config: Config) -> None:
        """
        Parses the 'localnet' configuration block.

        This method:
        1. Validates the configuration against registered providers.
        2. Rebuilds the internal routing table.
        3. Generates the status state (OK/Error) for each family.

        Args:
            config: The full device configuration object.
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
        Populates the 'localnet' state block.

        Publishes the validation results and status of the local network
        to the state message.

        Args:
            state: The state object to update.
        """
        if self._localnet_state and self._localnet_state.families:
            state.localnet = self._localnet_state

    def get_registered_families(self) -> List[str]:
        """
        Returns a list of all currently registered protocol families.

        Returns:
            A list of strings (e.g., ['modbus', 'bacnet']).
        """
        return list(self._providers.keys())

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles commands directed at the localnet manager.
        (Currently unused/pass-through).
        """

    # --- Robust Processing Logic ---

    def _process_family_config(self, family: str,
                               family_config: Any) -> FamilyLocalnetState:
        """
        Validates configuration and builds the routing table for a single family.

        This internal method checks if the provider exists and delegates
        address validation to that provider. It returns a state object
        indicating whether the configuration for this family is valid.

        Args:
            family: The protocol family name.
            family_config: The configuration object for this family.

        Returns:
            A FamilyLocalnetState object containing the status (Active, Error, Warning).
        """
        family_state = FamilyLocalnetState()
        family_state.addr = getattr(family_config, 'addr', None)

        # Provider Verification
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

        # Build Routing Table
        if family not in self._routing_table:
            self._routing_table[family] = {}

        if hasattr(family_config, 'devices') and family_config.devices:
            for device_id, phys_addr in family_config.devices.items():
                # Address Validation (Delegated to Provider)
                validator = getattr(provider, "validate_address", None)
                if validator and callable(validator):
                    is_valid = validator(phys_addr)
                    if not is_valid:
                        LOGGER.warning("Invalid address format for %s: %s",
                                       family, phys_addr)
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
        """
        Resolves a logical UDMI device ID to its physical network address.

        Args:
            family: The protocol family to look up (e.g., 'modbus').
            device_id: The logical device ID.

        Returns:
            The physical address string if found, otherwise None.
        """
        return self._routing_table.get(family, {}).get(device_id)
