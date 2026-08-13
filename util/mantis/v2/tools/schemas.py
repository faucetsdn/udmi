import json
import os
from typing import Any, Dict, List, Optional


def list_udmi_schemas(udmi_root: str, filter_keyword: Optional[str] = None) -> str:
    """
    Lists all available UDMI JSON schemas located under schema/.
    Can filter by category or keyword (e.g. 'pointset', 'state', 'config', 'system').
    """
    schema_dir = os.path.join(os.path.abspath(udmi_root), "schema")
    if not os.path.exists(schema_dir):
        return f"Error: Schema directory not found at '{schema_dir}'."

    files = sorted([f for f in os.listdir(schema_dir) if f.endswith(".json")])
    if filter_keyword:
        kw = filter_keyword.lower()
        files = [f for f in files if kw in f.lower()]

    lines = [
        f"### Available UDMI Schemas ({len(files)} matches)",
        "| Schema File | Title / Description |",
        "| :--- | :--- |"
    ]

    for fname in files:
        fpath = os.path.join(schema_dir, fname)
        title = "N/A"
        try:
            with open(fpath, "r", encoding="utf-8") as sf:
                data = json.load(sf)
                title = data.get("title") or data.get("description") or "N/A"
                if len(title) > 80:
                    title = title[:77] + "..."
        except Exception:
            pass
        lines.append(f"| `{fname}` | {title} |")

    return "\n".join(lines)


def inspect_udmi_schema(udmi_root: str, schema_name: str) -> str:
    """
    Retrieves and displays the JSON schema definition for a specified UDMI message type or block
    (e.g., 'pointset', 'events_pointset', 'state_system', 'config_pointset', 'metadata').
    """
    schema_dir = os.path.join(os.path.abspath(udmi_root), "schema")
    if not os.path.exists(schema_dir):
        return f"Error: Schema directory not found at '{schema_dir}'."

    clean_name = schema_name.strip()
    if not clean_name.endswith(".json"):
        clean_name += ".json"

    exact_path = os.path.join(schema_dir, clean_name)
    if not os.path.exists(exact_path):
        # Fuzzy match (e.g. 'pointset' -> 'events_pointset.json' or 'config_pointset.json')
        candidates = [f for f in os.listdir(schema_dir) if f.endswith(".json") and schema_name.lower() in f.lower()]
        if candidates:
            exact_path = os.path.join(schema_dir, candidates[0])
            clean_name = candidates[0]
        else:
            return f"Error: No schema matching '{schema_name}' found in '{schema_dir}'."

    try:
        with open(exact_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        formatted_json = json.dumps(data, indent=2)
        return f"### UDMI Schema: `{clean_name}`\n```json\n{formatted_json}\n```"
    except Exception as e:
        return f"Error reading schema '{clean_name}': {e}"
