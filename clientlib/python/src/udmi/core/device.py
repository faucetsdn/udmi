"""
Defines the core UDMI Device class.

This module contains the central Device object that manages the
application's lifecycle, state, and message handling logic.
"""

import datetime
import logging
import time
from dataclasses import dataclass
from dataclasses import field
from threading import Event
from typing import Any
from typing import Dict
from typing import List
from typing import Optional

from udmi.constants import UDMI_VERSION
from udmi.core.managers import BaseManager
from udmi.core.messaging import AbstractMessageDispatcher
from udmi.schema import Config
from udmi.schema import State
from udmi.schema import SystemState

LOGGER = logging.getLogger(__name__)


@dataclass
class _LoopConfig:
    """Configuration for the device's main loop timing."""
    auth_check_interval_sec: int = 15 * 60  # 15 minutes
    publish_state_interval_sec: int = 600   # 10 minutes


@dataclass
class _LoopState:
    """Holds the dynamic state of the device's main loop."""
    stop_event: Event = field(default_factory=Event)
    last_auth_check: float = 0.0
    last_state_publish_time: float = 0.0


class Device:
    """
    Core Device Orchestrator.

    This class composes all services (dispatcher, managers) and runs
    the main device loop. It delegates all UDMI logic (Config, State, Command)
    to a list of registered managers.

    This class is NOT intended to be subclassed.
    """

    def __init__(self, managers: List[BaseManager]):
        """
        Initializes the Device.

        Args:
              managers: A list of initialized BaseManager subclasses.
        """
        LOGGER.info("Initializing device...")
        self.managers = managers
        self.dispatcher: Optional[AbstractMessageDispatcher] = None

        self._loop_config = _LoopConfig()
        self._loop_state = _LoopState()

        self.state: State = State(
            timestamp=datetime.datetime.now(datetime.timezone.utc).isoformat(),
            version=UDMI_VERSION,
            system=SystemState(last_config=None)
        )
        self.config: Config = Config()
        LOGGER.info("Device initialized with %s managers.", len(self.managers))

    def wire_up_dispatcher(self, dispatcher: AbstractMessageDispatcher) -> None:
        """
        Connects the device to its message dispatcher and finalizes setup.
        This must be called before .run()
        """
        LOGGER.debug("Wiring up dispatcher...")
        if self.dispatcher is not None:
            LOGGER.warning("Dispatcher already wired. Overwriting.")
        self.dispatcher = dispatcher
        for manager in self.managers:
            manager.set_device_context(self, self.dispatcher)
        self._setup_message_handlers()

    def _setup_message_handlers(self) -> None:
        """Registers handlers with the message dispatcher."""
        if not self.dispatcher:
            LOGGER.error("Cannot set up handlers; dispatcher is not wired.")
            return
        self.dispatcher.register_handler("config", self.handle_config)
        self.dispatcher.register_handler("commands/#", self.handle_command)
        LOGGER.debug("Registered config and command handlers.")

    # --- Connection Callbacks ---

    def on_ready(self) -> None:
        """
        Callback for when the dispatcher confirms connection and subscriptions.
        """
        LOGGER.info("Connection successful. Publishing initial state.")
        self._publish_state()

    def on_disconnect(self, rc: int) -> None:
        """
        Callback for when the client disconnects.
        Args:
              rc: The reason code for the disconnection.
        """
        LOGGER.warning("Client disconnected with code: %s", rc)

    # --- Message Handlers ---

    def handle_config(self, _channel: str, payload: Dict) -> None:
        """
        Orchestration method to handle a new config.
        Deserializes the config and delegates to all managers.

        Args:
              _channel: The channel the message came from (e.g., 'config').
              payload: The pre-parsed dictionary of the JSON payload.
        """
        LOGGER.info("New config received, deserializing and delegating...")
        try:
            self.config = Config.from_dict(payload)
        except (TypeError, ValueError) as e:
            LOGGER.error("Failed to parse config message: %s", e)
            return

        for manager in self.managers:
            try:
                manager.handle_config(self.config)
            except (AttributeError, TypeError, KeyError, ValueError) as e:
                LOGGER.error(
                    "Error in %s.handle_config: %s",
                    manager.__class__.__name__, e)

        self._publish_state()

    def handle_command(self, channel: str, payload: Dict[str, Any]) -> None:
        """
        Orchestration method to handle a new command.
        Delegates to all managers.

        Args:
              channel: The full command channel (e.g., 'commands/reboot').
              payload: The pre-parsed dictionary of the JSON payload.
        """
        command_name = channel.split('/')[-1]
        LOGGER.info("Command '%s' received, delegating to managers...",
                     command_name)
        for manager in self.managers:
            try:
                manager.handle_command(command_name, payload)
            except (AttributeError, TypeError, KeyError, ValueError) as e:
                LOGGER.error("Error in %s.handle_command: %s",
                             manager.__class__.__name__, e)

    # --- Public API Methods & Main Loop ---

    def _publish_state(self) -> None:
        """
        Orchestration method to build and publish the State message.
        Gathers contributions from all managers.
        """
        LOGGER.debug("Assembling state message...")
        # initialize state before assembling
        self.state = State(
            timestamp=datetime.datetime.now(datetime.timezone.utc).isoformat(),
            version=UDMI_VERSION,
            system=self.state.system
        )
        for manager in self.managers:
            try:
                manager.update_state(self.state)
            except (AttributeError, TypeError, KeyError, ValueError) as e:
                LOGGER.error("Error in %s.update_state: %s",
                             manager.__class__.__name__, e)

        self.dispatcher.publish_state(self.state)
        self._loop_state.last_state_publish_time = time.time()
        LOGGER.debug("State message published.")

    def run(self) -> None:
        """
        Starts the device and enters the main blocking application loop.
        """
        LOGGER.info("Connecting and starting device run loop...")
        self._loop_state.stop_event.clear()

        try:
            self.dispatcher.connect()
            self.dispatcher.start_loop()
            self._loop_state.last_auth_check = time.time()

            for manager in self.managers:
                manager.start()

            LOGGER.info("Device is running. Waiting for events...")
            while not self._loop_state.stop_event.is_set():
                now = time.time()

                # publish state periodically
                if (now - self._loop_state.last_state_publish_time >
                        self._loop_config.publish_state_interval_sec):
                    self._publish_state()

                # check for auth token refresh
                if (now - self._loop_state.last_auth_check >
                        self._loop_config.auth_check_interval_sec):
                    LOGGER.debug("Checking for auth token refresh...")
                    self.dispatcher.check_authentication()
                    self._loop_state.last_auth_check = now

                time.sleep(1)
        except KeyboardInterrupt:
            LOGGER.info("Keyboard interrupt received.")
        finally:
            self.stop()

    def stop(self) -> None:
        """Stops the device loop and disconnects."""
        if not self._loop_state.stop_event.is_set():
            LOGGER.info("Stopping device...")
            self._loop_state.stop_event.set()

            LOGGER.debug("Stopping all managers...")
            for manager in self.managers:
                try:
                    manager.stop()
                except (AttributeError, TypeError, KeyError, ValueError) as e:
                    LOGGER.error("Error stopping %s: %s",
                                 manager.__class__.__name__, e)

            self.dispatcher.close()
            LOGGER.info("Device stopped.")
