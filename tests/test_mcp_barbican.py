"""Unit and integration tests for UDMI Barbican MCP Server and Client."""

import json
import subprocess
import threading
import time
import unittest
from http.server import HTTPServer
from unittest.mock import MagicMock, patch

from mcp.barbican.client import BarbicanClient
from mcp.barbican.provider import (
    BarbicanProvider,
    natural_compare,
    NATURAL_SORT_KEY,
    prefix_range_end,
)
from mcp.barbican.server import (
    BarbicanMcpServer,
    BarbicanMcpHttpHandler,
    MCP_TOOLS,
)


class TestBarbicanProviderLogic(unittest.TestCase):
    """Test pure algorithmic logic in BarbicanProvider without requiring live backend."""

    def test_natural_comparator(self):
        items = ["dev-10", "dev-1", "dev-2", "dev-20", "dev-03"]
        items.sort(key=NATURAL_SORT_KEY)
        self.assertEqual(items, ["dev-1", "dev-2", "dev-03", "dev-10", "dev-20"])

        items2 = ["AHU-1", "AHU-2", "AHU-10", "CHILLER-1", "CHILLER-2"]
        items2.sort(key=NATURAL_SORT_KEY)
        self.assertEqual(items2, ["AHU-1", "AHU-2", "AHU-10", "CHILLER-1", "CHILLER-2"])

    def test_prefix_range_end(self):
        end = prefix_range_end("/r/")
        self.assertEqual(end, b"/r0")
        end_dev = prefix_range_end("/r/reg1/d/dev1")
        self.assertEqual(end_dev, b"/r/reg1/d/dev2")

    def test_list_registries_parsing(self):
        provider = BarbicanProvider("http://127.0.0.1:2379")
        keys = [
            "/r/cloud_iot_registry/d/AHU-1:numId",
            "/r/cloud_iot_registry/d/AHU-1/c/state:latest",
            "/r/cloud_iot_registry/d/AHU-2:numId",
            "/r/cloud_iot_registry:last_state",
            "/r/acme_corp/d/dev-1:numId",
            "/r/acme_corp/d/dev-10:numId",
            "/r/acme_corp/d/dev-2:numId",
            "/r/acme_corp/d/dev-2:last_state",
        ]
        with patch.object(provider, "get_prefix_keys", return_value=keys):
            result = provider.list_registries("/r/")
            self.assertEqual(result["registries"], ["acme_corp", "cloud_iot_registry"])
            self.assertEqual(result["totalDevicesCount"], 5)

    def test_list_devices_parsing(self):
        provider = BarbicanProvider("http://127.0.0.1:2379")
        keys = [
            "/r/cloud_iot_registry/d/AHU-10:numId",
            "/r/cloud_iot_registry/d/AHU-1:numId",
            "/r/cloud_iot_registry/d/AHU-2/config",
            "/r/cloud_iot_registry/d/AHU-2:last_state",
        ]
        with patch.object(provider, "get_prefix_keys", return_value=keys):
            result = provider.list_devices("cloud_iot_registry")
            self.assertEqual(result["registryId"], "cloud_iot_registry")
            self.assertEqual(result["devices"], ["AHU-1", "AHU-2", "AHU-10"])

    def test_get_device_properties_parsing(self):
        provider = BarbicanProvider("http://127.0.0.1:2379")
        prefix = "/r/reg1/d/dev1"

        def mock_get_prefix(p):
            if p == prefix + ":":
                return {prefix + ":numId": "12345", prefix + ":make_model": "Bosch"}
            if p == prefix + "/":
                return {prefix + "/status": '{"online": true}'}
            return {}

        with patch.object(provider, "get_prefix_entries", side_effect=mock_get_prefix), \
             patch.object(provider, "get_entry", return_value="raw-device-blob"):
            result = provider.get_device_properties("reg1", "dev1")
            self.assertEqual(result["registryId"], "reg1")
            self.assertEqual(result["deviceId"], "dev1")
            props = result["properties"]
            self.assertEqual(props.get(":value"), "raw-device-blob")
            self.assertEqual(props.get(":numId"), "12345")
            self.assertEqual(props.get(":make_model"), "Bosch")
            self.assertEqual(props.get("/status"), '{"online": true}')

    def test_discover_target_success(self):
        with patch("socket.create_connection") as mock_conn:
            target = BarbicanProvider.discover_target(candidates=[9002])
            self.assertEqual(target, "http://127.0.0.1:9002")

    def test_discover_target_etcd_port_env(self):
        with patch.dict("os.environ", {"ETCD_PORT": "19999"}), patch("socket.create_connection") as mock_conn:
            target = BarbicanProvider.discover_target()
            self.assertEqual(target, "http://127.0.0.1:19999")

    def test_discover_target_fail_fast(self):
        with patch("socket.create_connection", side_effect=ConnectionRefusedError):
            with self.assertRaises(RuntimeError) as cm:
                BarbicanProvider.discover_target(candidates=[9002, 9003])
            self.assertIn("No reachable Barbican/etcd datastore found", str(cm.exception))

    def test_discover_target_default_canonical_when_no_active_candidate(self):
        with patch("socket.create_connection", side_effect=ConnectionRefusedError):
            with patch.dict("os.environ", {}, clear=True):
                target = BarbicanProvider.discover_target()
                self.assertEqual(target, "http://127.0.0.1:2379")


