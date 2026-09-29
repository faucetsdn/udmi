"""Unit tests for MCP Server and JSON-RPC 2.0 protocol handling."""

import asyncio
import json
import pytest
from mantis.mcp_server import MCPServer
from mantis.session import SessionManager


@pytest.fixture
def mcp_server():
    mgr = SessionManager()
    return MCPServer(session_mgr=mgr)


def test_mcp_initialize(mcp_server):
    req = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {},
    }
    resp = mcp_server.handle_request(req)
    assert resp["jsonrpc"] == "2.0"
    assert resp["id"] == 1
    assert resp["result"]["serverInfo"]["name"] == "udmi-test-infra"
    assert resp["result"]["protocolVersion"] == "2024-11-05"


def test_mcp_ping_and_notifications(mcp_server):
    ping_req = {"jsonrpc": "2.0", "id": 2, "method": "ping"}
    ping_resp = mcp_server.handle_request(ping_req)
    assert ping_resp == {"jsonrpc": "2.0", "id": 2, "result": {}}

    notif_req = {"jsonrpc": "2.0", "method": "notifications/initialized"}
    assert mcp_server.handle_request(notif_req) is None


def test_mcp_tools_list(mcp_server):
    req = {"jsonrpc": "2.0", "id": 3, "method": "tools/list", "params": {}}
    resp = mcp_server.handle_request(req)
    tools = [t["name"] for t in resp["result"]["tools"]]
    assert "ensure_test_setup" in tools
    assert "inspect_udmi_schema" in tools
    assert "inspect_site_model" in tools
    assert "patch_site_model" in tools
    assert "get_test_timeline" in tools
    assert "compare_test_runs" in tools
    assert "diagnose_test_failure" in tools


