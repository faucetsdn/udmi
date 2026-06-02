#!/usr/bin/env python3
import argparse
import sys
import os
import re
import subprocess
from datetime import datetime, timedelta, timezone

# Reuse timestamp parser from harness
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src"))
from triage.harness.context import parse_timestamp

def extract_test_timebounds(sequence_log_path: str, test_id: str) -> tuple[datetime, datetime]:
    """Parses sequence.log to extract the start and end timestamps of the given test."""
    if not os.path.exists(sequence_log_path):
        print(f"Error: Sequencer log not found at {sequence_log_path}", file=sys.stderr)
        sys.exit(1)

    start_dt = None
    end_dt = None
    
    # Timestamps usually look like: 2026-05-27T05:53:09Z or 05:53:09.103
    ts_pattern = re.compile(r'^([\d\-T:Z\.,\s]+)\s+')

    with open(sequence_log_path, 'r', encoding='utf-8', errors='replace') as f:
        for line in f:
            m = ts_pattern.match(line)
            if not m:
                continue
            
            timestamp_str = m.group(1)
            
            if f"Starting test {test_id}" in line:
                start_dt = parse_timestamp(timestamp_str)
            elif f"terminating test {test_id}" in line or f"Timeout" in line and start_dt and not end_dt:
                # Fallback to general timeouts/failure terminations if it occurs after start
                ts = parse_timestamp(timestamp_str)
                if ts and ts > start_dt:
                    end_dt = ts

    # If we found start but not end, default end to start + 5 minutes
    if start_dt and not end_dt:
        end_dt = start_dt + timedelta(minutes=5)

    return start_dt, end_dt

def pull_gcloud_logs(project: str, service: str, start_dt: datetime, end_dt: datetime, padding_seconds: int) -> list[str]:
    """Executes gcloud logging read to pull structured logs in the padded time range."""
    padded_start = start_dt - timedelta(seconds=padding_seconds)
    padded_end = end_dt + timedelta(seconds=padding_seconds)

    # Format timestamps for gcloud filter: YYYY-MM-DDTHH:MM:SSZ
    start_str = padded_start.strftime("%Y-%m-%dT%H:%M:%SZ")
    end_str = padded_end.strftime("%Y-%m-%dT%H:%M:%SZ")

    # Construct resilient GKE / Cloud Run hybrid query
    log_filter = (
        f'(resource.type="k8s_container" AND resource.labels.project_id="{project}" AND (labels."k8s-pod/app"="{service}" OR labels."k8s_pod/app"="{service}")) AND '
        f'timestamp >= "{start_str}" AND '
        f'timestamp <= "{end_str}"'
    )

    cmd = [
        "gcloud", "logging", "read",
        log_filter,
        f"--project={project}",
        "--format=json"
    ]

    print(f"Running gcloud command: {' '.join(cmd)}")
    
    try:
        out = subprocess.check_output(cmd, text=True)
        import json
        entries = json.loads(out) if out.strip() else []
        
        # Parse, extract text, and format chronologically
        log_lines = []
        for entry in entries:
            ts = entry.get("timestamp", "unknown")
            payload = entry.get("textPayload") or json.dumps(entry.get("jsonPayload", {}))
            log_lines.append(f"{ts} {payload.strip()}")
            
        # Sort chronologically
        log_lines.sort()
        return log_lines
    except Exception as e:
        print(f"Warning: failed to pull logs from Cloud Logging: {e}", file=sys.stderr)
        return []

def main():
    parser = argparse.ArgumentParser(description="Pull Cloud Logs for a Padded Test Execution Window")
    parser.add_argument("--sequence-log", "-sl", required=True, help="Path to sequence.log")
    parser.add_argument("--test", "-t", required=True, help="Target test ID to filter by")
    parser.add_argument("--project", "-p", required=True, help="GCP Project ID")
    parser.add_argument("--service", "-s", default="udmis", help="GCP Project ID")
    parser.add_argument("--padding", type=int, default=60, help="Padded time window in seconds")
    parser.add_argument("--output", "-o", help="Output log file path (defaults to <test_id>_cloud.log)")

    args = parser.parse_args()

    print(f"Analyzing sequencer log: {args.sequence_log} for test: {args.test}")
    start_dt, end_dt = extract_test_timebounds(args.sequence_log, args.test)

    if not start_dt:
        print(f"Error: Could not locate test execution window for '{args.test}' in sequence log.", file=sys.stderr)
        sys.exit(1)

    print(f"Located Test execution bounds:")
    print(f"  Start: {start_dt.strftime('%Y-%m-%dT%H:%M:%SZ')}")
    print(f"  End:   {end_dt.strftime('%Y-%m-%dT%H:%M:%SZ')}")
    print(f"  Padded Window (+/- {args.padding}s):")
    print(f"    Start: {(start_dt - timedelta(seconds=args.padding)).strftime('%Y-%m-%dT%H:%M:%SZ')}")
    print(f"    End:   {(end_dt + timedelta(seconds=args.padding)).strftime('%Y-%m-%dT%H:%M:%SZ')}")

    log_lines = pull_gcloud_logs(args.project, args.service, start_dt, end_dt, args.padding)

    output_path = args.output or f"{args.test}_cloud.log"
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(log_lines) + "\n")

    print(f"\nSuccessfully pulled {len(log_lines)} log entries to: {output_path}")

if __name__ == "__main__":
    main()
