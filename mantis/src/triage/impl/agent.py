# UDMI-specific Triage Agent, inheriting from the generic triage_harness pipeline
import asyncio
import os
import re
import sys
from pathlib import Path
from typing import List, Optional

from google import genai

from .tools import expand_log_window
from .tools import git_read_operations
from .tools import grep_codebase
from .tools import grep_file
from .tools import list_directory
from .tools import read_file_lines
from ..harness.pipeline import TriagePipeline
from ..harness.config.playbook import Playbook
from ..harness.config.cache import SemanticCache
from ..harness.ui import color_text, GREEN

IMPL_DIR = os.path.dirname(os.path.abspath(__file__))  # src/triage/impl
TRIAGE_DIR = os.path.dirname(IMPL_DIR)                 # src/triage
SRC_DIR = os.path.dirname(TRIAGE_DIR)                  # src
MANTIS_DIR = os.path.dirname(SRC_DIR)                  # mantis root
UDMI_ROOT = os.path.dirname(MANTIS_DIR)                # udmi root


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
    return os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../.."))

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

# Tool maps for different diagnostic phases
PHASE1_TOOLS = {
    "read_file_lines": read_file_lines,
    "grep_file": grep_file
}

PHASE1_5_TOOLS = {
    "grep_codebase": grep_codebase,
    "read_file_lines": read_file_lines,
    "list_directory": list_directory
}

PHASE2_TOOLS = {
    "list_directory": list_directory,
    "grep_codebase": grep_codebase,
    "read_file_lines": read_file_lines,
    "git_read_operations": git_read_operations,
    "grep_file": grep_file,
    "expand_log_window": expand_log_window
}


class UDMITriagePipeline(TriagePipeline):
    """
    UDMI-specific implementation of the AI log triage pipeline.
    Sets up custom system prompts, guidelines, and tools optimized for the UDMI ecosystem.
    """

    def __init__(
        self,
        client: genai.Client,
        skills_dir: Path,
        concurrency_semaphore: asyncio.Semaphore = None,
        playbook: Optional[Playbook] = None
    ):
        super().__init__(
            client=client,
            skills_dir=skills_dir,
            concurrency_semaphore=concurrency_semaphore,
            playbook=playbook
        )

    def build_timeline_deterministically(self, prompt_payload: str) -> Optional[str]:
        raw_logs = parse_merged_logs_from_payload(prompt_payload)
        if raw_logs:
            return build_deterministic_timeline(raw_logs)
        return None

    def harvest_intent_deterministically(self, target_id: str, prompt_payload: str) -> Optional[str]:
        ws_root = get_workspace_root()
        if ws_root:
            is_physical = "Global_Pubber_Log" not in prompt_payload or '"Global_Pubber_Log": false' in prompt_payload
            return harvest_test_code_context(ws_root, target_id, is_physical_device=is_physical)
        return None


