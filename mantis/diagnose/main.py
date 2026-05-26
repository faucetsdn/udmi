#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
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
                # Assume today's date in UTC
                today = datetime.now(timezone.utc).date()
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
    ts_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+')
    # Precise starting/ending patterns
    start_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+NOTICE\s+Starting\s+test')
    end_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+(NOTICE\s+Ending\s+test|ERROR\s+terminating\s+test|RESULT\s+fail|RESULT\s+pass)')
    
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
    ts_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+')
    
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
                if " TRACE " in line:
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

def load_skills_context():
    """Loads all SKILL.md files from the diagnose/skills directory into a formatted context string."""
    skills_dir = os.path.join(MANTIS_DIR, "diagnose", "skills")
    if not os.path.exists(skills_dir):
        return ""
    
    skills_content = []
    skills_content.append("\n## Skill Library Context (Reference Guidelines)")
    skills_content.append("Use the following guidelines and procedural instructions to shape your analysis strategy. You must follow them strictly and do not query codebase/git repeatedly for these rules:")
    
    for skill_folder in sorted(os.listdir(skills_dir)):
        skill_path = os.path.join(skills_dir, skill_folder, "SKILL.md")
        if os.path.isfile(skill_path):
            try:
                with open(skill_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    # Exclude metadata frontmatter if present to save context tokens
                    content_clean = re.sub(r'^---.*?---', '', content, flags=re.DOTALL).strip()
                    skills_content.append(f"\n### Skill: {skill_folder}\n{content_clean}")
            except Exception as e:
                print(f"Warning: Failed to read skill file at {skill_path}: {e}", file=sys.stderr)
                
    return "\n".join(skills_content)


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

import glob

def auto_detect_metadata(bundles_dir_path):
    """Auto-detects target and site directory from the bundles directory path."""
    base = os.path.basename(os.path.normpath(bundles_dir_path))
    
    # Strip trailing timestamp or run markers if any
    clean_name = re.sub(r'_\d{8}_\d{6}$', '', base)
    clean_name = re.sub(r'^run_\d+$', '', clean_name)
    clean_name = re.sub(r'^(before|after)_', '', clean_name, flags=re.IGNORECASE)
    
    if not clean_name:
        # If bundles_dir is just "run_1", let's check its parent directory!
        parent_path = os.path.dirname(os.path.normpath(bundles_dir_path))
        parent_base = os.path.basename(parent_path)
        clean_name = re.sub(r'_\d{8}_\d{6}$', '', parent_base)
        clean_name = re.sub(r'^(before|after)_', '', clean_name, flags=re.IGNORECASE)
        
    # Reconstruct target spec
    parts = clean_name.split("_", 1)
    if len(parts) == 2:
        target = f"//{parts[0]}/{parts[1]}"
    else:
        target = f"//{clean_name}" if clean_name else "//mqtt/localhost"
        
    # Discover site_dir
    first_word = parts[0] if parts else "mqtt"
    site_dir = f"sites/{first_word}"
    if not os.path.exists(os.path.join(UDMI_ROOT, site_dir)):
        site_dir = "sites/udmi_site_model"
        
    return target, site_dir

def discover_device_id(run_dir, test_id=None):
    """Discovers the device under test from the run directory."""
    devices_dir = os.path.join(run_dir, "out", "devices")
    if not os.path.exists(devices_dir):
        devices_dir = os.path.join(run_dir, "devices")
    if not os.path.exists(devices_dir):
        return "AHU-1"
        
    if test_id:
        for dev in os.listdir(devices_dir):
            dev_path = os.path.join(devices_dir, dev)
            if os.path.isdir(dev_path):
                test_path = os.path.join(dev_path, "tests", test_id)
                if os.path.exists(test_path):
                    return dev
                    
    dirs = [d for d in os.listdir(devices_dir) if os.path.isdir(os.path.join(devices_dir, d))]
    if dirs:
        return dirs[0]
    return "AHU-1"

def scan_failures_from_metrics(clean_target, output_dir=None):
    """Scans metrics JSON files in output_dir matching the target to find mismatched test failures."""
    if not output_dir:
        output_dir = os.path.join(MANTIS_DIR, "out")
        
    # Find the absolute latest metrics file for this target
    pattern = os.path.join(output_dir, f"metrics_{clean_target}_*.json")
    metric_files = glob.glob(pattern)
    
    failures = []
    if not metric_files:
        return failures
        
    latest_file = max(metric_files, key=os.path.getmtime)
    print(f"Discovered metrics baseline: {os.path.basename(latest_file)}")
    
    try:
        with open(latest_file, 'r') as f:
            metrics = json.load(f)
        for ukey, val in metrics.items():
            # If fail_count > 0, the actual outcome mismatched the expected golden baseline!
            if val.get('fail_count', 0) > 0:
                failures.append({
                    'test_name': val['test_name'],
                    'category': val['category'],
                    'suite': val['test_suite']
                })
    except Exception as e:
        print(f"Warning: Failed to load or parse metrics file {latest_file}: {e}", file=sys.stderr)
        
    return failures

def main():
    parser = argparse.ArgumentParser(
        description="Mantis Triage Agent (Diagnose) - AI-Powered Diagnostics"
    )
    parser.add_argument("--bundles-dir", "-i", dest="bundles_dir", help="Input bundles directory containing run backups (single or multi-run)")
    parser.add_argument("--run-dir", help=argparse.SUPPRESS)  # Hidden legacy alias for compatibility
    parser.add_argument("--test", "-t", help="Specific test case to triage (sweeps all failures if omitted)")
    parser.add_argument("--target", help="Target project (default: auto-detected from bundles-dir)")
    parser.add_argument("--site-dir", help="Path to site model folder (default: auto-detected)")
    
    args = parser.parse_args()
    
    bundles_dir_arg = args.bundles_dir if args.bundles_dir is not None else args.run_dir
    if not bundles_dir_arg:
        parser.print_help()
        sys.exit(1)
        
    bundles_path = os.path.abspath(bundles_dir_arg)
    if not os.path.exists(bundles_path):
        print(f"Error: Specified bundles directory '{bundles_dir_arg}' does not exist.", file=sys.stderr)
        sys.exit(1)
        
    # Auto-detect target and site-dir if omitted
    detected_target, detected_site_dir = auto_detect_metadata(bundles_path)
    target = args.target if args.target is not None else detected_target
    site_dir = args.site_dir if args.site_dir is not None else detected_site_dir
    
    clean_target = target.replace("/", "_").replace("+", "_").strip("_")
    site_id = os.path.basename(site_dir)
    
    # Determine if we have a single run or multi-run folder structure
    run_subdirs = sorted(glob.glob(os.path.join(bundles_path, "run_*")))
    
    # List of (run_dir_path, failed_test_dict)
    runs_to_triage = []
    
    if run_subdirs:
        print(f"Multi-run directory mode: scanning {len(run_subdirs)} runs for failures...")
        unique_failed_tests = {}
        
        # 1. Try to read actual regressions from stability metrics first!
        metrics_failures = []
        if not args.test:
            metrics_failures = scan_failures_from_metrics(clean_target)
            
        if metrics_failures:
            print(f"Found {len(metrics_failures)} regression failures from stability metrics.")
            for f in metrics_failures:
                test_id = f['test_name']
                # Locate the first run directory where this test case actually produced a raw failure
                for run_subdir in run_subdirs:
                    failures = []
                    failures.extend(scan_failures_from_out(os.path.join(run_subdir, "sequencer.out")))
                    failures.extend(scan_failures_from_out(os.path.join(run_subdir, "test_itemized.out")))
                    if any(x['test_name'] == test_id for x in failures):
                        unique_failed_tests[test_id] = {
                            'failure': f,
                            'run_dir': run_subdir
                        }
                        break
        else:
            # Fallback to standard raw failures sweep across all run outs
            for run_subdir in run_subdirs:
                failures = []
                failures.extend(scan_failures_from_out(os.path.join(run_subdir, "sequencer.out")))
                failures.extend(scan_failures_from_out(os.path.join(run_subdir, "test_itemized.out")))
                
                for f in failures:
                    test_id = f['test_name']
                    if args.test and test_id != args.test:
                        continue
                    if test_id not in unique_failed_tests:
                        unique_failed_tests[test_id] = {
                            'failure': f,
                            'run_dir': run_subdir
                        }
                        
        if args.test and args.test not in unique_failed_tests:
            # If specific test forced but not swept, triage in run_1
            unique_failed_tests[args.test] = {
                'failure': {'test_name': args.test, 'category': 'unknown', 'suite': 'both'},
                'run_dir': run_subdirs[0]
            }
            
        for test_id, info in unique_failed_tests.items():
            runs_to_triage.append((info['run_dir'], info['failure']))
            
    else:
        print("Single run directory mode active.")
        failures = []
        if args.test:
            failures.append({'test_name': args.test, 'category': 'unknown', 'suite': 'both'})
        else:
            # 1. Try metrics first
            metrics_failures = scan_failures_from_metrics(clean_target)
            if metrics_failures:
                print(f"Found {len(metrics_failures)} regression failures from stability metrics.")
                failures.extend(metrics_failures)
            else:
                # Fallback to raw sweep
                failures.extend(scan_failures_from_out(os.path.join(bundles_path, "sequencer.out")))
                failures.extend(scan_failures_from_out(os.path.join(bundles_path, "test_itemized.out")))
                
                seen = set()
                unique_failures = []
                for f in failures:
                    if f['test_name'] not in seen:
                        seen.add(f['test_name'])
                        unique_failures.append(f)
                failures = unique_failures
                
        for f in failures:
            runs_to_triage.append((bundles_path, f))
            
    if not runs_to_triage:
        print("🎉 No regression or failing test cases detected in run outputs. Diagnostics complete!")
        sys.exit(0)
        
    print(f"Found {len(runs_to_triage)} test case failures to triage: {', '.join(item[1]['test_name'] for item in runs_to_triage)}")
    
    triage_summaries = []
    
    # 2. Triage each failure
    for idx, (run_dir, f) in enumerate(runs_to_triage, start=1):
        test_id = f['test_name']
        print(f"\n--- Triaging Failure {idx} of {len(runs_to_triage)}: {test_id} (in {os.path.basename(run_dir)}) ---")
        
        device_id = discover_device_id(run_dir, test_id)
        
        # 3. Load triage metadata registry to resolve sharded logs accurately
        triage_metadata = {}
        metadata_path = os.path.join(run_dir, "triage_metadata.json")
        if os.path.exists(metadata_path):
            try:
                with open(metadata_path, 'r', encoding='utf-8') as fm:
                    triage_metadata = json.load(fm)
            except Exception:
                pass
                
        test_meta = triage_metadata.get(test_id, {})
        
        # Initialize default paths
        devices_path = os.path.join(run_dir, "out", "devices")
        if not os.path.exists(devices_path):
            devices_path = os.path.join(run_dir, "devices")
            
        local_seq_log = os.path.join(devices_path, f"{device_id}/tests/{test_id}/sequence.log")
        local_seq_md = os.path.join(devices_path, f"{device_id}/tests/{test_id}/sequence.md")
        pubber_log_path = os.path.join(run_dir, "pubber.log")
        udmis_log_path = os.path.join(run_dir, "udmis.log")
        shard_suffix = ""

        if isinstance(test_meta, dict) and "sequence_log" in test_meta:
            # Rich metadata resolution pointing to the exact sharded directories under run_dir
            shard_suffix = test_meta.get("shard", "")
            local_seq_log = os.path.join(run_dir, test_meta["sequence_log"])
            local_seq_md = os.path.join(run_dir, test_meta["sequence_md"]) if test_meta.get("sequence_md") else ""
            
            pubber_log_path = os.path.join(run_dir, test_meta.get("pubber_log", "pubber.log"))
            udmis_log_path = os.path.join(run_dir, test_meta.get("udmis_log", "udmis.log"))
            print(f"Successfully resolved sharded logs for test '{test_id}' -> Shard Suffix: {shard_suffix}")
        else:
            # Legacy fallback
            shard_suffix = test_meta if isinstance(test_meta, str) else ""
            if shard_suffix:
                pubber_log_path = os.path.join(run_dir, f"pubber_{shard_suffix}.log")
                udmis_log_path = os.path.join(run_dir, f"udmis_{shard_suffix}.log")
            if not os.path.exists(pubber_log_path):
                pubber_log_path = os.path.join(run_dir, "pubber.log")
            if not os.path.exists(udmis_log_path):
                udmis_log_path = os.path.join(run_dir, "udmis.log")
            
        catalog = {
            'Sequencer_Summary': os.path.exists(os.path.join(run_dir, "sequencer.out")),
            'Itemized_Summary': os.path.exists(os.path.join(run_dir, "test_itemized.out")),
            'Local_Sequence_Log': os.path.exists(local_seq_log),
            'Local_Sequence_Markdown': os.path.exists(local_seq_md),
            'Global_Pubber_Log': os.path.exists(pubber_log_path),
            'Global_UDMIS_Log': os.path.exists(udmis_log_path),
            'Sharded_Logs_Active': bool(shard_suffix)
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
                local_seq_md_content = fm.read()[:100000]
                
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
        # Determine run environment dynamically
        run_env = "Cloud Run" if "localhost" not in clean_target else "Local Run"
        
        payload.append(f"- **Project ID**: {clean_target}")
        payload.append(f"- **Site ID**: {site_id}")
        payload.append(f"- **Device ID**: {device_id}")
        payload.append(f"- **Test ID**: {test_id}")
        payload.append(f"- **Run Environment**: {run_env}")
        payload.append(f"- **Triage Trigger Source**: Support Package Archive")
        payload.append(f"- **Active Sequence Log**: {test_meta.get('sequence_log') if isinstance(test_meta, dict) and 'sequence_log' in test_meta else os.path.basename(local_seq_log)}")
        payload.append(f"- **Active UDMIS Log**: {test_meta.get('udmis_log') if isinstance(test_meta, dict) and 'udmis_log' in test_meta else os.path.basename(udmis_log_path)}")
        payload.append(f"- **Active Pubber Log**: {test_meta.get('pubber_log') if isinstance(test_meta, dict) and 'pubber_log' in test_meta else os.path.basename(pubber_log_path)}")
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
            
        payload.append(f"\n## 💡 Reference Trace Pattern Guide for Asynchronous Race Conditions")
        payload.append(
            "Pay close attention to this known architectural pattern in UDMI runs:\n"
            "1. **Out-of-Order Pub/Sub Regression Race**:\n"
            "   - **Scenario**: Sequencer sets expected `last_start` to `1970-01-01T00:01:13Z` via pre-test reset `RC:xxxxxx.00000004`.\n"
            "   - **UDMIS Action**: Processes reset (version 2405, setting last_start=1970) and publishes it at `T1`. Later, UDMIS receives device state (real boot time 2026), auto-munges/synchronizes config (version 2406, setting last_start=2026) and publishes it at `T2`.\n"
            "   - **Pub/Sub Race**: Deliveries arrive out-of-order at the Sequencer. Sequencer processes `T2` (2026) first, setting local expected `last_start` to 2026. Then, it processes the older `T1` (1970) second, reverting its local expected `last_start` back to 1970. This causes a synchronization check mismatch (reported state is 2026, but sequencer regressed expected to 1970) leading to timeout.\n"
            "   - **Evidence**: Check if Sequencer log reports receiving `CGW-501/config/update as PS:xxxxxx` (updating expected to 2026) FIRST, followed by receiving `CGW-501/config/update as RC:xxxxxx` (regressing expected to 1970) SECOND. If this ordering is observed, isolate the root cause specifically as a Sequencer-side out-of-order regression race and propose the temporal guard rail fix in `SequenceBase.java` to prevent regressing last_start backward."
        )
        
        prompt_payload = "\n".join(payload) + "\n" + load_skills_context()
        
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
        
        # Extract breakpoint summary
        breakpoint_summary = "Triage complete. Review details."
        insufficient = "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE" in analysis_text
        
        bm = re.search(r'(?:## Breakpoint Summary|## \d\.\s*Breakpoint Summary|## \d\.\s*Executive Defect Summary|## Executive Defect Summary|## \d\.\s*Root Cause Summary|## Root Cause Summary)\s*\n*>\s*(.*)', analysis_text)
        if bm:
            breakpoint_summary = bm.group(1).strip()
        elif insufficient:
            breakpoint_summary = "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE"
            
        triage_summaries.append({
            'test_id': test_id,
            'category': f['category'],
            'suite': f['suite'],
            'breakpoint': breakpoint_summary,
            'insufficient': insufficient,
            'report_link': f"./{device_id}/{test_id}/triage_analysis.md"
        })
        
    # 8. Compile consolidated triage_summary_report.md
    print("\nCompiling site-specific Triage Summary Report...")
    root_report_path = os.path.join(MANTIS_DIR, "out", "diagnose", clean_target, site_id, "triage_summary_report.md")
    os.makedirs(os.path.dirname(root_report_path), exist_ok=True)
    
    sum_md = []
    sum_md.append("# Mantis AI Diagnostics: Triage Summary Report 🦗👁️")
    sum_md.append(f"**Target Project**: `{target}`  ")
    sum_md.append(f"**Site ID**: `{site_id}`  ")
    sum_md.append(f"**Triage executed at**: `{datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}`  ")
    sum_md.append("\n---")
    
    total_checked = len(runs_to_triage)
    insufficient_count = len([s for s in triage_summaries if s['insufficient']])
    diagnosed_count = total_checked - insufficient_count
    
    sum_md.append("## Triage Dashboard")
    sum_md.append(f"- **Total Failed Test Cases Scanned**: `{total_checked}`")
    sum_md.append(f"- **Successfully Diagnosed (Isolated Breakpoint)**: `{diagnosed_count}`")
    sum_md.append(f"- **Insufficient Information (Guardrail Triggered)**: `{insufficient_count}`")
    sum_md.append("\n---")
    
    sum_md.append("## 📋 Failed Test Diagnostics Breakdown")
    sum_md.append("| Test Case | Suite | Category | Breakpoint & Root Cause Isolation Summary | Link to Analysis |")
    sum_md.append("| :--- | :--- | :--- | :--- | :--- |")
    for s in triage_summaries:
        sum_md.append(f"| `{s['test_id']}` | `{s['suite']}` | `{s['category']}` | {s['breakpoint']} | [View Analysis]({s['report_link']}) |")
        
    sum_md.append("\n---")
    
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
