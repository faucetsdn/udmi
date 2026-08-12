import glob
import json
import os
import re
import subprocess
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple



def locate_test_artifacts(
    udmi_root: str,
    site: Optional[str] = None,
    device: Optional[str] = None,
    test: Optional[str] = None,
    run_dir: Optional[str] = None
) -> Dict[str, Any]:
    """
    Autonomously discovers and locates test output files and logs across all UDMI
    filesystem topologies:
    1. sites/<site>/udmi/out/devices/<device>/tests/<test>/
    2. sites/<site>/out/devices/<device>/tests/<test>/
    3. out/devices/<device>/tests/<test>/
    4. root out/ (pubber.log, udmis.log, validator.out, etc.)
    5. Sharded out_* directories and test run backups (tmp/, var/).
    """
    udmi_root = os.path.abspath(udmi_root)
    result = {
        "site": site,
        "device": device,
        "test": test,
        "search_paths": [],
        "artifacts": {
            "sequence_log": None,
            "sequence_md": None,
            "result_log": None,
            "results_md": None,
            "device_system_log": None,
            "events_json": [],
            "state_json": [],
            "config_json": [],
            "pubber_log": None,
            "udmis_log": None,
            "custom_context": [],
            "extra_logs": []
        }
    }

    # Discover candidate base directories
    candidate_dirs = []

    # If run_dir is specified, search there first
    if run_dir and os.path.exists(run_dir):
        candidate_dirs.append(os.path.abspath(run_dir))

    # Candidate site directories under sites/
    sites_dir = os.path.join(udmi_root, "sites")
    if os.path.exists(sites_dir):
        if site:
            # Handle site given as "sites/UK-LON-GLAB" or "UK-LON-GLAB" or "sites/UK-LON-GLAB/udmi"
            clean_site = site.replace("sites/", "").split("/")[0]
            candidate_dirs.append(os.path.join(sites_dir, clean_site))
            candidate_dirs.append(os.path.join(sites_dir, clean_site, "udmi"))
        else:
            for s in os.listdir(sites_dir):
                s_path = os.path.join(sites_dir, s)
                if os.path.isdir(s_path):
                    candidate_dirs.append(s_path)
                    candidate_dirs.append(os.path.join(s_path, "udmi"))

    # Root out directory
    candidate_dirs.append(os.path.join(udmi_root, "out"))
    
    # Sharded out_* directories
    for sharded in glob.glob(os.path.join(udmi_root, "out_*")):
        candidate_dirs.append(sharded)

    # Search for test-specific directories
    test_dirs = []
    device_dirs = []

    for base in candidate_dirs:
        if not os.path.exists(base):
            continue
        result["search_paths"].append(os.path.relpath(base, udmi_root))

        # Check out/devices/<device> or devices/<device>
        possible_device_roots = [
            os.path.join(base, "out", "devices"),
            os.path.join(base, "devices"),
            os.path.join(base, "out_devices"),
        ]
        
        for dev_root in possible_device_roots:
            if not os.path.exists(dev_root):
                continue
            
            # If device is specified, check that specific device dir
            dev_subdirs = [device] if device else [d for d in os.listdir(dev_root) if os.path.isdir(os.path.join(dev_root, d))]
            for dev_name in dev_subdirs:
                dev_path = os.path.join(dev_root, dev_name)
                if not os.path.exists(dev_path):
                    continue
                device_dirs.append(dev_path)

                # Check suite-level results.md / RESULT.log at device level
                for res_name in ("results.md", "RESULT.log", "result.log", f"sequencer_{dev_name}.json"):
                    res_file = os.path.join(dev_path, res_name)
                    if os.path.exists(res_file):
                        rel_res = os.path.relpath(res_file, udmi_root)
                        if not test:
                            # Only set as primary result if we are inspecting the entire device suite
                            if res_name.endswith(".md") and not result["artifacts"]["results_md"]:
                                result["artifacts"]["results_md"] = rel_res
                            elif not result["artifacts"]["result_log"]:
                                result["artifacts"]["result_log"] = rel_res
                        else:
                            # Store in extra_logs so it does not distract from the isolated test
                            if rel_res not in result["artifacts"]["extra_logs"]:
                                result["artifacts"]["extra_logs"].append(rel_res)

                # Check tests subdirectory

                tests_base = os.path.join(dev_path, "tests")
                if os.path.exists(tests_base):
                    test_subdirs = [test] if test else [t for t in os.listdir(tests_base) if os.path.isdir(os.path.join(tests_base, t))]
                    for t_name in test_subdirs:
                        t_path = os.path.join(tests_base, t_name)
                        if os.path.exists(t_path):
                            test_dirs.append(t_path)

    # Extract artifacts from test directories
    for t_dir in test_dirs:
        for preferred_log in ("sequence.log", "sequencer.log"):
            p_log = os.path.join(t_dir, preferred_log)
            if os.path.exists(p_log) and not result["artifacts"]["sequence_log"]:
                result["artifacts"]["sequence_log"] = os.path.relpath(p_log, udmi_root)
                break

        for preferred_md in ("sequence.md", "sequencer.md"):
            p_md = os.path.join(t_dir, preferred_md)
            if os.path.exists(p_md) and not result["artifacts"]["sequence_md"]:
                result["artifacts"]["sequence_md"] = os.path.relpath(p_md, udmi_root)
                break

        for fname in os.listdir(t_dir):
            fpath = os.path.join(t_dir, fname)
            rel = os.path.relpath(fpath, udmi_root)
            if fname in ("sequence.log", "sequencer.log", "sequence.md", "sequencer.md"):
                continue
            if fname in ("device_system.log", "device.log", "system.log"):
                if not result["artifacts"]["device_system_log"]:
                    result["artifacts"]["device_system_log"] = rel

            elif fname.startswith("events_") and fname.endswith(".json"):
                if rel not in result["artifacts"]["events_json"]:
                    result["artifacts"]["events_json"].append(rel)
            elif fname.startswith("state_") and fname.endswith(".json"):
                if rel not in result["artifacts"]["state_json"]:
                    result["artifacts"]["state_json"].append(rel)
            elif fname.startswith("config_") and fname.endswith(".json"):
                if rel not in result["artifacts"]["config_json"]:
                    result["artifacts"]["config_json"].append(rel)
            elif fname.endswith(".log") or fname.endswith(".out"):
                if rel not in result["artifacts"]["extra_logs"]:
                    result["artifacts"]["extra_logs"].append(rel)

    # Search for global pubber.log and udmis.log
    for base in [os.path.join(udmi_root, "out"), udmi_root] + candidate_dirs:
        pub_p = os.path.join(base, "pubber.log")
        if os.path.exists(pub_p) and not result["artifacts"]["pubber_log"]:
            result["artifacts"]["pubber_log"] = os.path.relpath(pub_p, udmi_root)
        udm_p = os.path.join(base, "udmis.log")
        if os.path.exists(udm_p) and not result["artifacts"]["udmis_log"]:
            result["artifacts"]["udmis_log"] = os.path.relpath(udm_p, udmi_root)

    # Discover custom context files (e.g. prior reports, notes, annotations, summaries)
    context_roots = [
        os.path.join(udmi_root, "out", "mantis", "diagnose"),
        os.path.join(udmi_root, "out", "diagnose")
    ]
    for c_root in context_roots:
        if not os.path.exists(c_root):
            continue
        for root, _, files in os.walk(c_root):
            if device and device not in root:
                continue
            if test and test not in root:
                continue
            for f in files:
                if f.endswith((".md", ".json", ".txt")) and any(k in f.lower() for k in ("report", "context", "summary", "notes")):
                    rel = os.path.relpath(os.path.join(root, f), udmi_root)
                    if rel not in result["artifacts"]["custom_context"]:
                        result["artifacts"]["custom_context"].append(rel)

    for t_dir in test_dirs:
        for fname in ("notes.md", "context.json", "user_notes.md", "report.md", "custom_context.md"):
            fpath = os.path.join(t_dir, fname)
            if os.path.exists(fpath):
                rel = os.path.relpath(fpath, udmi_root)
                if rel not in result["artifacts"]["custom_context"]:
                    result["artifacts"]["custom_context"].append(rel)

    return result




