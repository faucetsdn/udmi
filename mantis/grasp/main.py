#!/usr/bin/env python3
import argparse
import glob
import os
import shutil
import subprocess
import sys

# Resolve root directory
MANTIS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UDMI_ROOT = os.path.dirname(MANTIS_DIR)

# Import from local package
from .analyzer import RunAnalyzer
from .reporter import MantisReporter

def run_command(args, cwd=UDMI_ROOT):
    """Helper to execute shell commands and handle failures cleanly."""
    print(f"Executing: {' '.join(args)}")
    result = subprocess.run(args, cwd=cwd, stdout=sys.stdout, stderr=sys.stderr)
    return result.returncode

def main():
    parser = argparse.ArgumentParser(
        description="Project Mantis - Grasp stability and flakiness metric analyzer",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--bundles-dir", required=True, help="Path to folder containing sharded/local test bundles (zips or tgzs)")
    parser.add_argument("--target", default="//mqtt/localhost", help="Target project spec (default: //mqtt/localhost)")
    parser.add_argument("--phase", choices=["before", "after"], default="before", help="Exercise phase: before or after stabilization")
    parser.add_argument("--output-dir", default="out/mantis", help="Output directory for reports (default: out/mantis)")

    args = parser.parse_args()

    # Resolve absolute paths
    bundles_path = os.path.abspath(args.bundles_dir)
    output_dir = os.path.abspath(args.output_dir)
    
    if not os.path.exists(bundles_path):
        print(f"Error: Specified --bundles-dir does not exist: '{args.bundles_dir}'", file=sys.stderr)
        sys.exit(1)

    os.makedirs(output_dir, exist_ok=True)

    clean_target = args.target.replace("/", "_").replace("+", "_").strip("_")
    phase_dir = os.path.join(output_dir, f"{args.phase}_{clean_target}")
    os.makedirs(phase_dir, exist_ok=True)

    analyzer = RunAnalyzer(udmi_root=UDMI_ROOT)
    reporter = MantisReporter(target=args.target, phase=args.phase, output_dir=output_dir)

    run_analyses = []

    # ==========================================
    # IMPORT & ANALYZE SHARDED/LOCAL TEST BUNDLES
    # ==========================================
    print(f"=== Running in Mantis Grasp ANALYSIS mode ===")
    print(f"Target: {args.target} | Phase: {args.phase}")
    print(f"Scanning directory for bundles: {bundles_path}")
    
    # Support both zip and tgz packages
    artifacts = sorted(glob.glob(os.path.join(bundles_path, "*udmi-support_*")) + 
                       glob.glob(os.path.join(bundles_path, "*_udmi-support_*.tgz")))
    
    if not artifacts:
        print(f"Error: No udmi-support artifacts or packages found in {bundles_path}", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(artifacts)} run packages to evaluate.")

    for i, art_path in enumerate(artifacts, start=1):
        print(f"\n--- Processing Run Package {i} of {len(artifacts)}: {os.path.basename(art_path)} ---")
        
        # 1. Extract and consolidate support package
        cmd_status = run_command(["bin/support_process", art_path])
        if cmd_status != 0:
            print(f"Warning: bin/support_process returned non-zero exit code {cmd_status} for {art_path}", file=sys.stderr)
            
        # 2. Resolve outputs consolidated by bin/support_process
        seq_out = os.path.join(UDMI_ROOT, "out/sequencer.out")
        item_out = os.path.join(UDMI_ROOT, "out/test_itemized.out")

        # Save raw output to run backups
        run_backup_dir = os.path.join(phase_dir, f"run_{i}")
        os.makedirs(run_backup_dir, exist_ok=True)

        if os.path.exists(seq_out):
            shutil.copy(seq_out, os.path.join(run_backup_dir, "sequencer.out"))
        if os.path.exists(item_out):
            shutil.copy(item_out, os.path.join(run_backup_dir, "test_itemized.out"))

        # 3. Parse and analyze outputs
        analysis = analyzer.analyze_run(
            sequencer_out_path=seq_out if os.path.exists(seq_out) else None,
            itemized_out_path=item_out if os.path.exists(item_out) else None
        )
        
        if analysis:
            run_analyses.append(analysis)
            print(f"Parsed {len(analysis)} test case results from Run {i}.")
        else:
            print(f"Warning: No test results could be parsed for Run {i}.", file=sys.stderr)

    # ==========================================
    # AGGREGATE METRICS & GENERATE REPORT
    # ==========================================
    if not run_analyses:
        print("\nError: No test results were evaluated or parsed. Report generation aborted.", file=sys.stderr)
        sys.exit(1)

    print(f"\n=== Aggregating stability metrics across {len(run_analyses)} runs ===")
    aggregates = analyzer.aggregate_runs(run_analyses)
    
    reporter.save_report(aggregates)
    
    print("\n🎉 Project Mantis Grasp analysis complete. Metric aggregation finished successfully!")

if __name__ == "__main__":
    main()
