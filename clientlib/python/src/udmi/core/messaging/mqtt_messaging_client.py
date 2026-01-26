"""
A concrete implementation of the AbstractMessagingClient for the MQTT protocol.

This module wraps the Paho MQTT library to provide a UDMI-compliant messaging
client that handles connection management, TLS, authentication refreshing,
and topic parsing.
"""

import logging
import re
from dataclasses import dataclass
from typing import Any
from typing import Optional
from typing import Set
from typing import Tuple

import paho.mqtt.client as mqtt
from jwt.exceptions import PyJWTError

from udmi.core.auth.intf.auth_provider import AuthProvider
from udmi.core.auth.no_auth_provider import NoAuthProvider
from udmi.core.messaging.abstract_client import AbstractMessagingClient
from udmi.core.messaging.abstract_client import OnConnectHandler
from udmi.core.messaging.abstract_client import OnDisconnectHandler
from udmi.core.messaging.abstract_client import OnMessageHandler
from udmi.schema import EndpointConfiguration

LOGGER = logging.getLogger(__name__)


@dataclass
class TlsConfig:
    """TLS configuration parameters."""
    ca_certs: Optional[str] = None
    cert_file: Optional[str] = None
    key_file: Optional[str] = None
    enable_tls: Optional[bool] = None


@dataclass
class ReconnectConfig:
    """Reconnection backoff parameters."""
    min_delay_sec: int = 1
    max_delay_sec: int = 60


@dataclass
class MqttClientCallbacks:
    """Dataclass to hold external callbacks for the MQTT client."""
    on_message: Optional[OnMessageHandler] = None
    on_connect: Optional[OnConnectHandler] = None
    on_disconnect: Optional[OnDisconnectHandler] = None


