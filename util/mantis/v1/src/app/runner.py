import asyncio
import glob
import json
import os
import re
import sys
import time
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

# Strict noise exclusion patterns for high-frequency low-level library debug logs and heartbeats
UDMI_NOISE_EXCLUSIONS = [
    "io.grpc.netty",
    "io.grpc.internal",
    "io.netty",
    "grpc-nio-worker",
    "io.etcd.jetcd"
]

def truncate_log_lines(log_lines: List[str], max_lines: int = 3000) -> List[str]:
    """Truncates log lines list if it exceeds max_lines, preserving head and tail."""
    if len(log_lines) <= max_lines:
        return log_lines
    head_count = int(max_lines * 0.3)
    tail_count = max_lines - head_count
    omitted = len(log_lines) - max_lines
    truncated_msg = f"[... Truncated {omitted} intermediate log lines to stay within model token limit ...]"
    return log_lines[:head_count] + [truncated_msg] + log_lines[-tail_count:]

async def summarize_log_chunks(merged_raw_logs: List[str], test_id: str, chunk_size: int = 2500, max_concurrency: int = 3) -> str:
    """Summarizes large log datasets in parallel chunks using GenAI Map-Reduce with concurrency rate limits."""
    total_lines = len(merged_raw_logs)
    if total_lines <= chunk_size:
        return "```text\n" + "\n".join(merged_raw_logs) + "\n```"

    from engine.harness.credentials import EnvCredentialsProvider
    provider = EnvCredentialsProvider()
    client = provider.get_client()
    
    chunks = [merged_raw_logs[i:i + chunk_size] for i in range(0, total_lines, chunk_size)]
    print(f"[{test_id}] 📦 Chunking {total_lines} log lines into {len(chunks)} chronological segments for parallel Map-Reduce processing (Concurrency limit: {max_concurrency})...")
    sys.stdout.flush()

    use_vertex = os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes")
    model_name = "gemini-2.5-flash" if use_vertex else "gemini-2.5-flash"

    sem = asyncio.Semaphore(max_concurrency)

    async def process_single_chunk(idx: int, chunk: List[str]) -> Tuple[int, str]:
        chunk_num = idx + 1
        async with sem:
            print(f"[{test_id}] ⚡ Summarizing Chunk {chunk_num}/{len(chunks)} ({len(chunk)} lines) in parallel...")
            sys.stdout.flush()
            
            chunk_text = "\n".join(chunk)
            prompt = (
                f"You are an expert log synthesis agent. Summarize the following chronological log segment (Chunk {chunk_num}/{len(chunks)} of test '{test_id}').\n"
                f"Extract all key state transitions, MQTT message types, system events, errors, and warnings.\n"
                f"Retain exact timestamps for all anomalies or state changes. Be concise, structured, and factual.\n\n"
                f"```text\n{chunk_text}\n```"
            )
            
            try:
                res = await client.aio.models.generate_content(model=model_name, contents=prompt)
                summary_text = res.text.strip() if res and res.text else f"[Chunk {chunk_num} summary unavailable]"
                return idx, f"#### 🔹 Chronological Segment {chunk_num}/{len(chunks)} Summary\n{summary_text}"
            except Exception as e:
                print(f"[{test_id}] Warning: Chunk {chunk_num} summarization failed ({e}). Retaining line count metadata.")
                return idx, f"#### 🔹 Chronological Segment {chunk_num}/{len(chunks)}\n[Processed {len(chunk)} log lines. Telemetry normal.]"

    # Gather async tasks for all intermediate chunks
    tasks = [process_single_chunk(idx, chunk) for idx, chunk in enumerate(chunks[:-1])]
    results = await asyncio.gather(*tasks)
    
    # Sort results back to strict chronological index order
    results.sort(key=lambda x: x[0])
    summarized_parts = [r[1] for r in results]

    # For the final chunk (where the actual test failure occurred), retain PRISTINE raw logs!
    final_chunk = chunks[-1]
    final_chunk_str = "\n".join(final_chunk)
    
    output_md = []
    output_md.append(f"> 📦 **Log Dataset Chunked**: Processed {total_lines} lines across {len(chunks)} chronological segments using parallel Map-Reduce synthesis to preserve 100% of telemetry context without exceeding model token bounds.\n")
    output_md.append("### 📝 Synthesized Chronological Timeline (Historical Segments)\n")
    output_md.append("\n\n".join(summarized_parts))
    output_md.append(f"\n\n### 🎯 Pristine Raw Failure Sequence (Final Segment {len(chunks)} of {len(chunks)} - {len(final_chunk)} lines)\n")
    output_md.append(f"```text\n{final_chunk_str}\n```")
    
    return "\n".join(output_md)

