"""
Unit tests for the core device factory.

This module tests the high-level factory functions responsible for
instantiating and assembling the core device components.

Key behaviors verified:
- `create_device_with_jwt`: Ensures that the `JwtAuthProvider` and
  `MqttMessagingClient` are correctly instantiated with the provided
  JWT arguments and default configurations.
- `create_device_with_basic_auth`: Ensures that the `BasicAuthProvider`
  and `MqttMessagingClient` are correctly instantiated with the provided
  credentials and TLS settings.
- `_wire_device`:
    - Verifies that default managers are created and wired
      up when no custom managers are provided.
    - Verifies that custom managers are used directly when they are
      provided, and the default managers are not created.
"""

from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from src.udmi.core.factory import _wire_device
from src.udmi.core.factory import create_device_with_basic_auth
from src.udmi.core.factory import create_device_with_jwt
from udmi.core.auth.jwt_auth_provider import JwtTokenConfig
from udmi.core.factory import ClientConfig
from udmi.core.factory import JwtAuthArgs
from udmi.core.messaging.mqtt_messaging_client import ReconnectConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig


# pylint: disable=redefined-outer-name


@pytest.fixture
def mock_mqtt_client():
    """A mock MqttMessagingClient instance."""
    return MagicMock(name="mock_mqtt_client")


@pytest.fixture
def mock_manager():
    """A mock BaseManager instance."""
    return MagicMock(name="mock_manager")


@patch('src.udmi.core.factory._wire_device')
@patch('src.udmi.core.factory.MqttMessagingClient')
@patch('src.udmi.core.factory.JwtAuthProvider')
def test_create_device_with_jwt(
    mock_jwt_auth_provider,
    mock_mqtt_messaging_client,
    mock_wire_device,
    mock_endpoint_config
):
    """
    Asserts that the factory creates and passes the JwtAuthProvider.
    """
    mock_provider_instance = mock_jwt_auth_provider.return_value
    mock_client_instance = mock_mqtt_messaging_client.return_value

    create_device_with_jwt(
        endpoint_config=mock_endpoint_config,
        jwt_auth_args=JwtAuthArgs(
            project_id="test-project",
            key_file="test-key.pem",
            algorithm="RS256"
        )
    )

    mock_jwt_auth_provider.assert_called_once_with(
        project_id="test-project",
        private_key_file="test-key.pem",
        algorithm="RS256",
        token_config=JwtTokenConfig(
            lifetime_minutes=60,
            refresh_buffer_minutes=5)  # We trust the default value
    )

    mock_mqtt_messaging_client.assert_called_once_with(
        endpoint_config=mock_endpoint_config,
        auth_provider=mock_provider_instance,
        tls_config=TlsConfig(
            ca_certs=None,
            cert_file=None,
            key_file=None,
            enable_tls=None
        ),
        reconnect_config=ReconnectConfig(
            min_delay_sec=1,
            max_delay_sec=60
        )
    )

    mock_wire_device.assert_called_once_with(
        mqtt_client=mock_client_instance,
        managers=None,
        endpoint_config=mock_endpoint_config,
        persistence_path=None
    )


@patch('src.udmi.core.factory._wire_device')
@patch('src.udmi.core.factory.MqttMessagingClient')
@patch('src.udmi.core.factory.BasicAuthProvider')
def test_create_device_with_basic_auth(
    mock_basic_auth_provider,
    mock_mqtt_messaging_client,
    mock_wire_device,
    mock_endpoint_config
):
    """
    Asserts that the factory creates and passes the BasicAuthProvider
    and does NOT pass mTLS certs.
    """
    mock_provider_instance = mock_basic_auth_provider.return_value
    mock_client_instance = mock_mqtt_messaging_client.return_value

    create_device_with_basic_auth(
        endpoint_config=mock_endpoint_config,
        username="user",
        password="pass",
        managers=None,
        client_config=ClientConfig(
            tls_config=TlsConfig(ca_certs="test-ca.pem")
        )
    )

    mock_basic_auth_provider.assert_called_once_with(
        username="user",
        password="pass"
    )

    mock_mqtt_messaging_client.assert_called_once_with(
        endpoint_config=mock_endpoint_config,
        auth_provider=mock_provider_instance,
        tls_config=TlsConfig(
            ca_certs="test-ca.pem",
            cert_file=None,
            key_file=None,
            enable_tls=None
        ),
        reconnect_config=ReconnectConfig(
            min_delay_sec=1,
            max_delay_sec=60
        )
    )

    mock_wire_device.assert_called_once_with(
        mqtt_client=mock_client_instance,
        managers=None,
        endpoint_config=mock_endpoint_config,
        persistence_path=None
    )


@patch('src.udmi.core.factory.MessageDispatcher')
@patch('src.udmi.core.factory.Device')
@patch('src.udmi.core.factory.PointsetManager')
@patch('src.udmi.core.factory.SystemManager')
def test_wire_device_with_defaults(
    mock_system_manager,
    mock_pointset_manager,
    mock_device,
    mock_message_dispatcher,
    mock_mqtt_client
):
    """
    Tests the _wire_device helper, asserting that it creates the
    default SystemManager when managers=None.
    """
    mock_system_manager_instance = mock_system_manager.return_value
    mock_pointset_manager_instance = mock_pointset_manager.return_value
    mock_device_instance = mock_device.return_value
    mock_dispatcher_instance = mock_message_dispatcher.return_value

    device = _wire_device(
        mqtt_client=mock_mqtt_client,
        managers=None
    )

    mock_system_manager.assert_called_once()

    mock_device.assert_called_once()
    device_call_args = mock_device.call_args
    assert device_call_args.kwargs['managers'] == [mock_system_manager_instance,
                                                   mock_pointset_manager_instance]

    mock_message_dispatcher.assert_called_once_with(
        client=mock_mqtt_client,
        on_ready_callback=mock_device_instance.on_ready,
        on_disconnect_callback=mock_device_instance.on_disconnect
    )

    mock_device_instance.wire_up_dispatcher.assert_called_once_with(
        mock_dispatcher_instance
    )

    assert device == mock_device_instance


@patch('src.udmi.core.factory.MessageDispatcher')
@patch('src.udmi.core.factory.Device')
@patch('src.udmi.core.factory.SystemManager')
def test_wire_device_with_custom_managers(
    mock_system_manager,
    mock_device,
    mock_message_dispatcher,
    mock_mqtt_client,
    mock_manager
):
    """
    Tests the _wire_device helper, asserting that it uses the
    provided list of managers.
    """
    mock_device_instance = mock_device.return_value

    device = _wire_device(
        mqtt_client=mock_mqtt_client,
        managers=[mock_manager]
    )

    mock_system_manager.assert_not_called()

    mock_device.assert_called_once()
    device_call_args = mock_device.call_args
    assert device_call_args.kwargs['managers'] == [mock_manager]

    mock_message_dispatcher.assert_called_once_with(
        client=mock_mqtt_client,
        on_ready_callback=mock_device_instance.on_ready,
        on_disconnect_callback=mock_device_instance.on_disconnect
    )

    mock_device_instance.wire_up_dispatcher.assert_called_once()

    assert device == mock_device_instance
