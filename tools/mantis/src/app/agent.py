# UDMI-specific Triage Agent, inheriting from the generic triage_harness pipeline
import asyncio
import os
import re
import sys
from pathlib import Path
from typing import List, Optional, Dict

from google import genai


from engine.pipeline import TriagePipeline, run_triage_session_async
from engine.config.playbook import Playbook
from engine.ui import color_text, GREEN


IMPL_DIR = os.path.dirname(os.path.abspath(__file__))  # src/app
SRC_DIR = os.path.dirname(IMPL_DIR)                  # src
MANTIS_DIR = os.path.dirname(SRC_DIR)                  # mantis root
UDMI_ROOT = os.path.dirname(os.path.dirname(MANTIS_DIR))                # udmi root



def parse_merged_logs_from_payload(prompt_payload: str) -> List[str]:
    m = re.search(r'## Chronologically Merged Global Logs \(Test Execution Context\)\s*\n*```text\n(.*?)\n```', prompt_payload, re.DOTALL)
    if m:
        return m.group(1).splitlines()
    return []

def build_deterministic_timeline(merged_raw_logs: List[str]) -> str:
    important_patterns = [
        r"Starting test",
        r"Ending test",
        r"RESULT",
        r"NOTICE",
        r"WARNING",
        r"ERROR",
        r"Exception",
        r"NullPointerException",
        r"JsonParseException",
        r"configTransaction",
        r"config update synchronized",
        r"State update",
        r"Config update",
        r"last_config",
        r"received config",
        r"State update defer",
        r"inFlight tokens",
        r"affinity",
        r"Munge config",
        r"Sharding state"
    ]
    regexes = [re.compile(p, re.IGNORECASE) for p in important_patterns]
    
    timeline_lines = []
    timeline_lines.append("## 1. Detailed Timeline of Events\n")
    timeline_lines.append("| Timestamp (UTC) | Source | Log Message / Event | Significance |")
    timeline_lines.append("| :--- | :--- | :--- | :--- |")
    
    for line in merged_raw_logs:
        line = line.strip()
        if not line:
            continue
            
        # Parse tag and content
        m = re.match(r'^\[([^\]]+)\]\s+([\d\-T:Z\.,\s]+)\s+(\w+)\s+(.*)$', line)
        if not m:
            m = re.match(r'^\[([^\]]+)\]\s+([\d\-T:Z\.,\s]+)\s+(.*)$', line)
        
        if m:
            tag = m.group(1)
            ts = m.group(2)
            content = m.group(len(m.groups()))
            
            # Check if important
            is_important = any(rx.search(line) for rx in regexes)
            if not is_important:
                continue
                
            # Clean up timestamp for display
            ts_display = ts.split("T")[-1].split("Z")[0]
            if "." in ts_display:
                ts_display = ts_display.split(".")[0]
            
            # Clean content
            clean_content = content.replace("|", "\\|").strip()
            if len(clean_content) > 200:
                clean_content = clean_content[:200] + "..."
            
            # Auto-determine significance
            sig = "Telemetry update or state synchronization event."
            if "starting test" in clean_content.lower():
                sig = "Test case execution initiated."
            elif "ending test" in clean_content.lower() or "terminating test" in clean_content.lower():
                sig = "Test case terminated."
            elif "result pass" in clean_content.lower():
                sig = "Test completed successfully (PASS)."
            elif "result fail" in clean_content.lower():
                sig = "Test failed (FAIL)."
            elif "adding configtransaction" in clean_content.lower() or "configtransaction" in clean_content.lower():
                sig = "New configuration transaction dispatched to database reflector."
            elif "nullpointerexception" in clean_content.lower():
                sig = "CRITICAL: Unhandled NullPointerException encountered."
            elif "jsonparseexception" in clean_content.lower():
                sig = "CRITICAL: JSON syntax parse error encountered."
            elif "inflight tokens" in clean_content.lower():
                sig = "WARNING: MQTT outbound channel congestion detected."
            elif "dropped" in clean_content.lower():
                sig = "CRITICAL: Message dropped due to queue overflow."
            elif "received config" in clean_content.lower():
                sig = "Device successfully received configuration payload."
            elif "config update synchronized" in clean_content.lower():
                sig = "Configuration synchronization verification."
            elif "munge config" in clean_content.lower():
                sig = "UDMIS ReflectProcessor routing and database update."
            elif "sharding state" in clean_content.lower():
                sig = "UDMIS StateProcessor sharding state sub-blocks."
            
            timeline_lines.append(f"| {ts_display} | {tag} | `{clean_content}` | {sig} |")
    
    if len(timeline_lines) <= 3:
        timeline_lines.append("| - | - | `[No significant event matches found in log slice]` | - |")
        
    return "\n".join(timeline_lines)

def get_workspace_root() -> str:
    curr = os.path.dirname(os.path.abspath(__file__))
    for _ in range(10):
        if os.path.exists(os.path.join(curr, "validator")):
            return curr
        parent = os.path.dirname(curr)
        if parent == curr:
            break
        curr = parent
    return os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../../.."))

