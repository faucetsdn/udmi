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
from typing import Callable
from typing import Dict
from typing import List
from typing import Optional
from typing import Type
from typing import TypeVar

from udmi.constants import IOT_ENDPOINT_CONFIG_BLOB_KEY
from udmi.constants import PERSISTENT_STORE_PATH
from udmi.constants import UDMI_VERSION
from udmi.core.auth import CertManager
from udmi.core.blob import parse_blob_as_object
from udmi.core.managers import BaseManager
from udmi.core.messaging import AbstractMessageDispatcher
from udmi.core.persistence import DevicePersistence
from udmi.schema import Config
from udmi.schema import EndpointConfiguration
from udmi.schema import State
from udmi.schema import SystemState

LOGGER = logging.getLogger(__name__)

T = TypeVar("T", bound=BaseManager)
MAX_CONNECTION_RETRIES = 3


class ConnectionResetException(Exception):
    """
    Raised when the device needs to tear down the current connection
    and re-initialize (e.g., due to an endpoint change).
    """


@dataclass
class _LoopConfig:
    """Configuration for the device's main loop timing."""
    auth_check_interval_sec: int = 15 * 60  # 15 minutes
    publish_state_interval_sec: int = 600  # 10 minutes


@dataclass
class _LoopState:
    """Holds the dynamic state of the device's main loop."""
    stop_event: Event = field(default_factory=Event)
    reset_event: Event = field(default_factory=Event)
    last_auth_check: float = 0.0
    last_state_publish_time: float = 0.0
    consecutive_failures: int = 0  # Track auth failures


ConnectionFactory = Callable[
    [EndpointConfiguration, Callable[[], None], Callable[[int], None]],
    AbstractMessageDispatcher
]


