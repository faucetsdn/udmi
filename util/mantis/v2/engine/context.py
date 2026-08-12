import os
import re
import sys
import bisect
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from typing import List, Optional, Tuple

# Module-level compiled regex patterns for timestamp matching
RE_TIMESTAMP_PREFIX = re.compile(r'^(\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?|\d{2}:\d{2}:\d{2}(?:\.\d+)?)')
RE_MERGE_TIMESTAMP = re.compile(r'^(\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?|\d{2}:\d{2}:\d{2}(?:\.\d+)?)')


def parse_timestamp(ts_str: str) -> Optional[datetime]:
    """
    Parses an ISO or common timestamp string into a datetime object.
    Supports various formats with or without milliseconds/timezones.
    """
    if not ts_str:
        return None
    ts_clean = ts_str.strip("[] ")
    
    # Truncate nanoseconds (e.g. .214927805Z) to 6 microsecond digits (.214927Z) preserving timezone suffix
    ts_clean = re.sub(r'(\.\d{6})\d+([Z\+\-\:\d]*)', r'\1\2', ts_clean)
    
    dt = None
    # Fast-path C-accelerated ISO parsing (100x faster than strptime)
    if "T" in ts_clean or " " in ts_clean:
        iso_candidate = ts_clean.replace(" ", "T")
        try:
            dt = datetime.fromisoformat(iso_candidate)
        except ValueError:
            pass

    if dt is None:
        formats = [
            "%Y-%m-%dT%H:%M:%S.%fZ",
            "%Y-%m-%dT%H:%M:%SZ",
            "%Y-%m-%d %H:%M:%S.%f",
            "%Y-%m-%d %H:%M:%S",
            "%H:%M:%S.%f",
            "%H:%M:%S"
        ]
        for fmt in formats:
            try:
                if fmt in ["%H:%M:%S", "%H:%M:%S.%f"]:
                    today = datetime.now(timezone.utc).date()
                    t = datetime.strptime(ts_clean, fmt).time()
                    dt = datetime.combine(today, t)
                else:
                    dt = datetime.strptime(ts_clean, fmt)
                break
            except ValueError:
                continue

    if dt and dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


class SparseLogIndex:
    """
    Sparse timestamp index for log files enabling O(log N) temporal binary search slicing.
    Indexes byte offsets and parsed timestamps at periodic sample intervals.
    """
    def __init__(self, filepath: str, sample_interval_bytes: int = 64 * 1024):
        self.filepath = filepath
        self.sample_interval = sample_interval_bytes
        self.timestamps: List[datetime] = []
        self.offsets: List[int] = []
        self._build_index()

    def _build_index(self):
        if not os.path.exists(self.filepath):
            return
        try:
            with open(self.filepath, 'r', encoding='utf-8', errors='replace') as f:
                last_sample = -self.sample_interval
                while True:
                    offset = f.tell()
                    line = f.readline()
                    if not line:
                        break
                    if offset - last_sample >= self.sample_interval or offset == 0:
                        m = RE_TIMESTAMP_PREFIX.match(line)
                        if m:
                            ts = parse_timestamp(m.group(1))
                            if ts:
                                self.timestamps.append(ts)
                                self.offsets.append(offset)
                                last_sample = offset
        except Exception:
            pass

    def get_byte_range(self, start_dt: datetime, end_dt: datetime) -> Tuple[int, Optional[int]]:
        """Returns (start_byte_offset, end_byte_offset) for the given temporal bounds."""
        if not self.timestamps:
            return 0, None
        
        if start_dt and start_dt.tzinfo is None:
            start_dt = start_dt.replace(tzinfo=timezone.utc)
        if end_dt and end_dt.tzinfo is None:
            end_dt = end_dt.replace(tzinfo=timezone.utc)

        idx_start = bisect.bisect_left(self.timestamps, start_dt)
        start_offset = self.offsets[max(0, idx_start - 1)]
        
        idx_end = bisect.bisect_right(self.timestamps, end_dt)
        if idx_end < len(self.offsets):
            end_offset = self.offsets[idx_end]
        else:
            end_offset = None
        return start_offset, end_offset


