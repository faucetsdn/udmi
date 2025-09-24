"""Local configuration."""
import json
import typing
import pathlib
import copy

from typing import TypedDict, Any

class MqttConfig(TypedDict):
  device_id: str
  host: str
  port: int
  registry_id: str
  region: str
  project_id: str
  key_file: str
  public_key_file: str
  algorithm: str
  ca_file: str
  authentication_mechanism: str
  cert_file: str


class DiscoveryConfig(TypedDict):
  ipv4: bool # enable ipv4 discovery
  ether: bool # enable 
  bacnet: bool


class UdmiConfig(TypedDict):
  discovery: DiscoveryConfig


class BacnetConfig(TypedDict):
  ip: str


class EtherConfig(TypedDict):
  ip: str


class LocalConfig(TypedDict):
  configs_dir: str
  mqtt: MqttConfig
  udmi: UdmiConfig
  bacnet: BacnetConfig
  ether: EtherConfig

def merge_dicts(base: dict, override: dict) -> dict:
  """Recursively merges two dictionaries.

  Args:
    base: Base dict
    override: Dict with override values
  
  Returns:
    merged config
  
  """
  for key, value in override.items():
    if isinstance(value, dict) and key in base and isinstance(base[key], dict):
      base[key] = merge_dicts(base[key], value)
    else:
      base[key] = value
  return base


def read_config(file_name: str) -> LocalConfig:
  """ Reads config from all files.

    Merges additional files as required
  """

  with open(file_name, "rb") as f:
    base_config = json.load(f)

  if configs_dir := base_config.get("configs_dir"):
    merged_config = copy.deepcopy(base_config)
    for extra_file in pathlib.Path(configs_dir).glob('*.json'):
      with open(extra_file, "rb") as f:
        overlay = json.load(f)
      merged_config = merge_dicts(merged_config, overlay)
    return typing.cast(LocalConfig, merged_config)
  else:
    return base_config
