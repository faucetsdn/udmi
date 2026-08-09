import json
import os
import unittest
from unittest.mock import patch, MagicMock

from mantis.tools.schemas import inspect_udmi_schema, list_udmi_schemas
from mantis.tools.site_models import inspect_site_model, list_site_devices
from mantis.tools.traces import inspect_message_trace
from mantis.tools.cloud_logs import extract_test_timebounds, pull_cloud_logs_for_test
from mantis.tools.differential import compare_test_sequences, parse_sequence_events, get_historical_site_state
from mantis.agent.prompts import build_udmi_system_prompt





class TestUDMIDomainTools(unittest.TestCase):
    """Tests for specialized UDMI schema, site model, trace, cloud logs, differential analysis, and prompt tools."""

    def setUp(self):
        self.udmi_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))

    def test_inspect_udmi_schema_pointset(self):
        res = inspect_udmi_schema(self.udmi_root, "pointset")
        self.assertIn("pointset", res.lower())
        self.assertIn("```json", res)

    def test_list_udmi_schemas_with_filter(self):
        res = list_udmi_schemas(self.udmi_root, filter_keyword="pointset")
        self.assertIn("events_pointset.json", res)

    def test_inspect_site_model_and_device(self):
        res = inspect_site_model(self.udmi_root, "sites/UK-LON-GLAB", device_id="EM-11")
        self.assertIn("UK-LON-GLAB", res)
        self.assertIn("EM-11", res)

    def test_list_site_devices(self):
        res = list_site_devices(self.udmi_root, "sites/UK-LON-GLAB")
        self.assertIn("EM-11", res)
        self.assertIn("points", res)

    def test_inspect_message_trace(self):
        res = inspect_message_trace(
            self.udmi_root,
            site_name="sites/UK-LON-GLAB",
            device_id="EM-11",
            test_name="pointset_publish"
        )
        self.assertIn("Message Traces", res)
        self.assertIn("pointset_publish", res)

    def test_extract_test_timebounds(self):
        sample_log = os.path.join(
            self.udmi_root,
            "sites/UK-LON-GLAB/udmi/out/devices/EM-11/tests/pointset_publish/sequence.log"
        )
        if os.path.exists(sample_log):
            start_dt, end_dt = extract_test_timebounds(sample_log, "pointset_publish")
            self.assertIsNotNone(start_dt)
            self.assertIsNotNone(end_dt)

    @patch("mantis.tools.cloud_logs.pull_gcloud_logs", return_value=["2026-05-27T05:53:09Z [UDMIS] Processing message"])
    def test_pull_cloud_logs_for_test(self, mock_pull):
        res = pull_cloud_logs_for_test(
            udmi_root=self.udmi_root,
            test_id="pointset_publish",
            site_name="sites/UK-LON-GLAB",
            device_id="EM-11",
            project="test-project"
        )
        self.assertIn("GCP Cloud Logs", res)
        self.assertIn("Processing message", res)

    def test_parse_sequence_events(self):
        sample_raw = """
2024-12-05T12:45:08Z NOTICE starting test pointset_publish
2024-12-05T12:45:17Z DEBUG update config_update, adding configTransaction RC:9a6ddf.00000134
2024-12-05T12:45:18Z TRACE update state_update, has configTransaction RC:9a6ddf.00000134
2024-12-05T12:45:19Z NOTICE RESULT PASS
"""
        events = parse_sequence_events(sample_raw)
        self.assertEqual(len(events), 4)
        self.assertEqual(events[0]["type"], "TEST_START")
        self.assertEqual(events[1]["type"], "CONFIG_DISPATCH")
        self.assertEqual(events[2]["type"], "TRANSACTION_ACK")
        self.assertEqual(events[3]["type"], "TEST_RESULT")
        self.assertEqual(events[3]["status"], "PASS")

    def test_compare_test_sequences(self):
        res = compare_test_sequences(
            udmi_root=self.udmi_root,
            test_id="pointset_publish",
            device_id="EM-11",
            site_name="sites/UK-LON-GLAB"
        )
        self.assertIn("Differential Log Analysis", res)
        self.assertIn("EM-11", res)

    def test_get_historical_site_state(self):
        res = get_historical_site_state(
            udmi_root=self.udmi_root,
            site_name="sites/UK-LON-GLAB",
            timestamp="2024-12-05T12:45:08Z",
            device_id="EM-11"
        )
        self.assertIn("Historical Site Model State", res)
        self.assertIn("UK-LON-GLAB", res)

    def test_build_udmi_system_prompt(self):
        prompt = build_udmi_system_prompt(
            workspace_root=self.udmi_root,
            active_site_model="sites/UK-LON-GLAB",
            active_device="EM-11",
            active_test="pointset_publish"
        )
        self.assertIn("Pubber", prompt)
        self.assertIn("Sequencer", prompt)
        self.assertIn("UK-LON-GLAB", prompt)
        self.assertIn("compare_test_sequences", prompt)



if __name__ == "__main__":
    unittest.main()
