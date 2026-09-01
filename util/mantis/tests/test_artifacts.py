import json
import os
import tempfile
import unittest
from unittest.mock import MagicMock

from mantis.tools.artifacts import locate_test_artifacts, list_available_test_runs
from mantis.agent.chat import MantisChatSession


class TestArtifactsDiscovery(unittest.TestCase):
    """Tests for the autonomous test output and log artifact discovery suite."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = self.temp_dir.name

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_locate_site_udmi_out_artifacts(self):
        # Create mock structure: sites/SITE_A/udmi/out/devices/DEV_1/tests/TEST_1/
        test_dir = os.path.join(self.root, "sites", "SITE_A", "udmi", "out", "devices", "DEV_1", "tests", "TEST_1")
        os.makedirs(test_dir, exist_ok=True)

        seq_log = os.path.join(test_dir, "sequence.log")
        seq_md = os.path.join(test_dir, "sequence.md")
        events_json = os.path.join(test_dir, "events_pointset.json")
        with open(seq_log, "w") as f:
            f.write("2026-08-07T07:22:05Z NOTICE Starting test TEST_1\n2026-08-07T07:22:10Z RESULT PASS\n")
        with open(seq_md, "w") as f:
            f.write("## TEST_1\nTest passed.\n")
        with open(events_json, "w") as f:
            f.write('{"timestamp": "2026-08-07T07:22:08Z"}\n')

        res = locate_test_artifacts(
            udmi_root=self.root,
            site="SITE_A",
            device="DEV_1",
            test="TEST_1"
        )
        art = res.get("artifacts", {})
        self.assertIsNotNone(art["sequence_log"])
        self.assertTrue(art["sequence_log"].endswith("sequence.log"))
        self.assertIsNotNone(art["sequence_md"])
        self.assertEqual(len(art["events_json"]), 1)

    def test_locate_real_workspace_artifacts_if_present(self):
        # Test on active repo root
        real_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
        site_path = os.path.join(real_root, "sites", "udmi_site_model", "out", "devices", "AHU-1", "tests", "pointset_publish")
        if os.path.exists(site_path):
            res = locate_test_artifacts(
                udmi_root=real_root,
                site="udmi_site_model",
                device="AHU-1",
                test="pointset_publish"
            )
            art = res.get("artifacts", {})
            self.assertIsNotNone(art["sequence_log"])
            self.assertIsNotNone(art["sequence_md"])

    def test_list_available_test_runs_output(self):
        test_dir = os.path.join(self.root, "sites", "SITE_B", "udmi", "out", "devices", "DEV_2", "tests", "system_test")
        os.makedirs(test_dir, exist_ok=True)
        with open(os.path.join(test_dir, "sequence.log"), "w") as f:
            f.write("log")

        summary = list_available_test_runs(udmi_root=self.root, site="SITE_B")
        self.assertIn("SITE_B", summary)
        self.assertIn("DEV_2", summary)
        self.assertIn("system_test", summary)

    def test_chat_session_timeline_with_located_artifacts(self):
        test_dir = os.path.join(self.root, "sites", "SITE_C", "udmi", "out", "devices", "DEV_3", "tests", "pointset_publish")
        os.makedirs(test_dir, exist_ok=True)
        seq_log = os.path.join(test_dir, "sequence.log")
        with open(seq_log, "w") as f:
            f.write("[sequencer] 2026-08-07T07:22:05Z NOTICE Starting test pointset_publish\n"
                    "[sequencer] 2026-08-07T07:22:15Z RESULT FAIL: Missing points\n")

        mock_client = MagicMock()
        session = MantisChatSession(
            udmi_root=self.root,
            client=mock_client,
            site_model="SITE_C",
            device_id="DEV_3",
            test_id="pointset_publish"
        )
        timeline = session.get_test_event_timeline()
        self.assertIn("Starting test pointset_publish", timeline)
        self.assertIn("Missing points", timeline)

    def test_get_test_execution_summary(self):
        from mantis.tools.artifacts import get_test_execution_summary
        test_dir = os.path.join(self.root, "sites", "SITE_D", "udmi", "out", "devices", "DEV_4", "tests", "pointset_publish")
        os.makedirs(test_dir, exist_ok=True)
        seq_log = os.path.join(test_dir, "sequence.log")
        with open(seq_log, "w") as f:
            f.write("2024-12-05T12:45:08Z NOTICE starting test pointset_publish\n"
                    "2024-12-05T12:45:17Z DEBUG update config_update, adding configTransaction RC:9a6ddf.00000134\n"
                    "2024-12-05T12:45:18Z TRACE update state_update, has configTransaction RC:9a6ddf.00000134\n"
                    "2024-12-05T12:45:19Z NOTICE RESULT PASS\n")

        res = get_test_execution_summary(
            udmi_root=self.root,
            test_id="pointset_publish",
            device_id="DEV_4",
            site_name="SITE_D"
        )
        self.assertIn("Test Execution Summary", res)
        self.assertIn("DEV_4", res)
        self.assertIn("PASS", res)
        self.assertIn("RC:9a6ddf.00000134", res)


if __name__ == "__main__":
    unittest.main()

