#!/usr/bin/env python3
import argparse
import glob
import os
import shutil
import subprocess
import sys
from datetime import datetime

# Resolve root directory
MANTIS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UDMI_ROOT = os.path.dirname(MANTIS_DIR)

# Insert mantis src to path
sys.path.insert(0, os.path.join(MANTIS_DIR, "src"))
from analyzer import RunAnalyzer
from reporter import MantisReporter

def run_command(args, cwd=UDMI_ROOT):
    """Helper to execute shell commands and handle failures cleanly."""
    print(f"Executing: {' '.join(args)}")
    result = subprocess.run(args, cwd=cwd, stdout=sys.stdout, stderr=sys.stderr)
    return result.returncode

def clean_pubber_processes():
    """Kill any lingering pubber Java processes to guarantee environment isolation."""
    print("Cleaning up lingering pubber processes...")
    try:
        # Get matching process IDs
        ps_output = subprocess.check_output(["ps", "ax"], text=True)
        pids_to_kill = []
        for line in ps_output.splitlines():
            if "pubber" in line and "java" in line:
                pid = line.strip().split()[0]
                pids_to_kill.append(pid)
                
        if pids_to_kill:
            print(f"Killing lingering pubber PIDs: {', '.join(pids_to_kill)}")
            for pid in pids_to_kill:
                subprocess.run(["kill", "-9", pid], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception as e:
        print(f"Warning: failed to clean pubber processes: {e}", file=sys.stderr)

def main():
    parser = argparse.ArgumentParser(
        description="Project Mantis - Sequencer Flakiness & Stability Predator",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--target", required=True, help="Target project string (e.g., //mqtt/localhost)")
    parser.add_argument("--iterations", type=int, default=10, help="Number of iterations to execute locally (default: 10)")
    parser.add_argument("--phase", choices=["before", "after"], default="before", help="Exercise phase: before or after stabilization")
    parser.add_argument("--suite", choices=["sequencer", "itemized", "both"], default="both", help="Test suite to run (default: both)")
    parser.add_argument("--tests", help="Comma-separated list of specific sequencer tests to run (e.g. valid_serial_no,pointset_publish)")
    parser.add_argument("--github-dir", help="Path to folder with downloaded sharded GitHub Actions run support packages (zips or tgzs)")
    parser.add_argument("--output-dir", default="out/mantis", help="Output directory for reports (default: out/mantis)")

    args = parser.parse_args()

    # Resolve absolute output dir
    output_dir = os.path.abspath(args.output_dir)
    os.makedirs(output_dir, exist_ok=True)

    clean_target = args.target.replace("/", "_").replace("+", "_").strip("_")
    phase_dir = os.path.join(output_dir, f"{args.phase}_{clean_target}")
    os.makedirs(phase_dir, exist_ok=True)

    analyzer = RunAnalyzer(udmi_root=UDMI_ROOT)
    reporter = MantisReporter(target=args.target, phase=args.phase, output_dir=output_dir)

    run_analyses = []

    # ==========================================
    # MODE A: IMPORT GITHUB ARTIFACTS
    # ==========================================
    if args.github_dir:
        github_path = os.path.abspath(args.github_dir)
        print(f"=== Running in GITHUB ACTIONS ARTIFACTS IMPORT mode ===")
        print(f"Scanning directory: {github_path}")
        
        # Support both zip (directly from download-artifact) and tgz (inside zip, or individually)
        artifacts = sorted(glob.glob(os.path.join(github_path, "*udmi-support_*")) + 
                           glob.glob(os.path.join(github_path, "*_udmi-support_*.tgz")))
        
        if not artifacts:
            print(f"Error: No udmi-support artifacts found in {github_path}", file=sys.stderr)
            sys.exit(1)

        print(f"Found {len(artifacts)} run packages to evaluate.")

        for i, art_path in enumerate(artifacts, start=1):
            print(f"\n--- Processing GitHub Run Package {i} of {len(artifacts)}: {os.path.basename(art_path)} ---")
            
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
    # MODE B: LOCAL EXECUTION LOOP
    # ==========================================
    else:
        print(f"=== Running in LOCAL EXECUTION LOOP mode ===")
        print(f"Target: {args.target} | Iterations: {args.iterations} | Suite: {args.suite}")
        if args.tests:
            print(f"Selective Sequencer Tests: {args.tests}")
            if args.suite == "itemized" or args.suite == "both":
                print("Note: Selective test execution (--tests) only applies to the sequencer suite. Itemized suite will run fully.", file=sys.stderr)

        # Validate local startup if running local target
        if "localhost" in args.target:
            print("Checking if local mosquitto is running...")
            # Check standard mosquitto port (1883)
            import socket
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(1)
            result = sock.connect_ex(('127.0.0.1', 1883))
            sock.close()
            if result != 0:
                print("\n⚠️ Warning: Local broker (mosquitto) port 1883 is not open.")
                print("Please make sure you run local services first:")
                print(f"  bin/start_local sites/udmi_site_model {args.target}\n")

        # Build validator/pubber once before loops to prevent race conditions
        print("Prefetching and building components once...")
        run_command(["pubber/bin/build"])
        run_command(["validator/bin/build"])

        specific_tests = args.tests.split(",") if args.tests else []

        for i in range(1, args.iterations + 1):
            print(f"\n=============================================================")
            print(f"🚀 Starting Local Iteration {i} of {args.iterations} at {datetime.now().strftime('%H:%M:%S')}")
            print(f"=============================================================")
            
            clean_pubber_processes()

            # 1. Run Sequencer Suite
            if args.suite in ["sequencer", "both"]:
                print("\n--- Executing Sequencer Suite ---")
                # 1a. Sequence tests clean
                seq_clean_args = ["bin/test_sequencer", "clean", "nocheck", args.target] + specific_tests
                run_command(seq_clean_args)
                
                # 1b. Sequence tests alpha
                seq_alpha_args = ["bin/test_sequencer", "alpha", "nocheck", args.target] + specific_tests
                run_command(seq_alpha_args)

            # 2. Run Itemized Suite
            if args.suite in ["itemized", "both"]:
                print("\n--- Executing Itemized Suite ---")
                item_args = ["bin/test_itemized", args.target]
                run_command(item_args)

            # 3. Backup outputs
            seq_out = os.path.join(UDMI_ROOT, "out/sequencer.out")
            item_out = os.path.join(UDMI_ROOT, "out/test_itemized.out")

            run_backup_dir = os.path.join(phase_dir, f"run_{i}")
            os.makedirs(run_backup_dir, exist_ok=True)

            if os.path.exists(seq_out):
                shutil.copy(seq_out, os.path.join(run_backup_dir, "sequencer.out"))
            if os.path.exists(item_out):
                shutil.copy(item_out, os.path.join(run_backup_dir, "test_itemized.out"))

            # 4. Parse results
            analysis = analyzer.analyze_run(
                sequencer_out_path=seq_out if os.path.exists(seq_out) else None,
                itemized_out_path=item_out if os.path.exists(item_out) else None
            )
            
            if analysis:
                run_analyses.append(analysis)
                print(f"Parsed {len(analysis)} test case results from Iteration {i}.")
            else:
                print(f"Warning: No test results could be parsed for Iteration {i}.", file=sys.stderr)

            # Cleanup processes after iteration
            clean_pubber_processes()

    # ==========================================
    # AGGREGATE METRICS & GENERATE REPORT
    # ==========================================
    if not run_analyses:
        print("\nError: No test results were evaluated or parsed. Report generation aborted.", file=sys.stderr)
        sys.exit(1)

    print(f"\n=== Aggregating stability metrics across {len(run_analyses)} runs ===")
    aggregates = analyzer.aggregate_runs(run_analyses)
    
    reporter.save_report(aggregates)
    
    print("\n🎉 Project Mantis execution complete. Ruthless hunting of bugs finished successfully!")

if __name__ == "__main__":
    main()
