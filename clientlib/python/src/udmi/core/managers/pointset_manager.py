"""
Provides the concrete implementation for the PointsetManager.

This manager is responsible for handling the 'pointset' block of the config
and state, and for reporting pointset telemetry events.
"""

import logging
import threading
import time
from datetime import datetime
from datetime import timezone
from typing import Any
from typing import Callable
from typing import Dict
from typing import Mapping
from typing import Optional
from typing import Union
from dataclasses import dataclass

from udmi.constants import UDMI_VERSION
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import Metadata
from udmi.schema import PointPointsetConfig
from udmi.schema import PointPointsetEvents
from udmi.schema import PointPointsetModel
from udmi.schema import PointPointsetState
from udmi.schema import PointsetEvents
from udmi.schema import PointsetState
from udmi.schema import State
from udmi.schema import ValueState

from udmi.core.managers.point.abstract_point import AbstractPoint
from udmi.core.managers.point.concrete_point import Point

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
WritebackHandler = Callable[[str, Any], Union[None, ValueState, WritebackResult]]

# Poll Callback now returns a dictionary of {point_name: value}
PollCallback = Callable[[], Dict[str, Any]]




class PointsetManager(BaseManager):
    """
    Manages the 'pointset' block.
    Handles configuration of points, reporting of point states, and
    periodic publishing of telemetry events based on global rate OR per-point COV.
    """
    PERSISTENCE_KEY = "pointset_state"

    @property
    def model_field_name(self) -> str:
        return "pointset"

    def __init__(self, sample_rate_sec: int = DEFAULT_SAMPLE_RATE_SEC, point_factory: Optional[Callable] = None):
        """
        Initializes the PointsetManager.

        Args:
            sample_rate_sec: The default interval between telemetry checks.
            point_factory: Optional factory to create points. Defaults to Point.
        """
        super().__init__()
        self._point_factory = point_factory or Point
        self._points: Dict[str, AbstractPoint] = {}
        self._last_set_values: Dict[str, Any] = {}
        self._sample_rate_sec = sample_rate_sec
        self._state_etag: Optional[str] = None

        self._writeback_handler: Optional[WritebackHandler] = None
        self._telemetry_wake_event: Optional[threading.Event] = None
        self._poll_callback: Optional[PollCallback] = None

        LOGGER.info("PointsetManager initialized.")

    @property
    def points(self) -> Mapping[str, AbstractPoint]:
        """Returns a read-only mapping of the managed points."""
        return self._points

    def set_model(self, model: Metadata) -> None:
        """
        Applies the Pointset Metadata (Model) to the manager.

        This iterates through the metadata model and initializes points
        defined therein.

        Args:
            model: The device metadata object.
        """
        super().set_model(model)
        if not self.model or not hasattr(self.model, 'points') or not self.model.points:
            return

        LOGGER.info("Applying Pointset Metadata Model...")
        for name, point_model in self.model.points.items():
            if name not in self._points:
                self.add_point(name)

    def set_writeback_handler(self, handler: WritebackHandler) -> None:
        """
        Registers a callback for 'set_value' requests from cloud.

        Args:
            handler: A callable taking (point_name, value).
        """
        self._writeback_handler = handler

    def set_poll_callback(self, callback: PollCallback) -> None:
        """
        Registers a callback invoked immediately before telemetry generation.

        This allows the application to perform a "just-in-time" read of
        sensor data before the manager checks for COV.

        Args:
            callback: A callable returning a dict of {point_name: value}.
        """
        self._poll_callback = callback
        LOGGER.info("Registered telemetry poll callback.")

    def add_point(self, name: str) -> None:
        """
        Registers a point to be managed.

        Args:
            name: The name of the point to add.
        """
        if name not in self._points:
            point_model = None
            if self.model and hasattr(self.model, "points") and self.model.points:
                point_model = self.model.points.get(name)

            self._points[name] = self._point_factory(name, model=point_model)
            LOGGER.debug("Added point '%s' to manager.", name)
            
    def set_point_value(self, name: str, value: Any) -> None:
        """
        API for Client Application to update a point's value.

        Args:
            name: The name of the point.
            value: The new value.
        """
        if name not in self._points:
            self.add_point(name)
            
        point = self._points[name]
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
        super().stop()
        LOGGER.info("PointsetManager stopped.")

    def handle_config(self, config: Config) -> None:
        """
        Handles 'pointset' block of config.
        Updates sample rates and dynamic point configurations.

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

        # Update Etag
        self._state_etag = config.pointset.state_etag

        # Update Points (Dynamic Provisioning)
        new_point_configs = config.pointset.points or {}
        current_point_names = set(self._points.keys())
        new_point_names = set(new_point_configs.keys())

        for name in current_point_names - new_point_names:
            LOGGER.info("Removing point '%s' not present in received config", name)
            del self._points[name]

        for point_name, point_config in new_point_configs.items():
            if point_name not in self._points:
                LOGGER.info("Provisioning new point from config: %s", point_name)
                self.add_point(point_name)

            point = self._points[point_name]
            point.set_config(point_config)

            if self._writeback_handler is not None and point_config.set_value is not None:
                previous_set = self._last_set_values.get(point_name)
                if point_config.set_value != previous_set:
                    self._last_set_values[point_name] = point_config.set_value
                    try:
                        result = self._writeback_handler(point_name, point_config.set_value)
                        if isinstance(result, ValueState):
                            point.value_state = result
                        elif isinstance(result, WritebackResult):
                            point.value_state = result.value_state
                            if result.status:
                                point.status = result.status
                    except Exception as e: # pylint: disable=broad-exception-caught
                        LOGGER.error("Error in writeback handler for %s: %s",
                                    point_name, e)
                        point.value_state = ValueState.failure
                        point.status = Entry(message=str(e), level=500)

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles commands directed at the pointset (currently none).
        """

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
                if point_name not in self._points:
                    continue

                point = self._points[point_name]
                if "present_value" in point_data:
                    point.present_value = point_data["present_value"]
                    restored_count += 1

            LOGGER.info("Restored state for %s points.", restored_count)

        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to load persisted pointset state: %s", e)

    def update_state(self, state: State) -> None:
        """
        Populates state.pointset with status of all managed points.

        Args:
            state: The state object to update.
        """
        points_state_map = {
            name: point.get_state()
            for name, point in self._points.items()
        }

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
            for name, point in self._points.items():
                if point.present_value is not None:
                    data_to_save[name] = {
                        "present_value": point.present_value
                    }

            self._device.persistence.set(self.PERSISTENCE_KEY, data_to_save)
        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to persist pointset state: %s", e)

    def publish_telemetry(self) -> None:
        """
        Generates and publishes a PointsetEvents message containing
        only the points that are 'due' for reporting.

        This method:
        1. Invokes the Poll Callback (if registered) to refresh values.
        2. Iterates over all points to check `should_report()` (COV/Heartbeat).
        3. Constructs a payload of only the reporting points.
        4. Publishes to MQTT via the dispatcher.
        """
        if self._poll_callback:
            try:
                new_values = self._poll_callback()
                if isinstance(new_values, dict):
                    for point_name, value in new_values.items():
                        self.set_point_value(point_name, value)
                else:
                    LOGGER.warning("Poll callback returned non-dict type: %s",
                                   type(new_values))
            except Exception as e: # pylint: disable=broad-exception-caught
                LOGGER.error("Error in telemetry poll callback: %s", e,
                             exc_info=True)

        if not self._points:
            return

        points_map = {}
        for name, point in self._points.items():
            point.update_data()
            if point.should_report(self._sample_rate_sec):
                points_map[name] = point.get_data()
                point.mark_reported()

        if not points_map:
            return

        try:
            event = PointsetEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                points=points_map
            )
            self.publish_event(event, "pointset")
            LOGGER.debug("Published telemetry for %s points.", len(points_map))
        except Exception as e:  # pylint:disable=broad-exception-caught
            LOGGER.error("Failed to publish telemetry: %s", e)
