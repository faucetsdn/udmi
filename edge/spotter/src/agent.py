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
import socket
import sys
import time
from typing import Any, Dict, Optional

from udmi.core.factory import ClientConfig, create_device
from udmi.core.managers import LocalnetManager
from udmi.core.messaging.mqtt_messaging_client import TlsConfig
from udmi.schema import (
    AuthProvider,
    Basic,
    EndpointConfiguration,
    Jwt,
    Protocol,
    StateSystemHardware,
    SystemState,
)

try:
  from host_telemetry import (
      get_host_hardware_info,
      get_host_os_info,
  )
  from manager.discovery import SpotterDiscoveryManager
  from manager.system import SpotterSystemManager
  from providers.bacnet import BacnetFamilyProvider
  from providers.ether import EtherFamilyProvider
  from providers.passive import PassiveFamilyProvider
except ImportError:
  from edge.spotter.src.host_telemetry import (
      get_host_hardware_info,
      get_host_os_info,
  )
  from edge.spotter.src.manager.discovery import SpotterDiscoveryManager
  from edge.spotter.src.manager.system import SpotterSystemManager
  from edge.spotter.src.providers.bacnet import BacnetFamilyProvider
  from edge.spotter.src.providers.ether import EtherFamilyProvider
  from edge.spotter.src.providers.passive import PassiveFamilyProvider


LOGGER = logging.getLogger("spotter_agent")


def resolve_config_path(
    base_dir: Optional[str], path: Optional[str]
) -> Optional[str]:
  """Resolves relative config file path against base directory if provided."""
  if path and base_dir and not os.path.isabs(path):
    return os.path.normpath(os.path.join(base_dir, path))
  return path


def calculate_local_password(key_file: str) -> str:
  """Calculates password for udmi_local authentication mechanism."""
  base_path, _, _ = key_file.rpartition(".")
  pkcs_file = f"{base_path}.pkcs8"
  if not os.path.exists(pkcs_file):
    pkcs_file = key_file
  with open(pkcs_file, "rb") as f:
    key_bytes = f.read()
    h = hashlib.sha256(key_bytes).hexdigest()
    return h[:8]


