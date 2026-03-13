"""
Provides the concrete implementation for the SystemManager.

This manager is responsible for handling the 'system' block of the
config and state, and for reporting system-level events like startup.
It also handles Generic Blobset management (OTA) and Key Rotation.
"""

import logging
import os
import tempfile
import threading
from datetime import datetime
from datetime import timezone
from typing import Any
from typing import Callable
from typing import Dict
from typing import Optional

try:
    import psutil
except ImportError:
    psutil = None

from udmi.constants import UDMI_VERSION
from udmi.core.blob import get_verified_blob_file
from udmi.core.blob.handlers import BlobPipelineHandlers
from udmi.core.blob.handlers import PostProcessHandler
from udmi.core.blob.handlers import ProcessHandler
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import BlobBlobsetConfig
from udmi.schema import BlobBlobsetState
from udmi.schema import BlobsetConfig
from udmi.schema import BlobsetState
from udmi.schema import Config
from udmi.schema import Entry
from udmi.schema import Metadata
from udmi.schema import Metrics
from udmi.schema import Mode
from udmi.schema import State
from udmi.schema import StateSystemHardware
from udmi.schema import StateSystemOperation
from udmi.schema import SystemConfig
from udmi.schema import SystemEvents
from udmi.schema import SystemState
from udmi.schema.common import Phase
from udmi.schema.config_system import Operation

LOGGER = logging.getLogger(__name__)

BYTES_PER_MEGABYTE = 1024 * 1024
DEFAULT_METRICS_RATE_SEC = 60

# Command Handler Signature: (payload: dict) -> None
CommandHandler = Callable[[Dict[str, Any]], None]

# Key Rotation Callback Signature: (new_public_key_pem, backup_identifier) -> bool (success)
KeyRotationCallback = Callable[[str, str], bool]

# Lifecycle Handler Signature: () -> None
LifecycleHandler = Callable[[], None]


