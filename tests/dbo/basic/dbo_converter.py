import json
import yaml
import sys
from pathlib import Path

def extract_dbo_config(site_model_dir: Path) -> dict:
    building_config = {}

    # We will assume a static CONFIG_METADATA for this basic example
    building_config["CONFIG_METADATA"] = {
        "operation": "INITIALIZE"
    }

    devices_dir = site_model_dir / "devices"

    device_id_to_guid = {}
    metadata_map = {}

    # Pass 1: Build the mapping of device ID -> GUID
    for device_dir in sorted(devices_dir.iterdir()):
        if not device_dir.is_dir():
            continue

        metadata_path = device_dir / "metadata.json"
        if not metadata_path.exists():
            continue

        with open(metadata_path, 'r') as f:
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

        if "label" in dbo_external:
            device_entry["code"] = dbo_external["label"]
        else:
            device_entry["code"] = device_id

        if "cloud" in metadata and "num_id" in metadata["cloud"]:
            device_entry["cloud_device_id"] = str(metadata["cloud"]["num_id"])

        if "relationships" in metadata and metadata["relationships"]:
            # Map UDMI device IDs to GUIDs
            connections = {}
            for target_id, relation in metadata["relationships"].items():
                target_guid = device_id_to_guid.get(target_id, target_id)
                if isinstance(relation, dict):
                    conn_list = []
                    for rel_type, rel_instances in relation.items():
                        for inst in rel_instances:
                            conn_obj = inst.copy()
                            conn_obj["type"] = rel_type
                            conn_list.append(conn_obj)

                    # If it's just one type without tags, flatten it.
                    if len(conn_list) == 1 and list(conn_list[0].keys()) == ["type"]:
                        connections[target_guid] = conn_list[0]["type"]
                    else:
                        connections[target_guid] = conn_list

            device_entry["connections"] = connections

        # extract point translation and links from core UDMI pointset constructs
        pointset = metadata.get("pointset", {}).get("points", {})
        translations = {}
        links_dbo = {}
        for point_name, point_info in pointset.items():
            pt_dbo = {}
            if "ref" in point_info:
                pt_dbo["present_value"] = point_info["ref"]
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

            if pt_dbo:
                translations[point_name] = pt_dbo

            if "links" in point_info:
                for remote_device_id, remote_pt in point_info["links"].items():
                    remote_guid = device_id_to_guid.get(remote_device_id, remote_device_id)
                    if remote_guid not in links_dbo:
                        links_dbo[remote_guid] = {}
                    # DBO links are local_point: remote_point
                    links_dbo[remote_guid][point_name] = remote_pt

        if translations:
            device_entry["translation"] = translations

        if links_dbo:
            device_entry["links"] = links_dbo

        if "type" in dbo_external:
            device_entry["type"] = dbo_external["type"]

        building_config[guid] = device_entry

    return building_config


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python dbo_converter.py <site_model_dir> <output_yaml>")
        sys.exit(1)

    site_model_dir = Path(sys.argv[1])
    output_yaml = Path(sys.argv[2])

    building_config = extract_dbo_config(site_model_dir)

    with open(output_yaml, 'w') as f:
        # Avoid aliases in YAML output to match standard formatting
        yaml.Dumper.ignore_aliases = lambda *args : True
        yaml.dump(building_config, f, sort_keys=False, default_flow_style=False)

    print(f"Extracted building config to {output_yaml}")