def list_available_test_runs(
    udmi_root: str,
    site: Optional[str] = None,
    device: Optional[str] = None,
    test_filter: Optional[str] = None,
    limit: int = 40
) -> str:
    """
    Discovers and provides a high-level summary of available test runs across the workspace.
    Groups results by site and device to maintain a compact, high-signal response.
    """
    device_map: Dict[str, Dict[str, List[str]]] = {}  # {site: {device: [tests]}}
    total_tests = 0

    sites_dir = os.path.join(udmi_root, "sites")
    if os.path.exists(sites_dir):
        # Filter sites if specified
        target_sites = [site.replace("sites/", "").split("/")[0]] if site else sorted(os.listdir(sites_dir))
        for s in target_sites:
            s_path = os.path.join(sites_dir, s)
            if not os.path.isdir(s_path):
                continue
            site_key = f"sites/{s}"
            if site_key not in device_map:
                device_map[site_key] = {}

            for sub in [s_path, os.path.join(s_path, "udmi")]:
                dev_root = os.path.join(sub, "out", "devices")
                if not os.path.exists(dev_root):
                    continue

                dev_subdirs = [device] if device else sorted(os.listdir(dev_root))
                for dev in dev_subdirs:
                    dev_p = os.path.join(dev_root, dev)
                    tests_p = os.path.join(dev_p, "tests")
                    if not os.path.exists(tests_p) or not os.path.isdir(tests_p):
                        continue

                    if dev not in device_map[site_key]:
                        device_map[site_key][dev] = []

                    for t in sorted(os.listdir(tests_p)):
                        t_path = os.path.join(tests_p, t)
                        if not os.path.isdir(t_path):
                            continue
                        if test_filter and test_filter.lower() not in t.lower():
                            continue
                        if t not in device_map[site_key][dev]:
                            device_map[site_key][dev].append(t)
                            total_tests += 1

    lines = [
        "### Available UDMI Test Runs Summary",
        f"- **Total Discovered Tests**: {total_tests}",
        f"- **Filter Applied**: site=`{site or '*'}` , device=`{device or '*'}` , test_filter=`{test_filter or '*'}`\n"
    ]

    # If user filtered on a specific device or test keyword, show individual test items
    if device or test_filter:
        lines.append("| Site | Device | Test Case | Status Indicator |")
        lines.append("| :--- | :--- | :--- | :--- |")
        count = 0
        for s_key, dev_dict in device_map.items():
            for d_name, test_list in dev_dict.items():
                for t_name in test_list:
                    if count >= limit:
                        lines.append(f"| ... | ... | *[Truncated {total_tests - limit} additional tests]* | - |")
                        break
                    lines.append(f"| `{s_key}` | `{d_name}` | `{t_name}` | Artifacts Available |")
                    count += 1
                if count >= limit:
                    break
            if count >= limit:
                break
    else:
        # High-level hierarchical device rollup table
        lines.append("| Site Model | Device ID | Test Count | Sample Tests Executed |")
        lines.append("| :--- | :--- | :--- | :--- |")
        device_count = 0
        for s_key, dev_dict in device_map.items():
            for d_name, test_list in dev_dict.items():
                if not test_list:
                    continue
                device_count += 1
                if len(test_list) <= 4:
                    samples = ", ".join([f"`{t}`" for t in test_list])
                else:
                    samples = ", ".join([f"`{t}`" for t in test_list[:3]]) + f", *+{len(test_list)-3} more*"
                lines.append(f"| `{s_key}` | `{d_name}` | {len(test_list)} tests | {samples} |")

        if device_count == 0:
            lines.append("| *No structured test output directories located* | - | - | - |")

        lines.append("\n> **Tip**: Call `locate_test_artifacts(device=\"<device>\", test=\"<test>\")` to fetch exact log paths for any test.")

    return "\n".join(lines)


