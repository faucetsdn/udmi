#!/usr/bin/env python3
import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import re
from datetime import datetime

# Resolve root directory
MANTIS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UDMI_ROOT = os.path.dirname(MANTIS_DIR)

# Import from local package
from .analyzer import RunAnalyzer, TestResult
from .reporter import MantisReporter

def discover_active_site_name(UDMI_ROOT):
    """Helper to dynamically discover the active site model folder name under sites/."""
    try:
        sites_dir = os.path.join(UDMI_ROOT, "sites")
        if os.path.exists(sites_dir):
            dirs = [d for d in os.listdir(sites_dir) if d != "udmi_site_model" and os.path.isdir(os.path.join(sites_dir, d))]
            if dirs:
                return dirs[0]
    except Exception:
        pass
    return "udmi_site_model"

def resolve_sharded_itemized_logs(test_name, occurrence_idx, UDMI_ROOT, device_id=None):
    """Reads the consolidated test_itemized.out, extracts the prefix token for the failed occurrence,
    and resolves the exact sharded log files. Automatically falls back to singular non-sharded resolution."""
    itemized_out = os.path.join(UDMI_ROOT, "out/test_itemized.out")
    if not os.path.exists(itemized_out):
        return "", "", ""
        
    prefix = ""
    curr_idx = 0
    with open(itemized_out, 'r') as f:
        for line in f:
            tokens = line.split()
            if tokens and tokens[0].isdigit():
                # Prefix format: e.g. 24
                # Line format: <prefix> RESULT <outcome> <category> <test_name> ...
                if len(tokens) > 4 and tokens[4] == test_name:
                    if curr_idx == occurrence_idx:
                        prefix = tokens[0]
                        break
                    curr_idx += 1
                    
    # If no sharded directories exist (single run mode) or prefix resolution failed
    sharded_out_dirs = glob.glob(os.path.join(UDMI_ROOT, "out_*"))
    dev_pattern = device_id if device_id else "*"
    if not sharded_out_dirs or not prefix:
        # Single-run fallback: resolve logs directly from global out/ and sites/<active_site_name>/
        active_site = discover_active_site_name(UDMI_ROOT)
        site_path = os.path.join(UDMI_ROOT, "sites", active_site)
        
        # Search recursively across all device folders for the sequence log without needing upfront device ID discovery
        seq_glob = glob.glob(os.path.join(site_path, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True)
        seq_log = os.path.relpath(seq_glob[0], UDMI_ROOT) if seq_glob else ""
        
        pub_log = ""
        for name in ["pubber.log.combined", "pubber.log"]:
            p = os.path.join(UDMI_ROOT, "out", name)
            if os.path.exists(p):
                pub_log = os.path.relpath(p, UDMI_ROOT)
                break
                
        udm_log = ""
        for name in ["udmis.log.combined", "udmis.log"]:
            p = os.path.join(UDMI_ROOT, "out", name)
            if os.path.exists(p):
                udm_log = os.path.relpath(p, UDMI_ROOT)
                break
                
        return seq_log, pub_log, udm_log
        
    # Sharded resolution
    for sod in sorted(sharded_out_dirs):
        suffix = os.path.basename(sod).replace("out_", "")
        path_seq = os.path.join(sod, f"sequencer.log-{prefix}")
        if os.path.exists(os.path.join(UDMI_ROOT, path_seq)):
            active_site = discover_active_site_name(UDMI_ROOT)
            shard_site = f"sites/udmi_site_model_{suffix}"
            if not os.path.exists(os.path.join(UDMI_ROOT, shard_site)):
                shard_site = f"sites/{active_site}_{suffix}"
            if not os.path.exists(os.path.join(UDMI_ROOT, shard_site)):
                shard_site = f"sites/{active_site}"
                
            site_path = os.path.join(UDMI_ROOT, shard_site)
            # Search recursively across all device folders for sequence log
            seq_glob = glob.glob(os.path.join(site_path, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True)
            site_seq_log = ""
            if seq_glob:
                site_seq_log = os.path.relpath(seq_glob[0], UDMI_ROOT)
                
            pub_log = os.path.join(sod, f"pubber.log-{prefix}")
            if not os.path.exists(os.path.join(UDMI_ROOT, pub_log)):
                pub_log = os.path.join(sod, "pubber.log")
                
            udm_log = ""
            for name in ["udmis.log.combined", "udmis.log"]:
                p = os.path.join(sod, name)
                if os.path.exists(os.path.join(UDMI_ROOT, p)):
                    udm_log = p
                    break
                    
            resolved_seq = site_seq_log if site_seq_log else os.path.relpath(path_seq, UDMI_ROOT)
            return resolved_seq, os.path.relpath(pub_log, UDMI_ROOT) if os.path.exists(os.path.join(UDMI_ROOT, pub_log)) else "", os.path.relpath(udm_log, UDMI_ROOT) if udm_log else ""
            
    return "", "", ""

def resolve_sharded_sequencer_logs(test_name, UDMI_ROOT, device_id=None):
    """Finds which sites/udmi_site_model_<shard> contains the sequence.log that failed for normal sequencer tests."""
    sharded_site_dirs = glob.glob(os.path.join(UDMI_ROOT, "sites", "udmi_site_model_*"))
    active_site = discover_active_site_name(UDMI_ROOT)
    dev_pattern = device_id if device_id else "*"
    
    if not sharded_site_dirs:
        # Single-run fallback: resolve directly from global sites/<active_site>/
        site_path = os.path.join(UDMI_ROOT, "sites", active_site)
        seq_glob = glob.glob(os.path.join(site_path, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True)
        seq_log = os.path.relpath(seq_glob[0], UDMI_ROOT) if seq_glob else ""
        
        pub_log = ""
        for name in ["pubber.log.combined", "pubber.log"]:
            p = os.path.join(UDMI_ROOT, "out", name)
            if os.path.exists(p):
                pub_log = os.path.relpath(p, UDMI_ROOT)
                break
                
        udm_log = ""
        for name in ["udmis.log.combined", "udmis.log"]:
            p = os.path.join(UDMI_ROOT, "out", name)
            if os.path.exists(p):
                udm_log = os.path.relpath(p, UDMI_ROOT)
                break
                
        return seq_log, pub_log, udm_log
        
    # Sharded resolution
    for ssd in sorted(sharded_site_dirs):
        suffix = os.path.basename(ssd).replace("udmi_site_model_", "")
        seq_glob = glob.glob(os.path.join(ssd, f"**/devices/{dev_pattern}/tests/{test_name}/sequence*.log"), recursive=True)
        if seq_glob:
            seq_log = seq_glob[0]
            try:
                with open(seq_log, 'r', encoding='utf-8', errors='replace') as f:
                    content = f.read()
                if "RESULT fail" in content or "Failed waiting" in content:
                    shard_out = f"out_{suffix}"
                    pub_log = ""
                    for name in ["pubber.log.combined", "pubber.log"]:
                        p = os.path.join(UDMI_ROOT, shard_out, name)
                        if os.path.exists(p):
                            pub_log = os.path.relpath(p, UDMI_ROOT)
                            break
                            
                    udm_log = ""
                    for name in ["udmis.log.combined", "udmis.log"]:
                        p = os.path.join(UDMI_ROOT, shard_out, name)
                        if os.path.exists(p):
                            udm_log = os.path.relpath(p, UDMI_ROOT)
                            break
                            
                    return os.path.relpath(seq_log, UDMI_ROOT), pub_log, udm_log
            except Exception:
                pass
                
    return "", "", ""


def run_command(args, cwd=UDMI_ROOT):
    """Helper to execute shell commands and handle failures cleanly."""
    executable = args[0]
    if not os.path.isabs(executable):
        abs_executable = os.path.join(cwd, executable)
        if os.path.exists(abs_executable):
            args = [abs_executable] + args[1:]
            
    print(f"Executing: {' '.join(args)}")
    result = subprocess.run(args, cwd=cwd, stdout=sys.stdout, stderr=sys.stderr)
    return result.returncode

def detect_target(bundles_dir_path):
    """Helper to auto-detect target from bundles directory name."""
    base = os.path.basename(os.path.normpath(bundles_dir_path))
    clean_name = re.sub(r'_\d{8}_\d{6}$', '', base)
    clean_name = re.sub(r'^(before|after)_', '', clean_name, flags=re.IGNORECASE)
    
    parts = clean_name.split("_", 1)
    if len(parts) == 2:
        return f"//{parts[0]}/{parts[1]}"
    return f"//{clean_name}" if clean_name else "//mqtt/localhost"

def get_directory_timestamp(dir_path):
    """Resolves the chronological timestamp of a directory."""
    base = os.path.basename(os.path.normpath(dir_path))
    m = re.search(r'(\d{8})_(\d{6})', base)
    if m:
        try:
            return datetime.strptime(m.group(0), "%Y%m%d_%H%M%S")
        except Exception:
            pass
    # Fallback to directory modification time
    return datetime.fromtimestamp(os.path.getmtime(dir_path))

def smart_copy_sequence_logs(src_dir, dest_dir):
    """Recursively copies sequence logs from src_dir to dest_dir, preserving failed/longer runs if collisions occur.
    Returns a dictionary mapping test_id to the extracted shard suffix for successfully copied sequence logs."""
    copied_tests = {}
    if not os.path.exists(src_dir):
        return copied_tests
    os.makedirs(dest_dir, exist_ok=True)
    
    # Extract shard suffix from parent folder (e.g. udmi_site_model_5 -> 5)
    parent_name = os.path.basename(os.path.dirname(src_dir))
    m = re.search(r'_(\d+)$', parent_name)
    suffix = m.group(1) if m else ""
    
    for root, dirs, files in os.walk(src_dir):
        rel_path = os.path.relpath(root, src_dir)
        dest_root = os.path.abspath(os.path.join(dest_dir, rel_path))
        
        for d in dirs:
            os.makedirs(os.path.join(dest_root, d), exist_ok=True)
            
        for f in files:
            src_file = os.path.join(root, f)
            dest_file = os.path.join(dest_root, f)
            
            copy_needed = False
            test_name = None
            
            # Extract test case name from rel_path (e.g. devices/AHU-1/tests/broken_config)
            parts = rel_path.split(os.sep)
            if len(parts) >= 4 and parts[-2] == "tests":
                test_name = parts[-1]
                
            if os.path.exists(dest_file) and f == "sequence.log":
                try:
                    with open(src_file, 'r', encoding='utf-8', errors='replace') as sf:
                        src_content = sf.read()
                    with open(dest_file, 'r', encoding='utf-8', errors='replace') as df:
                        dest_content = df.read()
                        
                    src_has_fail = "RESULT fail" in src_content
                    dest_has_fail = "RESULT fail" in dest_content
                    
                    if src_has_fail and not dest_has_fail:
                        copy_needed = True
                    elif not src_has_fail and dest_has_fail:
                        pass
                    elif len(src_content) > len(dest_content):
                        copy_needed = True
                except Exception:
                    copy_needed = True
            elif not os.path.exists(dest_file):
                copy_needed = True
                
            if copy_needed:
                try:
                    shutil.copy2(src_file, dest_file)
                    if test_name and f == "sequence.log":
                        copied_tests[test_name] = suffix
                except Exception:
                    pass
                    
    return copied_tests

def evaluate_single_directory(bundles_path, target, output_dir, analyzer):
    """Unpacks, processes, aggregates, and evaluates stability for a single directory."""
    print(f"\n=============================================================")
    print(f"📊 Evaluating Directory: {os.path.basename(bundles_path)}")
    print(f"=============================================================")
    
    # Save run backups and extracted logs directly in the input bundles directory
    phase_dir = bundles_path
    
    run_analyses = []
    
    # 1. Check if there are already run_* subdirectories in the input directory
    run_dirs = sorted(glob.glob(os.path.join(bundles_path, "run_*")))
    
    if run_dirs:
        print(f"Found {len(run_dirs)} existing run directories. Skipping zip/tgz extraction.")
        for i, run_dir in enumerate(run_dirs, start=1):
            seq_out = os.path.join(run_dir, "sequencer.out")
            item_out = os.path.join(run_dir, "test_itemized.out")
            
            analysis = analyzer.analyze_run(
                sequencer_out_path=seq_out if os.path.exists(seq_out) else None,
                itemized_out_path=item_out if os.path.exists(item_out) else None
            )
            if analysis:
                run_analyses.append(analysis)
    else:
        # Support both zip and tgz packages
        artifacts = sorted(glob.glob(os.path.join(bundles_path, "*udmi-support*")))
        
        if not artifacts:
            print(f"Warning: No udmi-support packages or run_* directories found in {bundles_path}", file=sys.stderr)
            return None

        print(f"Found {len(artifacts)} run packages to evaluate.")
        for i, art_path in enumerate(artifacts, start=1):
            # Extract and consolidate support package
            cmd_status = run_command(["bin/support_process", art_path])
            if cmd_status != 0:
                print(f"Warning: support_process failed with exit code {cmd_status} for {art_path}", file=sys.stderr)
                
            seq_out = os.path.join(UDMI_ROOT, "out/sequencer.out")
            item_out = os.path.join(UDMI_ROOT, "out/test_itemized.out")

            # Save raw output to run backups
            run_backup_dir = os.path.join(phase_dir, f"run_{i}")
            os.makedirs(run_backup_dir, exist_ok=True)

            if os.path.exists(seq_out):
                shutil.copy(seq_out, os.path.join(run_backup_dir, "sequencer.out"))
            if os.path.exists(item_out):
                shutil.copy(item_out, os.path.join(run_backup_dir, "test_itemized.out"))
            
            # Copy all raw sharded directories to run_backup_dir to keep them fully intact and sharded
            print("Unpacking and preserving raw sharded test directories...")
            triage_metadata = {}
            sharded_site_dirs = glob.glob(os.path.join(UDMI_ROOT, "sites", "udmi_site_model_*"))
            sharded_out_dirs = glob.glob(os.path.join(UDMI_ROOT, "out_*"))
            
            # 1. Copy sharded sites/udmi_site_model_*
            for ssd in sharded_site_dirs:
                dest_ssd = os.path.join(run_backup_dir, "sites", os.path.basename(ssd))
                try:
                    shutil.copytree(ssd, dest_ssd, dirs_exist_ok=True)
                except Exception as err:
                    print(f"Warning: failed to copy sharded site {ssd}: {err}", file=sys.stderr)
                    
            # 2. Copy sharded out_* folders
            for sod in sharded_out_dirs:
                dest_sod = os.path.join(run_backup_dir, os.path.basename(sod))
                try:
                    shutil.copytree(sod, dest_sod, dirs_exist_ok=True)
                except Exception as err:
                    print(f"Warning: failed to copy sharded out folder {sod}: {err}", file=sys.stderr)
                    
            # 3. Copy global out/ and discovered active site model folder
            active_site_name = discover_active_site_name(UDMI_ROOT)
            src_global_site = os.path.join(UDMI_ROOT, "sites", active_site_name)
            if os.path.exists(src_global_site):
                try:
                    shutil.copytree(src_global_site, os.path.join(run_backup_dir, "sites", active_site_name), dirs_exist_ok=True)
                except Exception:
                    pass
            src_global_out = os.path.join(UDMI_ROOT, "out")
            if os.path.exists(src_global_out):
                try:
                    shutil.copytree(src_global_out, os.path.join(run_backup_dir, "out"), dirs_exist_ok=True)
                except Exception:
                    pass
            print("Compiling and analyzing results directly from device sequence logs...")
            active_site = discover_active_site_name(UDMI_ROOT)
            site_run_path = os.path.join(run_backup_dir, "sites", active_site)
            print(f"Debug active_site: {active_site}")
            print(f"Debug site_run_path: {site_run_path} (exists: {os.path.exists(site_run_path)})")
            
            raw_results = []
            log_files = glob.glob(os.path.join(site_run_path, "**/sequence*.log"), recursive=True)
            print(f"Debug log_files count: {len(log_files)}")
            
            for lf in log_files:
                try:
                    # Dynamically resolve device_id from filepath
                    device_id = "AHU-1"
                    parts = lf.split("devices/")
                    if len(parts) > 1:
                        device_id = parts[1].split("/")[0]
                        
                    with open(lf, 'r', encoding='utf-8', errors='replace') as f:
                        lines = f.readlines()
                        # Search last 5 lines backwards to locate the RESULT/CPBLTY assertion outcome
                        for line in reversed(lines[-5:]):
                            if "RESULT" in line or "CPBLTY" in line:
                                m = re.search(r'(RESULT|CPBLTY)\s+.*', line)
                                if m:
                                    # Detect suite type dynamically by checking golden expected baseline maps
                                    tokens = m.group(0).split()
                                    if tokens and tokens[0].isdigit():
                                        tokens = tokens[1:]
                                    test_name = tokens[3] if len(tokens) > 3 else ""
                                    
                                    is_itemized = test_name in analyzer.itemized_baseline.expected
                                    res = TestResult(m.group(0), device_id=device_id, is_itemized=is_itemized)
                                    raw_results.append(res)
                                    break
                except Exception as err:
                    print(f"Warning: failed to parse log {lf}: {err}", file=sys.stderr)
                    
            # Separate results by suite to map against their respective baseline occurrences
            item_results = [r for r in raw_results if r.test_name in analyzer.itemized_baseline.expected]
            seq_results = [r for r in raw_results if r.test_name not in analyzer.itemized_baseline.expected]
            print(f"Debug raw_results count: {len(raw_results)}")
            print(f"Debug item_results count: {len(item_results)}")
            print(f"Debug seq_results count: {len(seq_results)}")
            
            analysis = {}
            analysis.update(analyzer.analyze_results(seq_results, test_suite='sequencer'))
            analysis.update(analyzer.analyze_results(item_results, test_suite='itemized'))
            print(f"Debug analysis count: {len(analysis)}")
            
            # 4. Map failed test cases to their exact sharded log sources
            if analysis:
                for ukey, val in analysis.items():
                    if val['outcome'] == 'fail':
                        test_name = val['test_name']
                        suite_type = val['test_suite']
                        occurrence_idx = val['occurrence']
                        
                        device_id = val.get('device_id', '')
                        seq_log, pub_log, udm_log = "", "", ""
                        if suite_type == 'itemized':
                            seq_log, pub_log, udm_log = resolve_sharded_itemized_logs(test_name, occurrence_idx, UDMI_ROOT, device_id=device_id)
                        else:
                            seq_log, pub_log, udm_log = resolve_sharded_sequencer_logs(test_name, UDMI_ROOT, device_id=device_id)
                            
                        if seq_log:
                            if test_name not in triage_metadata:
                                triage_metadata[test_name] = []
                                
                            triage_metadata[test_name].append({
                                "sequence_log": seq_log,
                                "sequence_md": seq_log.replace(".log", ".md") if os.path.exists(os.path.join(UDMI_ROOT, seq_log.replace(".log", ".md"))) else "",
                                "udmis_log": udm_log,
                                "pubber_log": pub_log
                            })
                            print(f"Successfully resolved and sharded logs for test '{test_name}' (occurrence index {occurrence_idx}):")
                            print(f"  ├─ sequence_log: {seq_log}")
                            print(f"  ├─ pubber_log: {pub_log}")
                            print(f"  └─ udmis_log: {udm_log}")

            triage_metadata_path = os.path.join(run_backup_dir, "triage_metadata.json")
            with open(triage_metadata_path, 'w', encoding='utf-8') as fm:
                json.dump(triage_metadata, fm, indent=2)
                
            if analysis:
                run_analyses.append(analysis)
                
    if not run_analyses:
        print(f"Warning: No test results could be parsed for {os.path.basename(bundles_path)}", file=sys.stderr)
        return None
        
    aggregates = analyzer.aggregate_runs(run_analyses)
    return aggregates

def main():
    parser = argparse.ArgumentParser(
        description="Project Mantis - Stability and flakiness metric analyzer (evaluate_stability)",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--bundles-dir", "-i", required=True, help="Primary input bundles directory to evaluate")
    parser.add_argument("--compare", "-c", nargs="+", help="Additional bundles directories to compare against chronologically")
    parser.add_argument("--target", "-t", help="Target project spec (default: auto-detected from bundles-dir)")

    args = parser.parse_args()

    primary_path = os.path.abspath(args.bundles_dir)
    output_dir = os.path.join(MANTIS_DIR, "out")
    
    if not os.path.exists(primary_path):
        print(f"Error: Specified --bundles-dir does not exist: '{args.bundles_dir}'", file=sys.stderr)
        sys.exit(1)

    # Discover target
    detected_target = detect_target(primary_path)
    target = args.target if args.target is not None else detected_target

    analyzer = RunAnalyzer(udmi_root=UDMI_ROOT)
    reporter = MantisReporter(target=target, output_dir=output_dir)

    datasets = []

    # 1. Evaluate Primary Directory
    primary_aggregates = evaluate_single_directory(primary_path, target, output_dir, analyzer)
    if primary_aggregates:
        primary_ts = get_directory_timestamp(primary_path)
        reporter.save_single_report(primary_aggregates, primary_ts, os.path.basename(primary_path))
        datasets.append({
            'name': os.path.basename(primary_path),
            'timestamp': primary_ts,
            'aggregates': primary_aggregates
        })

    # 2. Evaluate Comparison Directories if supplied
    if args.compare:
        for cmp_dir in args.compare:
            cmp_path = os.path.abspath(cmp_dir)
            if not os.path.exists(cmp_path):
                print(f"Warning: Comparison directory does not exist, skipping: '{cmp_dir}'", file=sys.stderr)
                continue
                
            cmp_aggregates = evaluate_single_directory(cmp_path, target, output_dir, analyzer)
            if cmp_aggregates:
                cmp_ts = get_directory_timestamp(cmp_path)
                reporter.save_single_report(cmp_aggregates, cmp_ts, os.path.basename(cmp_path))
                datasets.append({
                    'name': os.path.basename(cmp_path),
                    'timestamp': cmp_ts,
                    'aggregates': cmp_aggregates
                })

    # 3. Generate Chronological Comparative Report if multiple datasets exist
    if len(datasets) >= 2:
        reporter.save_comparison_report(datasets)
    else:
        print("\n🎉 Project Mantis Stability Evaluation complete. Single directory processed successfully!")

if __name__ == "__main__":
    main()
