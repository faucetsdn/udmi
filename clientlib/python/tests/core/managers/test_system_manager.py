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
- Persistence: Verifies restart counts are loaded, incremented, and saved.
"""

import logging
from unittest.mock import MagicMock
from unittest.mock import patch

import pytest
from udmi.schema import Config
from udmi.schema import State
from udmi.schema import StateSystemHardware
from udmi.schema import SystemConfig
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
    assert state.system.hardware == StateSystemHardware(make='pyudmi',
                                                        model='device-v1',
                                                        sku=None, rev=None)
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


def test_handle_config_updates_metrics_rate(system_manager):
    """
    test_handle_config_updates_metrics_rate
    Verify that providing a system config with metrics_rate_sec updates
    the internal rate.
    """
    assert system_manager._metrics_rate_sec == 60

    new_rate = 120
    config_obj = Config(system=SystemConfig(metrics_rate_sec=new_rate))

    system_manager.handle_config(config_obj)

    assert system_manager._metrics_rate_sec == new_rate


@patch("src.udmi.core.managers.system_manager.psutil")
def test_publish_metrics_success(mock_psutil, system_manager, mock_dispatcher):
    """
    test_publish_metrics_success
    Mocks psutil to return known memory values, then verifies that
    publish_event is called with the correctly calculated MB values.
    """
    bytes_in_mb = 1024 * 1024
    mock_vm = MagicMock()
    mock_vm.total = 100 * bytes_in_mb
    mock_vm.available = 40 * bytes_in_mb
    mock_psutil.virtual_memory.return_value = mock_vm

    system_manager.set_device_context(device=None, dispatcher=mock_dispatcher)
    system_manager.publish_metrics()

    mock_psutil.virtual_memory.assert_called_once()
    assert mock_dispatcher.publish_event.called

    call_args = mock_dispatcher.publish_event.call_args
    topic = call_args.args[0]
    event = call_args.args[1]

    assert topic == "events/system"
    assert isinstance(event, SystemEvents)
    assert event.metrics is not None

    assert event.metrics.mem_total_mb == 100.0
    assert event.metrics.mem_free_mb == 40.0


@patch("src.udmi.core.managers.system_manager.psutil")
def test_publish_metrics_handles_import_error(mock_psutil, system_manager,
    caplog):
    """
    test_publish_metrics_handles_import_error
    Verify that if psutil raises ImportError (simulating not installed),
    it logs an error and does not crash.
    """
    mock_psutil.virtual_memory.side_effect = ImportError(
        "No module named psutil")

    with caplog.at_level(logging.ERROR):
        system_manager.publish_metrics()

    assert "psutil not installed" in caplog.text


@patch("src.udmi.core.managers.system_manager.psutil")
def test_publish_metrics_handles_generic_exception(mock_psutil, system_manager,
    caplog):
    """
    test_publish_metrics_handles_generic_exception
    Verify that generic exceptions during metric collection are caught and
    logged.
    """
    mock_psutil.virtual_memory.side_effect = ValueError(
        "Something weird happened")

    with caplog.at_level(logging.ERROR):
        system_manager.publish_metrics()

    assert "Failed to publish metrics" in caplog.text
    assert "Something weird happened" in caplog.text


def test_stop_cleans_up_thread(system_manager, mock_dispatcher):
    """
    test_stop_cleans_up_thread
    Start the manager (which spawns a thread) and ensure stop() joins it
    correctly.
    """
    system_manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    system_manager.start()
    assert system_manager._metrics_thread.is_alive()
    assert not system_manager._stop_event.is_set()

    system_manager.stop()

    assert system_manager._stop_event.is_set()
    assert not system_manager._metrics_thread.is_alive()


def test_start_increments_restart_count(system_manager, mock_dispatcher):
    """
    test_start_increments_restart_count
    Verify that start() loads the previous count from persistence,
    increments it, and saves it.
    """
    mock_device = MagicMock()
    mock_persistence = MagicMock()

    mock_device.persistence = mock_persistence
    mock_persistence.get.return_value = 5

    system_manager.set_device_context(device=mock_device,
                                      dispatcher=mock_dispatcher)

    with patch.object(system_manager, '_metrics_loop'):
        system_manager.start()

    mock_persistence.get.assert_called_with("restart_count", 0)
    mock_persistence.set.assert_called_with("restart_count", 6)

    assert system_manager._restart_count == 6


def test_start_handles_persistence_error(system_manager, mock_dispatcher,
    caplog):
    """
    Verify that if persistence fails (raises Exception), start() catches it,
    logs an error, and continues startup.
    """
    mock_device = MagicMock()
    mock_persistence = MagicMock()
    mock_device.persistence = mock_persistence
    mock_persistence.get.side_effect = RuntimeError("Disk full")

    system_manager.set_device_context(device=mock_device,
                                      dispatcher=mock_dispatcher)

    with caplog.at_level(logging.ERROR):
        with patch.object(system_manager, '_metrics_loop'):
            system_manager.start()

    assert "Failed to handle persistence" in caplog.text
    assert "Disk full" in caplog.text

    mock_dispatcher.publish_event.assert_called()


def test_update_state_includes_restart_count(system_manager):
    """
    test_update_state_includes_restart_count
    Verify that update_state includes the internal restart_count in the
    StateSystemOperation block.
    """
    system_manager._restart_count = 101

    state = State()
    system_manager.update_state(state)

    assert state.system is not None
    assert state.system.operation.restart_count == 101
