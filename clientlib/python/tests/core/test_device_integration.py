"""
Integration tests for the core UDMI device's MQTT logic.
"""

import json
import logging
from unittest.mock import MagicMock
from unittest.mock import call
from unittest.mock import patch

import pytest

from src.udmi.core import create_device
from tests.conftest import _system_lifecycle
from udmi.core.managers import BaseManager
from udmi.core.managers import SystemManager


# pylint: disable=redefined-outer-name,protected-access,unused-argument


@pytest.fixture
def test_device(
    mock_paho_client_class,
    mock_auth_provider,
    mock_endpoint_config
):
    """
    Creates a full Device instance using the factory,
    with the Paho client completely mocked.
    """
    mock_paho_client_class.mock_instance.is_connected.return_value = False

    device = create_device(
        endpoint_config=mock_endpoint_config,
    )
    return device


@pytest.fixture
def connected_test_device(
    test_device,
    mock_paho_client_instance
):
    """
    A helper fixture to get a device in the 'connected' state
    and reset mocks, so we only test post-connection events.
    """
    mock_paho_client_instance.is_connected.return_value = True

    on_connect_callback = mock_paho_client_instance.on_connect
    on_connect_callback(mock_paho_client_instance, None, None, 0)

    mock_paho_client_instance.publish.reset_mock()
    return test_device


def test_device_connect_and_initial_state(
    test_device,
    mock_paho_client_instance
):
    """
    Test the full connection and initial state publish sequence.
    """
    test_device.dispatcher.connect()
    test_device.dispatcher.start_loop()

    mock_paho_client_instance.connect_async.assert_called_with("mock.host",
                                                               8883)
    mock_paho_client_instance.loop_start.assert_called_once()

    on_connect_callback = mock_paho_client_instance.on_connect
    on_connect_callback(mock_paho_client_instance, None, None, 0)

    expected_subs_list = [
        call.mock_paho_client_instance.subscribe('/devices/d/config', qos=1),
        call.mock_paho_client_instance.subscribe('/devices/d/commands/#',
                                                 qos=1)]

    mock_paho_client_instance.subscribe.assert_has_calls(expected_subs_list,
                                                         any_order=True)

    assert mock_paho_client_instance.publish.call_count == 1
    publish_call = mock_paho_client_instance.publish.call_args

    topic = publish_call.args[0]
    payload = json.loads(publish_call.args[1])

    assert topic == "/devices/d/state"
    assert payload["system"]["operation"]["operational"] is True


def test_device_config_state_loop(
    test_device,
    mock_paho_client_instance
):
    """
    Test the full config -> state acknowledgement loop.
    """
    with patch("udmi.core.device.STATE_THROTTLE_SEC", 0):
        on_connect_callback = mock_paho_client_instance.on_connect
        on_connect_callback(mock_paho_client_instance, None, None, 0)

        mock_paho_client_instance.publish.reset_mock()

        config_topic = "/devices/d/config"
        config_payload = {
            "timestamp": "2025-10-17T12:00:00Z",
            "system": {"min_loglevel": 300}
        }

        on_message_callback = mock_paho_client_instance.on_message

        mock_msg = MagicMock()
        mock_msg.topic = config_topic
        mock_msg.payload = json.dumps(config_payload).encode('utf-8')

        on_message_callback(mock_paho_client_instance, None, mock_msg)

        assert test_device.config.system.min_loglevel == 300
        assert test_device.state.system.last_config == "2025-10-17T12:00:00Z"

        assert mock_paho_client_instance.publish.call_count == 1
        publish_call = mock_paho_client_instance.publish.call_args

        topic = publish_call.args[0]
        payload = json.loads(publish_call.args[1])

        assert topic == "/devices/d/state"
        assert payload["system"]["last_config"] == "2025-10-17T12:00:00Z"


