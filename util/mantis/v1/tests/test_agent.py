import asyncio
import os
import tempfile
import unittest
from unittest.mock import patch, MagicMock, AsyncMock

from app.agent import (
    parse_merged_logs_from_payload,
    build_deterministic_timeline,
    harvest_test_code_context,
    UDMITriagePipeline,
    run_triage_analysis_async,
    run_triage_analysis
)


class TestAgentFunctions(unittest.TestCase):
    """Unit tests for top-level helper functions in app/agent.py."""

    def test_parse_merged_logs_from_payload(self):
        """Test parsing merged logs from prompt markdown payload."""
        self.assertEqual(parse_merged_logs_from_payload("No log section"), [])

        payload = (
            "Header text\n\n"
            "## Chronologically Merged Global Logs (Test Execution Context)\n"
            "```text\n"
            "[LOG] 2026-06-26T10:00:00Z line 1\n"
            "[LOG] 2026-06-26T10:00:01Z line 2\n"
            "```\n"
            "Footer text"
        )
        logs = parse_merged_logs_from_payload(payload)
        self.assertEqual(logs, ["[LOG] 2026-06-26T10:00:00Z line 1", "[LOG] 2026-06-26T10:00:01Z line 2"])

    def test_build_deterministic_timeline(self):
        """Test building deterministic event timeline markdown table from raw logs."""
        raw_logs = [
            "[seq] 2026-06-26T10:00:00.123Z NOTICE Starting test test_alpha",
            "[seq] 2026-06-26T10:00:05.123Z ERROR NullPointerException encountered in handler",
            "[seq] 2026-06-26T10:00:10.123Z NOTICE RESULT fail test_alpha"
        ]
        timeline = build_deterministic_timeline(raw_logs)
        self.assertIn("Detailed Timeline of Events", timeline)
        self.assertIn("| Timestamp (UTC) | Source | Log Message / Event | Significance |", timeline)
        self.assertIn("10:00:00", timeline)
        self.assertIn("Test case execution initiated.", timeline)
        self.assertIn("CRITICAL: Unhandled NullPointerException encountered.", timeline)
        self.assertIn("Test failed (FAIL).", timeline)

    def test_build_deterministic_timeline_no_matches(self):
        """Test fallback row in timeline when no significant logs match patterns."""
        raw_logs = ["[seq] 2026-06-26T10:00:00Z INFO Just a regular message"]
        timeline = build_deterministic_timeline(raw_logs)
        self.assertIn("[No significant event matches found in log slice]", timeline)

    def test_harvest_test_code_context(self):
        """Test harvesting test definition code and golden baseline references."""
        self.assertEqual(harvest_test_code_context("/tmp", ""), "")

        temp_dir = tempfile.TemporaryDirectory()
        ws_root = temp_dir.name

        # Create mock java sequence file
        seq_dir = os.path.join(ws_root, "validator/src/main/java/com/google/daq/mqtt/sequencer/sequences")
        os.makedirs(seq_dir)
        java_file = os.path.join(seq_dir, "SystemSequences.java")
        with open(java_file, "w") as f:
            f.write("// Comment\npublic void test_target() {\n  System.out.println(\"hello\");\n}\n")

        # Create mock etc baseline files
        etc_dir = os.path.join(ws_root, "etc")
        os.makedirs(etc_dir)
        with open(os.path.join(etc_dir, "sequencer.out"), "w") as f:
            f.write("2026-01-01T00:00:00Z pass point system test_target 5s\n")

        code_context = harvest_test_code_context(ws_root, "test_target", is_physical_device=False)
        self.assertIn("Deterministic Codebase Context", code_context)
        self.assertIn("public void test_target()", code_context)
        self.assertIn("Golden Baseline Reference Outcomes", code_context)
        self.assertIn("test_target", code_context)

        temp_dir.cleanup()


class TestUDMITriagePipelineAndAsync(unittest.TestCase):
    """Unit tests for UDMITriagePipeline class and async entrypoints."""

    def test_pipeline_run_intent_deterministically(self):
        """Test UDMITriagePipeline run_intent_deterministically hook."""
        mock_client = MagicMock()
        pipeline = UDMITriagePipeline(client=mock_client)
        with patch("app.agent.get_workspace_root", return_value=""):
            res = pipeline.run_intent_deterministically("test_1", "Global_Pubber_Log: true")
            self.assertIsNone(res)

    @patch("app.agent.run_triage_session_async", new_callable=AsyncMock)
    def test_run_triage_analysis_async(self, mock_session):
        """Test run_triage_analysis_async parses metadata and invokes session orchestrator."""
        mock_session.return_value = "# Diagnostic Report Summary"

        payload = (
            "- **Project ID**: proj_1\n"
            "- **Site ID**: site_1\n"
            "- **Device ID**: dev_1\n"
            "- **Test ID**: test_1\n"
            "## Local Sequencer log.log (Raw Console)\n```text\nError log trace\n```\n"
        )

        res = asyncio.run(run_triage_analysis_async(payload))
        self.assertEqual(res, "# Diagnostic Report Summary")
        self.assertTrue(mock_session.called)
        call_kwargs = mock_session.call_args.kwargs
        self.assertEqual(call_kwargs["target_id"], "test_1")
        self.assertIn("Test ID: test_1", call_kwargs["cache_query"])

    @patch("app.agent.run_triage_analysis_async", return_value="Sync Report")
    def test_run_triage_analysis_sync_wrapper(self, mock_async):
        """Test backward-compatible synchronous wrapper run_triage_analysis."""
        res = run_triage_analysis("payload")
        self.assertEqual(res, "Sync Report")


if __name__ == "__main__":
    unittest.main()