class MqttMessagingClient(AbstractMessagingClient):
    """
    Manages the connection to an MQTT broker using the paho-mqtt library.
    Supports Gateway functionality (publishing/subscribing for proxies).
    """

    # pylint: disable=too-many-instance-attributes

    def __init__(self, endpoint_config: EndpointConfiguration,
        auth_provider: Optional[AuthProvider] = None,
        tls_config: Optional[TlsConfig] = None,
        reconnect_config: Optional[ReconnectConfig] = None):
        """
        Initializes the MQTT Client.

        Args:
            endpoint_config: The EndpointConfiguration dataclass.
            auth_provider: The authentication provider (e.g., JWT).
            tls_config: TLS configuration parameters.
            reconnect_config: Reconnection backoff parameters.
        """
        super().__init__()

        self._config = endpoint_config
        self._auth_provider = auth_provider
        self._tls_config = tls_config or TlsConfig()
        self._reconnect_config = reconnect_config or ReconnectConfig()

        prefix = (self._config.topic_prefix or "devices").strip("/")
        self._topic_pattern = re.compile(rf"^/?{prefix}/([^/]+)/(.+)$")
        self._topic_prefix_str = prefix

        self._callbacks = MqttClientCallbacks()

        # Initialize Paho Client
        self._mqtt_client = mqtt.Client(client_id=self._config.client_id)
        self._mqtt_client.on_connect = self._on_connect
        self._mqtt_client.on_message = self._on_message
        self._mqtt_client.on_disconnect = self._on_disconnect

        # Track subscriptions to re-apply on reconnect
        self._subscribed_channels: Set[Tuple[str, str]] = set()

    @property
    def _device_id(self) -> str:
        """Returns the device ID, extracted from the client ID."""
        return self._config.client_id.split('/')[-1]

    @property
    def _enable_tls(self) -> bool:
        """Infers the TLS setting based on config and port."""
        if self._tls_config.enable_tls is not None:
            return self._tls_config.enable_tls

        is_secure_port = self._config.port == 8883
        certs_provided = any([
            self._tls_config.ca_certs,
            self._tls_config.cert_file,
            self._tls_config.key_file
        ])
        return is_secure_port or certs_provided

    def connect(self) -> None:
        """Configures and initiates the connection to the broker."""
        LOGGER.info("Connecting to MQTT broker at %s:%s",
                    self._config.hostname, self._config.port)

        if self._enable_tls:
            self._configure_tls()

        self._configure_auth()

        self._mqtt_client.reconnect_delay_set(
            min_delay=self._reconnect_config.min_delay_sec,
            max_delay=self._reconnect_config.max_delay_sec
        )

        try:
            self._mqtt_client.connect_async(self._config.hostname,
                                            self._config.port)
        except Exception as e:
            LOGGER.error("Failed to initiate connection: %s", e)
            raise

    def check_authentication(self) -> None:
        """Checks if the auth token needs to be refreshed."""
        if self._auth_provider and self._auth_provider.needs_refresh():
            LOGGER.info("Auth token expiring. Refreshing credentials...")
            try:
                username = self._auth_provider.get_username()
                password = self._auth_provider.get_password()

                self._mqtt_client.username_pw_set(username=username,
                                                  password=password)

                if self._mqtt_client.is_connected():
                    LOGGER.info("Forcing disconnect to apply new credentials.")
                    self._mqtt_client.disconnect()

            except (PyJWTError, TypeError, ValueError) as e:
                LOGGER.error("Failed during auth token refresh: %s", e)

    def publish(self, channel: str, payload: str,
        device_id: Optional[str] = None) -> None:
        """Publishes a payload to a UDMI channel."""
        target_id = device_id if device_id else self._device_id
        # Construct topic: /prefix/device_id/channel
        topic = f"{self._topic_prefix_str}/{target_id}/{channel}"
        if not topic.startswith("/"):
            topic = "/" + topic

        LOGGER.debug("Publishing %d bytes to %s", len(payload), topic)
        info = self._mqtt_client.publish(topic, payload, qos=1)
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            LOGGER.warning("Publish failed with code %s", info.rc)

    def run(self) -> None:
        """Starts the non-blocking network loop."""
        LOGGER.debug("Starting MQTT network loop...")
        self._mqtt_client.loop_start()

    def close(self) -> None:
        """Stops the network loop and disconnects."""
        LOGGER.info("Stopping MQTT network loop...")
        self._mqtt_client.loop_stop()
        self._mqtt_client.disconnect()

    def register_channel_subscription(self, channel: str,
        device_id: Optional[str] = None) -> None:
        """Stores the channel interest."""
        target_id = device_id if device_id else self._device_id
        LOGGER.debug("Registering subscription: Device=%s, Channel=%s",
                     target_id, channel)
        self._subscribed_channels.add((target_id, channel))
        if self._mqtt_client.is_connected():
            topic = f"{self._topic_prefix_str}/{target_id}/{channel}"
            if not topic.startswith("/"):
                topic = "/" + topic

            LOGGER.info("Dynamic subscription to: %s", topic)
            try:
                self._mqtt_client.subscribe(topic, qos=1)
            except Exception as e: # pylint: disable=broad-exception-caught
                LOGGER.error("Subscribe failed for %s: %s", topic, e)

    # --- Callback Setters ---

    def set_on_message_handler(self, handler: OnMessageHandler) -> None:
        self._callbacks.on_message = handler

    def set_on_connect_handler(self, handler: OnConnectHandler) -> None:
        self._callbacks.on_connect = handler

    def set_on_disconnect_handler(self, handler: OnDisconnectHandler) -> None:
        self._callbacks.on_disconnect = handler

    # --- Private Helpers ---

    def _configure_tls(self) -> None:
        LOGGER.debug("Enabling TLS connection.")
        try:
            self._mqtt_client.tls_set(
                ca_certs=self._tls_config.ca_certs,
                certfile=self._tls_config.cert_file,
                keyfile=self._tls_config.key_file
            )
        except Exception as e:
            LOGGER.error("Failed to set TLS: %s", e)
            raise

    def _configure_auth(self) -> None:
        if isinstance(self._auth_provider, NoAuthProvider):
            LOGGER.info("Authentication explicitly disabled.")
            return

        if self._auth_provider:
            LOGGER.info("Using %s for authentication.",
                        self._auth_provider.__class__.__name__)
            self._mqtt_client.username_pw_set(
                username=self._auth_provider.get_username(),
                password=self._auth_provider.get_password()
            )
        elif self._tls_config.cert_file:
            LOGGER.info("Using mTLS for authentication.")
        else:
            LOGGER.warning("No authentication method provided.")

    # --- Paho Callbacks ---

    def _on_connect(self, client: Any, _userdata: Any, _flags: Any,
        rc: int) -> None:
        if rc == 0:
            LOGGER.info("Connected to MQTT broker. Subscribing...")
            for device_id, channel in self._subscribed_channels:
                topic = f"{self._topic_prefix_str}/{device_id}/{channel}"
                if not topic.startswith("/"):
                    topic = "/" + topic
                try:
                    client.subscribe(topic, qos=1)
                except Exception as e: # pylint: disable=broad-exception-caught
                    LOGGER.error("Subscribe failed for %s: %s", topic, e)

            if self._callbacks.on_connect:
                self._callbacks.on_connect()
        else:
            LOGGER.error("MQTT connection failed with code: %s", rc)

    def _on_message(self, _client: Any, _userdata: Any, msg: Any) -> None:
        topic = msg.topic
        match = self._topic_pattern.match(topic)
        if match:
            device_id = match.group(1)
            channel = match.group(2)
            try:
                payload = msg.payload.decode('utf-8')
                if self._callbacks.on_message:
                    self._callbacks.on_message(device_id, channel, payload)
            except Exception as e: # pylint: disable=broad-exception-caught
                LOGGER.error("Error processing message on %s: %s", topic, e)
        else:
            LOGGER.warning("Unexpected topic format: %s", topic)

    def _on_disconnect(self, _client: Any, _userdata: Any, rc: int) -> None:
        if rc != 0:
            LOGGER.warning("Unexpected disconnect (rc=%s). Reconnecting...", rc)
        else:
            LOGGER.info("Clean disconnect.")

        if self._callbacks.on_disconnect:
            self._callbacks.on_disconnect(rc)
