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
        t1 = self.provider.get_topic("state", "udmi")
        self.assertEqual(t1, "/uufi/c/state/udmi")

        t2 = self.provider.get_topic("config", "pointset", registry_id="ZZ-TRI-FECTA", device_id="AHU-1")
        self.assertEqual(t2, "/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/config/pointset")

        provider_ns = UUFIProvider(
            project_spec="//mqtt/localhost:18833/my_prefix",
            prefix="my_prefix",
            auto_connect=False,
        )
        t3 = provider_ns.get_topic("model", "system", registry_id="ZZ-TRI-FECTA", device_id="AHU-1")
        self.assertEqual(t3, "/my_prefix/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/model/system")

    def test_pubsub_uri_parsing_and_port_rejection(self):
        """Verifies pubsub:// and //pubsub/ spec parsing and :port prohibition per uufi.md §2.2."""
        ps = UUFIProvider(project_spec="pubsub://app_ctrl@my-gcp-project/custom_uufi", auto_connect=False)
        self.assertEqual(ps.transport, "pubsub")
        self.assertEqual(ps.project_id, "my-gcp-project")
        self.assertEqual(ps.pubsub_topic, "custom_uufi")
        self.assertEqual(ps.pubsub_subscription, "custom_uufi+app_ctrl")
        self.assertEqual(ps.principal, "app_ctrl@")

        ps_default = UUFIProvider(project_spec="pubsub://my-gcp-project", auto_connect=False)
        self.assertEqual(ps_default.pubsub_topic, "udmi_uufi")
        self.assertEqual(ps_default.pubsub_subscription, "udmi_uufi+unknown")
        self.assertEqual(ps_default.principal, "unknown@")

        ps_slash = UUFIProvider(project_spec="//pubsub/my-gcp-project/dev_+operator", auto_connect=False)
        self.assertEqual(ps_slash.transport, "pubsub")
        self.assertEqual(ps_slash.project_id, "my-gcp-project")
        self.assertEqual(ps_slash.pubsub_topic, "dev_udmi_uufi")
        self.assertEqual(ps_slash.pubsub_subscription, "dev_udmi_uufi+operator")
        self.assertEqual(ps_slash.principal, "operator@")

        with self.assertRaises(ValueError):
            UUFIProvider(project_spec="pubsub://user@my-gcp-project:8085/udmi_uufi", auto_connect=False)

    def test_envelope_creation(self):
        """Verifies mandatory envelope fields, null query payloads, and string sentinels."""
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
        self.assertEqual(env["principal"], self.provider.principal)
        self.assertTrue(env["transactionId"].startswith("UUFI:"))
        self.assertTrue(env["publishTime"].endswith("Z"))
        self.assertEqual(env["payload"]["version"], "1.5.7")
        self.assertTrue(env["payload"]["timestamp"].endswith("Z"))
        self.assertEqual(env["payload"]["software"]["system"], "2.0.0")

        # Null query payload must remain None
        q_env = self.provider.create_envelope(
            sub_type="state",
            sub_folder="pointset",
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-1",
            inner_payload=None,
        )
        self.assertIsNone(q_env["payload"])

        # String sentinel must not have version/timestamp injected
        s_env = self.provider.create_envelope(
            sub_type="config",
            sub_folder="pointset",
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-1",
            inner_payload="delete",
        )
        self.assertEqual(s_env["payload"], "delete")

    def test_publish_config_records_publication(self):
        """Verifies publish_config constructs compliant message and records it."""
        res = self.provider.publish_config(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            sub_folder="blobset",
            payload={"blobs": {}},
        )
        self.assertEqual(res["status"], "DISPATCHED")
        self.assertEqual(res["topic"], "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/config/blobset")

        msgs = self.provider.get_published_messages()
        self.assertEqual(len(msgs), 1)
        self.assertEqual(msgs[0]["topic"], "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/config/blobset")
        self.assertEqual(msgs[0]["envelope"]["payload"]["version"], "1.5.7")

    def test_publish_config_wait_for_ack_progress_and_success(self):
        """Verifies wait_for_ack ignores self-echo, records level<500 progress, resolves on Ack."""
        def _simulate_ack_sequence():
            time.sleep(0.05)
            msgs = self.provider.get_published_messages()
            req_env = msgs[-1]["envelope"]
            tx_id = req_env["transactionId"]
            topic = msgs[-1]["topic"]

            # 1. Self-echo (non-udmis source) must be ignored
            self.provider.inject_inbound_message(topic, req_env)

            # 2. Interim progress (level 200) must keep waiter open
            self.provider.inject_inbound_message(
                topic,
                {
                    "subType": "config",
                    "subFolder": "pointset",
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-22",
                    "transactionId": tx_id,
                    "source": "udmis",
                    "principal": self.provider.principal,
                    "payload": {
                        "version": "1.5.7",
                        "timestamp": "2026-11-24T19:00:01Z",
                        "control": {
                            "status": {
                                "message": "Applying config step 1/2",
                                "category": "system.config.receive",
                                "level": 200,
                                "timestamp": "2026-11-24T19:00:01Z",
                            }
                        },
                    },
                },
            )
            time.sleep(0.05)

            # 3. Final Success Ack clears waiter
            self.provider.inject_inbound_message(
                topic,
                {
                    "subType": "config",
                    "subFolder": "pointset",
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-22",
                    "transactionId": tx_id,
                    "source": "udmis",
                    "principal": self.provider.principal,
                    "payload": {
                        "version": "1.5.7",
                        "timestamp": "2026-11-24T19:00:02Z",
                        "points": {"temp": {"set_value": 22.0}},
                    },
                },
            )

        worker = threading.Thread(target=_simulate_ack_sequence)
        worker.start()

        res = self.provider.publish_config(
            registry_id="ZZ-TRI-FECTA",
            device_id="AHU-22",
            sub_folder="pointset",
            payload={"points": {"temp": {"set_value": 22.0}}},
            wait_for_ack=True,
            timeout_sec=2.0,
        )
        worker.join()

        self.assertEqual(res["status"], "ACKNOWLEDGED")
        self.assertEqual(len(res["progress"]), 1)
        self.assertEqual(res["progress"][0]["control"]["status"]["level"], 200)
        self.assertEqual(res["ack"]["points"]["temp"]["set_value"], 22.0)

    def test_publish_config_wait_for_ack_control_error_raises(self):
        """Verifies terminal control reply (level >= 500) raises RuntimeError."""
        def _simulate_error():
            time.sleep(0.05)
            msgs = self.provider.get_published_messages()
            req_env = msgs[-1]["envelope"]
            self.provider.inject_inbound_message(
                msgs[-1]["topic"],
                {
                    "subType": "config",
                    "subFolder": "pointset",
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-22",
                    "transactionId": req_env["transactionId"],
                    "source": "udmis",
                    "principal": self.provider.principal,
                    "payload": {
                        "version": "1.5.7",
                        "timestamp": "2026-11-24T19:00:01Z",
                        "control": {
                            "status": {
                                "message": "Unsupported string sentinel",
                                "category": "system.config.parse",
                                "level": 500,
                                "timestamp": "2026-11-24T19:00:01Z",
                            }
                        },
                    },
                },
            )

        worker = threading.Thread(target=_simulate_error)
        worker.start()

        with self.assertRaises(RuntimeError) as ctx:
            self.provider.publish_config(
                registry_id="ZZ-TRI-FECTA",
                device_id="AHU-22",
                sub_folder="pointset",
                payload="unknown_sentinel",
                wait_for_ack=True,
                timeout_sec=2.0,
            )
        worker.join()
        self.assertIn("Unsupported string sentinel", str(ctx.exception))
        # Verify string sentinel was published verbatim without dict wrapping
        self.assertEqual(self.provider.get_published_messages()[-1]["envelope"]["payload"], "unknown_sentinel")

    def test_pubsub_unwrapped_query_and_sentinel_ack(self):
        """Verifies PubSub unwrapped publish/receive, principal filtering, and string sentinel replies."""
        ps = UUFIProvider(project_spec="pubsub://tester@my-proj/udmi_uufi", auto_connect=False)

        def _simulate_ps_reply():
            time.sleep(0.05)
            rec = ps.get_published_messages()[-1]
            self.assertEqual(rec["data"], b"null")
            self.assertEqual(rec["attributes"]["subType"], "state")
            self.assertEqual(rec["attributes"]["subFolder"], "blobset")
            self.assertEqual(rec["attributes"]["principal"], "tester@")
            tx_id = rec["attributes"]["transactionId"]

            # Different principal should be ignored
            ps.inject_inbound_pubsub_message(
                {
                    "subType": "state",
                    "subFolder": "blobset",
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-99",
                    "transactionId": tx_id,
                    "source": "udmis",
                    "principal": "other@",
                },
                {"wrong": True},
            )

            # Matching principal with string sentinel "missing"
            ps.inject_inbound_pubsub_message(
                {
                    "subType": "state",
                    "subFolder": "blobset",
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-99",
                    "transactionId": tx_id,
                    "source": "pubsub+udmis",
                    "principal": "tester@",
                },
                "missing",
            )

        worker = threading.Thread(target=_simulate_ps_reply)
        worker.start()
        reply = ps.query_state("ZZ-TRI-FECTA", "AHU-99", sub_folder="blobset", timeout_sec=2.0)
        worker.join()
        self.assertEqual(reply, "missing")

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

        poll_status = self.provider.poll_events(cursor=0, sub_folders=["status"])
        self.assertEqual(len(poll_status["events"]), 1)
        self.assertEqual(poll_status["events"][0]["subFolder"], "status")

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
                    "principal": self.provider.principal,
                    "payload": {
                        "version": "1.5.7",
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
        """Verifies query_state publishes single-channel null-payload query on state/<subFolder>."""
        def _simulate_state_response():
            time.sleep(0.05)
            msgs = self.provider.get_published_messages()
            if msgs:
                req_rec = msgs[-1]
                self.assertEqual(
                    req_rec["topic"], "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/state/blobset"
                )
                req_env = req_rec["envelope"]
                self.assertEqual(req_env["subType"], "state")
                self.assertEqual(req_env["subFolder"], "blobset")
                self.assertIsNone(req_env["payload"])
                tx_id = req_env["transactionId"]
                state_reply = {
                    "subType": "state",
                    "subFolder": "blobset",
                    "transactionId": tx_id,
                    "source": "udmis",
                    "principal": self.provider.principal,
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-22",
                    "payload": {
                        "version": "1.5.7",
                        "blobs": {"system": {"phase": "applied"}},
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

        self.assertEqual(reply["blobs"]["system"]["phase"], "applied")

    def test_query_system_model_correlated_response(self):
        """Verifies query_system_model publishes single-channel null-payload query on model/system."""
        def _simulate_model_response():
            time.sleep(0.05)
            msgs = self.provider.get_published_messages()
            if msgs:
                req_rec = msgs[-1]
                self.assertEqual(
                    req_rec["topic"], "/uufi/r/ZZ-TRI-FECTA/d/AHU-22/c/model/system"
                )
                req_env = req_rec["envelope"]
                self.assertEqual(req_env["subType"], "model")
                self.assertEqual(req_env["subFolder"], "system")
                self.assertIsNone(req_env["payload"])
                tx_id = req_env["transactionId"]
                model_reply = {
                    "subType": "model",
                    "subFolder": "system",
                    "transactionId": tx_id,
                    "source": "udmis",
                    "principal": self.provider.principal,
                    "deviceRegistryId": "ZZ-TRI-FECTA",
                    "deviceId": "AHU-22",
                    "payload": {
                        "version": "1.5.7",
                        "hardware": {"make": "Bosch", "model": "Ctrl-X"},
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

        self.assertEqual(reply["hardware"]["make"], "Bosch")


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
