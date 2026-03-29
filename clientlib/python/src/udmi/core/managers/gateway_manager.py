"""
Provides the concrete implementation for the GatewayManager.
This manager handles the 'gateway' block, manages proxy device attachments,
and routes proxy-specific config/commands to registered handlers.
"""

import logging
from datetime import datetime
from datetime import timezone
from typing import Callable
from typing import Dict
from typing import List
from typing import Optional
from typing import Set
from typing import TYPE_CHECKING

from udmi.core.managers.base_manager import BaseManager
from udmi.schema import Config
from udmi.schema import DataModel
from udmi.schema import Entry
from udmi.schema import GatewayConfig
from udmi.schema import GatewayState
from udmi.schema import State

if TYPE_CHECKING:
    from udmi.core.managers.localnet_manager import LocalnetManager

LOGGER = logging.getLogger(__name__)

# Callback signatures for proxy events
ProxyConfigHandler = Callable[[str, Config], None]
# ProxyCommandHandler: (device_id, command_name, payload) -> None
ProxyCommandHandler = Callable[[str, str, dict], None]


class GatewayManager(BaseManager):
    """
    Manages the 'gateway' functionality.
    Allows this device to act as a gateway for other 'proxy' devices.
    """

    @property
    def model_field_name(self) -> str:
        return "gateway"

    PERSISTENCE_KEY = "gateway_proxies"

    def __init__(self) -> None:
        """
        Initializes the GatewayManager.

        Sets up internal storage for proxy IDs, handlers, and the initial
        gateway state.
        """
        super().__init__()
        self._proxies: List[str] = []
        self._proxy_config_handlers: Dict[str, ProxyConfigHandler] = {}
        self._proxy_command_handlers: Dict[str, ProxyCommandHandler] = {}
        self._default_config_handler: Optional[ProxyConfigHandler] = None
        self._default_command_handler: Optional[ProxyCommandHandler] = None
        self._gateway_state = GatewayState()

        LOGGER.info("GatewayManager initialized.")

    def start(self) -> None:
        """
        Called when the device loop starts.
        Restores attached proxies from persistence and re-sends attach messages.
        """
        # Load persisted proxies
        if self._device and self._device.persistence:
            saved_proxies = self._device.persistence.get(self.PERSISTENCE_KEY, [])
            count_loaded = 0
            for proxy_id in saved_proxies:
                if proxy_id not in self._proxies:
                    self._proxies.append(proxy_id)
                    count_loaded += 1

            if count_loaded > 0:
                LOGGER.info("Restored %s proxies from persistence.", count_loaded)

        # Re-attach everyone to ensure broker state is consistent
        if self._proxies:
            LOGGER.info("Re-attaching %s proxy devices...", len(self._proxies))
            for device_id in self._proxies:
                self._send_attach_message(device_id)

    def handle_config(self, config: Config) -> None:
        """
        Handles the 'gateway' block of the *Gateway's* own config.
        Syncs the active proxy list with the cloud configuration.
        """
        if not config.gateway:
            return

        self._validate_gateway_config(config.gateway)
        self._sync_proxies(config.gateway)

    def _validate_gateway_config(self, gateway_config: GatewayConfig) -> None:
        """
        Validates that the requested gateway target family is supported by
        the registered Localnet providers.
        """
        if not gateway_config.target or not gateway_config.target.family:
            return

        target_family = gateway_config.target.family

        # pylint: disable=import-outside-toplevel
        from udmi.core.managers.localnet_manager import LocalnetManager
        localnet_manager: Optional[LocalnetManager] = None
        if self._device:
            localnet_manager = self._device.get_manager(LocalnetManager)

        if not localnet_manager:
            LOGGER.warning("Gateway validation skipped: LocalnetManager not active.")
            return

        supported_families = localnet_manager.get_registered_families()

        if target_family not in supported_families:
            msg = f"Gateway target family '{target_family}' is not supported. " \
                  f"Available: {supported_families}"
            LOGGER.error(msg)

            self._gateway_state.status = Entry(
                message=msg,
                level=500,
                timestamp=datetime.now(timezone.utc).isoformat()
            )
            self.trigger_state_update()
        else:
            LOGGER.info("Gateway target family '%s' is valid.", target_family)
            if self._gateway_state.status and self._gateway_state.status.level >= 500:
                self._gateway_state.status = None
                self.trigger_state_update()

    def _sync_proxies(self, gateway_config: GatewayConfig) -> None:
        """
        Reconciles the list of proxies in the config with the active proxies.
        """
        config_proxies: Set[str] = set(gateway_config.proxy_ids or [])
        current_proxies: Set[str] = set(self._proxies)

        to_add = config_proxies - current_proxies
        for device_id in to_add:
            LOGGER.info("Config requests new proxy: %s", device_id)
            self.add_proxy(device_id)

        to_remove = current_proxies - config_proxies
        for device_id in to_remove:
            LOGGER.info("Config removed proxy: %s", device_id)
            self.remove_proxy(device_id)

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles commands directed at the gateway device itself.

        Args:
            command_name: The name of the command.
            payload: The command payload.
        """

    def update_state(self, state: State) -> None:
        """Populates the 'gateway' state block."""
        if not state.gateway:
            state.gateway = self._gateway_state

    # --- Public API ---

    def add_proxy(self, device_id: str,
                  config_handler: Optional[ProxyConfigHandler] = None,
                  command_handler: Optional[ProxyCommandHandler] = None) -> None:
        """
        Registers a proxy device, persists it, and sends the 'attach' message.

        Args:
            device_id: The unique ID of the proxy device.
            config_handler: Optional callback for this proxy's config updates.
            command_handler: Optional callback for this proxy's commands.
        """
        if device_id not in self._proxies:
            self._proxies.append(device_id)
            LOGGER.info("Attached proxy device: %s", device_id)
            self._save_proxies()
            if self._dispatcher:
                self._send_attach_message(device_id)
                self._dispatcher.client.register_channel_subscription("config",
                                                                      device_id)
                self._dispatcher.client.register_channel_subscription("commands/#",
                                                                      device_id)

        if config_handler:
            self._proxy_config_handlers[device_id] = config_handler
        if command_handler:
            self._proxy_command_handlers[device_id] = command_handler

    def remove_proxy(self, device_id: str) -> None:
        """
        Unregisters a proxy device, removes from persistence, and sends 'detach'.

        Args:
            device_id: The unique ID of the proxy device to remove.
        """
        if device_id in self._proxies:
            self._proxies.remove(device_id)
            self._proxy_config_handlers.pop(device_id, None)
            self._proxy_command_handlers.pop(device_id, None)
            LOGGER.info("Detached proxy device: %s", device_id)
            self._save_proxies()
            if self._dispatcher:
                self._send_detach_message(device_id)

    # --- Internal Helpers ---

    def _save_proxies(self) -> None:
        """Helper to save the current proxy list to persistence."""
        if self._device and self._device.persistence:
            self._device.persistence.set(self.PERSISTENCE_KEY, self._proxies)

    def handle_proxy_config(self, device_id: str, config: Config) -> None:
        """
        Routes configuration updates to the specific proxy handler.

        Args:
            device_id: The ID of the proxy device receiving config.
            config: The parsed configuration object.
        """
        handler = self._proxy_config_handlers.get(device_id, self._default_config_handler)
        if handler:
            try:
                handler(device_id, config)
            except Exception as e: # pylint: disable=broad-exception-caught
                LOGGER.error("Error in proxy config handler for %s: %s", device_id, e)

    def handle_proxy_command(self, device_id: str, command_name: str, payload: dict) -> None:
        """
        Routes commands to the specific proxy handler.

        Args:
            device_id: The ID of the proxy device receiving the command.
            command_name: The name of the command.
            payload: The command payload dictionary.
        """
        handler = self._proxy_command_handlers.get(device_id, self._default_command_handler)
        if handler:
            try:
                handler(device_id, command_name, payload)
            except Exception as e: # pylint: disable=broad-exception-caught
                LOGGER.error("Error in proxy command handler for %s: %s", device_id, e)

    def _send_attach_message(self, device_id: str) -> None:
        """
        Publishes an MQTT 'attach' message to the broker for the given device.

        Args:
            device_id: The ID of the proxy device to attach.
        """
        if self._dispatcher:
            self._dispatcher.client.publish("attach", "", device_id=device_id)

    def _send_detach_message(self, device_id: str) -> None:
        """
        Publishes an MQTT 'detach' message to the broker for the given device.

        Args:
            device_id: The ID of the proxy device to detach.
        """
        if self._dispatcher:
            self._dispatcher.client.publish("detach", "", device_id=device_id)

    def publish_proxy_state(self, device_id: str, state: DataModel) -> None:
        """
        Publishes a state update on behalf of a proxy device.

        Args:
            device_id: The ID of the proxy device.
            state: The state object to publish.
        """
        if self._dispatcher:
            self._dispatcher.publish_state(state, device_id=device_id)

    def publish_proxy_event(self, device_id: str, event_model: DataModel, subfolder: str) -> None:
        """
        Publishes an event (telemetry) on behalf of a proxy device.

        Args:
            device_id: The ID of the proxy device.
            event_model: The event data model to publish.
            subfolder: The event subfolder (e.g., 'system', 'pointset').
        """
        if self._dispatcher:
            self._dispatcher.publish_event(f"events/{subfolder}", event_model, device_id=device_id)

    def set_default_handlers(self, config_handler: Optional[ProxyConfigHandler] = None,
                             command_handler: Optional[ProxyCommandHandler] = None) -> None:
        """
        Sets the default handlers for proxies that don't have specific ones registered.

        Args:
            config_handler: The default callback for config updates.
            command_handler: The default callback for commands.
        """
        self._default_config_handler = config_handler
        self._default_command_handler = command_handler
