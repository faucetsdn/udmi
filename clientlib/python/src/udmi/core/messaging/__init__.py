"""
Messaging package for the UDMI device.

This package defines the core abstract interfaces for messaging:
- AbstractMessagingClient: Defines the contract for a protocol-specific client (e.G., MQTT).
- AbstractMessageDispatcher: Defines the contract for routing and serializing messages.

It also provides concrete implementations:
- MqttMessagingClient: A client for the MQTT protocol.
- MessageDispatcher: A dispatcher that links a client to the device logic.

The imports in this file make these key classes directly available
from the `udmi.core.messaging` namespace for easier use.
"""

from udmi.core.messaging.abstract_client import AbstractMessagingClient
from udmi.core.messaging.abstract_dispatcher import AbstractMessageDispatcher
from udmi.core.messaging.message_dispatcher import MessageDispatcher
from udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
