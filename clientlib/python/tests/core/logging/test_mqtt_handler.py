"""
Unit tests for the UDMIMqttLogHandler.

These tests verify that Python log records are correctly transformed
into UDMI SystemEvents and published via the SystemManager.
"""

import logging
from unittest.mock import MagicMock

import pytest
from src.udmi.core.logging.mqtt_handler import UDMIMqttLogHandler
from udmi.schema import SystemEvents


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def mock_system_manager():
    """Mocks the SystemManager."""
    return MagicMock()


@pytest.fixture
def handler(mock_system_manager):
    """Creates a handler instance with the mocked manager."""
    return UDMIMqttLogHandler(mock_system_manager)


def test_emit_publishes_correct_event(handler, mock_system_manager):
    """
    test_emit_publishes_correct_event
    Verifies that a standard log record is formatted, converted to a SystemEvent,
    and published to the 'system' channel.
    """
    record = logging.LogRecord(
        name="test_logger",
        level=logging.INFO,
        pathname=__file__,
        lineno=10,
        msg="Test log message",
        args=(),
        exc_info=None
    )

    handler.emit(record)

    mock_system_manager.publish_event.assert_called_once()

    call_args = mock_system_manager.publish_event.call_args
    event = call_args[0][0]
    channel = call_args[0][1]

    assert channel == "system"
    assert isinstance(event, SystemEvents)

    assert len(event.logentries) == 1
    entry = event.logentries[0]
    assert entry.message == "Test log message"
    assert entry.level == 200  # INFO (20) * 10


def test_level_mapping(handler, mock_system_manager):
    """
    test_level_mapping
    Verifies that different logging levels are mapped to UDMI levels correctly (x10).
    """
    # Test ERROR (40) -> 400
    record = logging.LogRecord("test", logging.ERROR, "path", 1, "Error", (),
                               None)
    handler.emit(record)
    event = mock_system_manager.publish_event.call_args[0][0]
    assert event.logentries[0].level == 400

    # Test DEBUG (10) -> 100
    record = logging.LogRecord("test", logging.DEBUG, "path", 1, "Debug", (),
                               None)
    handler.emit(record)
    event = mock_system_manager.publish_event.call_args[0][0]
    assert event.logentries[0].level == 100


def test_timestamp_formatting(handler, mock_system_manager):
    """
    test_timestamp_formatting
    Verifies that the record.created timestamp (float) is converted to ISO format.
    """
    record = logging.LogRecord("test", logging.INFO, "path", 1, "msg", (), None)

    # Set specific time: 2023-01-01 12:00:00 UTC
    fixed_ts = 1672574400.0
    record.created = fixed_ts

    handler.emit(record)

    event = mock_system_manager.publish_event.call_args[0][0]
    expected_iso = "2023-01-01T12:00:00+00:00"
    assert event.logentries[0].timestamp == expected_iso


def test_formatting_with_args(handler, mock_system_manager):
    """
    test_formatting_with_args
    Verifies that the handler uses the standard logging formatter logic
    (interpolating %s args).
    """
    record = logging.LogRecord(
        name="test",
        level=logging.INFO,
        pathname="path",
        lineno=1,
        msg="Hello %s",
        args=("World",),
        exc_info=None
    )

    handler.emit(record)

    event = mock_system_manager.publish_event.call_args[0][0]
    assert event.logentries[0].message == "Hello World"


def test_emit_handles_exception(handler, mock_system_manager):
    """
    test_emit_handles_exception
    Verifies that if publish_event fails, the handler calls handleError
    instead of crashing the application.
    """
    mock_system_manager.publish_event.side_effect = Exception("MQTT Error")

    handler.handleError = MagicMock()

    record = logging.LogRecord("test", logging.INFO, "path", 1, "msg", (), None)

    handler.emit(record)

    handler.handleError.assert_called_once_with(record)
