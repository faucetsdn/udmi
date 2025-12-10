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
from typing import Optional

from udmi.constants import UDMI_VERSION
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import Config
from udmi.schema import PointPointsetConfig
from udmi.schema import PointPointsetEvents
from udmi.schema import PointPointsetState
from udmi.schema import PointsetEvents
from udmi.schema import PointsetState
from udmi.schema import State

LOGGER = logging.getLogger(__name__)

DEFAULT_SAMPLE_RATE_SEC = 10

# Callback signature: function(point_name, value)
WritebackHandler = Callable[[str, Any], None]


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
        self.status: Optional[Any] = None
        self.value_state: Optional[str] = None

        # Writeback
        self.set_value: Any = None

        # Reporting Configuration
        self.cov_increment: Optional[float] = None

        # Reporting State
        self.last_reported_value: Any = None
        self.last_reported_time: float = 0.0

    def set_present_value(self, value: Any) -> None:
        """Updates the current reading of the point."""
        self.present_value = value

    def update_config(self, config: PointPointsetConfig) -> bool:
        """
        Updates point metadata based on config.
        Returns True if a writeback (set_present_value) occurred.
        """
        dirty = False

        if config.units:
            self.units = config.units
        if config.cov_increment is not None:
            self.cov_increment = config.cov_increment

        if config.set_value is not None:
            if config.set_value != self.set_value:
                self.set_value = config.set_value
                dirty = True

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

    def should_report(self) -> bool:
        """
        Determines if this point needs to be reported based on Change of Value (COV).

        Returns: True if the point should be included in the next telemetry event.
        """
        if self.present_value is None:
            return False

        if self.last_reported_value is None:
            return True

        if self.present_value != self.last_reported_value:
            if (isinstance(self.present_value, (int, float)) and
                    isinstance(self.last_reported_value, (int, float)) and
                    self.cov_increment):

                delta = abs(self.present_value - self.last_reported_value)
                LOGGER.debug(f"delta: {delta} for present {self.present_value} and last reported {self.last_reported_value}")
                if delta >= self.cov_increment:
                    return True
                return False

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

    def __init__(self, sample_rate_sec: int = DEFAULT_SAMPLE_RATE_SEC):
        super().__init__()
        self._points: Dict[str, Point] = {}
        self._sample_rate_sec = sample_rate_sec
        self._state_etag: Optional[str] = None

        self._writeback_handler: Optional[WritebackHandler] = None

        # Telemetry Loop Controls
        self._stop_event = threading.Event()
        self._telemetry_thread: Optional[threading.Thread] = None

        LOGGER.info("PointsetManager initialized.")

    def set_writeback_handler(self, handler: WritebackHandler) -> None:
        """
        Registers a callback that is triggered when the cloud sends a 'set_value'.
        Args:
            handler: function taking (point_name, value)
        """
        self._writeback_handler = handler

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
        self._stop_event.clear()
        self._telemetry_thread = threading.Thread(target=self._telemetry_loop,
                                                  name="PointsetTelemetryThread",
                                                  daemon=True)
        self._telemetry_thread.start()

    def stop(self) -> None:
        """
        Stops the background telemetry loop.
        """
        LOGGER.info("Stopping PointsetManager...")
        self._stop_event.set()
        if self._telemetry_thread and self._telemetry_thread.is_alive():
            self._telemetry_thread.join(timeout=2.0)
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

        # Update Etag
        self._state_etag = config.pointset.state_etag

        # Update Points (Dynamic Provisioning)
        if config.pointset.points:
            for point_name, point_config in config.pointset.points.items():
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

    def _telemetry_loop(self) -> None:
        """
        Background loop to publish telemetry events.
        It runs frequently (every 1s) to check if any points need reporting
        based on their individual rules (COV), while ensuring a fallback heartbeat.
        """
        while not self._stop_event.is_set():
            start_time = time.time()
            self.publish_telemetry()

            elapsed = time.time() - start_time
            sleep_time = min(float(self._sample_rate_sec), 1.0)
            sleep_time = max(0.1, sleep_time - elapsed)
            self._stop_event.wait(timeout=sleep_time)

    def publish_telemetry(self) -> None:
        """
        Generates and publishes a PointsetEvents message containing
        only the points that are 'due' for reporting.
        """
        if not self._points:
            return

        points_map = {}
        for name, point in self._points.items():
            if point.present_value is None:
                continue

            is_global_heartbeat = (time.time() - point.last_reported_time) >= self._sample_rate_sec

            if point.should_report() or is_global_heartbeat:
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
