import os
import re
import tempfile
import unittest
from datetime import datetime, timezone, timedelta
from engine.context import (
    parse_timestamp,
    slice_log_by_timebounds,
    LogCondensationRule,
    condense_log_verbosity,
    merge_and_sort_logs,
)


class TestParseTimestamp(unittest.TestCase):
    def test_empty_or_none(self):
        self.assertIsNone(parse_timestamp(""))
        self.assertIsNone(parse_timestamp(None))

    def test_iso_formats(self):
        ts1 = parse_timestamp("2026-06-26T11:00:00Z")
        self.assertIsNotNone(ts1)
        self.assertEqual(ts1.year, 2026)
        self.assertEqual(ts1.month, 6)
        self.assertEqual(ts1.day, 26)
        self.assertEqual(ts1.hour, 11)

        ts2 = parse_timestamp("[2026-06-26 11:00:00.123456]")
        self.assertIsNotNone(ts2)
        self.assertEqual(ts2.microsecond, 123456)

    def test_time_only_formats(self):
        ts = parse_timestamp("11:30:45")
        self.assertIsNotNone(ts)
        self.assertEqual(ts.hour, 11)
        self.assertEqual(ts.minute, 30)
        self.assertEqual(ts.second, 45)
        today = datetime.now(timezone.utc).date()
        self.assertEqual(ts.date(), today)

    def test_invalid_format(self):
        self.assertIsNone(parse_timestamp("not-a-timestamp"))


class TestSliceLogByTimebounds(unittest.TestCase):
    def setUp(self):
        self.temp_file = tempfile.NamedTemporaryFile(mode="w+", delete=False, encoding="utf-8")
        lines = [
            "2026-06-26T10:00:00Z Early event" + chr(10),
            "2026-06-26T11:00:00Z Target event 1" + chr(10),
            "2026-06-26T11:30:00Z Target event 2" + chr(10),
            "2026-06-26T13:00:00Z Late event" + chr(10),
        ]
        self.temp_file.writelines(lines)
        self.temp_file.close()

    def tearDown(self):
        if os.path.exists(self.temp_file.name):
            os.remove(self.temp_file.name)

    def test_nonexistent_file_or_invalid_bounds(self):
        start = datetime(2026, 6, 26, 11, 0, 0)
        end = datetime(2026, 6, 26, 12, 0, 0)
        self.assertEqual(slice_log_by_timebounds("nonexistent.log", start, end), [])
        self.assertEqual(slice_log_by_timebounds(self.temp_file.name, None, end), [])
        self.assertEqual(slice_log_by_timebounds(self.temp_file.name, start, None), [])

    def test_slicing_with_timezone_aware_datetime(self):
        start = datetime(2026, 6, 26, 11, 10, 0, tzinfo=timezone.utc)
        end = datetime(2026, 6, 26, 11, 20, 0, tzinfo=timezone.utc)
        result = slice_log_by_timebounds(self.temp_file.name, start, end, padding_seconds=1200)
        self.assertEqual(len(result), 2)
        self.assertIn("Target event 1", result[0])

    def test_slicing_with_padding(self):
        start = datetime(2026, 6, 26, 11, 10, 0)
        end = datetime(2026, 6, 26, 11, 20, 0)
        result = slice_log_by_timebounds(self.temp_file.name, start, end, padding_seconds=1200)
        self.assertEqual(len(result), 2)
        self.assertIn("Target event 1", result[0])
        self.assertIn("Target event 2", result[1])


class TestCondenseLogVerbosity(unittest.TestCase):
    def test_empty_or_no_rules(self):
        self.assertEqual(condense_log_verbosity([]), [])
        logs = ["line1", "line2"]
        self.assertEqual(condense_log_verbosity(logs), logs)

    def test_condensation_rule(self):
        pattern = re.compile(r"^(?:[\d\-T:Z]+\s+)?(defer operation (\w+))", re.IGNORECASE)
        rule = LogCondensationRule(pattern, "Repeated {count} defer operations for {val}")
        logs = [
            "2026-06-26T11:00:00Z defer operation opA",
            "2026-06-26T11:00:01Z defer operation opA",
            "2026-06-26T11:00:02Z defer operation opA",
            "2026-06-26T11:00:03Z writeback normal status",
        ]
        result = condense_log_verbosity(logs, rules=[rule])
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0], "Repeated 3 defer operations for opA")
        self.assertEqual(result[1], "2026-06-26T11:00:03Z writeback normal status")

    def test_generic_custom_rule(self):
        pattern = re.compile(r"^(?:[\d\-T:Z]+\s+)?(custom alert (\w+))", re.IGNORECASE)
        rule = LogCondensationRule(pattern, "Condensed {count} alerts for {val}")
        logs = [
            "2026-06-26T11:00:00Z custom alert device1",
            "2026-06-26T11:00:01Z custom alert device1",
        ]
        result = condense_log_verbosity(logs, rules=[rule])
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0], "Condensed 2 alerts for device1")


class TestMergeAndSortLogs(unittest.TestCase):
    def test_empty_and_single_source(self):
        self.assertEqual(merge_and_sort_logs([]), [])
        src = [("SYS", ["2026-06-26T11:00:00Z line1" + chr(10)])]
        self.assertEqual(merge_and_sort_logs(src), ["[SYS] 2026-06-26T11:00:00Z line1"])

    def test_multi_source_sorting(self):
        sources = [
            ("SYS", ["2026-06-26T11:10:00Z system log", "invalid timestamp line"]),
            ("APP", ["2026-06-26T11:05:00Z app log"]),
        ]
        result = merge_and_sort_logs(sources)
        self.assertEqual(len(result), 3)
        self.assertTrue(result[0].startswith("[SYS] invalid timestamp line"))
        self.assertTrue(result[1].startswith("[APP] 2026-06-26T11:05:00Z app log"))
        self.assertTrue(result[2].startswith("[SYS] 2026-06-26T11:10:00Z system log"))


if __name__ == "__main__":
    unittest.main()
