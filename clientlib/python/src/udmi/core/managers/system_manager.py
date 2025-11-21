"""
Provides the concrete implementation for the SystemManager.

This manager is responsible for handling the 'system' block of the
config and state, and for reporting system-level events like startup.
"""

import logging
from datetime import datetime
from datetime import timezone
from typing import Dict
from typing import Optional

from udmi.constants import UDMI_VERSION
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import State
from udmi.schema import StateSystemOperation
from udmi.schema import SystemEvents
from udmi.schema import SystemState

LOGGER = logging.getLogger(__name__)


class SystemManager(BaseManager):
    """
    Manages the 'system' block of the config and state.
    """

    def __init__(self, hardware_info: Dict = None, software_info: Dict = None):
        """
        Initializes the SystemManager.

        Args:
            hardware_info: A dict to populate 'state.system.hardware'
            software_info: A dict to populate 'state.system.software'
        """
        super().__init__()
        self._last_config_ts: Optional[str] = None
        self._hardware = hardware_info or {"make": "pyudmi",
                                           "model": "device-v1"}
        self._software = software_info or {"firmware": "1.0.0"}
        LOGGER.info("SystemManager initialized.")

    def start(self) -> None:
        """
        Called when the device starts. We'll publish a startup event.
        """
        LOGGER.info("SystemManager starting, publishing system startup event.")
        try:
            log_entry = Entry(
                message="Device has started",
                level=200,  # INFO
                timestamp=datetime.now(timezone.utc).isoformat()
            )

            startup_event_message = SystemEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                logentries=[log_entry]
            )

            self.publish_event(startup_event_message, "system")

        except (TypeError, AttributeError) as e:
            LOGGER.error("Failed to publish startup event: %s", e)

    def handle_config(self, config: Config) -> None:
        """
        Handles the 'system' portion of a new config message.
        """
        if not config:
            return

        if config.timestamp:
            self._last_config_ts = config.timestamp
            LOGGER.debug("Captured config.timestamp: %s", self._last_config_ts)

        if not config.system:
            LOGGER.debug(
                "No 'system' block in config, skipping system-specific config.")
            return

        if config.system.min_loglevel is not None:
            LOGGER.info("Setting system min_loglevel to: %s",
                        config.system.min_loglevel)

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles 'system' related commands.
        (e.g., reboot, shutdown)
        """
        # For now, we don't implement any system commands
        if command_name in ["reboot", "shutdown"]:
            LOGGER.warning("Received '%s' command, but it is not implemented.",
                           command_name)
        # This manager doesn't handle other commands

    def update_state(self, state: State) -> None:
        """
        Contributes the 'system' block to the state message.
        """
        state.system = SystemState(
            last_config=self._last_config_ts,
            operation=StateSystemOperation(operational=True),
            hardware=self._hardware,
            software=self._software
        )
        LOGGER.debug("Populated state.system block.")
