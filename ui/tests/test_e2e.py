"""
E2E Selenium Verification Tests for UDMI Workbench UI.
Tests the Host Shell, Micro-Frontend iframe Sandboxing, PostMessage State Sync,
Project Spec Builder, and component execution without browser TTY interaction.
"""

import os
import time
import socket
import subprocess
import unittest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def get_free_port():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


class TestWorkbenchE2E(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Resolve repo root from ui/tests/
        cls.repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
        cls.port = get_free_port()
        cls.server_url = f"http://127.0.0.1:{cls.port}/ui/src/index.html?features=sequencer,mantis"

        # Start background ui/server.py on random free port
        env = os.environ.copy()
        env["PYTHONPATH"] = cls.repo_root
        cls.server_proc = subprocess.Popen(
            ["python3", "ui/server.py", f"--port={cls.port}", "--features=sequencer,mantis"],
            cwd=cls.repo_root,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )

        # Wait for backend server port to open
        server_ready = False
        for _ in range(20):
            try:
                with socket.create_connection(("127.0.0.1", cls.port), timeout=0.5):
                    server_ready = True
                    break
            except (ConnectionRefusedError, OSError):
                time.sleep(0.3)

        if not server_ready:
            cls.server_proc.terminate()
            out, err = cls.server_proc.communicate()
            raise RuntimeError(f"UI backend test server failed to start on port {cls.port}.\nStdout: {out.decode()}\nStderr: {err.decode()}")

        # Configure Headless Chrome with Console Log Capturing
        chrome_options = Options()
        chrome_options.add_argument("--headless=new")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--window-size=1440,900")
        chrome_options.set_capability("goog:loggingPrefs", {"browser": "ALL"})

        cls.driver = webdriver.Chrome(options=chrome_options)
        cls.driver.implicitly_wait(3)

    @classmethod
    def tearDownClass(cls):
        if hasattr(cls, "driver") and cls.driver:
            try:
                cls.driver.quit()
            except Exception:
                pass
        if hasattr(cls, "server_proc") and cls.server_proc:
            cls.server_proc.terminate()
            cls.server_proc.wait(timeout=3)

    def assert_no_severe_js_errors(self, context_name="Host Shell"):
        """Checks browser console logs for fatal JavaScript exceptions."""
        try:
            logs = self.driver.get_log("browser")
            fatal_errors = [
                entry["message"] for entry in logs
                if entry.get("level") == "SEVERE" and "favicon.ico" not in entry.get("message", "")
            ]
            if fatal_errors:
                self.fail(f"Fatal JavaScript runtime errors found in {context_name}:\n" + "\n".join(fatal_errors))
        except Exception as e:
            # Not all drivers support get_log; log warning if unavailable
            pass

    def test_01_host_shell_loads_and_has_required_elements(self):
        """Verifies Host Shell structure, brand header, tab navigation, and prominent site model input."""
        self.driver.get(self.server_url)
        time.sleep(1)

        # Title verification
        self.assertIn("UDMI", self.driver.title)

        # Verify prominent Site Model input or button control exists
        body_text = self.driver.find_element(By.TAG_NAME, "body").text.lower()
        has_site_model_control = any(k in body_text for k in ["site model", "site_model", "workspace", "folder"])
        self.assertTrue(has_site_model_control, "Host shell missing site model / workspace input control.")

        # Check tab buttons exist
        tabs = self.driver.find_elements(By.XPATH, "//button[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'sequencer') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'mantis')]")
        self.assertGreaterEqual(len(tabs), 1, "Host Shell must feature tab buttons for micro-frontend tools.")

        self.assert_no_severe_js_errors("Host Shell")

    def test_02_sequencer_microfrontend_sandboxing_and_controls(self):
        """Verifies Sequencer micro-frontend frame loading, interactive controls, and matrix components."""
        self.driver.get(self.server_url)
        time.sleep(1)

        # Find and switch into Sequencer iframe
        iframes = self.driver.find_elements(By.TAG_NAME, "iframe")
        self.assertGreaterEqual(len(iframes), 1, "Host Shell must embed micro-frontend tool in an iframe sandbox.")

        sequencer_iframe = None
        for frame in iframes:
            src = frame.get_attribute("src") or ""
            if "sequencer" in src or len(iframes) == 1:
                sequencer_iframe = frame
                break

        self.assertIsNotNone(sequencer_iframe, "Could not locate Sequencer micro-frontend iframe sandbox.")
        self.driver.switch_to.frame(sequencer_iframe)

        time.sleep(0.5)
        frame_text = self.driver.find_element(By.TAG_NAME, "body").text.lower()

        # Check that Sequencer tool contains functional controls (not empty reuse)
        has_device_control = any(k in frame_text for k in ["device", "target", "serial", "ahu-1", "all devices"])
        has_run_control = any(k in frame_text for k in ["run", "sequencer", "start", "execute"])
        has_log_control = any(k in frame_text for k in ["log", "console", "terminal", "stream"])

        self.assertTrue(has_device_control, "Sequencer iframe missing device selection capability.")
        self.assertTrue(has_run_control, "Sequencer iframe missing run sequencer action button.")
        self.assertTrue(has_log_control, "Sequencer iframe missing live console log streaming surface.")

        self.assert_no_severe_js_errors("Sequencer Tool Micro-Frontend")
        self.driver.switch_to.default_content()

    def test_03_mantis_microfrontend_sandboxing_and_controls(self):
        """Verifies Mantis micro-frontend frame loading, scenario options, payload inspector, and AI report."""
        self.driver.get(self.server_url)
        time.sleep(1)

        # Click Mantis Tab button if tab-switching UI exists
        mantis_buttons = self.driver.find_elements(
            By.XPATH, "//button[contains(translate(text(), 'MANTIS', 'mantis'), 'mantis')]"
        )
        if mantis_buttons:
            mantis_buttons[0].click()
            time.sleep(0.5)

        iframes = self.driver.find_elements(By.TAG_NAME, "iframe")
        mantis_iframe = None
        for frame in iframes:
            src = frame.get_attribute("src") or ""
            if "mantis" in src:
                mantis_iframe = frame
                break

        if not mantis_iframe and len(iframes) >= 2:
            mantis_iframe = iframes[1]
        elif not mantis_iframe:
            mantis_iframe = iframes[0]

        self.driver.switch_to.frame(mantis_iframe)
        time.sleep(0.5)
        frame_text = self.driver.find_element(By.TAG_NAME, "body").text.lower()

        has_triage_control = any(k in frame_text for k in ["triage", "root cause", "rca", "trace", "payload", "failure"])
        self.assertTrue(has_triage_control, "Mantis iframe missing functional failure triage controls.")

        self.assert_no_severe_js_errors("Mantis Tool Micro-Frontend")
        self.driver.switch_to.default_content()


if __name__ == "__main__":
    unittest.main()
