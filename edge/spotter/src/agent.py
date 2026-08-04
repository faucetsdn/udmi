import argparse
import base64
import hashlib
import json
import logging
import os
import sys
import time
import threading
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from udmi.constants import UDMI_VERSION
from udmi.core.factory import create_device, ClientConfig
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.core.managers import SystemManager
from udmi.core.managers.base_manager import BaseManager
from udmi.schema import (
    AuthProvider, Basic, EndpointConfiguration, Protocol,
    Config, State, DiscoveryState, FamilyDiscoveryState,
    StreamEvents, Entry
)
from udmi.schema.state_discovery_family import Phase as DiscoveryPhase
from udmi.schema.common import Depth

LOGGER = logging.getLogger("spotter_agent")

class TraceDiscoveryManager(BaseManager):
    """Manages TRACE-level diagnostic discovery operations (e.g. PCAP network capturing).

    In Spotter's dual-process co-existence runtime, this manager selectively processes only
    discovery families configured with depth == Depth.trace, ignoring standard BACnet/IP scans
    to prevent contention with the legacy discovery daemon.
    """

    @property
    def model_field_name(self) -> str:
        return "discovery"

    def __init__(self) -> None:
        super().__init__()
        self._discovery_state = DiscoveryState(families={})
        self._active_threads: Dict[str, threading.Thread] = {}
        LOGGER.info("TraceDiscoveryManager initialized.")

    def update_state(self, state: State) -> None:
        if self._discovery_state.families:
            state.discovery = self._discovery_state

    def handle_command(self, command_name: str, payload: dict) -> None:
        pass

    def handle_config(self, config: Config) -> None:
        if not config.discovery or not config.discovery.families:
            return

        for family, fam_config in config.discovery.families.items():
            depth_val = getattr(fam_config, "depth", None)
            # In dual-process mode, ONLY process TRACE operations
            if depth_val != Depth.trace and str(depth_val) != "trace" and depth_val != "Depth.trace":
                LOGGER.debug("Ignoring non-trace discovery family '%s' (handled by legacy discovery node).", family)
                continue

            current_state = self._discovery_state.families.get(family)
            if current_state and current_state.generation == fam_config.generation and current_state.phase != DiscoveryPhase.stopped:
                LOGGER.debug("Already processing TRACE generation '%s' for family '%s'", fam_config.generation, family)
                continue

            LOGGER.info("Starting TRACE discovery operation for family '%s' (generation: %s)", family, fam_config.generation)
            self._start_trace_capture(family, fam_config)

    def _start_trace_capture(self, family: str, fam_config: Any) -> None:
        f_state = FamilyDiscoveryState(
            generation=fam_config.generation,
            phase=DiscoveryPhase.active,
            status=Entry(
                category="discovery.family",
                level=200,
                message=f"Starting trace capture for family '{family}'..."
            )
        )
        self._discovery_state.families[family] = f_state
        self.trigger_state_update()

        thread = threading.Thread(
            target=self._run_trace_worker,
            args=(family, fam_config),
            name=f"TraceCapture-{family}",
            daemon=True
        )
        self._active_threads[family] = thread
        thread.start()

    def _run_trace_worker(self, family: str, fam_config: Any) -> None:
        try:
            from pcap import capture_packets
        except ImportError:
            from edge.spotter.src.pcap import capture_packets

        interface = getattr(fam_config, "interface", None) or "any"
        filter_str = getattr(fam_config, "filter", None) or ""
        max_duration_sec = int(getattr(fam_config, "scan_duration_sec", None) or 60)
        max_bytes = int(getattr(fam_config, "max_bytes", None) or (10 * 1024 * 1024))

        f_state = self._discovery_state.families[family]
        try:
            LOGGER.info("Spawning capture worker on interface '%s' (filter: '%s', max_duration: %ds, max_bytes: %d)", interface, filter_str, max_duration_sec, max_bytes)
            data_generator = capture_packets(
                interface=interface,
                filter_str=filter_str,
                max_duration_sec=max_duration_sec,
                max_bytes=max_bytes
            )

            captured_chunks = list(data_generator)
            full_data = b"".join(captured_chunks)

            LOGGER.info("Capture complete (%d total bytes captured). Publishing StreamEvents over MQTT...", len(full_data))
            chunk_size = 128 * 1024  # 128KB chunks
            total_bytes = len(full_data)
            total_chunks = (total_bytes + chunk_size - 1) // chunk_size if total_bytes > 0 else 1
            session_id = f"trace-{family}-{int(time.time())}"

            for idx in range(total_chunks):
                start = idx * chunk_size
                end = min(start + chunk_size, total_bytes)
                chunk_data = full_data[start:end]

                b64_data = base64.b64encode(chunk_data).decode()

                chunk_event = StreamEvents(
                    timestamp=datetime.now(timezone.utc).isoformat(),
                    version=UDMI_VERSION,
                    session_id=session_id,
                    event_no=idx,
                    chunk_index=idx,
                    total_chunks=total_chunks,
                    data=b64_data
                )
                self.publish_event(chunk_event, "stream")
                LOGGER.info("Published TRACE stream chunk %d/%d (event_no: %d, %d bytes)", idx + 1, total_chunks, idx, len(chunk_data))

            f_state.phase = DiscoveryPhase.stopped
            f_state.active_count = total_chunks
            f_state.status = Entry(
                category="discovery.family",
                level=200,
                message=f"Trace capture complete. {total_chunks} stream chunks emitted over MQTT."
            )
            LOGGER.info("TRACE discovery completed successfully for family '%s'.", family)

        except Exception as e: # pylint: disable=broad-exception-caught
            LOGGER.error("Trace capture failed for family '%s': %s", family, e, exc_info=True)
            f_state.phase = DiscoveryPhase.stopped
            f_state.status = Entry(
                category="discovery.family",
                level=500,
                message=str(e)
            )
        finally:
            if family in self._active_threads:
                del self._active_threads[family]
            self.trigger_state_update()

