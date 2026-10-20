"""Tests for the lightweight device listing used by the Sequencer device picker.

Some sites hold more than 11,000 devices. The picker needs only
ids plus gateway/proxy flags, so it must not pay for the full inventory
(system, hardware, and every point) that /api/devices builds for the Devices
view. results_commit likewise needs only the id set.
"""

import json
import os
import threading
import urllib.parse
import urllib.request

import pytest

from workbench.server import discovery, results_commit
from workbench.server.gateway import create_gateway

SITE_MODEL = "sites/udmi_site_model"
UDMI_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def _site(tmp_path):
    site = tmp_path / "site"
    (site / "devices").mkdir(parents=True)
    (site / "cloud_iot_config.json").write_text(
        json.dumps({"iot_provider": "mqtt", "project_id": "udmis"}), encoding="utf-8"
    )

    def device(device_id, metadata):
        path = site / "devices" / device_id
        path.mkdir()
        if metadata is not None:
            content = metadata if isinstance(metadata, str) else json.dumps(metadata)
            (path / "metadata.json").write_text(content, encoding="utf-8")

    device("GAT-1", {
        "gateway": {"proxy_ids": ["DEV-2"]},
        "pointset": {"points": {"p1": {}, "p2": {}}},
        "system": {"hardware": {"make": "ACME"}},
    })
    device("DEV-2", {"gateway": {"gateway_id": "GAT-1"}, "pointset": {"points": {"x": {}}}})
    device("PLAIN-3", {"system": {}})
    device("NOMETA-4", None)
    device("BROKEN-5", "{ not json")
    device("LIST-6", "[]")
    (site / "devices" / "stray-file.txt").write_text("not a device", encoding="utf-8")
    return site


def test_device_ids_match_the_full_listing(tmp_path):
    site = _site(tmp_path)

    ids = discovery.list_device_ids(str(tmp_path), str(site))

    assert ids == ["BROKEN-5", "DEV-2", "GAT-1", "LIST-6", "NOMETA-4", "PLAIN-3"]


def test_device_ids_on_real_site_equal_list_devices():
    full = [d["device_id"] for d in discovery.list_devices(UDMI_ROOT, SITE_MODEL)]
    assert discovery.list_device_ids(UDMI_ROOT, SITE_MODEL) == full


def test_device_ids_fail_explicitly_without_devices_dir(tmp_path):
    site = tmp_path / "empty_site"
    site.mkdir()
    (site / "cloud_iot_config.json").write_text("{}", encoding="utf-8")

    with pytest.raises(discovery.DiscoveryError, match="Devices directory not found"):
        discovery.list_device_ids(str(tmp_path), str(site))


def test_summaries_carry_only_picker_fields(tmp_path):
    site = _site(tmp_path)

    summaries = {s["device_id"]: s for s in discovery.list_device_summaries(str(tmp_path), str(site))}

    assert summaries["GAT-1"] == {"device_id": "GAT-1", "is_gateway": True, "gateway_id": None}
    assert summaries["DEV-2"] == {"device_id": "DEV-2", "is_gateway": False, "gateway_id": "GAT-1"}
    assert summaries["PLAIN-3"] == {"device_id": "PLAIN-3", "is_gateway": False, "gateway_id": None}
    assert summaries["NOMETA-4"] == {"device_id": "NOMETA-4", "is_gateway": False, "gateway_id": None}
    assert set(summaries["BROKEN-5"]) == {"device_id", "error"}
    assert "Invalid metadata.json" in summaries["BROKEN-5"]["error"]
    assert "expected a JSON object" in summaries["LIST-6"]["error"]
    for summary in summaries.values():
        assert "points" not in summary and "point_count" not in summary


def test_summaries_agree_with_full_listing_on_real_site():
    full = {d["device_id"]: d for d in discovery.list_devices(UDMI_ROOT, SITE_MODEL)}
    summaries = discovery.list_device_summaries(UDMI_ROOT, SITE_MODEL)

    assert [s["device_id"] for s in summaries] == list(full)
    for summary in summaries:
        source = full[summary["device_id"]]
        assert summary["is_gateway"] == source["is_gateway"]
        assert summary["gateway_id"] == source["gateway_id"]


def test_summary_endpoint_serves_the_light_listing(tmp_path):
    server = create_gateway(host="127.0.0.1", port=0, config_path=str(tmp_path / "wb.json"))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        base = f"http://127.0.0.1:{server.server_address[1]}"
        query = urllib.parse.urlencode({"site_model": SITE_MODEL})
        with urllib.request.urlopen(f"{base}/api/devices/summary?{query}", timeout=30) as resp:
            body = json.loads(resp.read().decode("utf-8"))
        with urllib.request.urlopen(f"{base}/api/devices?{query}", timeout=30) as resp:
            full = json.loads(resp.read().decode("utf-8"))
    finally:
        server.shutdown()
        server.server_close()

    assert body["site_model"] == SITE_MODEL
    assert [d["device_id"] for d in body["devices"]] == [d["device_id"] for d in full["devices"]]
    assert all(set(d) == {"device_id", "is_gateway", "gateway_id"} for d in body["devices"])
    assert any("points" in d for d in full["devices"]), "/api/devices must stay the full listing"


def test_commit_attribution_needs_no_metadata_parsing(tmp_path, monkeypatch):
    """`known` flags come from directory names; list_devices is never called."""
    site = _site(tmp_path)

    def forbidden(*_args, **_kwargs):
        raise AssertionError("results_commit must not parse every metadata.json")

    monkeypatch.setattr(discovery, "list_devices", forbidden)

    devices, site_changes = results_commit._attribute(
        str(tmp_path),
        str(site),
        [
            "devices/GAT-1/out/state.json",
            "devices/GHOST-9/out/state.json",
            "site_metadata.json",
        ],
    )

    assert devices == [
        {"device_id": "GAT-1", "files": ["devices/GAT-1/out/state.json"], "file_count": 1, "known": True},
        {"device_id": "GHOST-9", "files": ["devices/GHOST-9/out/state.json"], "file_count": 1, "known": False},
    ]
    assert site_changes == ["site_metadata.json"]
