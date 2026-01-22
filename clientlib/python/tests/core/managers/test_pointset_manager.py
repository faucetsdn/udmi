"""
Unit tests for the `PointsetManager` class.

This module tests the `PointsetManager` and its helper `Point` class in isolation.
"""

from unittest.mock import MagicMock
from unittest.mock import patch
import time

import pytest
from udmi.schema import Config
from udmi.schema import PointPointsetConfig
from udmi.schema import PointsetConfig
from udmi.schema import PointsetEvents
from udmi.schema import PointsetState
from udmi.schema import State

from src.udmi.core.managers.pointset_manager import Point
from src.udmi.core.managers.pointset_manager import PointsetManager
from src.udmi.core.messaging import AbstractMessageDispatcher


# pylint: disable=redefined-outer-name,protected-access

@pytest.fixture
def mock_dispatcher():
    """Provides a mock dispatcher object conforming to the spec."""
    return MagicMock(spec=AbstractMessageDispatcher)


@pytest.fixture
def manager():
    """Provides a default PointsetManager instance."""
    return PointsetManager()


# --- Point Class Tests ---

def test_point_value_update():
    """Test that setting a value updates the internal state for events."""
    point = Point("temp_sensor")
    assert point.present_value is None

    point.set_present_value(25.5)
    assert point.present_value == 25.5

    event = point.get_event()
    assert event.present_value == 25.5


def test_point_config_update():
    """Test that updating config updates the internal state for reporting."""
    point = Point("temp_sensor")
    assert point.units is None

    config = PointPointsetConfig(units="Celsius")
    point.update_config(config)

    assert point.units == "Celsius"

    state = point.get_state()
    assert state.units == "Celsius"


def test_point_should_report_cov_logic():
    """
    Test the Change of Value (COV) logic.
    """
    point = Point("test_point")
    point.cov_increment = 1.0

    point.set_present_value(10.0)
    assert point.should_report(sample_rate_sec=10) is True
    point.mark_reported()

    point.set_present_value(10.5)
    assert point.should_report(sample_rate_sec=10) is False

    point.set_present_value(11.1)
    assert point.should_report(sample_rate_sec=10) is True
    point.mark_reported()

    with patch("time.time", return_value=point.last_reported_time + 601):
        assert point.should_report(sample_rate_sec=10) is True


# --- PointsetManager Tests ---

def test_initialization(manager):
    """Test default initialization values."""
    assert manager._sample_rate_sec == 10
    assert len(manager._points) == 0


def test_add_point_and_set_value(manager):
    """Test the API for adding points and setting values."""
    manager.set_point_value("pressure", 101.3)

    assert "pressure" in manager._points
    assert manager._points["pressure"].present_value == 101.3


def test_handle_config_updates_sample_rate(manager):
    """Test that the sample rate is updated from config."""
    assert manager._sample_rate_sec == 10

    config = Config(
        pointset=PointsetConfig(sample_rate_sec=60)
    )
    manager.handle_config(config)

    assert manager._sample_rate_sec == 60


def test_handle_config_adds_points(manager):
    """Test that points defined in config are added to the manager."""
    config = Config(
        pointset=PointsetConfig(
            points={
                "room_temp": PointPointsetConfig(units="C"),
                "humidity": PointPointsetConfig(units="%")
            }
        )
    )
    manager.handle_config(config)

    assert "room_temp" in manager._points
    assert manager._points["room_temp"].units == "C"
    assert "humidity" in manager._points
    assert manager._points["humidity"].units == "%"


def test_handle_config_updates_state_etag(manager):
    """Test that state_etag is captured from config."""
    config = Config(
        pointset=PointsetConfig(state_etag="abcdef123")
    )
    manager.handle_config(config)

    assert manager._state_etag == "abcdef123"


def test_update_state_populates_state_block(manager):
    """Test that update_state populates the state object correctly."""
    manager.add_point("temp")
    manager._points["temp"].units = "C"
    manager._state_etag = "etag_value"

    state = State()
    manager.update_state(state)

    assert state.pointset is not None
    assert isinstance(state.pointset, PointsetState)
    assert state.pointset.state_etag == "etag_value"
    assert "temp" in state.pointset.points
    assert state.pointset.points["temp"].units == "C"


def test_publish_telemetry_sends_events(manager, mock_dispatcher):
    """
    Test that publish_telemetry sends a PointsetEvents message
    via the dispatcher.
    """
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    manager.set_point_value("temp", 22.0)
    manager.set_point_value("pressure", 1000)

    manager.publish_telemetry()

    mock_dispatcher.publish_event.assert_called_once()
    call_args = mock_dispatcher.publish_event.call_args
    channel = call_args[0][0]
    payload = call_args[0][1]

    assert channel == "events/pointset"
    assert isinstance(payload, PointsetEvents)
    assert payload.points["temp"].present_value == 22.0
    assert payload.points["pressure"].present_value == 1000


def test_publish_telemetry_ignores_points_without_values(manager,
    mock_dispatcher):
    """
    Test that points with no value (None) are excluded from the event.
    """
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    manager.set_point_value("valid_point", 10)
    manager.add_point("empty_point")

    manager.publish_telemetry()

    call_args = mock_dispatcher.publish_event.call_args
    payload = call_args[0][1]

    assert "valid_point" in payload.points
    assert "empty_point" not in payload.points


def test_publish_telemetry_does_nothing_if_no_values(manager, mock_dispatcher):
    """
    Test that nothing is published if no points have values.
    """
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)
    manager.add_point("empty_point")

    manager.publish_telemetry()

    mock_dispatcher.publish_event.assert_not_called()


def test_lifecycle_start_stop(manager):
    """
    Test that start/stop manages the thread correctly.
    """
    assert len(manager._active_threads) == 0

    with patch('udmi.core.managers.pointset_manager.PointsetManager._load_persisted_state'):
        manager.start()

    assert len(manager._active_threads) == 1
    thread = manager._active_threads[0]
    assert thread.is_alive()
    assert not manager._shutdown_event.is_set()

    with patch('udmi.core.managers.pointset_manager.PointsetManager._persist_state'):
        manager.stop()

    assert manager._shutdown_event.is_set()
    assert len(manager._active_threads) == 0


def test_handle_config_triggers_writeback_handler(manager):
    """
    Verifies that if a config contains 'set_value', the registered
    writeback handler is called.
    """
    mock_handler = MagicMock()
    manager.set_writeback_handler(mock_handler)

    config = Config(
        pointset=PointsetConfig(
            points={
                "setpoint": PointPointsetConfig(set_value=22.5)
            }
        )
    )

    manager.handle_config(config)

    mock_handler.assert_called_once_with("setpoint", 22.5)
    assert manager._points["setpoint"].set_value == 22.5


def test_publish_telemetry_invokes_poll_callback(manager, mock_dispatcher):
    """
    Verifies that if a poll callback is registered, it is called
    before telemetry generation, and its results are included.
    """
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    def mock_poll():
        return {"polled_point": 123}

    manager.set_poll_callback(mock_poll)

    manager.publish_telemetry()

    call_args = mock_dispatcher.publish_event.call_args
    payload = call_args[0][1]

    assert "polled_point" in payload.points
    assert payload.points["polled_point"].present_value == 123