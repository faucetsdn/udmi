"""
A concrete implementation of the AbstractMessagingClient for the MQTT protocol.
"""

import logging
import socket
import ssl
from dataclasses import dataclass
from typing import Callable
from typing import Optional

import paho.mqtt.client as mqtt
from jwt.exceptions import PyJWTError

from udmi.core.auth import AuthProvider
from udmi.core.messaging.abstract_client import AbstractMessagingClient
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
    on_message: Optional[Callable[[str, str], None]] = None
    on_connect: Optional[Callable[[], None]] = None
    on_disconnect: Optional[Callable[[int], None]] = None


class MqttMessagingClient(AbstractMessagingClient):
    """
    Manages the connection to an MQTT broker using the paho-mqtt library.
    """
    _callbacks: MqttClientCallbacks

    def __init__(self, endpoint_config: EndpointConfiguration,
                 auth_provider: Optional[AuthProvider] = None,
                 tls_config: TlsConfig = None,
                 reconnect_config: ReconnectConfig = None):
        """
        Initializes the MQTT Client.

        Args:
            endpoint_config: The EndpointConfiguration dataclass
                             containing all connection
                             parameters (host, port, certs, etc.)
            auth_provider: The authentication provider (e.g., JWT or
                             basic). This is used if mTLS is not.
            tls_config: TLS configuration parameters.
            reconnect_config: Reconnection backoff parameters.
        """
        super().__init__()
        self._config = endpoint_config
        self._auth_provider = auth_provider
        self._tls_config = tls_config or TlsConfig()
        self._reconnect_config = reconnect_config or ReconnectConfig()

        # --- Callbacks ---
        self._callbacks = MqttClientCallbacks()

        # --- Initialize Paho Client ---
        self._mqtt_client = mqtt.Client(client_id=self._config.client_id)
        self._mqtt_client.on_connect = self._on_connect
        self._mqtt_client.on_message = self._on_message
        self._mqtt_client.on_disconnect = self._on_disconnect
        self._subscribed_channels = set()

    # --- Properties to derive values from config ---

    @property
    def _topic_prefix(self) -> str:
        """Returns the fully-qualified topic prefix."""
        return (self._config.topic_prefix or "/devices/").rstrip("/")

    @property
    def _device_id(self) -> str:
        """Returns the device ID, extracted from the client ID."""
        return self._config.client_id.split('/')[-1]

    @property
    def _prefix_segments(self) -> int:
        """Returns the number of segments in the topic prefix."""
        return len(self._topic_prefix.strip("/").split('/'))

    @property
    def _enable_tls(self) -> bool:
        """Infers the TLS setting based on config and port."""
        if self._tls_config.enable_tls:
            LOGGER.debug("TLS explicitly enabled by user.")
            return True
        if self._tls_config.enable_tls is False:
            LOGGER.debug("TLS explicitly disabled by user.")
            return False

        LOGGER.debug("TLS not specified, inferring based on port/certs...")
        is_secure_port = self._config.port == 8883
        certs_provided = (self._tls_config.ca_certs or
                          self._tls_config.cert_file or
                          self._tls_config.key_file) is not None
        return is_secure_port or certs_provided

    # --- Public API Methods ---

    def connect(self) -> None:
        """
        Sets credentials, configures TLS/reconnection, and connects to the
        broker.
        """
        LOGGER.info("Connecting to MQTT broker at %s:%s",
                    self._config.hostname, self._config.port)

        if self._enable_tls:
            self._configure_tls()

        self._configure_auth()

        self._mqtt_client.reconnect_delay_set(
            min_delay=self._reconnect_config.min_delay_sec,
            max_delay=self._reconnect_config.max_delay_sec
        )

        self._mqtt_client.connect(self._config.hostname, self._config.port)

    def check_authentication(self) -> None:
        """
        Checks if the auth token needs to be refreshed.

        If so, updates the client and triggers a reconnect to apply the
        new credentials. This should be called periodically by the main
        device loop.
        """
        if self._auth_provider and self._auth_provider.needs_refresh():
            LOGGER.info(
                "Authentication token is expiring. "
                "Updating credentials and reconnecting..."
            )
            try:
                username = self._auth_provider.get_username()
                password = self._auth_provider.get_password()

                self._mqtt_client.username_pw_set(username=username,
                                                  password=password)

                self._mqtt_client.reconnect()
            except (PyJWTError, TypeError, ValueError, socket.error) as e:
                LOGGER.error("Failed during auth token refresh: %s", e)

    def publish(self, channel: str, payload: str) -> None:
        """
        Publishes a payload to a UDMI channel.

        Maps the channel (e.g., "state") to a full MQTT topic
        (e.g., "/devices/my-device/state") and publishes.

        Args:
            channel: The UDMI channel.
            payload: The JSON string payload to send.
        """
        topic = f"{self._topic_prefix}/{self._device_id}/{channel}"

        LOGGER.debug("Publishing %d bytes to MQTT topic %s", len(payload),
                     topic)
        self._mqtt_client.publish(topic, payload, qos=1)

    def run(self) -> None:
        """Starts the non-blocking network loop in a background thread."""
        LOGGER.debug("Starting MQTT network loop...")
        self._mqtt_client.loop_start()

    def close(self) -> None:
        """Stops the network loop and disconnects the MQTT client."""
        LOGGER.info("Stopping MQTT network loop and disconnecting...")
        self._mqtt_client.loop_stop()
        self._mqtt_client.disconnect()
        LOGGER.info("MQTT client disconnected.")

    def register_channel_subscription(self, channel: str) -> None:
        """
        Implements the abstract method.
        Stores the channel interest to be acted upon during connection.
        """
        LOGGER.debug("Registering channel subscription interest for: %s",
                     channel)
        self._subscribed_channels.add(channel)

    # --- Public Callback Setters ---

    def set_on_message_handler(self,
                               handler: Callable[[str, str], None]) -> None:
        """
        Sets the external callback for incoming messages.
        This is typically called by the MessageDispatcher.

        Args:
            handler: A callable that accepts (channel: str, payload: str)
        """
        LOGGER.debug("Setting on_message handler")
        self._callbacks.on_message = handler

    def set_on_connect_handler(self, handler: Callable[[], None]) -> None:
        """
        Sets the external callback for successful connection events.

        Args:
            handler: A callable that takes no arguments.
        """
        LOGGER.debug("Setting on_connect handler")
        self._callbacks.on_connect = handler

    def set_on_disconnect_handler(self, handler: Callable[[int], None]) -> None:
        """
        Sets the external callback for disconnect events.

        Args:
            handler: A callable that accepts (rc: int)
        """
        LOGGER.debug("Setting on_disconnect handler")
        self._callbacks.on_disconnect = handler

    # --- Private Helper Methods ---

    def _configure_tls(self) -> None:
        """Sets the TLS context on the Paho client."""
        LOGGER.debug("Enabling TLS connection.")
        try:
            self._mqtt_client.tls_set(
                ca_certs=self._tls_config.ca_certs,
                certfile=self._tls_config.cert_file,
                keyfile=self._tls_config.key_file
            )
            LOGGER.debug("TLS context set. ca_certs=%s, cert_file=%s",
                         self._tls_config.ca_certs, self._tls_config.cert_file)
        except FileNotFoundError as e:
            LOGGER.error("A certificate file was not found: %s", e)
            raise
        except (ssl.SSLError, ValueError) as e:
            LOGGER.error("Failed to set TLS: %s", e)
            raise

    def _configure_auth(self) -> None:
        """Sets the authentication method on the Paho client."""
        if self._tls_config.cert_file and self._tls_config.key_file:
            LOGGER.info("Using client certificate (mTLS) for authentication.")
        elif self._auth_provider:
            LOGGER.info("Using %s for authentication.",
                        self._auth_provider.__class__.__name__)
            username = self._auth_provider.get_username()
            password = self._auth_provider.get_password()
            self._mqtt_client.username_pw_set(username=username,
                                              password=password)
        else:
            LOGGER.warning(
                "No authentication method provided (mTLS or AuthProvider).")

    # --- Internal Paho Callbacks ---

    def _on_connect(self, client, _userdata, _flags, rc):
        """
        Internal Paho callback for connection events.
        Calls the external on_connect_callback.
        """
        if rc == 0:
            LOGGER.info("Subscribing to registered channels...")
            subscription_failed = False

            for channel in self._subscribed_channels:
                topic = f"{self._topic_prefix}/{self._device_id}/{channel}"
                try:
                    client.subscribe(topic, 1)
                    LOGGER.debug("Subscribed to topic: %s", topic)
                except (ValueError, socket.error) as e:
                    LOGGER.error("Failed to subscribe to topic %s: %s",
                                 topic, e)
                    subscription_failed = True

            if subscription_failed:
                LOGGER.error(
                    "Subscription failed. Disconnecting to trigger retry...")
                client.disconnect()
                return

            if self._callbacks.on_connect:
                self._callbacks.on_connect()
        else:
            LOGGER.error("MQTT connection failed with code: %s", rc)

    def _on_message(self, _client, _userdata, msg):
        """
        Internal Paho callback for message events.
        Parses the topic into a channel and calls the external on_message
        handler.
        """
        topic_parts = msg.topic.strip("/").split('/')
        channel_start_index = self._prefix_segments + 1

        if len(topic_parts) > channel_start_index:
            channel = '/'.join(topic_parts[channel_start_index:])
            try:
                payload = msg.payload.decode('utf-8')
            except UnicodeDecodeError as e:
                LOGGER.error("Failed to decode message payload on %s: %s",
                             msg.topic, e)
                return
            LOGGER.debug("Received message on channel '%s'", channel)

            if self._callbacks.on_message:
                self._callbacks.on_message(channel, payload)
        else:
            LOGGER.warning("Received message on an unexpected topic format: %s",
                           msg.topic)

    def _on_disconnect(self, _client, _userdata, rc):
        """
        Internal Paho callback for disconnect events.
        Calls the external on_disconnect handler.
        Paho's loop will handle the reconnection automatically.
        """
        if rc != 0:
            LOGGER.warning(
                "Unexpectedly disconnected from MQTT broker (rc: %s). "
                "Attempting to reconnect automatically...", rc
            )
        else:
            LOGGER.info("Cleanly disconnected from MQTT broker.")

        if self._callbacks.on_disconnect:
            self._callbacks.on_disconnect(rc)
