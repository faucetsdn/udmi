"""
Unit tests for the core device factory.
"""
from unittest.mock import ANY
from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from src.udmi.core.factory import _wire_device
from udmi.core import create_device
from udmi.core.factory import ClientConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import EndpointConfiguration
from udmi.schema import Metadata


# pylint: disable=redefined-outer-name,too-many-arguments,too-many-positional-arguments


@pytest.fixture
def mock_mqtt_client():
    """A mock MqttMessagingClient instance."""
    return MagicMock(name="mock_mqtt_client")


@pytest.fixture
def mock_manager():
    """A mock BaseManager instance."""
    return MagicMock(name="mock_manager")


@pytest.fixture
def mock_endpoint_config():
    """A simple mock endpoint config."""
    return EndpointConfiguration(
        client_id="projects/p/l/r/d",
        hostname="mock.host",
        port=8883
    )


@patch('udmi.core.factory._wire_device')
@patch('udmi.core.messaging.MqttMessagingClient')
@patch('udmi.core.messaging.JwtAuthProvider')
@patch('udmi.core.messaging.initialize_credential_manager')
@patch('udmi.core.factory.initialize_credential_manager')
def test_create_device_with_jwt(
    mock_factory_init_creds,
    mock_messaging_init_creds,
    mock_jwt_auth_provider,
    mock_mqtt_messaging_client,
    mock_wire_device,
    mock_jwt_endpoint_config
):
    """
    Asserts that the factory creates and passes the JwtAuthProvider.
    """
    mock_creds_manager = mock_factory_init_creds.return_value

    mock_messaging_init_creds.return_value = mock_creds_manager

    create_device(
        endpoint_config=mock_jwt_endpoint_config,
        key_file="test-key.pem"
    )

    mock_factory_init_creds.assert_called_with(
        key_file="test-key.pem",
        algorithm="RS256",
        tls_config=ANY,
        is_mtls=False
    )

    mock_jwt_auth_provider.assert_called_once_with(
        project_id="test-project",
        signer=mock_creds_manager,
        algorithm=mock_creds_manager.get_algorithm_name()
    )

    mock_mqtt_messaging_client.assert_called_once()

    mock_wire_device.assert_called_once()
    call_kwargs = mock_wire_device.call_args.kwargs
    assert call_kwargs['credential_manager'] == mock_creds_manager


@patch('udmi.core.factory._wire_device')
@patch('udmi.core.messaging.MqttMessagingClient')
@patch('udmi.core.messaging.BasicAuthProvider')
def test_create_device_with_basic_auth(
    mock_basic_auth_provider,
    mock_mqtt_messaging_client,
    mock_wire_device,
    mock_basic_auth_endpoint_config
):
    """
    Asserts that the factory creates and passes the BasicAuthProvider.
    """
    mock_provider_instance = mock_basic_auth_provider.return_value
    mock_client_instance = mock_mqtt_messaging_client.return_value

    create_device(
        endpoint_config=mock_basic_auth_endpoint_config,
        managers=None,
        client_config=ClientConfig(
            tls_config=TlsConfig(ca_certs="test-ca.pem")
        )
    )

    mock_basic_auth_provider.assert_called_once_with(
        username="user",
        password="pass"
    )

    mock_mqtt_messaging_client.assert_called_once()
    args, kwargs = mock_mqtt_messaging_client.call_args
    assert args[0] == mock_basic_auth_endpoint_config
    assert args[1] == mock_provider_instance
    assert args[2].ca_certs == "test-ca.pem"

    mock_wire_device.assert_called_once()


@patch('src.udmi.core.factory.MessageDispatcher')
@patch('src.udmi.core.factory.Device')
@patch('src.udmi.core.factory.PointsetManager')
@patch('src.udmi.core.factory.SystemManager')
def test_wire_device_with_defaults(
    mock_system_manager,
    mock_pointset_manager,
    mock_device,
    mock_message_dispatcher,
    mock_mqtt_client,
    mock_endpoint_config
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
        managers=None,
        endpoint_config=mock_endpoint_config
    )

    mock_system_manager.assert_called_once()

    mock_device.assert_called_once()
    device_call_args = mock_device.call_args

    managers_arg = device_call_args.kwargs['managers']
    assert mock_system_manager_instance in managers_arg
    assert mock_pointset_manager_instance in managers_arg
    assert device_call_args.kwargs['endpoint_config'] == mock_endpoint_config

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
    mock_manager,
    mock_endpoint_config
):
    """
    Tests the _wire_device helper, asserting that it uses the
    provided list of managers.
    """
    mock_device_instance = mock_device.return_value

    device = _wire_device(
        mqtt_client=mock_mqtt_client,
        managers=[mock_manager],
        endpoint_config=mock_endpoint_config
    )

    mock_system_manager.assert_not_called()

    mock_device.assert_called_once()
    device_call_args = mock_device.call_args
    assert device_call_args.kwargs['managers'] == [mock_manager]
    assert device_call_args.kwargs['endpoint_config'] == mock_endpoint_config

    mock_message_dispatcher.assert_called_once_with(
        client=mock_mqtt_client,
        on_ready_callback=mock_device_instance.on_ready,
        on_disconnect_callback=mock_device_instance.on_disconnect
    )

    mock_device_instance.wire_up_dispatcher.assert_called_once()

    assert device == mock_device_instance


@patch('udmi.core.factory._wire_device')
@patch('udmi.core.messaging.MqttMessagingClient')
def test_create_device_passes_optional_dependencies(
    mock_mqtt_client,
    mock_wire_device,
    mock_basic_auth_endpoint_config
):
    """
    Verifies that optional arguments (persistence_backend, initial_model)
    are correctly passed through the factory to the wiring function.
    """
    mock_backend = MagicMock()
    mock_model = Metadata()

    create_device(
        endpoint_config=mock_basic_auth_endpoint_config,
        persistence_backend=mock_backend,
        initial_model=mock_model
    )

    mock_wire_device.assert_called_once()
    call_kwargs = mock_wire_device.call_args.kwargs

    assert call_kwargs['persistence_backend'] == mock_backend
    assert call_kwargs['initial_model'] == mock_model
