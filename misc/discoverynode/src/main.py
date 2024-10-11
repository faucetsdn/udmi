"""Client"""
# pylint: disable=wrong-import-position
import argparse
import logging
import sys
import time
import json
import warnings
import os
# filter deprecation notice from SCAPY import
warnings.filterwarnings(action="ignore", module=".*ipsec.*")

import udmi.core
import udmi.publishers.mqtt


def or_required_from_env(key: str) -> dict[str, str | int | bool]:
  """Used in argparse to return a non-optional value from env vars.

  Unpack return value as argument for argparse.ArgumentParser.add_argument.

  Args:
    key: environmental variable name.

  Returns:
    value from env var if set or indicator that field is required.
  """
  try:
    return {"default": os.environ[key]}
  except KeyError:
    return {"required": True}


def get_arguments():
  parser = argparse.ArgumentParser(description="Start UDMI Discovey Client")
  parser.add_argument(
      "--config_file",
      type=str,
      help="path to config file"
  )
  return parser.parse_args()


def load_config_from_file(file_name: str):
  with open(file_name, "rb") as f:
    return json.load(f)


def main():

  stdout = logging.StreamHandler(sys.stdout)
  stdout.addFilter(lambda log: log.levelno < logging.WARNING)
  stdout.setLevel(logging.INFO)
  stderr = logging.StreamHandler(sys.stderr)
  stderr.setLevel(logging.WARNING)
  logging.basicConfig(
      format="%(asctime)s|%(levelname)s|%(module)s:%(funcName)s %(message)s",
      handlers=[stderr, stdout],
      level=logging.INFO,
  )
  logging.root.setLevel(logging.INFO)

  args = get_arguments()

  logging.info("Loading config from %s", args.config_file)
  config = load_config_from_file(args.config_file)

  # Initialise (but not start) the MQTT Client
  mclient = udmi.publishers.mqtt.MQTT(
      device_id=config["mqtt"]["device_id"],
      registry_id=config["mqtt"]["registry_id"],
      region=config["mqtt"]["region"],
      project_id=config["mqtt"]["project_id"],
      hostname=config["mqtt"]["host"],
      port=config["mqtt"]["port"],
      key_file=config["mqtt"]["key_file"],
      algorithm=config["mqtt"]["algorithm"],
  )

  udmi_client = udmi.core.UDMI(
      publisher=mclient,
      topic_prefix=f'/devices/{config["mqtt"]["device_id"]}',
      config=config,
  )

  mclient.set_config_callback(udmi_client.config_handler)
  mclient.start_client()

  while True:
    time.sleep(0.1)


if __name__ == "__main__":
  main()
