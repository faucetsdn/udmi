"""
Unit tests for the `SystemManager` class.

This module tests the `SystemManager` in isolation by mocking the
`AbstractMessageDispatcher` and other dependencies.

Key behaviors verified:
- Start Hook: `start()` correctly publishes a `SystemEvents` message
  with the "Device has started" log entry.
- Config Handling: `handle_config()` correctly captures and stores the
  `timestamp` from the received `Config` object.
- State Update: `update_state()` correctly populates the `state.system`
  field with default hardware, software, and operation sub-objects.
- Command Handling: `handle_command()` logs a warning for
  unimplemented commands (like 'reboot'), as expected.
"""

import logging
from unittest.mock import MagicMock

import pytest
from udmi.schema import Config
from udmi.schema import State
from udmi.schema import SystemEvents

from src.udmi.core.managers.system_manager import SystemManager
from src.udmi.core.messaging import AbstractMessageDispatcher


# pylint: disable=redefined-outer-name,protected-access


@pytest.fixture
def mock_dispatcher():
    """Provides a mock dispatcher object conforming to the spec."""
    return MagicMock(spec=AbstractMessageDispatcher)


@pytest.fixture
def system_manager():
    """Provides a default SystemManager instance."""
    return SystemManager()


def test_start_publishes_startup_event(system_manager, mock_dispatcher):
    """
    test_start_publishes_startup_event
    Verify mock_dispatcher.publish_event was called with the channel "system"
    and a SystemEvents object.
    """
    system_manager.set_device_context(device=None, dispatcher=mock_dispatcher)
    system_manager.start()
    mock_dispatcher.publish_event.assert_called_once()

    call_args = mock_dispatcher.publish_event.call_args
    assert call_args.args[0] == "events/system"
    assert isinstance(call_args.args[1], SystemEvents)

    published_event: SystemEvents = call_args.args[1]
    assert len(published_event.logentries) == 1
    assert published_event.logentries[0].message == "Device has started"


def test_handle_config_captures_timestamp(system_manager):
    """
    test_handle_config_captures_timestamp
    Check that manager._last_config_ts equals the timestamp.
    """
    test_timestamp = "2025-10-28T14:30:00Z"
    config_obj = Config(timestamp=test_timestamp)

    system_manager.handle_config(config_obj)

    assert system_manager._last_config_ts == test_timestamp


def test_update_state_populates_all_fields(system_manager):
    """
    test_update_state_populates_all_fields
    Verify state.system.hardware, state.system.software, and
    state.system.operation are all populated.
    """
    state = State()
    system_manager.update_state(state)

    assert state.system is not None
    assert state.system.hardware == {"make": "pyudmi", "model": "device-v1"}
    assert state.system.software == {"firmware": "1.0.0"}
    assert state.system.operation is not None
    assert state.system.operation.operational is True
    assert state.system.last_config is None


def test_handle_command_logs_warning(system_manager, caplog):
    """
    test_handle_command_logs_warning
    Verify a warning was logged containing "not implemented."
    """
    with caplog.at_level(logging.WARNING):
        system_manager.handle_command("reboot", {})

    assert "Received 'reboot' command" in caplog.text
    assert "not implemented" in caplog.text
