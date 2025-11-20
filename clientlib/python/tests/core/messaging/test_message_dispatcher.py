"""
Unit tests for the `MessageDispatcher` class.

This module tests the `MessageDispatcher` in isolation by mocking the
underlying `AbstractMessagingClient`.

Key behaviors verified:
- Handler Registration: Ensures `register_handler` correctly separates
  exact-match handlers from wildcard (MQTT-style '+' and '#') handlers.
- Message Routing Logic:
    - Verifies routing to exact-match handlers.
    - Verifies routing to single-level (`+`) wildcard handlers.
    - Verifies routing to multi-level (`#`) wildcard handlers.
    - Confirms that an exact-match handler is always preferred over a
      competing wildcard match for the same topic.
- Robustness:
    - Ensures that a malformed JSON payload is caught, logged,
      and does not crash the dispatcher's `_on_message` callback.
    - Ensures that an exception raised from *within* a message handler
      is caught, logged, and does not crash the dispatcher.
    - Ensures a message for an unhandled topic is safely logged as a warning.
- Outbound Publishing:
    - Verifies `publish_state` correctly serializes the `State` object
      to a compact JSON string and publishes to the 'state' channel.
    - Verifies `publish_event` serializes any `DataModel` (e.g.,
      `SystemEvents`) to compact JSON and publishes to the specified channel.
- Lifecycle Passthrough: Confirms that core lifecycle methods
  (`connect`, `start_loop`, `close`, `check_authentication`) are
  passed through to the underlying client.
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
    # Verify the dispatcher set its callback on the client
    mock_client.set_on_message_handler.assert_called_once_with(
        dispatcher._on_message)

    # Register handlers
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

    dispatcher._on_message(channel="config", payload=json.dumps(payload_dict))

    handlers["config"].assert_called_once_with("config", payload_dict)


def test_dispatcher_routes_multi_level_wildcard(dispatcher, handlers):
    """Test that a 'commands/#' wildcard works."""
    dispatcher.register_handler("commands/#", handlers["commands_wildcard"])
    payload_dict = {"arg": 1}

    channel = "commands/my/custom/sub/command"
    dispatcher._on_message(channel=channel, payload=json.dumps(payload_dict))

    handlers["commands_wildcard"].assert_called_once_with(channel, payload_dict)


def test_dispatcher_routes_single_level_wildcard(dispatcher, handlers):
    """Test that a 'pointset/+/config' wildcard works."""
    dispatcher.register_handler("pointset/+/config",
                                handlers["pointset_wildcard"])
    payload_dict = {"set_value": 10}

    channel = "pointset/zone_1/config"
    dispatcher._on_message(channel=channel, payload=json.dumps(payload_dict))

    handlers["pointset_wildcard"].assert_called_once_with(channel, payload_dict)


def test_dispatcher_prefers_exact_over_wildcard(dispatcher, handlers):
    """Test that an exact match is preferred over a wildcard."""
    dispatcher.register_handler("commands/reboot", handlers["command_reboot"])
    dispatcher.register_handler("commands/#", handlers["commands_wildcard"])
    payload_dict = {}

    dispatcher._on_message(channel="commands/reboot",
                           payload=json.dumps(payload_dict))

    # The exact handler should be called
    handlers["command_reboot"].assert_called_once_with("commands/reboot",
                                                       payload_dict)
    # The wildcard handler should NOT be called
    handlers["commands_wildcard"].assert_not_called()


def test_dispatcher_handles_json_decode_error(dispatcher, handlers, caplog):
    """Test that bad JSON is caught and logged, but doesn't crash."""
    dispatcher.register_handler("config", handlers["config"])

    # No exception should be raised
    with caplog.at_level(logging.ERROR):
        dispatcher._on_message(channel="config", payload="this is not json")

    # The handler should not be called
    handlers["config"].assert_not_called()
    # We should have logged the error
    assert "Failed to decode JSON payload" in caplog.text


def test_dispatcher_handles_handler_exception(dispatcher, handlers, caplog):
    """Test that an exception in a handler is caught and logged."""
    # Make the handler raise an exception when called
    handlers["config"].side_effect = TypeError("Something broke!")
    dispatcher.register_handler("config", handlers["config"])

    # No exception should be raised from the dispatcher itself
    with caplog.at_level(logging.ERROR):
        dispatcher._on_message(channel="config", payload="{}")

    # The handler should have been called
    handlers["config"].assert_called_once()
    # We should have logged the error
    assert "Handler for channel config failed" in caplog.text


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
        '"blobset": null, "pointset": null}'
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
        '"metrics": null}'
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
        dispatcher._on_message(channel="unknown/channel", payload="{}")

    handlers["config"].assert_not_called()
    assert "No handler found for message" in caplog.text
    assert "unknown/channel" in caplog.text
