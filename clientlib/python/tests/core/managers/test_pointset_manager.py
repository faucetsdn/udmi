"""
Unit tests for the `PointsetManager` class.

This module tests the `PointsetManager` and its helper `Point` class in isolation.

Key behaviors verified:
- Point Logic:
    - Value updates are reflected in telemetry events.
    - Config updates (units) are reflected in state.
- Manager Config Handling:
    - Sample rate updates are applied.
    - State Etag is captured.
    - New points defined in config are added to the manager.
- State Generation:
    - `update_state` correctly populates the `state.pointset` block with
      metadata from all managed points.
- Telemetry Publishing:
    - `publish_telemetry` generates a `PointsetEvents` object.
    - Only points with set values are included in the event.
    - Events are published to the 'pointset' channel.
- Lifecycle:
    - Start/Stop correctly manages the background thread.
"""

from unittest.mock import MagicMock

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

    point.set_value(25.5)
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
    assert manager._telemetry_thread is None

    manager.start()
    assert manager._telemetry_thread is not None
    assert manager._telemetry_thread.is_alive()
    assert not manager._stop_event.is_set()

    manager.stop()
    assert manager._stop_event.is_set()
    assert not manager._telemetry_thread.is_alive()
