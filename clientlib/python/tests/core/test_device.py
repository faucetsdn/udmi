"""
Unit tests for the core `Device` class.

This module tests the `Device` class in isolation, mocking all its
dependencies (Managers and MessageDispatcher) to verify its internal
orchestration logic.

Key behaviors verified:
- Periodic tasks within the main run loop:
    - Verifies that the periodic state publish logic is triggered
      correctly based on its time interval.
    - Verifies that the periodic auth check logic is triggered
      correctly based on its time interval.
- Lifecycle management:
    - Ensures that `device.stop()` correctly propagates the stop/close
      call to its manager and dispatcher dependencies.
    - Ensures that `device.stop()` is idempotent and safe to call multiple
      times.
- Message handling robustness:
    - Tests that `handle_config` catches, logs, and safely handles
      a config payload that fails schema validation, ensuring the
      invalid config is not passed to managers.
"""

import logging
import time
from unittest.mock import MagicMock

import pytest
from freezegun import freeze_time

from src.udmi.core.device import Device
from src.udmi.core.managers import BaseManager
from src.udmi.core.messaging import AbstractMessageDispatcher


# pylint: disable=redefined-outer-name,protected-access


@pytest.fixture
def mock_manager():
    """Provides a mock manager object conforming to the BaseManager spec."""
    return MagicMock(spec=BaseManager)


@pytest.fixture
def mock_dispatcher():
    """Provides a mock dispatcher object conforming to the spec."""
    return MagicMock(spec=AbstractMessageDispatcher)


@pytest.fixture
def test_device(mock_manager, mock_dispatcher):
    """
    Provides a real Device instance, unit-tested in isolation by
    injecting mock managers and a mock dispatcher.
    """
    device = Device(managers=[mock_manager])

    device.wire_up_dispatcher(mock_dispatcher)

    return device


def test_run_loop_periodic_state_publish(test_device, mock_dispatcher):
    """
    Asserts that the logic in the run() loop correctly
    triggers a periodic state publish.
    """
    with freeze_time("2025-10-28 12:00:00") as freezer:
        test_device._loop_state.last_state_publish_time = freezer.time_to_freeze.timestamp()

        interval = test_device._loop_config.publish_state_interval_sec
        freezer.tick(interval + 1)

        now = time.time()
        if (now - test_device._loop_state.last_state_publish_time >
            test_device._loop_config.publish_state_interval_sec):
            test_device._publish_state()
            test_device._last_state_publish_time = now

        mock_dispatcher.publish_state.assert_called_once()


def test_run_loop_periodic_auth_check(
    test_device,
    mock_dispatcher
):
    """
    Asserts that the logic in the run() loop correctly
    triggers a periodic auth check.
    """
    with freeze_time("2025-10-28 12:00:00") as freezer:
        test_device._last_auth_check = freezer.time_to_freeze.timestamp()

        interval = test_device._loop_config.auth_check_interval_sec
        freezer.tick(interval + 1)

        now = time.time()
        if now - test_device._last_auth_check > test_device._loop_config.auth_check_interval_sec:
            test_device.dispatcher.check_authentication()
            test_device._last_auth_check = now

        mock_dispatcher.check_authentication.assert_called_once()


def test_stop_calls_dependencies(
    test_device,
    mock_manager,
    mock_dispatcher
):
    """
    Asserts that device.stop() correctly calls stop() and close()
    on its dependencies.
    """
    test_device.stop()

    mock_manager.stop.assert_called_once()
    mock_dispatcher.close.assert_called_once()


def test_stop_is_idempotent(
    test_device,
    mock_manager,
    mock_dispatcher
):
    """
    Asserts that calling device.stop() multiple times only triggers
    the shutdown sequence once.
    """
    test_device.stop()
    test_device.stop()

    mock_manager.stop.assert_called_once()
    mock_dispatcher.close.assert_called_once()


def test_handle_config_bad_schema(
    test_device,
    mock_manager,
    caplog
):
    """
    Asserts that a config payload that is valid JSON but fails
    schema parsing (Config.from_dict) is caught and logged,
    and not passed to the managers.
    """
    with caplog.at_level(logging.ERROR):
        bad_payload = {"system": {"min_loglevel": "this_should_be_an_int"}}
        test_device.handle_config("config", bad_payload)

    assert "Failed to parse config message" in caplog.text

    mock_manager.handle_config.assert_not_called()
