"""Tests for GUMMI Server REST API, static assets, and backend database integrations."""

import json
import os
import sys
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
from gummi.src.uufi import GummiUUFIClient
from gummi.src.server import GummiServer, ThreadingHTTPServer, GummiRequestHandler


@pytest.fixture(scope="module")
def gummi_server():
    """Spawns an in-process GUMMI server on a dynamic port."""
    # Find an open port
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


class TestGummiServer:
    """Test suite for GUMMI HTTP REST API and UI assets."""

    def test_static_index_html(self, gummi_server):
        with urllib.request.urlopen(f"{gummi_server}/") as resp:
            assert resp.status == 200
            content = resp.read().decode("utf-8")
            assert "GUMMI" in content
            assert "Portfolio Overview" in content
            assert "Devices Explorer" in content
            assert "Bridgehead Admin" in content

    def test_static_css_and_js(self, gummi_server):
        with urllib.request.urlopen(f"{gummi_server}/css/app.css") as resp:
            assert resp.status == 200
            assert "var(--primary-color)" in resp.read().decode("utf-8")

        with urllib.request.urlopen(f"{gummi_server}/js/app.js") as resp:
            assert resp.status == 200
            assert "GUMMI Frontend" in resp.read().decode("utf-8")

    def test_api_system_capabilities(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/system/capabilities")
        assert data.get("environment") == "LOCAL_BRIDGEHEAD"
        assert "portfolio" in data.get("features", [])
        assert "devices" in data.get("features", [])
        assert "managed_rollout" in data.get("features", [])

    def test_api_bridgehead_status(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/bridgehead/status")
        assert "overall_status" in data
        assert "components" in data
        assert "postgres" in data["components"]
        assert "influxdb" in data["components"]
        assert "mqtt_broker" in data["components"]

    def test_api_portfolio_summary(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/portfolio/summary")
        assert "device_counts" in data
        assert "total" in data["device_counts"]
        assert "online" in data["device_counts"]
        assert "offline" in data["device_counts"]
        assert "error" in data["device_counts"]

    def test_api_portfolio_alerts(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/portfolio/alerts?limit=5")
        assert "alerts" in data
        assert isinstance(data["alerts"], list)

    def test_api_devices_list(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/devices?limit=10&offset=0")
        assert "total" in data
        assert "devices" in data
        assert isinstance(data["devices"], list)

    def test_api_device_detail(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/devices/ZZ-TRI-FECTA/AHU-1")
        assert data.get("registry_id") == "ZZ-TRI-FECTA"
        assert data.get("device_id") == "AHU-1"
        assert "metadata" in data
        assert "state" in data
        assert "config" in data

    def test_api_config_publish(self, gummi_server):
        payload = {
            "sub_folder": "system",
            "payload": {
                "system": {
                    "software": {"system": "2.5.1"}
                }
            }
        }
        data = http_post_json(f"{gummi_server}/api/devices/ZZ-TRI-FECTA/AHU-1/config", payload)
        assert "transaction_id" in data
        assert data.get("status") in ("DISPATCHED", "SIMULATED")

    def test_api_rollout_lifecycle(self, gummi_server):
        # 1. Create Rollout
        create_payload = {
            "name": "Upgrade AHU Fleet",
            "target_filter": {"make": "Acme"},
            "target_payload": {"system": {"software": {"system": "2.5.2"}}},
            "batch_size": 5,
        }
        created = http_post_json(f"{gummi_server}/api/rollouts", create_payload)
        assert "id" in created
        assert created.get("name") == "Upgrade AHU Fleet"
        assert created.get("status") == "RUNNING"
        rollout_id = created["id"]

        # 2. List Rollouts
        rollouts = http_get_json(f"{gummi_server}/api/rollouts")
        assert any(r["id"] == rollout_id for r in rollouts)

        # 3. Pause Rollout
        paused = http_post_json(f"{gummi_server}/api/rollouts/{rollout_id}/pause", {})
        assert paused.get("status") == "PAUSED"

        # 4. Cancel Rollout
        cancelled = http_post_json(f"{gummi_server}/api/rollouts/{rollout_id}/cancel", {})
        assert cancelled.get("status") == "CANCELLED"
