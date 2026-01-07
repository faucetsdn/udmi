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

from udmi.constants import PERSISTENT_STORE_PATH
from udmi.core.auth import CredentialManager
from udmi.core.device import Device
from udmi.core.managers import DiscoveryManager
from udmi.core.managers import GatewayManager
from udmi.core.managers import LocalnetManager
from udmi.core.managers import PointsetManager
from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.pointset_manager import DEFAULT_SAMPLE_RATE_SEC
from udmi.core.managers.system_manager import SystemManager
from udmi.core.messaging import AbstractMessageDispatcher
from udmi.core.messaging import create_client_from_endpoint_config
from udmi.core.messaging import initialize_credential_manager
from udmi.core.messaging.message_dispatcher import MessageDispatcher
from udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
from udmi.core.messaging.mqtt_messaging_client import ReconnectConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.core.persistence import DevicePersistence
from udmi.core.persistence import PersistenceBackend
from udmi.core.persistence.file_backend import FilePersistenceBackend
from udmi.schema import EndpointConfiguration
from udmi.schema import Metadata

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
        LocalnetManager(),
        GatewayManager(),
        DiscoveryManager()
    ]


# --- Internal Wiring Functions ---

def _wire_device(
    mqtt_client: MqttMessagingClient,
    managers: Optional[List[BaseManager]] = None,
    endpoint_config: Optional[EndpointConfiguration] = None,
    persistence_path: str = PERSISTENT_STORE_PATH,
    connection_factory: Optional[Callable] = None,
    credential_manager: Optional["CredentialManager"] = None,
    initial_model: Optional[Metadata] = None,
    persistence_backend: Optional[PersistenceBackend] = None,
) -> Device:
    """
    Internal private function to handle the final wiring of components.

    Args:
        mqtt_client: MqttMessagingClient instance used for communication.
        managers: List of BaseManager instances to handle device logic.
                  If None, default managers are created.
        endpoint_config: The initial endpoint configuration.
        persistence_path: Path to the persistent file store (used if persistence_backend is None).
        connection_factory: A callable (factory) used to create NEW dispatchers
                            during a connection reset.
        credential_manager: Manager for handling device credentials and keys.
        initial_model: Initial Metadata model for the device.
        persistence_backend: Custom backend for persistence (e.g., database, specialized file store).
                             If None, a FilePersistenceBackend at persistence_path is used.
    """
    LOGGER.debug("Wiring device components...")
    backend = persistence_backend or FilePersistenceBackend(persistence_path)
    persistence = DevicePersistence(backend, endpoint_config)

    final_managers = managers or get_default_managers()
    LOGGER.info("Device configured with %s managers: %s",
                len(final_managers),
                [m.__class__.__name__ for m in final_managers])

    device = Device(
        managers=final_managers,
        endpoint_config=endpoint_config,
        persistence_manager=persistence,
        connection_factory=connection_factory,
        credential_manager=credential_manager,
        initial_model=initial_model
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
    credential_manager: Optional["CredentialManager"] = None
) -> AbstractMessageDispatcher:
    """
    Helper that builds the full Client -> Dispatcher stack based on
    config inference.
    """
    client = create_client_from_endpoint_config(
        config=endpoint_config,
        key_file=key_file,
        tls_config=client_config.tls_config if client_config else None,
        reconnect_config=client_config.reconnect_config if client_config else None,
        credential_manager=credential_manager
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
    persistence_path: str = PERSISTENT_STORE_PATH,
    initial_model: Optional[Metadata] = None,
    persistence_backend: Optional[PersistenceBackend] = None,
) -> Device:
    """
    Unified Smart Factory for creating a UDMI Device.

    This function orchestrates the creation of a fully functional UDMI Device, including
    setting up the MQTT client, authentication (auto-detecting strategy), managers,
    and persistence layer.

    Args:
        endpoint_config: Configuration for the connection endpoint (host, port, client_id, etc.).
        managers: Optional list of custom managers to use. If None, default managers (System, Pointset, etc.) are used.
        client_config: Optional configuration for TLS and reconnection settings.
        key_file: Optional path to the private key file. Required for JWT and mTLS auth if credentials are not otherwise provided.
        persistence_path: Path to the file used for default persistence. Defaults to '.udmi_persistence.json'.
        initial_model: Optional initial Metadata model to seed the device state.
        persistence_backend: Optional custom backend for data persistence. Overrides persistence_path if provided.

    Returns:
        A configured and wired `Device` instance ready to run.
    """
    LOGGER.info("Determining auth strategy for %s...",
                endpoint_config.client_id)
    client_config = client_config or ClientConfig()

    credential_manager = None
    if key_file:
        credential_manager = initialize_credential_manager(
            key_file=key_file,
            algorithm=endpoint_config.algorithm or "RS256",
            tls_config=client_config.tls_config,
            is_mtls=(endpoint_config.auth_provider is None)
        )

    client = create_client_from_endpoint_config(
        endpoint_config, key_file,
        client_config.tls_config, client_config.reconnect_config,
        credential_manager=credential_manager
    )

    connection_factory = partial(
        _create_dispatcher_stack,
        key_file=key_file,
        client_config=client_config,
        credential_manager=credential_manager
    )

    return _wire_device(
        mqtt_client=client,
        managers=managers,
        endpoint_config=endpoint_config,
        persistence_path=persistence_path,
        connection_factory=connection_factory,
        credential_manager=credential_manager,
        initial_model=initial_model,
        persistence_backend=persistence_backend
    )
