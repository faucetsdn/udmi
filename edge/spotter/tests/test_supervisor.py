#!/usr/bin/env python3
import os
import sys
import time
import tempfile
import subprocess
import unittest

SUPERVISOR_PATH = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "../container/supervisor.sh")
)

class TestSupervisorHost(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.config_file = os.path.join(self.test_dir.name, "config.json")
        with open(self.config_file, "w") as f:
            f.write('{"log_level": "INFO"}')

    def tearDown(self):
        self.test_dir.cleanup()

    def create_mock_script(self, name, behavior):
        path = os.path.join(self.test_dir.name, name)
        script_content = f"""#!/usr/bin/env python3
import os
import sys
import time
import signal

print(f"MOCK START: {name} (PID: {{os.getpid()}})", flush=True)

def handler(signum, frame):
    print(f"MOCK SIGTERM: {name} (PID: {{os.getpid()}})", flush=True)
    sys.exit(0)

signal.signal(signal.SIGTERM, handler)
signal.signal(signal.SIGINT, handler)

{behavior}
print(f"MOCK EXIT: {name} (PID: {{os.getpid()}})", flush=True)
"""
        with open(path, "w") as f:
            f.write(script_content)
        os.chmod(path, 0o755)
        return path

    def run_supervisor_test(self, legacy_behavior, spotter_behavior, action):
        legacy_path = self.create_mock_script("legacy.py", legacy_behavior)
        spotter_path = self.create_mock_script("spotter.py", spotter_behavior)

        env = os.environ.copy()
        env["LEGACY_PATH"] = legacy_path
        env["SPOTTER_PATH"] = spotter_path
        env["LEGACY_VENV"] = "python3"
        env["SPOTTER_VENV"] = "python3"

        cmd = [SUPERVISOR_PATH, "--config_file", self.config_file]
        proc = subprocess.Popen(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        if action == "shutdown":
            time.sleep(2)
            proc.terminate()
            try:
                exit_code = proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
                exit_code = proc.wait()
        else:
            try:
                exit_code = proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
                exit_code = proc.wait()

        stdout, stderr = proc.communicate()
        return exit_code, stdout, stderr

    def test_normal_lifecycle_and_shutdown(self):
        exit_code, output, errors = self.run_supervisor_test(
            "time.sleep(60)", "time.sleep(60)", "shutdown"
        )
        self.assertEqual(exit_code, 0, f"Expected exit code 0, got {exit_code}\nSTDOUT:\n{output}\nSTDERR:\n{errors}")
        self.assertIn("Supervisor: Received shutdown signal", output)
        self.assertIn("Supervisor: All child processes terminated. Exiting.", output)

    def test_legacy_crash(self):
        exit_code, output, errors = self.run_supervisor_test(
            "sys.exit(1)", "time.sleep(60)", "wait"
        )
        self.assertEqual(exit_code, 101, f"Expected exit code 101, got {exit_code}\nSTDOUT:\n{output}\nSTDERR:\n{errors}")
        self.assertIn("Supervisor: Fatal crash detected on legacy node. Restarting container.", output)

    def test_spotter_crash(self):
        exit_code, output, errors = self.run_supervisor_test(
            "time.sleep(60)", "sys.exit(2)", "wait"
        )
        self.assertEqual(exit_code, 102, f"Expected exit code 102, got {exit_code}\nSTDOUT:\n{output}\nSTDERR:\n{errors}")
        self.assertIn("Supervisor: Fatal crash detected on Spotter agent. Restarting container.", output)

    def test_ota_staging_rollback(self):
        spotter_behavior = f"""
flag_file = "{os.path.join(self.test_dir.name, 'restarted')}"
if not os.path.exists(flag_file):
    with open(flag_file, 'w') as f:
        f.write('1')
    sys.exit(42)
else:
    sys.exit(2)
"""
        exit_code, output, errors = self.run_supervisor_test(
            "time.sleep(60)", spotter_behavior, "wait"
        )
        self.assertEqual(exit_code, 102, f"Expected exit code 102, got {exit_code}\nSTDOUT:\n{output}\nSTDERR:\n{errors}")
        self.assertIn("Supervisor: OTA staging exit code (42) detected from Spotter agent.", output)
        self.assertIn("Rejecting update and initiating ROLLBACK...", output)
        self.assertIn("OTA rollback complete. Restarting Spotter Core Agent on previous known-good state...", output)

if __name__ == "__main__":
    unittest.main()