class TestBarbicanMcpServerAndClient(unittest.TestCase):
    """Test MCP JSON-RPC protocol and HTTP server/client abstraction."""

    @classmethod
    def setUpClass(cls):
        cls.mock_provider = MagicMock(spec=BarbicanProvider)
        cls.mock_provider.health.return_value = {
            "status": "UP",
            "service": "barbican",
            "connected": True,
        }
        cls.mock_provider.list_registries.return_value = {
            "registries": ["acme", "cloud_iot"],
            "totalDevicesCount": 4,
        }
        cls.mock_provider.list_devices.return_value = {
            "registryId": "cloud_iot",
            "devices": ["AHU-1", "AHU-2"],
        }
        cls.mock_provider.get_device_properties.return_value = {
            "registryId": "cloud_iot",
            "deviceId": "AHU-1",
            "properties": {":numId": "999", "/status": "HEALTHY"},
        }
        cls.mock_provider.get_entry.return_value = "hello_barbican"
        cls.mock_provider.get_prefix_entries.return_value = {"/test/a": "valA"}
        cls.mock_provider.put_entry.return_value = True
        cls.mock_provider.delete_entry.return_value = 1

        cls.mcp_server = BarbicanMcpServer(cls.mock_provider)

        # Start ephemeral HTTP server
        BarbicanMcpHttpHandler.server_instance = cls.mcp_server
        cls.httpd = HTTPServer(("127.0.0.1", 0), BarbicanMcpHttpHandler)
        cls.port = cls.httpd.server_address[1]
        cls.server_thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.server_thread.start()
        time.sleep(0.1)

        cls.client = BarbicanClient(port=cls.port)

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()

    def test_jsonrpc_initialize(self):
        res = self.mcp_server.handle_request({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}})
        self.assertEqual(res["result"]["serverInfo"]["name"], "udmi-barbican")
        self.assertEqual(res["result"]["protocolVersion"], "2024-11-05")

    def test_jsonrpc_ping(self):
        res = self.mcp_server.handle_request({"jsonrpc": "2.0", "id": 2, "method": "ping", "params": {}})
        self.assertEqual(res["result"], {})

    def test_jsonrpc_tools_list(self):
        res = self.mcp_server.handle_request({"jsonrpc": "2.0", "id": 3, "method": "tools/list", "params": {}})
        tool_names = [t["name"] for t in res["result"]["tools"]]
        self.assertIn("list_registries", tool_names)
        self.assertIn("list_devices", tool_names)
        self.assertIn("get_device_properties", tool_names)
        self.assertIn("health", tool_names)

    def test_jsonrpc_tools_call(self):
        res = self.mcp_server.handle_request({
            "jsonrpc": "2.0",
            "id": 4,
            "method": "tools/call",
            "params": {"name": "list_registries", "arguments": {"prefix": "/r/"}},
        })
        self.assertFalse(res["result"]["isError"])
        content_text = res["result"]["content"][0]["text"]
        data = json.loads(content_text)
        self.assertEqual(data["registries"], ["acme", "cloud_iot"])

    def test_client_direct_rpc_methods(self):
        # 1. health
        h = self.client.health()
        self.assertEqual(h["status"], "UP")

        # 2. list_registries
        regs = self.client.list_registries()
        self.assertEqual(regs["registries"], ["acme", "cloud_iot"])
        self.assertEqual(regs["totalDevicesCount"], 4)

        # 3. list_devices
        devs = self.client.list_devices("cloud_iot")
        self.assertEqual(devs["devices"], ["AHU-1", "AHU-2"])

        # 4. get_device_properties
        props = self.client.get_device_properties("cloud_iot", "AHU-1")
        self.assertEqual(props["properties"][":numId"], "999")

        # 5. get_entry
        val = self.client.get_entry("/some/key")
        self.assertEqual(val, "hello_barbican")

        # 6. put_entry
        ok = self.client.put_entry("/some/key", "new_val")
        self.assertTrue(ok)

        # 7. delete_entry
        del_count = self.client.delete_entry("/some/key")
        self.assertEqual(del_count, 1)

    def test_http_jsonrpc_direct(self):
        import urllib.request
        import urllib.error
        req_body = json.dumps({
            "jsonrpc": "2.0",
            "id": 100,
            "method": "list_registries",
            "params": {"prefix": "/r/"},
        }).encode("utf-8")
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.port}/rpc",
            data=req_body,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req) as resp:
            self.assertEqual(resp.status, 200)
            body = json.loads(resp.read().decode("utf-8"))
            self.assertEqual(body["result"]["registries"], ["acme", "cloud_iot"])

        # Confirm GET /api/registries returns 404 (no legacy REST fallback)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{self.port}/api/registries")
            self.fail("Expected 404 for legacy REST GET /api/registries")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 404)

    def test_client_endpoint_formatting(self):
        c1 = BarbicanClient(endpoint="http://remote-barbican:8085")
        self.assertEqual(c1.endpoint, "http://remote-barbican:8085/rpc")
        c2 = BarbicanClient(endpoint="http://remote-barbican:8085/rpc")
        self.assertEqual(c2.endpoint, "http://remote-barbican:8085/rpc")

    def test_provider_ssl_context_setup(self):
        provider = BarbicanProvider(target="https://etcd:2379")
        self.assertEqual(provider.target, "https://etcd:2379")
        self.assertIsNotNone(provider.ssl_context)



