import os
import tempfile
import unittest
from unittest.mock import patch, MagicMock

from app.resolver import UDMILogResolver, UDMIResultParser
from engine.models import TriageFailure


class TestUDMILogResolver(unittest.TestCase):
    """Unit tests for UDMILogResolver class in app/resolver.py."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.udmi_root = self.temp_dir.name
        self.resolver = UDMILogResolver(self.udmi_root)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_discover_active_site_name_default(self):
        """Test default site name returned when sites/ directory does not exist."""
        site_name = self.resolver.discover_active_site_name()
        self.assertEqual(site_name, "udmi_site_model")

    def test_discover_active_site_name_custom(self):
        """Test active site name discovery when custom site folder exists."""
        sites_dir = os.path.join(self.udmi_root, "sites")
        os.makedirs(os.path.join(sites_dir, "udmi_site_model"))
        os.makedirs(os.path.join(sites_dir, "udmi_site_model_1"))
        os.makedirs(os.path.join(sites_dir, "ZZ-TRI-FECTA_3"))

        site_name = self.resolver.discover_active_site_name()
        self.assertEqual(site_name, "ZZ-TRI-FECTA")

    def test_discover_active_site_name_exception_handling(self):
        """Test discover_active_site_name gracefully handles filesystem errors."""
        with patch("os.path.exists", side_effect=Exception("Disk error")):
            site_name = self.resolver.discover_active_site_name()
            self.assertEqual(site_name, "udmi_site_model")

    def test_discover_device_id_fallback(self):
        """Test default device ID returned when devices directory does not exist."""
        device_id = self.resolver.discover_device_id(self.udmi_root)
        self.assertEqual(device_id, "AHU-1")

    def test_discover_device_id_with_test_id_match(self):
        """Test device ID discovery matching test_id inside devices folder."""
        devices_dir = os.path.join(self.udmi_root, "out", "devices")
        os.makedirs(os.path.join(devices_dir, "DEV-1", "tests", "test_alpha"))
        os.makedirs(os.path.join(devices_dir, "DEV-2", "tests", "test_beta"))

        device_id = self.resolver.discover_device_id(self.udmi_root, test_id="test_beta")
        self.assertEqual(device_id, "DEV-2")

    def test_discover_device_id_first_available(self):
        """Test fallback to first available device folder when test_id is not provided or not found."""
        devices_dir = os.path.join(self.udmi_root, "devices")
        os.makedirs(os.path.join(devices_dir, "DEV-X"))

        device_id = self.resolver.discover_device_id(self.udmi_root)
        self.assertEqual(device_id, "DEV-X")

    def test_resolve_sharded_itemized_logs_single_run(self):
        """Test resolve_sharded_itemized_logs in single-run fallback mode."""
        run_dir = os.path.join(self.udmi_root, "run_1")
        os.makedirs(run_dir)

        site_path = os.path.join(self.udmi_root, "sites", "udmi_site_model", "devices", "AHU-1", "tests", "test_test")
        os.makedirs(site_path)
        seq_log = os.path.join(site_path, "sequence.log")
        with open(seq_log, "w") as f:
            f.write("log data")

        out_dir = os.path.join(run_dir, "out")
        os.makedirs(out_dir)
        pub_log = os.path.join(out_dir, "pubber.log")
        udm_log = os.path.join(out_dir, "udmis.log")
        with open(pub_log, "w") as f:
            f.write("pubber")
        with open(udm_log, "w") as f:
            f.write("udmis")

        res_seq, res_pub, res_udm, res_dev, res_opts = self.resolver.resolve_sharded_itemized_logs(
            run_dir=run_dir,
            test_name="test_test",
            occurrence_idx=0
        )

        self.assertTrue(res_seq.endswith("sequence.log"))
        self.assertTrue(res_pub.endswith("pubber.log"))
        self.assertTrue(res_udm.endswith("udmis.log"))
        self.assertEqual(res_dev, "AHU-1")
        self.assertEqual(res_opts, "")

    def test_resolve_sharded_itemized_logs_sharded(self):
        """Test resolve_sharded_itemized_logs with itemized index and sharded out directories."""
        run_dir = os.path.join(self.udmi_root, "run_1")
        os.makedirs(run_dir)

        itemized_out = os.path.join(run_dir, "test_itemized.out")
        with open(itemized_out, "w") as f:
            f.write("001 pass point system test_target 10s\n")
            f.write("002 fail point system test_target 15s\n")

        etc_dir = os.path.join(self.udmi_root, "etc")
        os.makedirs(etc_dir)
        with open(os.path.join(etc_dir, "test_itemized.in"), "w") as f:
            f.write("WITH DEV-CUSTOM\n")
            f.write("TEST test_target --opt1 --opt2\n")
            f.write("TEST test_target --opt3\n")

        out_2 = os.path.join(run_dir, "out_2")
        os.makedirs(out_2)
        with open(os.path.join(out_2, "sequencer.log-002"), "w") as f:
            f.write("seq")
        with open(os.path.join(out_2, "pubber.log-002"), "w") as f:
            f.write("pub")
        with open(os.path.join(out_2, "udmis.log"), "w") as f:
            f.write("udm")

        res_seq, res_pub, res_udm, res_dev, res_opts = self.resolver.resolve_sharded_itemized_logs(
            run_dir=run_dir,
            test_name="test_target",
            occurrence_idx=1
        )

        self.assertIn("sequencer.log-002", res_seq)
        self.assertIn("pubber.log-002", res_pub)
        self.assertIn("udmis.log", res_udm)
        self.assertEqual(res_dev, "DEV-CUSTOM")
        self.assertEqual(res_opts, "--opt1 --opt2")

    def test_resolve_sharded_sequencer_logs_single_and_sharded(self):
        """Test resolve_sharded_sequencer_logs in both single mode and sharded site mode."""
        run_dir = os.path.join(self.udmi_root, "run_1")
        os.makedirs(run_dir)

        res_seq, res_pub, res_udm = self.resolver.resolve_sharded_sequencer_logs(
            run_dir=run_dir, test_name="test_foo"
        )
        self.assertEqual((res_seq, res_pub, res_udm), ("", "", ""))

        shard_site = os.path.join(run_dir, "sites", "udmi_site_model_100")
        test_dir = os.path.join(shard_site, "devices", "DEV-1", "tests", "test_foo")
        os.makedirs(test_dir)
        seq_file = os.path.join(test_dir, "sequence_1.log")
        with open(seq_file, "w") as f:
            f.write("2026-01-01T00:00:00Z RESULT fail test_foo Reason")

        shard_out = os.path.join(run_dir, "out_100")
        os.makedirs(shard_out)
        with open(os.path.join(shard_out, "pubber.log"), "w") as f:
            f.write("pub log")
        with open(os.path.join(shard_out, "udmis.log"), "w") as f:
            f.write("udm log")

        res_seq, res_pub, res_udm = self.resolver.resolve_sharded_sequencer_logs(
            run_dir=run_dir, test_name="test_foo", expect_fail=True
        )
        self.assertIn("sequence_1.log", res_seq)
        self.assertIn("pubber.log", res_pub)
        self.assertIn("udmis.log", res_udm)


class TestUDMIResultParser(unittest.TestCase):
    """Unit tests for UDMIResultParser class in app/resolver.py."""

    def setUp(self):
        self.parser = UDMIResultParser()
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_normalize_reason(self):
        """Test normalize_reason redacts timestamps and event pipeline error patterns."""
        self.assertEqual(UDMIResultParser.normalize_reason(""), "")
        self.assertEqual(UDMIResultParser.normalize_reason(None), "")

        raw_text = "Failed at 2026-06-26T11:02:20Z with error."
        normalized = UDMIResultParser.normalize_reason(raw_text)
        self.assertNotIn("2026-06-26T11:02:20Z", normalized)
        self.assertIn("TIMESTAMP", normalized)

        pipeline_err = "Pipeline type event error: While processing message Extra detail here"
        normalized_pipe = UDMIResultParser.normalize_reason(pipeline_err)
        self.assertEqual(normalized_pipe, "Pipeline type event error: While processing message REDACTED")

    def test_parse_results_file_nonexistent(self):
        """Test parsing nonexistent file returns empty list."""
        results = self.parser.parse_results_file("/tmp/nonexistent_sequencer.out")
        self.assertEqual(results, [])

    def test_parse_results_file_sequencer(self):
        """Test parsing standard sequencer.out results file."""
        file_path = os.path.join(self.temp_dir.name, "sequencer.out")
        with open(file_path, "w") as f:
            f.write("# Comment line\n")
            f.write("\n")
            f.write("2026-06-26T10:00:00Z pass point system test_pass 5s\n")
            f.write("2026-06-26T10:00:05Z fail schemas system test_schema 5s Schema error\n")
            f.write("2026-06-26T10:00:10Z fail point system test_fail 10s Error 2026-06-26T10:00:10Z detail\n")

        failures = self.parser.parse_results_file(file_path, is_itemized=False)
        self.assertEqual(len(failures), 1)
        self.assertIsInstance(failures[0], TriageFailure)
        self.assertEqual(failures[0].test_name, "system")
        self.assertEqual(failures[0].category, "point")
        self.assertEqual(failures[0].suite, "sequencer")
        self.assertIn("TIMESTAMP", failures[0].metadata["raw_reason"])

    def test_parse_results_file_itemized(self):
        """Test parsing test_itemized.out with numerical index prefix."""
        file_path = os.path.join(self.temp_dir.name, "test_itemized.out")
        with open(file_path, "w") as f:
            f.write("001 2026-06-26T10:00:10Z fail state system test_item_fail 12s Custom failure reason\n")

        failures = self.parser.parse_results_file(file_path, is_itemized=True)
        self.assertEqual(len(failures), 1)
        self.assertEqual(failures[0].test_name, "system")
        self.assertEqual(failures[0].suite, "itemized")


if __name__ == "__main__":
    unittest.main()
