"""Unit and integration tests for Butler MCP Server, Provider, and Client."""

from datetime import datetime, timezone
import json
import os
import subprocess
import sys
import threading
import time
from typing import Any, Dict, List
import unittest
from unittest.mock import MagicMock, patch
import urllib.error
import urllib.request

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "butler"))
sys.path.insert(0, REPO_ROOT)

from mcp.butler.provider import ButlerProvider
from mcp.butler.server import ButlerMcpServer, ButlerMcpHttpHandler
from mcp.butler.client import ButlerClient
from http.server import HTTPServer


class TestButlerProvider(unittest.TestCase):
    """Tests for ButlerProvider data encapsulation."""

    def setUp(self):
        self.mock_pg = MagicMock()
        self.mock_influx = MagicMock()
        self.provider = ButlerProvider(
            pg_manager=self.mock_pg,
            influx_manager=self.mock_influx,
        )

    def test_health_both_up(self):
        # PG mock
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = (1,)
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        # Influx mock
        mock_client = MagicMock()
        mock_ready = MagicMock()
        mock_ready.status = "ready"
        mock_client.ready.return_value = mock_ready
        self.mock_influx.get_client.return_value = mock_client

        h = self.provider.health()
        self.assertEqual(h["status"], "UP")
        self.assertEqual(h["service"], "butler")
        self.assertTrue(h["connected"])
        self.assertTrue(h["datastores"]["relational"])
        self.assertTrue(h["datastores"]["timeseries"])

    def test_health_degraded_and_down(self):
        # Influx down, PG up
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = (1,)
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn
        self.mock_influx.get_client.side_effect = RuntimeError("Influx down")

        h = self.provider.health()
        self.assertEqual(h["status"], "DEGRADED")
        self.assertTrue(h["datastores"]["relational"])
        self.assertFalse(h["datastores"]["timeseries"])

        # Both down
        self.mock_pg.get_connection.side_effect = RuntimeError("PG down")
        h_down = self.provider.health()
        self.assertEqual(h_down["status"], "DOWN")
        self.assertFalse(h_down["connected"])

    def test_get_discovery_events_from_messages(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        raw_payload = json.dumps({
            "version": "1.5.7",
            "family": "vendor",
            "addr": "0x68",
            "generation": "2026-09-01T12:00:00Z",
            "families": {
                "vendor": {"addr": "0x68"},
                "bacnet": {"addr": "10022"},
                "ipv4": {"addr": "192.168.1.122"},
            },
        })
        mock_cur.fetchall.return_value = [
            (101, "GAT-123", raw_payload, "2026-09-01T12:00:00Z"),
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        events = self.provider.get_discovery_events("ZZ-TRI-FECTA")
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]["id"], 101)
        self.assertEqual(events[0]["gateway_id"], "GAT-123")
        self.assertEqual(events[0]["payload"]["family"], "vendor")

    def test_get_discovered_devices(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        raw_payload = {
            "family": "vendor",
            "addr": "0x68",
            "generation": "2026-09-01T12:00:00Z",
            "families": {
                "vendor": {"addr": "0x68"},
                "bacnet": {"addr": "10022"},
                "ipv4": {"addr": "192.168.1.122"},
            },
        }
        mock_cur.fetchall.return_value = [
            (101, "GAT-123", json.dumps(raw_payload), "2026-09-01T12:00:00Z"),
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        devs = self.provider.get_discovered_devices("ZZ-TRI-FECTA")
        self.assertEqual(len(devs), 1)
        dev = devs[0]
        self.assertEqual(dev["gateway_id"], "GAT-123")
        self.assertEqual(dev["bacnet"], "10022")
        self.assertEqual(dev["ipv4"], "192.168.1.122")
        self.assertEqual(dev["vendor"], "0x68")
        self.assertEqual(dev["generation"], "2026-09-01T12:00:00Z")

    def test_get_device_messages(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        model_payload = {
            "version": "1.5.7",
            "system": {"serial_no": "AHU-22-001"},
            "localnet": {"families": {"vendor": {"addr": "0x65"}}},
        }
        prop_payload = {
            "version": "1.5.7",
            "updateFrom": "2026-08-20T10:00:00Z",
            "source": "butler",
            "transactionId": "TXN-map-01",
            "families": {"vendor": {"addr": "0x68"}},
        }
        mock_cur.fetchall.return_value = [
            (1, "2026-08-20T10:00:00Z", "ZZ-TRI-FECTA", "AHU-22", "model", "system", json.dumps(model_payload)),
            (2, "2026-08-20T10:05:00Z", "ZZ-TRI-FECTA", "AHU-22", "propose", "localnet", json.dumps(prop_payload)),
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        msgs = self.provider.get_device_messages("ZZ-TRI-FECTA", "AHU-22")
        self.assertEqual(len(msgs), 2)
        self.assertEqual(msgs[0]["sub_type"], "model")
        self.assertEqual(msgs[1]["sub_type"], "propose")
        self.assertEqual(msgs[1]["updateFrom"], "2026-08-20T10:00:00Z")
        self.assertEqual(msgs[1]["transaction_id"], "TXN-map-01")

    def test_record_message(self):
        payload = {"version": "1.5.7", "families": {"vendor": {"addr": "0x68"}}}
        res = self.provider.record_message(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            sub_type="propose",
            sub_folder="localnet",
            payload=payload,
        )
        self.assertEqual(res["status"], "SUCCESS")
        self.mock_pg.insert_row.assert_called_once()
        table_arg, row_arg = self.mock_pg.insert_row.call_args[0]
        self.assertEqual(table_arg, "udmi_messages")
        self.assertEqual(row_arg["device_id"], "AHU-22")
        self.assertEqual(row_arg["sub_type"], "propose")

    def test_telemetry_operations(self):
        # 1. Write telemetry
        self.mock_influx.write_pointset_payload.return_value = 2
        res_write = self.provider.write_telemetry(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            points={"supply_temp": 22.5, "alarm": False},
        )
        self.assertEqual(res_write["status"], "SUCCESS")
        self.assertEqual(res_write["points_written"], 2)

        # 2. Get telemetry
        mock_client = MagicMock()
        mock_query_api = MagicMock()
        mock_record = MagicMock()
        mock_record.values = {"point_name": "supply_temp"}
        mock_record.get_value.return_value = 22.5
        mock_record.get_time.return_value = datetime(2026, 9, 1, 12, 0, 0, tzinfo=timezone.utc)
        mock_record.get_field.return_value = "present_value_num"

        mock_table = MagicMock()
        mock_table.records = [mock_record]
        mock_query_api.query.return_value = [mock_table]
        mock_client.query_api.return_value = mock_query_api
        self.mock_influx.get_client.return_value = mock_client
        self.mock_influx.bucket = "home"

        telem = self.provider.get_device_telemetry(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            point_names=["supply_temp"],
        )
        self.assertEqual(telem["device_id"], "AHU-22")
        self.assertEqual(len(telem["series"]), 1)
        self.assertEqual(telem["series"][0]["point_name"], "supply_temp")
        self.assertEqual(telem["series"][0]["values"][0]["value"], 22.5)

    def test_clear_registry_mapping_data(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.rowcount = 5
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        res = self.provider.clear_registry_mapping_data("ZZ-TRI-FECTA")
        self.assertEqual(res["status"], "SUCCESS")
        self.assertGreaterEqual(res["deleted_records"], 5)

    def test_get_portfolio_summary(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.fetchone.side_effect = [
            (10, 2),  # total_devices, total_registries
            (3,),     # critical_alerts_24h
            (1,),     # error_devices
            (1,),     # active_rollouts_count
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        summary = self.provider.get_portfolio_summary()
        self.assertEqual(summary["device_counts"]["total"], 10)
        self.assertEqual(summary["device_counts"]["online"], 9)
        self.assertEqual(summary["device_counts"]["error"], 1)
        self.assertEqual(summary["registries_count"], 2)
        self.assertEqual(summary["critical_alerts_24h"], 3)
        self.assertEqual(summary["active_rollouts_count"], 1)

    def test_get_alerts(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = [
            (1, "REG-1", "DEV-1", 500, "cat", "msg", "detail", datetime(2026, 9, 1, 12, 0, 0, tzinfo=timezone.utc)),
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        alerts = self.provider.get_alerts(limit=10, min_level=500)
        self.assertEqual(len(alerts), 1)
        self.assertEqual(alerts[0]["device_id"], "DEV-1")
        self.assertEqual(alerts[0]["level"], 500)

    def test_get_devices(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = (1,)  # total count
        mock_cur.fetchall.return_value = [
            (1, "REG-1", "DEV-1", "Acme", "Model-A", "SN-1", [{"version": "1.0.0"}], datetime(2026, 9, 1, 12, 0, 0, tzinfo=timezone.utc)),
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        devs = self.provider.get_devices(limit=10, offset=0, registry_id="REG-1")
        self.assertEqual(devs["total"], 1)
        self.assertEqual(len(devs["devices"]), 1)
        self.assertEqual(devs["devices"][0]["device_id"], "DEV-1")
        self.assertEqual(devs["devices"][0]["software_version"], "1.0.0")
        self.assertEqual(devs["devices"][0]["last_seen"], "2026-09-01T12:00:00Z")

    def test_get_device_detail(self):
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.fetchone.side_effect = [
            ("Acme", "Model-A", "SN-1", "revA", "skuA", {"system": "1.0.0"}, datetime(2026, 9, 1, 12, 0, 0, tzinfo=timezone.utc)),
            ("Room-101", "Floor-1", {}),
        ]
        mock_cur.fetchall.side_effect = [
            [("temp", "applied", "C", 300, "OK", datetime(2026, 9, 1, 12, 0, 0, tzinfo=timezone.utc), datetime(2026, 9, 1, 12, 0, 0, tzinfo=timezone.utc))],
            [(500, "cat", "msg", "detail", datetime(2026, 9, 1, 12, 0, 0, tzinfo=timezone.utc))],
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        self.mock_pg.get_connection.return_value = mock_conn

        detail = self.provider.get_device_detail("REG-1", "DEV-1")
        self.assertIsNotNone(detail)
        self.assertEqual(detail["device_id"], "DEV-1")
        self.assertEqual(detail["metadata"]["last_seen"], "2026-09-01T12:00:00Z")
        self.assertEqual(detail["state"]["system"]["last_seen"], "2026-09-01T12:00:00Z")
        self.assertIn("temp", detail["state"]["pointset"]["points"])
        self.assertEqual(len(detail["events"]), 1)

    def test_rollout_lifecycle(self):
        mock_rm = MagicMock()
        self.provider.rollout_manager = mock_rm

        # 1. Create rollout
        mock_rm.create_rollout.return_value = {
            "id": 1,
            "name": "Upgrade Fleet",
            "status": "RUNNING",
        }
        created = self.provider.create_rollout(
            name="Upgrade Fleet",
            target_filter={"make": "Acme"},
            target_payload={"system": {"software": {"system": "2.0.0"}}},
            batch_size=5,
            total_devices=10,
        )
        self.assertEqual(created["id"], 1)
        self.assertEqual(created["status"], "RUNNING")
        self.assertEqual(created["name"], "Upgrade Fleet")
        mock_rm.create_rollout.assert_called_once()

        # 2. List rollouts
        mock_rm.list_rollouts.return_value = [created]
        rollouts = self.provider.list_rollouts()
        self.assertEqual(len(rollouts), 1)
        self.assertEqual(rollouts[0]["id"], 1)

        # 3. Get rollout
        mock_rm.get_rollout.return_value = created
        rollout = self.provider.get_rollout(1)
        self.assertIsNotNone(rollout)
        self.assertEqual(rollout["id"], 1)

        # 4. Update rollout with status="PAUSED"
        mock_rm.update_rollout.return_value = {
            "id": 1,
            "name": "Upgrade Fleet",
            "status": "PAUSED",
        }
        paused = self.provider.update_rollout(1, status="PAUSED")
        self.assertEqual(paused["status"], "PAUSED")
        mock_rm.update_rollout.assert_called_with(
            rollout_id=1,
            status="PAUSED",
            converged_devices=None,
            failed_devices=None,
        )

        # 5. Non-existent rollout
        mock_rm.get_rollout.return_value = None
        mock_rm.update_rollout.return_value = None
        self.assertIsNone(self.provider.get_rollout(999))
        self.assertIsNone(self.provider.update_rollout(999, status="PAUSED"))


class TestButlerMcpServer(unittest.TestCase):
    """Tests for ButlerMcpServer handling JSON-RPC 2.0 requests."""

    def setUp(self):
        self.mock_provider = MagicMock()
        self.server = ButlerMcpServer(self.mock_provider)

    def test_initialize(self):
        req = {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}}
        resp = self.server.handle_request(req)
        self.assertEqual(resp["id"], 1)
        self.assertEqual(resp["result"]["serverInfo"]["name"], "udmi-butler")
        self.assertIn("tools", resp["result"]["capabilities"])

    def test_ping_and_notifications(self):
        req = {"jsonrpc": "2.0", "id": 2, "method": "ping", "params": {}}
        resp = self.server.handle_request(req)
        self.assertEqual(resp["result"], {})

        notif = {"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}
        self.assertIsNone(self.server.handle_request(notif))

    def test_tools_list(self):
        req = {"jsonrpc": "2.0", "id": 3, "method": "tools/list", "params": {}}
        resp = self.server.handle_request(req)
        tool_names = [t["name"] for t in resp["result"]["tools"]]
        expected = [
            "health",
            "get_discovery_events",
            "get_discovered_devices",
            "get_device_messages",
            "record_message",
            "get_device_telemetry",
            "write_telemetry",
            "clear_registry_mapping_data",
            "get_portfolio_summary",
            "get_alerts",
            "get_devices",
            "get_device_detail",
            "create_rollout",
            "list_rollouts",
            "get_rollout",
            "update_rollout",
        ]
        for exp in expected:
            self.assertIn(exp, tool_names)

    def test_tools_call_rollout_methods(self):
        # 1. create_rollout tool
        self.mock_provider.create_rollout.return_value = {"id": 1, "status": "RUNNING", "name": "Rollout 1"}
        req = {
            "jsonrpc": "2.0",
            "id": 10,
            "method": "tools/call",
            "params": {
                "name": "create_rollout",
                "arguments": {
                    "name": "Rollout 1",
                    "target_payload": {"system": {"software": {"system": "1.0"}}},
                },
            },
        }
        resp = self.server.handle_request(req)
        self.assertFalse(resp["result"]["isError"])
        res_data = json.loads(resp["result"]["content"][0]["text"])
        self.assertEqual(res_data["id"], 1)

        # 2. update_rollout tool
        self.mock_provider.update_rollout.return_value = {"id": 1, "status": "PAUSED"}
        req_update = {
            "jsonrpc": "2.0",
            "id": 11,
            "method": "tools/call",
            "params": {
                "name": "update_rollout",
                "arguments": {
                    "rollout_id": 1,
                    "status": "PAUSED",
                },
            },
        }
        resp_update = self.server.handle_request(req_update)
        self.assertFalse(resp_update["result"]["isError"])
        res_update = json.loads(resp_update["result"]["content"][0]["text"])
        self.assertEqual(res_update["status"], "PAUSED")

    def test_tools_call_get_discovered_devices(self):
        self.mock_provider.get_discovered_devices.return_value = [
            {"gateway_id": "GAT-1", "bacnet": "10022", "vendor": "0x68"}
        ]
        req = {
            "jsonrpc": "2.0",
            "id": 4,
            "method": "tools/call",
            "params": {
                "name": "get_discovered_devices",
                "arguments": {"registry_id": "REG-1"},
            },
        }
        resp = self.server.handle_request(req)
        self.assertFalse(resp["result"]["isError"])
        text = resp["result"]["content"][0]["text"]
        data = json.loads(text)
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["gateway_id"], "GAT-1")

    def test_invalid_and_unknown_methods(self):
        # Missing method
        resp = self.server.handle_request({"jsonrpc": "2.0", "id": 5})
        self.assertEqual(resp["error"]["code"], -32600)

        # Unknown method
        resp = self.server.handle_request({"jsonrpc": "2.0", "id": 6, "method": "non_existent_method"})
        self.assertEqual(resp["error"]["code"], -32601)


class TestButlerClientIntegration(unittest.TestCase):
    """End-to-end integration test of ButlerClient against ButlerMcpHttpHandler."""

    @classmethod
    def setUpClass(cls):
        cls.mock_provider = MagicMock()
        cls.server_instance = ButlerMcpServer(cls.mock_provider)
        ButlerMcpHttpHandler.server_instance = cls.server_instance

        # Bind ephemeral port
        cls.httpd = HTTPServer(("127.0.0.1", 0), ButlerMcpHttpHandler)
        cls.port = cls.httpd.server_address[1]
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()
        time.sleep(0.1)

    @classmethod
    def tearDownClass(cls):
        cls.httpd.server_close()

    def setUp(self):
        self.client = ButlerClient(port=self.port)

    def test_health(self):
        self.mock_provider.health.return_value = {
            "status": "UP",
            "service": "butler",
            "connected": True,
            "datastores": {"relational": True, "timeseries": True},
        }
        h = self.client.health()
        self.assertEqual(h["status"], "UP")
        self.assertTrue(h["connected"])

    def test_client_get_discovered_devices(self):
        self.mock_provider.get_discovered_devices.return_value = [
            {"gateway_id": "GAT-123", "bacnet": "10022", "vendor": "0x68"}
        ]
        devs = self.client.get_discovered_devices("ZZ-TRI-FECTA")
        self.assertEqual(len(devs), 1)
        self.assertEqual(devs[0]["gateway_id"], "GAT-123")

    def test_client_record_message_and_get_messages(self):
        self.mock_provider.record_message.return_value = {
            "status": "SUCCESS",
            "device_id": "AHU-22",
        }
        res = self.client.record_message(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            sub_type="propose",
            sub_folder="localnet",
            payload={"version": "1.5.7"},
        )
        self.assertEqual(res["status"], "SUCCESS")

        self.mock_provider.get_device_messages.return_value = [
            {"device_id": "AHU-22", "sub_type": "propose", "updateFrom": "2026-08-20T10:00:00Z"}
        ]
        msgs = self.client.get_device_messages("ZZ-TRI-FECTA", "AHU-22")
        self.assertEqual(len(msgs), 1)
        self.assertEqual(msgs[0]["updateFrom"], "2026-08-20T10:00:00Z")

    def test_client_telemetry(self):
        self.mock_provider.write_telemetry.return_value = {
            "status": "SUCCESS",
            "points_written": 1,
        }
        res = self.client.write_telemetry(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            points={"temp": 20.0},
        )
        self.assertEqual(res["points_written"], 1)

        self.mock_provider.get_device_telemetry.return_value = {
            "device_id": "AHU-22",
            "series": [{"point_name": "temp", "values": [{"value": 20.0}]}],
        }
        telem = self.client.get_device_telemetry("ZZ-TRI-FECTA", "AHU-22")
        self.assertEqual(telem["series"][0]["point_name"], "temp")

    def test_client_portfolio_and_devices_queries(self):
        self.mock_provider.get_portfolio_summary.return_value = {
            "device_counts": {"total": 10, "online": 9, "offline": 0, "error": 1},
            "registries_count": 2,
            "active_rollouts_count": 0,
            "critical_alerts_24h": 3,
        }
        summary = self.client.get_portfolio_summary()
        self.assertEqual(summary["device_counts"]["total"], 10)

        self.mock_provider.get_alerts.return_value = [{"id": 1, "level": 500}]
        alerts = self.client.get_alerts(limit=5)
        self.assertEqual(len(alerts), 1)

        self.mock_provider.get_devices.return_value = {"total": 1, "devices": [{"device_id": "DEV-1"}]}
        devs = self.client.get_devices(limit=10)
        self.assertEqual(devs["total"], 1)

        self.mock_provider.get_device_detail.return_value = {"device_id": "DEV-1"}
        detail = self.client.get_device_detail("REG-1", "DEV-1")
        self.assertEqual(detail["device_id"], "DEV-1")

    def test_client_tools_list(self):
        tools = self.client.tools_list()
        self.assertGreaterEqual(len(tools), 16)

    def test_client_rollouts(self):
        self.mock_provider.create_rollout.return_value = {"id": 1, "status": "RUNNING"}
        created = self.client.create_rollout(
            name="R1",
            target_filter={},
            target_payload={"system": {}},
        )
        self.assertEqual(created["id"], 1)

        self.mock_provider.list_rollouts.return_value = [{"id": 1, "status": "RUNNING"}]
        rollouts = self.client.list_rollouts()
        self.assertEqual(len(rollouts), 1)

        self.mock_provider.get_rollout.return_value = {"id": 1, "status": "RUNNING"}
        r = self.client.get_rollout(1)
        self.assertEqual(r["id"], 1)

        self.mock_provider.update_rollout.return_value = {"id": 1, "status": "PAUSED"}
        up = self.client.update_rollout(1, status="PAUSED")
        self.assertEqual(up["status"], "PAUSED")


class TestButlerMcpStdioRunner(unittest.TestCase):
    """Test running bin/mcp_butler in stdio mode via subprocess."""

    def test_stdio_initialize_and_tools_list(self):
        proc = subprocess.Popen(
            ["bin/mcp_butler", "mcp"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        try:
            # 1. initialize
            proc.stdin.write(json.dumps({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}}) + "\n")
            proc.stdin.flush()
            init_line = proc.stdout.readline()
            init_resp = json.loads(init_line)
            self.assertEqual(init_resp["result"]["serverInfo"]["name"], "udmi-butler")

            # 2. tools/list
            proc.stdin.write(json.dumps({"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}) + "\n")
            proc.stdin.flush()
            tools_line = proc.stdout.readline()
            tools_resp = json.loads(tools_line)
            tool_names = [t["name"] for t in tools_resp["result"]["tools"]]
            self.assertIn("get_discovery_events", tool_names)
            self.assertIn("get_discovered_devices", tool_names)
            self.assertIn("record_message", tool_names)
            self.assertIn("get_device_telemetry", tool_names)
            self.assertIn("health", tool_names)
            self.assertIn("create_rollout", tool_names)
            self.assertIn("list_rollouts", tool_names)
            self.assertIn("get_rollout", tool_names)
            self.assertIn("update_rollout", tool_names)

        finally:
            proc.terminate()
            proc.wait(timeout=2)


class TestMapperIntegrationWithButlerMcp(unittest.TestCase):
    """Verifies that run_mapping successfully consumes discovered devices via ButlerClient."""

    def test_run_mapping_with_butler_client(self):
        import tempfile
        from butler.src.mapping import run_mapping

        with tempfile.TemporaryDirectory() as tmp_site:
            dev_dir = os.path.join(tmp_site, "devices", "AHU-22")
            os.makedirs(dev_dir, exist_ok=True)
            meta_path = os.path.join(dev_dir, "metadata.json")
            base_meta = {
                "version": "1.5.7",
                "timestamp": "2026-08-20T10:00:00Z",
                "system": {"serial_no": "AHU-22-001"},
                "localnet": {
                    "families": {
                        "vendor": {"addr": "0x65"}
                    }
                }
            }
            with open(meta_path, "w", encoding="utf-8") as f:
                json.dump(base_meta, f)

            mock_client = MagicMock()
            mock_client.get_discovered_devices.return_value = [
                {
                    "gateway_id": "GAT-123",
                    "generation": "2026-09-01T12:00:00Z",
                    "bacnet": "10022",
                    "ipv4": "192.168.1.122",
                    "vendor": "0x68",
                }
            ]

            run_mapping(
                conn_spec=None,
                registry_id="ZZ-TRI-FECTA",
                site_model=tmp_site,
                butler_client=mock_client,
            )

            mock_client.get_discovered_devices.assert_called_once_with("ZZ-TRI-FECTA")

            unk_path = os.path.join(tmp_site, "devices", "UNK-1", "metadata.json")
            self.assertTrue(os.path.exists(unk_path))
            with open(unk_path, "r", encoding="utf-8") as f:
                unk_meta = json.load(f)
            self.assertEqual(unk_meta["localnet"]["families"]["vendor"]["addr"], "0x68")
            self.assertEqual(unk_meta["localnet"]["families"]["bacnet"]["addr"], "10022")


if __name__ == "__main__":
    unittest.main()
