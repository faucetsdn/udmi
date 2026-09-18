"""Unit and integration test suite for UDMI UUFI MCP Server and Client."""

import json
import os
import sys
import threading
import time
import unittest
import urllib.error

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, REPO_ROOT)

from mcp.uufi.client import UUFIClient
from mcp.uufi.provider import UUFIProvider
from mcp.uufi.server import UUFIMcpServer, run_http_server


class TestUUFIProvider(unittest.TestCase):
    """Tests core UUFIProvider topic logic, envelope encapsulation, and message handling."""

    def setUp(self):
        self.provider = UUFIProvider(
            project_spec="//mqtt/localhost:18833",
            registry_id="ZZ-TRI-FECTA",
            client_id="test_client",
            prefix="",
            auto_connect=False,
        )

    def test_topic_formatting(self):
        """Verifies rigid segment and leading slash topic construction per uufi.md §2.2."""
        # Common channel
        t1 = self.provider.get_topic("state", "udmi")
        self.assertEqual(t1, "/uufi/c/state/udmi")

        # Device-scoped channel
        t2 = self.provider.get_topic("config", "pointset", registry_id="ZZ-TRI-FECTA", device_id="AHU-1")
        self.assertEqual(t2, "/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/config/pointset")

        # Scoped channel with prefix
        provider_ns = UUFIProvider(
            project_spec="//mqtt/localhost:18833/my_prefix",
            prefix="my_prefix",
            auto_connect=False,
        )
        t3 = provider_ns.get_topic("model", "system", registry_id="ZZ-TRI-FECTA", device_id="AHU-1")
        self.assertEqual(t3, "/my_prefix/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/model/system")

    def test_envelope_creation(self):
        """Verifies mandatory envelope fields and inner payload metadata per uufi.md §4, §8.4."""
        env = self.provider.create_envelope(
            sub_type="config",
            sub_folder="system",
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-1",
            inner_payload={"software": {"system": "2.0.0"}},
        )

        self.assertEqual(env["subType"], "config")
        self.assertEqual(env["subFolder"], "system")
        self.assertEqual(env["deviceRegistryId"], "ZZ-TRI-FECTA")
        self.assertEqual(env["deviceId"], "AHU-1")
        self.assertEqual(env["projectId"], "localhost")
        self.assertTrue(env["transactionId"].startswith("UUFI:"))
        self.assertTrue(env["publishTime"].endswith("Z"))
        self.assertEqual(env["payload"]["version"], "1.5.2")
        self.assertTrue(env["payload"]["timestamp"].endswith("Z"))
        self.assertEqual(env["payload"]["software"]["system"], "2.0.0")

    def test_publish_config_records_publication(self):
        """Verifies publish_config constructs compliant message and records it."""
        res = self.provider.publish_config(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            sub_folder="blobset",
            payload={"blobset": {"blobs": {}}},
        )
        self.assertEqual(res["status"], "DISPATCHED")
        self.assertEqual(res["topic"], "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/config/blobset")

        msgs = self.provider.get_published_messages()
        self.assertEqual(len(msgs), 1)
        self.assertEqual(msgs[0]["topic"], "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/config/blobset")
        self.assertEqual(msgs[0]["envelope"]["payload"]["version"], "1.5.2")

    def test_update_system_model(self):
        """Verifies update_system_model publishes on model/system."""
        res = self.provider.update_system_model(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            payload={"software": {"system": "3.1.0"}},
        )
        self.assertEqual(res["status"], "DISPATCHED")
        self.assertEqual(res["topic"], "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/model/system")

    def test_inbound_event_buffering_and_polling(self):
        """Verifies event ring buffer, cursor incrementation, and filtering."""
        # Inject state message
        self.provider.inject_inbound_message(
            "/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/state/system",
            {
                "subType": "state",
                "subFolder": "system",
                "deviceRegistryId": "ZZ-TRI-FECTA",
                "deviceId": "AHU-1",
                "payload": {"make": "Acme", "model": "Ctrl"},
            },
        )
        # Inject alert event
        self.provider.inject_inbound_message(
            "/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/events/status",
            {
                "subType": "events",
                "subFolder": "status",
                "deviceRegistryId": "ZZ-TRI-FECTA",
                "deviceId": "AHU-1",
                "payload": {"level": 500, "message": "High temp"},
            },
        )

        poll1 = self.provider.poll_events(cursor=0)
        self.assertEqual(len(poll1["events"]), 2)
        self.assertEqual(poll1["cursor"], 2)

        # Poll with sub_folders filter
        poll_status = self.provider.poll_events(cursor=0, sub_folders=["status"])
        self.assertEqual(len(poll_status["events"]), 1)
        self.assertEqual(poll_status["events"][0]["subFolder"], "status")

        # Poll since cursor 2 returns nothing new
        poll2 = self.provider.poll_events(cursor=2)
        self.assertEqual(len(poll2["events"]), 0)

    def test_handshake_layer1_flow(self):
        """Simulates Layer 1 handshake request-reply sequence with symmetric transactionId."""
        def _simulate_server_reply():
            time.sleep(0.05)
            msgs = self.provider.get_published_messages()
            if msgs:
                hs_req = msgs[0]["envelope"]
                tx_id = hs_req["transactionId"]
                reply_msg = {
                    "subType": "config",
                    "subFolder": "udmi",
                    "transactionId": tx_id,
                    "source": "udmis",
                    "payload": {
                        "version": "1.5.2",
                        "setup": {
                            "deviceRegistryId": "DISCOVERED-REG",
                            "functions_ver": 9,
                        },
                        "reply": {
                            "transaction_id": tx_id,
                        },
                    },
                }
                self.provider.inject_inbound_message("/uufi/c/config/udmi", reply_msg)

        worker = threading.Thread(target=_simulate_server_reply)
        worker.start()

        res = self.provider.handshake(functions_ver=9, timeout_sec=2.0)
        worker.join()

        self.assertEqual(res["status"], "ACTIVE")
        self.assertEqual(res["deviceRegistryId"], "DISCOVERED-REG")
        self.assertEqual(self.provider.active_registry, "DISCOVERED-REG")
        self.assertEqual(self.provider.handshake_status, "ACTIVE")

    def test_query_state_correlated_response(self):
        """Verifies query_state publishes query and waits for correlated reply."""
        def _simulate_state_response():
            time.sleep(0.05)
            msgs = self.provider.get_published_messages()
            if msgs:
                req_env = msgs[-1]["envelope"]
                tx_id = req_env["transactionId"]
                state_reply = {
                    "subType": "state",
                    "subFolder": "blobset",
                    "transactionId": tx_id,
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-22",
                    "payload": {
                        "version": "1.5.2",
                        "blobset": {"blobs": {"system": {"phase": "applied"}}},
                    },
                }
                self.provider.inject_inbound_message(
                    "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/state/blobset", state_reply
                )

        worker = threading.Thread(target=_simulate_state_response)
        worker.start()

        reply = self.provider.query_state(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            sub_folder="blobset",
            timeout_sec=2.0,
        )
        worker.join()

        self.assertEqual(reply["blobset"]["blobs"]["system"]["phase"], "applied")

    def test_query_system_model_correlated_response(self):
        """Verifies query_system_model publishes query and waits for correlated model reply."""
        def _simulate_model_response():
            time.sleep(0.05)
            msgs = self.provider.get_published_messages()
            if msgs:
                req_env = msgs[-1]["envelope"]
                tx_id = req_env["transactionId"]
                model_reply = {
                    "subType": "model",
                    "subFolder": "system",
                    "transactionId": tx_id,
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-22",
                    "payload": {
                        "version": "1.5.2",
                        "system": {"hardware": {"make": "Bosch", "model": "Ctrl-X"}},
                    },
                }
                self.provider.inject_inbound_message(
                    "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/model/system", model_reply
                )

        worker = threading.Thread(target=_simulate_model_response)
        worker.start()

        reply = self.provider.query_system_model(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            timeout_sec=2.0,
        )
        worker.join()

        self.assertEqual(reply["system"]["hardware"]["make"], "Bosch")


