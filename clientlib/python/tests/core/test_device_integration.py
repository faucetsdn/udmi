"""
Integration tests for the core UDMI device's MQTT logic.

This module tests the MQTT-level functionality of the device instance
created by the factory, specifically focusing on the
device's internal dispatcher and default managers.

Key behaviors verified:
- The full connection sequence: `connect`, `on_connect` callback,
  topic subscriptions, and initial state publish.
- The config-receive -> state-publish acknowledgment loop.
- Correct routing of command messages.
- Robustness against connection failures, malformed JSON, and
  invalid UDMI schemas.
- The periodic authentication refresh loop.
- The `start` lifecycle event hook.
"""

import json
import logging
from unittest.mock import MagicMock
from unittest.mock import call
from unittest.mock import patch

import pytest

from src.udmi.core import create_mqtt_device_instance
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
    device = create_mqtt_device_instance(
        endpoint_config=mock_endpoint_config,
        auth_provider=mock_auth_provider
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
    # Start the device
    test_device.dispatcher.connect()
    test_device.dispatcher.start_loop()

    # 1. Verify connect() was called correctly
    mock_paho_client_instance.connect.assert_called_with("mock.host", 8883)
    mock_paho_client_instance.loop_start.assert_called_once()

    # 2. --- SIMULATE CONNECTION ---
    # Manually trigger the on_connect callback
    on_connect_callback = mock_paho_client_instance.on_connect
    on_connect_callback(mock_paho_client_instance, None, None, 0)  # rc=0

    # 3. --- VERIFY SUBSCRIPTIONS ---
    expected_subs_list = [
        call.mock_paho_client_instance.subscribe('/devices/d/config', 1),
        call.mock_paho_client_instance.subscribe('/devices/d/commands/#', 1)]
    mock_paho_client_instance.subscribe.assert_has_calls(expected_subs_list,
                                                         any_order=True)

    # 4. Verify initial state was published
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
    # --- 1. SIMULATE CONNECTION ---
    on_connect_callback = mock_paho_client_instance.on_connect
    on_connect_callback(mock_paho_client_instance, None, None, 0)

    # Reset the mock to ignore the initial state publish
    mock_paho_client_instance.publish.reset_mock()

    # --- 2. SIMULATE CONFIG MESSAGE ---
    config_topic = "/devices/d/config"
    config_payload = {
        "timestamp": "2025-10-17T12:00:00Z",
        "system": {"min_loglevel": 300}
    }

    # Get the on_message callback
    on_message_callback = mock_paho_client_instance.on_message

    # Create a mock Paho message object
    mock_msg = MagicMock()
    mock_msg.topic = config_topic
    mock_msg.payload = json.dumps(config_payload).encode('utf-8')

    # Manually trigger the on_message callback
    on_message_callback(mock_paho_client_instance, None, mock_msg)

    # --- 3. VERIFY BEHAVIOR ---
    # Verify the device updated its internal state
    assert test_device.config.system.min_loglevel == 300
    assert test_device.state.system.last_config == "2025-10-17T12:00:00Z"

    # Verify a new state message was published
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

    assert "Failed to decode JSON payload" in caplog.text
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


def test_device_handles_command(
    connected_test_device,
    mock_paho_client_instance,
    caplog
):
    """
    Test that a command is routed to the SystemManager, which logs a warning.
    """
    on_message_callback = mock_paho_client_instance.on_message
    mock_msg = MagicMock()
    mock_msg.topic = "/devices/d/commands/reboot"
    mock_msg.payload = b'{"payload": 1}'

    with caplog.at_level(logging.WARNING):
        on_message_callback(mock_paho_client_instance, None, mock_msg)

    assert "Received 'reboot' command" in caplog.text
    assert "not implemented" in caplog.text
    mock_paho_client_instance.publish.assert_not_called()


def test_device_auth_refresh_loop(
    test_device,
    mock_auth_provider,
    mock_paho_client_instance):
    """
    Test the periodic auth refresh check logic.
    """
    mock_auth_provider.get_password.reset_mock()
    mock_auth_provider.needs_refresh.return_value = True

    test_device.dispatcher.check_authentication()

    mock_auth_provider.get_password.assert_called_once()
    mock_paho_client_instance.reconnect.assert_called_once()


def test_system_manager_start_event(
    connected_test_device,
    mock_paho_client_instance
):
    """
    Test that the manager's 'start' hook is called and publishes an event.
    """
    with patch.object(SystemManager, '_metrics_loop'):
        for manager in connected_test_device.managers:
            manager.start()

    mock_paho_client_instance.publish.assert_called_once()
    publish_call = mock_paho_client_instance.publish.call_args

    topic = publish_call.args[0]
    payload = json.loads(publish_call.args[1])

    assert topic == "/devices/d/events/system"
    assert payload["logentries"][0]["message"] == "Device has started"
