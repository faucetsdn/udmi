"""
Factory for creating and wiring a complete UDMI Device instance.

This module provides convenient helper functions to instantiate all
core components (Device, Dispatcher, Client, Managers) and correctly
wire them together.
"""

import logging
from dataclasses import dataclass
from dataclasses import field
from typing import List
from typing import Optional

from udmi.core.auth.auth_provider import AuthProvider
from udmi.core.auth.basic_auth_provider import BasicAuthProvider
from udmi.core.auth.jwt_auth_provider import JwtAuthProvider
from udmi.core.auth.jwt_auth_provider import JwtTokenConfig
from udmi.core.device import Device
from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.system_manager import SystemManager
from udmi.core.messaging.message_dispatcher import MessageDispatcher
from udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
from udmi.core.messaging.mqtt_messaging_client import ReconnectConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import EndpointConfiguration

LOGGER = logging.getLogger(__name__)

# pylint: disable=too-many-arguments,too-many-positional-arguments

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


# --- Internal Wiring Functions ---

def _get_default_managers() -> List[BaseManager]:
    """Returns the standard set of managers for a default device."""
    return [SystemManager()]


def _wire_device(
    mqtt_client: MqttMessagingClient,
    managers: Optional[List[BaseManager]] = None,
    additional_managers: Optional[List[BaseManager]] = None
) -> Device:
    """Internal private function to handle the final wiring of components."""
    LOGGER.debug("Wiring device components...")

    # 1. Determine the base list of managers
    if managers is not None:
        # User provided an explicit list, overriding defaults entirely.
        final_managers = managers
    else:
        final_managers = _get_default_managers()

    # 2. Add any additional managers
    if additional_managers:
        LOGGER.debug("Adding %s additional managers.", len(additional_managers))
        final_managers.extend(additional_managers)

    LOGGER.info("Device configured with %s managers: %s", len(final_managers),
                [m.__class__.__name__ for m in final_managers])

    # 3. Instantiate the Device Orchestrator (in its initial state)
    device = Device(managers=final_managers)

    # 4. Instantiate the Dispatcher, giving it the client and the device
    dispatcher = MessageDispatcher(
        client=mqtt_client,
        on_ready_callback=device.on_ready,
        on_disconnect_callback=device.on_disconnect
    )

    # 5. Use the public method to finalize the device's setup
    device.wire_up_dispatcher(dispatcher)

    LOGGER.info("Device instance created and wired successfully.")
    return device


# --- Public Factory Functions ---

def create_mqtt_device_instance(
    endpoint_config: EndpointConfiguration,
    auth_provider: Optional[AuthProvider],
    managers: Optional[List[BaseManager]] = None,
    additional_managers: Optional[List[BaseManager]] = None,
    client_config: ClientConfig = ClientConfig()
) -> Device:
    """Creates a UDMI device with a user-provided AuthProvider instance.

    Use this function when you have a custom or pre-configured AuthProvider.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        auth_provider: A pre-initialized AuthProvider instance.
        managers: (Optional) A completely custom list of managers. If provided,
            this list REPLACES the default managers (e.g., SystemManager).
            Use this only if you need full control over the device's internal
            logic and want to omit standard UDMI behaviors.
        additional_managers: (Optional) A list of extra managers to add to
            the device. These are appended to the standard defaults (or
            to the custom `managers` list if one is provided). This is the
            recommended way to add custom application logic while keeping
            standard UDMI functionality.
        client_config: (Optional) Configuration for TLS and reconnection.

    Returns:
        A fully wired, ready-to-run Device instance.
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
        managers=managers,
        additional_managers=additional_managers
    )


def create_device_with_jwt(
    endpoint_config: EndpointConfiguration,
    jwt_auth_args: JwtAuthArgs,
    managers: Optional[List[BaseManager]] = None,
    additional_managers: Optional[List[BaseManager]] = None,
    token_config: JwtTokenConfig = JwtTokenConfig(),
    client_config: ClientConfig = ClientConfig()
) -> Device:
    """Convenience factory to create a device using JWT authentication.

    This is the typical method for connecting to cloud platforms
    like GCP IoT Core.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        jwt_auth_args: Dataclass with project_id, key_file, and algorithm.
        managers: (Optional) A completely custom list of managers. If provided,
            this list REPLACES the default managers (e.g., SystemManager).
            Use this only if you need full control over the device's internal
            logic and want to omit standard UDMI behaviors.
        additional_managers: (Optional) A list of extra managers to add to
            the device. These are appended to the standard defaults (or
            to the custom `managers` list if one is provided). This is the
            recommended way to add custom application logic while keeping
            standard UDMI functionality.
        token_config: (Optional) Configuration for JWT lifetime/refresh.
        client_config: (Optional) Configuration for TLS and reconnection.

    Returns:
        A fully wired, ready-to-run Device instance.
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
        additional_managers=additional_managers,
        client_config=client_config
    )


def create_device_with_basic_auth(
    endpoint_config: EndpointConfiguration,
    username: str,
    password: str,
    managers: Optional[List[BaseManager]] = None,
    additional_managers: Optional[List[BaseManager]] = None,
    client_config: ClientConfig = ClientConfig()
) -> Device:
    """Convenience factory to create a device using Basic (username/password) auth.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        username: The MQTT username.
        password: The MQTT password.
        managers: (Optional) A completely custom list of managers. If provided,
            this list REPLACES the default managers (e.g., SystemManager).
            Use this only if you need full control over the device's internal
            logic and want to omit standard UDMI behaviors.
        additional_managers: (Optional) A list of extra managers to add to
            the device. These are appended to the standard defaults (or
            to the custom `managers` list if one is provided). This is the
            recommended way to add custom application logic while keeping
            standard UDMI functionality.
        client_config: (Optional) Configuration for TLS and reconnection.

    Returns:
        A fully wired, ready-to-run Device instance.
    """
    LOGGER.info(
        "Creating device with Basic (username/password) authentication...")

    auth_provider = BasicAuthProvider(username=username, password=password)

    return create_mqtt_device_instance(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        managers=managers,
        additional_managers=additional_managers,
        client_config=client_config
    )
