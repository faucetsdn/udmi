"""
Factory for creating and wiring a complete UDMI Device instance.

This module provides convenient helper functions to instantiate all
core components (Device, Dispatcher, Client, Managers) and correctly
wire them together.
"""

import logging
from typing import List
from typing import Optional

from udmi.schema import EndpointConfiguration

from .auth import AuthProvider
from .auth import BasicAuthProvider
from .auth import JwtAuthProvider
from .managers import BaseManager
from .managers import SystemManager
from .messaging import MessageDispatcher
from .messaging import MqttMessagingClient
from ..core.device import Device

LOGGER = logging.getLogger(__name__)


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

def create_device_with_auth_provider(
    endpoint_config: EndpointConfiguration,
    auth_provider: Optional[AuthProvider],
    managers: Optional[List[BaseManager]] = None,
    ca_certs: Optional[str] = None,
    cert_file: Optional[str] = None,
    key_file: Optional[str] = None,
    enable_tls: Optional[bool] = None,
    min_reconnect_delay_sec: int = 1,
    max_reconnect_delay_sec: int = 60
) -> Device:
    """
    Creates a UDMI device with a user-provided AuthProvider instance.

    Use this function when you have a custom or pre-configured
    AuthProvider.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        auth_provider: A pre-initialized AuthProvider instance.
        managers: (Optional) A list of managers. Uses SystemManager if None.
        ca_certs: Path to the root CA certificate file for server TLS.
        cert_file: Path to the client's public certificate file for mTLS.
        key_file: Path to the client's private key file for mTLS.
        enable_tls: Explicitly enable/disable TLS. If None, it's inferred.
        min_reconnect_delay_sec: Initial delay for reconnection attempts.
        max_reconnect_delay_sec: Max delay for exponential backoff.

    Returns:
        :return: A fully wired, ready-to-run Device instance.
    """
    LOGGER.info("Creating device with custom AuthProvider...")

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

    return _wire_device(
        mqtt_client=client,
        managers=managers
    )


def create_device_with_jwt(
    endpoint_config: EndpointConfiguration,
    project_id: str,
    key_file: str,
    algorithm: str,
    managers: Optional[List[BaseManager]] = None,
    ca_certs: Optional[str] = None,
    token_lifetime_minutes: int = JwtAuthProvider.DEFAULT_TOKEN_LIFETIME_MINUTES,
    min_reconnect_delay_sec: int = 1,
    max_reconnect_delay_sec: int = 60
) -> Device:
    """
    Convenience factory to create a device using JWT authentication.

    This is the typical method for connecting to cloud platforms
    like GCP IoT Core.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        project_id: The cloud project ID.
        key_file: Path to the device's private key file (e.g., rsa_private.pem).
        algorithm: The signing algorithm (e.g., "RS256").
        managers: (Optional) A list of managers. Uses SystemManager if None.
        ca_certs: Path to the root CA certificate file (e.g., roots.pem).
        token_lifetime_minutes: Lifetime for the generated JWTs.
        min_reconnect_delay_sec: Initial delay for reconnection attempts.
        max_reconnect_delay_sec: Max delay for exponential backoff.

    Returns:
        :return: A fully wired, ready-to-run Device instance.
    """
    LOGGER.info("Creating device with JWT authentication...")

    auth_provider = JwtAuthProvider(
        project_id=project_id,
        private_key_file=key_file,
        algorithm=algorithm,
        token_lifetime_minutes=token_lifetime_minutes
    )

    return create_device_with_auth_provider(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        managers=managers,
        ca_certs=ca_certs,
        min_reconnect_delay_sec=min_reconnect_delay_sec,
        max_reconnect_delay_sec=max_reconnect_delay_sec
    )


def create_device_with_basic_auth(
    endpoint_config: EndpointConfiguration,
    username: str,
    password: str,
    managers: Optional[List[BaseManager]] = None,
    ca_certs: Optional[str] = None,
    enable_tls: Optional[bool] = None,
    min_reconnect_delay_sec: int = 1,
    max_reconnect_delay_sec: int = 60
) -> Device:
    """
    Convenience factory to create a device using Basic (username/password) auth.

    Args:
        endpoint_config: The EndpointConfiguration dataclass.
        username: The MQTT username.
        password: The MQTT password.
        managers: (Optional) A list of managers. Uses SystemManager if None.
        ca_certs: Path to the root CA certificate file for server TLS.
        enable_tls: Explicitly enable/disable TLS. If None, it's inferred.
        min_reconnect_delay_sec: Initial delay for reconnection attempts.
        max_reconnect_delay_sec: Max delay for exponential backoff.

    Returns:
        :return: A fully wired, ready-to-run Device instance.
    """
    LOGGER.info(
        "Creating device with Basic (username/password) authentication...")

    auth_provider = BasicAuthProvider(username=username, password=password)

    return create_device_with_auth_provider(
        endpoint_config=endpoint_config,
        auth_provider=auth_provider,
        managers=managers,
        ca_certs=ca_certs,
        enable_tls=enable_tls,
        min_reconnect_delay_sec=min_reconnect_delay_sec,
        max_reconnect_delay_sec=max_reconnect_delay_sec
    )
