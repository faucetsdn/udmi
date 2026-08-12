#!/usr/bin/env python3
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime

# Resolve root directory
SRC_UTIL_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.dirname(SRC_UTIL_DIR)
MANTIS_DIR = os.path.dirname(SRC_DIR)
UDMI_ROOT = os.path.dirname(os.path.dirname(MANTIS_DIR))

# Import from local package
from .analyzer import RunAnalyzer, TestResult
from .reporter import MantisReporter


from mantis.src.util.logging import Tee


from app.resolver import UDMILogResolver


_resolver = None

def get_resolver(udmi_root):
    global _resolver
    if _resolver is None:
        _resolver = UDMILogResolver(udmi_root)
    return _resolver

def discover_active_site_name(UDMI_ROOT, run_dir=None):
    """Helper to dynamically discover the active site model folder name under sites/."""
    return get_resolver(UDMI_ROOT).discover_active_site_name(run_dir)

def resolve_sharded_itemized_logs(test_name, occurrence_idx, UDMI_ROOT, device_id=None):
    """Reads the consolidated test_itemized.out, extracts the prefix token, and resolves exact sharded logs."""
    return get_resolver(UDMI_ROOT).resolve_sharded_itemized_logs(
        run_dir=UDMI_ROOT,
        test_name=test_name,
        occurrence_idx=occurrence_idx,
        device_id=device_id
    )

def resolve_sharded_sequencer_logs(test_name, UDMI_ROOT, device_id=None, expect_fail=True):
    """Finds which sites/udmi_site_model_<shard> contains the sequence.log that failed for normal sequencer tests."""
    return get_resolver(UDMI_ROOT).resolve_sharded_sequencer_logs(
        run_dir=UDMI_ROOT,
        test_name=test_name,
        device_id=device_id,
        expect_fail=expect_fail
    )


def run_command(args, cwd=UDMI_ROOT):
    """Helper to execute shell commands, capture output, and print/mirror in real-time."""
    # Resolve the executable relative to cwd if it is a relative path and exists there
    executable = args[0]
    if not os.path.isabs(executable):
        abs_executable = os.path.join(cwd, executable)
        if os.path.exists(abs_executable):
            args = [abs_executable] + args[1:]

    print(f"Executing: {' '.join(args)}")
    try:
        # Merge stderr into stdout so they are captured in order
        process = subprocess.Popen(
            args,
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )

        # Read stdout line-by-line in real-time and write to sys.stdout (which calls Tee.write)
        for line in iter(process.stdout.readline, ""):
            sys.stdout.write(line)

        process.stdout.close()
        returncode = process.wait()
        return returncode
    except Exception as e:
        print(f"Error executing command {' '.join(args)}: {e}", file=sys.stderr)
        return 1


def extract_target_from_config(run_dir):
    """Helper to perfectly reconstruct the test target from sequencer config."""
    config_path = os.path.join(run_dir, "out", "sequencer_config.json")
    if not os.path.exists(config_path):
        return "//mqtt/localhost"
        
    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
            
        provider = config.get("iot_provider") or "mqtt"
        project = config.get("project_id") or "localhost"
        namespace = config.get("udmi_namespace")
        user = config.get("user_name")
        
        target = f"//{provider}/{project}"
        if namespace:
            target += f"/{namespace}"
        if user:
            target += f"+{user}"
        return target
    except Exception as e:
        print(f"Error extracting target from {config_path}: {e}", file=sys.stderr)
        return "//mqtt/localhost"


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


