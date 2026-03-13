"""
Messaging package for the UDMI device.

This package defines the core abstract interfaces for messaging:
- AbstractMessagingClient: Contract for protocol-specific clients (e.g., MQTT).
- AbstractMessageDispatcher: Contract for routing and serializing messages.

It also provides concrete implementations:
- MqttMessagingClient: A client for the MQTT protocol.
- MessageDispatcher: A dispatcher that links a client to the device logic.

Helper functions are provided to simplify client instantiation from configuration.
"""
import logging
import os
from typing import Optional

from udmi.core.auth.basic_auth_provider import BasicAuthProvider
from udmi.core.auth.credential_manager import CredentialManager
from udmi.core.auth.crypto_algo import get_algorithm_strategy
from udmi.core.auth.jwt_auth_provider import JwtAuthProvider
from udmi.core.auth.file_key_store import FileKeyStore
from udmi.core.auth.no_auth_provider import NoAuthProvider
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
    Creates a CredentialManager and ensures all keys/certs exist on disk.

    Args:
        key_file: Path to the private key.
        algorithm: Key algorithm (e.g., RS256).
        tls_config: TLS configuration object (may be modified).
        is_mtls: If True, ensures a client certificate is generated.

    Returns:
        A configured CredentialManager instance.
    """
    store = FileKeyStore(key_file)
    algo_impl = get_algorithm_strategy(algorithm)
    manager = CredentialManager(store, algo_impl)

    manager.ensure_credentials()

    if is_mtls:
        if not tls_config.cert_file:
            base, _ = os.path.splitext(key_file)
            tls_config.cert_file = f"{base}.crt"

        manager.ensure_certificate(tls_config.cert_file)

    return manager


def create_client_from_endpoint_config(
    config: EndpointConfiguration,
    key_file: Optional[str] = None,
    tls_config: Optional[TlsConfig] = None,
    reconnect_config: Optional[ReconnectConfig] = None,
    credential_manager: Optional[CredentialManager] = None
) -> MqttMessagingClient:
    """
    Helper to build an MQTT client from an endpoint config object.

    Logic:
    1. If config has 'auth_provider' (Basic/JWT), use it.
    2. If 'key_file' exists (or is created), assume mTLS or JWT.
    3. If 'key_file' MISSING and no 'auth_provider', fallback to NoAuthProvider.
    """
    auth_provider = None
    target_key_file = key_file or "rsa_private.pem"

    key_exists = os.path.exists(target_key_file)

    tls_config = tls_config or TlsConfig()

    is_mtls = config.auth_provider is None

    # Initialize CredentialManager if needed (for JWT signing OR mTLS gen)
    # We do this if:
    # 1. User explicitly passed a key_file (implies they want to use/gen it)
    # 2. OR the key file already exists
    # 3. OR we need to sign a JWT (config.auth_provider.jwt exists)
    needs_creds = (
        (config.auth_provider and config.auth_provider.jwt) or
        is_mtls or
        key_exists or
        key_file is not None
    )

    if not credential_manager and needs_creds:
        credential_manager = initialize_credential_manager(
            key_file=target_key_file,
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
                raise ValueError("CredentialManager required for JWT Auth.")

            auth_provider = JwtAuthProvider(
                project_id=config.auth_provider.jwt.audience,
                signer=credential_manager,
                algorithm=credential_manager.get_algorithm_name()
            )

    elif is_mtls and credential_manager:
        LOGGER.info("No AuthProvider found in config. Using mTLS strategy.")
        if not tls_config.key_file:
            tls_config.key_file = target_key_file

    else:
        LOGGER.info(
            "No private key found at '%s' and no AuthProvider in config. "
            "Configuring for No-Auth mode.", target_key_file
        )
        auth_provider = NoAuthProvider()

    return MqttMessagingClient(config, auth_provider, tls_config,
                               reconnect_config)