class TestUUFIJsonRpcProtocol(unittest.TestCase):
    """Verifies JSON-RPC 2.0 protocol handling in UUFIMcpServer."""

    def setUp(self):
        self.provider = UUFIProvider(auto_connect=False)
        self.server = UUFIMcpServer(self.provider)

    def test_initialize(self):
        resp = self.server.handle_request({
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {},
        })
        self.assertEqual(resp["result"]["serverInfo"]["name"], "uufi-mcp")
        self.assertEqual(resp["result"]["protocolVersion"], "2024-11-05")

    def test_tools_list(self):
        resp = self.server.handle_request({
            "jsonrpc": "2.0",
            "id": 2,
            "method": "tools/list",
            "params": {},
        })
        tools = resp["result"]["tools"]
        names = [t["name"] for t in tools]
        self.assertIn("health", names)
        self.assertIn("handshake", names)
        self.assertIn("publish_config", names)
        self.assertIn("query_state", names)
        self.assertIn("query_system_model", names)
        self.assertIn("update_system_model", names)
        self.assertIn("poll_events", names)

    def test_tool_call_health(self):
        resp = self.server.handle_request({
            "jsonrpc": "2.0",
            "id": 3,
            "method": "tools/call",
            "params": {
                "name": "health",
                "arguments": {},
            },
        })
        self.assertFalse(resp["result"]["isError"])
        content_text = resp["result"]["content"][0]["text"]
        health = json.loads(content_text)
        self.assertIn("status", health)
        self.assertIn("broker", health)

    def test_tool_call_publish_config(self):
        resp = self.server.handle_request({
            "jsonrpc": "2.0",
            "id": 4,
            "method": "tools/call",
            "params": {
                "name": "publish_config",
                "arguments": {
                    "registry_id": "REG-1",
                    "device_id": "DEV-1",
                    "sub_folder": "system",
                    "payload": {"software": {"system": "1.0.0"}},
                },
            },
        })
        self.assertFalse(resp["result"]["isError"])
        content = json.loads(resp["result"]["content"][0]["text"])
        self.assertEqual(content["status"], "DISPATCHED")
        self.assertEqual(content["topic"], "/uufi/r/REG-1/d/DEV-1/c/config/system")


