"""
Provides the concrete implementation for the SystemManager.

This manager is responsible for handling the 'system' block of the
config and state, and for reporting system-level events like startup.
It also handles Generic Blobset management (OTA).
"""

import logging
import threading
import traceback
from datetime import datetime
from datetime import timezone
from typing import Any
from typing import Callable
from typing import Dict
from typing import Optional

import psutil

from udmi.constants import UDMI_VERSION
from udmi.core.blob import get_verified_blob_bytes
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import BlobBlobsetConfig
from udmi.schema import BlobBlobsetState
from udmi.schema import BlobsetConfig
from udmi.schema import BlobsetState
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import Metrics
from udmi.schema import State
from udmi.schema import StateSystemHardware
from udmi.schema import StateSystemOperation
from udmi.schema import SystemEvents
from udmi.schema import SystemState
from udmi.schema.common import Phase

LOGGER = logging.getLogger(__name__)

BYTES_PER_MEGABYTE = 1024 * 1024
DEFAULT_METRICS_RATE_SEC = 60

# Blob Handler Signature: (blob_id: str, data: bytes) -> None
BlobHandler = Callable[[str, bytes], None]

# Command Handler Signature: (payload: dict) -> None
CommandHandler = Callable[[Dict[str, Any]], None]

class SystemManager(BaseManager):
    """
    Manages the 'system' block of the config and state.
    Also manages 'blobset' for OTA/File updates.
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

        # --- Blobset Setup ---
        self._blob_handlers: Dict[str, BlobHandler] = {}
        self._applied_blob_generations: Dict[str, str] = {}
        self._blob_states: Dict[str, BlobBlobsetState] = {}

        # --- Command Handling Setup ---
        self._command_handlers: Dict[str, CommandHandler] = {}

        # --- Metrics Loop Setup ---
        self._metrics_rate_sec = DEFAULT_METRICS_RATE_SEC
        self._stop_event = threading.Event()
        self._metrics_thread: Optional[threading.Thread] = None

        LOGGER.info("SystemManager initialized.")

    def register_blob_handler(self, blob_key: str,
        handler: BlobHandler) -> None:
        """
        Registers a callback to handle a specific blob key.
        Args:
            blob_key: The identifier in the blobset (e.g., 'firmware').
            handler: A function that accepts (blob_key, data_bytes).
        """
        self._blob_handlers[blob_key] = handler
        LOGGER.info("Registered handler for blob key: '%s'", blob_key)

    def register_command_handler(self, command_name: str,
        handler: CommandHandler) -> None:
        """
        Registers a callback handler for a specific system command.

        Args:
            command_name: The command name (e.g., 'reboot', 'custom_cmd').
            handler: A callable accepting the command payload dict.
        """
        self._command_handlers[command_name] = handler
        LOGGER.info("Registered handler for system command: '%s'", command_name)

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

                saved_blobs = self._device.persistence.get("applied_blobs", {})
                if saved_blobs:
                    self._applied_blob_generations = saved_blobs
                    LOGGER.info("Restored blob states: %s", saved_blobs.keys())
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
        Handles the 'system' and 'blobset' portions of a new config message.
        """
        if not config:
            return

        if config.timestamp:
            self._last_config_ts = config.timestamp
            LOGGER.debug("Captured config.timestamp: %s", self._last_config_ts)

        if config.system:
            if config.system.min_loglevel is not None:
                LOGGER.info("Setting system min_loglevel to: %s",
                            config.system.min_loglevel)

            if config.system.metrics_rate_sec is not None:
                new_rate = config.system.metrics_rate_sec
                if new_rate != self._metrics_rate_sec:
                    LOGGER.info("Updating metrics rate from %s to %s",
                                self._metrics_rate_sec, new_rate)
                    self._metrics_rate_sec = new_rate

        if config.blobset:
            self._process_blobset_config(config.blobset)

    def _process_blobset_config(self, blobset: BlobsetConfig) -> None:
        """
        Iterates through blobs, checks for updates, and invokes handlers.
        """
        if not blobset.blobs:
            return

        for key, blob_config in blobset.blobs.items():
            if key not in self._blob_handlers:
                continue

            current_gen = self._applied_blob_generations.get(key)
            if blob_config.generation and blob_config.generation == current_gen:
                continue

            LOGGER.info("New generation detected for blob '%s': %s",
                        key, blob_config.generation)

            self._apply_blob(key, blob_config)

    def _apply_blob(self, key: str, config: BlobBlobsetConfig) -> None:
        """
        Downloads, verifies, and hands off the blob.
        """
        self._update_blob_state(key, Phase.apply, config.generation)

        # Force a state publish via dirty bit
        # For now, the next loop cycle will pick up the state change.

        try:
            LOGGER.info("Fetching blob '%s' from %s...", key, config.url)
            data = get_verified_blob_bytes(config)
            LOGGER.info("Blob '%s' verified successfully.", key)

            handler = self._blob_handlers[key]
            LOGGER.info("Invoking handler for blob '%s'...", key)
            handler(key, data)

            LOGGER.info("Blob '%s' applied successfully.", key)
            self._applied_blob_generations[key] = config.generation

            self._update_blob_state(key, Phase.final, config.generation)

            if self._device and self._device.persistence:
                self._device.persistence.set("applied_blobs",
                                             self._applied_blob_generations)

        except Exception as e:  # pylint:disable=broad-exception-caught
            LOGGER.error("Failed to apply blob '%s': %s", key, e)
            LOGGER.debug(traceback.format_exc())
            self._update_blob_state(key, Phase.final, config.generation, str(e))

    def _update_blob_state(self, key: str, phase: Phase, generation: str,
        error_message: str = None) -> None:
        """
        Helper to update the internal state map for blobs.
        """
        status_entry = None
        if error_message:
            status_entry = Entry(
                message=error_message,
                level=500,
                timestamp=datetime.now(timezone.utc).isoformat()
            )

        self._blob_states[key] = BlobBlobsetState(
            phase=phase,
            status=status_entry,
            generation=generation
        )

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handles 'system' related commands by delegating to registered handlers.
        """
        LOGGER.info("SystemManager received command: %s", command_name)

        handler = self._command_handlers.get(command_name)
        if handler:
            try:
                handler(payload)
            except Exception as e:  # pylint: disable=broad-exception-caught
                LOGGER.error("Error executing handler for command '%s': %s",
                             command_name, e)
                LOGGER.debug(traceback.format_exc())
        else:
            LOGGER.warning("Unknown command '%s' received. No handler registered.",
                           command_name)


    def update_state(self, state: State) -> None:
        """
        Contributes system and blobset blocks to the state.
        """
        self._system_state.last_config = self._last_config_ts
        if self._system_state.operation is None:
            self._system_state.operation = StateSystemOperation()
        self._system_state.operation.operational = True
        self._system_state.operation.restart_count = self._restart_count

        state.system = self._system_state

        if self._blob_states:
            state.blobset = BlobsetState(blobs=self._blob_states)

        LOGGER.debug("Populated state.system and state.blobset blocks.")

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
        Gathers system metrics and publishes a SystemEvent.
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
