"""
Provides the concrete implementation for the SystemManager.

This manager is responsible for handling the 'system' block of the
config and state, and for reporting system-level events like startup.
"""

import logging
import threading
from datetime import datetime
from datetime import timezone
from typing import Optional

import psutil
from udmi.constants import UDMI_VERSION
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import Metrics
from udmi.schema import State
from udmi.schema import StateSystemHardware
from udmi.schema import StateSystemOperation
from udmi.schema import SystemEvents
from udmi.schema import SystemState

LOGGER = logging.getLogger(__name__)

BYTES_PER_MEGABYTE = 1024 * 1024
DEFAULT_METRICS_RATE_SEC = 60


class SystemManager(BaseManager):
    """
    Manages the 'system' block of the config and state.
    """

    # pylint: disable=too-many-instance-attributes

    def __init__(self, system_state: Optional[SystemState] = None):
        """
        Initializes the SystemManager.

        Args:
            system_state: A pre-populated SystemState object containing static
                          device info (hardware, software, serial_no, etc.).
        """
        super().__init__()
        self._last_config_ts: Optional[str] = None
        if system_state:
            self._system_state = system_state
        else:
            self._system_state = SystemState(
                hardware=StateSystemHardware(make="pyudmi", model="device-v1"),
                software={"firmware": "1.0.0"}
            )

        # --- Persistence Setup ---
        self._restart_count = 0

        # --- Metrics Loop Setup ---
        self._metrics_rate_sec = DEFAULT_METRICS_RATE_SEC
        self._stop_event = threading.Event()
        self._metrics_thread: Optional[threading.Thread] = None

        LOGGER.info("SystemManager initialized.")

    def start(self) -> None:
        """
        Called when the device starts.
        Increments restart count and publishes startup event.
        """
        try:
            if self._device and self._device.persistence:
                saved_count = self._device.persistence.get("restart_count", 0)
                self._restart_count = saved_count + 1
                self._device.persistence.set("restart_count",
                                             self._restart_count)
                LOGGER.info("Device restart count incremented to: %s",
                            self._restart_count)
            else:
                LOGGER.warning(
                    "Device context not set; cannot manage persistence.")
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to handle persistence: %s", e)

        LOGGER.info("SystemManager starting, publishing system startup event.")
        self._publish_startup_event()
        self._stop_event.clear()
        self._metrics_thread = threading.Thread(target=self._metrics_loop,
                                                name="SystemMetricsThread",
                                                daemon=True)
        self._metrics_thread.start()
        LOGGER.info("System metrics loop started (rate: %ss).",
                    self._metrics_rate_sec)

    def stop(self) -> None:
        """
        Called when the device stops.
        Stops the background metrics thread.
        """
        LOGGER.info("Stopping SystemManager...")
        self._stop_event.set()
        if self._metrics_thread and self._metrics_thread.is_alive():
            self._metrics_thread.join(timeout=2.0)
        LOGGER.info("SystemManager stopped.")

    def handle_config(self, config: Config) -> None:
        """
        Handles the 'system' portion of a new config message.
        """
        if not config:
            return

        if config.timestamp:
            self._last_config_ts = config.timestamp
            LOGGER.debug("Captured config.timestamp: %s", self._last_config_ts)

        if not config.system:
            LOGGER.debug(
                "No 'system' block in config, skipping system-specific config.")
            return

        if config.system.min_loglevel is not None:
            LOGGER.info("Setting system min_loglevel to: %s",
                        config.system.min_loglevel)

        if config.system.metrics_rate_sec is not None:
            new_rate = config.system.metrics_rate_sec
            if new_rate != self._metrics_rate_sec:
                LOGGER.info("Updating metrics rate from %s to %s",
                            self._metrics_rate_sec, new_rate)
                self._metrics_rate_sec = new_rate

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles 'system' related commands.
        (e.g., reboot, shutdown)
        """
        # For now, we don't implement any system commands
        if command_name in ["reboot", "shutdown"]:
            LOGGER.warning("Received '%s' command, but it is not implemented.",
                           command_name)
        # This manager doesn't handle other commands

    def update_state(self, state: State) -> None:
        """
        Contributes the 'system' block to the state message.
        """
        self._system_state.last_config = self._last_config_ts
        if self._system_state.operation is None:
            self._system_state.operation = StateSystemOperation()
        self._system_state.operation.operational = True
        self._system_state.operation.restart_count = self._restart_count

        state.system = self._system_state
        LOGGER.debug("Populated state.system block.")

    def _publish_startup_event(self):
        """Helper to publish the startup event."""
        try:
            log_entry = Entry(
                message="Device has started",
                level=200,
                timestamp=datetime.now(timezone.utc).isoformat()
            )
            startup_event = SystemEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                logentries=[log_entry]
            )
            self.publish_event(startup_event, "system")
        except (TypeError, AttributeError) as e:
            LOGGER.error("Failed to publish startup event: %s", e)

    def _metrics_loop(self) -> None:
        """
        Background loop that publishes metrics periodically.
        """
        while not self._stop_event.is_set():
            self.publish_metrics()
            self._stop_event.wait(timeout=self._metrics_rate_sec)

    def publish_metrics(self) -> None:
        """
        Gathers system metrics and publishes a SystemEvent by populating the
        'metrics' field.
        """
        LOGGER.debug("Collecting and publishing system metrics...")
        try:
            vm = psutil.virtual_memory()

            mem_total_mb = vm.total / BYTES_PER_MEGABYTE
            mem_free_mb = vm.available / BYTES_PER_MEGABYTE

            metrics = Metrics(
                mem_total_mb=round(mem_total_mb, 2),
                mem_free_mb=round(mem_free_mb, 2),
                store_total_mb=None
            )

            system_event = SystemEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                metrics=metrics
            )

            self.publish_event(system_event, "system")
            LOGGER.info(
                "Published metrics: Total=%.0fMB, Free=%.0fMB",
                mem_total_mb,
                mem_free_mb
            )

        except ImportError:
            LOGGER.error("psutil not installed. Cannot collect metrics.")
        except Exception as e:  # pylint:disable=broad-exception-caught
            LOGGER.error("Failed to publish metrics: %s", e)
