import asyncio
import glob
import json
import os
import re
import sys
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

from app.resolver import UDMILogResolver, UDMIResultParser
from app.reporter import UDMITriageReporter
from app.agent import run_triage_analysis_async
from engine.context import parse_timestamp, slice_log_by_timebounds, merge_and_sort_logs, LogCondensationRule


UDMI_CONDENSE_PATTERNS = [
    LogCondensationRule(
        re.compile(r'(.*State update defer\s+)(-?\d+)(ms|s)(.*)', re.IGNORECASE),
        "[State update deferred x{count}]"
    ),
    LogCondensationRule(
        re.compile(r'(.*value of noWriteback:\s+)(\w+)(.*)', re.IGNORECASE),
        "[value of noWriteback: {val} x{count}]"
    )
]

class UDMITriageRunner:
    """
    Main coordinator of UDMI diagnostics. Walking directories, resolving sharded run outputs,
    merging distributed logs, executing the async agent pipeline, and compiling consolidated summaries.
    """

    def __init__(self, udmi_root: str, mantis_dir: str):
        self.udmi_root = os.path.abspath(udmi_root)
        self.mantis_dir = os.path.abspath(mantis_dir)
        self.out_dir = os.path.join(self.udmi_root, "out", "mantis")
        self.resolver = UDMILogResolver(self.udmi_root)
        self.parser = UDMIResultParser()

    @staticmethod
    def extract_timebounds_from_log(seq_log_path: str, test_id: Optional[str] = None) -> Tuple[Optional[datetime], Optional[datetime]]:
        """Scans a sequence log to find starting and ending timestamps of a specific test."""
        start_ts, end_ts = None, None
        first_ts, last_ts = None, None
        if not os.path.exists(seq_log_path):
            return None, None

        ts_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+')

        if test_id:
            start_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+NOTICE\s+Starting\s+test\s+' + re.escape(test_id))
            end_pattern = re.compile(
                r'^([\d\-T:Z\.,]+)\s+(?:NOTICE\s+Ending\s+test\s+' + re.escape(test_id) +
                r'|ERROR\s+terminating\s+test\s+' + re.escape(test_id) +
                r'|RESULT\s+(?:fail|pass)\s+\S+\s+' + re.escape(test_id) + r')'
            )
        else:
            start_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+NOTICE\s+Starting\s+test')
            end_pattern = re.compile(
                r'^([\d\-T:Z\.,]+)\s+(NOTICE\s+Ending\s+test|ERROR\s+terminating\s+test|RESULT\s+fail|RESULT\s+pass)'
            )

        try:
            with open(seq_log_path, 'r', encoding='utf-8', errors='replace') as f:
                for line in f:
                    ts_match = ts_pattern.match(line)
                    if ts_match:
                        ts = parse_timestamp(ts_match.group(1))
                        if ts:
                            if not first_ts:
                                first_ts = ts
                            last_ts = ts

                    sm = start_pattern.match(line)
                    if sm:
                        start_ts = parse_timestamp(sm.group(1))

                    em = end_pattern.match(line)
                    if em:
                        end_ts = parse_timestamp(em.group(1))
        except Exception as e:
            print(f"Warning: failed to extract timestamps from {seq_log_path}: {e}", file=sys.stderr)

        if not start_ts:
            start_ts = first_ts
        if not end_ts:
            end_ts = last_ts

        return start_ts, end_ts

    @staticmethod
    def read_filtered_sequence_log(filepath: str, test_id: Optional[str] = None, max_chars: int = 300000) -> str:
        """Slices the sequencer console logs directly to the target test bounds."""
        if not os.path.exists(filepath):
            return ""
        try:
            filtered_lines = []
            curr_chars = 0

            start_reading = False if test_id else True
            start_other_test_pattern = re.compile(r'NOTICE\s+Starting\s+test\s+(\S+)')

            with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
                for line in f:
                    if not start_reading and test_id:
                        if f"NOTICE  Starting test {test_id}" in line or f"Starting test {test_id}" in line:
                            start_reading = True

                    if start_reading:
                        if test_id:
                            match = start_other_test_pattern.search(line)
                            if match and match.group(1) != test_id:
                                break

                        line_len = len(line)
                        if curr_chars + line_len > max_chars:
                            filtered_lines.append("\n[TRUNCATED due to character limit]\n")
                            break
                        filtered_lines.append(line)
                        curr_chars += line_len

                        if test_id and (f"Ending test {test_id}" in line or f"terminating test {test_id}" in line):
                            break
            return "".join(filtered_lines)
        except Exception as e:
            print(f"Warning: failed to read filtered log {filepath}: {e}", file=sys.stderr)
            return ""

    def find_successful_run_for_test(self, parent_dir: str, test_id: str) -> Optional[str]:
        """Scans sibling run directories to find one where test_id passed."""
        if not os.path.isdir(parent_dir):
            return None

        for rdir in sorted(os.listdir(parent_dir)):
            full_rdir = os.path.join(parent_dir, rdir)
            if not os.path.isdir(full_rdir) or not rdir.startswith("run_"):
                continue

            # Check sequencer.out failures
            seq_out = os.path.join(full_rdir, "sequencer.out")
            if os.path.exists(seq_out):
                failures = self.parser.parse_results_file(seq_out, is_itemized=False)
                if not any(fail.test_name == test_id for fail in failures):
                    try:
                        with open(seq_out, 'r', encoding='utf-8', errors='replace') as f:
                            for line in f:
                                if test_id in line and "RESULT pass" in line:
                                    return full_rdir
                    except Exception:
                        pass

            # Check test_itemized.out failures
            item_out = os.path.join(full_rdir, "test_itemized.out")
            if os.path.exists(item_out):
                failures = self.parser.parse_results_file(item_out, is_itemized=True)
                if not any(fail.test_name == test_id for fail in failures):
                    try:
                        with open(item_out, 'r', encoding='utf-8', errors='replace') as f:
                            for line in f:
                                if test_id in line and "RESULT pass" in line:
                                    return full_rdir
                    except Exception:
                        pass
        return None

    def auto_detect_metadata(self, bundles_dir_path: str) -> Tuple[str, str]:
        """Auto-detects target and site directory from the bundles directory path."""
        base = os.path.basename(os.path.normpath(bundles_dir_path))

        clean_name = re.sub(r'_\d{8}_\d{6}$', '', base)
        clean_name = re.sub(r'^run_\d+$', '', clean_name)
        clean_name = re.sub(r'^(before|after)_', '', clean_name, flags=re.IGNORECASE)

        if not clean_name:
            parent_path = os.path.dirname(os.path.normpath(bundles_dir_path))
            parent_base = os.path.basename(parent_path)
            clean_name = re.sub(r'_\d{8}_\d{6}$', '', parent_base)
            clean_name = re.sub(r'^(before|after)_', '', clean_name, flags=re.IGNORECASE)

        parts = clean_name.split("_", 1)
        if len(parts) == 2:
            target = f"//{parts[0]}/{parts[1]}"
        else:
            target = f"//{clean_name}" if clean_name else "//mqtt/localhost"

        first_word = parts[0] if parts else "mqtt"
        site_dir = f"sites/{first_word}"
        if not os.path.exists(os.path.join(self.udmi_root, site_dir)):
            site_dir = "sites/udmi_site_model"

        return target, site_dir

    def scan_failures_from_metrics(self, clean_target: str, output_dir: Optional[str] = None) -> List[Dict[str, Any]]:
        """Scans metrics JSON files in output_dir matching the target to find mismatched test failures."""
        if not output_dir:
            output_dir = self.out_dir

        pattern = os.path.join(output_dir, f"metrics_{clean_target}_*.json")
        metric_files = glob.glob(pattern)

        failures = []
        if not metric_files:
            return failures

        latest_file = max(metric_files, key=os.path.getmtime)
        print(f"Discovered metrics baseline: {os.path.basename(latest_file)}")

        try:
            with open(latest_file, 'r', encoding='utf-8') as f:
                metrics = json.load(f)
            for ukey, val in metrics.items():
                if val.get('fail_count', 0) > 0:
                    failures.append({
                        'test_name': val['test_name'],
                        'category': val['category'],
                        'suite': val['test_suite']
                    })
        except Exception as e:
            print(f"Warning: Failed to load or parse metrics file {latest_file}: {e}", file=sys.stderr)

        return failures

    async def triage_single_failure(
        self,
        idx: int,
        total_count: int,
        run_dir: str,
        f: dict,
        test_meta: Any,
        semaphore: asyncio.Semaphore,
        target: str,
        site_id: str,
        clean_target: str,
        force: bool = False,
        oem: bool = False
    ) -> dict:
        """Coordinating task to analyze a single test case failure in parallel."""
        test_id = f['test_name']
        print(f"\n[{test_id}] --- Triaging Failure {idx} of {total_count}: {test_id} (in {os.path.basename(run_dir)}) ---")

        device_id = self.resolver.discover_device_id(run_dir, test_id)

        devices_path = os.path.join(run_dir, "out", "devices")
        if not os.path.exists(devices_path):
            devices_path = os.path.join(run_dir, "devices")

        local_seq_log = os.path.join(devices_path, f"{device_id}/tests/{test_id}/sequence.log")
        local_seq_md = os.path.join(devices_path, f"{device_id}/tests/{test_id}/sequence.md")
        pubber_log_path = os.path.join(run_dir, "pubber.log")
        udmis_log_path = os.path.join(run_dir, "udmis.log")
        shard_suffix = ""

        if isinstance(test_meta, dict) and "sequence_log" in test_meta:
            local_seq_log = os.path.join(self.udmi_root, test_meta["sequence_log"])
            local_seq_md = os.path.join(self.udmi_root, test_meta["sequence_md"]) if test_meta.get("sequence_md") else ""
            pub_meta = test_meta.get("pubber_log")
            pubber_log_path = os.path.join(self.udmi_root, pub_meta) if pub_meta else ""
            udm_meta = test_meta.get("udmis_log")
            udmis_log_path = os.path.join(self.udmi_root, udm_meta) if udm_meta else ""

            m = re.search(r'(?:site_model_|out_)(\d+)[/_]', test_meta["sequence_log"])
            shard_suffix = m.group(1) if m else ""
            print(f"[{test_id}] Successfully resolved sharded logs for test '{test_id}' -> Shard Suffix: {shard_suffix}")
        else:
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

        print(f"[{test_id}] Available Context Catalog: {', '.join(k for k, v in catalog.items() if v)}")

        start_dt, end_dt = self.extract_timebounds_from_log(local_seq_log, test_id=test_id)
        sliced_pubber = []
        sliced_udmis = []

        if start_dt and end_dt:
            print(f"[{test_id}] Test start: {start_dt.strftime('%H:%M:%S')} | end: {end_dt.strftime('%H:%M:%S')} (Padded correlation active)")
            sliced_pubber = slice_log_by_timebounds(pubber_log_path, start_dt, end_dt)
            sliced_udmis = slice_log_by_timebounds(udmis_log_path, start_dt, end_dt)
        else:
            print(f"[{test_id}] Warning: Could not extract starting/ending timestamps from sequence log. Slicing bypassed.")

        local_seq_log_content = self.read_filtered_sequence_log(local_seq_log, test_id=test_id)

        local_seq_md_content = ""
        if os.path.exists(local_seq_md):
            with open(local_seq_md, 'r', encoding='utf-8', errors='replace') as fm:
                local_seq_md_content = fm.read()[:200000]

        success_seq_log = test_meta.get("success_log", "") if isinstance(test_meta, dict) else ""
        if success_seq_log:
            success_seq_log = os.path.join(self.udmi_root, success_seq_log)
        success_seq_md = success_seq_log.replace(".log", ".md") if (
                success_seq_log and os.path.exists(success_seq_log.replace(".log", ".md"))) else ""

        if not success_seq_log:
            success_run_dir = self.find_successful_run_for_test(os.path.dirname(run_dir), test_id)
            if success_run_dir:
                success_seq_log = os.path.join(success_run_dir, f"out/devices/{device_id}/tests/{test_id}/sequence*.log")
                s_glob = glob.glob(success_seq_log)
                success_seq_log = s_glob[0] if s_glob else ""
                success_seq_md = success_seq_log.replace(".log", ".md") if (
                        success_seq_log and os.path.exists(success_seq_log.replace(".log", ".md"))) else ""

        success_seq_log_content = ""
        success_seq_md_content = ""

        if success_seq_log and os.path.exists(success_seq_log):
            success_seq_log_content = self.read_filtered_sequence_log(success_seq_log, test_id=test_id)

        if success_seq_md and os.path.exists(success_seq_md):
            try:
                with open(success_seq_md, 'r', encoding='utf-8', errors='replace') as fsm:
                    success_seq_md_content = fsm.read()[:200000]
            except Exception:
                pass

        payload = []
        payload.append(f"## Metadata Context")
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

        log_sources = []
        if local_seq_log_content:
            log_sources.append(("Sequencer", local_seq_log_content.splitlines()))
        if sliced_udmis:
            log_sources.append(("UDMIS", sliced_udmis))
        if sliced_pubber:
            log_sources.append(("Device under Test", sliced_pubber))

        merged_raw_logs = merge_and_sort_logs(log_sources, condensation_rules=UDMI_CONDENSE_PATTERNS)

        payload.append(f"\n## Chronologically Merged Global Logs (Test Execution Context)")
        if merged_raw_logs:
            payload.append(f"```text\n" + "\n".join(merged_raw_logs) + "\n```")
        else:
            payload.append("`[No correlated raw console logs found inside test execution bounds]`")

        payload.append(f"\n## Reference Successful Run Details (Differential Triage Baseline)")
        if success_run_dir := self.find_successful_run_for_test(os.path.dirname(run_dir), test_id):
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

        analysis_text = ""
        try:
            analysis_text = await run_triage_analysis_async(prompt_payload, semaphore, out_dir=self.out_dir, force=force, oem=oem)
        except Exception as e:
            analysis_text = f"⚠️ Error executing Gemini AI diagnostics for {test_id}: {e}"

        nested_out_dir = os.path.join(self.out_dir, "diagnose", clean_target, site_id, device_id, test_id)
        os.makedirs(nested_out_dir, exist_ok=True)

        report_filepath = os.path.join(nested_out_dir, "triage_analysis.md")
        with open(report_filepath, 'w', encoding='utf-8') as fr:
            fr.write(analysis_text)

        print(f"[{test_id}] Diagnostic report successfully saved: {report_filepath}")

        breakpoint_summary = "Triage complete. Review details."
        insufficient = "INSUFFICIENT DATA TO TRACE ROOT CAUSE" in analysis_text

        bm = re.search(
            r'(?:## Breakpoint Summary|## \d\.\s*Breakpoint Summary|## \d\.\s*Executive Defect Summary|## Executive Defect Summary|## \d\.\s*Root Cause Summary|## Root Cause Summary)\s*\n*>\s*(.*)',
            analysis_text)
        if bm:
            breakpoint_summary = bm.group(1).strip()
        elif insufficient:
            breakpoint_summary = "INSUFFICIENT DATA TO TRACE ROOT CAUSE"

        return {
            'test_id': test_id,
            'suite': f['suite'],
            'category': f['category'],
            'breakpoint': breakpoint_summary,
            'insufficient': insufficient,
            'report_link': f"./{device_id}/{test_id}/triage_analysis.md"
        }

    async def run_triage(self, args: Any) -> None:
        """Orchestrates the parallel multi-job async diagnostics run."""
        # Determine active output directory dynamically
        if getattr(args, 'manifest', None):
            self.out_dir = os.path.dirname(os.path.abspath(args.manifest))
        elif getattr(args, 'bundles_dir', None):
            self.out_dir = os.path.abspath(args.bundles_dir)
        elif getattr(args, 'run_dir', None):
            self.out_dir = os.path.abspath(args.run_dir)
        else:
            self.out_dir = os.path.join(self.udmi_root, "out", "mantis")

        # Initialize persistent logging for the diagnostic run
        os.makedirs(self.out_dir, exist_ok=True)
        log_filepath = os.path.join(self.out_dir, "diagnose.log")
        from mantis.src.util.logging import Tee
        sys.stdout = Tee(sys.stdout, log_filepath)
        sys.stderr = Tee(sys.stderr, log_filepath)
        
        # 0. Manifest Mode Active Check
        if getattr(args, 'manifest', None):
            print(f"Manifest Mode active. Loading triage targets from manifest: {args.manifest}")
            manifest_path = os.path.abspath(args.manifest)
            if not os.path.exists(manifest_path):
                print(f"Error: Specified manifest file '{args.manifest}' does not exist.", file=sys.stderr)
                sys.exit(1)
                
            try:
                with open(manifest_path, 'r', encoding='utf-8') as fm:
                    manifest_data = json.load(fm)
            except Exception as e:
                print(f"Error: Failed to parse manifest file: {e}", file=sys.stderr)
                sys.exit(1)

            target = manifest_data['metadata']['target_project']
            site_id = manifest_data['metadata']['site_id']
            clean_target = target.replace("/", "_").replace("+", "_").strip("_")

            runs_to_triage = []
            for fail_item in manifest_data.get('failures', []):
                test_name = fail_item['test_name']
                suite = fail_item['suite']
                category = fail_item['category']
                device_id = fail_item['device_id']
                run_dir = os.path.abspath(os.path.join(self.udmi_root, fail_item['run_directory']))
                
                failed_logs = fail_item['logs']['failed_run']
                t_meta = {
                    'sequence_log': failed_logs['sequence_log'],
                    'sequence_md': failed_logs['sequence_md'],
                    'pubber_log': failed_logs['pubber_log'],
                    'udmis_log': failed_logs['udmis_log']
                }
                
                success_logs = fail_item['logs'].get('success_run')
                if success_logs:
                    t_meta['success_log'] = success_logs['sequence_log']
                    t_meta['success_md'] = success_logs['sequence_md']

                runs_to_triage.append((
                    run_dir,
                    {'test_name': test_name, 'category': category, 'suite': suite, 'device_id': device_id},
                    t_meta
                ))

            # Apply optional CLI filters
            if args.test:
                runs_to_triage = [x for x in runs_to_triage if x[1]['test_name'] in args.test]
            if args.device:
                runs_to_triage = [x for x in runs_to_triage if x[1]['device_id'] in args.device or x[2].get('sequence_log', '').split('devices/')[-1].split('/')[0] in args.device]
        
        else:
            # 1. Configure logs/folders matching parameters
            bundles_dir_arg = args.bundles_dir if args.bundles_dir is not None else args.run_dir
            if not args.sequence_log and not bundles_dir_arg:
                print("Error: Missing --bundles-dir or --sequence-log specifications.", file=sys.stderr)
                sys.exit(1)

            if args.sequence_log:
                print("Direct log diagnostic mode active. Ingesting direct log sources...")
                local_seq_log = os.path.abspath(args.sequence_log)
                local_seq_md = local_seq_log.replace(".log", ".md") if os.path.exists(local_seq_log.replace(".log", ".md")) else ""
                pubber_log_path = os.path.abspath(args.device_log) if args.device_log else ""
                udmis_log_path = os.path.abspath(args.udmis_log) if args.udmis_log else ""
                success_seq_log = os.path.abspath(args.success_log) if args.success_log else ""

                test_id = args.test[0] if args.test else ""
                if not test_id:
                    m = re.search(r'tests/([^/]+)/', local_seq_log)
                    test_id = m.group(1) if m else "unknown_test"

                site_id = "bos-platform-staging"
                m = re.search(r'sites/([^/]+)/', local_seq_log)
                if m:
                    site_id = m.group(1)
                clean_target = site_id
                target = clean_target

                runs_to_triage = [(
                    os.path.dirname(local_seq_log),
                    {'test_name': test_id, 'category': 'unknown', 'suite': 'both'},
                    {
                        'sequence_log': local_seq_log,
                        'sequence_md': local_seq_md,
                        'pubber_log': pubber_log_path,
                        'udmis_log': udmis_log_path,
                        'success_log': success_seq_log
                    }
                )]
            else:
                bundles_path = os.path.abspath(bundles_dir_arg)
                if not os.path.exists(bundles_path):
                    print(f"Error: Specified bundles directory '{bundles_dir_arg}' does not exist.", file=sys.stderr)
                    sys.exit(1)

                detected_target, detected_site_dir = self.auto_detect_metadata(bundles_path)
                target = args.target if args.target is not None else detected_target
                site_dir = args.site_dir if args.site_dir is not None else detected_site_dir

                clean_target = target.replace("/", "_").replace("+", "_").strip("_")
                site_id = os.path.basename(site_dir)

                run_subdirs = sorted(glob.glob(os.path.join(bundles_path, "run_*")))
                runs_to_triage = []
                all_raw_runs = []

                if run_subdirs:
                    print(f"Multi-run directory mode: scanning {len(run_subdirs)} runs for failures...")
                    for rsd in run_subdirs:
                        run_failures = self.scan_failures_from_metrics(clean_target, output_dir=rsd)
                        if not run_failures:
                            run_failures = self.scan_failures_from_metrics(clean_target, output_dir=os.path.join(rsd, "out"))
                        for f in run_failures:
                            all_raw_runs.append((rsd, f))
                else:
                    print("Single run directory mode: scanning for failures...")
                    failures = []
                    metrics_failures = self.scan_failures_from_metrics(clean_target)

                    if metrics_failures:
                        failures.extend(metrics_failures)
                    else:
                        failures.extend(self.parser.parse_results_file(os.path.join(bundles_path, "sequencer.out"), is_itemized=False))
                        failures.extend(self.parser.parse_results_file(os.path.join(bundles_path, "test_itemized.out"), is_itemized=True))

                    seen = set()
                    unique_failures = []
                    for f in failures:
                        # Format fails uniformly
                        fail_dict = f if isinstance(f, dict) else {
                            'test_name': f.test_name,
                            'category': f.category,
                            'suite': f.suite
                        }
                        if fail_dict['test_name'] not in seen:
                            seen.add(fail_dict['test_name'])
                            unique_failures.append(fail_dict)
                    for f in unique_failures:
                        all_raw_runs.append((bundles_path, f))

                if args.test:
                    all_raw_runs = [x for x in all_raw_runs if x[1]['test_name'] in args.test]
                    if not all_raw_runs:
                        for t in args.test:
                            all_raw_runs.append(
                                (run_subdirs[0] if run_subdirs else bundles_path,
                                 {'test_name': t, 'category': 'unknown', 'suite': 'both'})
                            )

                for run_subdir, f in all_raw_runs:
                    test_id = f['test_name']
                    triage_metadata = {}
                    metadata_path = os.path.join(run_subdir, "triage_metadata.json")
                    if os.path.exists(metadata_path):
                        try:
                            with open(metadata_path, 'r', encoding='utf-8') as fm:
                                triage_metadata = json.load(fm)
                        except Exception:
                            pass

                    test_meta_list = triage_metadata.get(test_id, [])
                    if isinstance(test_meta_list, list) and len(test_meta_list) > 0:
                        for meta in test_meta_list:
                            runs_to_triage.append((run_subdir, f, meta))
                    else:
                        if isinstance(test_meta_list, dict) and "sequence_log" in test_meta_list:
                            runs_to_triage.append((run_subdir, f, test_meta_list))
                        else:
                            runs_to_triage.append((run_subdir, f, None))

                if args.device:
                    filtered = []
                    for run_subdir, f, meta in runs_to_triage:
                        dev_id = ""
                        if meta and "sequence_log" in meta:
                            parts = meta["sequence_log"].split("devices/")
                            if len(parts) > 1:
                                dev_id = parts[1].split("/")[0]
                        if not dev_id:
                            dev_id = self.resolver.discover_device_id(run_subdir, f['test_name'])

                        if dev_id in args.device:
                            filtered.append((run_subdir, f, meta))
                    runs_to_triage = filtered

        if not runs_to_triage:
            print("🎉 No regression or failing test cases detected in run outputs. Diagnostics complete!")
            sys.exit(0)

        failure_names = []
        for item in runs_to_triage:
            log_name = os.path.basename(item[2].get('sequence_log')) if (
                    item[2] and item[2].get('sequence_log')) else 'Legacy'
            failure_names.append(f"{item[1]['test_name']} ({log_name})")
        print(f"Found {len(runs_to_triage)} sharded test case failure occurrences to triage: {', '.join(failure_names)}")

        # Setup parallel semaphore
        concurrency_limit = args.concurrency
        print(f"Parallel processing initialized with concurrency limit: {concurrency_limit}")
        semaphore = asyncio.Semaphore(concurrency_limit)

        tasks = []
        for idx, (run_dir, f, test_meta) in enumerate(runs_to_triage, start=1):
            # Standardize test failures into dict keys
            fail_dict = f if isinstance(f, dict) else {
                'test_name': f.test_name,
                'category': f.category,
                'suite': f.suite
            }
            tasks.append(
                self.triage_single_failure(
                    idx=idx,
                    total_count=len(runs_to_triage),
                    run_dir=run_dir,
                    f=fail_dict,
                    test_meta=test_meta,
                    semaphore=semaphore,
                    target=target,
                    site_id=site_id,
                    clean_target=clean_target,
                    force=getattr(args, "force", False),
                    oem=getattr(args, "oem", False)
                )
            )

        triage_summaries = await asyncio.gather(*tasks)

        # Resolve failure classifiers from Playbook config
        from engine.config.playbook import Playbook
        from pathlib import Path

        oem = getattr(args, "oem", False)
        impl_dir = Path(os.path.dirname(os.path.abspath(__file__)))
        playbook_file = "config/playbook_oem_integrator.yaml" if oem else "config/playbook.yaml"
        playbook_path = impl_dir / playbook_file
        
        failure_classifiers = None
        if playbook_path.exists():
            try:
                playbook = Playbook(playbook_path).load()
                failure_classifiers = playbook.pipeline_config.get("failure_classifiers")
            except Exception as e:
                print(f"Warning: Failed to load playbook from {playbook_path} for report classification: {e}", file=sys.stderr)

        # Generate report via UDMITriageReporter
        print("\nCompiling site-specific Triage Summary Report...")
        reporter = UDMITriageReporter(target=target, site_id=site_id, out_dir=self.out_dir, failure_classifiers=failure_classifiers)
        root_report_path = reporter.save_report(triage_summaries)

        print(f"Consolidated triage summary report generated: {root_report_path}\n")
        print("Mantis Triage diagnostic run completed successfully.")
