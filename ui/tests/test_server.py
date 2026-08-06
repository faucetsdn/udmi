import unittest
import json
import urllib.request
import urllib.parse
import threading
import time
from http.server import HTTPServer
import ui.src.server as ui_server
from ui.src.server import UDMIRequestHandler

class TestUIServer(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(('127.0.0.1', 8089), UDMIRequestHandler)
        cls.server_thread = threading.Thread(target=cls.server.serve_forever)
        cls.server_thread.daemon = True
        cls.server_thread.start()
        time.sleep(0.2)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def test_post_run_triage_api_key_and_session_workspace(self):
        url = "http://127.0.0.1:8089/api/run_triage"
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
        except urllib.error.HTTPError as e:
            # 412 is acceptable if sequence log is missing for AHU-1 in demo environment
            self.assertIn(e.code, [200, 412])

    def test_post_authorization_header_extraction(self):
        url = "http://127.0.0.1:8089/api/run_triage"
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
        except urllib.error.HTTPError as e:
            self.assertIn(e.code, [200, 412])

    def test_path_traversal_prevention_read_file(self):
        url = "http://127.0.0.1:8089/api/read_file?path=/etc/passwd"
        req = urllib.request.Request(url, method="GET")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            urllib.request.urlopen(req)
        self.assertEqual(ctx.exception.code, 403)

    def test_path_traversal_prevention_list(self):
        url = "http://127.0.0.1:8089/api/list?path=/etc"
        req = urllib.request.Request(url, method="GET")
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            urllib.request.urlopen(req)
        self.assertEqual(ctx.exception.code, 403)

    def test_home_relative_list(self):
        url = "http://127.0.0.1:8089/api/list?path=~"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("path"), "~")

    def test_fetch_udmis_logs_parameter_acceptance(self):
        url = "http://127.0.0.1:8089/api/run_triage"
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
        except urllib.error.HTTPError as e:
            self.assertIn(e.code, [200, 412])

    def test_device_results_returns_project_spec(self):
        url = "http://127.0.0.1:8089/api/device_results?site_model=sites/udmi_site_model&device=AHU-1"
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
        url = "http://127.0.0.1:8089/api/list?path=sites"
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
        url = "http://127.0.0.1:8089/api/read_file?path=sites/udmi_site_model/cloud_iot_config.json"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("path", data)
            self.assertIn("content", data)
            self.assertIn("project_id", data["content"])

    def test_get_devices(self):
        url = "http://127.0.0.1:8089/api/devices?site_model=sites/udmi_site_model"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("site_model", data)
            self.assertIn("devices", data)
            self.assertIn("AHU-1", data["devices"])

    def test_testbed_status(self):
        url = "http://127.0.0.1:8089/api/testbed/status?site_model=sites/udmi_site_model"
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
        url = "http://127.0.0.1:8089/api/testbed/topology?site_model=sites/udmi_site_model&project_spec=//mqtt/localhost:18833"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertEqual(data.get("topology_type"), "LOCAL_MQTT")
            self.assertIn("nodes", data)
            self.assertIn("edges", data)

    def test_testbed_start(self):
        url = "http://127.0.0.1:8089/api/testbed/start"
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
            self.assertEqual(data.get("status"), "starting")
            sid = data["session_id"]
            if sid in ui_server.active_processes:
                proc_meta = ui_server.active_processes[sid]
                # Cleanup process
                p = proc_meta.get("process")
                if p and p.poll() is None:
                    p.terminate()

    def test_log_diff(self):
        url = "http://127.0.0.1:8089/api/log_diff"
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
        url = "http://127.0.0.1:8089/api/ai_query"
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
        url = "http://127.0.0.1:8089/api/testbed/jobs"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("jobs", data)
            self.assertIsInstance(data["jobs"], list)

    def test_git_status(self):
        url = "http://127.0.0.1:8089/api/git/status?site_model=sites/udmi_site_model"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as response:
            self.assertEqual(response.status, 200)
            data = json.loads(response.read().decode('utf-8'))
            self.assertIn("branch", data)
            self.assertIn("is_protected", data)

    def test_git_commit_safety_stop(self):
        import os, subprocess, shutil
        test_repo = os.path.join(ui_server.ROOT_DIR, 'out', 'test_git_repo_master')
        shutil.rmtree(test_repo, ignore_errors=True)
        os.makedirs(test_repo, exist_ok=True)
        try:
            subprocess.run(['git', '-C', test_repo, 'init', '--initial-branch=main'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            # Create a file so we can test status and committing
            with open(os.path.join(test_repo, 'test_file.txt'), 'w') as f:
                f.write('initial content')
            
            # Test safety stop: committing directly to 'main' without create_branch or force_main should return HTTP 400
            url = "http://127.0.0.1:8089/api/git/commit"
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

            # Now test creating a new results branch (should succeed without error)
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
        url = "http://127.0.0.1:8089/api/notifications/send_email"
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
        import os
        import shutil
        sessions_dir = os.path.join(ui_server.ROOT_DIR, 'out', 'sessions')
        os.makedirs(sessions_dir, exist_ok=True)
        # Create dummy session directories
        for i in range(15):
            d = os.path.join(sessions_dir, f"test_dummy_session_{i}")
            os.makedirs(d, exist_ok=True)
        
        ui_server.prune_old_sessions(10)
        
        # Clean up any remaining test_dummy folders
        remaining_dummies = [e for e in os.listdir(sessions_dir) if e.startswith("test_dummy_session_")]
        self.assertLessEqual(len(remaining_dummies), 10)
        for e in remaining_dummies:
            shutil.rmtree(os.path.join(sessions_dir, e), ignore_errors=True)

if __name__ == '__main__':
    unittest.main()


