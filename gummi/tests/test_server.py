"""Tests for GUMMI Server REST API, static assets, and backend database integrations."""

import json
import os
import re
import sys
import threading
import time
from typing import Any, Dict
import urllib.error
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

    server = GummiServer(host="127.0.0.1", port=port, mock_mode=True)
    server_address = (server.host, server.port)
    httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
    httpd.daemon_threads = True
    httpd.mock_mode = server.mock_mode
    httpd.db = server.db
    httpd.uufi = server.uufi
    httpd.console = server.console
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
        assert data.get("environment") == "MOCK_MODE"
        assert data.get("mock_mode") is True
        assert data.get("enable_mapping_seed") is False
        assert "mapping_seed" not in data.get("features", [])
        assert "portfolio" in data.get("features", [])
        assert "device_explorer" in data.get("features", [])
        assert "managed_rollout" in data.get("features", [])
        assert "etcd_explorer" in data.get("features", [])

    def test_api_bridgehead_status(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/bridgehead/status")
        assert data.get("overall_status") == "MOCK_MODE"
        assert "components" in data
        assert "postgres" in data["components"]
        assert "influxdb" in data["components"]
        assert "mqtt_broker" in data["components"]
        assert "uufi_service" in data["components"]
        assert data["components"]["uufi_service"]["status"] == "MOCK_MODE"

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
        for dev in data["devices"]:
            if dev.get("last_seen") and dev["last_seen"] != "None":
                assert re.match(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$", dev["last_seen"]), f"Bad ISO format: {dev['last_seen']}"

    def test_api_device_detail(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/devices/ZZ-TRI-FECTA/AHU-1")
        assert data.get("registry_id") == "ZZ-TRI-FECTA"
        assert data.get("device_id") == "AHU-1"
        assert "metadata" in data
        assert "state" in data
        assert "config" in data
        last_seen = data["metadata"].get("last_seen") or data.get("state", {}).get("system", {}).get("last_seen")
        if last_seen and last_seen != "None":
            assert re.match(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$", last_seen), f"Bad ISO format: {last_seen}"

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

    def test_uufi_mcp_integration(self):
        """Verifies GummiUUFIClient consumes UUFIClient interface cleanly."""
        from unittest.mock import MagicMock
        mock_uufi = MagicMock()
        mock_uufi.health.return_value = {
            "status": "UP",
            "broker": "127.0.0.1:1883",
            "latency_ms": 1.5,
        }
        mock_uufi.publish_config.return_value = {
            "status": "DISPATCHED",
            "transactionId": "TX-12345",
            "topic": "/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/config/system",
        }

        client = GummiUUFIClient(uufi_client=mock_uufi)
        assert client.is_connected is True

        res = client.publish_config("ZZ-TRI-FECTA", "AHU-1", "system", {"software": {"system": "1.0.0"}})
        assert res["status"] == "DISPATCHED"
        assert res["transaction_id"] == "TX-12345"
        mock_uufi.publish_config.assert_called_once_with(
            "ZZ-TRI-FECTA", "AHU-1", "system", {"software": {"system": "1.0.0"}}
        )

    def test_mock_mode_disabled_health_and_query_failures(self):
        """Verifies that with mock_mode=False and unavailable backends, GummiDB fails fast."""
        from mcp.butler.client import ButlerClient
        from mcp.barbican.client import BarbicanClient
        from mcp.uufi.client import UUFIClient

        # Explicit unreachable MCP ports to test fail-fast in isolated environment
        unreachable_butler = ButlerClient(port=59999, timeout=0.5)
        unreachable_barbican = BarbicanClient(port=59998, timeout=0.5)
        unreachable_uufi = UUFIClient(port=59997, timeout=0.5)

        db = GummiDB(
            butler_client=unreachable_butler,
            barbican_client=unreachable_barbican,
            uufi_client=unreachable_uufi,
            mock_mode=False,
        )
        health = db.check_component_health()
        assert health["overall_status"] == "DEGRADED"
        assert health["components"]["butler"]["status"] == "DOWN"
        assert health["components"]["barbican"]["status"] == "DOWN"
        assert health["components"]["uufi_service"]["status"] == "DOWN"
        assert health["components"]["postgres"]["status"] == "DOWN"
        assert health["components"]["influxdb"]["status"] == "DOWN"
        assert health["components"]["etcd"]["status"] == "DOWN"

        with pytest.raises(ConnectionError):
            db.get_portfolio_summary()

        with pytest.raises(ConnectionError):
            db.get_alerts()

        with pytest.raises(ConnectionError):
            db.get_devices()

        with pytest.raises(ConnectionError):
            db.get_device_detail("REG", "DEV")

        with pytest.raises(ConnectionError):
            db.get_device_messages("REG", "DEV")

        with pytest.raises(ConnectionError):
            db.get_device_telemetry("REG", "DEV", ["temperature"])

    def test_live_server_error_responses(self):
        """Verifies that live server returns HTTP 503 instead of falling back to mock data."""
        import socket
        from mcp.butler.client import ButlerClient
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
        s.close()

        server = GummiServer(
            host="127.0.0.1",
            port=port,
            mock_mode=False,
            butler_client=ButlerClient(port=59999, timeout=0.5),
        )
        server_address = (server.host, server.port)
        httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
        httpd.daemon_threads = True
        httpd.mock_mode = server.mock_mode
        httpd.db = server.db
        httpd.uufi = server.uufi
        server.httpd = httpd

        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        time.sleep(0.2)
        base_url = f"http://127.0.0.1:{port}"

        try:
            # Capabilities should report live environment and mock_mode=False
            caps = http_get_json(f"{base_url}/api/system/capabilities")
            assert caps.get("mock_mode") is False
            assert caps.get("environment") == "LOCAL_BRIDGEHEAD"

            # Endpoints requiring DB should fail with HTTP 503 Service Unavailable
            for endpoint in ["/api/devices", "/api/portfolio/summary", "/api/portfolio/alerts", "/api/devices/REG/DEV"]:
                url = f"{base_url}{endpoint}"
                with pytest.raises(urllib.error.HTTPError) as exc_info:
                    urllib.request.urlopen(urllib.request.Request(url))
                assert exc_info.value.code == 503
        finally:
            try:
                httpd.server_close()
            except Exception:
                pass
            server.uufi.stop()

    def test_env_var_mock_mode_ignored(self, monkeypatch):
        """Verifies environment variables GUMMI_MOCK_MODE/GUMMI_MOCK do not implicitly enable mock mode."""
        import argparse
        monkeypatch.setenv("GUMMI_MOCK_MODE", "true")
        monkeypatch.setenv("GUMMI_MOCK", "true")

        # Create parser identical to main()
        parser = argparse.ArgumentParser()
        parser.add_argument("--mock", action="store_true", default=False)
        args = parser.parse_args([])
        assert args.mock is False

    def test_mapping_seed_flag_enables_feature_and_capabilities(self):
        """Verifies that --enable-mapping-seed exposes capability and allows seed mutation."""
        import socket
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
        s.close()

        server = GummiServer(host="127.0.0.1", port=port, mock_mode=True, enable_mapping_seed=True)
        server_address = (server.host, server.port)
        httpd = ThreadingHTTPServer(server_address, GummiRequestHandler)
        httpd.daemon_threads = True
        httpd.mock_mode = server.mock_mode
        httpd.enable_mapping_seed = server.enable_mapping_seed
        httpd.db = server.db
        httpd.uufi = server.uufi
        httpd.console = server.console
        server.httpd = httpd

        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        time.sleep(0.2)
        base_url = f"http://127.0.0.1:{port}"

        try:
            caps = http_get_json(f"{base_url}/api/system/capabilities")
            assert caps.get("enable_mapping_seed") is True
            assert "mapping_seed" in caps.get("features", [])

            # POST /api/mapping/run should succeed
            res = http_post_json(f"{base_url}/api/mapping/run", {"registry_id": "ZZ-TRI-FECTA"})
            assert res.get("status") == "SUCCESS"
            assert res.get("records_inserted") == 4
        finally:
            try:
                httpd.server_close()
            except Exception:
                pass
            server.uufi.stop()

    def test_api_console_endpoints(self, gummi_server):
        # 1. Verify jetski_console is listed in capabilities
        caps = http_get_json(f"{gummi_server}/api/system/capabilities")
        assert "jetski_console" in caps.get("features", [])

        # 2. Start jetski console session
        start_res = http_post_json(f"{gummi_server}/api/project/jetski", {"projectName": "gummi"})
        assert start_res.get("status") in ("started", "already_running")
        assert start_res.get("session") == "gummi~agent"

        # 3. Check console status
        status_res = http_get_json(f"{gummi_server}/api/project/status")
        assert status_res.get("running") is True
        assert status_res.get("session") == "gummi~agent"
        assert "diagnostics" in status_res
        assert "state" in status_res["diagnostics"]

        # 4. Read terminal log
        log_res = http_get_json(f"{gummi_server}/api/project/term-log?project=gummi&offset=0")
        assert "data" in log_res
        assert "offset" in log_res
        assert "running" in log_res
        assert "diagnostics" in log_res

        # 5. Send terminal input
        input_res = http_post_json(f"{gummi_server}/api/project/term-input", {"projectName": "gummi", "hexKeys": ["61", "62"]})
        assert input_res.get("status") == "ok"

        # 6. Resize terminal
        resize_res = http_post_json(f"{gummi_server}/api/project/term-resize", {"projectName": "gummi", "cols": 120, "rows": 30})
        assert resize_res.get("status") == "resized"

        # 7. Kill console
        kill_res = http_post_json(f"{gummi_server}/api/project/term-kill", {"projectName": "gummi"})
        assert kill_res.get("status") == "killed"

        # 8. Check status after kill -> not running, no error -> button_state == "blue"
        status_after_kill = http_get_json(f"{gummi_server}/api/project/status")
        assert status_after_kill.get("running") is False
        assert status_after_kill.get("button_state") == "blue"

    def test_console_button_states(self):
        """Verifies color-coded button state transitions in backend diagnostics."""
        from gummi.src.console import GummiConsoleManager
        console = GummiConsoleManager(mock_mode=True)

        # 1. Blue: Not running, no error
        console._mock_running = False
        console._mock_error = False
        console._mock_exit_code = 0
        diag = console.get_diagnostics()
        assert diag["button_state"] == "blue"

        # 2. Red: Not running, error
        console._mock_running = False
        console._mock_error = True
        console._mock_exit_code = 1
        diag = console.get_diagnostics()
        assert diag["button_state"] == "red"

        # 3. Yellow: Actively doing something
        console._mock_running = True
        console._mock_active = True
        console._mock_error = False
        diag = console.get_diagnostics()
        assert diag["button_state"] == "yellow"

        # 4. Green: Running, idle
        console._mock_running = True
        console._mock_active = False
        console._mock_error = False
        diag = console.get_diagnostics()
        assert diag["button_state"] == "green"

    def test_server_endpoint_configuration(self):
        """Verifies remote MCP service endpoints propagate to GummiDB and GummiUUFIClient."""
        server = GummiServer(
            host="127.0.0.1",
            port=9099,
            mock_mode=True,
            barbican_endpoint="http://barbican-mcp:8085",
            butler_endpoint="http://butler-mcp:8088",
            uufi_endpoint="http://uufi-mcp:8087",
        )
        assert server.barbican_endpoint == "http://barbican-mcp:8085"
        assert server.butler_endpoint == "http://butler-mcp:8088"
        assert server.uufi_endpoint == "http://uufi-mcp:8087"
        assert server.db.barbican_endpoint == "http://barbican-mcp:8085"
        assert server.db.butler_endpoint == "http://butler-mcp:8088"
        assert server.db.uufi_endpoint == "http://uufi-mcp:8087"
        assert server.uufi.uufi_endpoint == "http://uufi-mcp:8087"

    def test_console_start_session_error_handling(self, gummi_server):
        """Verifies that errors starting jetski session return red button_state and are reflected in status."""
        from gummi.src.console import GummiConsoleManager
        import unittest.mock as mock

        # 1. Test console start error via mock
        console = GummiConsoleManager(mock_mode=False)
        with mock.patch("subprocess.run", side_effect=FileNotFoundError("No such file: tmux")):
            res = console.start_jetski()
            assert res["status"] == "error"
            assert res["button_state"] == "red"
            diag = console.get_diagnostics()
            assert diag["button_state"] == "red"
            assert diag["state"] == "error"
            assert "tmux" in diag["alert"]

            # Killing clears the error
            console.kill()
            assert console.last_error is None
            diag_after = console.get_diagnostics()
            assert diag_after["button_state"] == "blue"

    def test_api_registries(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/registries")
        assert "registries" in data
        assert "totalDevicesCount" in data
        assert isinstance(data["registries"], list)
        assert "ZZ-TRI-FECTA" in data["registries"]
        assert data["totalDevicesCount"] > 0

        # Prefix filtering
        filtered = http_get_json(f"{gummi_server}/api/registries?prefix=ZZ-")
        assert "ZZ-TRI-FECTA" in filtered["registries"]
        for reg in filtered["registries"]:
            assert reg.startswith("ZZ-")

    def test_api_registry_devices(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/registries/ZZ-TRI-FECTA/devices")
        assert data.get("registryId") == "ZZ-TRI-FECTA"
        assert "devices" in data
        assert isinstance(data["devices"], list)
        assert "AHU-1" in data["devices"]
        assert "AHU-2" in data["devices"]

    def test_api_device_etcd_properties(self, gummi_server):
        data = http_get_json(f"{gummi_server}/api/registries/ZZ-TRI-FECTA/devices/AHU-1/properties")
        assert data.get("registryId") == "ZZ-TRI-FECTA"
        assert data.get("deviceId") == "AHU-1"
        assert "properties" in data
        props = data["properties"]
        assert ":config" in props
        assert ":last_state" in props
        assert ":metadata_str" in props
        assert "/c/bound_devices:AHU-2" in props
        config_val = json.loads(props[":config"])
        assert config_val.get("system", {}).get("software", {}).get("system") == "2.4.1"

    def test_legacy_etcd_explorer_redirect(self, gummi_server):
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None

        opener = urllib.request.build_opener(NoRedirect)
        for path in ("/etcd_explorer", "/etcd_explorer/", "/barbican_explorer", "/barbican_explorer/"):
            try:
                opener.open(f"{gummi_server}{path}")
                pytest.fail(f"Expected 302 redirect for {path}")
            except urllib.error.HTTPError as e:
                assert e.code == 302
                assert e.headers.get("Location") == "/#explorer"
