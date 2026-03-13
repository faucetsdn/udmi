"""
Tests for the Device-Manager integration within the UDMI core.
"""

import json
import logging
from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from src.udmi.core.managers import BaseManager
from udmi.core import create_device
from udmi.schema import Config
from udmi.schema import State
from udmi.schema import StateSystemHardware
from udmi.schema import SystemState


# pylint: disable=redefined-outer-name,protected-access,unused-argument


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
def test_device(
    mock_paho_client_class,
    mock_auth_provider,
    mock_manager,
    mock_endpoint_config
):
    """
    Creates a full instance of the Device orchestrator.
    """
    device = create_device(
        endpoint_config=mock_endpoint_config,
        managers=[mock_manager]
    )
    return device


@pytest.fixture
def connected_device(
    test_device,
    mock_paho_client_instance,
    mock_manager
):
    """
    A helper fixture to get a device in the 'connected' state.
    """
    on_connect_callback = mock_paho_client_instance.on_connect

    on_connect_callback(mock_paho_client_instance, None, None, 0)

    mock_paho_client_instance.publish.reset_mock()
    mock_manager.update_state.reset_mock()
    return test_device


def test_manager_update_state_called(
    connected_device,
    mock_paho_client_instance,
    mock_manager
):
    """
    Test that the manager's 'update_state' method is called
    by the device's internal '_publish_state()'.
    """
    with patch("udmi.core.device.STATE_THROTTLE_SEC", 0):
        connected_device._publish_state()

        mock_manager.update_state.assert_called_once()
        assert mock_paho_client_instance.publish.call_count == 1

        payload_str = mock_paho_client_instance.publish.call_args[0][1]
        payload = json.loads(payload_str)
        assert payload["system"]["hardware"]["make"] == "TestMake"


def test_manager_handle_config_called(
    connected_device,
    mock_paho_client_instance,
    mock_manager
):
    """
    Test that the manager's 'handle_config' method is called
    by the device's 'handle_config()' orchestrator.
    """
    with patch("udmi.core.device.STATE_THROTTLE_SEC", 0):
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


def test_manager_handle_command_called(
    connected_device,
    mock_paho_client_instance,
    mock_manager
):
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


def test_manager_handle_config_exception_is_caught(
    connected_device,
    mock_paho_client_instance,
    mock_manager,
    caplog
):
    """
    Verifies that if a manager raises an exception during config handling,
    it is caught, logged, and the device continues.
    """
    mock_manager.handle_config.side_effect = ValueError("Config Boom!")

    with patch("udmi.core.device.STATE_THROTTLE_SEC", 0):
        config_topic = "/devices/d/config"
        config_payload = {"timestamp": "2025-01-01T00:00:00Z", "system": {}}

        on_message_callback = mock_paho_client_instance.on_message
        mock_msg = MagicMock(
            topic=config_topic,
            payload=json.dumps(config_payload).encode('utf-8')
        )

        with caplog.at_level(logging.ERROR):
            on_message_callback(mock_paho_client_instance, None, mock_msg)

        mock_manager.handle_config.assert_called_once()

        assert "Error in BaseManager.handle_config: Config Boom!" in caplog.text


def test_manager_handle_command_exception_is_caught(
    connected_device,
    mock_paho_client_instance,
    mock_manager,
    caplog
):
    """
    Verifies that if a manager raises an exception during command handling,
    it is caught and logged.
    """
    mock_manager.handle_command.side_effect = KeyError("Command Missing Key")

    command_topic = "/devices/d/commands/reset"
    mock_msg = MagicMock(topic=command_topic, payload=b'{}')

    with caplog.at_level(logging.ERROR):
        mock_paho_client_instance.on_message(mock_paho_client_instance, None,
                                             mock_msg)

    assert "Error in BaseManager.handle_command: 'Command Missing Key'" in caplog.text


def test_manager_update_state_exception_is_caught(
    connected_device,
    mock_manager,
    caplog
):
    """
    Verifies that if a manager fails to update state, the error is logged
    and the device attempts to continue publishing (or at least doesn't crash).
    """
    mock_manager.update_state.side_effect = ValueError("State Build Fail")

    with patch("udmi.core.device.STATE_THROTTLE_SEC", 0):
        with caplog.at_level(logging.ERROR):
            connected_device._publish_state()

    assert "Error in BaseManager.update_state: State Build Fail" in caplog.text


def test_multiple_managers_isolation(
    mock_endpoint_config,
    mock_paho_client_class
):
    """
    Verifies that with multiple managers, an error in one does not stop
    the others from receiving the update.
    """
    manager_good = MagicMock(spec=BaseManager)
    manager_bad = MagicMock(spec=BaseManager)
    manager_bad.handle_config.side_effect = ValueError("Fail")

    device = create_device(
        endpoint_config=mock_endpoint_config,
        managers=[manager_bad, manager_good]
    )

    config_payload = {"timestamp": "2025-01-01T00:00:00Z"}

    target_id = device.device_id

    device.handle_config(target_id, "config", config_payload)

    manager_bad.handle_config.assert_called_once()
    manager_good.handle_config.assert_called_once()


def test_lifecycle_stop_propagates(
    connected_device,
    mock_manager
):
    """
    Verifies that device.stop() calls stop() on all managers.
    """
    connected_device.stop()
    mock_manager.stop.assert_called_once()
