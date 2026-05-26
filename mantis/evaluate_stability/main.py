#!/usr/bin/env python3
import argparse
import glob
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
    """Recursively copies sequence logs from src_dir to dest_dir, preserving failed/longer runs if collisions occur."""
    if not os.path.exists(src_dir):
        return
    os.makedirs(dest_dir, exist_ok=True)
    
    for root, dirs, files in os.walk(src_dir):
        rel_path = os.path.relpath(root, src_dir)
        dest_root = os.path.abspath(os.path.join(dest_dir, rel_path))
        
        for d in dirs:
            os.makedirs(os.path.join(dest_root, d), exist_ok=True)
            
        for f in files:
            src_file = os.path.join(root, f)
            dest_file = os.path.join(dest_root, f)
            
            if os.path.exists(dest_file) and f == "sequence.log":
                try:
                    with open(src_file, 'r', encoding='utf-8', errors='replace') as sf:
                        src_content = sf.read()
                    with open(dest_file, 'r', encoding='utf-8', errors='replace') as df:
                        dest_content = df.read()
                        
                    src_has_fail = "RESULT fail" in src_content or "ERROR" in src_content
                    dest_has_fail = "RESULT fail" in dest_content or "ERROR" in dest_content
                    
                    if src_has_fail and not dest_has_fail:
                        shutil.copy2(src_file, dest_file)
                    elif not src_has_fail and dest_has_fail:
                        continue
                    elif len(src_content) > len(dest_content):
                        shutil.copy2(src_file, dest_file)
                except Exception:
                    try: shutil.copy2(src_file, dest_file)
                    except Exception: pass
            else:
                try: shutil.copy2(src_file, dest_file)
                except Exception: pass

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

            # Copy sequence logs to make the run backup self-contained
            backup_out = os.path.join(run_backup_dir, "out")
            if os.path.exists(backup_out):
                shutil.rmtree(backup_out)
                
            sharded_site_dirs = glob.glob(os.path.join(UDMI_ROOT, "sites", "udmi_site_model_*"))
            copied_sharded = False
            
            if sharded_site_dirs:
                os.makedirs(backup_out, exist_ok=True)
                for ssd in sorted(sharded_site_dirs):
                    # Glob all out* directories inside this sharded directory (e.g. out, out-seq, out-24)
                    shard_out_dirs = glob.glob(os.path.join(ssd, "out*"))
                    for od in shard_out_dirs:
                        if os.path.isdir(od):
                            smart_copy_sequence_logs(od, backup_out)
                            copied_sharded = True
                        
            if not copied_sharded:
                site_model_dir = os.path.join(UDMI_ROOT, "sites/udmi_site_model")
                if os.path.exists(site_model_dir):
                    out_dirs = glob.glob(os.path.join(site_model_dir, "out*"))
                    if out_dirs:
                        os.makedirs(backup_out, exist_ok=True)
                        for od in sorted(out_dirs):
                            if os.path.isdir(od):
                                smart_copy_sequence_logs(od, backup_out)
                                copied_sharded = True

            # Copy global UDMIS and Pubber logs
            global_log_dirs = [os.path.join(UDMI_ROOT, "out")] + glob.glob(os.path.join(UDMI_ROOT, "out_*"))
            for gld in global_log_dirs:
                if os.path.isdir(gld):
                    suffix = os.path.basename(gld).replace("out", "").strip("_")
                    dest_udmis = "udmis.log" if not suffix else f"udmis_{suffix}.log"
                    dest_pubber = "pubber.log" if not suffix else f"pubber_{suffix}.log"
                    
                    src_udmis = os.path.join(gld, "udmis.log")
                    src_pubber = os.path.join(gld, "pubber.log")
                    
                    if os.path.exists(src_udmis):
                        shutil.copy(src_udmis, os.path.join(run_backup_dir, dest_udmis))
                    if os.path.exists(src_pubber):
                        shutil.copy(src_pubber, os.path.join(run_backup_dir, dest_pubber))
                        
                    if suffix and suffix.isdigit() and int(suffix) == i - 1:
                        if os.path.exists(src_udmis):
                            shutil.copy(src_udmis, os.path.join(run_backup_dir, "udmis.log"))
                        if os.path.exists(src_pubber):
                            shutil.copy(src_pubber, os.path.join(run_backup_dir, "pubber.log"))

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
