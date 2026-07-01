import asyncio
import os
import re
import sys
from pathlib import Path
from typing import Callable, Dict, List, Optional, Any, Type

from agentskills_core import SkillRegistry
from agentskills_fs import LocalFileSystemSkillProvider
from google import genai
from google.genai import types

from .engine import AsyncTriageEngine, _get_response_text
from .config.playbook import Playbook
from .config.cache import SemanticCache
from .ui import print_green, print_magenta, color_text, GREEN, YELLOW, RED, print_mantis_banner


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
        skills_dirs: Optional[List[Path]] = None,
        custom_skills: Optional[List[Dict[str, str]]] = None,
        default_model: str = "gemini-3.1-pro-preview",
        concurrency_semaphore: Optional[asyncio.Semaphore] = None,
        rate_limiter: Optional[Any] = None,
        playbook: Optional[Playbook] = None,
        reference_run_marker: str = "## Reference Successful Run Details"
    ):
        self.client = client
        self.default_model = default_model

        if not playbook:
            from .config.playbook import Playbook
            playbook = Playbook.load_default()
        self.playbook = playbook

        # Extract from playbook if provided
        max_loops = 15
        enable_condensation = True
        enable_history_compaction = True
        if playbook:
            self.default_model = playbook.pipeline_config.get("default_model", default_model)
            max_loops = playbook.pipeline_config.get("max_loops", 15)
            enable_condensation = playbook.pipeline_config.get("enable_condensation", True)
            enable_history_compaction = playbook.pipeline_config.get("enable_history_compaction", True)

        self.engine = engine or AsyncTriageEngine(
            client=client,
            model_name=self.default_model,
            concurrency_semaphore=concurrency_semaphore,
            rate_limiter=rate_limiter,
            max_loops=max_loops,
            enable_condensation=enable_condensation,
            enable_history_compaction=enable_history_compaction
        )
        if skills_dir:
            self.skills_dirs = [skills_dir]
        else:
            self.skills_dirs = skills_dirs or []
        self.custom_skills = custom_skills or []
        self.skills_catalog = ""

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

    def register_custom_skill(self, name: str, content: str):
        """Allows programmatic injection of a custom prompt guideline skill at runtime."""
        if not self.custom_skills:
            self.custom_skills = []
        self.custom_skills.append({"name": name, "content": content})

    async def initialize_skills(self) -> str:
        """Registers and compiles skills from configured directories and programmatic injections."""
        registry = SkillRegistry()
        skills_batch = []

        # Resolve skills_dirs from constructor and Playbook configuration
        all_skills_dirs = list(self.skills_dirs or [])
        if self.playbook:
            playbook_dirs = self.playbook.pipeline_config.get("skills", [])
            playbook_parent = self.playbook.filepath.parent if (self.playbook and self.playbook.filepath) else None
            for pdir in playbook_dirs:
                p = Path(pdir)
                if not p.is_absolute() and playbook_parent:
                    p = (playbook_parent / p).resolve()
                all_skills_dirs.append(p)

        for sdir in all_skills_dirs:
            if not sdir.exists():
                continue
            try:
                provider = LocalFileSystemSkillProvider(sdir)
                for skill_folder in sorted(os.listdir(sdir)):
                    skill_path = sdir / skill_folder / "SKILL.md"
                    if skill_path.is_file():
                        skills_batch.append((skill_folder, provider))
            except Exception as e:
                print(f"Warning: Failed to scan skills directory {sdir}: {e}", file=sys.stderr)

        self.skills_catalog = ""
        if skills_batch:
            try:
                await registry.register(skills_batch)
                self.skills_catalog = await registry.get_skills_catalog()
            except Exception as e:
                print(f"Warning: Failed to compile skills folders: {e}", file=sys.stderr)

        # Append programmatically registered custom skills
        if self.custom_skills:
            catalog_parts = [self.skills_catalog] if self.skills_catalog else []
            for skill in self.custom_skills:
                name = skill.get("name")
                content = skill.get("content")
                if name and content:
                    catalog_parts.append(f"\n### Skill: {name}\n{content.strip()}")
            self.skills_catalog = "\n".join(catalog_parts).strip()

        return self.skills_catalog

    def get_skills_context_string(self) -> str:
        """Loads raw text of SKILL.md files for context matching."""
        all_skills_dirs = list(self.skills_dirs or [])
        if self.playbook:
            playbook_dirs = self.playbook.pipeline_config.get("skills", [])
            playbook_parent = self.playbook.filepath.parent if (self.playbook and self.playbook.filepath) else None
            for pdir in playbook_dirs:
                p = Path(pdir)
                if not p.is_absolute() and playbook_parent:
                    p = (playbook_parent / p).resolve()
                all_skills_dirs.append(p)

        skills_content = []
        if all_skills_dirs or self.custom_skills:
            skills_content.append(
                "\n## Skill Library Context (Reference Guidelines)")
            skills_content.append(
                "Use the following guidelines and procedural instructions to shape your analysis strategy. You must follow them strictly:")

        for sdir in all_skills_dirs:
            if not sdir.exists():
                continue
            for skill_folder in sorted(os.listdir(sdir)):
                skill_path = sdir / skill_folder / "SKILL.md"
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

        for skill in self.custom_skills:
            name = skill.get("name")
            content = skill.get("content")
            if name and content:
                content_clean = re.sub(r'^---.*?---', '', content, flags=re.DOTALL).strip()
                skills_content.append(f"\n### Skill: {name}\n{content_clean}")

        return "\n".join(skills_content)

    async def run_generic_stage_async(
        self,
        stage_name: str,
        prompt_payload: str,
        available_tools: Dict[str, Callable],
        out_dir: Optional[str] = None,
        force_cache_bypass: bool = False
    ) -> str:
        """Executes a single generic, dynamic stage configured from the Playbook."""
        stage_cfg = self.playbook.get_stage_config(stage_name) if self.playbook else None
        if not stage_cfg or not stage_cfg.enabled:
            print(f"Stage '{stage_name}' is disabled or not configured in the playbook. Skipping.")
            return ""

        # 1. Check for cached stage results in out_dir
        if out_dir and not force_cache_bypass:
            stage_filepath = Path(out_dir) / f"stage_{stage_name}.md"
            legacy_map = {
                "timeline": "raw_timeline.md",
                "intent": "raw_test_intent.md"
            }
            target_file = stage_filepath
            if stage_name in legacy_map:
                legacy_file = Path(out_dir) / legacy_map[stage_name]
                if legacy_file.exists():
                    target_file = legacy_file

            if target_file.exists():
                print(color_text(f"[Cache Hit] Loaded cached {stage_name} from: {target_file}", GREEN, bold=True))
                with open(target_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    self.context[stage_name] = content
                    return content

        # 2. Check for deterministic implementation hook
        det_helper = getattr(self, f"run_{stage_name}_deterministically", None)
        if det_helper:
            try:
                import inspect
                sig = inspect.signature(det_helper)
                kwargs = {}
                if "prompt_payload" in sig.parameters:
                    kwargs["prompt_payload"] = prompt_payload
                if "target_id" in sig.parameters:
                    kwargs["target_id"] = self.context.get("target_id")

                det_result = det_helper(**kwargs)
                if det_result:
                    print(color_text(f"[Deterministic Hybrid] Built {stage_name} using deterministic helper.", GREEN, bold=True))
                    self.context[stage_name] = det_result
                    return det_result
            except Exception as e:
                print(f"Warning: Deterministic helper for '{stage_name}' failed: {e}", file=sys.stderr)

        # 3. Fallback to GenAI stage execution
        print()
        print_green(f"▶▶ STAGE EXECUTION: {stage_name.upper()} (Type: {stage_cfg.type})", bold=True)
        sys.stdout.flush()

        # Resolve stage-specific tools
        stage_tools = self.playbook.resolve_tools(stage_name, available_tools) if self.playbook else {}
        
        # Standard accumulation of previous stage results in prompt
        payload_parts = [prompt_payload]
        for prev_stage, res_content in self.context.items():
            if res_content and prev_stage not in ("target_id", "payload", "starting_hypothesis"):
                if prev_stage == "critique_feedback":
                    payload_parts.append(f"\n## CRITIQUE FEEDBACK FROM INTERNAL PEER REVIEW\n{res_content}\n\nYou MUST address these flaws in your revised investigation and final report.")
                else:
                    payload_parts.append(f"\n## {prev_stage.capitalize()} Result\n{res_content}")
        
        if "starting_hypothesis" in self.context:
            h0 = self.context["starting_hypothesis"]
            payload_parts.append(
                f"\n> [!IMPORTANT]\n"
                f"> **Historical Triage Hypothesis (Starting Guide)**:\n"
                f"> A similar failure log trace was triaged in the past with the following diagnosis:\n"
                f"> ```\n"
                f"> {h0}\n"
                f"> ```\n"
                f"> Do NOT assume this root cause is identical. You MUST treat this as a candidate hypothesis. "
                f"Use your codebase exploration tools to audit the active repository state and confirm if this specific "
                f"code defect, configuration, or environment mismatch is currently active."
            )

        combined_payload = "\n".join(payload_parts)
        
        # Support system instruction dynamic template placeholder resolution from context
        sys_inst = stage_cfg.system_instruction or ""
        for key, val in self.context.items():
            placeholder = f"{{{key}}}"
            if placeholder in sys_inst:
                sys_inst = sys_inst.replace(placeholder, str(val))

        full_text = combined_payload + "\n\n" + self.skills_catalog
        if len(full_text) > 2500000:
            print(color_text(f"⚠️ Warning: Stage '{stage_name}' prompt payload is large ({len(full_text)} chars). Truncating to protect Gemini context limit...", YELLOW, bold=True))
            sys.stdout.flush()
            full_text = full_text[:500000] + "\n\n[... Intermediate prompt text truncated to stay within model token limit ...]\n\n" + full_text[-1800000:]

        print_green(f"🔍 [STAGE PROMPT SIZE] Stage '{stage_name}': {len(full_text)} chars (~{len(full_text)//4} estimated tokens)", bold=True)
        sys.stdout.flush()

        history = [types.Content(role="user", parts=[types.Part.from_text(
            text=full_text
        )])]

        active_model = self._resolve_model_for_stage(stage_name)
        try:
            result = await self.engine.execute_loop(
                system_instruction=sys_inst,
                history=history,
                tools_map=stage_tools,
                required_headers=stage_cfg.headers,
                model_name=active_model,
                executed_tool_signatures=self.executed_tool_signatures
            )
        except Exception as e:
            from engine.harness.rate_limiter import RateLimitTimeoutError
            if isinstance(e, RateLimitTimeoutError):
                raise
            import traceback
            print(color_text(f"❌ Exception caught in Stage '{stage_name}' (Model: {active_model}): {type(e).__name__}: {e}", RED, bold=True), file=sys.stderr)
            traceback.print_exc()
            sys.stderr.flush()
            raise RuntimeError(f"[Stage: '{stage_name}'] {type(e).__name__}: {e}") from e
        
        self.context[stage_name] = result
        return result

    def _compile_partial_report(self) -> str:
        """Compiles a fallback triage report using whatever intermediate stage context was collected."""
        md = []
        md.append("# Mantis Diagnostics: Partial Triage Report (Fail-Open)")
        md.append("\n> [!WARNING]")
        md.append("> This triage run was terminated early due to Gemini API rate-limiting timeout budget depletion.")
        md.append("> Below is the partial diagnostic data collected prior to termination.\n")
        
        target_id = self.context.get("target_id", "unknown")
        md.append(f"**Triage Target**: `{target_id}`\n")

        if "timeline" in self.context:
            md.append("## Harvested Timeline Context")
            md.append(self.context["timeline"])
            md.append("\n")

        if "intent" in self.context:
            md.append("## Identified Intent & Code Search Context")
            md.append(self.context["intent"])
            md.append("\n")

        if "analysis" in self.context:
            md.append("## Partial Analysis Findings")
            md.append(self.context["analysis"])
            md.append("\n")

        md.append("## Conclusion")
        md.append("**Triage incomplete due to rate limiting.**")
        
        return "\n".join(md)

    async def run_dynamic_pipeline_async(
        self,
        target_id: str,
        prompt_payload: str,
        available_tools: Dict[str, Callable],
        out_dir: Optional[str] = None,
        cache_query: Optional[str] = None,
        force_cache_bypass: bool = False,
        metadata: Optional[Dict[str, Any]] = None,
        namespace: Optional[str] = None,
        fallback_namespaces: Optional[List[str]] = None
    ) -> str:
        """
        Dynamically executes all enabled stages defined in the Playbook in sequential order.
        Passes accumulated context between stages automatically.
        """
        import time
        start_time = time.time()
        
        # Check semantic cache first if query is provided
        cache = None
        cache_filepath = None
        if self.playbook and cache_query:
            cpath = self.playbook.pipeline_config.get("cache_path")
            if cpath:
                cache_filepath = Path(cpath)
                if not cache_filepath.is_absolute() and self.playbook.filepath:
                    cache_filepath = (self.playbook.filepath.parent / cache_filepath).resolve()

        starting_hypothesis = None
        if cache_filepath and cache_query:
            try:
                use_vertex = os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes")
                embed_model = "text-embedding-004" if use_vertex else "models/gemini-embedding-2"
                cache = SemanticCache(
                    self.client,
                    cache_filepath,
                    embedding_model=embed_model,
                    rate_limiter=self.engine.rate_limiter
                )
                await cache.load_async()

                if not force_cache_bypass:
                    print(f"[{target_id}] Querying semantic cache...")
                    cached_entry, score = await cache.lookup(
                        cache_query,
                        namespace=namespace,
                        fallback_namespaces=fallback_namespaces
                    )
                    if cached_entry:
                        print(color_text(f"[Cache Hit] Found historical triage hypothesis! (similarity: {score:.3f}). Seeding into pipeline...", GREEN, bold=True))
                        starting_hypothesis = cached_entry["triage_report"]
                    else:
                        print(f"[{target_id}] Cache miss (best similarity score: {score:.3f}). Running GenAI pipeline...")
                else:
                    print(f"[{target_id}] Cache bypass requested via --force flag. Skipping cache lookup.")
            except Exception as e:
                print(f"Warning: Semantic cache lookup failed: {e}", file=sys.stderr)

        await self.initialize_skills()
        skills_context = self.get_skills_context_string()
        full_payload = prompt_payload + "\n" + skills_context

        self.context = {}
        self.context["target_id"] = target_id
        self.context["payload"] = prompt_payload
        if starting_hypothesis:
            self.context["starting_hypothesis"] = starting_hypothesis

        last_result = ""

        # Non-blocking lint check for critique stage presence
        has_critique_stage = False
        for name, stage_cfg in self.playbook.stages.items():
            if stage_cfg.enabled and (stage_cfg.type == "critique" or name == "critique"):
                has_critique_stage = True
                break
        if not has_critique_stage:
            print("\n" + color_text("[Mantis Harness Warning]: Playbook is configured without a 'critique' stage. Skipping peer-review passes may lead to increased diagnostic hallucinations.", YELLOW, bold=True) + "\n")

        max_revisions = self.playbook.pipeline_config.get("max_revisions", 1) if self.playbook else 1
        from engine.harness.rate_limiter import RateLimitTimeoutError

        try:
            for stage_name, stage_cfg in self.playbook.stages.items():
                if not stage_cfg.enabled:
                    continue

                is_critique = stage_cfg.type == "critique" or stage_name == "critique"

                if is_critique:
                    target_stage = stage_cfg.target_stage or "analysis"
                    for rev_pass in range(1, max_revisions + 1):
                        critique_result = await self.run_generic_stage_async(
                            stage_name=stage_name,
                            prompt_payload=full_payload,
                            available_tools=available_tools,
                            out_dir=out_dir,
                            force_cache_bypass=force_cache_bypass or (rev_pass > 1)
                        )
                        
                        if out_dir and critique_result:
                            os.makedirs(out_dir, exist_ok=True)
                            stage_filepath = os.path.join(out_dir, f"stage_{stage_name}_pass_{rev_pass}.md")
                            try:
                                with open(stage_filepath, "w", encoding="utf-8") as sf:
                                    sf.write(critique_result)
                                with open(os.path.join(out_dir, f"stage_{stage_name}.md"), "w", encoding="utf-8") as sf:
                                    sf.write(critique_result)
                            except Exception as e:
                                print(f"Warning: Failed to save critique result: {e}")

                        if "APPROVED" in critique_result:
                            last_result = critique_result
                            break

                        if rev_pass == max_revisions:
                            print(f"Critique Rejected on final revision pass {rev_pass}/{max_revisions}. Concluding triage.")
                            last_result = critique_result
                            break

                        print(f"Critique Rejected. Initiating dynamic revision pass {rev_pass}/{max_revisions}...")
                        self.context["critique_feedback"] = critique_result

                        revised_analysis = await self.run_generic_stage_async(
                            stage_name=target_stage,
                            prompt_payload=full_payload,
                            available_tools=available_tools,
                            out_dir=out_dir,
                            force_cache_bypass=True
                        )
                        
                        if revised_analysis:
                            self.context[target_stage] = revised_analysis
                            # Overwrite temporary analysis files with revised analysis
                            if out_dir:
                                try:
                                    with open(os.path.join(out_dir, f"stage_{target_stage}.md"), "w", encoding="utf-8") as sf:
                                        sf.write(revised_analysis)
                                except Exception as e:
                                    print(f"Warning: Failed to save revised stage result: {e}")
                else:
                    last_result = await self.run_generic_stage_async(
                        stage_name=stage_name,
                        prompt_payload=full_payload,
                        available_tools=available_tools,
                        out_dir=out_dir,
                        force_cache_bypass=force_cache_bypass
                    )
                    
                    if out_dir and last_result:
                        os.makedirs(out_dir, exist_ok=True)
                        stage_filepath = os.path.join(out_dir, f"stage_{stage_name}.md")
                        try:
                            with open(stage_filepath, "w", encoding="utf-8") as sf:
                                sf.write(last_result)
                        except Exception as e:
                            print(f"Warning: Failed to save stage result to {stage_filepath}: {e}")

            # Returns analysis context if present (fallback), else last result
            final_report = last_result
            for name, stage_cfg in self.playbook.stages.items():
                if stage_cfg.enabled and name == "analysis":
                    if "analysis" in self.context:
                        final_report = self.context["analysis"]
                        break
                elif stage_cfg.enabled and stage_cfg.target_stage:
                    target = stage_cfg.target_stage
                    if target in self.context:
                        final_report = self.context[target]
                        break

            if not final_report and "analysis" in self.context:
                final_report = self.context["analysis"]

        except RateLimitTimeoutError as e:
            print(f"\nFail-Open Warning: {e}", file=sys.stderr)
            print("Rate Limit wait timeout budget exhausted. Concluding diagnostic pipeline early with partial results.", file=sys.stderr)
            final_report = self._compile_partial_report()
            if out_dir:
                os.makedirs(out_dir, exist_ok=True)
                report_filepath = os.path.join(out_dir, "triage_analysis.md")
                with open(report_filepath, 'w', encoding='utf-8') as fr:
                    fr.write(final_report)

        # Extract and save structured JSON triage report alongside Markdown
        if out_dir:
            try:
                # Deduce status
                status = "SUCCESS"
                if "Partial Triage Report (Fail-Open)" in final_report:
                    status = "PARTIAL_FAIL_OPEN"
                elif "INSUFFICIENT DATA TO TRACE ROOT CAUSE" in final_report:
                    status = "FAILED"
                
                from engine.models import extract_structured_report
                print(f"[{target_id}] Extracting structured JSON triage report...")
                structured_report = await extract_structured_report(
                    client=self.client,
                    target_id=target_id,
                    markdown_report=final_report,
                    status=status
                )
                
                os.makedirs(out_dir, exist_ok=True)
                json_filepath = os.path.join(out_dir, "triage_analysis.json")
                with open(json_filepath, "w", encoding="utf-8") as jf:
                    jf.write(structured_report.model_dump_json(indent=2))
                print(f"[{target_id}] Saved structured report to {json_filepath}")
            except Exception as e:
                print(f"Warning: Failed to save structured JSON report: {e}", file=sys.stderr)

        # Cache successful diagnostic
        if cache and cache_query and "INSUFFICIENT DATA TO TRACE ROOT CAUSE" not in final_report:
            try:
                print(f"[{target_id}] Caching successful triage analysis...")
                await cache.add(
                    failure_text=cache_query,
                    triage_report=final_report,
                    metadata=metadata or {},
                    namespace=namespace
                )
            except Exception as e:
                print(f"Warning: Failed to save triage analysis to cache: {e}", file=sys.stderr)

        elapsed_time = time.time() - start_time
        print(f"[{target_id}] Total time taken for analysis: {elapsed_time:.2f} seconds")

        return final_report


async def run_triage_session_async(
    prompt_payload: str,
    target_id: str,
    workspace_root: str,
    playbook_path: Optional[Path] = None,
    out_dir: Optional[str] = None,
    cache_query: Optional[str] = None,
    metadata: Optional[Dict[str, Any]] = None,
    force: bool = False,
    concurrency_semaphore: Optional[asyncio.Semaphore] = None,
    rate_limiter: Optional[Any] = None,
    credentials_provider: Optional[Any] = None,
    pipeline_class: Type[TriagePipeline] = TriagePipeline,
    
    # ToolBelt config overrides
    exclude_dirs: Optional[List[str]] = None,
    include_files: Optional[List[str]] = None,
    namespace: Optional[str] = None,
    fallback_namespaces: Optional[List[str]] = None
) -> str:
    """Generic orchestrator to execute a dynamic diagnostic session with a playbook, tools, and GenAI client."""
    print_mantis_banner()

    # Resolve credentials via credentials provider
    if not credentials_provider:
        from .harness.credentials import EnvCredentialsProvider
        credentials_provider = EnvCredentialsProvider()
    
    client = credentials_provider.get_client()

    # Load playbook
    playbook = None
    if playbook_path and Path(playbook_path).exists():
        try:
            playbook = Playbook(Path(playbook_path)).load()
        except Exception as e:
            print(f"Warning: Failed to load playbook from {playbook_path}: {e}", file=sys.stderr)

    # Instantiate rate limiter
    if not rate_limiter:
        max_qpm = 15
        if playbook and playbook.pipeline_config:
            max_qpm = playbook.pipeline_config.get("max_queries_per_minute", 15)
        
        from .harness.rate_limiter import AsyncRateLimiter
        # Initialize semantic cache rate_limiter as well
        rate_limiter = AsyncRateLimiter(max_requests=max_qpm, time_period_seconds=60.0)

    # Initialize the pipeline
    pipeline = pipeline_class(
        client=client,
        concurrency_semaphore=concurrency_semaphore,
        rate_limiter=rate_limiter,
        playbook=playbook
    )

    # Load ToolBelt to resolve tools
    from .tools import ToolBelt
    tool_belt = ToolBelt(
        workspace_root=workspace_root,
        exclude_dirs=exclude_dirs,
        include_files=include_files
    )
    available_tools = tool_belt.get_tools_map()
    
    # Load custom tools from playbook
    if playbook and playbook.pipeline_config:
        custom_tools = playbook.pipeline_config.get("tools", [])
        if custom_tools and playbook_path:
            import importlib.util
            import inspect
            for tool_path_str in custom_tools:
                tp_path = (Path(playbook_path).parent / tool_path_str).resolve()
                if tp_path.exists():
                    try:
                        spec = importlib.util.spec_from_file_location(f"custom_tools_{tp_path.stem}", tp_path)
                        custom_module = importlib.util.module_from_spec(spec)
                        spec.loader.exec_module(custom_module)
                        
                        for name, func in inspect.getmembers(custom_module, inspect.isfunction):
                            if not name.startswith("_"):
                                available_tools[name] = func
                        print(color_text(f"🔧 Loaded custom tools from {tp_path.name}", GREEN))
                    except Exception as e:
                        print(f"Warning: Failed to load custom tools from {tp_path}: {e}", file=sys.stderr)
                else:
                    print(f"Warning: Custom tool file not found: {tp_path}", file=sys.stderr)

    # Run the dynamic pipeline
    return await pipeline.run_dynamic_pipeline_async(
        target_id=target_id,
        prompt_payload=prompt_payload,
        available_tools=available_tools,
        out_dir=out_dir,
        cache_query=cache_query,
        force_cache_bypass=force,
        metadata=metadata,
        namespace=namespace,
        fallback_namespaces=fallback_namespaces
    )