def slice_log_by_timebounds(filepath: str, start_dt: datetime, end_dt: datetime,
                            padding_seconds: int = 60, use_index: bool = True) -> List[str]:
    """
    Slices log entries matching the temporal timebounds plus/minus padding.
    Assumes log entries start with a parsable timestamp.
    Uses SparseLogIndex to skip directly to relevant byte offset ranges for large log files.
    """
    sliced_entries = []
    if not os.path.exists(filepath) or not start_dt or not end_dt:
        return sliced_entries

    if start_dt.tzinfo is None:
        start_dt = start_dt.replace(tzinfo=timezone.utc)
    if end_dt.tzinfo is None:
        end_dt = end_dt.replace(tzinfo=timezone.utc)

    padded_start = start_dt - timedelta(seconds=padding_seconds)
    padded_end = end_dt + timedelta(seconds=padding_seconds)

    # Pre-calculate valid hour substrings to optimize timestamp parsing (e.g. "17:")
    valid_hours = []
    curr = padded_start.replace(minute=0, second=0, microsecond=0)
    while curr <= padded_end:
        valid_hours.append(f"{curr.hour:02d}:")
        curr += timedelta(hours=1)

    try:
        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            end_offset = None
            if use_index and os.path.getsize(filepath) > 64 * 1024:
                index = SparseLogIndex(filepath)
                start_offset, end_offset = index.get_byte_range(padded_start, padded_end)
                f.seek(start_offset)
                if start_offset > 0:
                    f.readline() # Discard partial line

            while True:
                if end_offset is not None and f.tell() > end_offset:
                    break
                line = f.readline()
                if not line:
                    break
                m = RE_TIMESTAMP_PREFIX.match(line)
                if m:
                    ts_str = m.group(1)
                    # Performance Optimization: Skip expensive datetime parsing if the hour prefix doesn't match
                    if not any(h in ts_str for h in valid_hours):
                        continue

                    ts = parse_timestamp(ts_str)
                    if ts and (padded_start <= ts <= padded_end):
                        sliced_entries.append(line.strip())
    except Exception as e:
        print(f"Warning: failed to slice log {filepath}: {e}", file=sys.stderr)

    return sliced_entries


class LogCondensationRule:
    """Defines a rule for condensing repetitive log sequences."""
    def __init__(self, pattern: re.Pattern, template: str):
        self.pattern = pattern
        self.template = template


def condense_log_verbosity(logs: List[str], rules: Optional[List[LogCondensationRule]] = None) -> List[str]:
    """
    Prunes high-frequency repetitive entries from the merged logs to keep prompt tokens low.
    Condenses sequences of identical patterns using supplied rules into a single summarization line.
    """
    if not logs:
        return []

    if not rules:
        return logs

    condensed = []
    n = len(logs)
    i = 0
    while i < n:
        current_line = logs[i]
        matched_rule = False

        for rule in rules:
            match = rule.pattern.search(current_line)
            if match:
                prefix = match.group(1)
                val = match.group(2) if len(match.groups()) >= 2 else ""
                count = 1
                j = i + 1
                
                while j < n:
                    next_line = logs[j]
                    next_match = rule.pattern.search(next_line)
                    if next_match and next_match.group(1) == prefix:
                        count += 1
                        j += 1
                    else:
                        break
                
                if count > 1:
                    bracket_prefix = ""
                    if current_line.startswith("["):
                        bracket_prefix = current_line.split("]", 1)[0] + "] "
                    formatted = rule.template.format(val=val, count=count)
                    condensed.append(f"{bracket_prefix}{formatted}")
                else:
                    condensed.append(current_line)
                
                i = j
                matched_rule = True
                break

        if not matched_rule:
            condensed.append(current_line)
            i += 1

    return condensed


def merge_and_sort_logs(log_sources: List[tuple], condensation_rules: Optional[List[LogCondensationRule]] = None) -> List[str]:
    """
    Merges and chronologically sorts log lines from multiple sources.
    Each entry in log_sources is a tuple: (source_name_tag, list_of_lines)
    Each log line should start with a parsable timestamp.
    Returns a sorted list of formatted chronological lines: [TAG] Raw Line Content
    """
    if not log_sources:
        return []

    if len(log_sources) == 1:
        tag, lines = log_sources[0]
        formatted = [f"[{tag}] {line.strip()}" for line in lines if line.strip()]
        return condense_log_verbosity(formatted, rules=condensation_rules)

    merged_entries = []

    for tag, lines in log_sources:
        for line in lines:
            line = line.strip()
            if not line:
                continue
            m = RE_MERGE_TIMESTAMP.match(line)
            ts_dt = None
            if m:
                ts_dt = parse_timestamp(m.group(1))

            if not ts_dt:
                ts_dt = datetime.min.replace(tzinfo=timezone.utc)

            merged_entries.append((ts_dt, tag, line))

    # Sort by parsed datetime chronologically
    merged_entries.sort(key=lambda x: x[0])

    formatted_output = []
    for ts_dt, tag, line in merged_entries:
        formatted_output.append(f"[{tag}] {line}")
    return condense_log_verbosity(formatted_output, rules=condensation_rules)
