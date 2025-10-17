import json
from unittest.mock import MagicMock

import pytest
from udmi.schema import EndpointConfiguration

from src.udmi.core import Device
from src.udmi.core import create_device_instance


@pytest.fixture
def mock_auth_provider():
    provider = MagicMock()
    provider.get_username.return_value = "unused"
    provider.get_password.return_value = "mock_password"
    provider.needs_refresh.return_value = False
    return provider


@pytest.fixture
def test_device(mock_paho_client_class, mock_auth_provider):
    """
    Creates a full Device instance using the factory,
    with the Paho client completely mocked.
    """
    endpoint_config = EndpointConfiguration(
        client_id="projects/p/l/r/d",
        hostname="mock.host",
        port=8883
    )

    device = create_device_instance(
        device_class=Device,
        endpoint_config=endpoint_config,
        auth_provider=mock_auth_provider
    )
    return device


def test_device_connect_and_initial_state(test_device,
    mock_paho_client_instance):
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
        ('/devices/d/config', 1),
        ('/devices/d/commands/#', 1)
    ]
    # Check that subscribe was called exactly once with this list.
    mock_paho_client_instance.subscribe.assert_called_once_with(
        expected_subs_list)

    # 4. Verify initial state was published
    assert mock_paho_client_instance.publish.call_count == 1
    publish_call = mock_paho_client_instance.publish.call_args

    topic = publish_call.args[0]
    payload = json.loads(publish_call.args[1])

    assert topic == "/devices/d/state"
    assert payload["system"]["operation"]["operational"] == True


def test_device_config_state_loop(test_device, mock_paho_client_instance):
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
