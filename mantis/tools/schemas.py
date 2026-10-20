"""Authoritative UDMI JSON Schema inspection and navigation."""

import json
import os
from typing import Any, Dict, List, Optional


def _get_udmi_root(udmi_root: Optional[str] = None) -> str:
    if udmi_root is not None:
        return os.path.abspath(udmi_root)
    # Default to repo root
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def list_udmi_schemas(udmi_root: Optional[str] = None) -> List[Dict[str, Any]]:
    """List all available UDMI JSON schemas in the schema directory."""
    root = _get_udmi_root(udmi_root)
    schema_dir = os.path.join(root, "schema")
    if not os.path.isdir(schema_dir):
        return []

    schemas = []
    for filename in sorted(os.listdir(schema_dir)):
        if filename.endswith(".json"):
            filepath = os.path.join(schema_dir, filename)
            schema_info = {
                "name": filename[:-5],
                "filename": filename,
                "title": "",
                "description": "",
            }
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    schema_info["title"] = data.get("title", "")
                    schema_info["description"] = data.get("description", "")
            except Exception:
                pass
            schemas.append(schema_info)
    return schemas


def resolve_schema_refs(
    schema_node: Any,
    schema_dir: str,
    max_depth: int = 10,
    visited: Optional[set] = None,
    depth: int = 0,
) -> Any:
    """Recursively resolves $ref pointers in a JSON schema up to max_depth."""
    if depth >= max_depth:
        return schema_node

    if visited is None:
        visited = set()

    if isinstance(schema_node, list):
        return [
            resolve_schema_refs(item, schema_dir, max_depth, visited, depth + 1)
            for item in schema_node
        ]

    if not isinstance(schema_node, dict):
        return schema_node

    # If this dictionary contains a $ref
    if "$ref" in schema_node and isinstance(schema_node["$ref"], str):
        ref_str = schema_node["$ref"]
        clean_ref = ref_str[5:] if ref_str.startswith("file:") else ref_str
        parts = clean_ref.split("#", 1)
        target_file = parts[0].strip()
        pointer = parts[1].strip() if len(parts) > 1 else ""

        ref_key = (target_file, pointer)
        if ref_key in visited:
            return schema_node
        new_visited = visited | {ref_key}

        resolved_target = None
        if target_file:
            target_path = os.path.join(schema_dir, target_file)
            if os.path.isfile(target_path):
                try:
                    with open(target_path, "r", encoding="utf-8") as f:
                        resolved_target = json.load(f)
                except Exception:
                    pass

        if resolved_target is not None:
            if pointer:
                pointer_keys = [k for k in pointer.strip("/").split("/") if k]
                curr = resolved_target
                for pk in pointer_keys:
                    if isinstance(curr, dict) and pk in curr:
                        curr = curr[pk]
                    else:
                        curr = None
                        break
                resolved_node = curr
            else:
                resolved_node = resolved_target

            if resolved_node is not None:
                merged = json.loads(json.dumps(resolved_node))
                if isinstance(merged, dict):
                    for k, v in schema_node.items():
                        if k != "$ref":
                            merged[k] = v
                    return resolve_schema_refs(
                        merged, schema_dir, max_depth, new_visited, depth + 1
                    )
                return resolved_node

    # Recursively resolve all dictionary fields
    res = {}
    for k, v in schema_node.items():
        res[k] = resolve_schema_refs(v, schema_dir, max_depth, visited, depth + 1)
    return res


def inspect_udmi_schema(
    schema_name: str,
    sub_path: Optional[str] = None,
    resolve_refs: bool = False,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Retrieve authoritative JSON schema definitions for any UDMI message block."""
    root = _get_udmi_root(udmi_root)
    schema_dir = os.path.join(root, "schema")

    if not os.path.isdir(schema_dir):
        return {
            "status": "ERROR",
            "error": f"Schema directory not found: {schema_dir}",
        }

    clean_name = schema_name.strip()
    if clean_name.lower() in ("list", "*", "all"):
        all_schemas = list_udmi_schemas(root)
        return {
            "status": "SUCCESS",
            "schema_name": "list",
            "count": len(all_schemas),
            "schemas": all_schemas,
        }

    # Resolve filename with path containment
    resolved_schema_dir = os.path.realpath(schema_dir)
    target_file = clean_name if clean_name.endswith(".json") else f"{clean_name}.json"
    target_path = os.path.realpath(os.path.join(resolved_schema_dir, target_file))
    if not target_path.startswith(resolved_schema_dir + os.sep):
        return {
            "status": "ERROR",
            "error": f"Security violation: Invalid schema_name '{schema_name}' attempts path traversal.",
        }

    substituted_for = None
    if not os.path.isfile(target_path):
        candidates = [
            f for f in sorted(os.listdir(resolved_schema_dir))
            if f.endswith(".json") and (clean_name in f or f[:-5].endswith(clean_name))
        ]
        if candidates:
            # Prefer events_<name>.json or config_<name>.json if available
            preferred = None
            for prefix in ("events_", "config_", "state_", "model_"):
                if f"{prefix}{clean_name}.json" in candidates:
                    preferred = f"{prefix}{clean_name}.json"
                    break
            chosen = preferred or candidates[0]
            target_path = os.path.realpath(os.path.join(resolved_schema_dir, chosen))
            target_file = chosen
            substituted_for = f"Substituted '{chosen}' for requested schema '{schema_name}'"
        else:
            all_names = [f[:-5] for f in sorted(os.listdir(resolved_schema_dir)) if f.endswith(".json")]
            return {
                "status": "ERROR",
                "error": f"Schema '{schema_name}' not found in {schema_dir}",
                "available_schemas": all_names[:25],
            }

    try:
        with open(target_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return {
            "status": "ERROR",
            "error": f"Failed to parse schema file {target_file}: {e}",
        }

    # Navigate sub_path if requested
    selected_data = data
    if sub_path:
        sub_keys = [k for k in sub_path.strip().split(".") if k]
        curr = data
        for k in sub_keys:
            if isinstance(curr, dict) and k in curr:
                curr = curr[k]
            elif isinstance(curr, dict) and "properties" in curr and k in curr["properties"]:
                curr = curr["properties"][k]
            else:
                return {
                    "status": "ERROR",
                    "schema_name": target_file[:-5],
                    "error": f"Sub-path '{sub_path}' (key '{k}') not found in schema.",
                    "available_properties": list(curr.keys()) if isinstance(curr, dict) else [],
                }
        selected_data = curr

    if resolve_refs and isinstance(selected_data, (dict, list)):
        selected_data = resolve_schema_refs(selected_data, schema_dir)

    res_dict = {
        "status": "SUCCESS",
        "schema_name": target_file[:-5],
        "schema_file": target_path,
        "id": data.get("$id"),
        "title": data.get("title", target_file[:-5]),
        "description": data.get("description", ""),
        "required": data.get("required", []),
        "sub_path": sub_path,
        "resolved_refs": resolve_refs,
        "schema": selected_data,
    }
    if substituted_for:
        res_dict["substituted_for"] = substituted_for
    return res_dict
