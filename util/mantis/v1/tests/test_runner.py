import asyncio
import json
import os
import tempfile
import unittest
from unittest.mock import patch, MagicMock, AsyncMock
from datetime import datetime

from app.runner import (
    truncate_log_lines,
    summarize_log_chunks,
    UDMITriageRunner
)


class TestRunnerHelpers(unittest.TestCase):
    """Unit tests for top-level helper functions in app/runner.py."""

    def test_truncate_log_lines_no_op(self):
        """Test log lines under max_lines are returned unchanged."""
        lines = [f"line {i}" for i in range(10)]
        truncated = truncate_log_lines(lines, max_lines=20)
        self.assertEqual(truncated, lines)

    def test_truncate_log_lines_truncation(self):
        """Test log lines exceeding max_lines are truncated with intermediate message."""
        lines = [f"line {i}" for i in range(100)]
        truncated = truncate_log_lines(lines, max_lines=10)
        # max_lines=10 -> head_count = int(10*0.3) = 3, tail_count = 7, omitted = 90
        self.assertEqual(len(truncated), 11)  # 3 head + 1 message + 7 tail
        self.assertEqual(truncated[0], "line 0")
        self.assertEqual(truncated[2], "line 2")
        self.assertIn("[... Truncated 90 intermediate log lines", truncated[3])
        self.assertEqual(truncated[-1], "line 99")

    def test_summarize_log_chunks_small_dataset(self):
        """Test summarize_log_chunks with small dataset returns formatted raw text directly."""
        logs = ["line 1", "line 2"]
        result = asyncio.run(summarize_log_chunks(logs, test_id="test_small", chunk_size=10))
        self.assertEqual(result, "```text\nline 1\nline 2\n```")

    @patch("engine.harness.credentials.EnvCredentialsProvider")
    def test_summarize_log_chunks_large_dataset(self, mock_cred_provider):
        """Test summarize_log_chunks with large dataset invokes Map-Reduce parallel summarization."""
        mock_client = MagicMock()
        mock_res = MagicMock()
        mock_res.text = "Chunk summary text"
        mock_client.aio.models.generate_content = AsyncMock(return_value=mock_res)
        mock_cred_provider.return_value.get_client.return_value = mock_client

        logs = [f"log line {i}" for i in range(25)]
        result = asyncio.run(summarize_log_chunks(logs, test_id="test_large", chunk_size=10, max_concurrency=2))

        self.assertIn("Synthesized Chronological Timeline", result)
        self.assertIn("Pristine Raw Failure Sequence", result)
        self.assertIn("Chunk summary text", result)
        self.assertEqual(mock_client.aio.models.generate_content.call_count, 2)


class TestUDMITriageRunner(unittest.TestCase):
    """Unit tests for UDMITriageRunner class in app/runner.py."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.udmi_root = os.path.join(self.temp_dir.name, "udmi")
        self.mantis_dir = os.path.join(self.udmi_root, "util", "mantis")
        os.makedirs(self.mantis_dir)
        self.runner = UDMITriageRunner(self.udmi_root, self.mantis_dir)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_extract_timebounds_from_log_nonexistent(self):
        """Test extract_timebounds_from_log returns None, None for nonexistent file."""
        start, end = UDMITriageRunner.extract_timebounds_from_log("/tmp/nonexistent.log")
        self.assertIsNone(start)
        self.assertIsNone(end)

    def test_extract_timebounds_from_log_with_test_id(self):
        """Test extracting start and end timestamps for a specific test_id."""
        log_file = os.path.join(self.temp_dir.name, "seq.log")
        with open(log_file, "w") as f:
            f.write("2026-06-26T10:00:00Z NOTICE  Starting test test_alpha\n")
            f.write("2026-06-26T10:00:05Z INFO    Doing work\n")
            f.write("2026-06-26T10:00:10Z NOTICE  Ending test test_alpha\n")

        start, end = UDMITriageRunner.extract_timebounds_from_log(log_file, test_id="test_alpha")
        self.assertIsNotNone(start)
        self.assertIsNotNone(end)
        self.assertEqual(start.strftime("%H:%M:%S"), "10:00:00")
        self.assertEqual(end.strftime("%H:%M:%S"), "10:00:10")

    def test_read_filtered_sequence_log(self):
        """Test slicing sequence console log directly to target test bounds."""
        log_file = os.path.join(self.temp_dir.name, "seq_filter.log")
        with open(log_file, "w") as f:
            f.write("NOTICE  Starting test test_1\nline 1\nEnding test test_1\n")
            f.write("NOTICE  Starting test test_2\nline 2\nEnding test test_2\n")

        filtered = UDMITriageRunner.read_filtered_sequence_log(log_file, test_id="test_2")
        self.assertIn("Starting test test_2", filtered)
        self.assertIn("line 2", filtered)
        self.assertNotIn("line 1", filtered)

    def test_auto_detect_metadata(self):
        """Test auto_detect_metadata parses target and site directory from path."""
        target, site = self.runner.auto_detect_metadata("/tmp/runs/run_20260101_120000")
        self.assertTrue(target.startswith("//"))
        self.assertTrue(site.startswith("sites/"))

    def test_find_successful_run_for_test(self):
        """Test finding sibling run directory where a test passed."""
        parent_dir = os.path.join(self.temp_dir.name, "runs")
        run_1 = os.path.join(parent_dir, "run_001")
        run_2 = os.path.join(parent_dir, "run_002")
        os.makedirs(run_1)
        os.makedirs(run_2)

        with open(os.path.join(run_1, "sequencer.out"), "w") as f:
            f.write("2026-01-01T00:00:00Z fail point system test_target 10s Error\n")

        with open(os.path.join(run_2, "sequencer.out"), "w") as f:
            f.write("2026-01-01T00:00:00Z pass point system test_target 10s RESULT pass\n")

        succ_run = self.runner.find_successful_run_for_test(parent_dir, "test_target")
        self.assertEqual(succ_run, run_2)

    def test_scan_failures_from_metrics(self):
        """Test scanning failures from metric results file."""
        metrics_file = os.path.join(self.temp_dir.name, "metrics_target1_2026.json")
        with open(metrics_file, "w", encoding="utf-8") as f:
            json.dump({
                "key1": {
                    "test_name": "test_fail",
                    "category": "point",
                    "test_suite": "sys",
                    "fail_count": 1
                }
            }, f)
        failures = self.runner.scan_failures_from_metrics("target1", self.temp_dir.name)
        self.assertEqual(len(failures), 1)
        self.assertEqual(failures[0]['test_name'], "test_fail")

    def test_triage_single_failure(self):
        """Test triaging single failure invoking run_triage_analysis_async."""
        sem = asyncio.Semaphore(1)
        failure_item = {"test_name": "test1", "category": "point"}
        with patch("app.runner.run_triage_analysis_async", AsyncMock(return_value="Triage analysis report")):
            res = asyncio.run(self.runner.triage_single_failure(
                idx=0,
                total_count=1,
                run_dir=self.temp_dir.name,
                f=failure_item,
                test_meta=None,
                semaphore=sem,
                target="//mqtt/localhost",
                site_id="sites/udmi_site_model",
                clean_target="mqtt_localhost"
            ))
        self.assertEqual(res.get("test_id"), "test1")
        self.assertEqual(res.get("breakpoint"), "Triage complete. Review details.")


if __name__ == "__main__":
    unittest.main()
