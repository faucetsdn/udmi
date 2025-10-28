import json
from unittest.mock import MagicMock

import pytest
from udmi.schema import Config
from udmi.schema import EndpointConfiguration
from udmi.schema import State
from udmi.schema import StateSystemHardware
from udmi.schema import SystemState

from src.udmi.core import create_device_with_auth_provider
from src.udmi.core.managers import BaseManager


@pytest.fixture
def mock_auth_provider():
    """Provides a mock AuthProvider."""
    provider = MagicMock()
    provider.get_username.return_value = "unused"
    provider.get_password.return_value = "mock_password"
    provider.needs_refresh.return_value = False
    return provider


@pytest.fixture
def mock_manager():
    """
    Creates a MagicMock that conforms to the BaseManager spec.
    """
    manager = MagicMock(spec=BaseManager)

    def mock_update_state_impl(state: State):
        if state.system is None:
            state.system = SystemState()
        if state.system.hardware is None:
            state.system.hardware = StateSystemHardware()

        state.system.hardware.make = "TestMake"

    manager.update_state.side_effect = mock_update_state_impl
    return manager


@pytest.fixture
def test_device(mock_paho_client_class, mock_auth_provider, mock_manager):
    """
    Creates a full instance of the Device orchestrator.
    """
    endpoint_config = EndpointConfiguration(
        client_id="projects/p/l/r/d",
        hostname="mock.host",
        port=8883
    )

    device = create_device_with_auth_provider(
        endpoint_config=endpoint_config,
        auth_provider=mock_auth_provider,
        managers=[mock_manager]
    )
    return device


@pytest.fixture
def connected_device(test_device, mock_paho_client_instance, mock_manager):
    """
    A helper fixture to get a device in the 'connected' state.
    """
    # Get the internal callback that MqttMessagingClient registered with paho
    on_connect_callback = mock_paho_client_instance.on_connect

    # Simulate a successful connection (rc=0)
    on_connect_callback(mock_paho_client_instance, None, None, 0)

    # Reset mocks after the initial connection-related publish
    mock_paho_client_instance.publish.reset_mock()
    mock_manager.update_state.reset_mock()
    return test_device


def test_manager_update_state_called(connected_device,
    mock_paho_client_instance, mock_manager):
    """
    Test that the manager's 'update_state' method is called
    by the device's internal '_publish_state()'.
    """
    connected_device._publish_state()

    mock_manager.update_state.assert_called_once()
    assert mock_paho_client_instance.publish.call_count == 1

    payload_str = mock_paho_client_instance.publish.call_args[0][1]
    payload = json.loads(payload_str)
    assert payload["system"]["hardware"]["make"] == "TestMake"


def test_manager_handle_config_called(connected_device,
    mock_paho_client_instance, mock_manager):
    """
    Test that the manager's 'handle_config' method is called
    by the device's 'handle_config()' orchestrator.
    """
    config_topic = "/devices/d/config"
    config_payload = {"timestamp": "2025-10-17T12:00:00Z", "system": {}}

    config_obj = Config.from_dict(config_payload)
    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock(
        topic=config_topic,
        payload=json.dumps(config_payload).encode('utf-8')
    )
    on_message_callback(mock_paho_client_instance, None, mock_msg)

    mock_manager.handle_config.assert_called_once_with(config_obj)
    assert mock_paho_client_instance.publish.call_count == 1


def test_manager_handle_command_called(connected_device,
    mock_paho_client_instance, mock_manager):
    """
    Test that the manager's 'handle_command' method is called
    by the device's 'handle_command()' orchestrator.
    """
    command_topic = "/devices/d/commands/reboot"
    command_payload = {"delay_sec": 10}
    expected_command_name = "reboot"
    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock(
        topic=command_topic,
        payload=json.dumps(command_payload).encode('utf-8')
    )
    on_message_callback(mock_paho_client_instance, None, mock_msg)

    mock_manager.handle_command.assert_called_once_with(
        expected_command_name,
        command_payload
    )
    mock_paho_client_instance.publish.assert_not_called()