def evaluate_single_directory(test_runs_path, target, output_dir, analyzer):
    """Evaluates stability for a single directory containing run_* subdirectories."""
    print(f"\n=============================================================")
    print(f"Evaluating Directory: {os.path.basename(test_runs_path)}")
    print(f"=============================================================")

    run_analyses = []
    run_dirs = sorted(glob.glob(os.path.join(test_runs_path, "run_*")))

    if not run_dirs:
        print(f"Warning: No run_* directories found in {test_runs_path}. Please run prepare_bundles first.", file=sys.stderr)
        return None

    print(f"Found {len(run_dirs)} existing run directories.")
    for i, run_dir in enumerate(run_dirs, start=1):
        seq_out = os.path.join(run_dir, "sequencer.out")
        item_out = os.path.join(run_dir, "test_itemized.out")

        analysis = analyzer.analyze_run(
            sequencer_out_path=seq_out if os.path.exists(seq_out) else None,
            itemized_out_path=item_out if os.path.exists(item_out) else None
        )
        if analysis:
            # We still need to create triage_metadata for this run_dir
            triage_metadata = {}
            for ukey, val in analysis.items():
                if val['outcome'] == 'fail':
                    test_name = val['test_name']
                    suite_type = val['test_suite']
                    occurrence_idx = val['occurrence']
                    device_id = val.get('device_id', '')
                    
                    resolver = UDMILogResolver(UDMI_ROOT)
                    seq_log, pub_log, udm_log = "", "", ""
                    if suite_type == 'itemized':
                        seq_log, pub_log, udm_log, resolved_device, pubber_opts = resolver.resolve_sharded_itemized_logs(
                            run_dir=run_dir,
                            test_name=test_name,
                            occurrence_idx=occurrence_idx,
                            device_id=device_id
                        )
                    else:
                        seq_log, pub_log, udm_log = resolver.resolve_sharded_sequencer_logs(
                            run_dir=run_dir,
                            test_name=test_name,
                            device_id=device_id
                        )
                    
                    if seq_log:
                        if test_name not in triage_metadata:
                            triage_metadata[test_name] = []
                        triage_metadata[test_name].append({
                            "sequence_log": seq_log,
                            "sequence_md": seq_log.replace(".log", ".md") if os.path.exists(os.path.join(UDMI_ROOT, seq_log.replace(".log", ".md"))) else "",
                            "pubber_log": pub_log,
                            "udmis_log": udm_log
                        })

            triage_metadata_path = os.path.join(run_dir, "triage_metadata.json")
            with open(triage_metadata_path, 'w', encoding='utf-8') as fm:
                json.dump(triage_metadata, fm, indent=2)

            run_analyses.append(analysis)

    if not run_analyses:
        print(f"Warning: No test results could be parsed for {os.path.basename(test_runs_path)}", file=sys.stderr)
        return None

    aggregates = analyzer.aggregate_runs(run_analyses)

    if aggregates:
        manifest_failures = []
        for ukey, val in aggregates.items():
            if val['fail_count'] > 0:
                test_name = val['test_name']
                suite_type = val['test_suite']
                occurrence_idx = val['occurrence']
                device_id = val.get('device_id', '')
                is_flaky = val['flaky']

                failed_run_idx = -1
                success_run_idx = -1
                
                for idx, run_analysis in enumerate(run_analyses, start=1):
                    if ukey in run_analysis:
                        run_val = run_analysis[ukey]
                        if not run_val['matched'] and failed_run_idx == -1:
                            failed_run_idx = idx
                        if run_val['matched'] and success_run_idx == -1 and is_flaky:
                            success_run_idx = idx

                if failed_run_idx != -1:
                    run_backup_dir = os.path.join(test_runs_path, f"run_{failed_run_idx}")
                    resolver = UDMILogResolver(UDMI_ROOT)
                    
                    resolved_device = device_id
                    pubber_opts = ""
                    if suite_type == 'itemized':
                        seq_log, pub_log, udm_log, resolved_device, pubber_opts = resolver.resolve_sharded_itemized_logs(
                            run_dir=run_backup_dir,
                            test_name=test_name,
                            occurrence_idx=occurrence_idx,
                            device_id=device_id
                        )
                    else:
                        seq_log, pub_log, udm_log = resolver.resolve_sharded_sequencer_logs(
                            run_dir=run_backup_dir,
                            test_name=test_name,
                            device_id=device_id
                        )

                    failed_run_paths = {
                        "sequence_log": seq_log,
                        "sequence_md": seq_log.replace(".log", ".md") if seq_log and os.path.exists(os.path.join(UDMI_ROOT, seq_log.replace(".log", ".md"))) else "",
                        "pubber_log": pub_log,
                        "udmis_log": udm_log
                    }

                    success_run_paths = None
                    if is_flaky and success_run_idx != -1:
                        success_run_dir = os.path.join(test_runs_path, f"run_{success_run_idx}")
                        if suite_type == 'itemized':
                            s_seq_log, s_pub_log, s_udm_log, _, _ = resolver.resolve_sharded_itemized_logs(
                                run_dir=success_run_dir,
                                test_name=test_name,
                                occurrence_idx=occurrence_idx,
                                device_id=device_id
                            )
                        else:
                            s_seq_log, s_pub_log, s_udm_log = resolver.resolve_sharded_sequencer_logs(
                                run_dir=success_run_dir,
                                test_name=test_name,
                                device_id=device_id,
                                expect_fail=False
                            )
                        
                        if s_seq_log:
                            success_run_paths = {
                                "sequence_log": s_seq_log,
                                "sequence_md": s_seq_log.replace(".log", ".md") if os.path.exists(os.path.join(UDMI_ROOT, s_seq_log.replace(".log", ".md"))) else "",
                                "pubber_log": s_pub_log,
                                "udmis_log": s_udm_log
                            }

                    manifest_failures.append({
                        "failure_id": f"{test_name}-{resolved_device}-{occurrence_idx}",
                        "test_name": test_name,
                        "suite": suite_type,
                        "category": val['category'],
                        "device_id": resolved_device,
                        "pubber_options": pubber_opts,
                        "occurrence_index": occurrence_idx,
                        "is_flaky": is_flaky,
                        "run_directory": os.path.relpath(run_backup_dir, UDMI_ROOT),
                        "logs": {
                            "failed_run": failed_run_paths,
                            "success_run": success_run_paths
                        }
                    })

        # Calculate dynamic target and site
        active_site = os.path.basename(discover_active_site_name(
            UDMI_ROOT,
            os.path.join(test_runs_path, "run_1") if os.path.exists(os.path.join(test_runs_path, "run_1")) else test_runs_path
        ))
        
        manifest = {
            "metadata": {
                "target_project": target,
                "site_id": active_site,
                "triage_timestamp": datetime.now().isoformat() + "Z"
            },
            "failures": manifest_failures
        }

        manifest_path = os.path.join(test_runs_path, "triage_manifest.json")
        manifest_str = json.dumps(manifest, indent=2)
        with open(manifest_path, 'w', encoding='utf-8') as fm:
            fm.write(manifest_str)
        print(f"Generated dynamic triage manifest: {manifest_path}")

    return aggregates


