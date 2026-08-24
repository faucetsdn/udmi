"""Standalone test suite for GUMMI & Butler Discovery, Mapping, and Proposal Lifecycle."""

import json
import os
import sys
import tempfile
import threading
import time
from typing import Any, Dict
import urllib.request
import pytest

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gummi"))
sys.path.insert(0, REPO_ROOT)

from gummi.src.db import GummiDB
from gummi.src.server import GummiServer, ThreadingHTTPServer, GummiRequestHandler
from butler.src.mapping import run_mapping


@pytest.fixture(scope="module")
def gummi_server():
    """Spawns an in-process GUMMI server on a dynamic port."""
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()

    server = GummiServer(host="127.0.0.1", port=port)
    server_address = (server.host, server.port)
    httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
    httpd.daemon_threads = True
    httpd.db = server.db
    httpd.uufi = server.uufi
    server.httpd = httpd

    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    time.sleep(0.2)

    base_url = f"http://127.0.0.1:{port}"
    yield base_url

    try:
        httpd.server_close()
    except Exception:
        pass
    server.uufi.stop()


def http_get_json(url: str) -> Dict[str, Any]:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200
        return json.loads(resp.read().decode("utf-8"))


def http_post_json(url: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


class TestGummiMappingLifecycle:
    """Test suite covering the full Model -> Discovery -> Proposal mapping lifecycle."""

    def test_gummi_db_populate_and_get_messages(self):
        """Validates that GummiDB can populate and query the 3-stage message lifecycle."""
        db = GummiDB()
        res = db.populate_mapping_scenario(registry_id="ZZ-TRI-FECTA")
        assert res["status"] == "SUCCESS"
        assert res["records_inserted"] == 4

        messages = db.get_device_messages("ZZ-TRI-FECTA", "AHU-22")
        assert len(messages) >= 3

        # 1. Base Model
        model_msg = next((m for m in messages if m["sub_type"] == "model"), None)
        assert model_msg is not None
        assert model_msg["sub_folder"] == "system"
        assert "families" in model_msg["payload"]["localnet"]

        # 2. Discovery Event
        disc_msg = next((m for m in messages if m["sub_type"] == "events"), None)
        assert disc_msg is not None
        assert disc_msg["sub_folder"] == "discovery"
        assert disc_msg["payload"]["families"]["vendor"]["addr"] == "0x68"

        # 3. Reconciled Proposal
        prop_msg = next((m for m in messages if m["sub_type"] == "propose"), None)
        assert prop_msg is not None
        assert prop_msg["sub_folder"] in ("localnet", "pointset")
        assert prop_msg["updateFrom"] == model_msg["timestamp"]
        assert "families" in prop_msg["payload"] or "points" in prop_msg["payload"]

    def test_api_device_messages_endpoint(self, gummi_server):
        """Validates GET /api/devices/{reg}/{dev}/messages REST endpoint."""
        url = f"{gummi_server}/api/devices/ZZ-TRI-FECTA/AHU-22/messages"
        data = http_get_json(url)

        assert data["registry_id"] == "ZZ-TRI-FECTA"
        assert data["device_id"] == "AHU-22"
        assert "messages" in data
        assert len(data["messages"]) >= 3

        types = [m["sub_type"] for m in data["messages"]]
        assert "model" in types
        assert "events" in types
        assert "propose" in types

    def test_api_mapping_seed_trigger(self, gummi_server):
        """Validates POST /api/mapping/run endpoint."""
        url = f"{gummi_server}/api/mapping/run"
        res = http_post_json(url, {"registry_id": "ZZ-TRI-FECTA"})

        assert res["status"] == "SUCCESS"
        assert res["records_inserted"] == 4
        assert len(res["messages"]) == 4

    def test_mapping_engine_proposal_generation(self):
        """Tests that run_mapping() generates site model updates with complete proposal metadata."""
        with tempfile.TemporaryDirectory() as tmp_site:
            dev_dir = os.path.join(tmp_site, "devices", "AHU-22")
            os.makedirs(dev_dir, exist_ok=True)
            meta_path = os.path.join(dev_dir, "metadata.json")

            base_timestamp = "2026-08-20T10:00:00Z"
            base_meta = {
                "version": "1.5.7",
                "timestamp": base_timestamp,
                "system": {"serial_no": "AHU-22-001"},
                "localnet": {
                    "families": {
                        "vendor": {"addr": "0x65"}
                    }
                }
            }
            with open(meta_path, "w") as f:
                json.dump(base_meta, f)

            # Invoke mapping without DB connection to test file/model update handling
            run_mapping(conn_spec=None, registry_id="ZZ-TRI-FECTA", site_model=tmp_site)

            with open(meta_path, "r") as f:
                updated_meta = json.load(f)

            assert updated_meta["version"] == "1.5.7"
            assert "timestamp" in updated_meta
            assert updated_meta["system"]["serial_no"] == "AHU-22-001"
