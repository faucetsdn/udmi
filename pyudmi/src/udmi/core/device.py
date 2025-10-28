"""
Defines the core UDMI Device class.

This module contains the central Device object that manages the
application's lifecycle, state, and message handling logic.
"""

import datetime
import logging
import time
from threading import Event
from typing import Any
from typing import Dict
from typing import List
from typing import Optional

from udmi.schema import Config
from udmi.schema import State
from udmi.schema import SystemState

from .managers import BaseManager
from .messaging import AbstractMessageDispatcher
from ..constants import UDMI_VERSION

LOGGER = logging.getLogger(__name__)


class Device:
    """
    Core Device Orchestrator.

    This class composes all services (dispatcher, managers) and runs
    the main device loop. It delegates all UDMI logic (Config, State, Command)
    to a list of registered managers.

    This class is NOT intended to be subclassed.
    """
    AUTH_CHECK_INTERVAL_SEC = 15 * 60

    def __init__(self, managers: List[BaseManager]):
        """
        Initializes the Device.

        Args:
             managers: A list of initialized BaseManager subclasses.
        """
        LOGGER.info("Initializing device...")
        self.managers = managers
        self.dispatcher: Optional[AbstractMessageDispatcher] = None
        self._stop_event = Event()
        self._last_auth_check = 0.0
        self._last_state_publish_time: float = 0
        self.publish_state_interval_sec: int = 600
        self.state: State = State(
            timestamp=datetime.datetime.now(datetime.timezone.utc).isoformat(),
            version=UDMI_VERSION,
            system=SystemState(last_config=None)
        )
        self.config: Config = Config()
        LOGGER.info(f"Device initialized with {len(self.managers)} managers.")

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

    def handle_config(self, channel: str, payload: Dict) -> None:
        """
        Orchestration method to handle a new config.
        Deserializes the config and delegates to all managers.

        Args:
             channel: The channel the message came from (e.g., 'config').
             payload: The pre-parsed dictionary of the JSON payload.
        """
        LOGGER.info("New config received, deserializing and delegating...")
        try:
            self.config = Config.from_dict(payload)
        except Exception as e:
            LOGGER.error("Failed to parse config message: %s", e)
            return

        for manager in self.managers:
            try:
                manager.handle_config(self.config)
            except Exception as e:
                LOGGER.error(
                    f"Error in {manager.__class__.__name__}.handle_config: {e}")

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
        LOGGER.info(
            f"Command '{command_name}' received, delegating to managers...")
        for manager in self.managers:
            try:
                manager.handle_command(command_name, payload)
            except Exception as e:
                LOGGER.error(
                    f"Error in {manager.__class__.__name__}.handle_command: {e}")

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
            except Exception as e:
                LOGGER.error(
                    f"Error in {manager.__class__.__name__}.update_state: {e}")

        self.dispatcher.publish_state(self.state)
        self._last_state_publish_time = time.time()
        LOGGER.debug("State message published.")

    def run(self) -> None:
        """
        Starts the device and enters the main blocking application loop.
        """
        LOGGER.info("Connecting and starting device run loop...")
        self._stop_event.clear()

        try:
            self.dispatcher.connect()
            self.dispatcher.start_loop()
            self._last_auth_check = time.time()

            for manager in self.managers:
                manager.start()

            LOGGER.info("Device is running. Waiting for events...")
            while not self._stop_event.is_set():
                now = time.time()

                # publish state periodically
                if (now - self._last_state_publish_time >
                    self.publish_state_interval_sec):
                    self._publish_state()

                # check for auth token refresh
                if now - self._last_auth_check > self.AUTH_CHECK_INTERVAL_SEC:
                    LOGGER.debug("Checking for auth token refresh...")
                    self.dispatcher.check_authentication()
                    self._last_auth_check = now

                time.sleep(1)
        except KeyboardInterrupt:
            LOGGER.info("Keyboard interrupt received.")
        finally:
            self.stop()

    def stop(self) -> None:
        """Stops the device loop and disconnects."""
        if not self._stop_event.is_set():
            LOGGER.info("Stopping device...")
            self._stop_event.set()

            LOGGER.debug("Stopping all managers...")
            for manager in self.managers:
                try:
                    manager.stop()
                except Exception as e:
                    LOGGER.error(
                        f"Error stopping {manager.__class__.__name__}: {e}")

            self.dispatcher.close()
            LOGGER.info("Device stopped.")
