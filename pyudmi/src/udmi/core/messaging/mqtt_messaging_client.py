"""
A concrete implementation of the AbstractMessagingClient for the MQTT protocol.
"""

import logging
import ssl
from typing import Callable
from typing import Optional

import paho.mqtt.client as mqtt
from udmi.schema import EndpointConfiguration

from .abstract_client import AbstractMessagingClient
from ..auth.auth_provider import AuthProvider

LOGGER = logging.getLogger(__name__)


class MqttMessagingClient(AbstractMessagingClient):
    """
    Manages the connection to an MQTT broker using the paho-mqtt library.
    """

    def __init__(self, endpoint_config: EndpointConfiguration,
        auth_provider: Optional[AuthProvider] = None,
        ca_certs: str = None,
        cert_file: str = None,
        key_file: str = None,
        enable_tls: Optional[bool] = None,
        min_reconnect_delay_sec: int = 1,
        max_reconnect_delay_sec: int = 60):
        """
        Initializes the MQTT Client.

        Args:
            :param endpoint_config: The EndpointConfiguration dataclass
                                    containing all connection
                                    parameters (host, port, certs, etc.)
            :param auth_provider: The authentication provider (e.g., JWT or
                                  basic). This is used if mTLS is not.
            :param ca_certs: Path to the root CA certificate file for server
                             TLS verification. If None, the system trust store
                             is used.
            :param cert_file: Path to the client's public certificate file for
                              mTLS.
            :param key_file: Path to the client's private key file for mTLS.
            :param enable_tls: Explicitly enable/disable TLS. If None,
                               inferred from port and certs.
            :param min_reconnect_delay_sec: Initial delay for reconnection
                                            attempts.
            :param max_reconnect_delay_sec: Max delay for exponential backoff.
        """
        super().__init__()
        self._config = endpoint_config
        self._auth_provider = auth_provider

        # --- Callback Placeholders ---
        self._on_message_callback: Optional[Callable[[str, str], None]] = None
        self._on_connect_callback: Optional[Callable[[], None]] = None
        self._on_disconnect_callback: Optional[Callable[[int], None]] = None

        # --- Connection Parameters ---
        self._client_id = self._config.client_id
        self._hostname = self._config.hostname
        self._port = self._config.port
        self._topic_prefix = (self._config.topic_prefix or "/devices/").rstrip(
            "/")

        # --- TLS/mTLS Parameters ---
        self._ca_certs = ca_certs
        self._cert_file = cert_file
        self._key_file = key_file

        # --- Reconnection Parameters ---
        self._min_reconnect_delay = min_reconnect_delay_sec
        self._max_reconnect_delay = max_reconnect_delay_sec

        # --- Infer TLS setting ---
        if enable_tls:
            LOGGER.debug("TLS explicitly enabled by user.")
            self._enable_tls = True
        elif enable_tls is False:
            LOGGER.debug("TLS explicitly disabled by user.")
            self._enable_tls = False
        else:
            LOGGER.debug("TLS not specified, inferring based on port/certs...")
            is_secure_port = (self._port == 8883)
            certs_provided = (self._ca_certs or self._cert_file or
                              self._key_file) is not None
            self._enable_tls = is_secure_port or certs_provided
        LOGGER.info("TLS Enabled: %s", self._enable_tls)

        self._device_id = self._client_id.split('/')[-1]
        self._prefix_segments = len(self._topic_prefix.strip("/").split('/'))

        # --- Initialize Paho Client ---
        self._mqtt_client = mqtt.Client(client_id=self._client_id)
        self._mqtt_client.on_connect = self._on_connect
        self._mqtt_client.on_message = self._on_message
        self._mqtt_client.on_disconnect = self._on_disconnect

    # --- Public API Methods ---

    def connect(self) -> None:
        """
        Sets credentials, configures TLS/reconnection, and connects to the
        broker.
        """
        LOGGER.info("Connecting to MQTT broker at %s:%s",
                    self._hostname, self._port)

        if self._enable_tls:
            self._configure_tls()

        self._configure_auth()

        self._mqtt_client.reconnect_delay_set(
            min_delay=self._min_reconnect_delay,
            max_delay=self._max_reconnect_delay
        )

        self._mqtt_client.connect(self._hostname, self._port)

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
            except Exception as e:
                LOGGER.error("Failed during auth token refresh: %s", e)

    def publish(self, channel: str, payload: str) -> None:
        """
        Publishes a payload to a UDMI channel.

        Maps the channel (e.g., "state") to a full MQTT topic
        (e.g., "/devices/my-device/state") and publishes.

        Args:
            :param channel: The UDMI channel.
            :param payload: The JSON string payload to send.
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

    # --- Public Callback Setters ---

    def set_on_message_handler(self,
        handler: Callable[[str, str], None]) -> None:
        """
        Sets the external callback for incoming messages.
        This is typically called by the MessageDispatcher.

        Args:
            :param handler: A callable that accepts (channel: str, payload: str)
        """
        LOGGER.debug("Setting on_message handler")
        self._on_message_callback = handler

    def set_on_connect_handler(self, handler: Callable[[], None]) -> None:
        """
        Sets the external callback for successful connection events.

        Args:
            :param handler: A callable that takes no arguments.
        """
        LOGGER.debug("Setting on_connect handler")
        self._on_connect_callback = handler

    def set_on_disconnect_handler(self, handler: Callable[[int], None]) -> None:
        """
        Sets the external callback for disconnect events.

        Args:
            :param handler: A callable that accepts (rc: int)
        """
        LOGGER.debug("Setting on_disconnect handler")
        self._on_disconnect_callback = handler

    # --- Private Helper Methods ---

    def _configure_tls(self) -> None:
        """Sets the TLS context on the Paho client."""
        LOGGER.debug("Enabling TLS connection.")
        try:
            self._mqtt_client.tls_set(
                ca_certs=self._ca_certs,
                certfile=self._cert_file,
                keyfile=self._key_file,
                tls_version=ssl.PROTOCOL_TLS
            )
            LOGGER.debug("TLS context set. ca_certs=%s, cert_file=%s",
                         self._ca_certs, self._cert_file)
        except FileNotFoundError as e:
            LOGGER.error("A certificate file was not found: %s", e)
            raise
        except Exception as e:
            LOGGER.error("Failed to set TLS: %s", e)
            raise

    def _configure_auth(self) -> None:
        """Sets the authentication method on the Paho client."""
        if self._cert_file and self._key_file:
            LOGGER.info("Using client certificate (mTLS) for authentication.")
        elif self._auth_provider:
            LOGGER.info(f"Using {self._auth_provider.__class__.__name__} for "
                        f"authentication.")
            username = self._auth_provider.get_username()
            password = self._auth_provider.get_password()
            self._mqtt_client.username_pw_set(username=username,
                                              password=password)
        else:
            LOGGER.warning(
                "No authentication method provided (mTLS or AuthProvider).")

    # --- Internal Paho Callbacks ---

    def _on_connect(self, client, userdata, flags, rc):
        """
        Internal Paho callback for connection events.
        Calls the external on_connect_callback.
        """
        if rc == 0:
            LOGGER.info("Successfully connected to MQTT broker.")

            # Subscribe to topics using the dynamic prefix
            config_topic = f"{self._topic_prefix}/{self._device_id}/config"
            commands_topic = f"{self._topic_prefix}/{self._device_id}/commands/#"

            client.subscribe([(config_topic, 1), (commands_topic, 1)])
            LOGGER.info("Subscribed to config and commands topics.")

            if self._on_connect_callback:
                self._on_connect_callback()
        else:
            LOGGER.error("MQTT connection failed with code: %s", rc)
            if self._on_disconnect_callback:
                self._on_disconnect_callback(rc)

    def _on_message(self, client, userdata, msg):
        """
        Internal Paho callback for message events.
        Parses the topic into a channel and calls the external on_message
        handler.
        """
        topic_parts = msg.topic.strip("/").split('/')
        channel_start_index = self._prefix_segments + 1

        if len(topic_parts) > channel_start_index:
            channel = '/'.join(topic_parts[channel_start_index:])
            payload = msg.payload.decode('utf-8')
            LOGGER.debug("Received message on channel '%s'", channel)

            if self._on_message_callback:
                self._on_message_callback(channel, payload)
        else:
            LOGGER.warning("Received message on an unexpected topic format: %s",
                           msg.topic)

    def _on_disconnect(self, client, userdata, rc):
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

        if self._on_disconnect_callback:
            self._on_disconnect_callback(rc)
