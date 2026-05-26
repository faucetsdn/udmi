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
from .analyzer import RunAnalyzer
from .reporter import MantisReporter

def find_failing_shard_for_test(test_name, suite):
    """Scans all sharded out folders to find which shard actually executed and failed the test."""
    sharded_out_dirs = glob.glob(os.path.join(UDMI_ROOT, "out_*"))
    for sod in sorted(sharded_out_dirs):
        suffix = os.path.basename(sod).replace("out_", "")
        
        file_name = "test_itemized.out" if suite == 'itemized' else "sequencer.out.combined"
        out_path = os.path.join(sod, file_name)
        
        if os.path.exists(out_path):
            with open(out_path, 'r') as f:
                for line in f:
                    if test_name in line:
                        tokens = line.split()
                        if tokens and tokens[0].isdigit():
                            tokens = tokens[1:]
                        if len(tokens) > 1 and tokens[0] in ["RESULT", "CPBLTY"] and tokens[1] == "fail":
                            return suffix
                            
    # Fallback to first shard that executed the test
    for sod in sorted(sharded_out_dirs):
        suffix = os.path.basename(sod).replace("out_", "")
        file_name = "test_itemized.out" if suite == 'itemized' else "sequencer.out.combined"
        out_path = os.path.join(sod, file_name)
        if os.path.exists(out_path):
            with open(out_path, 'r') as f:
                for line in f:
                    if test_name in line:
                        return suffix
                        
    return ""

def resolve_triage_logs_for_test(test_name, suite, shard_suffix):
    """Resolves the exact sharded log files (sequence_log, pubber_log, udmis_log) for a test case."""
    shard_out = f"out_{shard_suffix}"
    shard_site = f"sites/udmi_site_model_{shard_suffix}"
    
    seq_log = ""
    pubber_log = ""
    udmis_log = ""
    
    # 1. Resolve udmis log
    for name in ["udmis.log.combined", "udmis.log", "registrar.log"]:
        path = os.path.join(shard_out, name)
        if os.path.exists(os.path.join(UDMI_ROOT, path)):
            udmis_log = path
            break
            
    if suite == 'itemized':
        # Find prefix index token from test_itemized.out (e.g. 24)
        itemized_out_path = os.path.join(UDMI_ROOT, shard_out, "test_itemized.out")
        prefix = ""
        if os.path.exists(itemized_out_path):
            with open(itemized_out_path, 'r') as f:
                for line in f:
                    tokens = line.split()
                    if tokens and tokens[0].isdigit():
                        # Standard: <prefix> RESULT <outcome> <category> <test_name> ...
                        if len(tokens) > 4 and tokens[4] == test_name:
                            prefix = tokens[0]
                            break
                            
        if prefix:
            path_seq = os.path.join(shard_out, f"sequencer.log-{prefix}")
            if os.path.exists(os.path.join(UDMI_ROOT, path_seq)):
                seq_log = path_seq
            path_pub = os.path.join(shard_out, f"pubber.log-{prefix}")
            if os.path.exists(os.path.join(UDMI_ROOT, path_pub)):
                pubber_log = path_pub
                
    if not seq_log:
        # Fallback for sequencer tests or if prefix resolver fails: find sequence.log under sites/udmi_site_model_<shard>/out-seq/devices/<device>/tests/<test_name>/sequence.log
        device_id = "AHU-1"
        site_path = os.path.join(UDMI_ROOT, shard_site)
        dev_glob = glob.glob(os.path.join(site_path, "out*/devices/*"))
        if dev_glob:
            device_id = os.path.basename(dev_glob[0])
            
        seq_glob = glob.glob(os.path.join(site_path, f"out*/devices/{device_id}/tests/{test_name}/sequence.log"))
        if seq_glob:
            seq_log = os.path.relpath(seq_glob[0], UDMI_ROOT)
            
    if not pubber_log:
        # Fallback for pubber log (global shard pubber log)
        for name in ["pubber.log.combined", "pubber.log"]:
            path = os.path.join(shard_out, name)
            if os.path.exists(os.path.join(UDMI_ROOT, path)):
                pubber_log = path
                break
                
    return seq_log, pubber_log, udmis_log


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
                    
            # 3. Copy global out/ and sites/udmi_site_model/
            src_global_site = os.path.join(UDMI_ROOT, "sites/udmi_site_model")
            if os.path.exists(src_global_site):
                try:
                    shutil.copytree(src_global_site, os.path.join(run_backup_dir, "sites/udmi_site_model"), dirs_exist_ok=True)
                except Exception:
                    pass
                    
            src_global_out = os.path.join(UDMI_ROOT, "out")
            if os.path.exists(src_global_out):
                try:
                    shutil.copytree(src_global_out, os.path.join(run_backup_dir, "out"), dirs_exist_ok=True)
                except Exception:
                    pass

            # Parse and analyze outputs first to discover failed tests
            analysis = analyzer.analyze_run(
                sequencer_out_path=seq_out if os.path.exists(seq_out) else None,
                itemized_out_path=item_out if os.path.exists(item_out) else None
            )
            
            # Resolve device ID dynamically
            device_id = "AHU-1"
            for ssd in sorted(sharded_site_dirs):
                dev_glob = glob.glob(os.path.join(ssd, "out*/devices/*"))
                if dev_glob:
                    device_id = os.path.basename(dev_glob[0])
                    break
                    
            # 4. Map failed test cases to their exact sharded log sources
            if analysis:
                for ukey, val in analysis.items():
                    if val['outcome'] == 'fail':
                        test_name = val['test_name']
                        suite_type = val['test_suite']
                        
                        suffix = find_failing_shard_for_test(test_name, suite_type)
                        if suffix:
                            seq_log, pub_log, udm_log = resolve_triage_logs_for_test(test_name, suite_type, suffix)
                            if seq_log:
                                # Construct rich metadata pointing to precise sharded log paths
                                triage_metadata[test_name] = {
                                    "sequence_log": seq_log,
                                    "sequence_md": seq_log.replace(".log", ".md") if "out-seq" in seq_log else "",
                                    "udmis_log": udm_log,
                                    "pubber_log": pub_log
                                }
                                print(f"Successfully resolved and sharded logs for test '{test_name}':")
                                print(f"  ├─ sequence_log: {seq_log}")
                                print(f"  ├─ pubber_log: {pub_log}")
                                print(f"  └─ udmis_log: {udm_log}")

            triage_metadata_path = os.path.join(run_backup_dir, "triage_metadata.json")
            with open(triage_metadata_path, 'w', encoding='utf-8') as fm:
                json.dump(triage_metadata, fm, indent=2)

            # Parse and analyze outputs
            analysis = analyzer.analyze_run(
                sequencer_out_path=seq_out if os.path.exists(seq_out) else None,
                itemized_out_path=item_out if os.path.exists(item_out) else None
            )
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
