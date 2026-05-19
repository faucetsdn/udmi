#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
from datetime import datetime, timedelta
from .agent import run_triage_analysis

# Resolve root directory
MANTIS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UDMI_ROOT = os.path.dirname(MANTIS_DIR)

def parse_timestamp(ts_str):
    """Parses an ISO timestamp string into a datetime object."""
    if not ts_str:
        return None
    # Strip brackets or whitespace if any
    ts_str = ts_str.strip("[] ")
    formats = ["%Y-%m-%dT%H:%M:%SZ", "%Y-%m-%d %H:%M:%S", "%H:%M:%S"]
    for fmt in formats:
        try:
            if fmt == "%H:%M:%S":
                # Assume today's date
                today = datetime.utcnow().date()
                t = datetime.strptime(ts_str, fmt).time()
                return datetime.combine(today, t)
            return datetime.strptime(ts_str, fmt)
        except ValueError:
            continue
    return None

def extract_timebounds_from_log(seq_log_path):
    """Scans a sequence log to find starting and ending timestamps, falling back to first/last lines if needed."""
    start_ts, end_ts = None, None
    first_ts, last_ts = None, None
    if not os.path.exists(seq_log_path):
        return None, None
        
    # Matches any timestamp at start of line
    ts_pattern = re.compile(r'^([\d\-T:Z]+)\s+')
    # Precise starting/ending patterns
    start_pattern = re.compile(r'^([\d\-T:Z]+)\s+NOTICE\s+Starting\s+test')
    end_pattern = re.compile(r'^([\d\-T:Z]+)\s+(NOTICE\s+Ending\s+test|ERROR\s+terminating\s+test|RESULT\s+fail|RESULT\s+pass)')
    
    try:
        with open(seq_log_path, 'r', encoding='utf-8', errors='replace') as f:
            for line in f:
                # Track any timestamp on the line
                ts_match = ts_pattern.match(line)
                if ts_match:
                    ts = parse_timestamp(ts_match.group(1))
                    if ts:
                        if not first_ts:
                            first_ts = ts
                        last_ts = ts
                        
                # Track start test
                sm = start_pattern.match(line)
                if sm:
                    start_ts = parse_timestamp(sm.group(1))
                    
                # Track end/terminating test
                em = end_pattern.match(line)
                if em:
                    end_ts = parse_timestamp(em.group(1))
    except Exception as e:
        print(f"Warning: failed to extract timestamps from {seq_log_path}: {e}", file=sys.stderr)
        
    # Fallbacks
    if not start_ts:
        start_ts = first_ts
    if not end_ts:
        end_ts = last_ts
        
    return start_ts, end_ts

def slice_log_by_timebounds(filepath, start_dt, end_dt, padding_seconds=5):
    """Slices log entries matching the timebounds plus/minus padding."""
    sliced_entries = []
    if not os.path.exists(filepath) or not start_dt or not end_dt:
        return sliced_entries
        
    padded_start = start_dt - timedelta(seconds=padding_seconds)
    padded_end = end_dt + timedelta(seconds=padding_seconds)
    
    # Regex to match ISO timestamps at the start of lines
    ts_pattern = re.compile(r'^([\d\-T:Z]+)\s+')
    
    try:
        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            for line in f:
                m = ts_pattern.match(line)
                if m:
                    ts = parse_timestamp(m.group(1))
                    if ts and (padded_start <= ts <= padded_end):
                        sliced_entries.append(line.strip())
    except Exception as e:
        print(f"Warning: failed to slice log {filepath}: {e}", file=sys.stderr)
        
    return sliced_entries

def read_filtered_sequence_log(filepath, max_chars=150000):
    """Reads a sequence log, filtering out noisy TRACE level lines and suppressed exceptions to save context and API quota."""
    if not os.path.exists(filepath):
        return ""
    try:
        filtered_lines = []
        curr_chars = 0
        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
            for line in f:
                # Exclude high-noise logs
                if " TRACE " in line or "Suppressing exception" in line or "ending stack trace:" in line or "Stack trace::" in line:
                    continue
                line_len = len(line)
                if curr_chars + line_len > max_chars:
                    break
                filtered_lines.append(line)
                curr_chars += line_len
        return "".join(filtered_lines)
    except Exception as e:
        print(f"Warning: failed to read filtered log {filepath}: {e}", file=sys.stderr)
        return ""