def calculate_local_password(key_file: str) -> str:
    """Calculates password for udmi_local authentication mechanism.
    
    This is based on the first 8 characters of the sha256 hash of the pkcs8 private key.
    """
    pkcs_file = f"{key_file.rpartition('.')[0]}.pkcs8"
    if not os.path.exists(pkcs_file):
        # Fallback to key_file if pkcs8 file does not exist
        pkcs_file = key_file
    with open(pkcs_file, 'rb') as f:
        key_bytes = f.read()
        h = hashlib.sha256(key_bytes).hexdigest()
        return h[:8]

def build_endpoint_config(config: Dict[str, Any]) -> EndpointConfiguration:
    mqtt_config = config.get("mqtt", {})
    device_id = mqtt_config.get("device_id")
    spotter_device_id = mqtt_config.get("spotter_device_id") or (f"{device_id}-spotter" if device_id else None)
    registry_id = mqtt_config.get("registry_id")
    host = mqtt_config.get("host", "localhost")
    port = int(mqtt_config.get("port", 8883))
    algorithm = mqtt_config.get("algorithm", "RS256")
    auth_mechanism = mqtt_config.get("authentication_mechanism", "jwt_gcp")
    key_file = mqtt_config.get("key_file")
    cert_file = mqtt_config.get("cert_file")
    ca_file = mqtt_config.get("ca_file")

    if auth_mechanism == "jwt_gcp":
        topic_prefix = f"/devices/"
        client_id = f"projects/{mqtt_config.get('project_id')}/locations/{mqtt_config.get('region')}/registries/{registry_id}/devices/{spotter_device_id}"
    else:
        topic_prefix = f"/r/{registry_id}/d/"
        client_id = f"/r/{registry_id}/d/{spotter_device_id}"
        client_id = mqtt_config.get("spotter_client_id", client_id)

    auth_provider = None
    if auth_mechanism == "udmi_local":
        username = f"/r/{registry_id}/d/{spotter_device_id}"
        password = calculate_local_password(key_file)
        auth_provider = AuthProvider(
            basic=Basic(
                username=username,
                password=password
            )
        )
    elif auth_mechanism in ("jwt_gcp", "jwt"):
        from udmi.schema import Jwt
        auth_provider = AuthProvider(
            jwt=Jwt(
                audience=mqtt_config.get("project_id")
            )
        )

    return EndpointConfiguration(
        client_id=client_id,
        hostname=host,
        port=port,
        topic_prefix=topic_prefix,
        algorithm=algorithm,
        auth_provider=auth_provider,
        ca_file=ca_file,
        cert_file=cert_file,
        key_file=key_file,
        protocol=Protocol.mqtt
    )

