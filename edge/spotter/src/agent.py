"""UDMI Spotter Core Agent.

Unified single-process edge node implementing network discovery,
remote ephemeral packet capture (PCAP), and automated lifecycle management.
"""

import argparse
import hashlib
import json
import logging
import os
import signal
import sys
from typing import Any, Dict

from udmi.core.factory import create_device, ClientConfig
from udmi.core.managers import SystemManager, LocalnetManager
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import (
    AuthProvider,
    Basic,
    EndpointConfiguration,
    Protocol,
)

try:
    from providers.bacnet import BacnetFamilyProvider
    from providers.ether import EtherFamilyProvider
    from providers.passive import PassiveFamilyProvider
    from host_telemetry import get_host_os_info, get_cpu_and_memory_metrics
    from manager.discovery import SpotterDiscoveryManager
except ImportError:
    from edge.spotter.src.providers.bacnet import BacnetFamilyProvider
    from edge.spotter.src.providers.ether import EtherFamilyProvider
    from edge.spotter.src.providers.passive import PassiveFamilyProvider
    from edge.spotter.src.host_telemetry import get_host_os_info, get_cpu_and_memory_metrics
    from edge.spotter.src.manager.discovery import SpotterDiscoveryManager

LOGGER = logging.getLogger("spotter_agent")


def calculate_local_password(key_file: str) -> str:
    """Calculates password for udmi_local authentication mechanism."""
    pkcs_file = f"{key_file.rpartition('.')[0]}.pkcs8"
    if not os.path.exists(pkcs_file):
        pkcs_file = key_file
    with open(pkcs_file, "rb") as f:
        key_bytes = f.read()
        h = hashlib.sha256(key_bytes).hexdigest()
        return h[:8]


def build_endpoint_config(config: Dict[str, Any]) -> EndpointConfiguration:
    """Builds a single UDMI EndpointConfiguration."""
    mqtt_config = config.get("mqtt", {})
    device_id = mqtt_config.get("device_id")
    registry_id = mqtt_config.get("registry_id")
    host = mqtt_config.get("host", "localhost")
    port = int(mqtt_config.get("port", 8883))
    algorithm = mqtt_config.get("algorithm", "RS256")
    auth_mechanism = mqtt_config.get("authentication_mechanism", "jwt_gcp")
    key_file = mqtt_config.get("key_file")
    cert_file = mqtt_config.get("cert_file")
    ca_file = mqtt_config.get("ca_file")

    if auth_mechanism == "jwt_gcp":
        topic_prefix = "/devices/"
        client_id = (
            f"projects/{mqtt_config.get('project_id')}/locations/"
            f"{mqtt_config.get('region')}/registries/{registry_id}/devices/{device_id}"
        )
    else:
        topic_prefix = f"/r/{registry_id}/d/"
        client_id = f"/r/{registry_id}/d/{device_id}"

    if "client_id" in mqtt_config:
        client_id = mqtt_config["client_id"]

    auth_provider = None
    if auth_mechanism == "udmi_local":
        username = f"/r/{registry_id}/d/{device_id}"
        password = calculate_local_password(key_file)
        auth_provider = AuthProvider(
            basic=Basic(
                username=username,
                password=password,
            )
        )
    elif auth_mechanism in ("jwt_gcp", "jwt"):
        from udmi.schema import Jwt

        auth_provider = AuthProvider(jwt=Jwt(audience=mqtt_config.get("project_id")))

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
        protocol=Protocol.mqtt,
    )


def handle_ephemeral_secret_command(payload: Dict[str, Any]) -> None:
    """Handles ephemeral secret/token delivery over the non-persisted commands/secret channel.

    Processes in-flight credentials strictly in memory without disk persistence.
    """
    secret_key = payload.get("key")
    if not secret_key:
        LOGGER.warning("Received commands/secret payload missing 'key' field: %s", payload)
        return
    LOGGER.info("Successfully received ephemeral secret for key '%s' over commands/secret channel.", secret_key)




def main():
    parser = argparse.ArgumentParser(description="Start Spotter Unified Edge Node")
    parser.add_argument("--config_file", type=str, help="path to config file", required=True)
    parser.add_argument("--serial_no", type=str, help="optional serial number", default="NA")
    args = parser.parse_args()

    # Read config
    with open(args.config_file, "r") as f:
        config = json.load(f)

    # Setup logging
    log_level_str = str(config.get("log_level", "INFO")).upper()
    log_level = getattr(logging, log_level_str, logging.INFO)

    handlers = [logging.StreamHandler(sys.stdout)]
    log_dir = "/var/log/spotter"
    if os.path.exists(log_dir) and os.access(log_dir, os.W_OK):
        log_file = os.path.join(log_dir, "agent.log")
        handlers.append(logging.FileHandler(log_file))

    logging.basicConfig(
        format="%(asctime)s|%(levelname)s|%(module)s:%(funcName)s %(message)s",
        handlers=handlers,
        level=log_level,
    )
    LOGGER.setLevel(log_level)

    LOGGER.info("Starting UDMI Spotter Unified Agent (Serial: %s)...", args.serial_no)
    endpoint_config = build_endpoint_config(config)
    LOGGER.info("Endpoint Config: %s", endpoint_config)

    key_file = config.get("mqtt", {}).get("key_file")
    ca_file = config.get("mqtt", {}).get("ca_file")
    cert_file = config.get("mqtt", {}).get("cert_file")
    insecure_tls = config.get("mqtt", {}).get("authentication_mechanism") == "udmi_local"
    tls_config = TlsConfig(
        ca_certs=ca_file, cert_file=cert_file, key_file=key_file, insecure=insecure_tls
    )
    client_config = ClientConfig(tls_config=tls_config)

    # Initialize Managers
    system_manager = SystemManager()
    system_manager.register_command_handler("secret", handle_ephemeral_secret_command)

    # Default metrics reporting rate: 300s (5 minutes) for production, or from config
    metrics_rate_sec = config.get("system", {}).get("metrics_rate_sec", 300)
    system_manager._metrics_rate_sec = metrics_rate_sec

    # Populate Host Telemetry Details
    os_info = get_host_os_info()
    if os_info:
        os_name = os_info.get("PRETTY_NAME") or os_info.get("NAME") or "Linux"
        os_version = os_info.get("VERSION_ID") or os_info.get("VERSION") or "unknown"
        if system_manager._system_state.software is None:
            system_manager._system_state.software = {}
        system_manager._system_state.software["os"] = os_name
        system_manager._system_state.software["os_version"] = os_version

    localnet_manager = LocalnetManager()
    bacnet_cfg = config.get("bacnet", {})
    bacnet_ip = bacnet_cfg.get("ip")
    bacnet_port = bacnet_cfg.get("port")
    localnet_manager.register_provider("bacnet", BacnetFamilyProvider(bacnet_ip=bacnet_ip, bacnet_port=bacnet_port))
    localnet_manager.register_provider("ether", EtherFamilyProvider())
    localnet_manager.register_provider("ipv4", PassiveFamilyProvider())

    discovery_manager = SpotterDiscoveryManager()

    # Create Unified Device
    device = create_device(
        endpoint_config,
        managers=[system_manager, localnet_manager, discovery_manager],
        client_config=client_config,
        key_file=key_file,
    )

    # Handle OS termination signals gracefully
    def handle_signal(signum, frame):
        LOGGER.info("Signal %s received. Shutting down Spotter...", signum)
        device.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    LOGGER.info("Spotter Agent running...")
    device.run()


if __name__ == "__main__":
    main()
