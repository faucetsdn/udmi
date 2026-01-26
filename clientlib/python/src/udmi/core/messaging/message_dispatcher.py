"""
Provides a concrete implementation of the AbstractMessageDispatcher.

This module is responsible for bridging the core Device logic with the
messaging client. It routes incoming messages to the correct handlers
and serializes outgoing data models for publication.
"""

import json
import logging
import re
from typing import Any
from typing import Callable
from typing import Dict
from typing import Optional

from udmi.core.messaging.abstract_client import AbstractMessagingClient
from udmi.core.messaging.abstract_dispatcher import AbstractMessageDispatcher
from udmi.core.messaging.abstract_dispatcher import MessageHandler
from udmi.schema import DataModel

LOGGER = logging.getLogger(__name__)


class MessageDispatcher(AbstractMessageDispatcher):
    """
    Dispatches incoming messages to registered handlers based on channel
    patterns. Also serializes and publishes outgoing messages.
    """

    def __init__(self, client: AbstractMessagingClient,
        on_ready_callback: Callable[[], None],
        on_disconnect_callback: Callable[[int], None]):
        """
        Initializes the MessageDispatcher.

        Args:
            client: A concrete instance of AbstractMessagingClient.
            on_ready_callback: Called when the client is fully connected.
            on_disconnect_callback: Called when the client disconnects.
        """
        self._client = client
        self._handlers: Dict[str, MessageHandler] = {}
        self._wildcard_handlers: Dict[re.Pattern, MessageHandler] = {}

        self._on_ready_callback = on_ready_callback
        self._on_disconnect_callback = on_disconnect_callback

        # Wire up internal handlers to the client
        self._client.set_on_message_handler(self._on_message)
        self._client.set_on_connect_handler(self._on_connect)
        self._client.set_on_disconnect_handler(self._on_disconnect)

    @property
    def client(self) -> AbstractMessagingClient:
        return self._client

    def register_handler(self, channel: str, handler: MessageHandler) -> None:
        """
        Registers a handler function for a specific message channel.
        Supports MQTT-style wildcards ('+' and '#').
        """
        self._client.register_channel_subscription(channel)

        if '#' in channel or '+' in channel:
            safe_pattern = re.escape(channel)
            safe_pattern = safe_pattern.replace(r'\+', '[^/]+')
            safe_pattern = safe_pattern.replace(r'\#', '.+')

            pattern = re.compile(f"^{safe_pattern}$")
            self._wildcard_handlers[pattern] = handler
            LOGGER.debug("Registered wildcard handler for %s", channel)
        else:
            self._handlers[channel] = handler
            LOGGER.debug("Registered handler for %s", channel)

    def _on_message(self, device_id: str, channel: str, payload: str) -> None:
        """
        Internal callback: Deserializes JSON and routes to handler.
        """
        try:
            payload_dict: Dict[str, Any] = json.loads(payload)
        except json.JSONDecodeError as e:
            LOGGER.error("Failed to decode JSON from %s/%s: %s",
                         device_id, channel, e)
            return

        # 1. Exact Match
        handler = self._handlers.get(channel)
        if handler:
            self._safe_invoke(handler, device_id, channel, payload_dict)
            return

        # 2. Wildcard Match
        for pattern, wildcard_handler in self._wildcard_handlers.items():
            if pattern.match(channel):
                self._safe_invoke(wildcard_handler, device_id, channel,
                                  payload_dict)
                return

        LOGGER.warning("No handler found for message on channel: %s", channel)

    @staticmethod
    def _safe_invoke(handler: MessageHandler, device_id: str,
        channel: str, payload: Dict[str, Any]) -> None:
        """Executes a handler, catching and logging any exceptions."""
        try:
            handler(device_id, channel, payload)
        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Handler exception for %s/%s: %s",
                         device_id, channel, e, exc_info=True)

    def publish_state(self, state: DataModel,
        device_id: Optional[str] = None) -> None:
        LOGGER.debug("Publishing 'state' message...")
        try:
            payload = state.to_json()
            self._client.publish("state", payload, device_id)
        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to publish state: %s", e)

    def publish_event(self, channel: str, event: DataModel,
        device_id: Optional[str] = None) -> None:
        LOGGER.debug("Publishing event to '%s'...", channel)
        try:
            payload = event.to_json()
            self._client.publish(channel, payload, device_id)
        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to publish event %s: %s", channel, e)

    # --- Lifecycle Methods ---

    def connect(self) -> None:
        self._client.connect()

    def start_loop(self) -> None:
        self._client.run()

    def check_authentication(self) -> None:
        self._client.check_authentication()

    def close(self) -> None:
        self._client.close()

    def _on_connect(self) -> None:
        LOGGER.info("Dispatcher: Client connected.")
        self._on_ready_callback()

    def _on_disconnect(self, rc: int) -> None:
        LOGGER.warning("Dispatcher: Client disconnected (rc: %s)", rc)
        self._on_disconnect_callback(rc)
