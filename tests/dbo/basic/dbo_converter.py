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

        device_entry = {}

        if "label" in dbo_external:
            device_entry["code"] = dbo_external["label"]

        if "cloud" in metadata and "num_id" in metadata["cloud"]:
            device_entry["cloud_device_id"] = str(metadata["cloud"]["num_id"])

        if "relationships" in metadata and metadata["relationships"]:
            device_entry["connections"] = metadata["relationships"]

        # extract point translation from core UDMI pointset constructs
        pointset = metadata.get("pointset", {}).get("points", {})
        translations = {}
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
            if "states" in point_info:
                pt_dbo["states"] = point_info["states"]

            if pt_dbo:
                translations[point_name] = pt_dbo

        if translations:
            device_entry["translation"] = translations

        if "links" in metadata and metadata["links"]:
            device_entry["links"] = metadata["links"]

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
