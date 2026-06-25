import re
import os
import sys
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from engine.context import parse_timestamp

class BaseLogParser(ABC):
    """Abstract base class for custom log parser plugins."""
    
    @abstractmethod
    def parse_line(self, line: str) -> Optional[Dict[str, Any]]:
        """Parses a single log line into normalized fields: timestamp, severity, tag, message."""
        pass

    @abstractmethod
    def parse_file(self, filepath: str, start_dt: Optional[datetime] = None, end_dt: Optional[datetime] = None) -> List[Dict[str, Any]]:
        """Parses a log file and returns a list of normalized log dicts, sliced within timebounds."""
        pass

class RegexLogParser(BaseLogParser):
    """
    A generic log parser that extracts fields using named capture groups in a regex pattern.
    Configured declaratively in the SRE playbook.
    """

    def __init__(self, pattern: str, timestamp_format: Optional[str] = None):
        self.pattern = re.compile(pattern)
        self.timestamp_format = timestamp_format

    def parse_line(self, line: str) -> Optional[Dict[str, Any]]:
        """Parses a line using the compiled regex map."""
        match = self.pattern.search(line)
        if not match:
            return None

        data = match.groupdict()
        raw_ts = data.get("timestamp")
        ts = None
        if raw_ts:
            if self.timestamp_format:
                try:
                    ts = datetime.strptime(raw_ts.strip("[] "), self.timestamp_format)
                except ValueError:
                    ts = parse_timestamp(raw_ts)
            else:
                ts = parse_timestamp(raw_ts)

        return {
            "timestamp": ts,
            "severity": data.get("severity", "INFO").upper(),
            "tag": data.get("tag", "sys"),
            "message": data.get("message", line.strip())
        }

    def parse_file(self, filepath: str, start_dt: Optional[datetime] = None, end_dt: Optional[datetime] = None) -> List[Dict[str, Any]]:
        """Parses the entire file line by line, filtering by timestamps if timebounds are provided."""
        parsed_entries = []
        if not os.path.exists(filepath):
            return parsed_entries

        try:
            with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
                for line in f:
                    entry = self.parse_line(line)
                    if not entry or not entry.get("timestamp"):
                        continue
                    
                    ts = entry["timestamp"]
                    # Apply optional timebound filtering
                    if start_dt and ts < start_dt:
                        continue
                    if end_dt and ts > end_dt:
                        continue
                    
                    parsed_entries.append(entry)
        except Exception as e:
            print(f"Warning: RegexLogParser failed to parse file {filepath}: {e}", file=sys.stderr)

        return parsed_entries
