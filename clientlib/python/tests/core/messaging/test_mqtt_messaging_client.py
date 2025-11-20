"""
Unit tests for the `MqttMessagingClient` class.

This module tests the `MqttMessagingClient` in isolation by mocking the
underlying `paho.mqtt.client` library.

Key behaviors verified:
- Initialization: Ensures `__init__` correctly instantiates and configures
  the Paho client with the correct client_id and internal callbacks.
-TLS Logic: Verifies the `_should_enable_tls` logic correctly
  infers whether TLS should be enabled based on port, certs, and flags.
- Auth Logic: Confirms the correct auth method is chosen, prioritizing
  mTLS (client certs) over the `AuthProvider` (username/password).
- Topic Formatting:
    - `publish`: Ensures the simple channel ('state') is correctly
      expanded to the full MQTT topic.
    - `_on_message`: Ensures the full MQTT topic is correctly parsed
      back into a simple channel.
- Subscriptions: Verifies `_on_connect` subscribes to all registered
  channels with the correctly formatted full MQTT topics.
"""

from unittest.mock import MagicMock
from unittest.mock import call

import pytest

from src.udmi.core.auth.auth_provider import AuthProvider
from src.udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import EndpointConfiguration


# pylint: disable=redefined-outer-name,protected-access,unused-argument
# pylint: disable=comparison-with-callable,too-many-arguments,too-many-positional-arguments


@pytest.fixture
def mock_auth_provider():
    """Provides a mock AuthProvider."""
    provider = MagicMock(spec=AuthProvider)
    provider.get_username.return_value = "mock_user"
    provider.get_password.return_value = "mock_pass"
    return provider


@pytest.fixture
def base_endpoint_config():
    """A base EndpointConfiguration for a device."""
    return EndpointConfiguration(
        client_id="projects/p/l/r/d",
        hostname="mock.host",
        port=8883,
        topic_prefix="/devices/"
    )


@pytest.fixture
def mqtt_client(mock_paho_client_class, base_endpoint_config,
    mock_auth_provider):
    """
    Returns an instance of MqttMessagingClient with paho mocked.
    Relies on mock_paho_client_class from conftest.py.
    """
    client = MqttMessagingClient(
        endpoint_config=base_endpoint_config,
        auth_provider=mock_auth_provider
    )
    return client


def test_init_configures_paho(mock_paho_client_class,
    mock_paho_client_instance,
    base_endpoint_config):
    """
    Test init configures paho
    Verify paho.mqtt.client.Client was called with the correct client_id.
    Verify the instance's on_connect, on_message, and on_disconnect
    properties were set.
    """
    client = MqttMessagingClient(
        endpoint_config=base_endpoint_config,
        auth_provider=None
    )

    mock_paho_client_class.assert_called_with(
        client_id=base_endpoint_config.client_id
    )

    assert mock_paho_client_instance.on_connect == client._on_connect
    assert mock_paho_client_instance.on_message == client._on_message
    assert mock_paho_client_instance.on_disconnect == client._on_disconnect


@pytest.mark.parametrize("port, cert_file, enable_tls, should_be_called", [
    (8883, None, None, True),  # Test 1: Secure port
    (1883, "path", None, True),  # Test 2: Insecure port, but cert provided
    (1883, None, False, False),  # Test 3: Insecure port, explicitly disabled
    (1883, None, None, False),
    # Test 4: Insecure port, no certs, implicit disable
    (8883, None, False, False)  # Test 5: Secure port, explicitly disabled
])
def test_tls_inference_logic(port, cert_file, enable_tls, should_be_called,
    mock_paho_client_instance, base_endpoint_config):
    """
    Tests all combinations of port, certs, and enable_tls flag.
    """
    config = base_endpoint_config
    config.port = port
    tls_config = TlsConfig(cert_file=cert_file, enable_tls=enable_tls)

    client = MqttMessagingClient(
        endpoint_config=config,
        tls_config=tls_config
    )

    try:
        client.connect()
    except FileNotFoundError:
        pass

    if should_be_called:
        mock_paho_client_instance.tls_set.assert_called_once()
    else:
        mock_paho_client_instance.tls_set.assert_not_called()


def test_auth_configuration_priority(mock_paho_client_instance,
    base_endpoint_config, mock_auth_provider):
    """
    test_auth_configuration_priority
    Test 1 (mTLS): cert_file and key_file present, provider is ignored.
    Test 2 (Provider): Only provider is present.
    """
    # Test 1: mTLS
    client_mtls = MqttMessagingClient(
        endpoint_config=base_endpoint_config,
        auth_provider=mock_auth_provider,
        tls_config=TlsConfig(cert_file="client.crt",  # mTLS certs
                             key_file="client.key")
    )

    try:
        client_mtls.connect()
    except FileNotFoundError:
        pass

    mock_paho_client_instance.tls_set.assert_called_once()
    mock_paho_client_instance.username_pw_set.assert_not_called()

    mock_paho_client_instance.reset_mock()

    # --- Test 2: Provider ---
    client_auth = MqttMessagingClient(
        endpoint_config=base_endpoint_config,
        auth_provider=mock_auth_provider,
        tls_config=TlsConfig(cert_file=None, key_file=None)
    )

    client_auth.connect()

    mock_paho_client_instance.tls_set.assert_called_once()
    mock_paho_client_instance.username_pw_set.assert_called_once_with(
        username="mock_user",
        password="mock_pass"
    )


def test_publish_topic_formatting(mqtt_client, mock_paho_client_instance):
    """
    test_publish_topic_formatting
    Verify mock_paho_client_instance.publish was called with the
            full topic.
    """
    mqtt_client.publish("state", "{\"message\": \"hi\"}")

    mock_paho_client_instance.publish.assert_called_once_with(
        "/devices/d/state",
        "{\"message\": \"hi\"}",
        qos=1
    )


def test_on_message_topic_parsing(mqtt_client):
    """
    test_on_message_topic_parsing
    Verify that mock callback was called with the simple channel.
    """
    mock_callback = MagicMock()
    mqtt_client.set_on_message_handler(mock_callback)

    mock_msg = MagicMock()
    mock_msg.topic = "/devices/d/commands/reboot"
    mock_msg.payload = b'{"delay": 10}'

    mqtt_client._on_message(None, None, mock_msg)

    mock_callback.assert_called_once_with(
        "commands/reboot",
        '{"delay": 10}'
    )


def test_on_connect_subscribes_to_channels(mqtt_client,
    mock_paho_client_instance):
    """
    test_on_connect_subscribes_to_channels
    Verify mock_paho_client_instance.subscribe was called with
    the full topic.
    """
    mqtt_client.register_channel_subscription("config")
    mqtt_client.register_channel_subscription("commands/#")

    mqtt_client._on_connect(mock_paho_client_instance, None, None, 0)

    expected_calls = [
        call("/devices/d/config", 1),
        call("/devices/d/commands/#", 1)
    ]
    mock_paho_client_instance.subscribe.assert_has_calls(
        expected_calls,
        any_order=True
    )
