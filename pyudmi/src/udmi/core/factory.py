"""
Factory for creating and wiring a complete UDMI Device instance.

This module provides a convenient helper function to instantiate all
core components (Device, Dispatcher, Client) and correctly wire them
together, resolving the circular dependencies between them.
"""

import logging
from typing import Optional
from typing import Type

from udmi.schema import EndpointConfiguration

from .auth.auth_provider import AuthProvider
from .device import Device
from .messaging.message_dispatcher import MessageDispatcher
from .messaging.mqtt_messaging_client import MqttMessagingClient

LOGGER = logging.getLogger(__name__)


def create_device_instance(
    device_class: Type[Device],
    endpoint_config: EndpointConfiguration,
    auth_provider: Optional[AuthProvider] = None,
    ca_certs: Optional[str] = None,
    cert_file: Optional[str] = None,
    key_file: Optional[str] = None,
    enable_tls: Optional[bool] = None,
    min_reconnect_delay_sec: int = 1,
    max_reconnect_delay_sec: int = 60
) -> Device:
    """
    Factory function to build and wire a complete UDMI Device.

    Args:
        :param device_class: The application-specific device class to
                            instantiate (e.g., MyThermostat). Must be
                            a subclass of `Device`.
        :param endpoint_config: The EndpointConfiguration dataclass.
        :param auth_provider: The authentication provider (e.g.,
                             JwtAuthProvider or BasicAuthProvider).
        :param ca_certs: Path to the root CA certificate file for server TLS.
        :param cert_file: Path to the client's public certificate file for mTLS.
        :param key_file: Path to the client's private key file for mTLS.
        :param enable_tls: Explicitly enable/disable TLS. If None, it's inferred.
        :param min_reconnect_delay_sec: Initial delay for reconnection attempts.
        :param max_reconnect_delay_sec: Max delay for exponential backoff.

    Returns:
        An initialized and fully "wired" instance of the provided
        `device_class`, ready for its `run()` method to be called.
    """
    LOGGER.info("Creating device instance for class: %s", device_class.__name__)

    # 1. Instantiate the Client
    # Callbacks are not set here; they will be wired up in step 4.
    client = MqttMessagingClient(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        ca_certs=ca_certs,
        cert_file=cert_file,
        key_file=key_file,
        enable_tls=enable_tls,
        min_reconnect_delay_sec=min_reconnect_delay_sec,
        max_reconnect_delay_sec=max_reconnect_delay_sec
    )

    # 2. Instantiate the Dispatcher
    # The dispatcher's `__init__` will call client.set_on_message_handler()
    # to register its internal `_on_message` router.
    dispatcher = MessageDispatcher(client=client)

    # 3. Instantiate the Device
    # The device's `__init__` will register its message handlers
    # (`handle_config`, `handle_command`) with the dispatcher.
    device = device_class(dispatcher=dispatcher)

    # 4. Wire up the final callbacks
    # We connect the Device's `_on_connect` and `_on_disconnect` methods to
    # the Client.
    client.set_on_connect_handler(device._on_connect)
    client.set_on_disconnect_handler(device._on_disconnect)

    LOGGER.info("Device instance created and wired.")
    return device
