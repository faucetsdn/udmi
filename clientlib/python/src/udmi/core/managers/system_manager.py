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
import traceback
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

# Command Handler Signature: (payload: dict) -> None
CommandHandler = Callable[[Dict[str, Any]], None]

# Key Rotation Callback Signature: (new_public_key_pem, backup_identifier) -> bool (success)
# The callback should return True if the key was successfully uploaded/processed, False otherwise.
KeyRotationCallback = Callable[[str, str], bool]


class SystemManager(BaseManager):
    """
    Manages the 'system' block of the config and state.
    Also manages 'blobset' for OTA/File updates and Key Rotation orchestration.
    """

    @property
    def model_field_name(self) -> str:
        return "system"

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
        self._blob_handlers: Dict[str, BlobPipelineHandlers] = {}
        self._applied_blob_generations: Dict[str, str] = {}
        self._blob_states: Dict[str, BlobBlobsetState] = {}

        # --- Command Handling Setup ---
        self._command_handlers: Dict[str, CommandHandler] = {
            "rotate_key": self.trigger_key_rotation
        }

        # --- Key Rotation Setup ---
        self._key_rotation_callback: Optional[KeyRotationCallback] = None

        # --- Metrics Loop Setup ---
        self._metrics_rate_sec = DEFAULT_METRICS_RATE_SEC
        self._metrics_wake_event: Optional[threading.Event] = None

        LOGGER.info("SystemManager initialized.")

    def register_blob_handler(self, blob_key: str,
        process: ProcessHandler,
        post_process: Optional[PostProcessHandler] = None,
        expects_file: bool = False) -> None:
        """
        Registers the pipeline handlers for a specific blob key.
        Args:
            process: The pipeline handler to register.
            post_process: The pipeline handler to post_process.
            expects_file: If True, 'process' receives a file path (str).
                          If False, 'process' receives raw bytes.
        """
        self._blob_handlers[blob_key] = BlobPipelineHandlers(
            process=process,
            post_process=post_process,
            expects_file=expects_file
        )
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

    def register_key_rotation_callback(self,
        callback: KeyRotationCallback) -> None:
        """Registers a callback to be invoked during key rotation."""
        self._key_rotation_callback = callback

    def rotate_key(self) -> None:
        """Public API to manually trigger key rotation."""
        self.trigger_key_rotation({})

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
        self._metrics_wake_event = self.start_periodic_task(
            interval_getter=lambda: self._metrics_rate_sec,
            task=self.publish_metrics,
            name="SystemMetrics"
        )
        LOGGER.info("System metrics loop started (rate: %ss).",
                    self._metrics_rate_sec)

    def stop(self) -> None:
        super().stop()
        LOGGER.info("SystemManager stopped.")

    def handle_config(self, config: Config) -> None:
        """
        Handles the 'system' and 'blobset' portions of a new config message.
        """
        if not config:
            return

        if config.timestamp:
            self._last_config_ts = config.timestamp

        if config.system:
            if config.system.min_loglevel is not None:
                # TODO: this could reconfigure the root logger
                LOGGER.info("Config requested min_loglevel: %s",
                            config.system.min_loglevel)

            if config.system.metrics_rate_sec is not None:
                new_rate = config.system.metrics_rate_sec
                if new_rate != self._metrics_rate_sec:
                    LOGGER.info("Updating metrics rate from %s to %s",
                                self._metrics_rate_sec, new_rate)
                    self._metrics_rate_sec = new_rate
                    if self._metrics_wake_event:
                        self._metrics_wake_event.set()

        if config.blobset:
            self._process_blobset_config(config.blobset)

    def _process_blobset_config(self, blobset: BlobsetConfig) -> None:
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
        Initiates the blob application process in a background thread.
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
        LOGGER.info("Spawned worker thread '%s' for blob processing.",
                    thread_name)

    def _run_blob_worker(self, key: str, config: BlobBlobsetConfig) -> None:
        """Background worker to download and process the blob."""
        LOGGER.info("Starting background blob worker for '%s'...", key)
        tmp_fd, tmp_path = tempfile.mkstemp()
        os.close(tmp_fd)

        try:
            LOGGER.info("Streaming blob '%s' to %s...", key, tmp_path)
            get_verified_blob_file(config, tmp_path)
            LOGGER.info("Blob '%s' verified successfully on disk.", key)

            handler = self._blob_handlers[key]
            payload = None

            if handler.expects_file:
                LOGGER.info("Handler expects file path. Passing %s", tmp_path)
                payload = tmp_path
            else:
                LOGGER.info("Handler expects bytes. Loading file into RAM...")
                with open(tmp_path, 'rb') as f:
                    payload = f.read()

            LOGGER.info("Invoking handler for blob '%s'...", key)
            process_output = handler.process(key, payload)

            LOGGER.info("Blob '%s' applied successfully.", key)
            self._applied_blob_generations[key] = config.generation

            LOGGER.info(f"Blob {key} applied. Flushing state...")
            self._update_blob_state(key, Phase.final, config.generation)
            self.trigger_state_update(immediate=True)

            if self._device and self._device.persistence:
                self._device.persistence.set("applied_blobs",
                                             self._applied_blob_generations)

            if handler.post_process:
                LOGGER.info(f"Running post-process for {key}...")
                handler.post_process(key, process_output)

        except Exception as e:  # pylint:disable=broad-exception-caught
            LOGGER.error("Failed to apply blob '%s': %s", key, e)
            LOGGER.debug(traceback.format_exc())
            self._update_blob_state(key, Phase.final, config.generation, str(e))
            self.trigger_state_update()

        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def _update_blob_state(self, key: str, phase: Phase, generation: str,
        error_message: Optional[str] = None) -> None:
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
            LOGGER.warning("Unknown command '%s' received.", command_name)

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

    def _publish_startup_event(self) -> None:
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
        except Exception as e:
            LOGGER.error("Failed to publish startup event: %s", e)

    def _report_status(self, level: int, message: str) -> None:
        self._system_state.status = Entry(
            message=message,
            level=level,
            timestamp=datetime.now(timezone.utc).isoformat()
        )

    def publish_metrics(self) -> None:
        LOGGER.debug("Collecting and publishing system metrics...")
        if psutil is None:
            LOGGER.warning("psutil not installed. Cannot collect metrics.")
            return

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
            LOGGER.info("Published metrics: Total=%.0fMB, Free=%.0fMB",
                        mem_total_mb, mem_free_mb)
        except Exception as e:
            LOGGER.error("Failed to publish metrics: %s", e)

    # --- Key Rotation Logic ---

    def trigger_key_rotation(self,
        _payload: Optional[Dict[str, Any]] = None) -> None:
        """
        Orchestrates the key rotation process:
        1. Backs up current key.
        2. Generates new key (atomic replace).
        3. Invokes callback (to upload new key to cloud).
        4. Reconnects device (using new key).
        5. Reverts if callback fails.
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
                try:
                    backup_identifier = cred_manager.store.backup()
                    LOGGER.info("Backup created: %s", backup_identifier)
                except Exception as e:
                    LOGGER.error("Backup failed: %s", e)
                    raise RuntimeError(
                        "Backup failed, aborting rotation") from e
            else:
                LOGGER.warning(
                    "KeyStore does not support backup. Rotation is risky.")

            new_pem = cred_manager.rotate_credentials(backup=False)
            LOGGER.info("New key pair generated and saved.")

            success = True
            if self._key_rotation_callback:
                LOGGER.info("Invoking rotation callback...")
                try:
                    success = self._key_rotation_callback(new_pem,
                                                          backup_identifier or "unknown")
                except Exception as e:
                    LOGGER.error("Rotation callback exception: %s", e)
                    success = False
            else:
                LOGGER.warning("No key rotation callback registered!")

            if success:
                LOGGER.info("Callback success. Committing rotation.")
                self._report_status(200,
                                    "Key rotation complete. New key active.")

                if self._device and hasattr(self._device,
                                            'request_connection_reset'):
                    LOGGER.info("Triggering connection reset...")
                    self._device.request_connection_reset("Key Rotation")
            else:
                raise RuntimeError("Rotation callback reported failure")

        except Exception as e:
            LOGGER.error("Key rotation sequence failed: %s", e)
            self._report_status(500, f"Key rotation failed: {e}")

            if backup_identifier:
                LOGGER.warning("Attempting to revert to backup key: %s",
                               backup_identifier)
                try:
                    if hasattr(cred_manager.store, 'restore_from_backup'):
                        cred_manager.store.restore_from_backup(backup_path=backup_identifier)
                        LOGGER.info("Successfully reverted to old key.")
                    else:
                        LOGGER.critical("KeyStore cannot restore backup!")
                except Exception as revert_e:
                    LOGGER.critical("FATAL: Failed to revert key: %s", revert_e)
        finally:
            self.trigger_state_update(immediate=True)