def get_device_id(site_dir):
    """Discovers the device under test by scanning the site out/devices folder."""
    devices_dir = os.path.join(UDMI_ROOT, site_dir, "out/devices")
    if os.path.exists(devices_dir):
        dirs = [d for d in os.listdir(devices_dir) if os.path.isdir(os.path.join(devices_dir, d))]
        if dirs:
            return dirs[0]  # Default to the first device folder found (e.g., AHU-1)
    return "AHU-1"

def scan_failures_from_out(filepath):
    """Scans sequencer.out or test_itemized.out to extract failed test case names."""
    failures = []
    if not os.path.exists(filepath):
        return failures
        
    try:
        with open(filepath, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                tokens = line.split()
                # Check standard RESULT format: RESULT fail category test_name ...
                # Itemized tests might have a leading digits token, handle it.
                if tokens and tokens[0].isdigit():
                    tokens = tokens[1:]
                if len(tokens) >= 4 and tokens[0] in ["RESULT", "CPBLTY"] and tokens[1] == "fail":
                    failures.append({
                        'test_name': tokens[3],
                        'category': tokens[2],
                        'suite': 'itemized' if 'itemized' in filepath else 'sequencer'
                    })
    except Exception as e:
        print(f"Warning: failed to scan failures from {filepath}: {e}", file=sys.stderr)
    return failures

def find_successful_run_for_test(parent_dir, test_id):
    """Scans sibling run directories to find one where test_id passed."""
    if not os.path.isdir(parent_dir):
        return None
        
    for rdir in sorted(os.listdir(parent_dir)):
        full_rdir = os.path.join(parent_dir, rdir)
        if not os.path.isdir(full_rdir) or not rdir.startswith("run_"):
            continue
            
        # Check sequencer.out
        seq_out = os.path.join(full_rdir, "sequencer.out")
        if os.path.exists(seq_out):
            failures = scan_failures_from_out(seq_out)
            # Check if it ran and passed (i.e. not in failures and present in output)
            if not any(fail['test_name'] == test_id for fail in failures):
                try:
                    with open(seq_out, 'r') as f:
                        for line in f:
                            if test_id in line and "RESULT pass" in line:
                                return full_rdir
                except Exception:
                    pass
                            
        # Check test_itemized.out
        item_out = os.path.join(full_rdir, "test_itemized.out")
        if os.path.exists(item_out):
            failures = scan_failures_from_out(item_out)
            if not any(fail['test_name'] == test_id for fail in failures):
                try:
                    with open(item_out, 'r') as f:
                        for line in f:
                            if test_id in line and "RESULT pass" in line:
                                return full_rdir
                except Exception:
                    pass
    return None

def main():
    parser = argparse.ArgumentParser(
        description="Mantis Triage Agent (Diagnose) - AI-Powered Diagnostics"
    )
    parser.add_argument("--target", required=True, help="Target project (e.g. //mqtt/localhost)")
    parser.add_argument("--run-dir", required=True, help="Directory containing iteration run backups")
    parser.add_argument("--site-dir", default="sites/udmi_site_model", help="Path to site model folder")
    parser.add_argument("--test", help="Specific test case to triage (sweeps all failures if omitted)")
    
    args = parser.parse_args()
    
    run_dir = os.path.abspath(args.run_dir)
    if not os.path.exists(run_dir):
        print(f"Error: Iteration run directory '{args.run_dir}' does not exist.", file=sys.stderr)
        sys.exit(1)
        
    # 1. Identify failed tests (Automated Triage Sweep)
    failed_tests = []
    if args.test:
        failed_tests.append({'test_name': args.test, 'category': 'unknown', 'suite': 'both'})
    else:
        print("No specific test provided. Running automated sweep scan for failures...")
        failed_tests.extend(scan_failures_from_out(os.path.join(run_dir, "sequencer.out")))
        failed_tests.extend(scan_failures_from_out(os.path.join(run_dir, "test_itemized.out")))
        
        # Remove duplicates
        seen = set()
        unique_failures = []
        for f in failed_tests:
            if f['test_name'] not in seen:
                seen.add(f['test_name'])
                unique_failures.append(f)
        failed_tests = unique_failures

    if not failed_tests:
        print("🎉 No failing test cases detected in run outputs. Diagnostics complete!")
        sys.exit(0)
        
    print(f"Found {len(failed_tests)} failed test cases to triage: {', '.join(f['test_name'] for f in failed_tests)}")

    # Resolve target output folder structure
    clean_target = args.target.replace("/", "_").replace("+", "_").strip("_")
    site_id = os.path.basename(args.site_dir)
    device_id = get_device_id(args.site_dir)
    
    # Global log sources
    pubber_log_path = os.path.join(run_dir, "pubber.log")
    udmis_log_path = os.path.join(run_dir, "udmis.log")
    
    triage_summaries = []

    # 2. Triage each failure
    for idx, f in enumerate(failed_tests, start=1):
        test_id = f['test_name']
        print(f"\n--- Triaging Failure {idx} of {len(failed_tests)}: {test_id} ---")
        
        local_seq_log = os.path.join(run_dir, f"out/devices/{device_id}/tests/{test_id}/sequence.log")
        local_seq_md = os.path.join(run_dir, f"out/devices/{device_id}/tests/{test_id}/sequence.md")
        
        # 3. Compile available context catalog
        catalog = {
            'Sequencer_Summary': os.path.exists(os.path.join(run_dir, "sequencer.out")),
            'Itemized_Summary': os.path.exists(os.path.join(run_dir, "test_itemized.out")),
            'Local_Sequence_Log': os.path.exists(local_seq_log),
            'Local_Sequence_Markdown': os.path.exists(local_seq_md),
            'Global_Pubber_Log': os.path.exists(pubber_log_path),
            'Global_UDMIS_Log': os.path.exists(udmis_log_path)
        }
        
        print(f"Available Context Catalog: {', '.join(k for k, v in catalog.items() if v)}")
        
        # 4. Extract padded timebounds and slice global streams
        start_dt, end_dt = extract_timebounds_from_log(local_seq_log)
        sliced_pubber = []
        sliced_udmis = []
        
        if start_dt and end_dt:
            print(f"Test start: {start_dt.strftime('%H:%M:%S')} | end: {end_dt.strftime('%H:%M:%S')} (Padded correlation active)")
            sliced_pubber = slice_log_by_timebounds(pubber_log_path, start_dt, end_dt)
            sliced_udmis = slice_log_by_timebounds(udmis_log_path, start_dt, end_dt)
        else:
            print("Warning: Could not extract starting/ending timestamps from sequence log. Slicing bypassed.")

        # Load local sequence logs
        local_seq_log_content = read_filtered_sequence_log(local_seq_log)

        local_seq_md_content = ""
        if os.path.exists(local_seq_md):
            with open(local_seq_md, 'r', encoding='utf-8', errors='replace') as fm:
                local_seq_md_content = fm.read()[:100000] # Markdown is already concise, read up to 100KB

        # Discover reference successful execution for differential triage
        success_run_dir = find_successful_run_for_test(os.path.dirname(run_dir), test_id)
        success_seq_log_content = ""
        success_seq_md_content = ""
        
        if success_run_dir:
            success_seq_log = os.path.join(success_run_dir, f"out/devices/{device_id}/tests/{test_id}/sequence.log")
            success_seq_md = os.path.join(success_run_dir, f"out/devices/{device_id}/tests/{test_id}/sequence.md")
            
            success_seq_log_content = read_filtered_sequence_log(success_seq_log)
            if os.path.exists(success_seq_md):
                try:
                    with open(success_seq_md, 'r', encoding='utf-8', errors='replace') as fsm:
                        success_seq_md_content = fsm.read()[:100000]
                except Exception:
                    pass

        # 5. Compile prompt payload
        payload = []
        payload.append(f"## Metadata Context")
        payload.append(f"- **Project ID**: {clean_target}")
        payload.append(f"- **Site ID**: {site_id}")
        payload.append(f"- **Device ID**: {device_id}")
        payload.append(f"- **Test ID**: {test_id}")
        payload.append(f"- **Current Available Context Catalog**: {json.dumps(catalog, indent=2)}")
        
        payload.append(f"\n## Local Sequencer log.md (Test Execution Details)")
        if local_seq_md_content:
            payload.append(f"```markdown\n{local_seq_md_content}\n```")
        else:
            payload.append("`[Not Available]`")

        payload.append(f"\n## Local Sequencer log.log (Raw Console)")
        if local_seq_log_content:
            payload.append(f"```text\n{local_seq_log_content}\n```")
        else:
            payload.append("`[Not Available]`")

        payload.append(f"\n## Padded Correlated Global UDMIS Logs")
        if sliced_udmis:
            payload.append(f"```text\n" + "\n".join(sliced_udmis) + "\n```")
        else:
            payload.append("`[No correlated UDMIS logs found in test time window]`")

        payload.append(f"\n## Padded Correlated Global Pubber Logs")
        if sliced_pubber:
            payload.append(f"```text\n" + "\n".join(sliced_pubber) + "\n```")
        elif not catalog['Global_Pubber_Log']:
            payload.append("`[Physical Device / Black-Box Device Mode: No emulator logs available]`")
        else:
            payload.append("`[No correlated Pubber logs found in test time window]`")
            
        payload.append(f"\n## Reference Successful Run Details (Differential Triage Baseline)")
        if success_run_dir:
            payload.append(f"Found successful execution of this test in sibling: `{os.path.basename(success_run_dir)}`")
            if success_seq_md_content:
                payload.append(f"### Reference Successful log.md")
                payload.append(f"```markdown\n{success_seq_md_content}\n```")
            if success_seq_log_content:
                payload.append(f"### Reference Successful log.log")
                payload.append(f"```text\n{success_seq_log_content}\n```")
        else:
            payload.append("`[No successful reference runs found in sibling directories]`")
            
        prompt_payload = "\n".join(payload)
        
        # 6. Run AI Diagnostic Agent
        analysis_text = ""
        try:
            analysis_text = run_triage_analysis(prompt_payload)
        except Exception as e:
            analysis_text = f"⚠️ Error executing Gemini AI diagnostics for {test_id}: {e}"

        # 7. Save localized report under self-contained mantis/out/diagnose/
        nested_out_dir = os.path.join(MANTIS_DIR, "out", "diagnose", clean_target, site_id, device_id, test_id)
        os.makedirs(nested_out_dir, exist_ok=True)
        
        report_filepath = os.path.join(nested_out_dir, "triage_analysis.md")
        with open(report_filepath, 'w', encoding='utf-8') as fr:
            fr.write(analysis_text)
            
        print(f"Diagnostic report successfully saved: {report_filepath}")
        
        # Extract high-level breakpoint for summary report
        breakpoint_summary = "Triage complete. Review details."
        insufficient = "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE" in analysis_text
        
        bm = re.search(r'## 2\. Breakpoint Summary\s*\n*>\s*(.*)', analysis_text)
        if bm:
            breakpoint_summary = bm.group(1).strip()
        elif insufficient:
            breakpoint_summary = "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE"
            
        # Triage summary links are relative to the summary report file
        triage_summaries.append({
            'test_id': test_id,
            'category': f['category'],
            'suite': f['suite'],
            'breakpoint': breakpoint_summary,
            'insufficient': insufficient,
            'report_link': f"./{device_id}/{test_id}/triage_analysis.md"
        })

    # 8. Compile consolidated triage_summary_report.md inside sites output directory
    print("\nCompiling site-specific Triage Summary Report...")
    root_report_path = os.path.join(MANTIS_DIR, "out", "diagnose", clean_target, site_id, "triage_summary_report.md")
    os.makedirs(os.path.dirname(root_report_path), exist_ok=True)
    
    sum_md = []
    sum_md.append("# Mantis AI Diagnostics: Triage Summary Report 🦗👁️")
    sum_md.append(f"**Target Project**: `{args.target}`  ")
    sum_md.append(f"**Site ID**: `{site_id}`  ")
    sum_md.append(f"**Triage executed at**: `{datetime.now().strftime('%Y-%m-%d %H:%M:%S UTC')}`  ")
    sum_md.append("\n---")
    
    # Dashboard
    total_checked = len(failed_tests)
    insufficient_count = len([s for s in triage_summaries if s['insufficient']])
    diagnosed_count = total_checked - insufficient_count
    
    sum_md.append("## Triage Dashboard")
    sum_md.append(f"- **Total Failed Test Cases Scanned**: `{total_checked}`")
    sum_md.append(f"- **Successfully Diagnosed (Isolated Breakpoint)**: `{diagnosed_count}`")
    sum_md.append(f"- **Insufficient Information (Guardrail Triggered)**: `{insufficient_count}`")
    sum_md.append("\n---")
    
    # Failure Breakdown Table
    sum_md.append("## 📋 Failed Test Diagnostics Breakdown")
    sum_md.append("| Test Case | Suite | Category | Breakpoint & Root Cause Isolation Summary | Link to Analysis |")
    sum_md.append("| :--- | :--- | :--- | :--- | :--- |")
    for s in triage_summaries:
        sum_md.append(f"| `{s['test_id']}` | `{s['suite']}` | `{s['category']}` | {s['breakpoint']} | [View Analysis]({s['report_link']}) |")
        
    sum_md.append("\n---")
    
    # Dynamic Failure Clustering
    sum_md.append("## ⚠️ Failure Signature Clustering")
    sum_md.append("> The following failures share similar root cause signatures or breakpoint profiles. Address them together!")
    
    clusters = {}
    for s in triage_summaries:
        sig = "Unknown failure signature"
        if "timed out" in s['breakpoint'].lower() or "timeout" in s['breakpoint'].lower():
            sig = "Sync Wait Timeout (Component latency / missing acknowledgments)"
        elif "schema" in s['breakpoint'].lower() or "validation failed" in s['breakpoint'].lower():
            sig = "Telemetry Schema Violation (Malformed JSON payload envelope)"
        elif "insufficient" in s['breakpoint'].lower() or s['insufficient']:
            sig = "Missing Log Streams (Context insufficient for triage)"
            
        if sig not in clusters:
            clusters[sig] = []
        clusters[sig].append(s)
        
    for sig, tests in clusters.items():
        sum_md.append(f"### 🗂️ {sig} (Affecting {len(tests)} Tests)")
        for t in tests:
            sum_md.append(f"- `{t['test_id']}` ([Triage Details]({t['report_link']}))")
            
    sum_md.append("\n---")
    
    # PR Comments Block
    sum_md.append("## 🤖 GitHub Actions Pull Request Alert Block")
    sum_md.append("```markdown")
    sum_md.append(f"### 🦗 Mantis AI Debugger isolated {total_checked} regressions in this test run:")
    for s in triage_summaries:
        emoji = "⚠️" if s['insufficient'] else "❌"
        sum_md.append(f"- {emoji} **{s['test_id']}**: {s['breakpoint']}")
    sum_md.append("```")
    
    with open(root_report_path, 'w', encoding='utf-8') as fs:
        fs.write("\n".join(sum_md))
        
    print(f"Consolidated triage summary report generated: {root_report_path}\n")
    print("Mantis Triage diagnostic run completed successfully.")

if __name__ == "__main__":
    main()