def harvest_test_code_context(workspace_root: str, test_id: str, is_physical_device: bool = False) -> str:
    if not test_id:
        return ""
    
    sequences_dir = os.path.join(workspace_root, "validator/src/main/java/com/google/daq/mqtt/sequencer/sequences")
    if not os.path.exists(sequences_dir):
        return ""
        
    code_context = []
    code_context.append("\n## Deterministic Codebase Context (Target Test Definition)")
    
    target_file = None
    method_content = ""
    
    try:
        for filename in os.listdir(sequences_dir):
            if not filename.endswith(".java"):
                continue
            filepath = os.path.join(sequences_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
                    content = f.read()
                    if f"public void {test_id}(" in content:
                        target_file = filename
                        lines = content.splitlines()
                        start_idx = -1
                        for idx, line in enumerate(lines):
                            if f"public void {test_id}(" in line:
                                start_idx = max(0, idx - 2)
                                break
                        
                        if start_idx != -1:
                            slice_lines = lines[start_idx:start_idx + 120]
                            method_content = "\n".join(slice_lines)
                            break
            except Exception:
                pass
    except Exception:
        pass
            
    if target_file and method_content:
        code_context.append(f"### File: `validator/.../sequences/{target_file}`")
        code_context.append(f"```java\n{method_content}\n```")
    else:
        code_context.append("`[Target test method definition not found in validator/src/...]`")
        
    if not is_physical_device:
        etc_dir = os.path.join(workspace_root, "etc")
        if os.path.exists(etc_dir):
            code_context.append("\n### Golden Baseline Reference Outcomes (etc/ files)")
            for baseline_file in ["sequencer.out", "test_itemized.out"]:
                filepath = os.path.join(etc_dir, baseline_file)
                if os.path.exists(filepath):
                    try:
                        with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
                            lines = [line.strip() for line in f if test_id in line]
                            if lines:
                                code_context.append(f"**From `{baseline_file}`:**")
                                code_context.append("```text\n" + "\n".join(lines) + "\n```")
                    except Exception:
                        pass
                    
    return "\n".join(code_context)



class UDMITriagePipeline(TriagePipeline):
    """
    UDMI-specific implementation of the AI log triage pipeline.
    Sets up custom system prompts, guidelines, and tools optimized for the UDMI ecosystem.
    """

    def __init__(
        self,
        client: genai.Client,
        concurrency_semaphore: asyncio.Semaphore = None,
        playbook: Optional[Playbook] = None
    ):
        super().__init__(
            client=client,
            concurrency_semaphore=concurrency_semaphore,
            playbook=playbook
        )

    def run_intent_deterministically(self, target_id: str, prompt_payload: str) -> Optional[str]:
        ws_root = get_workspace_root()
        if ws_root:
            is_physical = "Global_Pubber_Log" not in prompt_payload or '"Global_Pubber_Log": false' in prompt_payload
            raw_context = harvest_test_code_context(ws_root, target_id, is_physical_device=is_physical)
            self.context["raw_intent_context"] = raw_context
        return None


async def run_triage_analysis_async(
    prompt_payload: str,
    concurrency_semaphore: asyncio.Semaphore = None,
    out_dir: Optional[str] = None,
    force: bool = False,
    oem: bool = False,
    namespace: Optional[str] = None,
    fallback_namespaces: Optional[List[str]] = None
) -> str:
    """
    Asynchronous entrypoint to run the UDMI triage pipeline.
    Uses the generic session orchestrator from the triage harness.
    """
    # Extract metadata for caching and output folders
    meta = {"Project ID": "unknown", "Site ID": "unknown", "Device ID": "unknown", "Test ID": "unknown"}
    for key in meta.keys():
        m = re.search(rf'- \*\*{key}\*\*: (.*)', prompt_payload)
        if m:
            meta[key] = m.group(1).strip()

    project_id = meta["Project ID"]
    site_id = meta["Site ID"]
    device_id = meta["Device ID"]
    test_id = meta["Test ID"]

    base_out = out_dir if out_dir else os.path.join(MANTIS_DIR, "out")
    out_dir = os.path.join(base_out, "diagnose", project_id, site_id,
                           device_id, test_id)

    # Construct failure query text (Test ID + Raw log content)
    log_match = re.search(
        r'## Local Sequencer log.log \(Raw Console\)\s*\n*```text\n(.*?)\n```',
        prompt_payload,
        re.DOTALL
    )
    log_content = log_match.group(1).strip() if log_match else ""

    # Fallback to sequence.md content if log.log is not available
    if not log_content:
        md_match = re.search(
            r'## Local Sequencer log.md \(Test Execution Details\)\s*\n*```markdown\n(.*?)\n```',
            prompt_payload,
            re.DOTALL
        )
        log_content = md_match.group(1).strip() if md_match else ""

    query_text = f"Test ID: {test_id}\nFailure logs:\n{log_content}" if log_content else ""

    metadata = {k.lower().replace(" ", "_"): v for k, v in meta.items()}

    # Load the declarative playbook
    impl_dir = Path(os.path.dirname(os.path.abspath(__file__)))
    playbook_file = "config/playbook_oem_integrator.yaml" if oem else "config/playbook.yaml"
    playbook_path = impl_dir / playbook_file

    ns = namespace
    if not ns:
        ns = os.path.basename(os.path.abspath(UDMI_ROOT)) or "global"

    return await run_triage_session_async(
        prompt_payload=prompt_payload,
        target_id=test_id,
        workspace_root=UDMI_ROOT,
        playbook_path=playbook_path,
        out_dir=out_dir,
        cache_query=query_text,
        metadata=metadata,
        force=force,
        concurrency_semaphore=concurrency_semaphore,
        pipeline_class=UDMITriagePipeline,
        exclude_dirs=["bridgehead"],
        include_files=["*.java", "*.py", "*.yaml"],
        namespace=ns,
        fallback_namespaces=fallback_namespaces
    )


def run_triage_analysis(prompt_payload: str) -> str:
    """Backward-compatible synchronous wrapper for legacy executions."""
    return asyncio.run(run_triage_analysis_async(prompt_payload))
