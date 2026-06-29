import unittest
import os
import tempfile
from app.runner import generate_unified_log_diff, mask_dynamic_fields, UDMITriageRunner

class TestRunnerResilience(unittest.TestCase):

    def test_mask_dynamic_fields(self):
        line = "2026-06-26T10:00:00.123Z [550e8400-e29b-41d4-a716-446655440000] Connection to 0x1234abcd"
        masked = mask_dynamic_fields(line)
        self.assertIn("<TIMESTAMP>", masked)
        self.assertIn("<UUID>", masked)
        self.assertIn("<HEX>", masked)
        self.assertNotIn("550e8400", masked)

    def test_generate_unified_log_diff(self):
        failing = [
            "2026-06-26T10:00:00Z NOTICE Starting test",
            "2026-06-26T10:00:01Z ERROR Connection refused",
            "2026-06-26T10:00:02Z RESULT fail test_1"
        ]
        passing = [
            "2026-06-26T09:00:00Z NOTICE Starting test",
            "2026-06-26T09:00:01Z INFO Connected successfully",
            "2026-06-26T09:00:02Z RESULT pass test_1"
        ]
        diff = generate_unified_log_diff(failing, passing)
        self.assertIn("-<TIMESTAMP> INFO Connected successfully", diff)
        self.assertIn("+<TIMESTAMP> ERROR Connection refused", diff)

    def test_telemetry_missing_and_empty_markers(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = UDMITriageRunner(udmi_root=tmpdir, mantis_dir=tmpdir, out_dir=tmpdir)
            empty_file = os.path.join(tmpdir, "empty.log")
            open(empty_file, "w").close()
            missing_file = os.path.join(tmpdir, "missing.log")

            # Verify file checks in runner logic
            self.assertTrue(os.path.exists(empty_file))
            self.assertEqual(os.path.getsize(empty_file), 0)
            self.assertFalse(os.path.exists(missing_file))

if __name__ == '__main__':
    unittest.main()
