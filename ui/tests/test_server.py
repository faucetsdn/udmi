import unittest
import json
import urllib.request
import urllib.parse
import threading
import time
from http.server import HTTPServer
import ui.server
from ui.server import UDMIRequestHandler

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
                    self.assertIn(session_id, ui.server.active_processes)
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
            if "blob_update_oversize" in results:
                self.assertIn("project_spec", results["blob_update_oversize"])
                self.assertEqual(results["blob_update_oversize"]["project_spec"], "localhost")
            if "blob_update_success" in results:
                self.assertIn("project_spec", results["blob_update_success"])
                self.assertEqual(results["blob_update_success"]["project_spec"], "bos-platform-dev")

    def test_prune_old_sessions(self):
        import os
        import shutil
        sessions_dir = os.path.join(ui.server.ROOT_DIR, 'out', 'sessions')
        os.makedirs(sessions_dir, exist_ok=True)
        # Create dummy session directories
        for i in range(15):
            d = os.path.join(sessions_dir, f"test_dummy_session_{i}")
            os.makedirs(d, exist_ok=True)
        
        ui.server.prune_old_sessions(10)
        
        # Clean up any remaining test_dummy folders
        remaining_dummies = [e for e in os.listdir(sessions_dir) if e.startswith("test_dummy_session_")]
        self.assertLessEqual(len(remaining_dummies), 10)
        for e in remaining_dummies:
            shutil.rmtree(os.path.join(sessions_dir, e), ignore_errors=True)

if __name__ == '__main__':
    unittest.main()


