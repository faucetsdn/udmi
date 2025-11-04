"""
Factory for creating and wiring a complete UDMI Device instance.

This module provides convenient helper functions to instantiate all
core components (Device, Dispatcher, Client, Managers) and correctly
wire them together.
"""

import logging
from dataclasses import dataclass, field
from typing import List
from typing import Optional

from udmi.core.auth.auth_provider import AuthProvider
from udmi.core.auth.basic_auth_provider import BasicAuthProvider
from udmi.core.auth.jwt_auth_provider import JwtAuthProvider, JwtTokenConfig
from udmi.core.device import Device
from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.system_manager import SystemManager
from udmi.core.messaging.message_dispatcher import MessageDispatcher
from udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
from udmi.core.messaging.mqtt_messaging_client import ReconnectConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import EndpointConfiguration

LOGGER = logging.getLogger(__name__)


@dataclass
class ClientConfig:
    """Groups all client-level (TLS, Reconnect) configurations."""
    tls_config: TlsConfig = field(default_factory=TlsConfig)
    reconnect_config: ReconnectConfig = field(default_factory=ReconnectConfig)


@dataclass
class JwtAuthArgs:
    """Groups arguments specific to creating a JWT Auth Provider."""
    project_id: str
    key_file: str
    algorithm: str


# --- Internal Wiring Function ---

def _wire_device(
    mqtt_client: MqttMessagingClient,
    managers: Optional[List[BaseManager]] = None
) -> Device:
    """
    Internal private function to handle the final wiring of components.
    """
    LOGGER.debug("Wiring device components...")

    # 1. Create default managers if none are provided
    if managers is None:
        LOGGER.info("No custom managers provided, using default SystemManager.")
        managers = [SystemManager()]

    # 2. Instantiate the Device Orchestrator (in its initial state)
    device = Device(managers=managers)

    # 3. Instantiate the Dispatcher, giving it the client and the device
    dispatcher = MessageDispatcher(
        client=mqtt_client,
        on_ready_callback=device.on_ready,
        on_disconnect_callback=device.on_disconnect
    )

    # 4. Use the public method to finalize the device's setup
    device.wire_up_dispatcher(dispatcher)

    LOGGER.info("Device instance created and wired successfully.")
    return device


# --- Public Factory Functions ---

def create_mqtt_device_instance(
    endpoint_config: EndpointConfiguration,
    auth_provider: Optional[AuthProvider],
    managers: Optional[List[BaseManager]] = None,
    client_config: ClientConfig = ClientConfig()
) -> Device:
    """
    Creates a UDMI device with a user-provided AuthProvider instance.

    Use this function when you have a custom or pre-configured
    AuthProvider.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        auth_provider: A pre-initialized AuthProvider instance.
        managers: (Optional) A list of managers. Uses SystemManager if None.
        client_config: (Optional) Configuration for TLS and reconnection.

    Returns:
        :return: A fully wired, ready-to-run Device instance.
    """
    LOGGER.info("Creating device with custom AuthProvider...")

    client = MqttMessagingClient(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        tls_config=client_config.tls_config,
        reconnect_config=client_config.reconnect_config
    )

    return _wire_device(
        mqtt_client=client,
        managers=managers
    )


def create_device_with_jwt(
    endpoint_config: EndpointConfiguration,
    jwt_auth_args: JwtAuthArgs,
    managers: Optional[List[BaseManager]] = None,
    token_config: JwtTokenConfig = JwtTokenConfig(),
    client_config: ClientConfig = ClientConfig()
) -> Device:
    """
    Convenience factory to create a device using JWT authentication.

    This is the typical method for connecting to cloud platforms
    like GCP IoT Core.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        jwt_auth_args: Dataclass with project_id, key_file, and algorithm.
        managers: (Optional) A list of managers. Uses SystemManager if None.
        token_config: (Optional) Configuration for JWT lifetime/refresh.
        client_config: (Optional) Configuration for TLS and reconnection.

    Returns:
        :return: A fully wired, ready-to-run Device instance.
    """
    LOGGER.info("Creating device with JWT authentication...")

    auth_provider = JwtAuthProvider(
        project_id=jwt_auth_args.project_id,
        private_key_file=jwt_auth_args.key_file,
        algorithm=jwt_auth_args.algorithm,
        token_config=token_config
    )

    return create_mqtt_device_instance(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        managers=managers,
        client_config=client_config
    )


def create_device_with_basic_auth(
    endpoint_config: EndpointConfiguration,
    username: str,
    password: str,
    managers: Optional[List[BaseManager]] = None,
    client_config: ClientConfig = ClientConfig()
) -> Device:
    """
    Convenience factory to create a device using Basic (username/password) auth.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        username: The MQTT username.
        password: The MQTT password.
        managers: (Optional) A list of managers. Uses SystemManager if None.
        client_config: (Optional) Configuration for TLS and reconnection.

    Returns:
        :return: A fully wired, ready-to-run Device instance.
    """
    LOGGER.info(
        "Creating device with Basic (username/password) authentication...")

    auth_provider = BasicAuthProvider(username=username, password=password)

    return create_mqtt_device_instance(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        managers=managers,
        client_config=client_config
    )