class Device:
    """
    Core Device Orchestrator.

    This class composes all services (dispatcher, managers) and runs
    the main device loop. It delegates all UDMI logic (Config, State, Command)
    to a list of registered managers.

    This class is NOT intended to be subclassed.
    """
    # pylint:disable=too-many-instance-attributes

    def __init__(self,
        managers: List[BaseManager],
        endpoint_config: Optional[EndpointConfiguration] = None,
        connection_factory: ConnectionFactory = None,
        persistence_path: Optional[str] = PERSISTENT_STORE_PATH,
        cert_manager: Optional[CertManager] = None,
    ):
        """
        Initializes the Device.

        Args:
              managers: A list of initialized BaseManager subclasses.
        """
        LOGGER.info("Initializing device...")
        self.managers = managers
        self.connection_factory = connection_factory
        self.persistence = DevicePersistence(persistence_path, endpoint_config)
        self.cert_manager = cert_manager

        self.current_endpoint = self.persistence.get_effective_endpoint()
        self.device_id = self.current_endpoint.client_id.split('/')[-1]
        LOGGER.info("Device ID: %s", self.device_id)
        LOGGER.info("Endpoint Host: %s", self.current_endpoint.hostname)

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
        LOGGER.info("Connection successful. Resetting failure counter.")
        self._loop_state.consecutive_failures = 0
        self._publish_state()

    def on_disconnect(self, rc: int) -> None:
        """
        Callback for when the client disconnects.
        Args:
              rc: The reason code for the disconnection.
        """
        if rc != 0:
            self._loop_state.consecutive_failures += 1
            LOGGER.warning(
                "Client disconnected with code: %s. Failure count: %s/%s",
                rc, self._loop_state.consecutive_failures,
                MAX_CONNECTION_RETRIES)
        else:
            LOGGER.info("Client disconnected cleanly.")

    # --- Message Handlers ---

    def handle_config(self, device_id: str, _channel: str, payload: Dict) -> None:
        """
        Orchestration method to handle a new config.
        Deserializes the config and delegates to all managers.

        Args:
            device_id: The ID of the device this config is for.
            _channel: The raw channel (e.g. 'config').
            payload: The parsed JSON payload.
        """
        if device_id != self.device_id:
            self._route_proxy_config(device_id, payload)
            return

        LOGGER.info("New config received for Device %s...", self.device_id)
        try:
            config_obj = Config.from_dict(payload)
        except (TypeError, ValueError) as e:
            LOGGER.error("Failed to parse config message: %s", e)
            return

        if self._has_new_endpoint_config(config_obj):
            self._try_redirect_endpoint(config_obj)
            return

        self.config = config_obj
        for manager in self.managers:
            try:
                manager.handle_config(self.config)
            except (AttributeError, TypeError, KeyError, ValueError) as e:
                LOGGER.error(
                    "Error in %s.handle_config: %s",
                    manager.__class__.__name__, e)

        self._publish_state()

    def handle_command(self, device_id: str, channel: str, payload: Dict[str, Any]) -> None:
        """
        Orchestration method to handle a new command.
        Delegates to all managers.

        Args:
            device_id: The ID of the device this command is for.
            channel: The full command channel (e.g., 'commands/reboot').
            payload: The pre-parsed dictionary of the JSON payload.
        """
        command_name = channel.split('/')[-1]

        if device_id != self.device_id:
            self._route_proxy_command(device_id, command_name, payload)
            return

        LOGGER.info("Command '%s' received for Device, delegating to managers...", command_name)
        for manager in self.managers:
            try:
                manager.handle_command(command_name, payload)
            except (AttributeError, TypeError, KeyError, ValueError) as e:
                LOGGER.error("Error in %s.handle_command: %s",
                             manager.__class__.__name__, e)

    # --- Proxy Routing Helpers ---

    def _route_proxy_config(self, device_id: str, payload: Dict) -> None:
        """
        Routes a config message meant for a proxy device to the GatewayManager.
        """
        handled = False
        for manager in self.managers:
            if hasattr(manager, "handle_proxy_config"):
                try:
                    config_obj = Config.from_dict(payload)
                    manager.handle_proxy_config(device_id, config_obj)
                    handled = True
                except Exception as e:
                    LOGGER.error("Error routing proxy config to %s: %s",
                                 manager.__class__.__name__, e)

        if not handled:
            LOGGER.debug("Received config for proxy '%s' but no GatewayManager found.", device_id)

    def _route_proxy_command(self, device_id: str, command_name: str, payload: Dict) -> None:
        """
        Routes a command message meant for a proxy device to the GatewayManager.
        """
        handled = False
        for manager in self.managers:
            if hasattr(manager, "handle_proxy_command"):
                try:
                    manager.handle_proxy_command(device_id, command_name, payload)
                    handled = True
                except Exception as e:
                    LOGGER.error("Error routing proxy command to %s: %s",
                                 manager.__class__.__name__, e)

        if not handled:
            LOGGER.debug("Received command for proxy '%s' but no GatewayManager found.", device_id)

    # --- Device lifecycle and endpoint management ---

    def _has_new_endpoint_config(self, config: Config) -> bool:
        """Checks if the config contains a new, different endpoint blob."""
        if not config.blobset or not config.blobset.blobs:
            return False

        blob_config = config.blobset.blobs.get(IOT_ENDPOINT_CONFIG_BLOB_KEY)
        if not blob_config:
            return False

        current_gen = self.persistence.get_active_generation()
        if blob_config.generation and blob_config.generation != current_gen:
            return True

        return False

    def _try_redirect_endpoint(self, config: Config) -> None:
        """
        Fetches, parses, and stages a new endpoint configuration.
        """
        LOGGER.info(
            "New endpoint configuration detected. Attempting redirect...")
        try:
            new_endpoint, generation = parse_blob_as_object(
                config.blobset,
                IOT_ENDPOINT_CONFIG_BLOB_KEY,
                EndpointConfiguration
            )

            LOGGER.info("Endpoint blob fetched. Generation: %s. Saving...",
                        generation)

            self.persistence.save_active_endpoint(new_endpoint, generation)
            self.current_endpoint = new_endpoint
            self.device_id = self.current_endpoint.client_id.split('/')[-1]

            LOGGER.info("Signaling main loop to trigger connection reset...")
            self._loop_state.reset_event.set()

        except Exception as e:  # pylint:disable=broad-exception-caught
            LOGGER.error("Failed to process endpoint redirect: %s", e)

    # --- Main Run Loop ---

    def run(self) -> None:
        """
        Starts the device and enters the main blocking application loop.
        Handles ConnectionResetException to allow dynamic reloading of the connection.
        """
        LOGGER.info("Starting device run loop...")

        while True:
            try:
                just_connected = False
                if not self.dispatcher:
                    self._initialize_connection_robustly()
                    just_connected = True

                self._run_internal(skip_connect=just_connected)
                break
            except ConnectionResetException:
                LOGGER.warning(
                    "Connection Reset requested. Re-initializing network stack...")
                self._loop_state.reset_event.clear()
                self._loop_state.consecutive_failures = 0

                if self.dispatcher:
                    try:
                        self.dispatcher.close()
                    except Exception as e:  # pylint:disable=broad-exception-caught
                        LOGGER.warning("Error closing dispatcher: %s", e)
                    self.dispatcher = None
                continue
            except KeyboardInterrupt:
                LOGGER.info("Keyboard interrupt.")
                break
            except Exception as e:  # pylint:disable=broad-exception-caught
                LOGGER.critical("Unexpected crash in run loop: %s", e,
                                exc_info=True)
                break
            finally:
                self.stop()

    def _initialize_connection_robustly(self) -> None:
        """
        Attempts to build and connect the dispatcher.
        Implements Fallback Logic:
        If the current (Active) endpoint fails to initialize (e.g. invalid keys,
        dns failure), it clears the active endpoint and falls back to Backup/Site.
        """
        LOGGER.info("Initializing connection to %s...",
                    self.current_endpoint.hostname)

        if not self.connection_factory:
            raise RuntimeError(
                "Cannot initialize connection: No connection_factory provided.")

        try:
            self._attempt_connection_setup()
        except Exception as e:  # pylint:disable=broad-exception-caught
            LOGGER.error("Failed to connect to current endpoint: %s", e)
            self._trigger_fallback_or_raise(e)

    def _attempt_connection_setup(self):
        """Helper to create dispatcher and connect."""
        dispatcher = self.connection_factory(
            self.current_endpoint,
            self.on_ready,
            self.on_disconnect
        )
        self.wire_up_dispatcher(dispatcher)
        self.dispatcher.connect()

    def _trigger_fallback_or_raise(self, error: Exception):
        """
        Checks if we have an active endpoint to fallback from.
        If yes, clears it and triggers a full reset. If no, re-raises the error.
        """
        if self.persistence.get_active_endpoint():
            LOGGER.warning(
                "Active endpoint failed. Clearing bad config and resetting...")
            self.persistence.clear_active_endpoint()

            self.current_endpoint = self.persistence.get_effective_endpoint()
            self.device_id = self.current_endpoint.client_id.split('/')[-1]
            LOGGER.info("Fallback endpoint will be: %s",
                        self.current_endpoint.hostname)

            raise ConnectionResetException("Triggering Fallback Reset")
        raise error

    def _publish_state(self) -> None:
        """
        Orchestration method to build and publish the State message.
        Gathers contributions from all managers.
        """
        LOGGER.debug("Assembling state message...")
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

    def _run_internal(self, skip_connect: bool = False) -> None:
        """
        The inner loop that handles periodic tasks.
        Args:
            skip_connect: If True, assumes the dispatcher is already connected.
        """
        LOGGER.info("Network stack initialized. Starting main event loop...")
        self._loop_state.stop_event.clear()
        self._loop_state.reset_event.clear()

        if not self.dispatcher:
            raise RuntimeError("Dispatcher not wired.")

        if not skip_connect:
            self.dispatcher.connect()

        self.dispatcher.start_loop()
        self._loop_state.last_auth_check = time.time()

        for manager in self.managers:
            manager.start()

        LOGGER.info("Device is running. Waiting for events...")
        while not self._loop_state.stop_event.is_set():
            if self._loop_state.reset_event.is_set():
                raise ConnectionResetException()

            if self._loop_state.consecutive_failures >= MAX_CONNECTION_RETRIES:
                LOGGER.error("Max connection failures (%s) reached.",
                             MAX_CONNECTION_RETRIES)
                self._trigger_fallback_or_raise(
                    RuntimeError("Connection unstable."))
                raise ConnectionResetException()

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

            self._loop_state.stop_event.wait(timeout=1)

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

            if self.dispatcher:
                self.dispatcher.close()
            LOGGER.info("Device stopped.")

    def get_manager(self, manager_type: Type[T]) -> Optional[T]:
        """
        Retrieves the first registered manager of the specified type.

        Args:
            manager_type: The class type of the manager to retrieve.

        Returns:
            The manager instance if found, otherwise None.
        """
        for manager in self.managers:
            if isinstance(manager, manager_type):
                return manager
        return None
