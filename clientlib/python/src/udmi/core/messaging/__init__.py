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

from udmi.core.auth import BasicAuthProvider
from udmi.core.auth import JwtAuthProvider
from udmi.core.auth.cert_manager import CertManager
from udmi.core.messaging.abstract_client import AbstractMessagingClient
from udmi.core.messaging.abstract_dispatcher import AbstractMessageDispatcher
from udmi.core.messaging.message_dispatcher import MessageDispatcher
from udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
from udmi.core.messaging.mqtt_messaging_client import ReconnectConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import EndpointConfiguration

LOGGER = logging.getLogger(__name__)


def create_client_from_endpoint_config(
    config: EndpointConfiguration,
    key_file: str = None,
    tls_config: TlsConfig = None,
    reconnect_config: ReconnectConfig = None,
) -> MqttMessagingClient:
    """
    Helper to build an mqtt client from an endpoint config object.
    """
    auth_provider = None
    key_file = key_file or "rsa_private.pem"

    tls_config = tls_config or TlsConfig()

    if config.auth_provider:
        if config.auth_provider.basic:
            auth_provider = BasicAuthProvider(
                username=config.auth_provider.basic.username,
                password=config.auth_provider.basic.password
            )
        elif config.auth_provider.jwt:
            CertManager(key_file).ensure_keys_exist(config.algorithm or "RS256")
            auth_provider = JwtAuthProvider(
                project_id=config.auth_provider.jwt.audience,
                private_key_file=key_file,
                algorithm=config.algorithm or "RS256"
            )
    else:
        LOGGER.info("No AuthProvider found in config. Assuming mTLS strategy.")
        if not tls_config.key_file:
            tls_config.key_file = key_file
        if not tls_config.cert_file:
            tls_config.cert_file = "client_cert.pem"
        cert_manager = CertManager(
            key_file=tls_config.key_file,
            cert_file=tls_config.cert_file
        )
        cert_manager.ensure_keys_exist(config.algorithm or "RS256")

    return MqttMessagingClient(config, auth_provider, tls_config,
                               reconnect_config)
