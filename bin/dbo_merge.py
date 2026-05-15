#!/usr/bin/env python3
"""Script to merge a DBO config into a UDMI site model."""
import json
import os
import sys
import csv
import re
from datetime import datetime, timezone
from pathlib import Path
import yaml

def sanitize_id(id_str: str) -> str:
  """Sanitizes a string for use as a UDMI device_id."""
  # Replace non-alphanumeric with underscores, strip leading/trailing underscores
  s = re.sub(r'[^a-zA-Z0-9_-]+', '_', id_str)
  return s.strip('_')

def load_csv_map(site_model_dir: Path):
  """Loads device_num_id -> device_id map from discovery.csv if it exists."""
  csv_map = {}
  csv_path = site_model_dir / "discovery.csv"
  if not csv_path.exists():
    csv_path = site_model_dir.parent / "discovery.csv"
  
  if csv_path.exists():
    with open(csv_path, "r", encoding="utf-8") as f:
      reader = csv.DictReader(f)
      for row in reader:
        num_id = row.get("device_num_id")
        dev_id = row.get("device_id")
        if num_id and dev_id:
          csv_map[num_id] = dev_id
  return csv_map

def merge_dbo_config(yaml_file: Path, site_model_dir: Path):
  """Merges a DBO config file into the given UDMI site model."""
  with open(yaml_file, "r", encoding="utf-8") as f:
    config = yaml.safe_load(f)

  devices_dir = site_model_dir / "devices"
  devices_dir.mkdir(parents=True, exist_ok=True)

  csv_map = load_csv_map(site_model_dir)

  # First pass: map every GUID to a unique ID
  guid_to_id = {}
  used_ids = set()
  
  # Priority 1: CSV-mapped devices (don't sanitize these, assume they are authoritative)
  for guid, entity in config.items():
    if guid == "CONFIG_METADATA": continue
    cloud_id = str(entity.get("cloud_device_id", ""))
    if cloud_id in csv_map:
      dev_id = csv_map[cloud_id]
      guid_to_id[guid] = dev_id
      used_ids.add(dev_id)

  # Priority 2: Code (with sanitization and collision handling)
  # This now applies to ALL entities including FACILITIES
  for guid, entity in config.items():
    if guid == "CONFIG_METADATA" or guid in guid_to_id: continue
    
    code = entity.get("code")
    if code:
        base_id = sanitize_id(code)
    else:
        base_id = guid
        
    dev_id = base_id
    counter = 2
    while dev_id in used_ids:
      dev_id = f"{base_id}_{counter}"
      counter += 1
    
    guid_to_id[guid] = dev_id
    used_ids.add(dev_id)

  ancillary = {}

  # Second pass: merge data
  for guid, entity in config.items():
    if guid == "CONFIG_METADATA":
      continue

    etype = entity.get("type", "")
    if etype.startswith("FACILITIES/"):
      ancillary[guid] = entity
      continue

    device_id = guid_to_id[guid]
    device_dir = devices_dir / device_id
    device_dir.mkdir(parents=True, exist_ok=True)

    metadata_path = device_dir / "metadata.json"
    if metadata_path.exists():
      with open(metadata_path, "r", encoding="utf-8") as f:
        metadata = json.load(f)
    else:
      timestamp = os.environ.get("UDMI_TEST_TIMESTAMP", datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
      metadata = {
          "timestamp": timestamp,
          "version": "1.5.3",
      }

    # System
    system = metadata.setdefault("system", {})
    if "display_name" in entity:
      system["name"] = entity["display_name"]

    # Externals
    externals = metadata.setdefault("externals", {})
    dbo = externals.setdefault("dbo", {})
    dbo["ext_id"] = guid
    if "type" in entity:
      dbo["type"] = entity["type"]
    if "etag" in entity:
      dbo["etag"] = entity["etag"]
    if "code" in entity:
      dbo["label"] = entity["code"]

    # Cloud
    if "cloud_device_id" in entity:
      cloud = metadata.setdefault("cloud", {})
      cloud["num_id"] = str(entity["cloud_device_id"])

    # Relationships
    if "connections" in entity:
      relationships = metadata.setdefault("relationships", {})
      for target_guid, rel_data in entity["connections"].items():
        target_id = guid_to_id.get(target_guid, target_guid)

        if isinstance(rel_data, str):
          relationships[target_id] = {rel_data: [{}]}
        elif isinstance(rel_data, list):
          rel_dict = {}
          for item in rel_data:
            if isinstance(item, str):
              rel_dict.setdefault(item, []).append({})
            elif isinstance(item, dict):
              item_copy = item.copy()
              if "type" in item_copy:
                rel_type = item_copy.pop("type")
                rel_dict.setdefault(rel_type, []).append(item_copy)
          relationships[target_id] = rel_dict

    # Pointset Translation and Links
    if "translation" in entity or "links" in entity:
      pointset = metadata.setdefault("pointset", {})
      points = pointset.setdefault("points", {})

      if "translation" in entity:
        for pt_name, pt_dbo in entity["translation"].items():
          pt_udmi = points.setdefault(pt_name, {})
          if "present_value" in pt_dbo:
            pv = pt_dbo["present_value"]
            if pv != "present_value" and pv != f"points.{pt_name}.present_value":
              pt_udmi["ref"] = pv
          if "units" in pt_dbo and "values" in pt_dbo["units"]:
            udmi_unit = list(pt_dbo["units"]["values"].values())[0]
            pt_udmi["units"] = udmi_unit
          if "states" in pt_dbo:
            pt_udmi["value_map"] = pt_dbo["states"]

      if "links" in entity:
        for target_guid, link_map in entity["links"].items():
          target_id = guid_to_id.get(target_guid, target_guid)
          for local_pt, remote_pt in link_map.items():
            pt_udmi = points.setdefault(local_pt, {})
            pt_udmi["expr"] = f"{target_id}:{remote_pt}"

    with open(metadata_path, "w", encoding="utf-8") as f:
      json.dump(metadata, f, indent=2)
      f.write("\n")

  if ancillary:
    ancillary_path = site_model_dir / "ancillary.json"
    with open(ancillary_path, "w", encoding="utf-8") as f:
      json.dump(ancillary, f, indent=2)
      f.write("\n")

if __name__ == "__main__":
  if len(sys.argv) != 3:
    print(
        "Usage: python bin/dbo_merge.py <building_config.yaml> <site_model_dir>"
    )
    sys.exit(1)

  merge_dbo_config(Path(sys.argv[1]), Path(sys.argv[2]))
