import asyncio
import os
import re
import sys
from pathlib import Path
from typing import Callable
from typing import Dict
from typing import List
from typing import Optional

from agentskills_core import SkillRegistry
from agentskills_fs import LocalFileSystemSkillProvider
from google import genai
from google.genai import types

from .engine import AsyncTriageEngine, _get_response_text
from .config.playbook import Playbook
from .ui import print_green, print_magenta, color_text, GREEN


class TriagePipeline:
    """
    A domain-agnostic, highly configurable, multi-phase async AI log triage pipeline.
    Operates as a generic harness that can be used for any log triage scenario (e.g. UDMI,
    Linux Endpoints fleet, Kubernetes deployments, microservices logs) by supplying custom
    system instructions, prompts, and tools.
    """

    def __init__(
        self,
        client: genai.Client,
        engine: AsyncTriageEngine = None,
        skills_dir: Optional[Path] = None,
        default_model: str = "gemini-3.1-pro-preview",
        concurrency_semaphore: Optional[asyncio.Semaphore] = None,
        playbook: Optional[Playbook] = None,

        # Optional prompt & system instruction overrides
        timeline_sys_inst: Optional[str] = None,
        intent_sys_inst: Optional[str] = None,
        defect_sys_inst: Optional[str] = None,
        critic_sys_inst: Optional[str] = None,

        timeline_headers: Optional[List[str]] = None,
        intent_headers: Optional[List[str]] = None,
        defect_headers: Optional[List[str]] = None,
        reference_run_marker: str = "## Reference Successful Run Details"
    ):
        self.client = client
        self.playbook = playbook
        self.default_model = default_model

        # Extract from playbook if provided
        max_loops = 15
        if playbook:
            self.default_model = playbook.pipeline_config.get("default_model", default_model)
            max_loops = playbook.pipeline_config.get("max_loops", 15)

            # Load stage configs from playbook if present
            t_cfg = playbook.get_stage_config("timeline")
            if t_cfg and t_cfg.enabled:
                timeline_sys_inst = t_cfg.system_instruction or timeline_sys_inst
                timeline_headers = t_cfg.headers or timeline_headers

            i_cfg = playbook.get_stage_config("intent")
            if i_cfg and i_cfg.enabled:
                intent_sys_inst = i_cfg.system_instruction or intent_sys_inst
                intent_headers = i_cfg.headers or intent_headers

            a_cfg = playbook.get_stage_config("analysis")
            if a_cfg and a_cfg.enabled:
                defect_sys_inst = a_cfg.system_instruction or defect_sys_inst
                defect_headers = a_cfg.headers or defect_headers

            c_cfg = playbook.get_stage_config("critique")
            if c_cfg and c_cfg.enabled:
                critic_sys_inst = c_cfg.system_instruction or critic_sys_inst

        self.engine = engine or AsyncTriageEngine(
            client=client,
            model_name=self.default_model,
            concurrency_semaphore=concurrency_semaphore,
            max_loops=max_loops
        )
        self.skills_dir = skills_dir
        self.skills_catalog = ""

        # Setup standard generic defaults for the instructions and headers
        self.timeline_sys_inst = timeline_sys_inst or (
            "You are a Systems Log Timeline Harvester. Construct a complete, exhaustive chronological timeline of events "
            "from the provided logs. Output ONLY the markdown header '## 1. Detailed Timeline of Events' followed by a chronological table."
        )
        self.timeline_headers = timeline_headers or [
            "## 1. Detailed Timeline of Events"]

        self.intent_sys_inst = intent_sys_inst or (
            "You are a System Design Intent Harvester. Your goal is to locate and summarize the expected behavior, static specifications, "
            "or intent of the operation being triaged. Output using the structured format headers provided."
        )
        self.intent_headers = intent_headers or [
            "## 1. Reference Specifications",
            "## 2. Expected Flow / Baseline Behavior",
            "## 3. Assertions & Timeouts"
        ]

        self.defect_sys_inst = defect_sys_inst or (
            "You are a Senior Systems Triage Analyst and expert debugger.\n\n"
            "Guidelines:\n"
            "1. MERGE CONTEXT: Map system component behavior in your head based on the timeline and your codebase/configurations research.\n"
            "2. DEEP CODEBASE TRACING: Use your codebase tools to search and read relevant source files or configurations to identify the root cause. Surface-level guesses are unacceptable.\n"
            "3. ZOOM IN: Use log window tools to inspect raw log streams during critical transition states.\n"
            "4. PROPOSE FIXES: Propose a concrete solution, system correction, or code modification as a unified diff block.\n\n"
            "REQUIRED OUTPUT FORMAT:\n"
            "# Systems Triage Analysis Report: <Analysis Target>\n\n"
            "## 1. Executive Defect Summary\n> [Single line summary]\n[Detailed summary]\n\n"
            "## 2. Component Assessment\n- [Briefly list each component and its operational status (Pass/Fail)]\n\n"
            "## 3. Detailed Timeline of Events\n[Insert timeline of relevant events]\n\n"
            "## 4. Root Cause Analysis\n[Insert root cause analysis]\n\n"
            "## 5. Proposed Code Fix\n[Optional - Insert this section only if applicable]"
        )
        self.defect_headers = defect_headers or [
            "## 1. Executive Defect Summary",
            "## 2. Component Assessment",
            "## 3. Detailed Timeline of Events",
            "## 4. Root Cause Analysis"
        ]

        self.critic_sys_inst = critic_sys_inst or (
            "You are a Peer Critique Reviewer. Your job is to peer-review the proposed Root Cause Analysis (RCA) drafted by the Analyst.\n"
            "Compare the 'Draft Analysis' against the 'Timeline' and 'Design Intent'.\n"
            "Look for logical fallacies, premature conclusions, ignoring timestamp anomalies, or incorrect assertions.\n\n"
            "If the draft is logically sound and supported by evidence, output exactly: 'STATUS: APPROVED'\n"
            "If the draft has flaws, output 'STATUS: REJECTED' followed by a detailed peer critique explaining what was missed."
        )
        self.reference_run_marker = reference_run_marker
        self.executed_tool_signatures = set()
        self.context = {}

    @classmethod
    def from_playbook(
        cls,
        client: genai.Client,
        playbook_path: Path,
        engine: AsyncTriageEngine = None,
        skills_dir: Optional[Path] = None,
        concurrency_semaphore: Optional[asyncio.Semaphore] = None
    ) -> "TriagePipeline":
        """Factory method to instantiate the pipeline directly from a declarative YAML playbook."""
        playbook = Playbook(playbook_path).load()
        return cls(
            client=client,
            engine=engine,
            skills_dir=skills_dir,
            concurrency_semaphore=concurrency_semaphore,
            playbook=playbook
        )

    def _resolve_model_for_stage(self, stage_name: str) -> str:
        if self.playbook:
            stage_cfg = self.playbook.get_stage_config(stage_name)
            if stage_cfg and stage_cfg.model:
                return stage_cfg.model
        # Default to self.default_model fallback
        return self.default_model

    async def initialize_skills(self) -> str:
        """Registers and compiles skills from the skills directory if present."""
        if not self.skills_dir or not self.skills_dir.exists():
            self.skills_catalog = ""
            return ""

        try:
            provider = LocalFileSystemSkillProvider(self.skills_dir)
            registry = SkillRegistry()

            skills_batch = []
            for skill_folder in sorted(os.listdir(self.skills_dir)):
                skill_path = self.skills_dir / skill_folder / "SKILL.md"
                if skill_path.is_file():
                    skills_batch.append((skill_folder, provider))

            if skills_batch:
                await registry.register(skills_batch)
                self.skills_catalog = await registry.get_skills_catalog()
            else:
                self.skills_catalog = ""
        except Exception as e:
            print(f"Warning: Failed to initialize skills registry: {e}",
                  file=sys.stderr)
            self.skills_catalog = ""

        return self.skills_catalog

    def get_skills_context_string(self) -> str:
        """Loads raw text of SKILL.md files for context matching."""
        if not self.skills_dir or not self.skills_dir.exists():
            return ""

        skills_content = []
        skills_content.append(
            "\n## Skill Library Context (Reference Guidelines)")
        skills_content.append(
            "Use the following guidelines and procedural instructions to shape your analysis strategy. You must follow them strictly:")

        for skill_folder in sorted(os.listdir(self.skills_dir)):
            skill_path = self.skills_dir / skill_folder / "SKILL.md"
            if skill_path.is_file():
                try:
                    with open(skill_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                        content_clean = re.sub(r'^---.*?---', '', content,
                                               flags=re.DOTALL).strip()
                        skills_content.append(
                            f"\n### Skill: {skill_folder}\n{content_clean}")
                except Exception as e:
                    print(
                        f"Warning: Failed to read skill file at {skill_path}: {e}",
                        file=sys.stderr)

        return "\n".join(skills_content)

    def build_timeline_deterministically(self, prompt_payload: str) -> Optional[str]:
        """Subclasses can override this to build the timeline deterministically from logs without calling GenAI."""
        return None

    def harvest_intent_deterministically(self, target_id: str, prompt_payload: str) -> Optional[str]:
        """Subclasses can override this to harvest static context/specifications deterministically without calling GenAI."""
        return None

    async def run_timeline_generation(self, prompt_payload: str,
        tools_map: Dict[str, Callable]) -> str:
        """PHASE 1: Constructs the objective chronological event timeline."""
        print()
        print_green("--- Generating Chronological Event Timeline ---", bold=True)

        # Strip successful details to avoid bias in phase 1
        clean_payload = prompt_payload
        if self.reference_run_marker and self.reference_run_marker in prompt_payload:
            clean_payload = prompt_payload.split(self.reference_run_marker)[0]
        history = [types.Content(role="user", parts=[types.Part.from_text(
            text=clean_payload + "\n\n" + self.skills_catalog
        )])]

        active_model = self._resolve_model_for_stage("timeline")
        return await self.engine.execute_loop(
            self.timeline_sys_inst, history, tools_map, self.timeline_headers, model_name=active_model, executed_tool_signatures=self.executed_tool_signatures)

    async def run_intent_harvesting(self, target_id: str, prompt_payload: str,
        tools_map: Dict[str, Callable]) -> str:
        """PHASE 1.5: Harvesting Static Test/Task Design Intent and Specifications."""
        print()
        print_green("--- Harvesting Static Intent & Specifications ---", bold=True)
        history = [types.Content(role="user", parts=[types.Part.from_text(
            text=f"## Context\n{prompt_payload}\n\nTarget: {target_id}"
        )])]

        active_model = self._resolve_model_for_stage("intent")
        return await self.engine.execute_loop(
            self.intent_sys_inst, history, tools_map, self.intent_headers, model_name=active_model, executed_tool_signatures=self.executed_tool_signatures)

    def _strip_raw_logs_from_payload(self, payload: str) -> str:
        """Strips massive raw log contents from the payload to conserve tokens for the defect analyst."""
        clean_payload = payload
        header = "## Chronologically Merged Global Logs (Test Execution Context)"
        if header in clean_payload:
            parts = clean_payload.split(header, 1)
            before = parts[0]
            after = parts[1]
            if "## " in after:
                after = "\n## " + after.split("## ", 1)[1]
            else:
                after = ""
            clean_payload = before + after
        return clean_payload.strip()

    async def run_defect_analysis(
        self,
        prompt_payload: str,
        timeline: str,
        intent: str,
        tools_map: Dict[str, Callable],
        critique_feedback: str = None
    ) -> str:
        """PHASE 2: Performs root-cause differential analysis and codebase/config research."""
        iteration = " (REVISION PASS)" if critique_feedback else ""
        print()
        print_green(f"--- Running System Root Cause Analysis{iteration} ---", bold=True)

        clean_payload = self._strip_raw_logs_from_payload(prompt_payload)
        payload = f"{clean_payload}\n\n## Intent\n{intent}\n\n## Timeline\n{timeline}\n\n## Skills\n{self.skills_catalog}"
        if critique_feedback:
            payload += f"\n\n## CRITIQUE FEEDBACK FROM INTERNAL PEER REVIEW\n{critique_feedback}\n\nYou MUST address these flaws in your revised investigation and final report."

        history = [types.Content(role="user",
                                 parts=[types.Part.from_text(text=payload)])]

        active_model = self._resolve_model_for_stage("analysis")
        return await self.engine.execute_loop(
            self.defect_sys_inst, history, tools_map, self.defect_headers, model_name=active_model, executed_tool_signatures=self.executed_tool_signatures)

    async def run_hypothesis_critique(self, timeline: str, intent: str,
        draft_analysis: str) -> str:
        """PHASE 3: A separate critic agent peer-reviews the proposed analysis for logical flaws."""
        print()
        print_magenta("--- Peer-Reviewing Hypothesis ---", bold=True)

        payload = f"## Test Intent\n{intent}\n\n## Known Timeline\n{timeline}\n\n## Draft Analysis to Review\n{draft_analysis}"
        history = [types.Content(role="user",
                                 parts=[types.Part.from_text(text=payload)])]

        config = types.GenerateContentConfig(temperature=0.2,
                                             system_instruction=self.critic_sys_inst)

        response = await self.client.aio.models.generate_content(
            model=self.engine.model_name,
            contents=history,
            config=config
        )

        verdict = _get_response_text(response) or "STATUS: APPROVED"
        if "APPROVED" in verdict:
            print_magenta("Critique Passed: The analysis is mathematically sound.")
        else:
            print_magenta("Critique Failed: Flaws detected. Returning to Analyst.")
            print(verdict)

        return verdict

    async def run_pipeline_async(
        self,
        target_id: str,
        prompt_payload: str,
        phase1_tools: Optional[Dict[str, Callable]] = None,
        phase1_5_tools: Optional[Dict[str, Callable]] = None,
        phase2_tools: Optional[Dict[str, Callable]] = None,
        out_dir: Optional[str] = None,
        available_tools: Optional[Dict[str, Callable]] = None
    ) -> str:
        """
        Orchestrates the complete multi-phase async triage flow, returning the final report text.
        Supports sequential and dynamic playbook execution.
        """
        await self.initialize_skills()
        skills_context = self.get_skills_context_string()
        full_prompt_payload = prompt_payload + "\n" + skills_context

        # Dynamic tool resolution from playbook
        if self.playbook and available_tools:
            phase1_tools = self.playbook.resolve_tools("timeline", available_tools)
            phase1_5_tools = self.playbook.resolve_tools("intent", available_tools)
            phase2_tools = self.playbook.resolve_tools("analysis", available_tools)
        else:
            phase1_tools = phase1_tools or {}
            phase1_5_tools = phase1_5_tools or {}
            phase2_tools = phase2_tools or {}

        # Dynamic stage switches
        timeline_enabled = self.playbook.is_stage_enabled("timeline") if self.playbook else True
        intent_enabled = self.playbook.is_stage_enabled("intent") if self.playbook else True
        critique_enabled = self.playbook.is_stage_enabled("critique") if self.playbook else True

        # Step 1 & 2: Timeline and Intent Harvesting concurrently (Parallelized)
        timeline_content = ""
        intent_content = ""
        timeline_task = None
        intent_task = None

        if timeline_enabled:
            if out_dir and (Path(out_dir) / "raw_timeline.md").exists():
                timeline_path = Path(out_dir) / "raw_timeline.md"
                print("⚡ " + color_text(f"[Cache Hit] Loaded cached timeline from: {timeline_path}", GREEN, bold=True))
                with open(timeline_path, 'r', encoding='utf-8') as f:
                    timeline_content = f.read()

            if not timeline_content:
                async def get_timeline():
                    content = self.build_timeline_deterministically(full_prompt_payload)
                    if content:
                        print("⚡ [Deterministic Hybrid] Built timeline from logs.")
                        return content
                    return await self.run_timeline_generation(full_prompt_payload, phase1_tools)
                timeline_task = get_timeline()
        else:
            print("Stage 'timeline' is disabled in the playbook. Skipping timeline generation.")

        if intent_enabled and target_id:
            if out_dir and (Path(out_dir) / "test_intent.md").exists():
                intent_path = Path(out_dir) / "test_intent.md"
                print("⚡ " + color_text(f"[Cache Hit] Loaded cached test intent from: {intent_path}", GREEN, bold=True))
                with open(intent_path, 'r', encoding='utf-8') as f:
                    intent_content = f.read()

            if not intent_content:
                async def get_intent():
                    content = self.harvest_intent_deterministically(target_id, full_prompt_payload)
                    if content:
                        print("⚡ [Deterministic Hybrid] Harvested test context and golden baselines.")
                        return content
                    return await self.run_intent_harvesting(target_id, full_prompt_payload, phase1_5_tools)
                intent_task = get_intent()
        else:
            if not intent_enabled:
                print("Stage 'intent' is disabled in the playbook. Skipping intent harvesting.")

        # Execute tasks concurrently if they are defined
        tasks = []
        task_indices = {}
        if timeline_task is not None:
            task_indices["timeline"] = len(tasks)
            tasks.append(timeline_task)
        if intent_task is not None:
            task_indices["intent"] = len(tasks)
            tasks.append(intent_task)

        if tasks:
            print(f"⚡ [Parallel Triage] Spawning {len(tasks)} independent diagnostic tracks concurrently...")
            results = await asyncio.gather(*tasks)
            if "timeline" in task_indices:
                timeline_content = results[task_indices["timeline"]]
                if out_dir and timeline_content:
                    os.makedirs(out_dir, exist_ok=True)
                    with open(Path(out_dir) / "raw_timeline.md", 'w', encoding='utf-8') as f:
                        f.write(timeline_content)
            if "intent" in task_indices:
                intent_content = results[task_indices["intent"]]
                if out_dir and intent_content:
                    os.makedirs(out_dir, exist_ok=True)
                    with open(Path(out_dir) / "test_intent.md", 'w', encoding='utf-8') as f:
                        f.write(intent_content)

        # Step 3 & 4: Analysis with Critique Loop
        draft_analysis = await self.run_defect_analysis(full_prompt_payload,
                                                        timeline_content,
                                                        intent_content,
                                                        phase2_tools)
        
        if critique_enabled:
            max_revisions = self.playbook.pipeline_config.get("max_revisions", 1) if self.playbook else 1
            for rev_pass in range(1, max_revisions + 1):
                critique = await self.run_hypothesis_critique(timeline_content,
                                                              intent_content,
                                                              draft_analysis)
                if "APPROVED" in critique:
                    break
                
                print(f"🔄 Critique Rejected. Initiating revision pass {rev_pass}/{max_revisions}...")
                draft_analysis = await self.run_defect_analysis(
                    full_prompt_payload,
                    timeline_content,
                    intent_content,
                    phase2_tools,
                    critique_feedback=critique
                )
        else:
            print("Stage 'critique' is disabled in the playbook. Skipping critique loop.")

        return draft_analysis

    async def run_generic_stage_async(
        self,
        stage_name: str,
        prompt_payload: str,
        available_tools: Dict[str, Callable]
    ) -> str:
        """Executes a single generic, dynamic stage configured from the Playbook."""
        stage_cfg = self.playbook.get_stage_config(stage_name) if self.playbook else None
        if not stage_cfg or not stage_cfg.enabled:
            print(f"Stage '{stage_name}' is disabled or not configured in the playbook. Skipping.")
            return ""

        print()
        print_green(f"--- Running Playbook Stage: {stage_name.capitalize()} ---", bold=True)

        # Resolve stage-specific tools
        stage_tools = self.playbook.resolve_tools(stage_name, available_tools) if self.playbook else {}
        
        # Standard accumulation of previous stage results in prompt
        payload_parts = [prompt_payload]
        for prev_stage, res_content in self.context.items():
            if res_content and prev_stage != "target_id" and prev_stage != "payload":
                if prev_stage == "critique_feedback":
                    payload_parts.append(f"\n## CRITIQUE FEEDBACK FROM INTERNAL PEER REVIEW\n{res_content}\n\nYou MUST address these flaws in your revised investigation and final report.")
                else:
                    payload_parts.append(f"\n## {prev_stage.capitalize()} Result\n{res_content}")
        
        combined_payload = "\n".join(payload_parts)
        
        # Support system instruction dynamic template placeholder resolution from context
        sys_inst = stage_cfg.system_instruction or ""
        for key, val in self.context.items():
            placeholder = f"{{{key}}}"
            if placeholder in sys_inst:
                sys_inst = sys_inst.replace(placeholder, str(val))

        history = [types.Content(role="user", parts=[types.Part.from_text(
            text=combined_payload + "\n\n" + self.skills_catalog
        )])]

        active_model = self._resolve_model_for_stage(stage_name)
        result = await self.engine.execute_loop(
            system_instruction=sys_inst,
            history=history,
            tools_map=stage_tools,
            required_headers=stage_cfg.headers,
            model_name=active_model,
            executed_tool_signatures=self.executed_tool_signatures
        )
        
        self.context[stage_name] = result
        return result

    async def run_dynamic_pipeline_async(
        self,
        target_id: str,
        prompt_payload: str,
        available_tools: Dict[str, Callable],
        out_dir: Optional[str] = None
    ) -> str:
        """
        Dynamically executes all enabled stages defined in the Playbook in sequential order.
        Passes accumulated context between stages automatically.
        """
        await self.initialize_skills()
        skills_context = self.get_skills_context_string()
        full_payload = prompt_payload + "\n" + skills_context

        self.context = {}
        self.context["target_id"] = target_id
        self.context["payload"] = prompt_payload

        last_result = ""
        
        if not self.playbook or not self.playbook.stages:
            # Fallback to standard pipeline if no playbook or stages configured
            return await self.run_pipeline_async(
                target_id=target_id,
                prompt_payload=prompt_payload,
                available_tools=available_tools,
                out_dir=out_dir
            )

        max_revisions = self.playbook.pipeline_config.get("max_revisions", 1) if self.playbook else 1

        for stage_name, stage_cfg in self.playbook.stages.items():
            if not stage_cfg.enabled:
                continue

            if stage_name == "critique":
                for rev_pass in range(1, max_revisions + 1):
                    critique_result = await self.run_generic_stage_async(
                        stage_name="critique",
                        prompt_payload=full_payload,
                        available_tools=available_tools
                    )
                    
                    if out_dir and critique_result:
                        os.makedirs(out_dir, exist_ok=True)
                        stage_filepath = os.path.join(out_dir, f"stage_critique_pass_{rev_pass}.md")
                        try:
                            with open(stage_filepath, "w", encoding="utf-8") as sf:
                                sf.write(critique_result)
                            with open(os.path.join(out_dir, "stage_critique.md"), "w", encoding="utf-8") as sf:
                                sf.write(critique_result)
                        except Exception as e:
                            print(f"Warning: Failed to save critique result: {e}")

                    if "APPROVED" in critique_result:
                        last_result = critique_result
                        break

                    if rev_pass == max_revisions:
                        print(f"🔄 Critique Rejected on final revision pass {rev_pass}/{max_revisions}. Concluding triage.")
                        last_result = critique_result
                        break

                    print(f"🔄 Critique Rejected. Initiating dynamic revision pass {rev_pass}/{max_revisions}...")
                    self.context["critique_feedback"] = critique_result

                    revised_analysis = await self.run_generic_stage_async(
                        stage_name="analysis",
                        prompt_payload=full_payload,
                        available_tools=available_tools
                    )
                    
                    if out_dir and revised_analysis:
                        stage_filepath = os.path.join(out_dir, f"stage_analysis_revised_{rev_pass}.md")
                        try:
                            with open(stage_filepath, "w", encoding="utf-8") as sf:
                                sf.write(revised_analysis)
                            with open(os.path.join(out_dir, "stage_analysis.md"), "w", encoding="utf-8") as sf:
                                sf.write(revised_analysis)
                        except Exception as e:
                            print(f"Warning: Failed to save revised analysis: {e}")

                    self.context.pop("critique_feedback", None)
            else:
                last_result = await self.run_generic_stage_async(
                    stage_name=stage_name,
                    prompt_payload=full_payload,
                    available_tools=available_tools
                )
                
                if out_dir and last_result:
                    os.makedirs(out_dir, exist_ok=True)
                    stage_filepath = os.path.join(out_dir, f"stage_{stage_name}.md")
                    try:
                        with open(stage_filepath, "w", encoding="utf-8") as sf:
                            sf.write(last_result)
                    except Exception as e:
                        print(f"Warning: Failed to save stage result to {stage_filepath}: {e}")

        if "analysis" in self.context:
            return self.context["analysis"]
        return last_result
