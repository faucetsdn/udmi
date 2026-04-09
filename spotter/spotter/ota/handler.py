import json
import logging
import os
import subprocess
import sys
from typing import Any, Dict

LOGGER = logging.getLogger(__name__)

class SpotterOTAHandler:
    """
    Handles Over-The-Air (OTA) updates for the Spotter device.
    Uses a Git-based update strategy where the payload specifies the target commit hash.
    It reads a manifest file from the target commit to verify hardware requirements and dependencies.
    """

    def __init__(self, hardware_make: str, hardware_model: str, current_dependencies: Dict[str, str]):
        self.hardware_make = hardware_make
        self.hardware_model = hardware_model
        self.current_dependencies = current_dependencies
        self.staging_file = "/tmp/spotter_update.json"
        self.manifest_file_path = "spotter_manifest.json"

    def process(self, blob_key: str, data: bytes) -> str:
        """
        STAGE 1: PROCESS
        Validates the downloaded payload.
        Expected Payload format: Raw bytes representing the commit hash (e.g., b'abcd123').
        """
        commit_hash = data.decode("utf-8").strip()
        LOGGER.info(f"Processing OTA update for blob '{blob_key}'. Target commit: {commit_hash}")

        if not commit_hash:
            raise ValueError("Payload did not contain a valid commit hash.")

        try:
            # 1. Fetch the latest commits to ensure we have the target hash locally
            LOGGER.info("Fetching from git remote to find target commit...")
            subprocess.run(["git", "fetch", "origin"], check=True, capture_output=True)

            # 2. Extract the manifest file directly from the target commit using git show
            LOGGER.info(f"Extracting {self.manifest_file_path} from commit {commit_hash}...")
            result = subprocess.run(
                ["git", "show", f"{commit_hash}:{self.manifest_file_path}"],
                check=True, capture_output=True, text=True
            )

            manifest = json.loads(result.stdout)

        except subprocess.CalledProcessError as e:
            LOGGER.error(f"Git operation failed. Cannot find commit or manifest: {e.stderr}")
            raise ValueError(f"Git target error: Unable to verify commit {commit_hash} or read manifest.") from e
        except json.JSONDecodeError as e:
            LOGGER.error(f"Corrupted payload (manifest is not valid JSON): {e}")
            raise RuntimeError(f"Corrupted payload: {e}") from e

        # 3. Validate hardware mismatch
        if manifest.get("hardware_make") != self.hardware_make or manifest.get("hardware_model") != self.hardware_model:
            LOGGER.error("Hardware mismatch detected.")
            raise ValueError(f"Hardware mismatch: Expected {self.hardware_make} {self.hardware_model}")

        # 4. Validate dependency mismatch
        target_deps = manifest.get("dependencies", {})
        for dep, req_ver in target_deps.items():
            curr_ver = self.current_dependencies.get(dep)
            if curr_ver != req_ver:
                LOGGER.error(f"Dependency mismatch detected for {dep}. Required {req_ver}, got {curr_ver}.")
                raise ValueError(f"Dependency mismatch: Incompatible with local dependencies ({dep}).")

        LOGGER.info("Payload and target manifest validation passed. Ready for apply.")
        return commit_hash

    def post_process(self, blob_key: str, commit_hash: str):
        """
        STAGE 2: POST-PROCESS
        Applies the update by switching the Git commit hash and restarting the service.
        """
        LOGGER.info("STAGE 2: POST-PROCESS (State has been flushed!)")

        # 1. Standard log for telemetry
        LOGGER.info("blobset.apply.success")

        # 2. Execute Git commands
        LOGGER.info(f"Switching local Git commit hash to {commit_hash}...")
        try:
            subprocess.run(["git", "checkout", commit_hash], check=True, capture_output=True)
            LOGGER.info(f"Successfully checked out {commit_hash}")
        except subprocess.CalledProcessError as e:
            LOGGER.error(f"Git update failed: {e.stderr.decode()}")
            # Revert or handle failure
            return

        # 3. Simulate Restart or actually restart the service via OS
        LOGGER.warning("INITIATING SYSTEM RESTART...")
        sys.exit(0)
