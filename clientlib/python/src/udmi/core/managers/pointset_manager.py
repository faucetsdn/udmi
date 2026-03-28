"""
Provides the concrete implementation for the PointsetManager.

This manager is responsible for handling the 'pointset' block of the config
and state, and for reporting pointset telemetry events.
"""
import hashlib
import json
import logging
import threading
import time
import warnings
from dataclasses import dataclass
from datetime import datetime
from datetime import timezone
from typing import Any
from typing import Callable
from typing import Dict
from typing import Mapping
from typing import Optional
from typing import Union

from udmi.constants import UDMI_VERSION
from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.point.abstract_point import AbstractPoint
from udmi.core.managers.point.bulk_provider import BulkPointProvider
from udmi.core.managers.point.virtual_point import Point
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import Metadata
from udmi.schema import PointPointsetConfig
from udmi.schema import PointsetEvents
from udmi.schema import PointsetState
from udmi.schema import State
from udmi.schema import ValueState

LOGGER = logging.getLogger(__name__)

DEFAULT_SAMPLE_RATE_SEC = 10
DEFAULT_HEARTBEAT_SEC = 600


@dataclass
class WritebackResult:
    """
    Result of a point writeback operation.
    """
    value_state: ValueState
    status: Optional[Entry] = None


# Callback signature: function(point_name, value) -> Optional[ValueState | WritebackResult]
WritebackHandler = Callable[
    [str, Any], Union[None, ValueState, WritebackResult]]

# Poll Callback now returns a dictionary of {point_name: value}
PollCallback = Callable[[], Dict[str, Any]]


