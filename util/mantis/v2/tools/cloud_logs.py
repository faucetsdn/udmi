"""
GCP Cloud Logging Tool for UDMI Test Execution Windows.
"""

import json
import os
import re
import subprocess
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional, Tuple

from mantis.engine.context import parse_timestamp
from mantis.tools.artifacts import locate_test_artifacts


def extract_test_timebounds(sequence_log_path: str, test_id: str) -> Tuple[Optional[datetime], Optional[datetime]]:
    """Parses sequence.log to extract the start and end timestamps of a test case."""
    if not os.path.exists(sequence_log_path):
        return None, None

    start_dt = None
    end_dt = None

    ts_pattern = re.compile(r'^(\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?|\d{2}:\d{2}:\d{2}(?:\.\d+)?)')

    try:
        with open(sequence_log_path, 'r', encoding='utf-8', errors='replace') as f:
            for line in f:
                m = ts_pattern.match(line)
                if not m:
                    continue

                timestamp_str = m.group(1)

                lower_line = line.lower()
                if f"starting test {test_id.lower()}" in lower_line:
                    start_dt = parse_timestamp(timestamp_str)
                elif f"terminating test {test_id.lower()}" in lower_line or (f"result" in lower_line and start_dt and not end_dt):
                    ts = parse_timestamp(timestamp_str)
                    if ts and ts >= start_dt:
                        end_dt = ts

        if start_dt and not end_dt:
            end_dt = start_dt + timedelta(minutes=5)

    except Exception:
        pass

    return start_dt, end_dt


def pull_gcloud_logs(
    project: str,
    service: str,
    start_dt: datetime,
    end_dt: datetime,
    padding_seconds: int = 60,
    chunk_seconds: int = 180,
    max_chunks: int = 10,
    exclude_tokens: Optional[List[str]] = None
) -> List[str]:
    """
    Queries GCP Cloud Logging using time-sliced batching to fetch comprehensive container
    logs across the entire test execution window without hitting API truncation limits or timing out.
    """
    padded_start = start_dt - timedelta(seconds=padding_seconds)
    padded_end = end_dt + timedelta(seconds=padding_seconds)

    curr_start = padded_start
    all_entries = []
    seen_ids = set()
    chunk_count = 0

    while curr_start < padded_end and chunk_count < max_chunks:
        curr_end = min(curr_start + timedelta(seconds=chunk_seconds), padded_end)
        start_str = curr_start.strftime("%Y-%m-%dT%H:%M:%SZ")
        end_str = curr_end.strftime("%Y-%m-%dT%H:%M:%SZ")

        log_filter = (
            f'resource.type="k8s_container" AND '
            f'(resource.labels.container_name:"{service}" OR resource.labels.pod_name:"{service}") AND '
            f'timestamp >= "{start_str}" AND '
            f'timestamp <= "{end_str}"'
        )

        cmd = [
            "gcloud", "logging", "read",
            log_filter,
            f"--project={project}",
            "--limit=1500",
            "--format=json"
        ]

        try:
            out = subprocess.check_output(cmd, text=True, stderr=subprocess.DEVNULL, timeout=20)
            entries = json.loads(out) if out.strip() else []
            for entry in entries:
                insert_id = entry.get("insertId")
                if insert_id and insert_id in seen_ids:
                    continue
                if insert_id:
                    seen_ids.add(insert_id)
                all_entries.append(entry)
        except Exception:
            pass

        curr_start = curr_end
        chunk_count += 1

    log_lines = []
    for entry in all_entries:
        ts = entry.get("timestamp", "unknown")
        payload = entry.get("textPayload") or json.dumps(entry.get("jsonPayload", {}))
        if exclude_tokens and any(token in payload for token in exclude_tokens):
            continue
        log_lines.append(f"{ts} {payload.strip()}")

    log_lines.sort()
    return log_lines