def test_device_connection_failed(
    test_device,
    mock_paho_client_instance
):
    """
    Test what happens if on_connect reports an error.
    """
    client_instance = test_device.dispatcher.client
    mock_disconnect_callback = MagicMock()
    client_instance._callbacks.on_disconnect = mock_disconnect_callback

    on_connect_callback = mock_paho_client_instance.on_connect
    on_connect_callback(mock_paho_client_instance, None, None, 5)

    mock_paho_client_instance.subscribe.assert_not_called()
    mock_paho_client_instance.publish.assert_not_called()

    on_disconnect_callback = mock_paho_client_instance.on_disconnect
    on_disconnect_callback(mock_paho_client_instance, None, 5)

    mock_disconnect_callback.assert_called_with(5)


def test_device_receives_bad_json(
    connected_test_device,
    mock_paho_client_instance,
    caplog
):
    """
    Test that malformed JSON is caught, logged, and doesn't crash.
    """
    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock()
    mock_msg.topic = "/devices/d/config"
    mock_msg.payload = b"this is not json"

    with caplog.at_level(logging.ERROR):
        on_message_callback(mock_paho_client_instance, None, mock_msg)

    assert "Failed to decode JSON" in caplog.text
    mock_paho_client_instance.publish.assert_not_called()


def test_device_receives_bad_config_schema(
    connected_test_device,
    mock_paho_client_instance,
    caplog
):
    """
    Test that valid JSON with an invalid schema is caught and logged.
    """
    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock()
    mock_msg.topic = "/devices/d/config"
    mock_msg.payload = b'{"system": {"min_loglevel": "not_an_int"}}'

    with caplog.at_level(logging.ERROR):
        on_message_callback(mock_paho_client_instance, None, mock_msg)

    assert "Failed to parse config message" in caplog.text
    mock_paho_client_instance.publish.assert_not_called()


@patch("sys.exit")
def test_device_handles_command(
    mock_exit,
    connected_test_device,
    mock_paho_client_instance,
    caplog
):
    """
    Test that a command is routed to the SystemManager, which executes the
    lifecycle command.
    """
    sys_manager = connected_test_device.get_manager(SystemManager)
    sys_manager.register_command_handler(
        "reboot",
        lambda p: _system_lifecycle(192)
    )

    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock()
    mock_msg.topic = "/devices/d/commands/reboot"
    mock_msg.payload = b'{"payload": 1}'

    with caplog.at_level(logging.WARNING):
        on_message_callback(mock_paho_client_instance, None, mock_msg)

    mock_exit.assert_called_once_with(192)

    mock_paho_client_instance.publish.assert_not_called()


def test_device_auth_refresh_loop(
    test_device,
    mock_auth_provider,
    mock_paho_client_instance):
    """
    Test the periodic auth refresh check logic.
    """
    test_device.dispatcher.client._auth_provider = mock_auth_provider

    mock_auth_provider.get_password.reset_mock()
    mock_auth_provider.needs_refresh.return_value = True

    mock_paho_client_instance.is_connected.return_value = True

    test_device.dispatcher.check_authentication()

    mock_auth_provider.get_password.assert_called_once()
    mock_paho_client_instance.disconnect.assert_called_once()


def test_system_manager_start_event(
    connected_test_device,
    mock_paho_client_instance
):
    """
    Test that the manager's 'start' hook is called and publishes an event.
    """
    with patch.object(BaseManager, 'start_periodic_task'):
        for manager in connected_test_device.managers:
            manager.start()

    mock_paho_client_instance.publish.assert_called()
    publish_call = mock_paho_client_instance.publish.call_args
    topic = publish_call.args[0]
    payload = json.loads(publish_call.args[1])

    if topic != "/devices/d/events/system":
        found = False
        for call_args in mock_paho_client_instance.publish.call_args_list:
            if call_args.args[0] == "/devices/d/events/system":
                payload = json.loads(call_args.args[1])
                found = True
                break
        assert found

    assert payload["logentries"][0]["message"] == "Device has started"
