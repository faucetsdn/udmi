"""Script to extract a DBO config from a UDMI site model."""
import json
import sys
from pathlib import Path
import yaml
import re

def sanitize_id(id_str: str) -> str:
  """Sanitizes a string for use as a UDMI device_id."""
  # Replace non-alphanumeric with underscores, strip leading/trailing underscores
  s = re.sub(r'[^a-zA-Z0-9_-]+', '_', id_str)
  return s.strip('_')

def extract_dbo_config(site_model_dir: Path) -> dict:
  """Extracts DBO configurations to a dict."""
  building_config = {}
  
  ancillary_path = site_model_dir / "ancillary.json"
  if ancillary_path.exists():
    with open(ancillary_path, "r", encoding="utf-8") as f:
      building_config = json.load(f)

  device_id_to_guid = {}

  # Build mapping for FACILITIES from ancillary data
  used_ids = set()
  # Sort keys to ensure deterministic collision handling if any
  for guid in sorted(building_config.keys()):
    if guid == "CONFIG_METADATA": continue
    entity = building_config[guid]
    code = entity.get("code")
    base_id = sanitize_id(code) if code else guid
    dev_id = base_id
    counter = 2
    while dev_id in used_ids:
      dev_id = f"{base_id}_{counter}"
      counter += 1
    device_id_to_guid[dev_id] = guid
    used_ids.add(dev_id)

  devices_dir = site_model_dir / "devices"
  if not devices_dir.is_dir():
      return building_config

  metadata_map = {}

  # Pass 1: Build the mapping of device ID -> GUID
  for device_dir in sorted(devices_dir.iterdir()):
    if not device_dir.is_dir():
      continue

    metadata_path = device_dir / "metadata.json"
    if not metadata_path.exists():
      continue

    with open(metadata_path, "r", encoding="utf-8") as f:
      metadata = json.load(f)

    dbo_external = metadata.get("externals", {}).get("dbo")
    if not dbo_external:
      continue

    guid = dbo_external.get("ext_id")
    if not guid:
      continue

    device_id = device_dir.name
    device_id_to_guid[device_id] = guid
    metadata_map[device_id] = metadata

  # Pass 2: Build the resulting config
  for device_id, metadata in metadata_map.items():
    guid = device_id_to_guid[device_id]
    dbo_external = metadata.get("externals", {}).get("dbo", {})

    device_entry = {}

    device_entry["code"] = dbo_external.get("label", device_id)

    if "cloud" in metadata and "num_id" in metadata["cloud"]:
      device_entry["cloud_device_id"] = str(metadata["cloud"]["num_id"])

    if "relationships" in metadata and metadata["relationships"]:
      # Map UDMI device IDs to GUIDs
      connections = {}
      for target_id, relation in metadata["relationships"].items():
        target_guid = device_id_to_guid.get(target_id, target_id)
        if isinstance(relation, dict):
          if relation:
             # Match string enum format expected by schema
             rel_type = list(relation.keys())[0]
             connections[target_guid] = rel_type

      device_entry["connections"] = connections

    # extract point translation and links from core UDMI pointset constructs
    pointset = metadata.get("pointset", {}).get("points", {})
    translations = {}
    links_dbo = {}
    for point_name, point_info in pointset.items():
      # Reconstruct DBO translation from standard UDMI fields
      pt_dbo = {}
      pt_dbo["present_value"] = point_info.get("ref", f"points.{point_name}.present_value")
      
      if "units" in point_info:
          u_map = {
              "degC": "degrees_celsius",
              "L/s": "liters_per_second"
          }
          pt_dbo["units"] = {
              "key": f"pointset.points.{point_name}.units",
              "values": {
                  u_map.get(point_info["units"], point_info["units"]): point_info["units"]
              }
          }
      
      if "value_map" in point_info:
          pt_dbo["states"] = point_info["value_map"]

      if len(pt_dbo) > 1 or pt_dbo["present_value"] != f"points.{point_name}.present_value":
          translations[point_name] = pt_dbo

      if "expr" in point_info:
        link_val = point_info["expr"]
        if ":" in link_val:
          remote_device_id, remote_pt = link_val.split(":", 1)
          remote_guid = device_id_to_guid.get(remote_device_id, remote_device_id)
          if remote_guid not in links_dbo:
            links_dbo[remote_guid] = {}
          # DBO links are local_point: remote_point
          links_dbo[remote_guid][point_name] = remote_pt

    if translations:
      device_entry["translation"] = translations

    if links_dbo:
      device_entry["links"] = links_dbo

    if "etag" in dbo_external:
      device_entry["etag"] = dbo_external["etag"]

    if "system" in metadata and "name" in metadata["system"]:
      device_entry["display_name"] = metadata["system"]["name"]

    if "type" in dbo_external:
      device_entry["type"] = dbo_external["type"]

    building_config[guid] = device_entry

  return building_config

if __name__ == "__main__":
  if len(sys.argv) != 3:
    print("Usage: python dbo_converter.py <site_model_dir> <output_yaml>")
    sys.exit(1)

  site_dir = Path(sys.argv[1])
  if (site_dir / "site_model").is_dir():
    site_dir = site_dir / "site_model"
  out_yaml = Path(sys.argv[2])

  config_result = extract_dbo_config(site_dir)

  with open(out_yaml, "w", encoding="utf-8") as f_out:
    # Avoid aliases in YAML output to match standard formatting
    yaml.Dumper.ignore_aliases = lambda *args: True
    yaml.dump(config_result, f_out, sort_keys=True, default_flow_style=False)

  print(f"Extracted building config to {out_yaml}")