def test_mcp_tool_call_inspect_schema(mcp_server):
    req = {
        "jsonrpc": "2.0",
        "id": 4,
        "method": "tools/call",
        "params": {
            "name": "inspect_udmi_schema",
            "arguments": {"schema_name": "pointset"},
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 4
    assert resp["result"]["isError"] is False
    content_text = resp["result"]["content"][0]["text"]
    data = json.loads(content_text)
    assert data["status"] == "SUCCESS"
    assert "pointset" in data["schema_name"]


def test_mcp_tool_call_validation_error(mcp_server):
    # Calling ensure_test_setup without required test_id
    req = {
        "jsonrpc": "2.0",
        "id": 5,
        "method": "tools/call",
        "params": {
            "name": "ensure_test_setup",
            "arguments": {},
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 5
    assert resp["result"]["isError"] is True
    assert "validation error" in resp["result"]["content"][0]["text"].lower() or "missing" in resp["result"]["content"][0]["text"].lower()


@pytest.mark.anyio
async def test_mcp_async_handle_request(mcp_server):
    req = {
        "jsonrpc": "2.0",
        "id": 6,
        "method": "tools/call",
        "params": {
            "name": "inspect_site_model",
            "arguments": {"site_model": "sites/udmi_site_model"},
        },
    }
    resp = await mcp_server.handle_request_async(req)
    assert resp["id"] == 6
    assert resp["result"]["isError"] is False
    content_text = resp["result"]["content"][0]["text"]
    data = json.loads(content_text)
    assert data["status"] == "SUCCESS"
    assert "AHU-1" in data["devices"]


def test_mcp_http_transport(mcp_server):
    import threading
    import urllib.request

    httpd = mcp_server.run_http(host="127.0.0.1", port=0)
    port = httpd.server_address[1]

    server_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    server_thread.start()

    try:
        # 1. Health check
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/health") as resp:
            assert resp.status == 200
            data = json.loads(resp.read().decode("utf-8"))
            assert data["status"] == "OK"
            assert data["server"] == "udmi-test-infra"
            assert data["tools_count"] > 0

        # 2. SSE endpoint
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/sse") as resp:
            assert resp.status == 200
            assert "text/event-stream" in resp.headers.get("Content-Type", "")
            sse_data = resp.readline().decode("utf-8")
            assert "event: endpoint" in sse_data

        # 3. JSON-RPC POST
        rpc_req = json.dumps({
            "jsonrpc": "2.0",
            "id": 10,
            "method": "tools/list",
            "params": {},
        }).encode("utf-8")
        req = urllib.request.Request(
            f"http://127.0.0.1:{port}/message",
            data=rpc_req,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req) as resp:
            assert resp.status == 200
            rpc_resp = json.loads(resp.read().decode("utf-8"))
            assert rpc_resp["id"] == 10
            tools = [t["name"] for t in rpc_resp["result"]["tools"]]
            assert "inspect_udmi_schema" in tools

    finally:
        httpd.shutdown()
        httpd.server_close()


def test_mcp_tool_call_run_sequencer_test(mcp_server, monkeypatch):
    from unittest.mock import MagicMock
    mock_run = MagicMock(return_value={"status": "LAUNCHED", "test_name": "pointset_publish"})
    monkeypatch.setattr("mantis.tools.sequencer.run_sequencer_test", mock_run)

    req = {
        "jsonrpc": "2.0",
        "id": 20,
        "method": "tools/call",
        "params": {
            "name": "run_sequencer_test",
            "arguments": {
                "test_name": "pointset_publish",
                "device_id": "AHU-1",
                "target_spec": "//mqtt/localhost:28430",
            },
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 20
    assert resp["result"]["isError"] is False
    mock_run.assert_called_once()


def test_mcp_tool_call_start_session_process(mcp_server, monkeypatch):
    from unittest.mock import MagicMock
    mock_start = MagicMock(return_value={"status": "STARTED", "window": "sequencer"})
    monkeypatch.setattr("mantis.tools.process.start_session_process", mock_start)

    req = {
        "jsonrpc": "2.0",
        "id": 21,
        "method": "tools/call",
        "params": {
            "name": "start_session_process",
            "arguments": {
                "test_id": "dev_1",
                "window": "sequencer",
                "command": "bin/sequencer sites/udmi_site_model //mqtt/localhost:28430 AHU-1",
            },
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 21
    assert resp["result"]["isError"] is False
    mock_start.assert_called_once()


def test_mcp_tool_call_query_database(mcp_server, monkeypatch):
    from unittest.mock import MagicMock
    mock_query = MagicMock(return_value={"status": "SUCCESS", "results": [{"val": 42}]})
    monkeypatch.setattr("mantis.tools.database.query_database", mock_query)

    req = {
        "jsonrpc": "2.0",
        "id": 22,
        "method": "tools/call",
        "params": {
            "name": "query_database",
            "arguments": {
                "test_id": "dev_1",
                "database_type": "influx",
                "query": "SELECT * FROM pointset;",
            },
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 22
    assert resp["result"]["isError"] is False
    mock_query.assert_called_once()


def test_mcp_tool_call_publish_mqtt_message(mcp_server, monkeypatch):
    from unittest.mock import MagicMock
    mock_pub = MagicMock(return_value={"status": "PUBLISHED", "topic": "events/pointset"})
    monkeypatch.setattr("mantis.tools.mqtt.publish_mqtt_message", mock_pub)

    req = {
        "jsonrpc": "2.0",
        "id": 23,
        "method": "tools/call",
        "params": {
            "name": "publish_mqtt_message",
            "arguments": {
                "test_id": "dev_1",
                "topic": "events/pointset",
                "payload": "{}",
                "target_spec": "//mqtt/localhost:28430",
            },
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 23
    assert resp["result"]["isError"] is False
    mock_pub.assert_called_once()


def test_mcp_tool_call_read_udmi_file(mcp_server, tmp_path):
    sample = tmp_path / "test.txt"
    sample.write_text("Line 1\nLine 2\n")

    req = {
        "jsonrpc": "2.0",
        "id": 24,
        "method": "tools/call",
        "params": {
            "name": "read_udmi_file",
            "arguments": {
                "file_path": "test.txt",
                "udmi_root": str(tmp_path),
            },
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 24
    assert resp["result"]["isError"] is False
    data = json.loads(resp["result"]["content"][0]["text"])
    assert data["status"] == "SUCCESS"
    assert "Line 1" in data["content"]


def test_mcp_tool_call_detect_log_anomalies(mcp_server, tmp_path):
    run_dir = tmp_path / "run_test"
    run_dir.mkdir()
    (run_dir / "sequence.log").write_text("2026-08-26T12:45:00Z ignoring stale state update\n")

    req = {
        "jsonrpc": "2.0",
        "id": 25,
        "method": "tools/call",
        "params": {
            "name": "detect_log_anomalies",
            "arguments": {
                "run_dir": str(run_dir),
            },
        },
    }
    resp = mcp_server.handle_request(req)
    assert resp["id"] == 25
    assert resp["result"]["isError"] is False
    data = json.loads(resp["result"]["content"][0]["text"])
    assert data["status"] == "SUCCESS"
    assert data["total_anomalies"] >= 1


