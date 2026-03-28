"""
Unit tests for the `PointsetManager` class.

This module tests the `PointsetManager` and its helper `Point` class in isolation.
"""

from unittest.mock import MagicMock
from unittest.mock import patch
import time
import warnings

import pytest
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import PointPointsetConfig
from udmi.schema import PointsetConfig
from udmi.schema import PointsetEvents
from udmi.schema import PointsetState
from udmi.schema import State
from udmi.schema import ValueState

from udmi.core.managers.point.virtual_point import Point
from src.udmi.core.managers.pointset_manager import PointsetManager
from src.udmi.core.managers.pointset_manager import WritebackResult
from src.udmi.core.messaging import AbstractMessageDispatcher
from udmi.core.managers.point.bulk_provider import BulkPointProvider


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
    point.set_present_value(25.5)
    point.update_data() # Sync value to event
    
    event = point.get_data()
    assert event.present_value == 25.5


def test_point_config_update():
    """Test that updating config updates the internal state for reporting."""
    point = Point("temp_sensor")

    config = PointPointsetConfig(units="Celsius")
    point.set_config(config)

    state = point.get_state()
    assert state.units == "Celsius"


def test_point_should_report_cov_logic():
    """
    Test the Change of Value (COV) logic.
    """
    point = Point("test_point")
    config = PointPointsetConfig(cov_increment=1.0)
    point.set_config(config)

    point.set_present_value(10.0)
    point.update_data()
    assert point.should_report(sample_rate_sec=10) is True
    point.mark_reported()

    point.set_present_value(10.5)
    point.update_data()
    assert point.should_report(sample_rate_sec=10) is False

    point.set_present_value(11.1)
    point.update_data()
    assert point.should_report(sample_rate_sec=10) is True
    point.mark_reported()

    # Test periodic reporting without COV (Steady State)
    # 5 seconds - Should not report
    with patch("time.time", return_value=point._last_reported_time + 5):
        assert point.should_report(sample_rate_sec=10) is False

    # 11 seconds (exceeds sample_rate_sec=10) - Should report
    with patch("time.time", return_value=point._last_reported_time + 11):
        assert point.should_report(sample_rate_sec=10) is True


def test_point_set_model_syncs_ref():
    """Test that model limits and values behave correctly with metadata."""
    from udmi.schema import Category
    from udmi.schema import PointPointsetModel
    model = PointPointsetModel(ref="point_ref_123", writable=True)
    point = Point("temp_sensor", model=model)

    config_good = PointPointsetConfig(ref="point_ref_123")
    point.set_config(config_good)
    assert point.get_state().status is None

    config_bad = PointPointsetConfig(ref="bad_ref")
    point.set_config(config_bad)
    state = point.get_state()
    assert state.status is not None
    assert state.status.category == Category.POINTSET_POINT_FAILURE


# --- PointsetManager Tests ---

def test_initialization(manager):
    """Test default initialization values."""
    assert manager._sample_rate_sec == 10
    assert len(manager._all_points) == 0


def test_add_point_and_set_value(manager):
    """Test the API for adding points and setting values."""
    manager.set_point_value("pressure", 101.3)

    assert "pressure" in manager._all_points
    manager._all_points["pressure"].update_data()
    assert manager._all_points["pressure"].get_data().present_value == 101.3


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

    assert "room_temp" in manager._all_points
    assert manager._all_points["room_temp"].get_state().units == "C"
    assert "humidity" in manager._all_points
    assert manager._all_points["humidity"].get_state().units == "%"


def test_handle_config_updates_state_etag(manager):
    """Test that state_etag is NOT blindly captured from config."""
    manager._state_etag = "original"
    config = Config(
        pointset=PointsetConfig(state_etag="abcdef123")
    )
    manager.handle_config(config)

    assert manager._state_etag == "original"


