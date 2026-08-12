"""
Unit tests for the UDMI Workbench backend API & HTTP server.
"""

import json
import os
import shutil
import subprocess
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request
from http.server import HTTPServer
from unittest.mock import patch

try:
    import ui.v2.server as ui_server
    from ui.v2.server import UDMIRequestHandler
except ImportError:
    import ui.src.server as ui_server
    from ui.src.server import UDMIRequestHandler


class ReusableHTTPServer(HTTPServer):
    allow_reuse_address = True


class TestUIServer(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ReusableHTTPServer(('127.0.0.1', 0), UDMIRequestHandler)
        cls.port = cls.server.server_address[1]
        cls.server_thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.server_thread.start()
        time.sleep(0.2)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        # Clean up any remaining processes
        with ui_server.active_processes_lock:
            for meta in ui_server.active_processes.values():
                p = meta.get("process")
                if p and p.poll() is None:
                    try:
                        p.terminate()
                        p.wait(timeout=1)
                    except Exception:
                        pass

    def test_post_run_triage_api_key_and_session_workspace(self):
        url = f"http://127.0.0.1:{self.port}/api/run_triage"
        data = json.dumps({
            "device_id": "AHU-1",
            "test_id": "pointset_publish",
            "gemini_api_key": "test-secret-key-123",
            "site_model": "sites/udmi_site_model"
        }).encode('utf-8')

        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")

        try:
            with urllib.request.urlopen(req) as response:
                res_body = json.loads(response.read().decode('utf-8'))
                self.assertIn(response.status, [200, 412])
                if response.status == 200 and res_body.get("status") == "Started":
                    self.assertIn("session_id", res_body)
                    session_id = res_body["session_id"]
                    self.assertIn(session_id, ui_server.active_processes)
                    p = ui_server.active_processes[session_id].get("process")
                    if p and p.poll() is None:
                        p.terminate()
                        p.wait(timeout=1)
        except urllib.error.HTTPError as e:
            # 412 is acceptable if sequence log is missing for AHU-1 in demo environment
            self.assertIn(e.code, [200, 412])

    def test_post_authorization_header_extraction(self):
        url = f"http://127.0.0.1:{self.port}/api/run_triage"
        data = json.dumps({
            "device_id": "AHU-1",
            "test_id": "pointset_publish"
        }).encode('utf-8')

        headers = {
            "Content-Type": "application/json",
            "Authorization": "Bearer header-secret-key-456"
        }
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")

        try:
            with urllib.request.urlopen(req) as response:
                self.assertIn(response.status, [200, 412])
                res_body = json.loads(response.read().decode('utf-8'))
                if response.status == 200 and res_body.get("status") == "Started":
                    session_id = res_body["session_id"]
                    p = ui_server.active_processes[session_id].get("process")
                    if p and p.poll() is None:
                        p.terminate()
                        p.wait(timeout=1)
        except urllib.error.HTTPError as e:
            self.assertIn(e.code, [200, 412])

    def test_path_traversal_prevention_read_file(self):
        url = f"http://127.0.0.1:{self.port}/api/read_file?path=/etc/passwd"
        req = urllib.request.Request(url, method="GET")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            urllib.request.urlopen(req)
        self.assertEqual(ctx.exception.code, 403)

    def test_path_traversal_prevention_list(self):
        url = f"http://127.0.0.1:{self.port}/api/list?path=/etc"
        req = urllib.request.Request(url, method="GET")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            urllib.request.urlopen(req)
        self.assertEqual(ctx.exception.code, 403)

    def test_home_relative_list(self):
        url = f"http://127.0.0.1:{self.port}/api/list?path=~"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("path"), "~")

    def test_fetch_udmis_logs_parameter_acceptance(self):
        url = f"http://127.0.0.1:{self.port}/api/run_triage"
        data = json.dumps({
            "device_id": "AHU-1",
            "test_id": "pointset_publish",
            "gemini_api_key": "test-secret-key-123",
            "site_model": "sites/udmi_site_model",
            "project_spec": "//gcp/test-gcp-project",
            "fetch_udmis": True,
            "cloud_project": "test-gcp-project"
        }).encode('utf-8')

        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")

        try:
            with urllib.request.urlopen(req) as response:
                self.assertIn(response.status, [200, 412])
                res_body = json.loads(response.read().decode('utf-8'))
                if response.status == 200 and res_body.get("status") == "Started":
                    session_id = res_body["session_id"]
                    p = ui_server.active_processes[session_id].get("process")
                    if p and p.poll() is None:
                        p.terminate()
                        p.wait(timeout=1)
        except urllib.error.HTTPError as e:
            self.assertIn(e.code, [200, 412])

    def test_device_results_returns_project_spec(self):
        url = f"http://127.0.0.1:{self.port}/api/device_results?site_model=sites/udmi_site_model&device=AHU-1"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("device"), "AHU-1")
            self.assertIn("results", data)
            results = data["results"]
            for test_name, res in results.items():
                self.assertIn("project_spec", res)
                self.assertIsNotNone(res["project_spec"])

    def test_list_spec_fields(self):
        url = f"http://127.0.0.1:{self.port}/api/list?path=sites"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("path", data)
            self.assertIn("absolute_path", data)
            self.assertIn("parent_path", data)
            self.assertIn("entries", data)
            self.assertIn("folders", data)

    def test_read_file_json(self):
        url = f"http://127.0.0.1:{self.port}/api/read_file?path=sites/udmi_site_model/cloud_iot_config.json"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("path", data)
            self.assertIn("content", data)
            self.assertIn("project_id", data["content"])

    def test_get_devices(self):
        url = f"http://127.0.0.1:{self.port}/api/devices?site_model=sites/udmi_site_model"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("site_model", data)
            self.assertIn("devices", data)
            self.assertIn("AHU-1", data["devices"])

    def test_get_devices_udmi_nested(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            dev_dir = os.path.join(tmp_dir, "nested_site", "udmi", "devices", "DEV-1")
            os.makedirs(dev_dir, exist_ok=True)
            with open(os.path.join(dev_dir, "metadata.json"), "w") as f:
                json.dump({"version": "1.0", "serial_no": "12345"}, f)
            url = f"http://127.0.0.1:{self.port}/api/devices?site_model={os.path.join(tmp_dir, 'nested_site')}"
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req) as response:
                self.assertEqual(response.status, 200)
                data = json.loads(response.read().decode('utf-8'))
                self.assertIn("devices", data)
                self.assertIn("DEV-1", data["devices"])

    def test_testbed_status(self):
        url = f"http://127.0.0.1:{self.port}/api/testbed/status?site_model=sites/udmi_site_model"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("overall_status", data)
            self.assertIn("components", data)
            self.assertIn("mqtt_broker", data["components"])
            self.assertIn("validator", data["components"])
            self.assertIn("sequencer", data["components"])
            self.assertIn("udmis", data["components"])

    def test_testbed_topology(self):
        url = f"http://127.0.0.1:{self.port}/api/testbed/topology?site_model=sites/udmi_site_model&project_spec=//mqtt/localhost:18833"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("topology_type"), "LOCAL_MQTT")
            self.assertIn("nodes", data)
            self.assertIn("edges", data)

    def test_testbed_start(self):
        url = f"http://127.0.0.1:{self.port}/api/testbed/start"
        payload = json.dumps({
            "site_model": "sites/udmi_site_model",
            "project_spec": "//mqtt/localhost:18833"
        }).encode('utf-8')
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("session_id", data)
            self.assertIn(data.get("status"), ["starting", "ready"])
            sid = data["session_id"]
            if sid in ui_server.active_processes:
                proc_meta = ui_server.active_processes[sid]
                p = proc_meta.get("process")
                if p and p.poll() is None:
                    p.terminate()
                    p.wait(timeout=1)

    def test_log_diff(self):
        url = f"http://127.0.0.1:{self.port}/api/log_diff"
        payload = json.dumps({
            "site_model": "sites/udmi_site_model",
            "device_id": "AHU-1",
            "test_id": "system.config"
        }).encode('utf-8')
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("device_id"), "AHU-1")
            self.assertEqual(data.get("test_id"), "system.config")
            self.assertIn("diff_lines", data)

    def test_ai_query(self):
        url = f"http://127.0.0.1:{self.port}/api/ai_query"
        payload = json.dumps({
            "query": "Why did AHU-1 fail validation?",
            "context": {"site_model": "sites/udmi_site_model", "active_device": "AHU-1"}
        }).encode('utf-8')
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("query_id", data)
            self.assertIn("answer_markdown", data)

    def test_testbed_jobs(self):
        url = f"http://127.0.0.1:{self.port}/api/testbed/jobs"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("jobs", data)
            self.assertIsInstance(data["jobs"], list)

    def test_git_status(self):
        url = f"http://127.0.0.1:{self.port}/api/git/status?site_model=sites/udmi_site_model"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("branch", data)
            self.assertIn("is_protected", data)

    def test_git_commit_safety_stop(self):
        test_repo = os.path.join(ui_server.ROOT_DIR, 'out', 'test_git_repo_master')
        shutil.rmtree(test_repo, ignore_errors=True)
        os.makedirs(test_repo, exist_ok=True)
        try:
            subprocess.run(['git', '-C', test_repo, 'init', '--initial-branch=main'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            subprocess.run(['git', '-C', test_repo, 'config', 'user.email', 'test@example.com'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            subprocess.run(['git', '-C', test_repo, 'config', 'user.name', 'Test User'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            with open(os.path.join(test_repo, 'test_file.txt'), 'w', encoding='utf-8') as f:
                f.write('initial content')

            url = f"http://127.0.0.1:{self.port}/api/git/commit"
            payload = json.dumps({
                "site_model": test_repo,
                "commit_message": "test: check safety stop",
                "create_branch": False,
                "force_main": False
            }).encode('utf-8')
            headers = {"Content-Type": "application/json"}
            req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
            try:
                with urllib.request.urlopen(req) as response:
                    self.fail("Expected HTTP 400 error when committing directly to main without override")
            except urllib.error.HTTPError as e:
                self.assertEqual(e.code, 400)
                error_body = json.loads(e.read().decode('utf-8'))
                self.assertIn("Safety stop", error_body.get("error", ""))

            payload_override = json.dumps({
                "site_model": test_repo,
                "commit_message": "test: save results on new branch",
                "create_branch": True,
                "branch_name": "test-results-branch"
            }).encode('utf-8')
            req_override = urllib.request.Request(url, data=payload_override, headers=headers, method="POST")
            with urllib.request.urlopen(req_override) as res_override:
                self.assertEqual(res_override.status, 200)
                data = json.loads(res_override.read().decode('utf-8'))
                self.assertEqual(data.get("status"), "success")
                self.assertEqual(data.get("branch"), "test-results-branch")
        finally:
            shutil.rmtree(test_repo, ignore_errors=True)

    def test_send_email(self):
        url = f"http://127.0.0.1:{self.port}/api/notifications/send_email"
        payload = json.dumps({
            "recipient": "test_engineer@udmi.system",
            "subject": "UDMI CI Test Alert",
            "body": "Test execution summary for AHU-1.",
            "rca_markdown": "### RCA Report\nMisconfigured telemetry interval."
        }).encode('utf-8')
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("status"), "delivered")
            self.assertIn("outbox_file", data)

    def test_prune_old_sessions(self):
        sessions_dir = os.path.join(ui_server.ROOT_DIR, 'out', 'sessions')
        os.makedirs(sessions_dir, exist_ok=True)
        for i in range(15):
            d = os.path.join(sessions_dir, f"test_dummy_session_{i}")
            os.makedirs(d, exist_ok=True)

        ui_server.prune_old_sessions(10)

        remaining_dummies = [e for e in os.listdir(sessions_dir) if e.startswith("test_dummy_session_")]
        self.assertLessEqual(len(remaining_dummies), 10)
        for e in remaining_dummies:
            shutil.rmtree(os.path.join(sessions_dir, e), ignore_errors=True)

    @patch("ui.v2.server.shutil.which", return_value="/usr/bin/dot")
    @patch("ui.v2.server.os.path.exists", side_effect=lambda p: True if p == "/usr/bin/dot" else os.path.exists(p))
    @patch("ui.v2.server.subprocess.run")
    def test_graphviz_render_success(self, mock_subproc, mock_exists, mock_which):
        mock_subproc.return_value = subprocess.CompletedProcess(
            args=["/usr/bin/dot", "-Tsvg"],
            returncode=0,
            stdout="<svg><g id='TestTopology'></g></svg>",
            stderr=""
        )
        url = f"http://127.0.0.1:{self.port}/api/graphviz/render"
        payload = json.dumps({
            "dot": "digraph TestTopology { A -> B [label=\"proxy\"]; }"
        }).encode('utf-8')
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("status"), "success")
            self.assertIn("<svg", data.get("svg", ""))
            self.assertIn("TestTopology", data.get("svg", ""))

    @patch("ui.v2.server.shutil.which", return_value="/usr/bin/dot")
    @patch("ui.v2.server.os.path.exists", side_effect=lambda p: True if p == "/usr/bin/dot" else os.path.exists(p))
    @patch("ui.v2.server.subprocess.run")
    def test_graphviz_render_invalid(self, mock_subproc, mock_exists, mock_which):
        mock_subproc.return_value = subprocess.CompletedProcess(
            args=["/usr/bin/dot", "-Tsvg"],
            returncode=1,
            stdout="",
            stderr="syntax error in line 1"
        )
        url = f"http://127.0.0.1:{self.port}/api/graphviz/render"
        payload = json.dumps({
            "dot": "invalid syntax not a graph"
        }).encode('utf-8')
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as response:
                self.assertEqual(response.status, 422)
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 422)
            data = json.loads(e.read().decode('utf-8'))
            self.assertEqual(data.get("status"), "error")

    @patch("ui.v2.server.shutil.which", return_value=None)
    @patch("ui.v2.server.os.path.exists", side_effect=lambda p: False if p == "/usr/bin/dot" else os.path.exists(p))
    def test_graphviz_render_not_installed(self, mock_exists, mock_which):
        url = f"http://127.0.0.1:{self.port}/api/graphviz/render"
        payload = json.dumps({
            "dot": "digraph TestTopology { A -> B; }"
        }).encode('utf-8')
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as response:
                self.fail("Expected HTTP 501 when dot is not installed")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 501)
            data = json.loads(e.read().decode('utf-8'))
            self.assertIn("not installed", data.get("error", ""))

    def test_testbed_proc_status(self):
        url = f"http://127.0.0.1:{self.port}/api/testbed_proc_status"
        with urllib.request.urlopen(url) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("running", data)
            self.assertIn("log", data)
            self.assertIn("offset", data)
            self.assertIn("ready", data)

    def test_parse_project_spec(self):
        parse_project_spec = ui_server.parse_project_spec
        # Case 1: //gbos/bos-platform-dev/heykhyati
        p1 = parse_project_spec("//gbos/bos-platform-dev/heykhyati")
        self.assertEqual(p1["provider"], "gbos")
        self.assertEqual(p1["project"], "bos-platform-dev")
        self.assertEqual(p1["namespace"], "heykhyati")
        self.assertEqual(p1["effective_namespace"], "heykhyati")
        self.assertTrue(p1["is_cloud"])

        # Case 2: //gbos/bos-platform-staging (no namespace -> defaults to udmis)
        p2 = parse_project_spec("//gbos/bos-platform-staging")
        self.assertEqual(p2["provider"], "gbos")
        self.assertEqual(p2["project"], "bos-platform-staging")
        self.assertIsNone(p2["namespace"])
        self.assertEqual(p2["effective_namespace"], "udmis")
        self.assertTrue(p2["is_cloud"])

        # Case 3: //gref/bos-platform-dev+heykhyati (user suffix, no namespace -> defaults to udmis)
        p3 = parse_project_spec("//gref/bos-platform-dev+heykhyati")
        self.assertEqual(p3["provider"], "gref")
        self.assertEqual(p3["project"], "bos-platform-dev")
        self.assertIsNone(p3["namespace"])
        self.assertEqual(p3["effective_namespace"], "udmis")
        self.assertEqual(p3["user"], "heykhyati")
        self.assertTrue(p3["is_cloud"])

        # Case 4: //gref/bos-platform-dev/faucetsdn+heykhyati (explicit namespace + user)
        p4 = parse_project_spec("//gref/bos-platform-dev/faucetsdn+heykhyati")
        self.assertEqual(p4["provider"], "gref")
        self.assertEqual(p4["project"], "bos-platform-dev")
        self.assertEqual(p4["namespace"], "faucetsdn")
        self.assertEqual(p4["effective_namespace"], "faucetsdn")
        self.assertEqual(p4["user"], "heykhyati")
        self.assertTrue(p4["is_cloud"])

    def test_testbed_status_zanzara_namespace(self):
        url = f"http://127.0.0.1:{self.port}/api/testbed/status?site_model=sites/udmi_site_model&project_spec=//gbos/bos-platform-dev/heykhyati"
        with urllib.request.urlopen(url) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("components", data)
            comps = data["components"]
            self.assertIn("cloud_udmis", comps)
            self.assertIn("zanzara_ingress", comps)
            self.assertIn("zanzara_fabric", comps)
            # Namespace in response should reflect heykhyati
            self.assertEqual(comps["cloud_udmis"]["namespace"], "heykhyati")


if __name__ == '__main__':
    unittest.main()