def pull_cloud_logs_for_test(
    udmi_root: str,
    test_id: str,
    site_name: Optional[str] = None,
    device_id: Optional[str] = None,
    project: Optional[str] = None,
    service: str = "udmis",
    padding_sec: int = 60,
    output_path: Optional[str] = None
) -> str:
    """
    Tool: Queries GCP Cloud Logging for container logs (e.g. udmis, validator)
    corresponding to the exact time window of a specific test run.
    """
    # 1. Resolve GCP Project ID from parameter, site model cloud_iot_config.json, env, or gcloud
    gcp_project = project
    if not gcp_project and site_name:
        for candidate_cfg in [
            os.path.join(udmi_root, site_name, "udmi", "cloud_iot_config.json"),
            os.path.join(udmi_root, site_name, "cloud_iot_config.json"),
            os.path.join(udmi_root, "sites", site_name.replace("sites/", ""), "udmi", "cloud_iot_config.json"),
            os.path.join(udmi_root, "sites", site_name.replace("sites/", ""), "cloud_iot_config.json")
        ]:
            if os.path.exists(candidate_cfg):
                try:
                    with open(candidate_cfg, 'r', encoding='utf-8') as f:
                        cfg = json.load(f)
                        if cfg.get("project_id"):
                            gcp_project = cfg["project_id"]
                            break
                except Exception:
                    pass

    if not gcp_project:
        # Check all site models for cloud_iot_config.json if site not specified
        sites_dir = os.path.join(udmi_root, "sites")
        if os.path.exists(sites_dir):
            for s in os.listdir(sites_dir):
                for candidate_cfg in [
                    os.path.join(sites_dir, s, "udmi", "cloud_iot_config.json"),
                    os.path.join(sites_dir, s, "cloud_iot_config.json")
                ]:
                    if os.path.exists(candidate_cfg):
                        try:
                            with open(candidate_cfg, 'r', encoding='utf-8') as f:
                                cfg = json.load(f)
                                if cfg.get("project_id"):
                                    gcp_project = cfg["project_id"]
                                    break
                        except Exception:
                            pass
                if gcp_project:
                    break

    if not gcp_project:
        gcp_project = os.getenv("GCP_PROJECT") or os.getenv("GCLOUD_PROJECT")

    if not gcp_project:
        try:
            gcp_project = subprocess.check_output(["gcloud", "config", "get-value", "project"], text=True, stderr=subprocess.DEVNULL).strip()
        except Exception:
            pass

    if not gcp_project:
        return "Error: GCP Project ID could not be detected. Please specify project ID or configure cloud_iot_config.json."


    # 2. Locate sequence.log
    art_data = locate_test_artifacts(
        udmi_root=udmi_root,
        site=site_name,
        device=device_id,
        test=test_id
    )
    seq_rel = art_data.get("artifacts", {}).get("sequence_log")
    if not seq_rel:
        return f"Error: Could not locate 'sequence.log' for test='{test_id}', device='{device_id}' to determine timebounds."

    seq_path = os.path.join(udmi_root, seq_rel)
    start_dt, end_dt = extract_test_timebounds(seq_path, test_id)
    if not start_dt:
        return f"Error: Could not extract test execution timestamps from '{seq_rel}' for test='{test_id}'."

    # Avoid futile GCP queries for historical runs older than Cloud Logging retention (30 days)
    now_utc = datetime.now(timezone.utc)
    start_utc = start_dt if start_dt.tzinfo else start_dt.replace(tzinfo=timezone.utc)
    if (now_utc - start_utc).days > 30:
        return (
            f"Note: Test execution timestamp ({start_dt.isoformat()}) is older than 30 days and outside GCP Cloud Logging retention in '{gcp_project}'. "
            f"Rely on local recorded artifacts ('{seq_rel}', 'device_system.log') for diagnosis."
        )

    # 3. Pull cloud logs
    logs = pull_gcloud_logs(
        project=gcp_project,
        service=service,
        start_dt=start_dt,
        end_dt=end_dt,
        padding_seconds=padding_sec
    )


    if not logs:
        return f"No cloud log entries found in GCP project '{gcp_project}' for service='{service}' between {start_dt.isoformat()} and {end_dt.isoformat()}."

    if output_path:
        out_full = os.path.abspath(output_path)
        os.makedirs(os.path.dirname(out_full), exist_ok=True)
        with open(out_full, "w", encoding="utf-8") as f:
            f.write("\n".join(logs) + "\n")
        return f"Successfully retrieved {len(logs)} cloud log entries and saved to `{out_full}`."

    formatted_sample = "\n".join(logs[:150])
    truncated_msg = f"\n... [Truncated {len(logs) - 150} additional log lines] ..." if len(logs) > 150 else ""
    return f"### GCP Cloud Logs (`{service}` in `{gcp_project}` for `{test_id}`)\n```text\n{formatted_sample}{truncated_msg}\n```"
