"""
Defines the core UDMI Device class.

This module contains the central Device object that manages the
application's lifecycle, state, and message handling logic.
"""

import datetime
import logging
import time
from typing import Dict

from udmi.schema import Config
from udmi.schema import Events
from udmi.schema import State

from .messaging import AbstractMessageDispatcher

LOGGER = logging.getLogger(__name__)


class Device:
    """
    Represents a core UDMI device.

    This class is the main entry point for a pyudmi application.
    It manages the connection, handles the config/state loop, dispatches
    commands, and runs the main application loop, which includes
    periodic tasks like authentication refresh.
    """

    AUTH_CHECK_INTERVAL_SEC = 15 * 60

    def __init__(self, dispatcher: AbstractMessageDispatcher):
        """
        Initializes the Device.

        Args:
            :param dispatcher: An object that implements
                              `AbstractMessageDispatcher`.
        """
        LOGGER.info("Initializing device...")
        self.dispatcher = dispatcher

        self.state: State = self._create_initial_state()
        self.config: Config = Config()

        self._running = False
        self._last_auth_check = 0.0

        self._setup_message_handlers()

    def _setup_message_handlers(self) -> None:
        """Registers handlers with the message dispatcher."""
        self.dispatcher.register_handler("config", self.handle_config)
        self.dispatcher.register_handler("commands/#", self.handle_command)
        LOGGER.debug("Registered config and command handlers.")

    # --- Connection Callbacks ---

    def _on_connect(self) -> None:
        """
        Internal callback for when the client successfully connects.

        This method is wired to the client by the factory.
        It sets the device to operational and publishes the initial state.
        """
        LOGGER.info("Connection successful. Publishing initial state.")
        self.state.system.operation.operational = True
        self.publish_state()

    def _on_disconnect(self, rc: int) -> None:
        """
        Internal callback for when the client disconnects.

        This method is wired to the client by the factory.
        It sets the device to non-operational.

        Args:
            :param rc: The reason code for the disconnection.
        """
        LOGGER.warning("Client disconnected with code: %s", rc)
        self.state.system.operation.operational = False

    # --- Message Handlers ---

    def handle_config(self, channel: str, payload: Dict) -> None:
        """
        Handles an incoming config message.

        This method is registered with the dispatcher.
        It parses the config, updates the last_config timestamp,
        applies device-specific logic, and publishes the new state.

        Args:
            :param channel: The channel the message came from (e.g., 'config').
            :param payload: The pre-parsed dictionary of the JSON payload.
        """
        LOGGER.info("Received new config message.")
        try:
            self.config = Config.from_dict(payload)
            LOGGER.debug("Config parsed successfully. Timestamp: %s",
                         self.config.timestamp)

            if self.config.timestamp:
                self.state.system.last_config = self.config.timestamp

            self._apply_config(self.config)

            self.publish_state()
        except Exception as e:
            LOGGER.error("Failed to handle config message: %s", e)

    def handle_command(self, channel: str, payload: Dict) -> None:
        """
        Handles an incoming command message.

        This method is registered with the dispatcher.
        It logs the command and passes it to the _execute_command hook.

        Args:
            :param channel: The full command channel (e.g., 'commands/reboot').
            :param payload: The pre-parsed dictionary of the JSON payload.
        """
        LOGGER.info("Received command on channel %s", channel)
        try:
            self._execute_command(channel, payload)
        except Exception as e:
            LOGGER.error("Failed to handle command on channel %s: %s",
                         channel, e)

    # --- Public API Methods ---

    def publish_state(self) -> None:
        """
        Publishes the current device state.

        This method first calls the `_update_state_before_publish` hook
        to allow the application to inject live data (e.g., sensor
        readings) before serializing and sending the state.
        """
        LOGGER.info("Updating state before publish...")
        try:
            # Call the hook for subclasses to inject sensor data, etc.
            self._update_state_before_publish()
        except Exception as e:
            LOGGER.error("Failed during _update_state_before_publish hook: %s",
                         e)

        LOGGER.info("Publishing state...")
        self.dispatcher.publish_state(self.state)

    def publish_event(self, channel: str, event: Events) -> None:
        """
        Publishes a device event (e.g., pointset update, system event).

        Args:
            :param channel: The event channel (e.g., 'pointset', 'system').
            :param event: The Events instance to serialize and publish.
        """
        LOGGER.debug("Publishing event to %s", channel)
        self.dispatcher.publish_event(channel, event)

    def run(self) -> None:
        """
        Starts the device and enters the main blocking application loop.

        This loop is responsible for:
        1. Connecting the client.
        2. Starting the client's non-blocking network loop.
        3. Periodically checking for authentication refresh (e.g., JWT).
        4. Gracefully shutting down on exit.
        """
        LOGGER.info("Connecting and starting device run loop...")
        self._running = True
        self.dispatcher.connect()
        self.dispatcher.start_loop()
        self._last_auth_check = time.time()

        try:
            while self._running:
                now = time.time()
                if (now - self._last_auth_check) > self.AUTH_CHECK_INTERVAL_SEC:
                    LOGGER.debug("Checking for auth token refresh...")
                    self.dispatcher.check_authentication()
                    self._last_auth_check = now
                time.sleep(1)
        except KeyboardInterrupt:
            LOGGER.info("Device loop interrupted by user.")
        except Exception as e:
            LOGGER.error("Device loop failed: %s", e, exc_info=True)
        finally:
            self.close()

    def close(self) -> None:
        """Shuts down the device and disconnects the client."""
        LOGGER.info("Closing device connection...")
        self._running = False
        self.dispatcher.close()

    # --- EXTERNAL APPLICATION HOOKS ---
    # These methods are intended to be overridden by a subclass

    def _apply_config(self, config: Config) -> None:
        """
        **HOOK for subclasses.**

        Override this method to apply device-specific config logic.
        This is called *after* a new config is received and parsed.

        Args:
            :param config: The newly parsed Config object.
        """
        LOGGER.debug("No custom _apply_config logic implemented.")
        pass

    def _execute_command(self, channel: str, payload: Dict) -> None:
        """
        **HOOK for subclasses.**

        Override this method to implement device-specific commands.

        Args:
            :param channel: The full command channel (e.g., 'commands/reboot').
            :param payload: The dictionary payload for the command.
        """
        LOGGER.debug("No custom _execute_command logic for %s.", channel)
        pass

    def _update_state_before_publish(self) -> None:
        """
        **HOOK for subclasses.**

        Override this method to update `self.state` with application-specific
        data (e.g., current sensor readings) just before it is published.
        This is called every time `publish_state()` is invoked.
        """
        LOGGER.debug(
            "No custom _update_state_before_publish logic implemented.")
        pass

    def _create_initial_state(self) -> State:
        """
        **HOOK for subclasses.**

        Override this to provide a more detailed initial state, including
        device-specific hardware/pointset info.
        This is called once during __init__.

        Returns:
            The initial State object for the device.
        """
        LOGGER.debug("Creating base device state.")
        return State.from_dict({
            "system": {
                "last_config": datetime.datetime.now(
                    datetime.timezone.utc
                ).isoformat(),
                "hardware": {
                    "make": "pyudmi",
                    "model": "GenericDevice"
                },
                "operation": {
                    "operational": False
                }
            }
        })