async def run_triage_analysis_async(
    prompt_payload: str,
    concurrency_semaphore: asyncio.Semaphore = None,
    out_dir: Optional[str] = None,
    force: bool = False
) -> str:
    """
    Asynchronous entrypoint to run the UDMI triage pipeline.
    Supports dynamic playbooks, vectorized semantic caching, and intelligent rate limits.
    """
    use_vertex = os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes")
    token = os.getenv("GEMINI_API_KEY")
    if not token and not use_vertex:
        print("Error: Neither GEMINI_API_KEY nor MANTIS_USE_VERTEXAI environment variables are set.", file=sys.stderr)
        print("To use Vertex AI (Google Cloud enterprise quota with ADC), please 'export MANTIS_USE_VERTEXAI=true'.", file=sys.stderr)
        sys.exit(1)

    # Load the declarative playbook
    impl_dir = Path(os.path.dirname(os.path.abspath(__file__)))
    playbook_path = impl_dir / "config/playbook.yaml"

    playbook = None
    if playbook_path.exists():
        try:
            playbook = Playbook(playbook_path).load()
            print(f"[Playbook] Loaded playbook '{playbook.metadata.get('name')}' successfully.")
        except Exception as e:
            print(f"Warning: Failed to load playbook: {e}", file=sys.stderr)

    skills_dir = Path(os.path.dirname(os.path.abspath(__file__))) / "skills"
    
    if use_vertex:
        project_id = os.getenv("GCP_PROJECT") or os.getenv("GCLOUD_PROJECT")
        location = os.getenv("GCP_LOCATION", "us-central1")
        print(f"[Vertex AI] Initializing enterprise GenAI Client (project: {project_id or 'Auto-detect'}, location: {location})...")
        client = genai.Client(vertexai=True, project=project_id, location=location)
    else:
        client = genai.Client()

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

    # --- Vectorized Semantic Cache Lookup ---
    cache_filepath = Path(MANTIS_DIR) / "out" / "diagnose" / "semantic_cache.json"
    embed_model = "text-embedding-004" if use_vertex else "models/gemini-embedding-2"
    cache = SemanticCache(client, cache_filepath, embedding_model=embed_model)
    await cache.load_async()

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

    query_text = f"Test ID: {test_id}\nFailure logs:\n{log_content}"

    # Only perform semantic lookup if we actually have failure log content to compare and not forced
    if log_content and force:
        print(f"[{test_id}] Cache bypass requested via --force flag. Skipping cache lookup.")

    if log_content and not force:
        try:
            print(f"[{test_id}] Querying semantic cache...")
            cached_entry, score = await cache.lookup(query_text)
            if cached_entry:
                print(
                    "🚀 " + color_text(f"[Cache Hit] Found highly similar flakiness cluster! (similarity: {score:.3f})", GREEN, bold=True) + "\n"
                    "🚀 Delivering zero-shot triage report in milliseconds..."
                )
                # Save the cached report to the destination folder so it remains consistent
                os.makedirs(out_dir, exist_ok=True)
                report_filepath = os.path.join(out_dir, "triage_analysis.md")
                with open(report_filepath, 'w', encoding='utf-8') as fr:
                    fr.write(cached_entry["triage_report"])

                return cached_entry["triage_report"]
            else:
                print(f"[{test_id}] Cache miss (best similarity score: {score:.3f}). Running GenAI pipeline...")
        except Exception as e:
            print(f"Warning: Semantic cache lookup failed: {e}", file=sys.stderr)
    else:
        print(f"[{test_id}] No failure log content found in payload. Skipping cache lookup.")

    # Initialize pipeline with playbook if present
    pipeline = UDMITriagePipeline(
        client=client,
        skills_dir=skills_dir,
        concurrency_semaphore=concurrency_semaphore,
        playbook=playbook
    )
    if playbook:
        # Ensure engine uses playbook defined model
        pipeline.engine.model_name = playbook.pipeline_config.get("default_model", pipeline.engine.model_name)

    # Load ToolBelt to construct full tool map
    from .tools import ToolBelt
    tool_belt = ToolBelt(workspace_root=UDMI_ROOT, exclude_dirs=["bridgehead"], include_files=["*.java", "*.py", "*.yaml"])
    available_tools = tool_belt.get_tools_map()

    analysis_report = await pipeline.run_dynamic_pipeline_async(
        target_id=test_id,
        prompt_payload=prompt_payload,
        available_tools=available_tools,
        out_dir=out_dir
    )

    # --- Save successful diagnostic to cache ---
    # Only cache if the report is successful (contains actual root cause and not the insufficient data warning)
    if log_content and "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE" not in analysis_report:
        try:
            print(f"[{test_id}] Caching successful triage analysis...")
            await cache.add(
                failure_text=query_text,
                triage_report=analysis_report,
                metadata={
                    "project_id": project_id,
                    "site_id": site_id,
                    "device_id": device_id,
                    "test_id": test_id,
                }
            )
        except Exception as e:
            print(f"Warning: Failed to save triage analysis to cache: {e}", file=sys.stderr)

    return analysis_report


def run_triage_analysis(prompt_payload: str) -> str:
    """Backward-compatible synchronous wrapper for legacy executions."""
    return asyncio.run(run_triage_analysis_async(prompt_payload))