def build_endpoint_config(
    config: Dict[str, Any], base_dir: Optional[str] = None
) -> EndpointConfiguration:
  """Builds a single UDMI EndpointConfiguration."""
  mqtt_config = config.get("mqtt", {})
  device_id = mqtt_config.get("device_id")
  registry_id = mqtt_config.get("registry_id")
  host = mqtt_config.get("host", "localhost")
  port = int(mqtt_config.get("port", 8883))
  algorithm = mqtt_config.get("algorithm", "RS256")
  auth_mechanism = mqtt_config.get("authentication_mechanism", "jwt_gcp")
  key_file = resolve_config_path(base_dir, mqtt_config.get("key_file"))
  cert_file = resolve_config_path(base_dir, mqtt_config.get("cert_file"))
  ca_file = resolve_config_path(base_dir, mqtt_config.get("ca_file"))

  if auth_mechanism == "jwt_gcp":
    topic_prefix = "/devices/"
    proj_id = mqtt_config.get("project_id")
    region = mqtt_config.get("region")
    client_id = (
        f"projects/{proj_id}/locations/{region}/registries/{registry_id}/"
        f"devices/{device_id}"
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
    auth_provider = AuthProvider(
        jwt=Jwt(audience=mqtt_config.get("project_id"))
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
      protocol=Protocol.mqtt,
  )


def wait_for_broker_readiness(
    host: str, port: int, timeout_sec: int = 30
) -> bool:
  """Probes broker host and port with retry backoff for network readiness."""
  start_time = time.time()
  LOGGER.info(
      "Verifying connectivity to MQTT broker at %s:%d (timeout: %ds)...",
      host,
      port,
      timeout_sec,
  )
  while time.time() - start_time < timeout_sec:
    try:
      with socket.create_connection((host, port), timeout=2.0):
        LOGGER.info("MQTT broker is reachable at %s:%d.", host, port)
        return True
    except (ConnectionRefusedError, OSError, socket.error) as e:
      LOGGER.debug(
          "Broker at %s:%d not yet ready: %s. Retrying in 1s...", host, port, e
      )
      time.sleep(1)

  LOGGER.warning(
      "Broker at %s:%d could not be reached within %ds timeout.",
      host,
      port,
      timeout_sec,
  )
  return False


def main():
  """Main agent launcher."""
  parser = argparse.ArgumentParser(
      description="Start Spotter Unified Edge Node"
  )
  parser.add_argument(
      "--config_file", type=str, help="path to config file", required=True
  )
  parser.add_argument(
      "--serial_no", type=str, help="optional serial number", default="NA"
  )
  args = parser.parse_args()

  # Read config
  with open(args.config_file, "r", encoding="utf-8") as f:
    config = json.load(f)

  # Normalize relative certificate and key paths against the config directory
  base_dir = os.path.dirname(os.path.abspath(args.config_file))
  mqtt_config = config.setdefault("mqtt", {})
  for path_key in ("key_file", "cert_file", "ca_file"):
    if mqtt_config.get(path_key):
      mqtt_config[path_key] = resolve_config_path(
          base_dir, mqtt_config[path_key]
      )

  # Setup logging
  log_level_str = str(config.get("log_level", "INFO")).upper()
  log_level = getattr(logging, log_level_str, logging.INFO)

  handlers = [logging.StreamHandler(sys.stdout)]
  log_file = None
  if os.path.exists("/var/log/spotter") and os.access(
      "/var/log/spotter", os.W_OK
  ):
    log_file = "/var/log/spotter/agent.log"
  elif os.path.exists("out/spotter") and os.access("out/spotter", os.W_OK):
    log_file = "out/spotter/agent.log"
  elif os.path.exists("out") and os.access("out", os.W_OK):
    os.makedirs("out/spotter", exist_ok=True)
    log_file = "out/spotter/agent.log"

  if log_file:
    handlers.append(logging.FileHandler(log_file))

  logging.basicConfig(
      format="%(asctime)s|%(levelname)s|%(module)s:%(funcName)s %(message)s",
      handlers=handlers,
      level=log_level,
  )
  LOGGER.setLevel(log_level)

  LOGGER.info(
      "Starting UDMI Spotter Unified Agent (Serial: %s)...", args.serial_no
  )

  # Initialize device placeholder and register OS signal handlers early
  device = None

  def handle_signal(signum, frame):
    del frame
    LOGGER.info("Signal %s received. Shutting down Spotter...", signum)
    if device:
      device.stop()
    sys.exit(0)

  signal.signal(signal.SIGINT, handle_signal)
  signal.signal(signal.SIGTERM, handle_signal)

  endpoint_config = build_endpoint_config(config)
  LOGGER.info("Endpoint Config: %s", endpoint_config)

  # Verify broker readiness with retry backoff before launching client
  if endpoint_config.hostname and endpoint_config.port:
    connect_timeout = int(
        config.get("mqtt", {}).get("connect_timeout_sec", 30)
    )
    wait_for_broker_readiness(
        endpoint_config.hostname,
        int(endpoint_config.port),
        timeout_sec=connect_timeout,
    )

  key_file = config.get("mqtt", {}).get("key_file")
  ca_file = config.get("mqtt", {}).get("ca_file")
  cert_file = config.get("mqtt", {}).get("cert_file")
  insecure_tls = (
      config.get("mqtt", {}).get("authentication_mechanism") == "udmi_local"
  )
  tls_config = TlsConfig(
      ca_certs=ca_file,
      cert_file=cert_file,
      key_file=key_file,
      insecure=insecure_tls,
  )
  client_config = ClientConfig(tls_config=tls_config)

  # Initialize Managers with cleanly constructed SystemState
  metrics_rate_sec = int(
      config.get("system", {}).get("metrics_rate_sec", 300)
  )
  circuit_breaker_mem_pct = float(
      config.get("system", {}).get("circuit_breaker_mem_pct", 85.0)
  )

  os_info = get_host_os_info()
  os_name = (
      os_info.get("PRETTY_NAME") or os_info.get("NAME") or "Linux"
      if os_info
      else "Linux"
  )
  os_version = (
      os_info.get("VERSION_ID")
      or os_info.get("VERSION")
      or os_info.get("VERSION_CODENAME")
      or "unknown"
  ) if os_info else "unknown"

  hw_config = config.get("system", {}).get("hardware", {})
  hw_probed = get_host_hardware_info()
  make = hw_config.get("make") or hw_probed.get("make") or "UDMI"
  model = hw_config.get("model") or hw_probed.get("model") or "Spotter"

  initial_system_state = SystemState(
      hardware=StateSystemHardware(make=make, model=model),
      software={"os": os_name, "os_version": os_version},
  )

  system_manager = SpotterSystemManager(
      system_state=initial_system_state,
      max_mem_pct=circuit_breaker_mem_pct,
      metrics_rate_sec=metrics_rate_sec,
  )

  localnet_manager = LocalnetManager()
  bacnet_cfg = config.get("bacnet", {})
  bacnet_ip = bacnet_cfg.get("ip")
  bacnet_port = bacnet_cfg.get("port")
  localnet_manager.register_provider(
      "bacnet",
      BacnetFamilyProvider(bacnet_ip=bacnet_ip, bacnet_port=bacnet_port),
  )
  localnet_manager.register_provider("ether", EtherFamilyProvider())
  localnet_manager.register_provider("ipv4", PassiveFamilyProvider())

  discovery_manager = SpotterDiscoveryManager(
      max_mem_pct=circuit_breaker_mem_pct
  )

  # Create Unified Device
  device = create_device(
      endpoint_config,
      managers=[system_manager, localnet_manager, discovery_manager],
      client_config=client_config,
      key_file=key_file,
  )

  LOGGER.info("Spotter Agent running...")

  max_retries = int(config.get("mqtt", {}).get("connect_retries", 3))
  retry_delay_sec = int(
      config.get("mqtt", {}).get("connect_retry_delay_sec", 2)
  )

  for attempt in range(1, max_retries + 1):
    try:
      device.run()
      break
    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.warning(
          "Spotter device run loop encountered error (attempt %d/%d): %s",
          attempt,
          max_retries,
          err,
      )
      if attempt < max_retries:
        time.sleep(retry_delay_sec)
      else:
        LOGGER.error(
            "Spotter device exceeded connection retry attempts (%d).",
            max_retries,
        )
        raise


if __name__ == "__main__":
  main()

