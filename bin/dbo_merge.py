#!/usr/bin/env python3
"""Script to merge a DBO config into a UDMI site model."""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
import yaml

def merge_dbo_config(yaml_file: Path, site_model_dir: Path):
  """Merges a DBO config file into the given UDMI site model."""
  with open(yaml_file, "r", encoding="utf-8") as f:
    config = yaml.safe_load(f)

  devices_dir = site_model_dir / "devices"
  devices_dir.mkdir(parents=True, exist_ok=True)

  # First pass: map guids to device codes
  guid_to_code = {}
  for guid, entity in config.items():
    if guid == "CONFIG_METADATA":
      continue
    code = entity.get("code", guid)
    guid_to_code[guid] = code

  # Second pass: merge data
  for guid, entity in config.items():
    if guid == "CONFIG_METADATA":
      continue

    code = entity.get("code", guid)
    device_dir = devices_dir / code
    device_dir.mkdir(parents=True, exist_ok=True)

    metadata_path = device_dir / "metadata.json"
    if metadata_path.exists():
      with open(metadata_path, "r", encoding="utf-8") as f:
        metadata = json.load(f)
    else:
      timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
      metadata = {
          "timestamp": timestamp,
          "version": "1.5.3",
      }

    # Externals
    externals = metadata.setdefault("externals", {})
    dbo = externals.setdefault("dbo", {})
    dbo["ext_id"] = guid
    if "type" in entity:
      dbo["type"] = entity["type"]

    # Cloud
    if "cloud_device_id" in entity:
      cloud = metadata.setdefault("cloud", {})
      cloud["num_id"] = str(entity["cloud_device_id"])

    # Relationships
    if "connections" in entity:
      relationships = metadata.setdefault("relationships", {})
      for target_guid, rel_data in entity["connections"].items():
        target_code = guid_to_code.get(target_guid, target_guid)

        if isinstance(rel_data, str):
          relationships[target_code] = {rel_data: [{}]}
        elif isinstance(rel_data, list):
          rel_dict = {}
          for item in rel_data:
            item_copy = item.copy()
            if "type" in item_copy:
              rel_type = item_copy.pop("type")
              rel_dict.setdefault(rel_type, []).append(item_copy)
          relationships[target_code] = rel_dict

    # Pointset Translation and Links
    if "translation" in entity or "links" in entity:
      pointset = metadata.setdefault("pointset", {})
      points = pointset.setdefault("points", {})

      if "translation" in entity:
        for pt_name, pt_dbo in entity["translation"].items():
          pt_udmi = points.setdefault(pt_name, {})
          if "present_value" in pt_dbo:
            pt_udmi["ref"] = pt_dbo["present_value"]
          if "units" in pt_dbo and "values" in pt_dbo["units"]:
            # Extract the first value from the values dictionary
            udmi_unit = list(pt_dbo["units"]["values"].values())[0]
            pt_udmi["units"] = udmi_unit

      if "links" in entity:
        for target_guid, link_map in entity["links"].items():
          target_code = guid_to_code.get(target_guid, target_guid)
          for local_pt, remote_pt in link_map.items():
            pt_udmi = points.setdefault(local_pt, {})
            pt_udmi["link"] = f"{target_code}:{remote_pt}"

    with open(metadata_path, "w", encoding="utf-8") as f:
      json.dump(metadata, f, indent=2)
      f.write("\n")

if __name__ == "__main__":
  if len(sys.argv) != 3:
    print(
        "Usage: python bin/dbo_merge.py <building_config.yaml> <site_model_dir>"
    )
    sys.exit(1)

  merge_dbo_config(Path(sys.argv[1]), Path(sys.argv[2]))
