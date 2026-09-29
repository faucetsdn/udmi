"""Unit tests for mantis.tools.schemas."""

import pytest
from mantis.tools.schemas import inspect_udmi_schema, list_udmi_schemas


def test_list_udmi_schemas():
    schemas = list_udmi_schemas()
    assert len(schemas) > 0
    names = [s["name"] for s in schemas]
    assert "pointset" in names or "events_pointset" in names
    assert "metadata" in names


def test_inspect_udmi_schema_valid():
    res = inspect_udmi_schema("pointset")
    assert res["status"] == "SUCCESS"
    assert "schema" in res
    assert "properties" in res["schema"]


def test_inspect_udmi_schema_sub_path():
    res = inspect_udmi_schema("pointset", sub_path="properties.points")
    assert res["status"] == "SUCCESS"
    assert "schema" in res


def test_inspect_udmi_schema_list_all():
    res = inspect_udmi_schema("list")
    assert res["status"] == "SUCCESS"
    assert res["count"] > 0
    assert "schemas" in res


def test_inspect_udmi_schema_nonexistent():
    res = inspect_udmi_schema("nonexistent_schema_xyz")
    assert res["status"] == "ERROR"
    assert "not found" in res["error"]


def test_inspect_udmi_schema_resolve_refs(tmp_path):
    schema_dir = tmp_path / "schema"
    schema_dir.mkdir()

    common_schema = {
        "definitions": {
            "timestamp": {
                "type": "string",
                "format": "date-time",
                "description": "ISO timestamp"
            }
        }
    }
    target_schema = {
        "title": "Target Schema",
        "properties": {
            "time": {
                "$ref": "file:common.json#/definitions/timestamp"
            }
        }
    }

    import json
    (schema_dir / "common.json").write_text(json.dumps(common_schema))
    (schema_dir / "target.json").write_text(json.dumps(target_schema))

    # Without resolve_refs
    res_unresolved = inspect_udmi_schema("target", resolve_refs=False, udmi_root=str(tmp_path))
    assert res_unresolved["status"] == "SUCCESS"
    assert "$ref" in res_unresolved["schema"]["properties"]["time"]

    # With resolve_refs
    res_resolved = inspect_udmi_schema("target", resolve_refs=True, udmi_root=str(tmp_path))
    assert res_resolved["status"] == "SUCCESS"
    resolved_prop = res_resolved["schema"]["properties"]["time"]
    assert "$ref" not in resolved_prop
    assert resolved_prop["type"] == "string"
    assert resolved_prop["format"] == "date-time"
    assert resolved_prop["description"] == "ISO timestamp"

