import pytest
import json
from unittest.mock import MagicMock

from src.udmi.core import Device, create_device_instance
from udmi.schema import EndpointConfiguration, Config


# --- A Test Device Subclass ---
# We create a test-specific subclass that mocks its own hooks
# so we can assert they were called.

class MockableDevice(Device):
    def __init__(self, dispatcher):
        super().__init__(dispatcher)
        self._apply_config = MagicMock(wraps=self._apply_config)
        self._execute_command = MagicMock(wraps=self._execute_command)
        self._update_state_before_publish = MagicMock(
            wraps=self._update_state_before_publish)

    def _apply_config(self, config: Config) -> None:
        """Mockable hook."""
        super()._apply_config(config)

    def _execute_command(self, channel: str, payload: dict) -> None:
        """Mockable hook."""
        super()._execute_command(channel, payload)

    def _update_state_before_publish(self) -> None:
        """Mockable hook to inject test data."""
        super()._update_state_before_publish()
        self.state.system.hardware.make = "TestMake"


@pytest.fixture
def mock_auth_provider():
    provider = MagicMock()
    provider.get_username.return_value = "unused"
    provider.get_password.return_value = "mock_password"
    provider.needs_refresh.return_value = False
    return provider


@pytest.fixture
def test_device_subclass(mock_paho_client_class, mock_auth_provider):
    """
    Creates a full instance of our MockableDevice subclass
    using the factory and mocked Paho.
    """
    endpoint_config = EndpointConfiguration(
        client_id="projects/p/l/r/d",
        hostname="mock.host",
        port=8883
    )

    device = create_device_instance(
        device_class=MockableDevice,
        endpoint_config=endpoint_config,
        auth_provider=mock_auth_provider
    )
    return device


@pytest.fixture
def connected_device(test_device_subclass, mock_paho_client_instance):
    """A helper fixture to get a device in the 'connected' state."""
    # Simulate connection
    on_connect_callback = mock_paho_client_instance.on_connect
    on_connect_callback(mock_paho_client_instance, None, None, 0)

    # Reset mocks to ignore connection-time calls
    mock_paho_client_instance.publish.reset_mock()
    test_device_subclass._update_state_before_publish.reset_mock()
    return test_device_subclass


def test_update_state_hook_called(connected_device, mock_paho_client_instance):
    """
    Test that _update_state_before_publish is called by publish_state().
    """
    # Manually call publish_state
    connected_device.publish_state()

    # 1. Verify the hook was called
    connected_device._update_state_before_publish.assert_called_once()

    # 2. Verify the data injected by the hook is in the payload
    assert mock_paho_client_instance.publish.call_count == 1
    payload = json.loads(mock_paho_client_instance.publish.call_args[0][1])
    assert payload["system"]["hardware"]["make"] == "TestMake"


def test_apply_config_hook_called(connected_device, mock_paho_client_instance):
    """
    Test that _apply_config is called by handle_config().
    """
    config_topic = "/devices/d/config"
    config_payload = {"timestamp": "2025-10-17T12:00:00Z", "system": {}}
    config_obj = Config.from_dict(config_payload)

    # Get and call the on_message callback
    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock(
        topic=config_topic,
        payload=json.dumps(config_payload).encode('utf-8')
    )
    on_message_callback(mock_paho_client_instance, None, mock_msg)

    # 1. Verify the hook was called with the correct Config object
    connected_device._apply_config.assert_called_once_with(config_obj)

    # 2. Verify state was published by handle_config
    assert mock_paho_client_instance.publish.call_count == 1


def test_execute_command_hook_called(connected_device,
    mock_paho_client_instance):
    """
    Test that _execute_command is called by handle_command().
    """
    command_topic = "/devices/d/commands/reboot"
    command_payload = {"delay_sec": 10}
    expected_channel = "commands/reboot"

    # Get and call the on_message callback
    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock(
        topic=command_topic,
        payload=json.dumps(command_payload).encode('utf-8')
    )
    on_message_callback(mock_paho_client_instance, None, mock_msg)

    # 1. Verify the hook was called with the correct channel and payload
    connected_device._execute_command.assert_called_once_with(
        expected_channel,
        command_payload
    )

    # 2. Verify state was not published
    mock_paho_client_instance.publish.assert_not_called()
