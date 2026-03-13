"""
Unit tests for the `MessageDispatcher` class.

This module tests the `MessageDispatcher` in isolation by mocking the
underlying `AbstractMessagingClient`.
"""

import json
import logging
from unittest.mock import MagicMock

import pytest

from src.udmi.core.messaging import MessageDispatcher
from udmi.schema import State
from udmi.schema import SystemEvents


# pylint: disable=redefined-outer-name,protected-access


@pytest.fixture
def mock_client():
    """Mocks the AbstractMessagingClient."""
    client = MagicMock()
    client.set_on_message_handler = MagicMock()
    return client


@pytest.fixture
def dispatcher(mock_client):
    """Returns a MessageDispatcher instance with a mocked client."""
    return MessageDispatcher(
        client=mock_client,
        on_ready_callback=MagicMock(),
        on_disconnect_callback=MagicMock()
    )


@pytest.fixture
def handlers():
    """Provides a dictionary of mock handlers."""
    return {
        "config": MagicMock(),
        "command_reboot": MagicMock(),
        "commands_wildcard": MagicMock(),
        "pointset_wildcard": MagicMock()
    }


def test_dispatcher_registers_handlers(dispatcher, mock_client, handlers):
    """
    Test that the dispatcher correctly registers handlers and
    sets the client's on_message callback during __init__.
    """
    mock_client.set_on_message_handler.assert_called_once_with(
        dispatcher._on_message)

    dispatcher.register_handler("config", handlers["config"])
    dispatcher.register_handler("commands/reboot", handlers["command_reboot"])
    dispatcher.register_handler("commands/#", handlers["commands_wildcard"])
    dispatcher.register_handler("pointset/+/config",
                                handlers["pointset_wildcard"])

    assert "config" in dispatcher._handlers
    assert "commands/reboot" in dispatcher._handlers
    assert len(dispatcher._wildcard_handlers) == 2


def test_dispatcher_routes_exact_match(dispatcher, handlers):
    """Test that an exact channel match is routed correctly."""
    dispatcher.register_handler("config", handlers["config"])
    payload_dict = {"system": {}}

    dispatcher._on_message(device_id="test_device", channel="config",
                           payload=json.dumps(payload_dict))

    handlers["config"].assert_called_once_with("test_device",
                                               "config", payload_dict)


def test_dispatcher_routes_multi_level_wildcard(dispatcher, handlers):
    """Test that a 'commands/#' wildcard works."""
    dispatcher.register_handler("commands/#", handlers["commands_wildcard"])
    payload_dict = {"arg": 1}

    channel = "commands/my/custom/sub/command"
    dispatcher._on_message(device_id="test_device", channel=channel,
                           payload=json.dumps(payload_dict))

    handlers["commands_wildcard"].assert_called_once_with("test_device",
                                                          channel, payload_dict)


def test_dispatcher_routes_single_level_wildcard(dispatcher, handlers):
    """Test that a 'pointset/+/config' wildcard works."""
    dispatcher.register_handler("pointset/+/config",
                                handlers["pointset_wildcard"])
    payload_dict = {"set_value": 10}

    channel = "pointset/zone_1/config"
    dispatcher._on_message(device_id="test_device", channel=channel,
                           payload=json.dumps(payload_dict))

    handlers["pointset_wildcard"].assert_called_once_with("test_device",
                                                          channel, payload_dict)


def test_dispatcher_prefers_exact_over_wildcard(dispatcher, handlers):
    """Test that an exact match is preferred over a wildcard."""
    dispatcher.register_handler("commands/reboot", handlers["command_reboot"])
    dispatcher.register_handler("commands/#", handlers["commands_wildcard"])
    payload_dict = {}

    dispatcher._on_message(device_id="test_device", channel="commands/reboot",
                           payload=json.dumps(payload_dict))

    handlers["command_reboot"].assert_called_once_with(
        "test_device", "commands/reboot", payload_dict
    )
    handlers["commands_wildcard"].assert_not_called()


def test_dispatcher_handles_json_decode_error(dispatcher, handlers, caplog):
    """Test that bad JSON is caught and logged, but doesn't crash."""
    dispatcher.register_handler("config", handlers["config"])

    with caplog.at_level(logging.ERROR):
        dispatcher._on_message(device_id="test_device", channel="config",
                               payload="this is not json")

    handlers["config"].assert_not_called()
    assert "Failed to decode JSON from" in caplog.text


def test_dispatcher_handles_handler_exception(dispatcher, handlers, caplog):
    """Test that an exception in a handler is caught and logged."""
    handlers["config"].side_effect = TypeError("Something broke!")
    dispatcher.register_handler("config", handlers["config"])

    with caplog.at_level(logging.ERROR):
        dispatcher._on_message(device_id="test_device", channel="config",
                               payload="{}")

    handlers["config"].assert_called_once()
    assert "Handler exception for" in caplog.text


def test_publish_state(dispatcher, mock_client):
    """
    Verifies that publish_state sends a compact JSON payload.
    """
    state = State(version="1")
    dispatcher.publish_state(state)

    mock_client.publish.assert_called_once_with(
        "state",
        '{"timestamp": null, "version": "1", "upgraded_from": null, '
        '"system": null, "gateway": null, "discovery": null, "localnet": null, '
        '"blobset": null, "pointset": null}',
        None
    )


def test_publish_event(dispatcher, mock_client):
    """
    Verifies it accepts any DataModel (like SystemEvents)
    and publishes the compact JSON.
    """
    event = SystemEvents(version="1")
    dispatcher.publish_event("system", event)

    mock_client.publish.assert_called_once_with(
        "system",
        '{"timestamp": null, "version": "1", "upgraded_from": null, '
        '"last_config": null, "logentries": null, "event_no": null, '
        '"metrics": null}',
        None
    )


def test_lifecycle_passthrough_connect(dispatcher, mock_client):
    """Test Lifecycle Passthrough (connect)"""
    dispatcher.connect()
    mock_client.connect.assert_called_once()


def test_lifecycle_passthrough_start_loop(dispatcher, mock_client):
    """Test Lifecycle Passthrough (start_loop)"""
    dispatcher.start_loop()
    mock_client.run.assert_called_once()


def test_lifecycle_passthrough_close(dispatcher, mock_client):
    """Test Lifecycle Passthrough (close)"""
    dispatcher.close()
    mock_client.close.assert_called_once()


def test_lifecycle_passthrough_check_authentication(dispatcher, mock_client):
    """Test Lifecycle Passthrough (check_authentication)"""
    dispatcher.check_authentication()
    mock_client.check_authentication.assert_called_once()


def test_unhandled_message_logs_warning(dispatcher, handlers, caplog):
    """
    Verifies that a message to an unknown channel is logged
    and no handlers are called.
    """
    dispatcher.register_handler("config", handlers["config"])

    with caplog.at_level(logging.WARNING):
        dispatcher._on_message(device_id="test_device",
                               channel="unknown/channel", payload="{}")

    handlers["config"].assert_not_called()
    assert "No handler found for message" in caplog.text
    assert "unknown/channel" in caplog.text