def main():
    parser = argparse.ArgumentParser(
        description="Project Mantis - Stability and flakiness metric analyzer (evaluate_stability)",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--test-runs", "-i", required=True,
                        help="Primary input directory containing run_* folders to evaluate")
    parser.add_argument("--compare", "-c", nargs="+",
                        help="Additional test-runs directories to compare against chronologically")

    args = parser.parse_args()

    primary_path = os.path.abspath(args.test_runs)
    UDMI_OUT_MANTIS = os.path.join(UDMI_ROOT, "out", "mantis")
    output_dir = UDMI_OUT_MANTIS

    if not os.path.exists(primary_path):
        print(
            f"Error: Specified --test-runs directory does not exist: '{args.test_runs}'",
            file=sys.stderr)
        sys.exit(1)

    # Redirect all outputs to evaluate_stability.log inside the primary path directory
    log_filepath = os.path.join(primary_path, "evaluate_stability.log")
    if sys.stdout.isatty():
        sys.stdout = Tee(sys.stdout, log_filepath)
        sys.stderr = Tee(sys.stderr, log_filepath)

    # Discover target dynamically from config
    first_run = os.path.join(primary_path, "run_1")
    target = extract_target_from_config(first_run) if os.path.exists(first_run) else extract_target_from_config(primary_path)

    analyzer = RunAnalyzer(udmi_root=UDMI_ROOT)
    reporter = MantisReporter(target=target, output_dir=output_dir)

    datasets = []

    # 1. Evaluate Primary Directory
    primary_aggregates = evaluate_single_directory(primary_path, target,
                                                   output_dir, analyzer)
    if primary_aggregates:
        primary_ts = get_directory_timestamp(primary_path)
        reporter.save_single_report(primary_aggregates, primary_ts,
                                    os.path.basename(primary_path),
                                    output_dir=primary_path)
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
                print(
                    f"Warning: Comparison directory does not exist, skipping: '{cmp_dir}'",
                    file=sys.stderr)
                continue

            cmp_aggregates = evaluate_single_directory(cmp_path, target,
                                                       output_dir, analyzer)
            if cmp_aggregates:
                cmp_ts = get_directory_timestamp(cmp_path)
                reporter.save_single_report(cmp_aggregates, cmp_ts,
                                            os.path.basename(cmp_path),
                                            output_dir=cmp_path)
                datasets.append({
                    'name': os.path.basename(cmp_path),
                    'timestamp': cmp_ts,
                    'aggregates': cmp_aggregates
                })

    # 3. Generate Chronological Comparative Report if multiple datasets exist
    if len(datasets) >= 2:
        reporter.save_comparison_report(datasets, output_dir=primary_path)
    else:
        print(
            "\nProject Mantis Stability Evaluation complete.")


if __name__ == "__main__":
    main()