class SystemManager(BaseManager):
    """
    Manages the 'system' block of the config and state.
    Also manages 'blobset' for OTA/File updates and Key Rotation orchestration.
    """

    # pylint: disable=too-many-instance-attributes

    @property
    def model_field_name(self) -> str:
        return "system"

    def __init__(self, system_state: Optional[SystemState] = None):
        """
        Initializes the SystemManager.

        Args:
            system_state: A pre-populated SystemState object containing static
                          device info (hardware, software, serial_no, etc.).
        """
        super().__init__()
        self._last_config_ts: Optional[str] = None
        self._start_time = datetime.now(timezone.utc)

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
        self._blob_handlers: Dict[str, BlobPipelineHandlers] = {}
        self._applied_blob_generations: Dict[str, str] = {}
        self._blob_states: Dict[str, BlobBlobsetState] = {}

        # --- Command Handling Setup ---
        self._command_handlers: Dict[str, CommandHandler] = {
            "rotate_key": self.trigger_key_rotation
        }

        # --- Callback Hooks ---
        self._key_rotation_callback: Optional[KeyRotationCallback] = None
        self._restart_handler: Optional[LifecycleHandler] = None
        self._shutdown_handler: Optional[LifecycleHandler] = None

        # --- Metrics Loop Setup ---
        self._metrics_rate_sec = DEFAULT_METRICS_RATE_SEC
        self._metrics_wake_event: Optional[threading.Event] = None

        LOGGER.info("SystemManager initialized.")

    # --- Configuration Handling ---

    def handle_config(self, config: Config) -> None:
        """
        Handles the 'system' and 'blobset' portions of a new config message.

        Args:
            config: The full device configuration.
        """
        if not config:
            return

        if config.timestamp:
            self._last_config_ts = config.timestamp

        if config.system:
            self._handle_system_config(config.system)

        if config.blobset:
            self._process_blobset_config(config.blobset)

    def _handle_system_config(self, system_config: SystemConfig) -> None:
        """
        Applies system-level configuration updates.

        Updates log levels, metrics reporting rates, and handles system
        operations like reboot or shutdown.
        """
        # Log Level
        if system_config.min_loglevel is not None:
            self._update_log_level(system_config.min_loglevel)

        # Metrics Rate
        if system_config.metrics_rate_sec is not None:
            self._update_metrics_rate(system_config.metrics_rate_sec)

        # Operations (Reboot/Shutdown)
        if system_config.operation:
            self._handle_operation_config(system_config.operation)

    def _update_log_level(self, min_loglevel: int) -> None:
        """
        Updates the root logger level based on config.

        Args:
            min_loglevel: The UDMI log level (e.g., 500=Error, 200=Info).
        """
        # Map UDMI levels (100-500) to Python levels (10-50)
        python_level = max(10, min_loglevel // 10)
        current_level = logging.getLogger().getEffectiveLevel()

        if python_level != current_level:
            LOGGER.info("Updating log level from %s to %s", current_level, python_level)
            logging.getLogger().setLevel(python_level)

    def _update_metrics_rate(self, new_rate: int) -> None:
        """
        Updates the rate at which system metrics are published.

        Args:
            new_rate: The interval in seconds.
        """
        if new_rate != self._metrics_rate_sec:
            LOGGER.info("Updating metrics rate from %s to %s",
                        self._metrics_rate_sec, new_rate)
            self._metrics_rate_sec = new_rate
            if self._metrics_wake_event:
                self._metrics_wake_event.set()

    def _handle_operation_config(self, operation: Operation) -> None:
        """
        Handles system operation requests (Restart, Shutdown, Start Time Sync).

        Args:
            operation: The system operation configuration object.
        """
        # Mode Check (Active vs Restart/Shutdown)
        if operation.mode:
            current_mode = self._system_state.operation.mode
            LOGGER.info("System Operation Mode: Config=%s, State=%s",
                        operation.mode, current_mode)

            if operation.mode == Mode.restart:
                LOGGER.warning("Cloud requested System RESTART.")
                self._perform_system_restart()
                return

            if operation.mode == Mode.shutdown:
                LOGGER.warning("Cloud requested System SHUTDOWN.")
                self._perform_system_shutdown()
                return

            if operation.mode == Mode.active:
                self._system_state.operation.mode = Mode.active

        if operation.last_start:
            last_start_str = operation.last_start

            if isinstance(last_start_str, str):
                if last_start_str.endswith('Z'):
                    last_start_str = last_start_str[:-1] + '+00:00'
                config_start_dt = datetime.fromisoformat(last_start_str)
            else:
                config_start_dt = operation.last_start

            if config_start_dt.tzinfo is None:
                config_start_dt = config_start_dt.replace(tzinfo=timezone.utc)

            if self._start_time < config_start_dt:
                LOGGER.error(
                    "Device start time (%s) is older than config last_start (%s). "
                    "Forcing restart to resync.",
                    self._start_time, config_start_dt
                )
                self._perform_system_restart()

    def _perform_system_restart(self) -> None:
        """
        Executes a system restart via the registered callback.
        """
        LOGGER.critical("Initiating System Restart Sequence...")
        self.trigger_state_update(immediate=True)

        if self._restart_handler:
            try:
                self._restart_handler()
            except Exception as e: # pylint: disable=broad-exception-caught
                LOGGER.error("Error executing restart handler: %s", e)
        else:
            LOGGER.warning("No restart handler registered. Ignoring restart request.")

    def _perform_system_shutdown(self) -> None:
        """
        Executes a system shutdown via the registered callback.
        """
        LOGGER.critical("Initiating System Shutdown Sequence...")
        self.trigger_state_update(immediate=True)

        if self._shutdown_handler:
            try:
                self._shutdown_handler()
            except Exception as e: # pylint: disable=broad-exception-caught
                LOGGER.error("Error executing shutdown handler: %s", e)
        else:
            LOGGER.warning("No shutdown handler registered. Ignoring shutdown request.")

    # --- Lifecycle Callback Registration ---

    def register_restart_handler(self, handler: LifecycleHandler) -> None:
        """
        Registers a callback for system restart requests.

        Args:
            handler: Function to call to reboot the physical device.
        """
        self._restart_handler = handler
        LOGGER.info("Registered system restart handler.")

    def register_shutdown_handler(self, handler: LifecycleHandler) -> None:
        """
        Registers a callback for system shutdown requests.

        Args:
            handler: Function to call to shutdown the physical device.
        """
        self._shutdown_handler = handler
        LOGGER.info("Registered system shutdown handler.")

    # --- Metadata Handling ---

    def set_model(self, model: Metadata) -> None:
        """
        Applies Metadata to System State (Hardware/Software info).

        Args:
            model: The device metadata.
        """
        super().set_model(model)
        if not self.model:
            return

        if hasattr(self.model, 'system') and self.model.system:
            sys_meta = self.model.system

            # Update Hardware Info
            if sys_meta.hardware:
                self._system_state.hardware.make = sys_meta.hardware.make
                self._system_state.hardware.model = sys_meta.hardware.model

            # Update Software Info
            if sys_meta.software:
                self._system_state.software = sys_meta.software

    # --- Blobset Logic ---

    def register_blob_handler(self, blob_key: str,
                              process: ProcessHandler,
                              post_process: Optional[PostProcessHandler] = None,
                              expects_file: bool = False) -> None:
        """
        Registers a handler for a specific blob ID (e.g., 'firmware').

        Args:
            blob_key: The ID of the blob (e.g., 'firmware', 'credentials').
            process: The function to process the blob data.
            post_process: Optional function to run after successful processing.
            expects_file: If True, 'process' receives a file path instead of bytes.
        """
        self._blob_handlers[blob_key] = BlobPipelineHandlers(
            process=process,
            post_process=post_process,
            expects_file=expects_file
        )
        LOGGER.info("Registered handler for blob key: '%s'", blob_key)

    def _process_blobset_config(self, blobset: BlobsetConfig) -> None:
        """
        Iterates through blobset config and triggers updates for new generations.
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
        Initiates the background worker to fetch and apply a specific blob.
        """
        self._update_blob_state(key, Phase.apply, config.generation)
        self.trigger_state_update()

        thread_name = f"BlobWorker-{key}"
        worker_thread = threading.Thread(
            target=self._run_blob_worker,
            args=(key, config),
            name=thread_name,
            daemon=True
        )
        worker_thread.start()

    def _run_blob_worker(self, key: str, config: BlobBlobsetConfig) -> None:
        """
        Worker thread that downloads, verifies, and applies the blob.

        Args:
            key: The blob identifier.
            config: The configuration for this specific blob.
        """
        LOGGER.info("Starting background blob worker for '%s'...", key)
        tmp_fd, tmp_path = tempfile.mkstemp()
        os.close(tmp_fd)

        try:
            LOGGER.info("Streaming blob '%s' to %s...", key, tmp_path)
            get_verified_blob_file(config, tmp_path)

            handler = self._blob_handlers[key]
            payload = None

            if handler.expects_file:
                payload = tmp_path
            else:
                with open(tmp_path, 'rb') as f:
                    payload = f.read()

            LOGGER.info("Invoking handler for blob '%s'...", key)
            process_output = handler.process(key, payload)

            LOGGER.info("Blob '%s' applied successfully.", key)
            self._applied_blob_generations[key] = config.generation
            self._update_blob_state(key, Phase.final, config.generation)
            self.trigger_state_update(immediate=True)

            if self._device and self._device.persistence:
                self._device.persistence.set("applied_blobs",
                                             self._applied_blob_generations)

            if handler.post_process:
                handler.post_process(key, process_output)

        except Exception as e:  # pylint:disable=broad-exception-caught
            LOGGER.error("Failed to apply blob '%s': %s", key, e)
            self._update_blob_state(key, Phase.final, config.generation, str(e))
            self.trigger_state_update()

        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def _update_blob_state(self, key: str, phase: Phase, generation: str,
                           error_message: Optional[str] = None) -> None:
        """
        Updates the internal state for a specific blob and prepares it for reporting.
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

    # --- Standard Manager Methods ---

    def register_command_handler(self, command_name: str, handler: CommandHandler) -> None:
        """Registers a handler for a custom device command."""
        self._command_handlers[command_name] = handler

    def register_key_rotation_callback(self, callback: KeyRotationCallback) -> None:
        """Registers the callback that handles the physical key rotation."""
        self._key_rotation_callback = callback

    def rotate_key(self) -> None:
        """Manually triggers key rotation (convenience method)."""
        self.trigger_key_rotation({})

    def start(self) -> None:
        """
        Starts the SystemManager.

        Restores persistent state (restart counts, applied blobs), sends the
        startup event, and begins the metrics reporting loop.
        """
        try:
            if self._device and self._device.persistence:
                saved_count = self._device.persistence.get("restart_count", 0)
                self._restart_count = saved_count + 1
                self._device.persistence.set("restart_count", self._restart_count)

                saved_blobs = self._device.persistence.get("applied_blobs", {})
                if saved_blobs:
                    self._applied_blob_generations = saved_blobs
        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to handle persistence: %s", e)

        self._publish_startup_event()
        self._metrics_wake_event = self.start_periodic_task(
            interval_getter=lambda: self._metrics_rate_sec,
            task=self.publish_metrics,
            name="SystemMetrics"
        )

    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Routes a command to the appropriate registered handler.
        """
        handler = self._command_handlers.get(command_name)
        if handler:
            try:
                handler(payload)
            except Exception as e:  # pylint: disable=broad-exception-caught
                LOGGER.error("Error executing handler for '%s': %s", command_name, e)
        else:
            LOGGER.warning("Unknown command '%s' received.", command_name)

    def update_state(self, state: State) -> None:
        """
        Updates the system and blobset blocks in the state object.
        """
        self._system_state.last_config = self._last_config_ts
        if self._system_state.operation is None:
            self._system_state.operation = StateSystemOperation()
        self._system_state.operation.operational = True
        self._system_state.operation.restart_count = self._restart_count
        self._system_state.operation.last_start = str(self._start_time)

        state.system = self._system_state

        if self._blob_states:
            state.blobset = BlobsetState(blobs=self._blob_states)

    def _publish_startup_event(self) -> None:
        """Publishes the system startup event log."""
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
        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to publish startup event: %s", e)

    def _report_status(self, level: int, message: str) -> None:
        """Updates the system status entry."""
        self._system_state.status = Entry(
            message=message,
            level=level,
            timestamp=datetime.now(timezone.utc).isoformat()
        )

    def publish_metrics(self) -> None:
        """
        Collects system metrics using psutil and publishes them.
        """
        if psutil is None:
            return

        try:
            vm = psutil.virtual_memory()
            mem_total_mb = vm.total / BYTES_PER_MEGABYTE
            mem_free_mb = vm.available / BYTES_PER_MEGABYTE

            metrics = Metrics(
                mem_total_mb=round(mem_total_mb, 2),
                mem_free_mb=round(mem_free_mb, 2),
            )

            system_event = SystemEvents(
                timestamp=datetime.now(timezone.utc).isoformat(),
                version=UDMI_VERSION,
                metrics=metrics
            )
            self.publish_event(system_event, "system")
        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to publish metrics: %s", e)

    def trigger_key_rotation(self, _payload: Optional[Dict[str, Any]] = None) -> None:
        """
        Orchestrates the key rotation process.

        1. Backs up current keys (if supported).
        2. Generates new keys via CredentialManager.
        3. Invokes the callback to upload/notify the cloud.
        4. Requests a connection reset on success.
        """
        cred_manager = getattr(self._device, 'credential_manager', None)
        if not cred_manager:
            LOGGER.error("Cannot rotate key: CredentialManager not available.")
            self._report_status(500,
                                "Key rotation unavailable (No CredentialManager)")
            self.trigger_state_update()
            return

        LOGGER.info("Initiating Key Rotation...")
        self._report_status(200, "Key rotation started")
        self.trigger_state_update()

        backup_identifier = None
        try:
            if hasattr(cred_manager.store, 'backup'):
                backup_identifier = cred_manager.store.backup()

            new_pem = cred_manager.rotate_credentials(backup=False)

            success = True
            if self._key_rotation_callback:
                try:
                    success = self._key_rotation_callback(new_pem, backup_identifier or "")
                except Exception: # pylint: disable=broad-exception-caught
                    success = False

            if success:
                self._report_status(200, "Key rotation complete.")
                if self._device and hasattr(self._device, 'request_connection_reset'):
                    self._device.request_connection_reset("Key Rotation")
            else:
                raise RuntimeError("Rotation callback reported failure")

        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Key rotation failed: %s", e)
            self._report_status(500, f"Key rotation failed: {e}")
            if backup_identifier and hasattr(cred_manager.store, 'restore_from_backup'):
                cred_manager.store.restore_from_backup(backup_identifier)
        finally:
            self.trigger_state_update(immediate=True)