class TestUUFIHttpServerAndClient(unittest.TestCase):
    """Verifies UUFIClient communicating with UUFIMcpServer over HTTP JSON-RPC 2.0."""

    @classmethod
    def setUpClass(cls):
        cls.test_port = 28097
        cls.provider = UUFIProvider(auto_connect=False)
        cls.server_thread = threading.Thread(
            target=run_http_server,
            args=(cls.provider, cls.test_port),
            daemon=True,
        )
        cls.server_thread.start()

        cls.client = UUFIClient(port=cls.test_port)
        # Wait for server readiness
        for _ in range(30):
            try:
                h = cls.client.health()
                if h:
                    break
            except (urllib.error.URLError, ConnectionError, OSError, RuntimeError):
                time.sleep(0.05)

    def test_client_health_call(self):
        health = self.client.health()
        self.assertIn("status", health)
        self.assertIn("broker", health)

    def test_client_publish_config_call(self):
        res = self.client.publish_config(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-99",
            sub_folder="pointset",
            payload={"points": {"temp": {"set_value": 72.0}}},
        )
        self.assertEqual(res["status"], "DISPATCHED")
        self.assertEqual(res["registry_id"], "ZZ-TRI-FECTA")
        self.assertEqual(res["device_id"], "AHU-99")

    def test_client_poll_events_call(self):
        self.provider.inject_inbound_message(
            "/uufi/r/ZZ-TRI-FECTA/d/AHU-99/c/events/pointset",
            {
                "subType": "events",
                "subFolder": "pointset",
                "deviceRegistryId": "ZZ-TRI-FECTA",
                "deviceId": "AHU-99",
                "payload": {"points": {"temp": {"present_value": 71.8}}},
            },
        )
        events_resp = self.client.poll_events(cursor=0)
        self.assertIn("events", events_resp)
        self.assertTrue(len(events_resp["events"]) >= 1)


if __name__ == "__main__":
    unittest.main()
