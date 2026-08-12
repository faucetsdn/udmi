"""
Mantis Interactive Diagnostic Chat Session and Orchestration Agent.
"""

import asyncio
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

from google import genai
from google.genai import types

from mantis.agent.agent import build_deterministic_timeline, get_workspace_root, harvest_test_code_context
from mantis.agent.extractor import UDMIEntityExtractor
from mantis.agent.prompts import build_udmi_system_prompt
from mantis.engine.constants import DEFAULT_GEMINI_PRO_MODEL, get_udmi_root
from mantis.engine.engine import AsyncTriageEngine
from mantis.engine.harness.credentials import EnvCredentialsProvider
from mantis.engine.tools import ToolBelt
from mantis.engine.ui import BLUE, CYAN, GREEN, MAGENTA, RED, YELLOW, color_text, print_mantis_banner
from mantis.tools.artifacts import list_available_test_runs as list_impl
from mantis.tools.artifacts import locate_test_artifacts as locate_impl
from mantis.tools.resolver import UDMILogResolver, UDMIResultParser
from mantis.tools.schemas import inspect_udmi_schema as inspect_schema_impl
from mantis.tools.schemas import list_udmi_schemas as list_schemas_impl
from mantis.tools.site_models import inspect_site_model as inspect_site_impl
from mantis.tools.site_models import list_site_devices as list_devices_impl
from mantis.tools.traces import inspect_message_trace as inspect_trace_impl
from mantis.tools.cloud_logs import pull_cloud_logs_for_test as pull_cloud_logs_impl


