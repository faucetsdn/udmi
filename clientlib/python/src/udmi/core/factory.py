"""
Factory for creating and wiring a complete UDMI Device instance.

This module provides convenient helper functions to instantiate all
core components (Device, Dispatcher, Client, Managers) and correctly
wire them together.
"""

import logging
from dataclasses import dataclass
from dataclasses import field
from functools import partial
from typing import Callable
from typing import List
from typing import Optional

from udmi.core.auth import CertManager
from udmi.core.auth.auth_provider import AuthProvider
from udmi.core.device import Device
from udmi.core.managers import PointsetManager
from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.pointset_manager import DEFAULT_SAMPLE_RATE_SEC
from udmi.core.managers.system_manager import SystemManager
from udmi.core.messaging import AbstractMessageDispatcher
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
    persistence_path: str = None,
    connection_factory: Optional[Callable] = None,
    cert_manager: Optional["CertManager"] = None
) -> Device:
    """
    Internal private function to handle the final wiring of components.
    Args:
        mqtt_client: MqttMessagingClient instance
        managers: list of BaseManager instances
        endpoint_config: The initial endpoint configuration
        persistence_path: Path to persistent file store.
        connection_factory: A callable (factory) used to create NEW dispatchers
                            during a connection reset.
    """
    LOGGER.debug("Wiring device components...")
    final_managers = managers or get_default_managers()
    LOGGER.info("Device configured with %s managers: %s",
                len(final_managers),
                [m.__class__.__name__ for m in final_managers])

    device = Device(
        managers=final_managers,
        endpoint_config=endpoint_config,
        persistence_path=persistence_path,
        connection_factory=connection_factory,
        cert_manager=cert_manager
    )

    dispatcher = MessageDispatcher(
        client=mqtt_client,
        on_ready_callback=device.on_ready,
        on_disconnect_callback=device.on_disconnect
    )
    device.wire_up_dispatcher(dispatcher)
    LOGGER.info("Device instance created and wired successfully.")
    return device


def _create_dispatcher_stack(
    endpoint_config: EndpointConfiguration,
    on_ready: Callable[[], None],
    on_disconnect: Callable[[int], None],
    key_file: str,
    client_config: ClientConfig = None,
) -> AbstractMessageDispatcher:
    """
    Helper that builds the full Client -> Dispatcher stack based on
    config inference.
    """
    client = create_client_from_endpoint_config(
        config=endpoint_config,
        key_file=key_file,
        tls_config=client_config.tls_config if client_config else None,
        reconnect_config=client_config.reconnect_config if client_config else None
    )

    dispatcher = MessageDispatcher(
        client=client,
        on_ready_callback=on_ready,
        on_disconnect_callback=on_disconnect,
    )

    return dispatcher


def _create_dispatcher_stack_with_auth(
    endpoint_config: EndpointConfiguration,
    on_ready: Callable[[], None],
    on_disconnect: Callable[[int], None],
    auth_provider: AuthProvider,
    client_config: ClientConfig = None,
) -> AbstractMessageDispatcher:
    """
    Helper that builds the stack using an explicit AuthProvider
    """
    client = MqttMessagingClient(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        tls_config=client_config.tls_config if client_config else None,
        reconnect_config=client_config.reconnect_config if client_config else None
    )

    dispatcher = MessageDispatcher(
        client=client,
        on_ready_callback=on_ready,
        on_disconnect_callback=on_disconnect,
    )

    return dispatcher


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

    cert_manager = None
    if key_file:
        cert_file = client_config.tls_config.cert_file if client_config.tls_config else None
        cert_manager = CertManager(key_file=key_file, cert_file=cert_file)

    client = create_client_from_endpoint_config(
        endpoint_config, key_file,
        client_config.tls_config, client_config.reconnect_config
    )

    connection_factory = partial(
        _create_dispatcher_stack,
        key_file=key_file,
        client_config=client_config
    )

    return _wire_device(
        mqtt_client=client,
        managers=managers,
        endpoint_config=endpoint_config,
        persistence_path=persistence_path,
        connection_factory=connection_factory,
        cert_manager=cert_manager,
    )
