import os
import re
import sys
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from typing import List
from typing import Optional


def parse_timestamp(ts_str: str) -> Optional[datetime]:
    """
    Parses an ISO or common timestamp string into a datetime object.
    Supports various formats with or without milliseconds/timezones.
    """
    if not ts_str:
        return None
    ts_str = ts_str.strip("[] ")
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
                t = datetime.strptime(ts_str, fmt).time()
                return datetime.combine(today, t)
            return datetime.strptime(ts_str, fmt)
        except ValueError:
            continue
    return None


def slice_log_by_timebounds(filepath: str, start_dt: datetime, end_dt: datetime,
    padding_seconds: int = 60) -> List[str]:
    """
    Slices log entries matching the temporal timebounds plus/minus padding.
    Assumes log entries start with a parsable timestamp.
    """
    sliced_entries = []
    if not os.path.exists(filepath) or not start_dt or not end_dt:
        return sliced_entries

    padded_start = start_dt - timedelta(seconds=padding_seconds)
    padded_end = end_dt + timedelta(seconds=padding_seconds)
    ts_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+')

    # Pre-calculate valid hour substrings to optimize timestamp parsing (e.g. "17:")
    valid_hours = []
    curr = padded_start.replace(minute=0, second=0, microsecond=0)
    while curr <= padded_end:
        valid_hours.append(f"{curr.hour:02d}:")
        curr += timedelta(hours=1)

    try:
        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            for line in f:
                m = ts_pattern.match(line)
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
    merged_entries = []
    ts_pattern = re.compile(r'^([\d\-T:Z\.,\s]+)\s+')

    for tag, lines in log_sources:
        for line in lines:
            line = line.strip()
            if not line:
                continue
            m = ts_pattern.match(line)
            ts_dt = None
            if m:
                ts_dt = parse_timestamp(m.group(1))

            if not ts_dt:
                ts_dt = datetime.min

            merged_entries.append((ts_dt, tag, line))

    # Sort by parsed datetime chronologically
    merged_entries.sort(key=lambda x: x[0])

    formatted_output = []
    for ts_dt, tag, line in merged_entries:
        formatted_output.append(f"[{tag}] {line}")
    return condense_log_verbosity(formatted_output, rules=condensation_rules)