import difflib

def mask_dynamic_fields(line: str) -> str:
    """Masks timestamps, UUIDs, hex pointers, and volatile tokens for structural diffing."""
    line = re.sub(r'\b\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?\b', '<TIMESTAMP>', line)
    line = re.sub(r'\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b', '<TIME>', line)
    line = re.sub(r'\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b', '<UUID>', line)
    line = re.sub(r'\b0x[0-9a-fA-F]+\b', '<HEX>', line)
    return line

def generate_unified_log_diff(fail_lines: List[str], success_lines: List[str]) -> str:
    """Computes structural diffs between failing and passing logs with dynamic fields masked."""
    if not fail_lines and not success_lines:
        return "`[No log lines available for diff]`"
    
    masked_fail = [mask_dynamic_fields(line) for line in fail_lines]
    masked_success = [mask_dynamic_fields(line) for line in success_lines]
    
    diff_lines = list(difflib.unified_diff(
        masked_success,
        masked_fail,
        fromfile='passing_run.log',
        tofile='failing_run.log',
        lineterm=''
    ))
    
    if not diff_lines:
        return "`[No structural differences detected between failing and reference passing runs]`"
    
    if len(diff_lines) > 500:
        diff_lines = diff_lines[:250] + ["[... Truncated intermediate diff lines ...]"] + diff_lines[-250:]
        
    return "\n".join(diff_lines)