class MantisChatSession:
    """
    Interactive multi-turn conversational diagnostic session with Mantis.
    Combines live LLM orchestration, dynamic tool calling (codebase inspection,
    log harvesting, git history analysis), and session state management.
    """

    def __init__(
        self,
        udmi_root: Optional[str] = None,
        mantis_dir: Optional[str] = None,
        client: Optional[genai.Client] = None,
        model_name: str = DEFAULT_GEMINI_PRO_MODEL,
        manifest_path: Optional[str] = None,
        test_runs_dir: Optional[str] = None,
        device_id: Optional[str] = None,
        test_id: Optional[str] = None,
        site_model: Optional[str] = None,
        project_spec: Optional[str] = None,
        playbook_path: Optional[str] = None,
        use_vertex: Optional[bool] = None,
        gcp_project: Optional[str] = None,
        gcp_location: Optional[str] = None
    ):
        self.udmi_root = os.path.abspath(udmi_root or get_udmi_root() or os.getcwd())
        self.mantis_dir = os.path.abspath(mantis_dir or os.path.join(self.udmi_root, "util", "mantis"))
        self.model_name = model_name
        self.manifest_path = os.path.abspath(manifest_path) if manifest_path else None
        self.test_runs_dir = os.path.abspath(test_runs_dir) if test_runs_dir else None
        self.active_device = device_id
        self.active_test = test_id
        self.active_site_model = site_model
        self.project_spec = project_spec
        self.playbook_path = playbook_path
        self.gcp_project = gcp_project
        self.gcp_location = gcp_location

        # Resolve GenAI Client
        if client:
            self.client = client
            self.provider = None
        else:
            self.provider = EnvCredentialsProvider(
                use_vertex=use_vertex,
                project=gcp_project,
                location=gcp_location
            )
            self.client = self.provider.get_client()

        # Engine & Session State
        self.engine = AsyncTriageEngine(
            client=self.client,
            model_name=self.model_name,
            enable_condensation=True,
            enable_history_compaction=True
        )
        self.history: List[types.Content] = []
        self.manifest_data: Optional[Dict[str, Any]] = None
        self.tools_map: Dict[str, Callable] = {}
        self.is_cancelled = False

        self._init_tools()
        self.extractor = UDMIEntityExtractor(self.udmi_root)

        if self.manifest_path and os.path.exists(self.manifest_path):
            self.load_manifest(self.manifest_path)
        elif self.test_runs_dir and os.path.exists(self.test_runs_dir):
            potential_manifest = os.path.join(self.test_runs_dir, "triage_manifest.json")
            if os.path.exists(potential_manifest):
                self.load_manifest(potential_manifest)

        self._refresh_system_prompt()

    def cancel(self) -> None:
        """Signals active streaming or tool execution loops to immediately abort."""
        self.is_cancelled = True


    def _refresh_client(self) -> None:
        """Refreshes the GenAI Client instance to ensure clean event loop binding across multi-turn requests."""
        if hasattr(self, 'provider') and self.provider:
            self.client = self.provider.get_client()
            if hasattr(self, 'engine') and self.engine:
                self.engine.client = self.client
                self.engine._semaphore = None

    def _init_tools(self) -> None:

        """Initializes standard and domain-specific diagnostic tools."""
        toolbelt = ToolBelt(
            workspace_root=self.udmi_root,
            exclude_dirs=["bridgehead", "node_modules", "dist", ".git", ".idea"],
            include_files=["*.java", "*.py", "*.yaml", "*.json", "*.md", "*.log"]
        )
        self.tools_map = toolbelt.get_tools_map()

        # Register specialized Mantis diagnostic tools
        self.tools_map["get_triage_manifest_summary"] = self.get_triage_manifest_summary
        self.tools_map["get_test_source_code"] = self.get_test_source_code
        self.tools_map["get_test_event_timeline"] = self.get_test_event_timeline
        self.tools_map["locate_test_artifacts"] = self.locate_test_artifacts
        self.tools_map["get_test_execution_summary"] = self.get_test_execution_summary
        self.tools_map["list_available_test_runs"] = self.list_available_test_runs
        self.tools_map["compare_test_sequences"] = self.compare_test_sequences
        self.tools_map["get_historical_site_state"] = self.get_historical_site_state
        self.tools_map["inspect_udmi_schema"] = self.inspect_udmi_schema
        self.tools_map["list_udmi_schemas"] = self.list_udmi_schemas
        self.tools_map["inspect_site_model"] = self.inspect_site_model
        self.tools_map["list_site_devices"] = self.list_site_devices
        self.tools_map["inspect_message_trace"] = self.inspect_message_trace
        self.tools_map["pull_cloud_logs"] = self.pull_cloud_logs
        self.tools_map["get_active_session_context"] = self.get_active_session_context



    def pull_cloud_logs(
        self,
        test_id: Optional[str] = None,
        site_name: Optional[str] = None,
        device_id: Optional[str] = None,
        project: Optional[str] = None,
        service: str = "udmis",
        padding_sec: int = 60
    ) -> str:
        """Tool: Queries GCP Cloud Logging for container logs (e.g. udmis, validator) for a specific test execution window."""
        tst = test_id or self.active_test
        dev = device_id or self.active_device
        sname = site_name or self.active_site_model
        if not tst:
            return "Error: No test_id specified and no active test selected in session."
        return pull_cloud_logs_impl(
            udmi_root=self.udmi_root,
            test_id=tst,
            site_name=sname,
            device_id=dev,
            project=project or self.gcp_project,
            service=service,
            padding_sec=padding_sec
        )


    def inspect_udmi_schema(self, schema_name: str) -> str:
        """Tool: Inspects the JSON schema definition for a specified UDMI message type."""
        return inspect_schema_impl(udmi_root=self.udmi_root, schema_name=schema_name)

    def list_udmi_schemas(self, filter_keyword: Optional[str] = None) -> str:
        """Tool: Lists all available UDMI JSON schemas, optionally filtered by keyword."""
        return list_schemas_impl(udmi_root=self.udmi_root, filter_keyword=filter_keyword)

    def inspect_site_model(self, site_name: Optional[str] = None, device_id: Optional[str] = None) -> str:
        """Tool: Inspects site cloud_iot_config.json or device metadata.json for a site model."""
        sname = site_name or self.active_site_model or "sites/udmi_site_model"
        dev = device_id or self.active_device
        return inspect_site_impl(udmi_root=self.udmi_root, site_name=sname, device_id=dev)

    def list_site_devices(self, site_name: Optional[str] = None) -> str:
        """Tool: Lists all configured devices and point counts for a site model."""
        sname = site_name or self.active_site_model or "sites/udmi_site_model"
        return list_devices_impl(udmi_root=self.udmi_root, site_name=sname)

    def inspect_message_trace(
        self,
        site_name: Optional[str] = None,
        device_id: Optional[str] = None,
        test_name: Optional[str] = None,
        message_type: Optional[str] = None
    ) -> str:
        """Tool: Inspects recorded MQTT message payloads (events, state, config) captured during a test."""
        sname = site_name or self.active_site_model
        dev = device_id or self.active_device
        tname = test_name or self.active_test
        return inspect_trace_impl(
            udmi_root=self.udmi_root,
            site_name=sname,
            device_id=dev,
            test_name=tname,
            message_type=message_type
        )

    def locate_test_artifacts(self, site_model: Optional[str] = None, device_id: Optional[str] = None, test_id: Optional[str] = None) -> str:
        """Tool: Autonomously scans all site output directories and global out/ to discover exact paths to test logs."""
        site = site_model or self.active_site_model
        dev = device_id or self.active_device
        tst = test_id or self.active_test
        data = locate_impl(
            udmi_root=self.udmi_root,
            site=site,
            device=dev,
            test=tst,
            run_dir=self.test_runs_dir
        )
        return json.dumps(data, indent=2)

    def list_available_test_runs(
        self,
        site_model: Optional[str] = None,
        device_id: Optional[str] = None,
        test_filter: Optional[str] = None
    ) -> str:
        """Tool: Returns a compact, high-level summary of test runs grouped by site and device, with optional filtering."""
        site = site_model or self.active_site_model
        dev = device_id or self.active_device
        return list_impl(
            udmi_root=self.udmi_root,
            site=site,
            device=dev,
            test_filter=test_filter
        )

    def compare_test_sequences(
        self,
        test_id: Optional[str] = None,
        device_id: Optional[str] = None,
        run_b_commit: Optional[str] = None,
        run_b_dir: Optional[str] = None,
        site_name: Optional[str] = None
    ) -> str:
        """Tool: Performs behavioral differential log analysis by mapping and comparing protocol event sequences between a reference run and current run."""
        from mantis.tools.differential import compare_test_sequences as compare_impl
        tst = test_id or self.active_test
        dev = device_id or self.active_device
        site = site_name or self.active_site_model
        if not tst or not dev:
            return "Error: compare_test_sequences requires both `test_id` and `device_id` (e.g. test_id='pointset_publish', device_id='EM-11')."
        return compare_impl(
            udmi_root=self.udmi_root,
            test_id=tst,
            device_id=dev,
            run_b_commit=run_b_commit,
            run_b_dir=run_b_dir,
            site_name=site
        )

    def get_historical_site_state(
        self,
        timestamp: str,
        site_name: Optional[str] = None,
        device_id: Optional[str] = None
    ) -> str:
        """Tool: Reconstructs the site model configuration and device metadata as they existed in Git history at or before the given ISO timestamp of the test execution."""
        from mantis.tools.differential import get_historical_site_state as hist_impl
        site = site_name or self.active_site_model
        if not site:
            return "Error: get_historical_site_state requires a `site_name` parameter (e.g. 'sites/site_model_name')."
        dev = device_id or self.active_device

        return hist_impl(
            udmi_root=self.udmi_root,
            site_name=site,
            timestamp=timestamp,
            device_id=dev
        )


    def get_test_execution_summary(
        self,
        test_id: Optional[str] = None,
        device_id: Optional[str] = None,
        site_name: Optional[str] = None
    ) -> str:
        """Tool: Instantly extracts a high-density, precise execution summary of a specific test run (start/end lines, timestamps, transactions, errors, and final RESULT)."""
        from mantis.tools.artifacts import get_test_execution_summary as summary_impl
        tst = test_id or self.active_test
        dev = device_id or self.active_device
        site = site_name or self.active_site_model
        if not tst or not dev:
            return "Error: get_test_execution_summary requires both `test_id` and `device_id`."
        return summary_impl(
            udmi_root=self.udmi_root,
            test_id=tst,
            device_id=dev,
            site_name=site
        )





    def load_manifest(self, manifest_path: str) -> str:
        """Loads a triage manifest into the active session context."""
        try:
            with open(manifest_path, 'r', encoding='utf-8') as f:
                self.manifest_data = json.load(f)
            self.manifest_path = manifest_path

            meta = self.manifest_data.get("metadata", {})
            if meta.get("site_id") and not self.active_site_model:
                self.active_site_model = meta.get("site_id")

            failures = self.manifest_data.get("failures", [])
            if failures and not self.active_test:
                self.active_test = failures[0].get("test_name")
            if failures and not self.active_device:
                self.active_device = failures[0].get("device_id")

            self._refresh_system_prompt()
            return f"Loaded manifest from '{manifest_path}' with {len(failures)} identified failure entries."
        except Exception as e:
            return f"Error loading manifest: {e}"

    def get_triage_manifest_summary(self) -> str:
        """Tool: Returns a structured summary of identified failures from the loaded triage manifest."""
        if not self.manifest_data:
            return "No triage manifest currently loaded in this session."

        meta = self.manifest_data.get("metadata", {})
        failures = self.manifest_data.get("failures", [])

        lines = [
            "### Triage Manifest Summary",
            f"- **Target Project**: `{meta.get('target_project', 'unknown')}`",
            f"- **Site ID**: `{meta.get('site_id', 'unknown')}`",
            f"- **Total Identified Failures/Flakes**: {len(failures)}\n",
            "| Device | Test Case | Flake Rate | Primary Failure Log |",
            "| :--- | :--- | :--- | :--- |"
        ]
        for f in failures:
            dev = f.get("device_id", "N/A")
            test = f.get("test_name", "N/A")
            rate = f.get("flake_rate", "N/A")
            log_path = f.get("log_path", "N/A")
            lines.append(f"| {dev} | `{test}` | {rate} | `{os.path.basename(log_path)}` |")

        return "\n".join(lines)

    def get_test_source_code(self, test_id: Optional[str] = None) -> str:
        """Tool: Retrieves the Java source code definition of a test case from validator sequences."""
        target = test_id or self.active_test
        if not target:
            return "Error: No test_id specified and no active test selected in session."
        return harvest_test_code_context(self.udmi_root, target)

    def get_test_event_timeline(
        self,
        site_model: Optional[str] = None,
        device_id: Optional[str] = None,
        test_id: Optional[str] = None
    ) -> str:
        """Tool: Generates a deterministic event timeline from test logs for the specified device and test."""
        site = site_model or self.active_site_model
        dev = device_id or self.active_device
        tst = test_id or self.active_test

        artifacts_res = locate_impl(
            udmi_root=self.udmi_root,
            site=site,
            device=dev,
            test=tst,
            run_dir=self.test_runs_dir
        )
        art = artifacts_res.get("artifacts", {})

        found_logs = []
        priority_files = []
        if art.get("sequence_log"):
            priority_files.append(os.path.join(self.udmi_root, art["sequence_log"]))
        if art.get("device_system_log"):
            priority_files.append(os.path.join(self.udmi_root, art["device_system_log"]))
        if art.get("pubber_log"):
            priority_files.append(os.path.join(self.udmi_root, art["pubber_log"]))
        if art.get("udmis_log"):
            priority_files.append(os.path.join(self.udmi_root, art["udmis_log"]))

        for fpath in priority_files:
            if os.path.exists(fpath):
                try:
                    with open(fpath, "r", encoding="utf-8", errors="replace") as lf:
                        found_logs.extend(lf.readlines())
                except Exception:
                    pass

        if not found_logs:
            search_dirs = []
            if self.test_runs_dir:
                search_dirs.append(self.test_runs_dir)
            search_dirs.append(os.path.join(self.udmi_root, "out"))

            for sdir in search_dirs:
                if not os.path.exists(sdir):
                    continue
                for root, _, files in os.walk(sdir):
                    if dev and dev not in root:
                        continue
                    if tst and tst not in root:
                        continue
                    for f in files:
                        if f in ("sequence.log", "sequencer.log", "log.log", "sequence.md", "pubber.log", "udmis.log"):
                            fpath = os.path.join(root, f)
                            try:
                                with open(fpath, "r", encoding="utf-8", errors="replace") as lf:
                                    found_logs.extend(lf.readlines())
                            except Exception:
                                pass

        if not found_logs:
            return f"No execution log files found for site='{site}', device='{dev}', test='{tst}'."

        return build_deterministic_timeline(found_logs[:2000])

    def get_active_session_context(self) -> str:
        """Tool: Returns the current active session state, including active device, test case, site model, project spec, and execution mode."""
        is_cloud = bool(self.project_spec and "localhost" not in self.project_spec)
        return json.dumps({
            "workspace_root": self.udmi_root,
            "manifest_path": self.manifest_path,
            "test_runs_dir": self.test_runs_dir,
            "active_device": self.active_device or "None",
            "active_test": self.active_test or "None",
            "active_site_model": self.active_site_model or "None",
            "project_spec": self.project_spec or "None",
            "execution_mode": "CLOUD" if is_cloud else "LOCAL",
            "cloud_logging_recommended": is_cloud
        }, indent=2)

    def _refresh_system_prompt(self) -> None:
        """Compiles the system prompt incorporating the active session context and UDMI domain knowledge."""
        self.system_prompt = build_udmi_system_prompt(
            workspace_root=self.udmi_root,
            active_site_model=self.active_site_model,
            active_device=self.active_device,
            active_test=self.active_test,
            project_spec=self.project_spec,
            manifest_path=self.manifest_path,
            test_runs_dir=self.test_runs_dir
        )

    async def send_message(self, user_message: str) -> str:
        """Sends a user message to Mantis, executes tool-calling loops, and returns the response."""
        self._refresh_client()
        # Dynamic entity extraction from user prompt
        entities = self.extractor.extract_entities(user_message, current_site=self.active_site_model)
        changed = False
        if entities.get("site_model") and entities["site_model"] != self.active_site_model:
            self.active_site_model = entities["site_model"]
            changed = True
        if entities.get("device_id") and entities["device_id"] != self.active_device:
            self.active_device = entities["device_id"]
            changed = True
        if entities.get("test_id") and entities["test_id"] != self.active_test:
            self.active_test = entities["test_id"]
            changed = True

        if changed:
            self._refresh_system_prompt()

        user_content = types.Content(
            role="user",
            parts=[types.Part.from_text(text=user_message)]
        )
        self.history.append(user_content)

        response_text = await self.engine.execute_loop(
            system_instruction=self.system_prompt,
            history=self.history,
            tools_map=self.tools_map,
            model_name=self.model_name
        )
        return response_text

    async def send_message_stream(self, user_message: str):
        """
        Sends a user message and yields real-time streaming events (context updates,
        thoughts, tool activations, text tokens, completion) for UI consumption.
        """
        self._refresh_client()
        entities = self.extractor.extract_entities(user_message, current_site=self.active_site_model)
        changed = False
        if entities.get("site_model") and entities["site_model"] != self.active_site_model:
            self.active_site_model = entities["site_model"]
            changed = True
        if entities.get("device_id") and entities["device_id"] != self.active_device:
            self.active_device = entities["device_id"]
            changed = True
        if entities.get("test_id") and entities["test_id"] != self.active_test:
            self.active_test = entities["test_id"]
            changed = True

        if changed:
            self._refresh_system_prompt()

        yield {
            "type": "context_update",
            "site_model": self.active_site_model,
            "device_id": self.active_device,
            "test_id": self.active_test
        }

        user_content = types.Content(
            role="user",
            parts=[types.Part.from_text(text=user_message)]
        )
        self.history.append(user_content)

        self.is_cancelled = False
        full_text_accum = []
        async for event in self.engine.execute_loop_stream(
            system_instruction=self.system_prompt,
            history=self.history,
            tools_map=self.tools_map,
            model_name=self.model_name
        ):
            if self.is_cancelled:
                yield {"type": "stopped", "message": "Generation stopped by user"}
                break
            if event.get('type') == 'token' and event.get('text'):
                full_text_accum.append(event['text'])
            elif event.get('type') == 'done':
                final_text = event.get('full_text') or "".join(full_text_accum)
                if final_text:
                    self.history.append(types.Content(
                        role="model",
                        parts=[types.Part.from_text(text=final_text)]
                    ))
            yield event




    async def run_critique(self, focus_notes: str = "", previous_report: str = "") -> str:
        """
        Executes an automated adversarial critique & verification pass over the last diagnostic report.
        Challenges assumptions, re-examines test isolation, and performs differential git comparisons.
        """
        self._refresh_client()

        # Find last model response from history
        last_model_text = ""
        for msg in reversed(self.history):
            if msg.role == "model" and msg.parts:
                texts = [p.text for p in msg.parts if hasattr(p, 'text') and p.text and p.text.strip()]
                if texts:
                    last_model_text = "\n".join(texts)
                    break

        if not last_model_text and previous_report:
            last_model_text = previous_report.strip()

        if not last_model_text:
            return "No previous diagnostic report found in this session to fact-check. Ask Mantis for a diagnosis first!"


        critique_instruction = f"""You are an Adversarial Diagnostic Critic and Senior Peer Reviewer for the UDMI platform.
Your job is to rigorously audit, stress-test, and fact-check the previous diagnostic report against ground-truth logs, schemas, site models, and test sequences.

================ PREVIOUS DIAGNOSTIC REPORT ================
{last_model_text}
============================================================

Additional User Focus / Suspicions:
{focus_notes or "Perform a comprehensive claim-by-claim evidence audit and alternative hypothesis verification."}

CRITICAL REVIEW MANDATES:
1. DO NOT simply generate another generic diagnostic report or repeat the 3-part (Core Issue / Root Cause / Recommended Fix) template.
2. DO NOT conclude with "Would you like a full detailed RCA report...".
3. Claim-by-Claim Verification:
   - Extract every specific factual claim and technical assumption made in the previous report (e.g. device silence, gateway topology, sample rates, schema errors, state synchronization).
   - Use your diagnostic tools (`inspect_message_trace`, `inspect_site_model`, `inspect_udmi_schema`, `get_test_execution_summary`, `pull_cloud_logs`) to verify each claim against raw evidence.
   - Explicitly classify each claim as `✅ CONFIRMED`, `❌ REFUTED`, or `⚠️ UNVERIFIED ASSUMPTION` with direct proof (timestamps, log lines, JSON keys).
4. Alternative Hypotheses & Edge Cases:
   - Challenge the root cause. Did the previous analysis overlook alternative explanations (e.g. sequencer cutoff timestamp threshold race condition, proxy packet drop, gateway addressing mismatch, or non-fatal schema warnings vs fatal assertion failures)?
5. Required Output Format:
   ### 🛡️ Diagnostic Fact-Check & Verification Audit

   #### 1. 🔍 Claim-by-Claim Evidence Audit
   - **Claim 1: [Specific claim from previous report]**
     * **Verdict:** `✅ CONFIRMED` / `❌ REFUTED` / `⚠️ UNVERIFIED ASSUMPTION`
     * **Evidence:** [Exact log snippet, timestamp, schema property, or metadata file proof]
   - **Claim 2: ...**

   #### 2. 🔬 Stress-Testing & Alternative Hypotheses
   - **[Alternative Hypothesis 1]**: [Tested and ruled out / confirmed with evidence]
   - **[Alternative Hypothesis 2]**: [Tested and ruled out / confirmed with evidence]

   #### 3. 🏁 Fact-Check Verdict & Refinements
   - **Verdict:** `VERIFIED (Grounded & Sound)` or `REVISED (Assumptions Corrected)`
   - **Key Takeaways & Confirmed Root Cause:** [Grounded technical summary with confirmed next steps]
"""


        user_content = types.Content(
            role="user",
            parts=[types.Part.from_text(text=f"Please execute a formal critique and re-analysis of your previous diagnosis. Focus: {focus_notes or 'Check test isolation, verify raw sequence.log evidence, and perform differential git comparison.'}")]
        )
        self.history.append(user_content)

        response_text = await self.engine.execute_loop(
            system_instruction=critique_instruction,
            history=self.history,
            tools_map=self.tools_map,
            model_name=self.model_name
        )
        return response_text


    def handle_slash_command(self, cmd_line: str) -> Tuple[bool, str]:
        """
        Handles interactive REPL slash commands (/site, /load, /device, /test, /tools, /context, /clear, /export).
        Returns (is_handled, response_output).
        """
        parts = cmd_line.strip().split(maxsplit=1)
        cmd = parts[0].lower()
        arg = parts[1].strip() if len(parts) > 1 else ""

        if cmd in ("/help", "/?"):
            help_text = """
Mantis Quick Commands:
  /help, /?            - Show this help message
  /diagnose [test]     - Diagnose test failure for active target or specified test
  /diff [baseline]     - Run differential analysis between 2 sets of logs or against baseline
  /fact-check [notes]  - Perform an automated adversarial fact-check & verification pass (alias: /critique)
  /site <site>         - Set or switch active site model (e.g. sites/udmi_site_model)
  /device <id>         - Set or switch active target device ID (e.g. AHU-1)
  /test <name>         - Set or switch active test case name (e.g. system_min_loglevel)
  /load <path>         - Load a test run bundle directory or triage_manifest.json
  /tools               - List all available diagnostic tools
  /current-context     - View current session configuration & targets (alias: /context)
  /clear               - Clear conversation history
  /export [path]       - Export chat history transcript to a markdown file
  /exit, /quit         - Exit interactive chat session
"""
            return True, help_text.strip()



        elif cmd == "/site":
            if not arg:
                return True, f"Active Site Model: {self.active_site_model or 'None'}\nUsage: /site <site_name_or_path>"
            self.active_site_model = arg
            self._refresh_system_prompt()
            return True, f"Active site model set to `{arg}`."

        elif cmd == "/load":
            if not arg:
                return True, "Usage: /load <path/to/triage_manifest.json or run_directory>"
            target_path = os.path.abspath(os.path.expanduser(arg))
            if not os.path.exists(target_path):
                return True, f"Error: Path '{target_path}' does not exist."
            if os.path.isfile(target_path) and target_path.endswith(".json"):
                res = self.load_manifest(target_path)
            elif os.path.isdir(target_path):
                self.test_runs_dir = target_path
                potential_manifest = os.path.join(target_path, "triage_manifest.json")
                if os.path.exists(potential_manifest):
                    res = self.load_manifest(potential_manifest)
                else:
                    self._refresh_system_prompt()
                    res = f"Set test runs directory to '{target_path}'."
            else:
                res = f"Error: Unrecognized file type for '{target_path}'."
            return True, res

        elif cmd == "/device":
            if not arg:
                return True, f"Active Device: {self.active_device or 'None'}\nUsage: /device <device_id>"
            self.active_device = arg
            self._refresh_system_prompt()
            return True, f"Active device set to `{arg}`."

        elif cmd == "/test":
            if not arg:
                return True, f"Active Test: {self.active_test or 'None'}\nUsage: /test <test_name>"
            self.active_test = arg
            self._refresh_system_prompt()
            return True, f"Active test case set to `{arg}`."

        elif cmd == "/tools":
            tool_names = sorted(list(self.tools_map.keys()))
            tools_list = "\n".join([f"  - `{t}`" for t in tool_names])
            return True, f"Registered Diagnostic Tools ({len(tool_names)}):\n{tools_list}"

        elif cmd in ("/current-context", "/context"):
            return True, f"Current Session Context:\n{self.get_active_session_context()}"


        elif cmd == "/clear":
            self.history.clear()
            return True, "Conversation history cleared."

        elif cmd in ("/exit", "/quit"):
            return True, "__EXIT__"


        elif cmd == "/export":
            export_path = arg or f"mantis_chat_{datetime.now().strftime('%Y%m%d_%H%M%S')}.md"
            export_path = os.path.abspath(export_path)
            try:
                with open(export_path, 'w', encoding='utf-8') as f:
                    f.write("# Mantis Diagnostic Chat Transcript\n\n")
                    f.write(f"- **Timestamp**: `{datetime.now().isoformat()}`\n")
                    f.write(f"- **Site**: `{self.active_site_model or 'N/A'}`\n")
                    f.write(f"- **Device**: `{self.active_device or 'N/A'}`\n")
                    f.write(f"- **Test**: `{self.active_test or 'N/A'}`\n\n---\n\n")
                    for msg in self.history:
                        role_title = "### 👤 User" if msg.role == "user" else "### 🦗 Mantis"
                        f.write(f"{role_title}\n\n")
                        for p in msg.parts:
                            if p.text:
                                f.write(f"{p.text}\n\n")
                return True, f"Conversation exported to `{export_path}`."
            except Exception as e:
                return True, f"Error exporting transcript: {e}"


        return False, ""

    async def run_interactive_repl(self) -> None:
        """Starts the interactive terminal chat loop with colorful ANSI feedback."""
        print_mantis_banner()
        print(color_text("🦗 Welcome to Mantis Interactive Diagnostic Chat Mode!", GREEN, bold=True))
        print(color_text("Type your question, or use /help to inspect available session commands.\n", CYAN))

        if self.active_site_model or self.active_device or self.active_test:
            print(color_text(f"Session Initialized with Context:", YELLOW))
            if self.active_site_model:
                print(f"  • Site Model: {color_text(self.active_site_model, CYAN)}")
            if self.active_device:
                print(f"  • Device ID:  {color_text(self.active_device, CYAN)}")
            if self.active_test:
                print(f"  • Test Case:  {color_text(self.active_test, CYAN)}")
            print()

        while True:
            try:
                prefix = ""
                if self.active_device or self.active_test:
                    prefix = f"[{self.active_device or '*'}:{self.active_test or '*'}] "
                user_input = input(color_text(f"\n{prefix}you > ", GREEN, bold=True)).strip()

                if not user_input:
                    continue

                if user_input.lower() in ("/exit", "/quit", "exit", "quit"):
                    print(color_text("\nExiting Mantis. Goodbye!", YELLOW))
                    break

                if user_input.startswith("/"):
                    if user_input.lower().startswith(("/fact-check", "/factcheck", "/critique", "/review")):
                        arg = user_input.split(maxsplit=1)[1].strip() if " " in user_input else ""
                        print(color_text("\n🦗 Mantis is performing an Adversarial Fact-Check & Verification...", MAGENTA, bold=True))
                        response = await self.run_critique(arg)
                        print(f"\n{color_text('🦗 Mantis Fact-Check & Verification Report:', GREEN, bold=True)}\n{response}\n")
                        continue


                    is_cmd, output = self.handle_slash_command(user_input)
                    if is_cmd:
                        if output == "__EXIT__":
                            print(color_text("\nExiting Mantis. Goodbye!", YELLOW))
                            break
                        print(color_text(output, CYAN))
                        continue


                print(color_text("\n🦗 Mantis is analyzing...", MAGENTA))
                response = await self.send_message(user_input)
                print(f"\n{color_text('🦗 Mantis:', GREEN, bold=True)}\n{response}\n")

            except (KeyboardInterrupt, EOFError):
                print(color_text("\nSession interrupted. Exiting Mantis.", YELLOW))
                break
            except Exception as e:
                print(color_text(f"\nError: {e}", RED))