def main():
    parser = argparse.ArgumentParser(description="Start Spotter Core Agent")
    parser.add_argument(
        "--config_file",
        type=str,
        help="path to config file",
        required=True
    )
    args = parser.parse_args()

    # Read config
    with open(args.config_file, "r") as f:
        config = json.load(f)

    # Setup logging
    log_level_str = str(config.get("log_level", "INFO")).upper()
    log_level = getattr(logging, log_level_str, logging.INFO)
    
    log_dir = "/var/log/spotter"
    if os.path.exists(log_dir) and os.access(log_dir, os.W_OK):
        log_file = os.path.join(log_dir, "agent.log")
        handler = logging.FileHandler(log_file)
    else:
        handler = logging.StreamHandler(sys.stdout)
        
    logging.basicConfig(
        format="%(asctime)s|%(levelname)s|%(module)s:%(funcName)s %(message)s",
        handlers=[handler],
        level=log_level,
    )
    LOGGER.setLevel(log_level)

    LOGGER.info("Starting Spotter Core Agent...")
    endpoint_config = build_endpoint_config(config)
    LOGGER.info("Endpoint Config: %s", endpoint_config)

    key_file = config.get("mqtt", {}).get("key_file")
    ca_file = config.get("mqtt", {}).get("ca_file")
    cert_file = config.get("mqtt", {}).get("cert_file")
    insecure_tls = config.get("mqtt", {}).get("authentication_mechanism") == "udmi_local"
    tls_config = TlsConfig(
        ca_certs=ca_file,
        cert_file=cert_file,
        key_file=key_file,
        insecure=insecure_tls
    )
    client_config = ClientConfig(tls_config=tls_config)
    
    device = create_device(
        endpoint_config,
        managers=[SystemManager(), TraceDiscoveryManager()],
        client_config=client_config,
        key_file=key_file
    )

    sys_mgr = device.get_manager(SystemManager)
    if sys_mgr:
        sys_mgr.register_blob_handler("ota_package", process_ota_package, post_process_ota, expects_file=True)
        sys_mgr.register_blob_handler("discovery_rules", process_discovery_rules, expects_file=True)
        LOGGER.info("Registered OTA package and discovery rules blob handlers.")

    LOGGER.info("Device created. Running...")
    device.run()

def process_ota_package(key: str, filepath: str) -> str:
    """Stages an OTA package blob (.whl or bundle) for supervisor self-testing and promotion."""
    if not os.path.exists(filepath) or os.path.getsize(filepath) == 0:
        raise ValueError(f"Invalid or empty OTA package file for blob '{key}'")
    staging_dir = os.environ.get("SPOTTER_STAGING_DIR", "/tmp/spotter_staging")
    os.makedirs(staging_dir, exist_ok=True)
    staged_file = os.path.join(staging_dir, os.path.basename(filepath))
    with open(filepath, "rb") as src, open(staged_file, "wb") as dst:
        dst.write(src.read())
    marker_file = os.path.join(staging_dir, "OTA_STAGED")
    with open(marker_file, "w") as f:
        f.write(staged_file)
    LOGGER.info("OTA package blob '%s' staged at '%s'. Requesting supervisor restart cycle...", key, staged_file)
    return "staged"

def post_process_ota(key: str, output: Any) -> None:
    """Triggers an agent restart with code 42 after final state has been published."""
    LOGGER.warning("OTA package staged (%s). Triggering exit code 42 in 1s for supervisor sandbox verification...", key)
    def delayed_exit():
        time.sleep(1.0)
        os._exit(42)
    threading.Thread(target=delayed_exit, name="OTARestart", daemon=True).start()

def process_discovery_rules(key: str, filepath: str) -> str:
    """Dynamically hot-reloads discovery signature rules without dropping broker connections."""
    if not os.path.exists(filepath):
        raise ValueError(f"Missing discovery rules file for blob '{key}'")
    with open(filepath, "r", encoding="utf-8") as f:
        rules = json.load(f)
    LOGGER.info("Hot-reloaded discovery rules from blob '%s': %s", key, rules)
    return "reloaded"

if __name__ == "__main__":
    main()