def get_test_execution_summary(
    udmi_root: str,
    test_id: str,
    device_id: str,
    site_name: Optional[str] = None
) -> str:
    """
    Instantly extracts a high-density, precise execution summary of a specific test run:
    Identifies exact start/end line numbers, timestamps, transaction IDs dispatched,
    state/event messages received, schema validation errors, and the final test RESULT.
    """
    sname = site_name
    if not sname:
        sites_dir = os.path.join(udmi_root, "sites")
        if os.path.isdir(sites_dir):
            for candidate in sorted(os.listdir(sites_dir)):
                candidate_path = os.path.join(sites_dir, candidate)
                if os.path.isdir(candidate_path):
                    if (
                        os.path.exists(os.path.join(candidate_path, "devices", device_id)) or
                        os.path.exists(os.path.join(candidate_path, "udmi", "devices", device_id)) or
                        os.path.exists(os.path.join(candidate_path, "udmi", "out", "devices", device_id)) or
                        os.path.exists(os.path.join(candidate_path, "out", "devices", device_id))
                    ):
                        sname = f"sites/{candidate}"
                        break
    if not sname:
        return f"Error: No site model specified and could not auto-locate device '{device_id}' under any site in 'sites/'."

    art_data = locate_test_artifacts(udmi_root=udmi_root, site=sname, device=device_id, test=test_id)
    art = art_data.get("artifacts", {})
    log_rel = art.get("sequence_log")

    if not log_rel:
        return f"Error: Could not find sequence.log for device='{device_id}', test='{test_id}' in '{sname}'."


    full_log_path = os.path.join(udmi_root, log_rel)
    if not os.path.exists(full_log_path):
        return f"Error: Log file '{log_rel}' does not exist."

    # Detect GCP project from site config
    gcp_project = "unknown"
    for cfg_rel in [
        f"sites/{sname.replace('sites/', '').split('/')[0]}/udmi/cloud_iot_config.json",
        f"sites/{sname.replace('sites/', '').split('/')[0]}/cloud_iot_config.json"
    ]:
        cfg_full = os.path.join(udmi_root, cfg_rel)
        if os.path.exists(cfg_full):
            try:
                with open(cfg_full, 'r', encoding='utf-8') as f:
                    gcp_project = json.load(f).get("project_id", gcp_project)
            except Exception:
                pass

    start_line = None
    end_line = None
    result_status = "UNKNOWN"
    result_detail = ""
    start_ts = None
    end_ts = None
    tx_dispatched = []
    tx_acked = []
    errors_logged = []
    stale_state_warnings = []
    stale_cutoff = None
    nostate_mode = False
    telemetry_timestamps = []

    ts_pattern = re.compile(r'^(\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?|\d{2}:\d{2}:\d{2}(?:\.\d+)?)')

    with open(full_log_path, 'r', encoding='utf-8', errors='replace') as f:
        for idx, line in enumerate(f, start=1):
            line_str = line.strip()
            line_lower = line_str.lower()
            m = ts_pattern.match(line_str)
            ts = m.group(1) if m else None

            if f"starting test {test_id.lower()}" in line_lower:
                start_line = idx
                start_ts = ts

            if "running test with state checks disabled" in line_lower:
                nostate_mode = True

            if start_line and not end_line:
                if ts:
                    end_ts = ts

                if "stale state cutoff threshold is" in line_lower:
                    parts = line_str.split("Stale state cutoff threshold is")
                    if len(parts) > 1:
                        stale_cutoff = parts[1].strip()

                if "ignoring stale state update" in line_lower:
                    stale_state_warnings.append(line_str)

                if "handling device message events_" in line_lower or "received events_" in line_lower:
                    if ts and (not telemetry_timestamps or telemetry_timestamps[-1] != ts):
                        telemetry_timestamps.append(ts)

                if "adding configtransaction" in line_lower:
                    tx_m = re.search(r'RC:[a-f0-9\.]+', line_str)
                    if tx_m:
                        tx_dispatched.append(tx_m.group(0))
                elif "has configtransaction" in line_lower:
                    tx_m = re.search(r'RC:[a-f0-9\.]+', line_str)
                    if tx_m:
                        tx_acked.append(tx_m.group(0))

                if "error validating schema" in line_lower:
                    errors_logged.append(line_str)
                elif "failed waiting until" in line_lower or "timeout waiting" in line_lower or "stage timeout" in line_lower:
                    errors_logged.append(line_str)

                if f"terminating test {test_id.lower()}" in line_lower or ("result" in line_lower and ("pass" in line_lower or "fail" in line_lower or "skip" in line_lower)):
                    end_line = idx
                    if "result pass" in line_lower:
                        result_status = "PASS"
                    elif "result fail" in line_lower or "result error" in line_lower:
                        result_status = "FAIL"
                        result_detail = line_str.split("RESULT")[-1].strip() if "RESULT" in line_str else line_str

    if not start_line:
        start_line = 1

    # Check device metadata.json syntax and testing block (both in current workspace and at test execution timestamp)
    meta_syntax_error = None
    meta_nostate = None
    hist_syntax_error = None
    hist_commit_sha = None
    hist_commit_msg = None
    hist_nostate = None

    clean_s = sname.replace("sites/", "").split("/")[0]
    site_base = os.path.join(udmi_root, "sites", clean_s)

    for m_rel in [
        f"udmi/devices/{device_id}/metadata.json",
        f"devices/{device_id}/metadata.json"
    ]:
        full_m = os.path.join(site_base, m_rel)
        if os.path.exists(full_m):
            # Check current working tree
            try:
                with open(full_m, "r", encoding="utf-8") as mf:
                    raw_m = mf.read()
                m_obj = json.loads(raw_m)
                meta_nostate = m_obj.get("testing", {}).get("nostate", False)
            except json.JSONDecodeError as jde:
                meta_syntax_error = f"{jde.msg} at line {jde.lineno}, col {jde.colno}"
            except Exception as e:
                meta_syntax_error = str(e)

            # Check historical version at test run time
            if start_ts and os.path.exists(os.path.join(site_base, ".git")):
                try:
                    cmd = ["git", "log", "-n", "1", f"--before={start_ts}", "--format=%H|%s"]
                    proc = subprocess.run(cmd, cwd=site_base, capture_output=True, text=True)
                    if proc.returncode == 0 and proc.stdout.strip():
                        parts = proc.stdout.strip().split("|", 1)
                        hist_commit_sha = parts[0]
                        hist_commit_msg = parts[1] if len(parts) > 1 else ""

                        show_cmd = ["git", "show", f"{hist_commit_sha}:{m_rel}"]
                        show_proc = subprocess.run(show_cmd, cwd=site_base, capture_output=True, text=True)
                        if show_proc.returncode == 0:
                            try:
                                h_obj = json.loads(show_proc.stdout)
                                hist_nostate = h_obj.get("testing", {}).get("nostate", False)
                            except json.JSONDecodeError as hjde:
                                hist_syntax_error = f"{hjde.msg} at line {hjde.lineno}, col {hjde.colno}"
                except Exception as e:
                    hist_syntax_error = f"Exception: {e}"
            break


    summary_lines = [
        f"### 📋 Test Execution Summary: `{device_id}` / `{test_id}`",
        f"- **Log File**: `{log_rel}` (Lines {start_line} - {end_line or 'EOF'})",
        f"- **Execution Time Window**: `{start_ts or 'unknown'}` to `{end_ts or 'unknown'}`",
        f"- **Detected GCP Project (for Cloud Logs)**: `{gcp_project}`",
        f"- **State Checks Disabled (`nostate`) in Log**: `{nostate_mode}`",
        f"- **Final Test Result**: `{result_status}` {result_detail}\n"
    ]

    if hist_syntax_error:
        commit_disp = hist_commit_sha[:10] if hist_commit_sha else "unknown"
        summary_lines.append("#### 🚨 CRITICAL SITE MODEL SYNTAX ERROR AT TEST EXECUTION TIME:")
        summary_lines.append(f"- **Active Commit at Run Time (`{start_ts}`)**: `{commit_disp}` (*{hist_commit_msg}*)")
        summary_lines.append(f"- **Historical JSON Parse Failure**: `{hist_syntax_error}`")
        summary_lines.append("- **Root Cause**: When this test executed, `metadata.json` contained invalid JSON syntax (e.g. duplicate closing brace). This broke the Jackson parser in the Java Sequencer, preventing it from reading `testing: { nostate: true }` and causing it to fall back to full state synchronization and stall on stale state cutoff!")
        if not meta_syntax_error and meta_nostate:
            summary_lines.append("- **Resolution Note**: This syntax error was fixed in a subsequent commit in the site model, but was actively broken when the test ran.\n")

    elif meta_syntax_error:
        summary_lines.append("#### 🚨 CRITICAL SITE MODEL SYNTAX ERROR IN WORKING TREE:")
        summary_lines.append(f"- **File**: `sites/{clean_s}/devices/{device_id}/metadata.json`")
        summary_lines.append(f"- **JSON Parse Failure**: `{meta_syntax_error}`")
        summary_lines.append("- **Impact**: Broken JSON syntax prevents the Java Sequencer from reading `testing: { nostate: true }` or device metadata, causing it to fall back to full state synchronization and fail setup!\n")
    elif meta_nostate is not None:
        summary_lines.append(f"- **Device Metadata `testing.nostate` Configured**: `{meta_nostate}`")

    summary_lines.append("#### Protocol Transactions & State Synchronization")

    summary_lines.append(f"- **Config Transactions Dispatched**: {', '.join([f'`{t}`' for t in tx_dispatched]) if tx_dispatched else 'None'}")
    summary_lines.append(f"- **Transactions Acknowledged in State**: {', '.join([f'`{t}`' for t in tx_acked]) if tx_acked else 'None'}")

    if stale_cutoff:
        summary_lines.append(f"- **Stale State Cutoff Threshold**: `{stale_cutoff}`")

    if stale_state_warnings:
        summary_lines.append("\n#### 🚨 Stale State Updates Ignored by Sequencer:")
        for w in stale_state_warnings:
            summary_lines.append(f"- `{w}`")
        summary_lines.append("> 💡 **Root Cause Indicator**: The device published a state update whose timestamp lagged behind the sequencer's `stale state cutoff threshold`. The sequencer discarded the state message and timed out waiting for a fresh state update.")

    if telemetry_timestamps:
        summary_lines.append("\n#### 📡 Telemetry Event Publication Cadence:")
        summary_lines.append(f"- Recorded Event Timestamps: {', '.join([f'`{t}`' for t in telemetry_timestamps[:5]])}")
        if len(telemetry_timestamps) >= 2:
            summary_lines.append(f"- Total Events Observed: `{len(telemetry_timestamps)}`")
            deltas = []
            for i in range(len(telemetry_timestamps) - 1):
                try:
                    t1_str = telemetry_timestamps[i].replace("Z", "+00:00")
                    t2_str = telemetry_timestamps[i+1].replace("Z", "+00:00")
                    t1_dt = datetime.fromisoformat(t1_str)
                    t2_dt = datetime.fromisoformat(t2_str)
                    deltas.append(abs((t2_dt - t1_dt).total_seconds()))
                except Exception:
                    pass
            if deltas:
                avg_delta = sum(deltas) / len(deltas)
                summary_lines.append(f"- Average Inter-Arrival Interval: `~{int(avg_delta)}s`")
                if avg_delta >= 180:
                    summary_lines.append(f"> ⚠️ **Driver Rate Mismatch Warning**: Telemetry is emitted on a fixed ~{int(avg_delta)}s (5-minute) cadence. Because default sequencer pointset timeouts are 120s, the test runner times out waiting for 2 events unless test timeouts are extended or dynamic `sample_rate_sec` is supported by the driver.")



    if errors_logged:
        summary_lines.append("#### Critical Schema & Synchronization Errors in Test:")
        parsed_violations = []
        for err in errors_logged:
            # Extract individual violations if comma-separated schema error
            if "error validating schema" in err.lower():
                parts = err.split("Error validating schema:")[-1].split(", /")
                for p in parts:
                    clean_p = ("/" + p.lstrip("/")).strip()
                    if clean_p and clean_p not in parsed_violations:
                        parsed_violations.append(clean_p)
            else:
                parsed_violations.append(err.strip())

        for v in parsed_violations[:8]:
            summary_lines.append(f"- `{v}`")
        if len(parsed_violations) > 8:
            summary_lines.append(f"- *...+{len(parsed_violations) - 8} more schema violations*")
        summary_lines.append("")

    if result_status == "FAIL" or "timed out" in "".join(errors_logged).lower():
        summary_lines.append(f"> 💡 **Actionable Diagnostic Next Step**:")
        summary_lines.append(f"> Call `pull_cloud_logs(test_id=\"{test_id}\", site_name=\"{sname}\")` to inspect what UDMIS received in project `{gcp_project}` during `{start_ts}` - `{end_ts}`.")

    return "\n".join(summary_lines)