class TestBarbicanMcpStdioRunner(unittest.TestCase):
    """Test running bin/mcp_barbican in stdio mode via subprocess."""

    def test_stdio_initialize_and_tools_list(self):
        proc = subprocess.Popen(
            ["bin/mcp_barbican", "mcp"],
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
            self.assertEqual(init_resp["result"]["serverInfo"]["name"], "udmi-barbican")

            # 2. tools/list
            proc.stdin.write(json.dumps({"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}) + "\n")
            proc.stdin.flush()
            tools_line = proc.stdout.readline()
            tools_resp = json.loads(tools_line)
            tools = [t["name"] for t in tools_resp["result"]["tools"]]
            self.assertIn("list_registries", tools)
            self.assertIn("list_devices", tools)
            self.assertIn("get_device_properties", tools)
            self.assertIn("health", tools)
        finally:
            proc.terminate()
            try:
                proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                proc.kill()


class TestUiServerBarbicanIndirection(unittest.TestCase):
    """Test that ui/v2/server.py routes /api/registries endpoints via BarbicanClient."""

    @classmethod
    def setUpClass(cls):
        from ui.v2.server import UDMIRequestHandler

        class ReusableServer(HTTPServer):
            allow_reuse_address = True

        cls.httpd = ReusableServer(("127.0.0.1", 0), UDMIRequestHandler)
        cls.port = cls.httpd.server_address[1]
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()
        time.sleep(0.1)

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()

    @patch("ui.v2.server.start_etcd_explorer_service", return_value=True)
    @patch("ui.v2.server.BarbicanClient")
    def test_ui_server_indirected_registries(self, mock_client_cls, mock_start):
        mock_client = MagicMock()
        mock_client.list_registries.return_value = {
            "registries": ["reg_alpha", "reg_beta"],
            "totalDevicesCount": 12,
        }
        mock_client_cls.return_value = mock_client

        import urllib.request
        url = f"http://127.0.0.1:{self.port}/api/registries"
        with urllib.request.urlopen(url) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertEqual(data["registries"], ["reg_alpha", "reg_beta"])
            self.assertEqual(data["totalDevicesCount"], 12)

        mock_client.list_registries.assert_called_once()

    @patch("ui.v2.server.start_etcd_explorer_service", return_value=True)
    @patch("ui.v2.server.BarbicanClient")
    def test_ui_server_indirected_devices(self, mock_client_cls, mock_start):
        mock_client = MagicMock()
        mock_client.list_devices.return_value = {
            "registryId": "reg_alpha",
            "devices": ["DEV-1", "DEV-2"],
        }
        mock_client_cls.return_value = mock_client

        import urllib.request
        url = f"http://127.0.0.1:{self.port}/api/registries/reg_alpha/devices"
        with urllib.request.urlopen(url) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertEqual(data["devices"], ["DEV-1", "DEV-2"])

        mock_client.list_devices.assert_called_once_with("reg_alpha")

    @patch("ui.v2.server.start_etcd_explorer_service", return_value=True)
    @patch("ui.v2.server.BarbicanClient")
    def test_ui_server_indirected_properties(self, mock_client_cls, mock_start):
        mock_client = MagicMock()
        mock_client.get_device_properties.return_value = {
            "registryId": "reg_alpha",
            "deviceId": "DEV-1",
            "properties": {":numId": "42", "/status": "OK"},
        }
        mock_client_cls.return_value = mock_client

        import urllib.request
        url = f"http://127.0.0.1:{self.port}/api/registries/reg_alpha/devices/DEV-1/properties"
        with urllib.request.urlopen(url) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertEqual(data["properties"][":numId"], "42")

        mock_client.get_device_properties.assert_called_once_with("reg_alpha", "DEV-1")


if __name__ == "__main__":
    unittest.main()
