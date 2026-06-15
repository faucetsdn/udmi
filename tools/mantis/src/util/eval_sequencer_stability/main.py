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


def detect_target(bundles_dir_path):
    """Helper to auto-detect target from bundles directory name."""
    base = os.path.basename(os.path.normpath(bundles_dir_path))
    clean_name = re.sub(r'_\d{8}_\d{6}$', '', base)
    clean_name = re.sub(r'^(before|after)_', '', clean_name,
                        flags=re.IGNORECASE)

    if clean_name == "ci_search":
        return "//mqtt/localhost"

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
                    with open(src_file, 'r', encoding='utf-8',
                              errors='replace') as sf:
                        src_content = sf.read()
                    with open(dest_file, 'r', encoding='utf-8',
                              errors='replace') as df:
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


def process_run_bundle(bundles_path, run_backup_dir, process_target_path, analyzer):
    """Processes a single run segment (zip or segment directory), copies outputs, cleans up, and resolves logs."""
    cmd_status = run_command([os.path.join(MANTIS_DIR, "bin/support_process"), process_target_path])
    if cmd_status != 0:
        print(f"Warning: support_process failed for {process_target_path}", file=sys.stderr)

    os.makedirs(run_backup_dir, exist_ok=True)

    seq_out = os.path.join(UDMI_ROOT, "out/sequencer.out")
    item_out = os.path.join(UDMI_ROOT, "out/test_itemized.out")

    if os.path.exists(seq_out):
        shutil.copy(seq_out, os.path.join(run_backup_dir, "sequencer.out"))
    if os.path.exists(item_out):
        shutil.copy(item_out, os.path.join(run_backup_dir, "test_itemized.out"))

    # Copy sharded directories to preserve logs
    sharded_site_dirs = []
    sites_root = os.path.join(UDMI_ROOT, "sites")
    if os.path.exists(sites_root):
        for d in os.listdir(sites_root):
            if re.match(r'.+_\d+$', d) and os.path.isdir(os.path.join(sites_root, d)):
                sharded_site_dirs.append(os.path.join(sites_root, d))
    sharded_out_dirs = glob.glob(os.path.join(UDMI_ROOT, "out_*"))
    
    for ssd in sharded_site_dirs:
        shutil.copytree(ssd, os.path.join(run_backup_dir, "sites", os.path.basename(ssd)), dirs_exist_ok=True)
    for sod in sharded_out_dirs:
        shutil.copytree(sod, os.path.join(run_backup_dir, os.path.basename(sod)), dirs_exist_ok=True)

    active_site_name = "udmi_site_model"
    if sharded_site_dirs:
        base_dir_name = os.path.basename(sharded_site_dirs[0])
        active_site_name = re.sub(r'_\d+$', '', base_dir_name)

    src_global_site = os.path.join(UDMI_ROOT, "sites", active_site_name)
    if os.path.exists(src_global_site):
        shutil.copytree(src_global_site, os.path.join(run_backup_dir, "sites", active_site_name), dirs_exist_ok=True)
    src_global_out = os.path.join(UDMI_ROOT, "out")
    if os.path.exists(src_global_out):
        shutil.copytree(src_global_out, os.path.join(run_backup_dir, "out"), ignore=shutil.ignore_patterns("mantis"), dirs_exist_ok=True)

    # Cleanup temporary root directories
    for ssd in sharded_site_dirs:
        shutil.rmtree(ssd, ignore_errors=True)
    for sod in sharded_out_dirs:
        shutil.rmtree(sod, ignore_errors=True)
    out_dir = os.path.join(UDMI_ROOT, "out")
    if os.path.exists(out_dir):
        for f in os.listdir(out_dir):
            f_path = os.path.join(out_dir, f)
            if f != "mantis" and os.path.isfile(f_path):
                try:
                    os.remove(f_path)
                except Exception:
                    pass

    # Resolve sharded logs mapping for failed/flaky tests inside this run
    analysis = analyzer.analyze_run(
        sequencer_out_path=os.path.join(run_backup_dir, "sequencer.out"),
        itemized_out_path=os.path.join(run_backup_dir, "test_itemized.out")
    )
    
    triage_metadata = {}
    if analysis:
        for ukey, val in analysis.items():
            if val['outcome'] == 'fail':
                test_name = val['test_name']
                suite_type = val['test_suite']
                occurrence_idx = val['occurrence']
                device_id = val.get('device_id', '')
                
                resolver = UDMILogResolver(UDMI_ROOT)
                seq_log, pub_log, udm_log = "", "", ""
                if suite_type == 'itemized':
                    seq_log, pub_log, udm_log = resolver.resolve_sharded_itemized_logs(
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
                
                if seq_log:
                    if test_name not in triage_metadata:
                        triage_metadata[test_name] = []
                    triage_metadata[test_name].append({
                        "sequence_log": seq_log,
                        "sequence_md": seq_log.replace(".log", ".md") if os.path.exists(os.path.join(UDMI_ROOT, seq_log.replace(".log", ".md"))) else "",
                        "pubber_log": pub_log,
                        "udmis_log": udm_log
                    })

    triage_metadata_path = os.path.join(run_backup_dir, "triage_metadata.json")
    with open(triage_metadata_path, 'w', encoding='utf-8') as fm:
        json.dump(triage_metadata, fm, indent=2)

    return analysis


def evaluate_single_directory(bundles_path, target, output_dir, analyzer):
    """Unpacks, processes, aggregates, and evaluates stability for a single directory."""
    print(f"\n=============================================================")
    print(f"📊 Evaluating Directory: {os.path.basename(bundles_path)}")
    print(f"=============================================================")

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
        # Detect if bundles_path contains multiple zip bundles or represents a single sharded run
        zips = sorted(glob.glob(os.path.join(bundles_path, "*.zip")))
        
        if zips:
            print(f"Found {len(zips)} ZIP run bundles to evaluate.")
            for i, zip_path in enumerate(zips, start=1):
                print(f"Processing ZIP run bundle {i} of {len(zips)}: {os.path.basename(zip_path)}")
                run_backup_dir = os.path.join(phase_dir, f"run_{i}")
                analysis = process_run_bundle(bundles_path, run_backup_dir, zip_path, analyzer)
                if analysis:
                    run_analyses.append(analysis)
        else:
            # Case 2: Single sharded run bundle
            tgz_shards = sorted(glob.glob(os.path.join(bundles_path, "*udmi-support*.tgz")))
            if not tgz_shards:
                print(f"Warning: No udmi-support packages or run_* directories found in {bundles_path}", file=sys.stderr)
                return None

            print(f"Found {len(tgz_shards)} sharded tgz segments. Consolidating as a SINGLE run...")
            run_backup_dir = os.path.join(phase_dir, "run_1")
            analysis = process_run_bundle(bundles_path, run_backup_dir, bundles_path, analyzer)
            if analysis:
                run_analyses.append(analysis)

    if not run_analyses:
        print(
            f"Warning: No test results could be parsed for {os.path.basename(bundles_path)}",
            file=sys.stderr)
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
                    run_backup_dir = os.path.join(bundles_path, f"run_{failed_run_idx}")
                    resolver = UDMILogResolver(UDMI_ROOT)
                    
                    if suite_type == 'itemized':
                        seq_log, pub_log, udm_log = resolver.resolve_sharded_itemized_logs(
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
                        success_run_dir = os.path.join(bundles_path, f"run_{success_run_idx}")
                        if suite_type == 'itemized':
                            s_seq_log, s_pub_log, s_udm_log = resolver.resolve_sharded_itemized_logs(
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
                        "test_name": test_name,
                        "suite": suite_type,
                        "category": val['category'],
                        "device_id": device_id,
                        "occurrence_index": occurrence_idx,
                        "is_flaky": is_flaky,
                        "run_directory": os.path.relpath(run_backup_dir, UDMI_ROOT),
                        "logs": {
                            "failed_run": failed_run_paths,
                            "success_run": success_run_paths
                        }
                    })

        manifest = {
            "metadata": {
                "target_project": target,
                "site_id": os.path.basename(discover_active_site_name(
                    UDMI_ROOT,
                    os.path.join(bundles_path, "run_1") if os.path.exists(os.path.join(bundles_path, "run_1")) else bundles_path
                )),
                "triage_timestamp": datetime.now().isoformat() + "Z"
            },
            "failures": manifest_failures
        }

        manifest_path = os.path.join(bundles_path, "triage_manifest.json")
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
    parser.add_argument("--bundles-dir", "-i", required=True,
                        help="Primary input bundles directory to evaluate")
    parser.add_argument("--compare", "-c", nargs="+",
                        help="Additional bundles directories to compare against chronologically")
    parser.add_argument("--target", "-t",
                        help="Target project spec (default: auto-detected from bundles-dir)")

    args = parser.parse_args()

    primary_path = os.path.abspath(args.bundles_dir)
    UDMI_OUT_MANTIS = os.path.join(UDMI_ROOT, "out", "mantis")
    output_dir = UDMI_OUT_MANTIS

    if not os.path.exists(primary_path):
        print(
            f"Error: Specified --bundles-dir does not exist: '{args.bundles_dir}'",
            file=sys.stderr)
        sys.exit(1)

    # Redirect all outputs to evaluate_stability.log inside the primary path bundle directory
    log_filepath = os.path.join(primary_path, "evaluate_stability.log")
    if sys.stdout.isatty():
        sys.stdout = Tee(sys.stdout, log_filepath)
        sys.stderr = Tee(sys.stderr, log_filepath)

    # Discover target
    detected_target = detect_target(primary_path)
    target = args.target if args.target is not None else detected_target

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
            "\n🎉 Project Mantis Stability Evaluation complete. Single directory processed successfully!")


if __name__ == "__main__":
    main()
