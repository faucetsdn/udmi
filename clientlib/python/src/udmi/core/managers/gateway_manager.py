"""
Provides the concrete implementation for the GatewayManager.
This manager handles the 'gateway' block, manages proxy device attachments,
and routes proxy-specific config/commands to registered handlers.
"""

import logging
from typing import Callable
from typing import Dict
from typing import List
from typing import Optional

from udmi.core.managers.base_manager import BaseManager
from udmi.schema import Config
from udmi.schema import GatewayState
from udmi.schema import State

LOGGER = logging.getLogger(__name__)

# Callback signatures for proxy events
ProxyConfigHandler = Callable[[str, Config], None]
ProxyCommandHandler = Callable[[str, str, dict], None]


class GatewayManager(BaseManager):
    """
    Manages the 'gateway' functionality.
    Allows this device to act as a gateway for other 'proxy' devices.
    """

    PERSISTENCE_KEY = "gateway_proxies"

    def __init__(self):
        super().__init__()
        self._proxies: List[str] = []
        self._proxy_config_handlers: Dict[str, ProxyConfigHandler] = {}
        self._proxy_command_handlers: Dict[str, ProxyCommandHandler] = {}

        # Default catch-all handlers
        self._default_config_handler: Optional[ProxyConfigHandler] = None
        self._default_command_handler: Optional[ProxyCommandHandler] = None

        LOGGER.info("GatewayManager initialized.")

    def start(self) -> None:
        """
        Called when the device loop starts.
        Restores attached proxies from persistence and re-sends attach messages.
        """
        # 1. Load persisted proxies
        if self._device and self._device.persistence:
            saved_proxies = self._device.persistence.get(self.PERSISTENCE_KEY, [])
            count_loaded = 0
            for proxy_id in saved_proxies:
                if proxy_id not in self._proxies:
                    self._proxies.append(proxy_id)
                    count_loaded += 1

            if count_loaded > 0:
                LOGGER.info("Restored %s proxies from persistence.", count_loaded)
                # Sync back to ensure consistency
                self._save_proxies()

        # 2. Re-attach everyone
        # This ensures the broker knows we are acting for these devices
        # in this new session.
        if self._proxies:
            LOGGER.info("Re-attaching %s proxy devices...", len(self._proxies))
            for device_id in self._proxies:
                self._send_attach_message(device_id)

    def handle_config(self, config: Config) -> None:
        """
        Handles the 'gateway' block of the *Gateway's* own config.
        """
        if config.gateway:
            LOGGER.debug("Received gateway config: %s", config.gateway)

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles commands directed at the Gateway itself.
        """
        pass

    def update_state(self, state: State) -> None:
        """
        Populates the 'gateway' state block.
        """
        if not state.gateway:
            state.gateway = GatewayState()

        # In the future, we can list attached families/devices here if supported
        # by the schema version.

    # --- Public API for Client Applications ---

    def add_proxy(self, device_id: str,
                  config_handler: Optional[ProxyConfigHandler] = None,
                  command_handler: Optional[ProxyCommandHandler] = None) -> None:
        """
        Registers a proxy device, persists it, and sends the 'attach' message.
        """
        if device_id not in self._proxies:
            self._proxies.append(device_id)
            LOGGER.info("Added proxy device: %s", device_id)
            self._save_proxies()

        if config_handler:
            self._proxy_config_handlers[device_id] = config_handler
        if command_handler:
            self._proxy_command_handlers[device_id] = command_handler

        # If the dispatcher is already running (e.g. dynamic add), send immediately.
        # If not (setup phase), start() will handle it.
        if self._dispatcher:
            self._send_attach_message(device_id)

    def remove_proxy(self, device_id: str) -> None:
        """
        Unregisters a proxy device, removes from persistence, and sends 'detach'.
        """
        if device_id in self._proxies:
            self._proxies.remove(device_id)
            self._proxy_config_handlers.pop(device_id, None)
            self._proxy_command_handlers.pop(device_id, None)
            LOGGER.info("Removed proxy device: %s", device_id)
            self._save_proxies()

            if self._dispatcher:
                self._send_detach_message(device_id)

    def publish_proxy_state(self, device_id: str, state: State) -> None:
        """
        Publishes a State message on behalf of a proxy device.
        """
        if not self._dispatcher:
            LOGGER.error("Cannot publish proxy state: dispatcher not set")
            return

        if device_id not in self._proxies:
            LOGGER.warning("Publishing state for unknown proxy: %s", device_id)

        self._dispatcher.publish_state(state, device_id=device_id)

    def publish_proxy_event(self, device_id: str, event_model: object, subfolder: str) -> None:
        """
        Publishes an Event message on behalf of a proxy device.
        """
        if not self._dispatcher:
            LOGGER.error("Cannot publish proxy event: dispatcher not set")
            return

        self._dispatcher.publish_event(f"events/{subfolder}", event_model, device_id=device_id)

    def set_default_handlers(self,
                             config_handler: Optional[ProxyConfigHandler] = None,
                             command_handler: Optional[ProxyCommandHandler] = None):
        """Sets handlers for any proxy that doesn't have a specific callback registered."""
        self._default_config_handler = config_handler
        self._default_command_handler = command_handler

    # --- Internal Helpers ---

    def _save_proxies(self) -> None:
        """Helper to save the current proxy list to persistence."""
        if self._device and self._device.persistence:
            self._device.persistence.set(self.PERSISTENCE_KEY, self._proxies)

    # --- Internal Routing Hooks (Called by Device) ---

    def handle_proxy_config(self, device_id: str, config: Config) -> None:
        """
        Routed method: Handles a config message meant for a proxy.
        """
        LOGGER.info("Gateway received config for proxy: %s", device_id)

        handler = self._proxy_config_handlers.get(device_id, self._default_config_handler)
        if handler:
            try:
                handler(device_id, config)
            except Exception as e:
                LOGGER.error("Error in proxy config handler for %s: %s", device_id, e)
        else:
            LOGGER.warning("No handler registered for proxy config: %s", device_id)

    def handle_proxy_command(self, device_id: str, command_name: str, payload: dict) -> None:
        """
        Routed method: Handles a command message meant for a proxy.
        """
        LOGGER.info("Gateway received command '%s' for proxy: %s", command_name, device_id)

        handler = self._proxy_command_handlers.get(device_id, self._default_command_handler)
        if handler:
            try:
                handler(device_id, command_name, payload)
            except Exception as e:
                LOGGER.error("Error in proxy command handler for %s: %s", device_id, e)
        else:
            LOGGER.warning("No handler registered for proxy command: %s", device_id)

    # --- Protocol Actions ---

    def _send_attach_message(self, device_id: str) -> None:
        """
        Publishes an empty message to the 'attach' channel.
        """
        if not self._dispatcher:
            return

        LOGGER.info("Sending ATTACH for %s", device_id)
        self._dispatcher.client.publish("attach", "{}", device_id=device_id)

    def _send_detach_message(self, device_id: str) -> None:
        """
        Publishes an empty message to the 'detach' channel.
        """
        if not self._dispatcher:
            return

        LOGGER.info("Sending DETACH for %s", device_id)
        self._dispatcher.client.publish("detach", "{}", device_id=device_id)