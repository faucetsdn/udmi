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
import logging
from typing import Optional

from udmi.core.auth import BasicAuthProvider
from udmi.core.auth import CredentialManager
from udmi.core.auth import JwtAuthProvider
from udmi.core.auth.crypto_algo import get_algorithm_strategy
from udmi.core.auth.key_store import FileKeyStore
from udmi.core.messaging.abstract_client import AbstractMessagingClient
from udmi.core.messaging.abstract_dispatcher import AbstractMessageDispatcher
from udmi.core.messaging.message_dispatcher import MessageDispatcher
from udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
from udmi.core.messaging.mqtt_messaging_client import ReconnectConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import EndpointConfiguration

LOGGER = logging.getLogger(__name__)


def initialize_credential_manager(
    key_file: str,
    algorithm: str,
    tls_config: TlsConfig,
    is_mtls: bool
) -> CredentialManager:
    """
    Create a CredentialManager and ensure all keys/certs exist on disk.
    """
    store = FileKeyStore(key_file)
    algo_impl = get_algorithm_strategy(algorithm)
    manager = CredentialManager(store, algo_impl)

    manager.ensure_credentials()

    if is_mtls:
        if not tls_config.cert_file:
            tls_config.cert_file = "client_cert.pem"

        manager.ensure_certificate(tls_config.cert_file)

    return manager


def create_client_from_endpoint_config(
    config: EndpointConfiguration,
    key_file: str = None,
    tls_config: TlsConfig = None,
    reconnect_config: ReconnectConfig = None,
    credential_manager: Optional[CredentialManager] = None
) -> MqttMessagingClient:
    """
    Helper to build an mqtt client from an endpoint config object.
    """
    auth_provider = None
    key_file = key_file or "rsa_private.pem"
    tls_config = tls_config or TlsConfig()
    is_mtls = (config.auth_provider is None)

    if not credential_manager and key_file:
        credential_manager = initialize_credential_manager(
            key_file=key_file,
            algorithm=config.algorithm or "RS256",
            tls_config=tls_config,
            is_mtls=is_mtls
        )

    if config.auth_provider:
        if config.auth_provider.basic:
            auth_provider = BasicAuthProvider(
                username=config.auth_provider.basic.username,
                password=config.auth_provider.basic.password
            )
        elif config.auth_provider.jwt:
            if not credential_manager:
                raise ValueError("CredentialManager required for JWT Auth")

            auth_provider = JwtAuthProvider(
                project_id=config.auth_provider.jwt.audience,
                signer=credential_manager,
                algorithm=credential_manager.get_algorithm_name()
            )
    else:
        LOGGER.info("No AuthProvider found in config. Assuming mTLS strategy.")
        if not tls_config.key_file:
            tls_config.key_file = key_file
        if not tls_config.cert_file:
            tls_config.cert_file = "client_cert.pem"
        # if not credential_manager:
        #     credential_manager = CredentialManager(
        #         FileKeyStore(tls_config.key_file),
        #         get_algorithm_strategy(config.algorithm or "RS256")
        #     )
        # credential_manager.ensure_certificate(tls_config.cert_file)

    return MqttMessagingClient(config, auth_provider, tls_config,
                               reconnect_config)
