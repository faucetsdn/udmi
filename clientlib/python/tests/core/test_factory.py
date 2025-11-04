from unittest.mock import ANY
from unittest.mock import MagicMock
from unittest.mock import patch

import pytest
from udmi.schema import EndpointConfiguration

from src.udmi.core.factory import _wire_device
from src.udmi.core.factory import create_device_with_basic_auth
from src.udmi.core.factory import create_device_with_jwt


@pytest.fixture
def mock_endpoint_config():
    """A mock EndpointConfiguration object."""
    return EndpointConfiguration(
        client_id="projects/p/l/r/d",
        hostname="mock.host",
        port=8883
    )


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
def test_create_device_with_jwt(mock_JwtAuthProvider, mock_MqttMessagingClient,
    mock_wire_device, mock_endpoint_config):
    """
    Asserts that the factory creates and passes the JwtAuthProvider.
    """
    mock_provider_instance = mock_JwtAuthProvider.return_value
    mock_client_instance = mock_MqttMessagingClient.return_value

    create_device_with_jwt(
        endpoint_config=mock_endpoint_config,
        project_id="test-project",
        key_file="test-key.pem",
        algorithm="RS256"
    )

    mock_JwtAuthProvider.assert_called_once_with(
        project_id="test-project",
        private_key_file="test-key.pem",
        algorithm="RS256",
        token_lifetime_minutes=ANY  # We trust the default value
    )

    mock_MqttMessagingClient.assert_called_once_with(
        endpoint_config=mock_endpoint_config,
        auth_provider=mock_provider_instance,
        ca_certs=None,
        cert_file=None,
        key_file=None,
        enable_tls=None,
        min_reconnect_delay_sec=1,
        max_reconnect_delay_sec=60
    )

    mock_wire_device.assert_called_once_with(
        mqtt_client=mock_client_instance,
        managers=None
    )


@patch('src.udmi.core.factory._wire_device')
@patch('src.udmi.core.factory.MqttMessagingClient')
@patch('src.udmi.core.factory.BasicAuthProvider')
def test_create_device_with_basic_auth(mock_BasicAuthProvider,
    mock_MqttMessagingClient,
    mock_wire_device,
    mock_endpoint_config):
    """
    Asserts that the factory creates and passes the BasicAuthProvider
    and does NOT pass mTLS certs.
    """
    mock_provider_instance = mock_BasicAuthProvider.return_value
    mock_client_instance = mock_MqttMessagingClient.return_value

    create_device_with_basic_auth(
        endpoint_config=mock_endpoint_config,
        username="user",
        password="pass",
        managers=None,
        ca_certs="test-ca.pem"
    )

    mock_BasicAuthProvider.assert_called_once_with(
        username="user",
        password="pass"
    )

    mock_MqttMessagingClient.assert_called_once_with(
        endpoint_config=mock_endpoint_config,
        auth_provider=mock_provider_instance,
        ca_certs="test-ca.pem",
        cert_file=None,
        key_file=None,
        enable_tls=None,
        min_reconnect_delay_sec=1,
        max_reconnect_delay_sec=60
    )

    mock_wire_device.assert_called_once_with(
        mqtt_client=mock_client_instance,
        managers=None
    )


@patch('src.udmi.core.factory.MessageDispatcher')
@patch('src.udmi.core.factory.Device')
@patch('src.udmi.core.factory.SystemManager')
def test_wire_device_with_defaults(mock_SystemManager, mock_Device,
    mock_MessageDispatcher, mock_mqtt_client):
    """
    Tests the _wire_device helper, asserting that it creates the
    default SystemManager when managers=None.
    """
    mock_system_manager_instance = mock_SystemManager.return_value
    mock_device_instance = mock_Device.return_value
    mock_dispatcher_instance = mock_MessageDispatcher.return_value

    device = _wire_device(
        mqtt_client=mock_mqtt_client,
        managers=None
    )

    mock_SystemManager.assert_called_once_with()

    mock_Device.assert_called_once()
    device_call_args = mock_Device.call_args
    assert device_call_args.kwargs['managers'] == [mock_system_manager_instance]

    mock_MessageDispatcher.assert_called_once_with(
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
def test_wire_device_with_custom_managers(mock_SystemManager, mock_Device,
    mock_MessageDispatcher,
    mock_mqtt_client, mock_manager):
    """
    Tests the _wire_device helper, asserting that it uses the
    provided list of managers.
    """
    mock_device_instance = mock_Device.return_value

    device = _wire_device(
        mqtt_client=mock_mqtt_client,
        managers=[mock_manager]
    )

    mock_SystemManager.assert_not_called()

    mock_Device.assert_called_once()
    device_call_args = mock_Device.call_args
    assert device_call_args.kwargs['managers'] == [mock_manager]

    mock_MessageDispatcher.assert_called_once_with(
        client=mock_mqtt_client,
        on_ready_callback=mock_device_instance.on_ready,
        on_disconnect_callback=mock_device_instance.on_disconnect
    )

    mock_device_instance.wire_up_dispatcher.assert_called_once()

    assert device == mock_device_instance