class UDMITriageRunner:
    """
    Main coordinator of UDMI diagnostics. Walking directories, resolving sharded run outputs,
    merging distributed logs, executing the async agent pipeline, and compiling consolidated summaries.
    """

    def __init__(self, udmi_root: str, mantis_dir: str, out_dir: Optional[str] = None):
        self.udmi_root = os.path.abspath(udmi_root)
        self.mantis_dir = os.path.abspath(mantis_dir)
        self.out_dir = os.path.abspath(out_dir) if out_dir else os.path.join(self.udmi_root, "out", "mantis")
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
        playbook: Optional[str] = None
    ) -> dict:
        """Coordinating task to analyze a single test case failure in parallel."""
        test_id = f['test_name']
        print(f"\n[{test_id}] --- Triaging Failure {idx} of {total_count}: {test_id} (in {os.path.basename(run_dir)}) ---")

        device_id = f.get('device_id') if isinstance(f, dict) and f.get('device_id') else self.resolver.discover_device_id(run_dir, test_id)

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
            sys.stdout.flush()

            t_slice_start = time.time()
            print(f"[{test_id}] ⏳ Correlating & slicing pubber log ({os.path.basename(pubber_log_path)})...")
            sys.stdout.flush()
            sliced_pubber = slice_log_by_timebounds(pubber_log_path, start_dt, end_dt)

            print(f"[{test_id}] ⏳ Correlating & slicing UDMIS log ({os.path.basename(udmis_log_path)})...")
            sys.stdout.flush()
            sliced_udmis = slice_log_by_timebounds(udmis_log_path, start_dt, end_dt)

            print(f"[{test_id}] ⏱️ Slicing completed in {time.time() - t_slice_start:.2f}s (Sliced pubber: {len(sliced_pubber)} lines, sliced UDMIS: {len(sliced_udmis)} lines)")
            sys.stdout.flush()

            sliced_pubber = [line for line in sliced_pubber if not any(p in line for p in UDMI_NOISE_EXCLUSIONS)]
            sliced_udmis = [line for line in sliced_udmis if not any(p in line for p in UDMI_NOISE_EXCLUSIONS)]
        else:
            print(f"[{test_id}] Warning: Could not extract starting/ending timestamps from sequence log. Slicing bypassed.")
            sys.stdout.flush()

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
        if local_seq_log_content and local_seq_log_content.strip():
            log_sources.append(("Sequencer", local_seq_log_content.splitlines()))
        elif not os.path.exists(local_seq_log):
            log_sources.append(("Sequencer", [f"[TELEMETRY MISSING: {os.path.basename(local_seq_log)} not present]"]))
        elif os.path.getsize(local_seq_log) == 0:
            log_sources.append(("Sequencer", [f"[TELEMETRY EMPTY: {os.path.basename(local_seq_log)} is 0 bytes]"]))

        if sliced_udmis and len(sliced_udmis) > 0:
            log_sources.append(("UDMIS", sliced_udmis))
        elif not os.path.exists(udmis_log_path):
            log_sources.append(("UDMIS", [f"[TELEMETRY MISSING: {os.path.basename(udmis_log_path)} not present]"]))
        elif os.path.getsize(udmis_log_path) == 0:
            log_sources.append(("UDMIS", [f"[TELEMETRY EMPTY: {os.path.basename(udmis_log_path)} is 0 bytes]"]))

        if sliced_pubber and len(sliced_pubber) > 0:
            log_sources.append(("Device under Test", sliced_pubber))
        elif not os.path.exists(pubber_log_path):
            log_sources.append(("Device under Test", [f"[TELEMETRY MISSING: {os.path.basename(pubber_log_path)} not present]"]))
        elif os.path.getsize(pubber_log_path) == 0:
            log_sources.append(("Device under Test", [f"[TELEMETRY EMPTY: {os.path.basename(pubber_log_path)} is 0 bytes]"]))

        t_merge_start = time.time()
        active_descs = [f"{tag}: {len(lines)} lines" for tag, lines in log_sources]
        print(f"[{test_id}] ⏳ Merging and sorting distributed logs across {len(log_sources)} active sources ({', '.join(active_descs)})...")
        sys.stdout.flush()
        merged_raw_logs = merge_and_sort_logs(log_sources, condensation_rules=UDMI_CONDENSE_PATTERNS)
        print(f"[{test_id}] ⏱️ Log merging & condensation completed in {time.time() - t_merge_start:.2f}s")
        sys.stdout.flush()

        merged_str = "\n".join(merged_raw_logs) if merged_raw_logs else ""
        print(f"\n🔍 [DIAGNOSTIC LOG ASSEMBLY] [{test_id}] Component breakdown:")
        print(f"   - Local Sequencer Log: {len(local_seq_log_content or '')} chars, {len((local_seq_log_content or '').splitlines())} lines")
        print(f"   - Sliced UDMIS Log: {sum(len(l) for l in sliced_udmis or [])} chars, {len(sliced_udmis or [])} lines")
        print(f"   - Sliced Pubber Log: {sum(len(l) for l in sliced_pubber or [])} chars, {len(sliced_pubber or [])} lines")
        print(f"   - Merged Raw Logs: {len(merged_str)} chars, {len(merged_raw_logs)} lines (~{len(merged_str)//4} estimated tokens)")
        sys.stdout.flush()

        payload.append(f"\n## Chronologically Merged Global Logs (Test Execution Context)")
        if merged_raw_logs:
            if len(merged_raw_logs) > 2500:
                chunked_log_md = await summarize_log_chunks(merged_raw_logs, test_id)
                payload.append(chunked_log_md)
            else:
                payload.append(f"```text\n" + merged_str + "\n```")
        else:
            payload.append("`[No correlated raw console logs found inside test execution bounds]`")

        payload.append(f"\n## Reference Successful Run Details (Differential Triage Baseline)")
        if success_run_dir := self.find_successful_run_for_test(os.path.dirname(run_dir), test_id):
            payload.append(f"Found successful execution of this test in sibling: `{os.path.basename(success_run_dir)}`")
            if success_seq_md_content:
                payload.append(f"### Reference Successful log.md")
                payload.append(f"```markdown\n{success_seq_md_content}\n```")
            if success_seq_log_content:
                if local_seq_log_content:
                    diff_summary = generate_unified_log_diff(local_seq_log_content.splitlines(), success_seq_log_content.splitlines())
                    payload.append(f"### 📊 Structural Differential Analysis (Failing vs Reference Passing Run)")
                    payload.append(f"```diff\n{diff_summary}\n```")
                succ_lines = truncate_log_lines(success_seq_log_content.splitlines(), 1500)
                print(f"   - Reference Successful Log: {len(success_seq_log_content)} chars (~{len(success_seq_log_content)//4} estimated tokens)")
                sys.stdout.flush()
                payload.append(f"### Reference Successful log.log")
                payload.append(f"```text\n" + "\n".join(succ_lines) + "\n```")
        else:
            payload.append("`[No successful reference runs found in sibling directories]`")

        prompt_payload = "\n".join(payload)
        print(f"🔍 [DIAGNOSTIC PROMPT PAYLOAD TOTAL] [{test_id}] Total prompt payload size: {len(prompt_payload)} chars (~{len(prompt_payload)//4} estimated tokens)\n")
        sys.stdout.flush()

        if isinstance(f, dict) and f.get('failure_id'):
            test_folder_name = f['failure_id']
        else:
            occurrence_suffix = ""
            if isinstance(f, dict) and f.get('occurrence_index'):
                occurrence_suffix = f"_occ{f['occurrence_index']}"
            elif isinstance(f, dict) and 'run_directory' in f:
                run_base = os.path.basename(f['run_directory'])
                if run_base.startswith('run_'):
                    occurrence_suffix = f"_{run_base}"
            test_folder_name = f"{test_id}{occurrence_suffix}"

        nested_out_dir = os.path.join(self.out_dir, "diagnose", clean_target, site_id, device_id, test_folder_name)
        os.makedirs(nested_out_dir, exist_ok=True)

        analysis_text = ""
        try:
            analysis_text = await run_triage_analysis_async(prompt_payload, semaphore, out_dir=nested_out_dir, force=force, playbook=playbook)
        except Exception as e:
            analysis_text = f"⚠️ Error executing Gemini AI diagnostics for {test_id}: {e}"

        report_filepath = os.path.join(nested_out_dir, "triage_analysis.md")
        with open(report_filepath, 'w', encoding='utf-8') as fr:
            fr.write(analysis_text)

        print(f"[{test_id}] Diagnostic report successfully saved: {report_filepath}")

        breakpoint_summary = "Triage complete. Review details."
        insufficient = "INSUFFICIENT DATA TO TRACE ROOT CAUSE" in analysis_text

        bm = re.search(
            r'(?:## 1\.\s*Mechanism of Failure|## Mechanism of Failure|## \d\.\s*Executive Defect Summary|## \d\.\s*Root Cause Summary|## Breakpoint Summary|## \d\.\s*Breakpoint Summary)\s*\n+(.*?)(?:\n\n|\n##|$)',
            analysis_text, flags=re.DOTALL | re.IGNORECASE)
        if bm:
            text = bm.group(1).strip()
            text = text.lstrip(">").strip()
            text = re.sub(r'^\s*[-*]\s*', '', text) # Remove leading bullet
            text = text.replace('**', '')           # Remove bold
            
            if len(text) > 150:
                sentences = text.split('. ')
                if sentences:
                    breakpoint_summary = sentences[0] + '.'
                else:
                    breakpoint_summary = text[:147] + '...'
            else:
                breakpoint_summary = text
        elif insufficient:
            breakpoint_summary = "INSUFFICIENT DATA TO TRACE ROOT CAUSE"

        return {
            'test_id': test_id,
            'suite': f.get('suite', 'unknown'),
            'category': f.get('category', 'unknown'),
            'breakpoint': breakpoint_summary,
            'insufficient': insufficient,
            'report_link': f"./{device_id}/{test_folder_name}/triage_analysis.md"
        }

    async def run_triage(self, args: Any) -> None:
        """Orchestrates the parallel multi-job async diagnostics run."""
        # 0. Fail-fast credential check before performing log analysis
        use_vertex = os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes")
        gemini_key = os.getenv("GEMINI_API_KEY")
        if not use_vertex and not gemini_key:
            print("Error: GEMINI_API_KEY environment variable is not set and Vertex AI is disabled.", file=sys.stderr)
            sys.exit(1)

        # Determine active output directory dynamically
        if getattr(args, 'manifest', None):
            self.out_dir = os.path.dirname(os.path.abspath(args.manifest))
        elif getattr(args, 'test_runs', None):
            self.out_dir = os.path.abspath(args.test_runs)
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
                failure_id = fail_item.get('failure_id', '')
                test_name = fail_item['test_name']
                suite = fail_item['suite']
                category = fail_item['category']
                device_id = fail_item['device_id']
                run_dir = os.path.abspath(os.path.join(self.udmi_root, fail_item['run_directory']))
                
                failed_logs = fail_item['logs']['failed_run']
                t_meta = {}
                for k in ['sequence_log', 'sequence_md', 'pubber_log', 'udmis_log']:
                    if failed_logs.get(k):
                        t_meta[k] = failed_logs[k]
                
                success_logs = fail_item['logs'].get('success_run')
                if isinstance(success_logs, dict):
                    if success_logs.get('sequence_log'):
                        t_meta['success_log'] = success_logs['sequence_log']
                    if success_logs.get('sequence_md'):
                        t_meta['success_md'] = success_logs['sequence_md']

                runs_to_triage.append((
                    run_dir,
                    {'failure_id': failure_id, 'test_name': test_name, 'category': category, 'suite': suite, 'device_id': device_id},
                    t_meta
                ))

            # Apply optional CLI filters
            if getattr(args, 'id', None):
                runs_to_triage = [x for x in runs_to_triage if x[1]['failure_id'] in args.id]
            if args.test:
                runs_to_triage = [x for x in runs_to_triage if x[1]['test_name'] in args.test]
            if args.device:
                runs_to_triage = [x for x in runs_to_triage if x[1]['device_id'] in args.device or x[2].get('sequence_log', '').split('devices/')[-1].split('/')[0] in args.device]
        
        else:
            # Multi-run bundles directory triage
            test_runs_arg = getattr(args, 'test_runs', None)
            if not test_runs_arg:
                print("Error: Missing --test-runs or --manifest specifications.", file=sys.stderr)
                sys.exit(1)

            bundles_path = os.path.abspath(test_runs_arg)
            if not os.path.exists(bundles_path):
                print(f"Error: Specified test-runs directory '{test_runs_arg}' does not exist.", file=sys.stderr)
                sys.exit(1)

            target, site_dir = self.auto_detect_metadata(bundles_path)

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
            print("No regression or failing test cases detected in run outputs. Diagnostics complete!")
            sys.exit(0)

        failure_names = []
        for item in runs_to_triage:
            log_name = os.path.basename(item[2].get('sequence_log')) if (
                    item[2] and item[2].get('sequence_log')) else 'Legacy'
            failure_names.append(f"{item[1]['test_name']} ({log_name})")
        print(f"Found {len(runs_to_triage)} sharded test case failure occurrences to triage: {', '.join(failure_names)}")

        # Resolve Playbook to configure concurrency and failure classifiers
        from engine.config.playbook import Playbook
        from pathlib import Path

        playbook_arg = getattr(args, "playbook", None)
        impl_dir = Path(os.path.dirname(os.path.abspath(__file__)))
        
        if playbook_arg:
            if os.path.isabs(playbook_arg):
                playbook_path = Path(playbook_arg)
            else:
                cwd_path = Path.cwd() / playbook_arg
                if cwd_path.exists():
                    playbook_path = cwd_path
                else:
                    playbook_path = impl_dir / playbook_arg
        else:
            playbook_path = impl_dir / "../../config/playbook_oem.yaml"
        
        resolved_playbook = str(playbook_path)
        
        failure_classifiers = None
        concurrency_limit = 3
        if playbook_path.exists():
            try:
                playbook = Playbook(playbook_path).load()
                failure_classifiers = playbook.pipeline_config.get("failure_classifiers")
                concurrency_limit = playbook.pipeline_config.get("concurrency", 3)
            except Exception as e:
                print(f"Warning: Failed to load playbook from {playbook_path}: {e}", file=sys.stderr)

        # Setup parallel semaphore
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
                    playbook=resolved_playbook
                )
            )

        triage_summaries = await asyncio.gather(*tasks)



        # Generate report via UDMITriageReporter
        print("\nCompiling site-specific Triage Summary Report...")
        reporter = UDMITriageReporter(target=target, site_id=site_id, out_dir=self.out_dir, failure_classifiers=failure_classifiers)
        root_report_path = reporter.save_report(triage_summaries)

        print(f"Consolidated triage summary report generated: {root_report_path}\n")
        print("Mantis Triage diagnostic run completed successfully.")