class PointsetManager(BaseManager): # pylint: disable=too-many-instance-attributes
    """
    Manages the 'pointset' block.
    Acts as the central orchestrator for device telemetry. It dynamically provisions points
    from configuration, parses sample rates, oversees background telemetry generation loops,
    and handles point writebacks (including validation, staleness, and expiry).

    - Point Lifecycle: Dynamically instantiates new points based on config and manages 
      their lifecycles (active vs inactive points).
    - Writeback Timers: Spawns threading.Timer instances per-point for the 'set_value_expiry'
      directive. Transitions points back to base state and triggers State
      regeneration upon expiration.
    - State Etag Tracking: Calculates and tracks a deterministic state_etag (SHA-256) of all 
      managed points to prevent stale writebacks and ensure UI synchronicity.
    - Polling Pipeline: Interleaves Pointset callbacks, BulkPointProvider reads, and individual 
      point updates inside its publish_telemetry method.
    """
    PERSISTENCE_KEY = "pointset_state"
    PERSISTENCE_BUFFER_KEY = "pointset_telemetry_buffer"
    MAX_OFFLINE_BUFFER_SIZE = 1000

    @property
    def model_field_name(self) -> str:
        return "pointset"

    def __init__(self, sample_rate_sec: int = DEFAULT_SAMPLE_RATE_SEC,
        point_factory: Optional[Callable] = None):
        """
        Initializes the PointsetManager.

        Args:
            sample_rate_sec: The default interval between telemetry checks.
            point_factory: Optional factory to create points. Defaults to Point.
        """
        super().__init__()
        self._point_factory = point_factory or Point
        self._all_points: Dict[str, AbstractPoint] = {}
        self._last_set_values: Dict[str, Any] = {}
        self._sample_rate_sec = sample_rate_sec
        self._state_etag: Optional[str] = None
        self._active_points: set = set()
        self._last_active_points: set = set()

        self._sample_limit_sec: Optional[int] = None
        self._last_publish_time: float = 0.0

        self._last_full_publish_time: float = 0.0

        self._writeback_handler: Optional[WritebackHandler] = None
        self._telemetry_wake_event: Optional[threading.Event] = None
        self._poll_callback: Optional[PollCallback] = None
        self._writeback_timers: Dict[str, threading.Timer] = {}
        self._bulk_provider: Optional[BulkPointProvider] = None

        self._offline_buffer: list[dict] = []

        LOGGER.info("PointsetManager initialized.")

    @property
    def points(self) -> Mapping[str, AbstractPoint]:
        """Returns a read-only mapping of the active points."""
        return {
            name: self._all_points[name]
            for name in self._active_points
            if name in self._all_points
        }

    @property
    def all_points(self) -> Mapping[str, AbstractPoint]:
        """Returns a read-only mapping of all points."""
        return self._all_points

    def set_model(self, model: Metadata) -> None:
        """
        Applies the Pointset Metadata (Model) to the manager.
        Iterates through the static metadata model during startup to initialize
        points and apply static properties (like units).

        Args:
            model: The device metadata object.
        """
        super().set_model(model)
        if not self.model or not hasattr(self.model,
                                         'points') or not self.model.points:
            return

        LOGGER.info("Applying Pointset Metadata Model...")
        for name, point_model in self.model.points.items():
            if name not in self._all_points:
                self.add_point(name)
            else:
                self._all_points[name].set_model(point_model)

    def set_writeback_handler(self, handler: WritebackHandler) -> None:
        """
        Registers a callback for 'set_value' requests from cloud.

        Args:
            handler: A callable taking (point_name, value).
        """
        message = (
            "set_writeback_handler is deprecated and will be removed in v2.0.0. "
            "Please subclass BasicPoint or AbstractPoint and use point_factory instead."
        )
        LOGGER.warning(message)
        warnings.warn(message, DeprecationWarning, stacklevel=2)
        self._writeback_handler = handler

    def set_poll_callback(self, callback: PollCallback) -> None:
        """
        Registers a callback invoked immediately before telemetry generation.

        This allows the application to perform a "just-in-time" read of
        sensor data before the manager checks for COV.

        Args:
            callback: A callable returning a dict of {point_name: value}.
        """
        message = (
            "set_poll_callback is deprecated and will be removed in v2.0.0. "
            "Please migrate to the BulkPointProvider interface."
        )
        LOGGER.warning(message)
        warnings.warn(message, DeprecationWarning, stacklevel=2)
        self._poll_callback = callback
        LOGGER.info("Registered telemetry poll callback.")

    def register_bulk_provider(self, provider: BulkPointProvider) -> None:
        """
        Registers a bulk provider that supplies hardware reads for points.

        Args:
            provider: An instance of BulkPointProvider.
        """
        self._bulk_provider = provider
        LOGGER.info("Registered bulk telemetry provider.")

    def add_point(self, name: str) -> None:
        """
        Registers a point to be managed.

        Args:
            name: The name of the point to add.
        """
        if name not in self._all_points:
            point_model = None
            if self.model and hasattr(self.model,
                                      "points") and self.model.points:
                point_model = self.model.points.get(name)

            self._all_points[name] = self._point_factory(name,
                                                         model=point_model)
            LOGGER.debug("Added point '%s' to manager.", name)

    def set_point_value(self, name: str, value: Any) -> None:
        """
        API for Client Application to update a point's value.

        Args:
            name: The name of the point.
            value: The new value.
        """
        if name not in self._all_points:
            self.add_point(name)

        point = self._all_points[name]
        if hasattr(point, "set_present_value"):
            point.set_present_value(value)
        else:
            LOGGER.debug("Point '%s' does not support set_present_value", name)

    def start(self) -> None:
        """
        Starts the background telemetry reporting loop.

        Restores persisted state and launches the periodic task.
        """
        LOGGER.info("Starting PointsetManager telemetry loop...")
        self._load_persisted_state()
        self._load_persisted_buffer()
        self._telemetry_wake_event = self.start_periodic_task(
            interval_getter=lambda: self._sample_rate_sec,
            task=self.publish_telemetry,
            name="PointsetTelemetry"
        )

    def stop(self) -> None:
        """
        Stops the telemetry loop and persists current state.
        """
        self._persist_state()
        for timer in self._writeback_timers.values():
            timer.cancel()
        self._writeback_timers.clear()
        super().stop()
        LOGGER.info("PointsetManager stopped.")

    def handle_config(self, config: Config) -> None:
        # pylint: disable=too-many-locals,too-many-branches,too-many-statements,too-many-nested-blocks
        """
        Handles 'pointset' block of config.
        The central pointset state-machine driver for config updates. It updates 
        global sample rates, provisions new points dynamically, handles writeback 
        validations (etag checks, expiry evaluation), and synchronizes individual 
        point states with cloud commands.

        Args:
            config: The full device configuration.
        """
        if not config.pointset:
            return

        # Update Global Sample Rate
        if config.pointset.sample_rate_sec is not None:
            if config.pointset.sample_rate_sec != self._sample_rate_sec:
                LOGGER.info("Updating sample rate from %s to %s",
                            self._sample_rate_sec,
                            config.pointset.sample_rate_sec)
                self._sample_rate_sec = config.pointset.sample_rate_sec
                if self._telemetry_wake_event:
                    self._telemetry_wake_event.set()
        self._sample_limit_sec = config.pointset.sample_limit_sec

        # Update Points (Dynamic Provisioning)
        new_point_configs = config.pointset.points or {}
        self._active_points = set(new_point_configs.keys())

        config_timestamp_str = config.timestamp
        config_timestamp = None
        if config_timestamp_str:
            try:
                config_timestamp = datetime.fromisoformat(
                    config_timestamp_str.replace("Z", "+00:00")).timestamp()
            except ValueError:
                pass

        set_value_expiry_str = config.pointset.set_value_expiry
        set_value_expiry = None
        if set_value_expiry_str:
            try:
                set_value_expiry = datetime.fromisoformat(
                    set_value_expiry_str.replace("Z", "+00:00")).timestamp()
            except ValueError:
                pass

        incoming_etag = config.pointset.state_etag
        etag_mismatch = False
        if incoming_etag and self._state_etag and incoming_etag != self._state_etag:
            LOGGER.warning(
                "state_etag mismatch! Cloud: %s | Device: %s. Rejecting writebacks.",
                incoming_etag, self._state_etag
            )
            etag_mismatch = True

        for point_name in new_point_configs:
            if point_name not in self._all_points:
                LOGGER.info("Provisioning new point from config: %s",
                            point_name)
                self.add_point(point_name)

        for point_name, point in self._all_points.items():
            point_config = new_point_configs.get(point_name)

            invalid_expiry = False
            is_expired = False
            if point_config and point_config.set_value is not None:
                if set_value_expiry is None or (
                    config_timestamp is not None and set_value_expiry <= config_timestamp):
                    invalid_expiry = True
                elif set_value_expiry < time.time():
                    is_expired = True

            try:
                point.set_config(point_config, invalid_expiry=invalid_expiry,
                                 is_expired=is_expired,
                                 on_state_change=self.trigger_state_update)
            except TypeError:
                point.set_config(point_config)

            if point_config and point_config.set_value is not None:
                if invalid_expiry or is_expired:
                    if invalid_expiry:
                        self.trigger_state_update()
                    if point_name in self._writeback_timers:
                        self._writeback_timers[point_name].cancel()
                        del self._writeback_timers[point_name]
                    continue

                if etag_mismatch:
                    point.value_state = ValueState.invalid
                    point.status = Entry(
                        message="state_etag mismatch. Stale writeback prevented.",
                        level=500
                    )
                    self.trigger_state_update()
                    continue

                previous_set = self._last_set_values.get(point_name)
                if point_config.set_value != previous_set:
                    self._last_set_values[point_name] = point_config.set_value
                    if self._writeback_handler is not None:
                        try:
                            result = self._writeback_handler(point_name,
                                                             point_config.set_value)
                            if isinstance(result, ValueState):
                                point.value_state = result
                            elif isinstance(result, WritebackResult):
                                point.value_state = result.value_state
                                if result.status:
                                    point.status = result.status
                        except Exception as e:  # pylint: disable=broad-exception-caught
                            LOGGER.error(
                                "Error in writeback handler for %s: %s",
                                point_name, e)
                            point.value_state = ValueState.failure
                            point.status = Entry(message=str(e), level=500)
                    else:
                        # Rely entirely on the point's native set_config/set_value
                        # to manage hardware actuation and determine the resulting value_state.
                        pass

                if point.value_state in (ValueState.applied,
                                         ValueState.updating) and set_value_expiry:
                    delay = set_value_expiry - time.time()
                    if delay > 0:
                        if point_name in self._writeback_timers:
                            self._writeback_timers[point_name].cancel()

                        timer = threading.Timer(delay,
                                                self._handle_writeback_expiration,
                                                args=[point_name])
                        timer.daemon = True
                        self._writeback_timers[point_name] = timer
                        timer.start()

    def _handle_writeback_expiration(self, point_name: str) -> None:
        """
        Callback when a point's set_value_expiry is reached.
        """
        if point_name not in self._all_points:
            return

        point = self._all_points[point_name]
        try:
            if hasattr(point, "clear_writeback"):
                point.clear_writeback()
            elif hasattr(point, "set_config"):
                point.set_config(PointPointsetConfig())

            if point_name in self._writeback_timers:
                del self._writeback_timers[point_name]

            self._last_set_values.pop(point_name, None)
            self.trigger_state_update()
            LOGGER.info("Writeback timer expired for point: %s", point_name)
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Error expiring writeback for point %s: %s",
                         point_name, e)

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles commands directed at the pointset (currently none).
        """

    def _generate_state_etag(self, points_state_dict: dict) -> str:
        """
        Generates a unique SHA-256 hash representing the current pointset state.
        """
        # Ensure deterministic JSON serialization by sorting keys
        state_str = json.dumps(points_state_dict, sort_keys=True,
                               separators=(',', ':'))
        full_hash = hashlib.sha256(state_str.encode('utf-8')).hexdigest()
        return full_hash[:32]

    def _load_persisted_state(self) -> None:
        """Loads the last known point values from persistence."""
        if not self._device or not self._device.persistence:
            return

        try:
            saved_state = self._device.persistence.get(self.PERSISTENCE_KEY, {})
            if not saved_state:
                return

            restored_count = 0
            for point_name, point_data in saved_state.items():
                if point_name not in self._all_points:
                    continue

                if "present_value" in point_data:
                    self.set_point_value(point_name,
                                         point_data["present_value"])
                    restored_count += 1

            LOGGER.info("Restored state for %s points.", restored_count)

        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to load persisted pointset state: %s", e)

    def update_state(self, state: State) -> None:
        """
        Populates state.pointset with status of all managed points.
        Aggregates point-level states and compiles them into the device's main State.
        Checks for dirty flags and recalculates the state_etag if changes occurred.

        Args:
            state: The state object to update.
        """
        is_state_dirty = False

        if self._active_points != self._last_active_points:
            is_state_dirty = True
            self._last_active_points = self._active_points.copy()

        points_state_map = {}
        for name in self._active_points:
            if name in self._all_points:
                point = self._all_points[name]

                if point.is_dirty():
                    is_state_dirty = True

                points_state_map[name] = point.get_state()

        if is_state_dirty or self._state_etag is None:
            dict_map = {k: v.to_dict() for k, v in points_state_map.items()}
            self._state_etag = self._generate_state_etag(dict_map)
            LOGGER.debug("Pointset state changed. Recalculated state_etag: %s",
                         self._state_etag)

        state.pointset = PointsetState(
            state_etag=self._state_etag,
            points=points_state_map
        )
        self._persist_state()

    def _persist_state(self) -> None:
        """Saves the current values of all points to persistence."""
        if not self._device or not self._device.persistence:
            return

        try:
            data_to_save = {}
            for name, point in self._all_points.items():
                current_data = point.get_data()
                if current_data.present_value is not None:
                    data_to_save[name] = {
                        "present_value": current_data.present_value
                    }

            self._device.persistence.set(self.PERSISTENCE_KEY, data_to_save)
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to persist pointset state: %s", e)

    def _buffer_event(self, event: PointsetEvents) -> None:
        """Appends the event to the offline buffer and persists it."""
        self._offline_buffer.append(event.to_dict())

        if len(self._offline_buffer) > self.MAX_OFFLINE_BUFFER_SIZE:
            self._offline_buffer = self._offline_buffer[-self.MAX_OFFLINE_BUFFER_SIZE:]

        self._persist_buffer()
        LOGGER.info("Device offline. Buffered telemetry event. Buffer size: %s",
                    len(self._offline_buffer))

    def _flush_buffer(self) -> None:
        """Publishes all buffered events and clears the buffer."""
        if not self._offline_buffer:
            return

        try:
            LOGGER.info("Connection resumed. Flushing %s buffered telemetry events...",
                        len(self._offline_buffer))
            for event_dict in self._offline_buffer:
                event = PointsetEvents.from_dict(event_dict)
                self.publish_event(event, "pointset")
                time.sleep(0.01)

            self._offline_buffer.clear()
            self._persist_buffer()
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to flush telemetry buffer: %s", e)

    def _persist_buffer(self) -> None:
        """Saves the offline buffer to persistence."""
        if not self._device or not self._device.persistence:
            return
        try:
            self._device.persistence.set(self.PERSISTENCE_BUFFER_KEY, self._offline_buffer)
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to persist telemetry buffer: %s", e)

    def _load_persisted_buffer(self) -> None:
        """Loads the offline telemetry buffer from persistence."""
        if not self._device or not self._device.persistence:
            return
        try:
            saved_buffer = self._device.persistence.get(self.PERSISTENCE_BUFFER_KEY, [])
            if isinstance(saved_buffer, list):
                self._offline_buffer = saved_buffer
                if self._offline_buffer:
                    LOGGER.info("Restored offline telemetry buffer with %s events.",
                                len(self._offline_buffer))
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to load telemetry buffer: %s", e)

    def publish_telemetry(self) -> None:
        # pylint: disable=too-many-branches,too-many-statements
        """
        Generates and publishes a PointsetEvents message.
        Runs the periodic telemetry loop. It first updates points via Bulk Providers or Poll 
        Callbacks. Then, it evaluates every active point to see if it should_report() 
        based on COV or heartbeats, and crafts the final payload (including partial updates).
        """
        now = time.time()
        if self._sample_limit_sec is not None:
            if (now - self._last_publish_time) < self._sample_limit_sec:
                return

        if self._poll_callback and self._bulk_provider:
            LOGGER.warning(
                "Both _bulk_provider and _poll_callback are set, "
                "prioritizing _bulk_provider, but also executing _poll_callback"
            )

        if self._poll_callback:
            try:
                new_values = self._poll_callback()
                if isinstance(new_values, dict):
                    for point_name, value in new_values.items():
                        self.set_point_value(point_name, value)
                else:
                    LOGGER.warning("Poll callback returned non-dict type: %s",
                                   type(new_values))
            except Exception as e:  # pylint: disable=broad-exception-caught
                LOGGER.error("Error in telemetry poll callback: %s", e,
                             exc_info=True)

        if self._bulk_provider:
            try:
                new_values = self._bulk_provider.read_points()
                if isinstance(new_values, dict):
                    for point_name, value in new_values.items():
                        self.set_point_value(point_name, value)
                else:
                    LOGGER.warning("BulkProvider returned non-dict type: %s",
                                   type(new_values))
            except Exception as e:  # pylint: disable=broad-exception-caught
                LOGGER.error("Error in BulkPointProvider.read_points(): %s", e)

        if not self._all_points:
            return

        points_map = {}

        valid_active_points = [
            name for name in self._active_points if name in self._all_points
        ]

        force_full_update = (
            (now - self._last_full_publish_time) >= self._sample_rate_sec)

        for name in valid_active_points:
            point = self._all_points[name]
            try:
                point.update_data()
            except Exception as e:  # pylint: disable=broad-exception-caught
                LOGGER.error("Unable to update point data for '%s': %s", name,
                             e)
                continue

            try:
                if force_full_update or point.should_report(
                    self._sample_rate_sec):
                    points_map[name] = point.get_data()
                    point.mark_reported()
            except Exception as e:  # pylint: disable=broad-exception-caught
                LOGGER.error("Unable to process reporting for '%s': %s", name,
                             e)

        if not points_map:
            return

        is_partial = len(points_map) < len(valid_active_points)

        try:
            event = PointsetEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                points=points_map,
                partial_update=True if is_partial else None
            )

            if not self.is_connected:
                self._buffer_event(event)
            else:
                self._flush_buffer()
                self.publish_event(event, "pointset")
                LOGGER.debug(
                    "Published telemetry for %s points (Partial: %s, Forced Full: %s).",
                    len(points_map),
                    is_partial,
                    force_full_update
                )

            self._last_publish_time = now
            if force_full_update:
                self._last_full_publish_time = now
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to publish telemetry: %s", e)
