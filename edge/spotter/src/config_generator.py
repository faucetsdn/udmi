#!/usr/bin/env python3
"""Config generator for UDMI Spotter Agent."""

import argparse
import json
import os
import re
import sys


def parse_project_spec(project_spec, site_config):
  """Parses project specification into connection and broker endpoints."""
  raw_spec = (
      project_spec[2:] if project_spec.startswith("//") else project_spec
  )

  if project_spec.startswith("//"):
    if "/" in raw_spec:
      iot_provider = raw_spec.split("/", 1)[0]
      spec_no_provider = raw_spec.split("/", 1)[1]
    else:
      iot_provider = raw_spec
      spec_no_provider = ""
  else:
    iot_provider = "mqtt"
    spec_no_provider = raw_spec

  if "@" in spec_no_provider:
    spec_target, broker_spec = spec_no_provider.split("@", 1)
  else:
    broker_spec = ""
    spec_target = spec_no_provider

  udmi_prefix = ""
  if "/" in spec_target:
    project_id, udmi_namespace = spec_target.split("/", 1)
    if udmi_namespace:
      udmi_prefix = f"{udmi_namespace}~"
  elif "+" in spec_target:
    project_id, udmi_namespace = spec_target.split("+", 1)
    if udmi_namespace:
      udmi_prefix = f"{udmi_namespace}~"
  else:
    project_id = spec_target

  registry_base = site_config.get("registry_id", "")
  cloud_region = site_config.get("cloud_region") or "us-central1"
  registry_id = f"{udmi_prefix}{registry_base}"

  if iot_provider == "clearblade":
    host = f"{cloud_region}-mqtt.clearblade.com"
    port = 8883
    auth_mechanism = "jwt_gcp"
  elif iot_provider == "gbos":
    host = "mqtt.bos.goog"
    port = 8883
    auth_mechanism = "jwt_gcp"
  elif iot_provider in ("gcp", "gref"):
    host = "mqtt.googleapis.com"
    port = 8883
    auth_mechanism = "jwt_gcp"
  else:  # default: mqtt
    auth_mechanism = "udmi_local"
    port = 18883
    host = project_id

  if broker_spec:
    match = re.search(r":(\d+)$", broker_spec)
    if match:
      port = int(match.group(1))
      host = broker_spec[: match.start()]
    else:
      host = broker_spec
  else:
    match = re.search(r":(\d+)$", project_id)
    if match:
      port = int(match.group(1))
      project_id = project_id[: match.start()]
      if iot_provider == "mqtt":
        host = "localhost"

  if not project_id or (project_id == "localhost" and iot_provider != "mqtt"):
    project_id = site_config.get("project_id") or "my-project"

  return {
      "iot_provider": iot_provider,
      "project_id": project_id,
      "registry_id": registry_id,
      "cloud_region": cloud_region,
      "host": host,
      "port": port,
      "auth_mechanism": auth_mechanism,
  }


def generate_spotter_config(  # pylint: disable=too-many-positional-arguments,too-many-arguments
    site_path,
    project_spec,
    device_id,
    out_path=None,
    metrics_rate_sec=300,
    bacnet_ip=None,
):
  """Generates runtime spotter configuration dictionary and writes to JSON."""
  site_path = os.path.abspath(site_path)
  if not os.path.isdir(site_path):
    raise ValueError(f"Site path directory not found: {site_path}")

  site_config_path = os.path.join(site_path, "cloud_iot_config.json")
  if not os.path.isfile(site_config_path):
    raise ValueError(
        f"cloud_iot_config.json not found inside site path: {site_config_path}"
    )

  with open(site_config_path, "r", encoding="utf-8") as f:
    site_config = json.load(f)

  parsed_spec = parse_project_spec(project_spec, site_config)
  effective_device_id = device_id

  device_dir = os.path.join(site_path, "devices", device_id)
  if not os.path.isdir(device_dir):
    raise ValueError(f"Device directory does not exist: {device_dir}")

  def find_key_cert(target_dir):
    key_file = os.path.join(target_dir, "rsa_private.pem")
    is_ec = False
    if not os.path.isfile(key_file):
      key_file = os.path.join(target_dir, "ec_private.pem")
      is_ec = True

    cert_file = os.path.join(target_dir, "rsa_private.crt")
    if not os.path.isfile(cert_file):
      cert_file = os.path.join(target_dir, "ec_private.crt")

    return (
        key_file if os.path.isfile(key_file) else None,
        cert_file if os.path.isfile(cert_file) else None,
        is_ec,
    )

  base_key, base_cert, is_ec = find_key_cert(device_dir)
  if not base_key:
    raise ValueError(
        f"Private key file not found in device directory: {device_dir}"
    )

  algorithm = "ES256" if is_ec else "RS256"

  # Inspect device metadata if available
  metadata_path = os.path.join(device_dir, "metadata.json")
  if os.path.isfile(metadata_path):
    try:
      with open(metadata_path, "r", encoding="utf-8") as f:
        meta = json.load(f)
      auth_type = meta.get("cloud", {}).get("auth_type") or meta.get(
          "auth_type"
      )
      if auth_type:
        if "ES256" in auth_type:
          algorithm = "ES256"
        elif "RS256" in auth_type:
          algorithm = "RS256"
      if not bacnet_ip:
        bacnet_ip = (
            meta.get("localnet", {})
            .get("families", {})
            .get("bacnet", {})
            .get("ip")
        )
    except Exception:  # pylint: disable=broad-exception-caught
      pass

  ca_file = None
  if parsed_spec["iot_provider"] == "mqtt":
    ca_file = os.path.join(site_path, "reflector", "ca.crt")

  bacnet_section = {}
  if bacnet_ip:
    bacnet_section["ip"] = bacnet_ip

  config = {
      "log_level": "INFO",
      "system": {
          "metrics_rate_sec": metrics_rate_sec,
      },
      "mqtt": {
          "device_id": effective_device_id,
          "registry_id": parsed_spec["registry_id"],
          "host": parsed_spec["host"],
          "port": parsed_spec["port"],
          "authentication_mechanism": parsed_spec["auth_mechanism"],
          "region": parsed_spec["cloud_region"],
          "project_id": parsed_spec["project_id"],
          "key_file": base_key,
          "cert_file": base_cert,
          "ca_file": ca_file,
          "algorithm": algorithm,
      },
      "bacnet": bacnet_section,
  }

  if out_path:
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
      json.dump(config, f, indent=2)

  return config


def main():
  """Main entry point for command-line execution."""
  parser = argparse.ArgumentParser(
      description=(
          "Generate dynamic Spotter runtime configuration from site model."
      )
  )
  parser.add_argument(
      "--site_path", required=True, help="Path to site model directory"
  )
  parser.add_argument(
      "--project_spec", required=True, help="Project specification string"
  )
  parser.add_argument("--device_id", required=True, help="Target device ID")
  parser.add_argument("--out", required=True, help="Output config path")
  parser.add_argument(
      "--metrics_rate_sec",
      type=int,
      default=300,
      help="Metrics reporting rate in seconds (default: 300)",
  )
  parser.add_argument(
      "--bacnet_ip",
      default=None,
      help="BACnet interface IP address (default: auto-detect)",
  )

  args = parser.parse_args()
  try:
    generate_spotter_config(
        args.site_path,
        args.project_spec,
        args.device_id,
        args.out,
        metrics_rate_sec=args.metrics_rate_sec,
        bacnet_ip=args.bacnet_ip,
    )
    print(f"Dynamic configuration generated: {args.out}")
  except Exception as e:  # pylint: disable=broad-exception-caught
    print(f"Error: {e}", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
  main()