def test_update_state_populates_state_block(manager):
    """Test that update_state populates the state object correctly."""
    manager.add_point("temp")
    manager._all_points["temp"].set_config(PointPointsetConfig(units="C"))
    manager._state_etag = "etag_value"
    manager._active_points = {"temp"}

    state = State()
    manager.update_state(state)

    assert state.pointset is not None
    assert isinstance(state.pointset, PointsetState)
    assert state.pointset.state_etag == manager._state_etag
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
    manager._active_points = {"temp", "pressure"}

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
    Test that points with no value (None) are excluded from the event 
    when not doing a full update (force_full_update=False).
    """
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    manager.set_point_value("valid_point", 10)
    manager.add_point("empty_point")
    manager._active_points = {"valid_point", "empty_point"}

    import time
    manager._last_full_publish_time = time.time()
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
        timestamp="2030-01-01T00:00:00Z",
        pointset=PointsetConfig(
            set_value_expiry="2030-01-01T01:00:00Z",
            points={
                "setpoint": PointPointsetConfig(set_value=22.5)
            }
        )
    )

    manager.handle_config(config)

    mock_handler.assert_called_once_with("setpoint", 22.5)


def test_publish_telemetry_invokes_poll_callback(manager, mock_dispatcher):
    """
    Verifies that if a poll callback is registered, it is called
    before telemetry generation, and its results are included.
    """
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)

    def mock_poll():
        return {"polled_point": 123}

    manager.set_poll_callback(mock_poll)
    manager._active_points = {"polled_point"}

    manager.publish_telemetry()

    call_args = mock_dispatcher.publish_event.call_args
    payload = call_args[0][1]

    assert "polled_point" in payload.points
    assert payload.points["polled_point"].present_value == 123


def test_handle_config_writeback_returns_valuestate(manager):
    """
    Verifies that if a writeback handler returns ValueState, 
    the point's value_state is updated.
    """
    def mock_handler(point_name, value):
        return ValueState.overridden

    manager.set_writeback_handler(mock_handler)

    manager.add_point("setpoint")
    manager._all_points["setpoint"]._writable = True

    config = Config(
        timestamp="2030-01-01T00:00:00Z",
        pointset=PointsetConfig(
            set_value_expiry="2030-01-01T01:00:00Z",
            points={"setpoint": PointPointsetConfig(set_value=22.5)}
        )
    )

    manager.handle_config(config)

    assert manager._all_points["setpoint"].get_data().present_value == 22.5
    assert manager._all_points["setpoint"].value_state == ValueState.overridden


def test_handle_config_writeback_returns_result(manager):
    """
    Verifies that if a writeback handler returns WritebackResult,
    the point's value_state and status are updated.
    """
    custom_entry = Entry(message="Invalid setting", level=500)
    
    def mock_handler(point_name, value):
        return WritebackResult(value_state=ValueState.invalid, status=custom_entry)

    manager.set_writeback_handler(mock_handler)

    manager.add_point("setpoint")
    manager._all_points["setpoint"]._writable = True

    config = Config(
        timestamp="2030-01-01T00:00:00Z",
        pointset=PointsetConfig(
            set_value_expiry="2030-01-01T01:00:00Z",
            points={"setpoint": PointPointsetConfig(set_value=22.5)}
        )
    )

    manager.handle_config(config)

    point = manager._all_points["setpoint"]
    assert point.get_data().present_value == 22.5
    assert point.value_state == ValueState.invalid
    assert point.status == custom_entry


def test_handle_config_writeback_exception_sets_failure(manager):
    """
    Verifies that if a writeback handler raises an exception,
    the point's value_state is set to failure and status is populated.
    """
    def mock_handler(point_name, value):
        raise ValueError("Hardware communication failed")

    manager.set_writeback_handler(mock_handler)

    manager.add_point("setpoint")
    manager._all_points["setpoint"]._writable = True

    config = Config(
        timestamp="2030-01-01T00:00:00Z",
        pointset=PointsetConfig(
            set_value_expiry="2030-01-01T01:00:00Z",
            points={"setpoint": PointPointsetConfig(set_value=22.5)}
        )
    )

    manager.handle_config(config)

    point = manager._all_points["setpoint"]
    assert point.get_data().present_value == 22.5
    assert point.value_state == ValueState.failure
    assert point.status is not None
    assert point.status.level == 500
    assert "Hardware communication failed" in point.status.message


def test_set_writeback_handler_emits_deprecation_warning(manager):
    """Verifies that calling set_writeback_handler triggers a DeprecationWarning."""
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        manager.set_writeback_handler(lambda n, v: None)
        assert len(w) == 1
        assert issubclass(w[-1].category, DeprecationWarning)
        assert "set_writeback_handler is deprecated" in str(w[-1].message)


def test_set_poll_callback_emits_deprecation_warning(manager):
    """Verifies that calling set_poll_callback triggers a DeprecationWarning."""
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        manager.set_poll_callback(lambda: {})
        assert len(w) == 1
        assert issubclass(w[-1].category, DeprecationWarning)
        assert "set_poll_callback is deprecated" in str(w[-1].message)


class MockBulkProvider(BulkPointProvider):
    def read_points(self):
        return {"temp": 22.0}


def test_bulk_provider_updates_points(manager, mock_dispatcher):
    """Verifies that an initialized bulk provider populates values before publishing."""
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)
    
    provider = MockBulkProvider()
    manager.register_bulk_provider(provider)
    
    manager.add_point("temp")
    manager._active_points = {"temp"}

    # No point value set originally
    manager.publish_telemetry()
    
    # Assert dispatcher published with the value from the provider
    call_args = mock_dispatcher.publish_event.call_args
    payload = call_args[0][1]
    
    assert "temp" in payload.points
    assert payload.points["temp"].present_value == 22.0


def test_native_writeback_path_used_when_no_global_handler(manager):
    """Verifies point internally processes set_value when no global handler exists."""
    assert manager._writeback_handler is None
    
    manager.add_point("setpoint")
    manager._all_points["setpoint"]._writable = True
    
    config = Config(
        timestamp="2030-01-01T00:00:00Z",
        pointset=PointsetConfig(
            set_value_expiry="2030-01-01T01:00:00Z",
            points={"setpoint": PointPointsetConfig(set_value=22.5)}
        )
    )
    
    manager.handle_config(config)
    
    point = manager._all_points["setpoint"]
    assert point.get_data().present_value == 22.5
    assert point.value_state == ValueState.applied

def test_persistence_logic(manager, mock_dispatcher):
    """Verifies that _persist_state and _load_persisted_state interact with device persistence correctly."""
    mock_device = MagicMock()
    mock_persistence = MagicMock()
    mock_device.persistence = mock_persistence
    manager.set_device_context(device=mock_device, dispatcher=mock_dispatcher)

    manager.add_point("persisted_point")
    manager.set_point_value("persisted_point", 55.5)
    manager._all_points["persisted_point"].update_data()

    manager._persist_state()
    mock_persistence.set.assert_called_once()
    
    saved_data = mock_persistence.set.call_args[0][1]
    assert "persisted_point" in saved_data
    assert saved_data["persisted_point"]["present_value"] == 55.5

    mock_persistence.get.return_value = {"persisted_point": {"present_value": 77.7}}
    manager._load_persisted_state()
    
    manager._all_points["persisted_point"].update_data()
    assert manager._all_points["persisted_point"].get_data().present_value == 77.7

def test_handle_writeback_expiration(manager):
    """Verifies that writeback expiration clears the state."""
    manager.add_point("expiring_point")
    point = manager._all_points["expiring_point"]
    
    point._written = True
    point.value_state = ValueState.applied
    manager._last_set_values["expiring_point"] = 100
    manager._writeback_timers["expiring_point"] = MagicMock()
    
    manager._handle_writeback_expiration("expiring_point")
    
    assert point.value_state is None
    assert "expiring_point" not in manager._last_set_values
    assert "expiring_point" not in manager._writeback_timers

def test_generate_state_etag(manager):
    """Verifies state_etag generation is deterministic."""
    dict1 = {"pointA": {"value_state": "applied"}, "pointB": {"value_state": "invalid"}}
    dict2 = {"pointB": {"value_state": "invalid"}, "pointA": {"value_state": "applied"}}
    
    etag1 = manager._generate_state_etag(dict1)
    etag2 = manager._generate_state_etag(dict2)
    
    assert etag1 == etag2
    assert len(etag1) == 32

def test_publish_telemetry_catches_point_exceptions(manager, mock_dispatcher):
    """Verifies telemetry loop handles individual point failures gracefully."""
    manager.set_device_context(device=None, dispatcher=mock_dispatcher)
    manager.add_point("robust_point")
    manager._active_points = {"robust_point"}
    
    point = manager._all_points["robust_point"]
    point.update_data = MagicMock(side_effect=Exception("Test Error"))
    
    manager.publish_telemetry()

def test_handle_config_invalid_expiry_handling(manager):
    """Verifies that an invalid set_value_expiry transitions point to invalid."""
    manager.add_point("setpoint")
    manager._all_points["setpoint"]._writable = True
    config = Config(
        timestamp="2030-01-01T00:00:00Z",
        pointset=PointsetConfig(
            set_value_expiry="2020-01-01T00:00:00Z",
            points={"setpoint": PointPointsetConfig(set_value=123)}
        )
    )
    manager.handle_config(config)
    assert manager._all_points["setpoint"].value_state == ValueState.invalid

def test_handle_command_passthrough(manager):
    """Verifies that handle_command safely processes without failing."""
    manager.handle_command("some_command", {"payload": "data"})