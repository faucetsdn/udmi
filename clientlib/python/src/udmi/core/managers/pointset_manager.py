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
from typing import Dict
from typing import Optional
from typing import Any

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


class Point:
    """
    Represents a single data point within the Pointset.
    Acts as a container for value, configuration, and status.
    """

    def __init__(self, name: str):
        self.name = name
        self.present_value: Any = None
        self.units: Optional[str] = None
        self.status: Optional[Any] = None
        self.value_state: Optional[str] = None

    def set_value(self, value: Any) -> None:
        """Updates the current reading of the point."""
        self.present_value = value

    def update_config(self, config: PointPointsetConfig) -> None:
        """
        Updates point metadata based on config.
        In the future (Writeback), this is where set_value logic would go.
        """
        if config.units:
            self.units = config.units

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


class PointsetManager(BaseManager):
    """
    Manages the 'pointset' block.
    Handles configuration of points, reporting of point states, and
    periodic publishing of telemetry events.
    """

    def __init__(self, sample_rate_sec: int = DEFAULT_SAMPLE_RATE_SEC):
        super().__init__()
        self._points: Dict[str, Point] = {}
        self._sample_rate_sec = sample_rate_sec
        self._state_etag: Optional[str] = None

        # Telemetry Loop Controls
        self._stop_event = threading.Event()
        self._telemetry_thread: Optional[threading.Thread] = None

        LOGGER.info("PointsetManager initialized.")

    def add_point(self, name: str) -> None:
        """Register a point to be managed."""
        if name not in self._points:
            self._points[name] = Point(name)
            LOGGER.debug("Added point '%s' to manager.", name)

    def set_point_value(self, name: str, value: Any) -> None:
        """
        API for Client Application.
        Updates the value of a point to be sent in the next telemetry payload.
        """
        if name not in self._points:
            self.add_point(name)

        self._points[name].set_value(value)

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
        Updates sample rates and individual point configurations.
        """
        if not config.pointset:
            return

        # Update Sample Rate
        if config.pointset.sample_rate_sec is not None:
            if config.pointset.sample_rate_sec != self._sample_rate_sec:
                LOGGER.info("Updating sample rate from %s to %s",
                            self._sample_rate_sec,
                            config.pointset.sample_rate_sec)
                self._sample_rate_sec = config.pointset.sample_rate_sec

        # Update Etag
        self._state_etag = config.pointset.state_etag

        # Update Points
        if config.pointset.points:
            for point_name, point_config in config.pointset.points.items():
                if point_name not in self._points:
                    self.add_point(point_name)
                self._points[point_name].update_config(point_config)

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles pointset commands.
        (Future implementation for Writeback/Discovery)
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
        """
        while not self._stop_event.is_set():
            start_time = time.time()
            self.publish_telemetry()

            elapsed = time.time() - start_time
            sleep_time = max(0.1, self._sample_rate_sec - elapsed)
            self._stop_event.wait(timeout=sleep_time)

    def publish_telemetry(self) -> None:
        """
        Generates and publishes a PointsetEvents message.
        """
        if not self._points:
            return

        points_map = {
            name: point.get_event()
            for name, point in self._points.items()
            if point.present_value is not None
        }

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
        except Exception as e:  #pylint:disable=broad-exception-caught
            LOGGER.error("Failed to publish telemetry: %s", e)
