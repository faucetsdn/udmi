"""
Unit tests for the `SystemManager` class.

This module tests the `SystemManager` in isolation by mocking the
`AbstractMessageDispatcher` and other dependencies.
"""

import logging
from unittest.mock import MagicMock
from unittest.mock import patch

import pytest

from src.udmi.core.managers.system_manager import SystemManager
from src.udmi.core.messaging import AbstractMessageDispatcher
from tests.conftest import _system_lifecycle
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import BlobBlobsetConfig
from udmi.schema import BlobsetConfig
from udmi.schema import Config
from udmi.schema import State
from udmi.schema import StateSystemHardware
from udmi.schema import SystemConfig
from udmi.schema import SystemEvents
from udmi.schema.common import Phase


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
    Verify mock_dispatcher.publish_event was called with the channel "system"
    and a SystemEvents object.
    """
    system_manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    with patch.object(BaseManager, 'start_periodic_task'):
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
    Check that manager._last_config_ts equals the timestamp.
    """
    test_timestamp = "2025-10-28T14:30:00Z"
    config_obj = Config(timestamp=test_timestamp)

    system_manager.handle_config(config_obj)

    assert system_manager._last_config_ts == test_timestamp


def test_update_state_populates_all_fields(system_manager):
    """
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


@patch("sys.exit")
def test_handle_command_logs_warning(mock_exit, system_manager, caplog):
    """
    Verify a warning was logged containing "Initiating System RESTART".
    """
    system_manager.register_command_handler(
        "reboot",
        lambda p: _system_lifecycle(192)
    )

    with caplog.at_level(logging.WARNING):
        system_manager.handle_command("reboot", {})

    mock_exit.assert_called_once_with(192)


def test_handle_config_updates_metrics_rate(system_manager):
    """
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
    Verify that if psutil raises ImportError (simulating not installed),
    it logs an error and does not crash.
    """
    mock_psutil.virtual_memory.side_effect = ImportError(
        "No module named psutil")

    with caplog.at_level(logging.ERROR):
        system_manager.publish_metrics()

    assert "No module named psutil" in caplog.text


@patch("src.udmi.core.managers.system_manager.psutil")
def test_publish_metrics_handles_generic_exception(mock_psutil, system_manager,
    caplog):
    """
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
    Start the manager (which spawns a thread) and ensure stop() joins it
    correctly.
    """
    system_manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    system_manager.start()

    assert len(system_manager._active_threads) == 1
    thread = system_manager._active_threads[0]
    assert thread.is_alive()
    assert not system_manager._shutdown_event.is_set()

    system_manager.stop()

    assert system_manager._shutdown_event.is_set()
    assert len(system_manager._active_threads) == 0


def test_start_increments_restart_count(system_manager, mock_dispatcher):
    """
    Verify that start() loads the previous count from persistence,
    increments it, and saves it.
    """
    mock_device = MagicMock()
    mock_persistence = MagicMock()

    mock_device.persistence = mock_persistence
    mock_persistence.get.return_value = 5

    system_manager.set_device_context(device=mock_device,
                                      dispatcher=mock_dispatcher)

    with patch.object(BaseManager, 'start_periodic_task'):
        system_manager.start()

    mock_persistence.get.assert_any_call("restart_count", 0)
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
        with patch.object(BaseManager, 'start_periodic_task'):
            system_manager.start()

    assert "Failed to handle persistence" in caplog.text
    assert "Disk full" in caplog.text

    mock_dispatcher.publish_event.assert_called()


def test_update_state_includes_restart_count(system_manager):
    """
    Verify that update_state includes the internal restart_count in the
    StateSystemOperation block.
    """
    system_manager._restart_count = 101

    state = State()
    system_manager.update_state(state)

    assert state.system is not None
    assert state.system.operation.restart_count == 101


# --- New Tests for Missing Coverage ---

@patch("src.udmi.core.managers.system_manager.get_verified_blob_file")
@patch("builtins.open")
@patch("os.path.exists")
@patch("os.remove")
def test_blobset_apply_success(
    mock_remove,
    mock_exists,
    mock_open,
    mock_fetch_blob,
    system_manager,
    mock_dispatcher
):
    """
    Verifies the happy path for processing a blobset config:
    1. Detects new generation.
    2. Updates state to 'apply'.
    3. Fetches blob.
    4. Calls registered handler.
    5. Updates state to 'final'.
    """
    mock_device = MagicMock()
    mock_device.persistence = MagicMock()
    system_manager.set_device_context(device=mock_device,
                                      dispatcher=mock_dispatcher)

    mock_handler_func = MagicMock(return_value="Success")
    system_manager.register_blob_handler("firmware", mock_handler_func)

    blob_config = BlobBlobsetConfig(generation="gen_2", url="http://blob",
                                    sha256="hash")
    config = Config(blobset=BlobsetConfig(blobs={"firmware": blob_config}))

    mock_exists.return_value = True

    with patch("threading.Thread") as mock_thread_cls:
        system_manager.handle_config(config)
        mock_thread_cls.assert_called_once()
        target = mock_thread_cls.call_args[1]['target']
        args = mock_thread_cls.call_args[1]['args']
        target(*args)

    mock_fetch_blob.assert_called_once()

    mock_handler_func.assert_called_once()

    assert system_manager._blob_states["firmware"].phase == Phase.final
    assert system_manager._blob_states["firmware"].status is None

    mock_device.persistence.set.assert_called()


@patch("src.udmi.core.managers.system_manager.get_verified_blob_file")
def test_blobset_apply_failure_logs_error(
    mock_fetch_blob,
    system_manager,
    mock_dispatcher
):
    """
    Verifies that if blob processing fails, the error is logged and state
    is updated with the error message.
    """
    system_manager.set_device_context(device=MagicMock(),
                                      dispatcher=mock_dispatcher)
    system_manager.register_blob_handler("firmware", MagicMock())

    mock_fetch_blob.side_effect = ValueError("Hash Mismatch")

    blob_config = BlobBlobsetConfig(generation="gen_fail", url="http://blob",
                                    sha256="hash")

    system_manager._run_blob_worker("firmware", blob_config)

    state = system_manager._blob_states["firmware"]
    assert state.phase == Phase.final
    assert state.status.level == 500
    assert "Hash Mismatch" in state.status.message


def test_trigger_key_rotation_flow(system_manager):
    """
    Verifies the key rotation workflow:
    1. Checks CredentialManager availability.
    2. Calls rotate_credentials.
    3. Invokes callback.
    4. Requests connection reset.
    """
    mock_device = MagicMock()
    mock_cred_manager = MagicMock()
    mock_device.credential_manager = mock_cred_manager
    system_manager.set_device_context(device=mock_device,
                                      dispatcher=MagicMock())

    mock_callback = MagicMock(return_value=True)
    system_manager.register_key_rotation_callback(mock_callback)

    system_manager.trigger_key_rotation()

    mock_cred_manager.rotate_credentials.assert_called_once()
    mock_callback.assert_called_once()
    mock_device.request_connection_reset.assert_called_once()

    assert system_manager._system_state.status.level == 200
    assert "Key rotation complete" in system_manager._system_state.status.message


def test_key_rotation_handles_failure(system_manager):
    """
    Verifies handling when key rotation fails (e.g., backup failure).
    """
    mock_device = MagicMock()
    mock_cred_manager = MagicMock()
    mock_cred_manager.rotate_credentials.side_effect = RuntimeError(
        "Backup Failed")

    mock_device.credential_manager = mock_cred_manager
    system_manager.set_device_context(device=mock_device,
                                      dispatcher=MagicMock())

    system_manager.trigger_key_rotation()

    assert system_manager._system_state.status.level == 500
    assert "Backup Failed" in system_manager._system_state.status.message
    mock_device.request_connection_reset.assert_not_called()
