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

LOGGER = logging.getLogger(__name__)

DEFAULT_SAMPLE_RATE_SEC = 10
DEFAULT_HEARTBEAT_SEC = 600

# Callback signature: function(point_name, value)
WritebackHandler = Callable[[str, Any], None]

PollCallback = Callable[[], None]


class Point:
    """
    Represents a single data point within the Pointset.
    Acts as a container for value, configuration, and status.
    Tracks its own reporting state (last reported value/time) for COV logic.
    """

    def __init__(self, name: str):
        self.name = name
        self.present_value: Any = None
        self.units: Optional[str] = None
        self.status: Optional[Entry] = None
        self.value_state: Optional[str] = None

        # Writeback
        self.set_value: Any = None

        # Reporting Configuration
        self.cov_increment: Optional[float] = None

        # Reporting State
        self.last_reported_value: Any = None
        self.last_reported_time: float = 0.0

    def set_model(self, model: PointPointsetModel) -> None:
        """
        Applies static definition from Metadata.
        """
        if model.units:
            self.units = model.units

    def set_present_value(self, value: Any) -> None:
        """Updates the current reading of the point."""
        self.present_value = value
        if self.status and self.status.level >= 500:
            self.status = None

    def update_config(self, config: PointPointsetConfig) -> bool:
        """
        Updates point metadata based on config.
        Returns True if a writeback (set_present_value) occurred.
        """
        if config.units is not None:
            self.units = config.units
        if config.cov_increment is not None:
            self.cov_increment = config.cov_increment

        dirty = False
        if config.set_value is not None:
            self.value_state = None
            self.status = None

            if config.set_value != self.set_value:
                self.set_value = config.set_value
                dirty = True

            self.value_state = "applied"

        return dirty

    def get_state(self) -> PointPointsetState:
        """Returns the state representation of this point."""
        return PointPointsetState(
            status=self.status,
            value_state=self.value_state,
            units=self.units
        )

    def get_event(self) -> PointPointsetEvents:
        """Returns the telemetry event representation of this point."""
        return PointPointsetEvents(
            present_value=self.present_value
        )

    def should_report(self, sample_rate_sec: int) -> bool:
        """
        Determines if this point needs to be reported based on Change of Value (COV).
        Returns: True if the point should be included in the next telemetry event.
        """
        if self.present_value is None:
            return False

        if self.last_reported_value is None:
            return True

        now = time.time()

        if self.present_value != self.last_reported_value:
            if (isinstance(self.present_value, (int, float)) and
                    isinstance(self.last_reported_value, (int, float)) and
                    self.cov_increment):

                delta = abs(self.present_value - self.last_reported_value)
                LOGGER.debug(f"delta: {delta} for present {self.present_value} "
                             f"and last reported {self.last_reported_value}")
                if delta >= self.cov_increment:
                    return True
            else:
                return True

        heartbeat_interval = max(DEFAULT_HEARTBEAT_SEC, sample_rate_sec)

        if (now - self.last_reported_time) >= heartbeat_interval:
            return True

        return False

    def mark_reported(self) -> None:
        """Updates the reporting state after a successful publish."""
        self.last_reported_value = self.present_value
        self.last_reported_time = time.time()
        LOGGER.info(f"last reported value: {self.last_reported_value}, at time {self.last_reported_time}")


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

    def __init__(self, sample_rate_sec: int = DEFAULT_SAMPLE_RATE_SEC):
        super().__init__()
        self._points: Dict[str, Point] = {}
        self._sample_rate_sec = sample_rate_sec
        self._state_etag: Optional[str] = None

        self._writeback_handler: Optional[WritebackHandler] = None

        self._telemetry_wake_event: Optional[threading.Event] = None

        self._poll_callback: Optional[PollCallback] = None

        LOGGER.info("PointsetManager initialized.")

    @property
    def points(self) -> Mapping[str, Point]:
        """
        Returns a read-only view of the currently managed points.
        Client applications can iterate over this to know which points need data.
        """
        return self._points

    def set_model(self, model: Metadata) -> None:
        """
        Applies the Pointset Metadata (Model) to the manager.
        """
        super().set_model(model)
        if not self.model or not self.model.points:
            return

        LOGGER.info("Applying Pointset Metadata Model...")
        for name, point_model in self.model.points.items():
            if name not in self._points:
                self.add_point(name)

            self._points[name].set_model(point_model)

    def set_writeback_handler(self, handler: WritebackHandler) -> None:
        """
        Registers a callback that is triggered when the cloud sends a 'set_value'.
        Args:
            handler: function taking (point_name, value)
        """
        self._writeback_handler = handler

    def set_poll_callback(self, callback: PollCallback) -> None:
        """
        Registers a callback that is invoked immediately before telemetry generation.
        This allows the application to fetch fresh sensor readings just-in-time.
        """
        self._poll_callback = callback
        LOGGER.info("Registered telemetry poll callback.")

    def add_point(self, name: str) -> None:
        """Register a point to be managed."""
        if name not in self._points:
            self._points[name] = Point(name)
            LOGGER.debug("Added point '%s' to manager.", name)

    def set_point_value(self, name: str, value: Any) -> None:
        """
        API for Client Application.
        Updates the value of a point.
        """
        if name not in self._points:
            self.add_point(name)

        self._points[name].set_present_value(value)

    def start(self) -> None:
        """
        Starts the background telemetry reporting loop.
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
        Stops the background telemetry loop.
        """
        super().stop()
        LOGGER.info("PointsetManager stopped.")

    def handle_config(self, config: Config) -> None:
        """
        Handles 'pointset' block of config.
        Updates sample rates and dynamic point configurations.
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
            LOGGER.info("Removing point '%s' not present in received config",
                        name)
            del self._points[name]

        for point_name, point_config in new_point_configs.items():
            if point_name not in self._points:
                LOGGER.info("Provisioning new point from config: %s",
                            point_name)
                self.add_point(point_name)

            point = self._points[point_name]
            is_writeback = point.update_config(point_config)
            if is_writeback and self._writeback_handler is not None:
                try:
                    self._writeback_handler(point_name, point.set_value)
                except Exception as e:
                    LOGGER.error(f"Error in writeback handler for {point_name}: {e}")

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles pointset commands.
        """

    def _load_persisted_state(self) -> None:
        """
        Loads the last known point values from persistence.
        """
        if not self._device or not self._device.persistence:
            LOGGER.warning("Cannot load state: Persistence not available.")
            return

        try:
            saved_state = self._device.persistence.get(self.PERSISTENCE_KEY, {})
            if not saved_state:
                LOGGER.info("No persisted pointset state found.")
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

        except Exception as e:
            LOGGER.error("Failed to load persisted pointset state: %s", e)

    def update_state(self, state: State) -> None:
        """
        Populates state.pointset with status of all managed points.
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
        """
        Saves the current values of all points to persistence.
        """
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

        except Exception as e:
            LOGGER.error("Failed to persist pointset state: %s", e)

    def publish_telemetry(self) -> None:
        """
        Generates and publishes a PointsetEvents message containing
        only the points that are 'due' for reporting.
        """
        if self._poll_callback:
            try:
                self._poll_callback()
            except Exception as e:
                LOGGER.error("Error in telemetry poll callback: %s", e,
                             exc_info=True)

        if not self._points:
            return

        points_map = {}
        for name, point in self._points.items():
            if point.should_report(self._sample_rate_sec):
                points_map[name] = point.get_event()
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
