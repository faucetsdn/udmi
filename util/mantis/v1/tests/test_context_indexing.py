import unittest
import os
import tempfile
from datetime import datetime, timedelta, timezone
from engine.context import (
    RE_TIMESTAMP_PREFIX,
    RE_MERGE_TIMESTAMP,
    SparseLogIndex,
    slice_log_by_timebounds,
    parse_timestamp
)

class TestContextIndexing(unittest.TestCase):

    def test_regex_constants(self):
        self.assertIsNotNone(RE_TIMESTAMP_PREFIX.match("2026-06-26T10:00:00Z INFO Hello"))
        self.assertIsNotNone(RE_MERGE_TIMESTAMP.match("2026-06-26 10:00:00.123 NOTICE Test"))

    def test_sparse_log_index_and_slicing(self):
        with tempfile.NamedTemporaryFile("w+", delete=False) as f:
            filepath = f.name
            start_base = datetime(2026, 6, 26, 10, 0, 0, tzinfo=timezone.utc)
            # Write 2000 lines spanning 2000 seconds to exceed 64KB index threshold
            for i in range(2000):
                dt = start_base + timedelta(seconds=i)
                ts_str = dt.strftime("%Y-%m-%dT%H:%M:%SZ")
                f.write(f"{ts_str} INFO Sample log line number {i:04d} with extra padded data to make file large enough for indexing test\n")

        try:
            index = SparseLogIndex(filepath, sample_interval_bytes=2048)
            self.assertGreater(len(index.timestamps), 0)
            self.assertGreater(len(index.offsets), 0)

            # Query binary search range
            query_start = start_base + timedelta(seconds=500)
            query_end = start_base + timedelta(seconds=600)
            
            start_off, end_off = index.get_byte_range(query_start, query_end)
            self.assertGreaterEqual(start_off, 0)

            sliced = slice_log_by_timebounds(filepath, query_start, query_end, padding_seconds=10, use_index=True)
            self.assertGreater(len(sliced), 0)
            
            # Verify timestamps in sliced entries fall within expected window
            first_ts = parse_timestamp(sliced[0].split()[0])
            self.assertGreaterEqual(first_ts, query_start - timedelta(seconds=10))
        finally:
            if os.path.exists(filepath):
                os.remove(filepath)

if __name__ == '__main__':
    unittest.main()
