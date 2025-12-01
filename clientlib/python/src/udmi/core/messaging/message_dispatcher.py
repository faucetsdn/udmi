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
from udmi.schema import State

LOGGER = logging.getLogger(__name__)


class MessageDispatcher(AbstractMessageDispatcher):
    """
    Dispatches incoming messages to registered handlers based on channel
    patterns. Also serializes and publishes outgoing messages.

    This class acts as the "glue" layer between the Device (which
    understands UDMI logic) and the AbstractMessagingClient (which
    understands the network protocol).
    """

    def __init__(self, client: AbstractMessagingClient,
        on_ready_callback: Callable[[], None],
        on_disconnect_callback: Callable[[int], None], ):
        """
        Initializes the MessageDispatcher.

        Args:
            client: A concrete instance of AbstractMessagingClient (e.g.,
                   MqttMessagingClient). The dispatcher will set the
                   client's message handler to its own internal router.
            on_ready_callback: The function to call when the client is connected.
            on_disconnect_callback: The function to call when the client
                                     disconnects.
        """
        self.client = client
        self._handlers: Dict[str, MessageHandler] = {}
        self._wildcard_handlers: Dict[re.Pattern, MessageHandler] = {}

        self._on_ready_callback = on_ready_callback
        self._on_disconnect_callback = on_disconnect_callback

        self.client.set_on_message_handler(self._on_message)
        self.client.set_on_connect_handler(self._on_connect)
        self.client.set_on_disconnect_handler(self._on_disconnect)

    def register_handler(self, channel: str, handler: MessageHandler) -> None:
        """
        Registers a handler function for a specific message channel.

        Supports MQTT-style wildcards:
        - '+' (single-level): e.g., 'commands/+/sub'
        - '#' (multi-level): e.g., 'commands/#'

        Args:
            channel: The channel to subscribe to (e.g., 'config',
                            'commands/#').
            handler: The function to call when a message arrives on that
                            channel. The function must accept (channel,
                            payload_dict).
        """
        self.client.register_channel_subscription(channel)
        if '#' in channel or '+' in channel:
            safe_pattern = re.escape(channel)
            safe_pattern = safe_pattern.replace(r'\+', '[^/]+').replace(r'\#',
                                                                        '.+')
            pattern = re.compile(f"^{safe_pattern}$")

            self._wildcard_handlers[pattern] = handler
            LOGGER.debug("Registered wildcard handler for %s", channel)
        else:
            self._handlers[channel] = handler
            LOGGER.debug("Registered handler for %s", channel)

    def _on_message(self, device_id: str, channel: str, payload: str) -> None:
        """
        Internal callback given to the MessagingClient.

        This method is the single entry point for all messages from the client.
        It deserializes the JSON payload and routes it to the correct handler.

        Args:
            device_id: The device identifier.
            channel: The raw channel the message arrived on.
            payload: The raw string payload (expected to be JSON).
        """
        try:
            payload_dict: Dict[str, Any] = json.loads(payload)
        except json.JSONDecodeError as e:
            LOGGER.error(
                "Failed to decode JSON payload for %s on channel %s: %s",
                device_id, channel, e)
            return

        handler = self._handlers.get(channel)
        if handler:
            try:
                handler(device_id, channel, payload_dict)
            except (TypeError, KeyError, AttributeError) as e:
                LOGGER.error("Handler for channel %s failed: %s", channel, e)
            return

        for pattern, wildcard_handler in self._wildcard_handlers.items():
            if pattern.match(channel):
                try:
                    wildcard_handler(device_id, channel, payload_dict)
                except (TypeError, KeyError, AttributeError) as e:
                    LOGGER.error("Wildcard handler for %s failed: %s",
                                 channel, e)
                return

        LOGGER.warning("No handler found for message on channel: %s", channel)

    def publish_state(self, state: State,
        device_id: Optional[str] = None) -> None:
        """
        Serializes and publishes the device State message.

        Args:
            state: The State data model instance to publish.
            device_id: The device identifier.
        """
        LOGGER.debug("Publishing 'state' message for %s...",
                     device_id or "self")
        try:
            payload = state.to_json()
            self.client.publish("state", payload, device_id)
        except (TypeError, AttributeError) as e:
            LOGGER.error("Failed to serialize and publish state: %s", e)

    def publish_event(self, channel: str, event: DataModel,
        device_id: Optional[str] = None) -> None:
        """
        Serializes and publishes a device Event message to a sub-channel.

        Args:
            channel: The event sub-channel (e.g., 'pointset', 'system').
            event: The event data model instance to publish.
            device_id: The device identifier.
        """
        LOGGER.debug("Publishing event to '%s' channel for %s...", channel,
                     device_id or "self")
        try:
            payload = event.to_json()
            self.client.publish(channel, payload, device_id)
        except (TypeError, AttributeError) as e:
            LOGGER.error("Failed to serialize and publish event %s: %s",
                         channel, e)

    # --- Lifecycle Methods ---

    def connect(self) -> None:
        """Connects the underlying client."""
        LOGGER.debug("Dispatcher telling client to connect...")
        self.client.connect()

    def start_loop(self) -> None:
        """Starts the client's non-blocking loop."""
        LOGGER.debug("Dispatcher telling client to start loop...")
        self.client.run()

    def check_authentication(self) -> None:
        """Triggers the client's auth check."""
        LOGGER.debug("Dispatcher telling client to check authentication...")
        self.client.check_authentication()

    def close(self) -> None:
        """Shuts down the underlying client connection."""
        LOGGER.debug("Dispatcher telling client to close...")
        self.client.close()

    def _on_connect(self) -> None:
        """
        Callback for when the client connects and has subscribed.
        Notifies the device that it is ready.
        """
        LOGGER.info(
            "Dispatcher: Client connected and subscriptions are active.")
        self._on_ready_callback()

    def _on_disconnect(self, rc: int) -> None:
        """Callback for when the client disconnects."""
        LOGGER.warning("Dispatcher: Client disconnected (rc: %s)", rc)
        self._on_disconnect_callback(rc)
