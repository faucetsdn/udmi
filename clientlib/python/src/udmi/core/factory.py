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

from udmi.core.auth import CertManager
from udmi.core.auth.auth_provider import AuthProvider
from udmi.core.auth.basic_auth_provider import BasicAuthProvider
from udmi.core.auth.jwt_auth_provider import JwtAuthProvider
from udmi.core.auth.jwt_auth_provider import JwtTokenConfig
from udmi.core.device import Device
from udmi.core.managers import PointsetManager
from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.pointset_manager import DEFAULT_SAMPLE_RATE_SEC
from udmi.core.managers.system_manager import SystemManager
from udmi.core.messaging import create_client_from_endpoint_config
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


# --- Public Helper Functions ---

def get_default_managers(**kwargs) -> List[BaseManager]:
    """
    Returns the standard set of managers for a default device.
    Accepts **kwargs to allow flexible injection of dependencies
    into the default managers.

    Args:
        **kwargs:
            system_state (SystemState): Initial system state for SystemManager.
            sample_rate_sec (int): Sample rate in seconds for PointsetManager.
    """
    system_state = kwargs.get('system_state')
    sample_rate_sec = kwargs.get('sample_rate_sec', DEFAULT_SAMPLE_RATE_SEC)

    return [
        SystemManager(system_state=system_state),
        PointsetManager(sample_rate_sec=sample_rate_sec),
    ]


# --- Internal Wiring Functions ---

def _wire_device(
    mqtt_client: MqttMessagingClient,
    managers: Optional[List[BaseManager]] = None,
    endpoint_config: Optional[EndpointConfiguration] = None,
    persistence_path: str = None
) -> Device:
    """
    Internal private function to handle the final wiring of components.
    Args:
        mqtt_client: MqttMessagingClient instance
        managers: list of BaseManager instances
    """
    LOGGER.debug("Wiring device components...")
    final_managers = managers or get_default_managers()
    LOGGER.info("Device configured with %s managers: %s",
                len(final_managers),
                [m.__class__.__name__ for m in final_managers])

    device = Device(managers=final_managers, endpoint_config=endpoint_config,
                    persistence_path=persistence_path)
    dispatcher = MessageDispatcher(
        client=mqtt_client,
        on_ready_callback=device.on_ready,
        on_disconnect_callback=device.on_disconnect
    )
    device.wire_up_dispatcher(dispatcher)
    LOGGER.info("Device instance created and wired successfully.")
    return device


# --- Public Factory Functions ---

def create_device(
    endpoint_config: EndpointConfiguration,
    managers: Optional[List[BaseManager]] = None,
    client_config: ClientConfig = None,
    key_file: Optional[str] = None,
    persistence_path: str = None
) -> Device:
    """
    **[Recommended]** Unified Smart Factory.
    """
    LOGGER.info("Determining auth strategy for %s...",
                endpoint_config.client_id)
    client_config = client_config or ClientConfig()

    client = create_client_from_endpoint_config(
        endpoint_config, key_file,
        client_config.tls_config, client_config.reconnect_config
    )

    return _wire_device(mqtt_client=client, managers=managers,
                        endpoint_config=endpoint_config,
                        persistence_path=persistence_path)


def create_mqtt_device_instance(
    endpoint_config: EndpointConfiguration,
    auth_provider: Optional[AuthProvider],
    managers: Optional[List[BaseManager]] = None,
    client_config: ClientConfig = None,
    persistence_path: str = None
) -> Device:
    """Creates a UDMI device with a user-provided AuthProvider instance.

    Use this function when you have a custom or pre-configured AuthProvider.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        auth_provider: A pre-initialized AuthProvider instance.
        managers: (Optional) List of managers. If not provided, this defaults
                   to the list of default managers.
        client_config: (Optional) Configuration for TLS and reconnection.
        persistence_path: (Optional) Path to the persistence directory.

    Returns:
        A fully wired, ready-to-run Device instance.
    """
    LOGGER.info("Creating device with custom AuthProvider...")
    client_config = client_config or ClientConfig()

    client = MqttMessagingClient(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        tls_config=client_config.tls_config,
        reconnect_config=client_config.reconnect_config
    )

    return _wire_device(
        mqtt_client=client,
        managers=managers,
        endpoint_config=endpoint_config,
        persistence_path=persistence_path
    )


def create_device_with_jwt(
    endpoint_config: EndpointConfiguration,
    jwt_auth_args: JwtAuthArgs,
    managers: Optional[List[BaseManager]] = None,
    token_config: JwtTokenConfig = None,
    client_config: ClientConfig = None,
    persistence_path: str = None
) -> Device:
    """Convenience factory to create a device using JWT authentication.

    This is the typical method for connecting to cloud platforms
    like GCP IoT Core.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        jwt_auth_args: Dataclass with project_id, key_file, and algorithm.
        managers: (Optional) List of managers. If not provided, this defaults
                   to the list of default managers.
        token_config: (Optional) Configuration for JWT lifetime/refresh.
        client_config: (Optional) Configuration for TLS and reconnection.
        persistence_path: (Optional) Path to the persistence directory.

    Returns:
        A fully wired, ready-to-run Device instance.
    """
    LOGGER.info("Creating device with JWT authentication...")
    token_config = token_config or JwtTokenConfig()
    client_config = client_config or ClientConfig()
    CertManager(jwt_auth_args.key_file).ensure_keys_exist(
        jwt_auth_args.algorithm)

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
        client_config=client_config,
        persistence_path=persistence_path
    )


def create_device_with_basic_auth(
    endpoint_config: EndpointConfiguration,
    username: str,
    password: str,
    managers: Optional[List[BaseManager]] = None,
    client_config: ClientConfig = None,
    persistence_path: str = None
) -> Device:
    """Convenience factory to create a device using Basic (username/password) auth.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        username: The MQTT username.
        password: The MQTT password.
        managers: (Optional) List of managers. If not provided, this defaults
                   to the list of default managers.
        client_config: (Optional) Configuration for TLS and reconnection.
        persistence_path: (Optional) Path to the persistence file.

    Returns:
        A fully wired, ready-to-run Device instance.
    """
    LOGGER.info(
        "Creating device with Basic (username/password) authentication...")
    client_config = client_config or ClientConfig()

    auth_provider = BasicAuthProvider(username=username, password=password)

    return create_mqtt_device_instance(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        managers=managers,
        client_config=client_config,
        persistence_path=persistence_path
    )
