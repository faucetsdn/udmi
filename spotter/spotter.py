import argparse
import datetime
import logging
import os
import sys
import time
from typing import Any

from udmi.core.factory import create_device
from udmi.core.managers import SystemManager
from udmi.schema import EndpointConfiguration, AuthProvider, Basic, SystemState, StateSystemHardware

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
LOGGER = logging.getLogger("Spotter")

# Hardware definitions for Spotter
SPOTTER_MAKE = "PyUDMI"
SPOTTER_MODEL = "Spotter-v1"
SPOTTER_FIRMWARE_VER = "1.0.0"

class SpotterDevice:
    def __init__(self, endpoint: EndpointConfiguration, persist_path: str = "/tmp/spotter_persist.json"):
        self.endpoint = endpoint
        self.persist_path = persist_path

        # Define static device identity
        self.static_info = SystemState(
            hardware=StateSystemHardware(make=SPOTTER_MAKE, model=SPOTTER_MODEL),
            serial_no="SPOTTER-001",
            software={"firmware": SPOTTER_FIRMWARE_VER}
        )

        self.device = create_device(
            endpoint,
            system_state=self.static_info,
            persistence_path=self.persist_path
        )
        self.sys_manager = self.device.get_manager(SystemManager)

        # Register the OTA blob handler for 'firmware'
        self.sys_manager.register_blob_handler(
            "firmware",
            process=self.process_update,
            post_process=self.apply_update,
            expects_file=False
        )

    def process_update(self, blob_key: str, data: bytes) -> str:
        """
        STAGE 1: PROCESS
        Validates the downloaded payload. Spotter uses a mock strategy:
        The payload is expected to be a simulated binary.
        """
        LOGGER.info(f"STAGE 1: PROCESSING BLOB '{blob_key}' ({len(data)} bytes)")

        # 1. Hardware Mismatch: Reject payload if it contains specific wrong hardware signature
        if b"WRONG_HARDWARE" in data:
            LOGGER.error("Hardware mismatch detected.")
            raise ValueError("Hardware mismatch: Incorrect controller type.")

        # 2. Dependency Mismatch: Validate that new modules are compatible
        if b"WRONG_DEPENDENCY" in data:
            LOGGER.error("Dependency mismatch detected.")
            raise ValueError("Dependency mismatch: Incompatible with local dependencies.")

        # 3. Corrupted Payload: Trap OS-level execution exceptions for malformed binaries
        if b"CORRUPTED_PAYLOAD" in data:
            LOGGER.error("Corrupted payload detected.")
            # Simulating an OS-level execution exception
            raise RuntimeError("OS execution exception: Malformed binary.")

        LOGGER.info("Payload validation passed.")

        # In a real scenario, this might write to a staging path and return it.
        # For our Git-based mock strategy, the payload might contain the new commit hash.
        # Let's extract a mock commit hash from the payload if present, or just use a default.
        mock_commit = "abcd123"
        if b"COMMIT:" in data:
            try:
                # Extract simple COMMIT:<hash> structure
                parts = data.split(b"COMMIT:")
                if len(parts) > 1:
                    mock_commit = parts[1].split()[0].decode("utf-8")
            except Exception:
                pass

        return mock_commit

    def apply_update(self, blob_key: str, mock_commit: Any):
        """
        STAGE 2: POST-PROCESS
        Completes the update by switching the Git commit hash and restarting.
        """
        LOGGER.info("STAGE 2: POST-PROCESS (State has been flushed!)")

        # Log standard required milestones
        LOGGER.info("blobset.apply.success")
        LOGGER.info(f"Switching local Git commit hash to {mock_commit} and pulling new code...")

        # In a real scenario we would update the system state locally if it wasn't restarting,
        # but since it's restarting, the new instance will report the new version.

        LOGGER.warning("INITIATING SYSTEM RESTART...")
        # Simulate successful update and restart
        sys.exit(0)

    def run(self):
        LOGGER.info(f"Spotter running. Waiting for OTA...")
        self.device.run()

def main():
    parser = argparse.ArgumentParser(description="Spotter - UDMI Python Reference Client")
    parser.add_argument("--client_id", required=True, help="MQTT Client ID")
    parser.add_argument("--hostname", required=True, help="MQTT Broker Hostname")
    parser.add_argument("--port", type=int, default=8883, help="MQTT Broker Port")
    parser.add_argument("--topic_prefix", default="", help="MQTT Topic Prefix")
    parser.add_argument("--username", help="MQTT Username")
    parser.add_argument("--password", help="MQTT Password")

    args = parser.parse_args()

    auth_provider = None
    if args.username and args.password:
        auth_provider = AuthProvider(basic=Basic(username=args.username, password=args.password))

    endpoint = EndpointConfiguration(
        client_id=args.client_id,
        hostname=args.hostname,
        port=args.port,
        topic_prefix=args.topic_prefix,
        auth_provider=auth_provider
    )

    spotter = SpotterDevice(endpoint)

    try:
        spotter.run()
    except SystemExit:
        LOGGER.info("Spotter shutdown successfully (Simulated Restart).")
    except KeyboardInterrupt:
        LOGGER.info("Stopped by user.")

if __name__ == "__main__":
    main()
