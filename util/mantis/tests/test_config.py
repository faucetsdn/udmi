import asyncio
import unittest
from unittest.mock import AsyncMock, MagicMock
from pathlib import Path
import tempfile
import json
import shutil
import os
import sys
from datetime import datetime, timedelta



from engine.config.playbook import Playbook
from engine.config.cache import SemanticCache



class TestPlaybook(unittest.TestCase):

    def setUp(self):
        self.playbook_dir = Path(tempfile.mkdtemp())
        self.playbook_path = self.playbook_dir / "playbook.yaml"

        # Mock yaml playbook data
        self.playbook_data = """
metadata:
  name: "Test Playbook"
  description: "Playbook description"
  version: "1.0.0"

pipeline:
  default_model: "gemini-test-model"
  max_loops: 10

stages:
  timeline:
    enabled: true
    system_instruction: "Timeline Inst"
    headers:
      - "## Timeline Header"
    tools:
      - read_file_lines
  intent:
    enabled: false
    tools:
      - grep_codebase
  analysis:
    enabled: true
    tools:
      - list_directory
      - invalid_tool_name
  critique:
    enabled: true
    type: critique
    target_stage: analysis
"""
        with open(self.playbook_path, "w", encoding="utf-8") as f:
            f.write(self.playbook_data)

    def tearDown(self):
        shutil.rmtree(self.playbook_dir)

    def test_parsing(self):
        playbook = Playbook(self.playbook_path).load()
        self.assertEqual(playbook.metadata.get("name"), "Test Playbook")
        self.assertEqual(playbook.pipeline_config.get("max_loops"), 10)

        timeline_cfg = playbook.get_stage_config("timeline")
        self.assertTrue(timeline_cfg.enabled)
        self.assertEqual(timeline_cfg.system_instruction, "Timeline Inst")
        self.assertEqual(timeline_cfg.headers, ["## Timeline Header"])
        self.assertEqual(timeline_cfg.tools, ["read_file_lines"])

        intent_cfg = playbook.get_stage_config("intent")
        self.assertFalse(intent_cfg.enabled)

        critique_cfg = playbook.get_stage_config("critique")
        self.assertTrue(critique_cfg.enabled)
        self.assertEqual(critique_cfg.type, "critique")
        self.assertEqual(critique_cfg.target_stage, "analysis")

    def test_resolve_tools(self):
        playbook = Playbook(self.playbook_path).load()

        # Mock ToolBelt map
        available_tools = {
            "read_file_lines": lambda: "read",
            "grep_codebase": lambda: "grep",
            "list_directory": lambda: "list",
        }

        # Resolve tools for timeline stage
        resolved_timeline = playbook.resolve_tools("timeline", available_tools)
        self.assertIn("read_file_lines", resolved_timeline)
        self.assertEqual(len(resolved_timeline), 1)

        # Resolve tools for analysis (one tool is valid, one is invalid)
        resolved_analysis = playbook.resolve_tools("analysis", available_tools)
        self.assertIn("list_directory", resolved_analysis)
        self.assertNotIn("invalid_tool_name", resolved_analysis)
        self.assertEqual(len(resolved_analysis), 1)

    def test_extension_binding_and_crash_guard(self):
        # Create a playbook with an extension tool
        ext_playbook_data = """
metadata:
  name: "Extension Playbook"
pipeline:
  default_model: "gemini-3.1-pro-preview"
extensions:
  crashing_extension:
    command: ["/bin/false"]  # executes binary returning non-zero exit code
    description: "Always fails"
stages:
  analysis:
    enabled: true
    tools:
      - crashing_extension
"""
        playbook_path = self.playbook_dir / "ext_playbook.yaml"
        with open(playbook_path, "w") as f:
            f.write(ext_playbook_data)

        playbook = Playbook(playbook_path).load()
        resolved = playbook.resolve_tools("analysis", available_tools={})
        self.assertIn("crashing_extension", resolved)

        # Trigger execution and verify crash-guard returns UNVERIFIED_PLUGIN_FAILURE
        wrapped_tool = resolved["crashing_extension"]
        res = wrapped_tool({"input": "dummy"})
        self.assertEqual(res.get("status"), "UNVERIFIED_PLUGIN_FAILURE")
        self.assertIn("failed with exit code 1", res.get("error", ""))



class TestSemanticCache(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp())
        self.cache_path = self.temp_dir / "semantic_cache.json"

        # Mock client
        self.mock_client = MagicMock()
        self.mock_client.aio = MagicMock()
        self.mock_client.aio.models = MagicMock()

        # Configure fake embed response
        # embed_content is an async method
        self.mock_client.aio.models.embed_content = AsyncMock()

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_cosine_similarity(self):
        cache = SemanticCache(self.mock_client, self.cache_path)

        v1 = [1.0, 0.0, 0.0]
        v2 = [1.0, 0.0, 0.0]
        self.assertAlmostEqual(cache._cosine_similarity(v1, v2), 1.0)

        v3 = [0.0, 1.0, 0.0]
        self.assertAlmostEqual(cache._cosine_similarity(v1, v3), 0.0)

        v4 = [1.0, 1.0, 0.0]  # magnitude = sqrt(2)
        # similarity = 1.0 / (1 * sqrt(2)) = 0.707
        self.assertAlmostEqual(
            cache._cosine_similarity(v1, v4), 0.7071067811865475
        )

    async def test_persistence(self):
        cache = SemanticCache(self.mock_client, self.cache_path)
        self.assertEqual(len(cache.entries), 0)

        # Manual insertion
        cache.entries.append(
            {
                "failure_text": "test failure",
                "embedding": [0.1, 0.2, 0.3],
                "triage_report": "test triage",
                "metadata": {"test": "val"},
                "timestamp": "now",
            }
        )

        await cache.save_async()
        self.assertTrue(self.cache_path.exists())

        # Load in a new cache instance
        new_cache = SemanticCache(self.mock_client, self.cache_path)
        await new_cache.load_async()

        self.assertEqual(len(new_cache.entries), 1)
        self.assertEqual(new_cache.entries[0]["failure_text"], "test failure")
        self.assertEqual(new_cache.entries[0]["embedding"], [0.1, 0.2, 0.3])

    async def test_lookup_mechanism(self):
        # Mock embedding values
        mock_query_embedding = MagicMock()
        mock_query_embedding.values = [1.0, 0.0, 0.0]

        mock_cached_embedding = MagicMock()
        mock_cached_embedding.values = [0.95, 0.1, 0.0]

        # Set up embedding generator mock sequences
        response_1 = MagicMock()
        response_1.embeddings = [mock_query_embedding]
        self.mock_client.aio.models.embed_content.return_value = response_1

        cache = SemanticCache(
            self.mock_client, self.cache_path, similarity_threshold=0.90
        )

        # Initialize cache with a close-matching entry
        cache.entries.append(
            {
                "failure_text": "almost identical failure log",
                "embedding": [
                    0.95,
                    0.1,
                    0.0,
                ],  # high similarity with query [1.0, 0.0, 0.0]
                "triage_report": "Triage Report A",
                "metadata": {},
                "timestamp": "now",
            }
        )

        # Lookup should succeed because cosine similarity is high
        entry, score = await cache.lookup("new incoming failure log")
        self.assertIsNotNone(entry)
        self.assertEqual(entry["triage_report"], "Triage Report A")
        self.assertTrue(score >= 0.90)

        # Now set threshold to something extremely high (0.999)
        cache.similarity_threshold = 0.999
        entry, score = await cache.lookup("new incoming failure log")
        # Should result in a cache miss now
        self.assertIsNone(entry)
        self.assertTrue(score < 0.999)

    async def test_namespaced_cascading_lookup(self):
        mock_query_embedding = MagicMock()
        mock_query_embedding.values = [1.0, 0.0, 0.0]

        response = MagicMock()
        response.embeddings = [mock_query_embedding]
        self.mock_client.aio.models.embed_content.return_value = response

        cache = SemanticCache(
            self.mock_client, self.cache_path, similarity_threshold=0.90
        )

        # 1. Store global entry
        cache.entries.append({
            "failure_text": "global failure",
            "embedding": [0.99, 0.0, 0.0],
            "triage_report": "Global Report",
            "namespace": "global",
            "timestamp": "now",
        })

        # 2. Store specific namespace entry
        cache.entries.append({
            "failure_text": "specific namespace failure",
            "embedding": [0.98, 0.0, 0.0],
            "triage_report": "Ads Report",
            "namespace": "projects/myproject/src",
            "timestamp": "now",
        })

        # Test lookup inside namespace "projects/myproject/src"
        entry, score = await cache.lookup("query", namespace="projects/myproject/src")
        self.assertIsNotNone(entry)
        self.assertEqual(entry["triage_report"], "Ads Report")

        # Test lookup inside namespace "projects/myproject/lib" without fallback
        entry, score = await cache.lookup("query", namespace="projects/myproject/lib")
        self.assertIsNone(entry)  # Misses because projects/myproject/lib namespace has no entries

        # Test lookup inside namespace "projects/myproject/lib" WITH global fallback
        entry, score = await cache.lookup(
            "query",
            namespace="projects/myproject/lib",
            fallback_namespaces=["global"]
        )
        self.assertIsNotNone(entry)
        self.assertEqual(entry["triage_report"], "Global Report")  # Cascades to global!

    async def test_normalization_matching(self):
        # We want to verify that different query strings containing different timestamps/variables
        # are normalized identically and therefore generate identical embeddings and hit the cache.
        mock_embedding = MagicMock()
        mock_embedding.values = [0.8, 0.5, 0.1]
        
        response = MagicMock()
        response.embeddings = [mock_embedding]
        self.mock_client.aio.models.embed_content.return_value = response

        cache = SemanticCache(
            self.mock_client, self.cache_path, similarity_threshold=0.95
        )

        # 1. Add entry with timestamp A and hex address A
        await cache.add(
            failure_text="[2026-06-12 10:00:00.000] [ERROR] pubber: Failed to connect (0x7ffd)",
            triage_report="Triage Report Target",
            namespace="global"
        )

        # Reset mock call counters
        self.mock_client.aio.models.embed_content.reset_mock()
        self.mock_client.aio.models.embed_content.return_value = response

        # 2. Lookup entry with timestamp B and hex address B
        entry, score = await cache.lookup(
            "[2026-06-12 11:30:15.500] [ERROR] pubber: Failed to connect (0x1e2f)",
            namespace="global"
        )

        # Assert cache hits because embeddings are calculated on the normalized template string
        self.assertIsNotNone(entry)
        self.assertEqual(entry["triage_report"], "Triage Report Target")
        self.assertAlmostEqual(score, 1.0)
        
        # Verify that get_embedding was actually called with the normalized string
        embed_call_args = self.mock_client.aio.models.embed_content.call_args[1]
        self.assertEqual(
            embed_call_args["contents"],
            "[<TIMESTAMP>] [ERROR] pubber: Failed to connect (<HEX>)"
        )



class TestPipelineSkills(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.skills_base_dir = Path(tempfile.mkdtemp())
        self.skills_dir_1 = self.skills_base_dir / "dir1"
        self.skills_dir_2 = self.skills_base_dir / "dir2"
        self.skills_dir_1.mkdir()
        self.skills_dir_2.mkdir()

        # Create skill 1 in dir 1
        skill1_folder = self.skills_dir_1 / "skill-one"
        skill1_folder.mkdir()
        with open(skill1_folder / "SKILL.md", "w") as f:
            f.write("---\nname: skill-one\ndescription: test skill\n---\nGuideline for Skill One.")

        # Create skill 2 in dir 2
        skill2_folder = self.skills_dir_2 / "skill-two"
        skill2_folder.mkdir()
        with open(skill2_folder / "SKILL.md", "w") as f:
            f.write("---\nname: skill-two\ndescription: test skill\n---\nGuideline for Skill Two.")

        self.mock_client = MagicMock()

    def tearDown(self):
        shutil.rmtree(self.skills_base_dir)

    async def test_multi_skills_loading_and_injection(self):
        from engine.pipeline import TriagePipeline


        pipeline = TriagePipeline(
            client=self.mock_client,
            skills_dirs=[self.skills_dir_1, self.skills_dir_2],
            custom_skills=[{"name": "Skill Three", "content": "Guideline for Skill Three."}]
        )

        catalog = await pipeline.initialize_skills()
        self.assertIn("skill-one", catalog)
        self.assertIn("skill-two", catalog)
        self.assertIn("Skill Three", catalog)

        pipeline.register_custom_skill("Skill Four", "Guideline for Skill Four.")
        catalog_with_four = await pipeline.initialize_skills()
        self.assertIn("Skill Four", catalog_with_four)

        context_str = pipeline.get_skills_context_string()
        self.assertIn("skill-one", context_str)
        self.assertIn("skill-two", context_str)
        self.assertIn("Skill Three", context_str)
        self.assertIn("Skill Four", context_str)


class TestDynamicStagePipeline(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_client = MagicMock()

    async def test_fallback_playbook_initialization(self):
        from engine.pipeline import TriagePipeline
        
        pipeline = TriagePipeline(client=self.mock_client, playbook=None)
        self.assertIsNotNone(pipeline.playbook)
        self.assertEqual(pipeline.playbook.metadata.get("name"), "Default Mantis Triage Playbook")
        self.assertIn("timeline", pipeline.playbook.stages)
        self.assertIn("intent", pipeline.playbook.stages)
        self.assertIn("analysis", pipeline.playbook.stages)
        self.assertIn("critique", pipeline.playbook.stages)

    async def test_dynamic_deterministic_bypass(self):
        from engine.pipeline import TriagePipeline
        from typing import Optional

        class CustomTriagePipeline(TriagePipeline):
            def run_timeline_deterministically(self, prompt_payload: str) -> Optional[str]:
                return "DETERMINISTIC_TIMELINE_OUTPUT"

        pipeline = CustomTriagePipeline(client=self.mock_client, playbook=None)
        
        res = await pipeline.run_generic_stage_async(
            stage_name="timeline",
            prompt_payload="test payload",
            available_tools={}
        )
        self.assertEqual(res, "DETERMINISTIC_TIMELINE_OUTPUT")
        self.assertEqual(pipeline.context["timeline"], "DETERMINISTIC_TIMELINE_OUTPUT")
    async def test_run_triage_session_async(self):
        from engine.pipeline import run_triage_session_async, TriagePipeline
        from unittest.mock import patch

        mock_pipeline_instance = MagicMock()
        mock_pipeline_instance.run_dynamic_pipeline_async = AsyncMock(return_value="FINAL_REPORT_OUTPUT")
        
        mock_pipeline_class = MagicMock(return_value=mock_pipeline_instance)

        with patch("os.getenv") as mock_getenv, patch("google.genai.Client") as mock_client_cls:
            mock_getenv.side_effect = lambda k, default=None: "true" if k == "MANTIS_USE_VERTEXAI" else default
            
            res = await run_triage_session_async(
                prompt_payload="test payload",
                target_id="test_id",
                workspace_root="/tmp",
                pipeline_class=mock_pipeline_class
            )
            
            self.assertEqual(res, "FINAL_REPORT_OUTPUT")
            mock_pipeline_class.assert_called_once()
            mock_pipeline_instance.run_dynamic_pipeline_async.assert_called_once()

    async def test_fail_open_rate_limit_timeout(self):
        from engine.pipeline import TriagePipeline
        from engine.harness.rate_limiter import AsyncRateLimiter

        # Initialize rate limiter with 1 capacity and exhaust it
        limiter = AsyncRateLimiter(max_requests=1, time_period_seconds=10.0)
        self.assertTrue(await limiter.acquire())

        pipeline = TriagePipeline(
            client=self.mock_client,
            rate_limiter=limiter,
            playbook=None
        )
        
        from engine.harness.rate_limiter import RateLimitTimeoutError
        pipeline.engine.execute_loop = AsyncMock(side_effect=[
            "Timeline harvested successfully.",  # first call (timeline stage)
            RateLimitTimeoutError("Simulated Timeout")  # second call (intent/analysis stage)
        ])

        # Run pipeline
        report = await pipeline.run_dynamic_pipeline_async(
            target_id="test_target",
            prompt_payload="Initial prompt",
            available_tools={}
        )

        # Verify fail-open behavior
        self.assertIn("Partial Triage Report (Fail-Open)", report)
        self.assertIn("Timeline harvested successfully.", report)
        self.assertIn("Triage incomplete due to rate limiting", report)

    async def test_structured_json_report_generation(self):
        from engine.pipeline import TriagePipeline
        from engine.models import TriageReportModel
        from unittest.mock import patch

        # Mock out_dir
        temp_out = tempfile.mkdtemp()
        try:
            pipeline = TriagePipeline(
                client=self.mock_client,
                playbook=None
            )
            pipeline.engine.execute_loop = AsyncMock(return_value="Successful diagnostics report text.")

            from engine.models import HypothesisEvaluation, RootCauseAnalysis

            mock_model_output = TriageReportModel(
                target_id="test_target_pydantic",
                status="SUCCESS",
                verdict="VERIFIED_DEFECT",
                summary="Simulated Root Cause Summary",
                hypotheses_evaluated=[
                    HypothesisEvaluation(title="hypothesis1", status="DISPROVED", evidence="no logs"),
                    HypothesisEvaluation(title="hypothesis2", status="VERIFIED", evidence="defect found")
                ],
                root_cause_analysis=RootCauseAnalysis(
                    culprit_file="src/app/runner.py",
                    culprit_line_range="L20-L25",
                    explanation="Port binding mismatch"
                )
            )

            # Patch extract_structured_report helper
            with patch("engine.models.extract_structured_report", AsyncMock(return_value=mock_model_output)) as mock_extract:
                report = await pipeline.run_dynamic_pipeline_async(
                    target_id="test_target_pydantic",
                    prompt_payload="Initial prompt",
                    available_tools={},
                    out_dir=temp_out
                )
                
                mock_extract.assert_called_once()
                
                # Check JSON report file contents
                json_path = os.path.join(temp_out, "triage_analysis.json")
                self.assertTrue(os.path.exists(json_path))
                with open(json_path, "r", encoding="utf-8") as jf:
                    data = json.load(jf)
                
                self.assertEqual(data["target_id"], "test_target_pydantic")
                self.assertEqual(data["status"], "SUCCESS")
                self.assertEqual(data["verdict"], "VERIFIED_DEFECT")
                self.assertEqual(data["summary"], "Simulated Root Cause Summary")
                self.assertEqual(len(data["hypotheses_evaluated"]), 2)
                self.assertEqual(data["hypotheses_evaluated"][0]["title"], "hypothesis1")
                self.assertEqual(data["root_cause_analysis"]["culprit_file"], "src/app/runner.py")
        finally:
            shutil.rmtree(temp_out)

    @unittest.mock.patch("engine.pipeline.SemanticCache")
    async def test_hypothesis_seeding_h0_loop(self, mock_cache_cls):
        from engine.pipeline import TriagePipeline

        # Mock cache lookup hit
        mock_cache = MagicMock()
        mock_cache.load_async = AsyncMock()
        mock_cache.lookup = AsyncMock(return_value=({
            "triage_report": "Historical root cause was port collision"
        }, 0.95))
        mock_cache_cls.return_value = mock_cache

        # Set up a playbook with cache enabled
        from engine.config.playbook import Playbook
        playbook = Playbook.load_default()
        playbook.pipeline_config["cache_path"] = "fake_cache.json"

        pipeline = TriagePipeline(
            client=self.mock_client,
            playbook=playbook
        )
        
        # We want to capture the history payload passed to execute_loop
        execute_loop_mock = AsyncMock(return_value="Current active triage report.")
        pipeline.engine.execute_loop = execute_loop_mock

        # Run pipeline
        report = await pipeline.run_dynamic_pipeline_async(
            target_id="test_target_h0",
            prompt_payload="Active test failure logs.",
            available_tools={},
            cache_query="Active query failure trace"
        )

        # Assert execute_loop was called and did NOT short circuit
        execute_loop_mock.assert_called()
        self.assertEqual(report, "Current active triage report.")

        # Check prompt payload passed to execute_loop contains the starting hypothesis text
        call_args = execute_loop_mock.call_args[1]
        history_contents = call_args["history"][0].parts[0].text
        self.assertIn("Historical Triage Hypothesis (Starting Guide)", history_contents)
        self.assertIn("Historical root cause was port collision", history_contents)

    def test_toolbelt_custom_search_provider(self):
        from engine.tools import ToolBelt
        from engine.harness.search import CodeSearchProvider

        # Mock custom code search provider
        mock_provider = MagicMock(spec=CodeSearchProvider)
        mock_provider.grep_codebase = MagicMock(return_value="Mocked Code Search Result")

        # Instantiate ToolBelt
        tool_belt = ToolBelt(
            workspace_root="/tmp",
            search_dirs=["src"],
            exclude_dirs=["out"],
            exclude_files=["*.log"],
            include_files=["*.py"],
            search_provider=mock_provider
        )

        res = tool_belt.grep_codebase(pattern="my-pattern")
        self.assertEqual(res, "Mocked Code Search Result")
        mock_provider.grep_codebase.assert_called_once_with(
            workspace_root=os.path.abspath("/tmp"),
            pattern="my-pattern",
            search_dirs=["src"],
            exclude_dirs=["out"],
            exclude_files=["*.log"],
            include_files=["*.py"]
        )

    def test_toolbelt_custom_search_provider_lookup_symbol(self):
        from engine.tools import ToolBelt
        from engine.harness.search import CodeSearchProvider

        # Mock custom code search provider
        mock_provider = MagicMock(spec=CodeSearchProvider)
        mock_provider.grep_codebase = MagicMock(return_value="class MyClass declaration match")

        # Instantiate ToolBelt
        tool_belt = ToolBelt(
            workspace_root="/tmp",
            search_dirs=["src"],
            exclude_dirs=["out"],
            exclude_files=["*.log"],
            include_files=["*.py"],
            search_provider=mock_provider
        )

        res = tool_belt.lookup_symbol(symbol_name="MyClass")
        self.assertEqual(res, "class MyClass declaration match")
        
        # Verify that grep_codebase was called with correct patterns for lookup_symbol
        mock_provider.grep_codebase.assert_called_once()
        call_args = mock_provider.grep_codebase.call_args[1]
        self.assertEqual(call_args["workspace_root"], os.path.abspath("/tmp"))
        self.assertIn("MyClass", call_args["pattern"])


class TestSubprocessPlugin(unittest.TestCase):

    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp())
        self.script_path = self.temp_dir / "square_plugin.py"

        # Write dummy python plugin
        plugin_code = """
import sys
import json

def main():
    try:
        input_data = json.load(sys.stdin)
        val = input_data.get("value", 0)
        # Check for error trigger
        if input_data.get("trigger_error"):
            print("Simulated Plugin Error Logs on Stderr", file=sys.stderr)
            sys.exit(2)
        # Standard return
        print(json.dumps({"result": val * val}))
    except Exception as e:
        print(f"Plugin Exception: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()
"""
        with open(self.script_path, "w", encoding="utf-8") as f:
            f.write(plugin_code)

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_subprocess_plugin_success(self):
        from engine.harness.plugin import SubprocessPluginRunner

        runner = SubprocessPluginRunner([sys.executable, str(self.script_path)])
        output = runner.run({"value": 6})
        self.assertEqual(output.get("result"), 36)

    def test_subprocess_plugin_error_stderr(self):
        from engine.harness.plugin import SubprocessPluginRunner, PluginExecutionError

        runner = SubprocessPluginRunner([sys.executable, str(self.script_path)])
        with self.assertRaises(PluginExecutionError) as ctx:
            runner.run({"value": 6, "trigger_error": True})
        self.assertEqual(ctx.exception.exit_code, 2)
        self.assertIn("Simulated Plugin Error Logs on Stderr", ctx.exception.stderr_output)


class TestRegexLogParser(unittest.TestCase):

    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp())
        self.log_path = self.temp_dir / "test_run.log"

        # Log lines with ISO timestamp
        log_data = """2026-06-12T10:00:00 [INFO] main: Starting services
2026-06-12T10:01:00 [WARNING] db: Timeout detected
2026-06-12T10:02:00 [ERROR] net: Lost connection
"""
        with open(self.log_path, "w", encoding="utf-8") as f:
            f.write(log_data)

        self.pattern = r'^(?P<timestamp>\S+) \[(?P<severity>\w+)\] (?P<tag>\w+): (?P<message>.*)$'

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_parse_line_success(self):
        from engine.harness.parser import RegexLogParser

        parser = RegexLogParser(self.pattern, timestamp_format="%Y-%m-%dT%H:%M:%S")
        entry = parser.parse_line("2026-06-12T10:00:00 [ERROR] pubber: MQTT Broker Down")
        self.assertIsNotNone(entry)
        self.assertEqual(entry["severity"], "ERROR")
        self.assertEqual(entry["tag"], "pubber")
        self.assertEqual(entry["message"], "MQTT Broker Down")
        self.assertEqual(entry["timestamp"].year, 2026)
        self.assertEqual(entry["timestamp"].minute, 0)

    def test_parse_file_with_timebounds(self):
        from engine.harness.parser import RegexLogParser

        parser = RegexLogParser(self.pattern, timestamp_format="%Y-%m-%dT%H:%M:%S")
        
        # Test parsing the file within 10:00:30 and 10:01:30 timebounds
        start = datetime(2026, 6, 12, 10, 0, 30)
        end = datetime(2026, 6, 12, 10, 1, 30)
        
        entries = parser.parse_file(str(self.log_path), start_dt=start, end_dt=end)
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["severity"], "WARNING")
        self.assertEqual(entries[0]["tag"], "db")


class TestRateLimiter(unittest.IsolatedAsyncioTestCase):

    async def test_instant_acquisition_under_limit(self):
        from engine.harness.rate_limiter import AsyncRateLimiter

        limiter = AsyncRateLimiter(max_requests=5, time_period_seconds=10)
        # We should be able to acquire 5 times instantly
        for _ in range(5):
            res = await limiter.acquire(timeout_seconds=0.1)
            self.assertTrue(res)

    async def test_blocking_above_limit(self):
        from engine.harness.rate_limiter import AsyncRateLimiter
        import time

        # Refill is 2 requests per 1 second
        limiter = AsyncRateLimiter(max_requests=2, time_period_seconds=1.0)
        
        # Spend capacity
        self.assertTrue(await limiter.acquire())
        self.assertTrue(await limiter.acquire())

        # Third acquisition must wait or hit timeout
        start = time.monotonic()
        res = await limiter.acquire(timeout_seconds=0.1)
        # Should timeout and fail-open/return False
        self.assertFalse(res)
        
        # Now wait for refill to complete (at least 0.5s for 1 token)
        await asyncio.sleep(0.55)
        # Should succeed now
        self.assertTrue(await limiter.acquire())


class TestCredentialsProvider(unittest.TestCase):

    @unittest.mock.patch("os.getenv")
    @unittest.mock.patch("google.genai.Client")
    def test_env_credentials_provider_api_key(self, mock_client_cls, mock_getenv):
        from engine.harness.credentials import EnvCredentialsProvider
        
        # Configure env mocks to mimic standard API Key path
        mock_getenv.side_effect = lambda k, default=None: {
            "GEMINI_API_KEY": "fake_api_key",
            "MANTIS_USE_VERTEXAI": "false"
        }.get(k, default)

        provider = EnvCredentialsProvider()
        client = provider.get_client()

        self.assertIsNotNone(client)
        mock_client_cls.assert_called_once_with()

    @unittest.mock.patch("os.getenv")
    @unittest.mock.patch("google.genai.Client")
    def test_env_credentials_provider_vertex(self, mock_client_cls, mock_getenv):
        from engine.harness.credentials import EnvCredentialsProvider
        
        # Configure env mocks to mimic Vertex AI path
        mock_getenv.side_effect = lambda k, default=None: {
            "MANTIS_USE_VERTEXAI": "true",
            "GCP_PROJECT": "my-project",
            "GCP_LOCATION": "us-central1"
        }.get(k, default)

        provider = EnvCredentialsProvider()
        client = provider.get_client()

        self.assertIsNotNone(client)
        mock_client_cls.assert_called_once_with(vertexai=True, project="my-project", location="us-central1")


class TestStabilityAnalyzer(unittest.TestCase):
    def test_test_execution_key_id_parsing(self):
        from util.eval_sequencer_stability.analyzer import TestExecutionKey

        # Test key with device ID
        key1 = TestExecutionKey(
            suite="sequencer",
            type="RESULT",
            category="system",
            test_name="valid_serial_no",
            occurrence_index=2,
            device_id="AHU-1"
        )
        self.assertEqual(key1.id(), "AHU-1:sequencer:RESULT:system:valid_serial_no:2")
        
        parsed1 = TestExecutionKey.from_id(key1.id())
        self.assertEqual(parsed1, key1)

        # Test key without device ID
        key2 = TestExecutionKey(
            suite="itemized",
            type="CPBLTY",
            category="security",
            test_name="auth_failure",
            occurrence_index=0
        )
        self.assertEqual(key2.id(), "itemized:CPBLTY:security:auth_failure:0")

        parsed2 = TestExecutionKey.from_id(key2.id())
        self.assertEqual(parsed2, key2)

        # Test invalid key parsing
        with self.assertRaises(ValueError):
            TestExecutionKey.from_id("invalid:format")


class TestUDMITriageReporter(unittest.TestCase):
    def test_failure_signature_clustering(self):
        from app.reporter import UDMITriageReporter

        # Setup dummy summaries
        triage_summaries = [
            {
                'test_id': 'test_1',
                'suite': 'sequencer',
                'category': 'system',
                'breakpoint': 'Connection handshake timed out waiting for reflection update',
                'insufficient': False,
                'report_link': './AHU-1/test_1/triage_analysis.md'
            },
            {
                'test_id': 'test_2',
                'suite': 'sequencer',
                'category': 'system',
                'breakpoint': 'Telemetry payload key schema validation failed: missing expected double type value',
                'insufficient': False,
                'report_link': './AHU-1/test_2/triage_analysis.md'
            },
            {
                'test_id': 'test_3',
                'suite': 'sequencer',
                'category': 'security',
                'breakpoint': 'Certificates are not matching for TLS authentication check',
                'insufficient': False,
                'report_link': './AHU-1/test_3/triage_analysis.md'
            }
        ]

        # 1. Test default classifiers
        reporter1 = UDMITriageReporter(target="//mqtt/localhost", site_id="virtual-site", out_dir="/tmp")
        report1 = reporter1.compile_summary_report(triage_summaries)

        self.assertIn("### Sync Wait Timeout (Component latency / missing acknowledgments) (Affecting 1 Tests)", report1)
        self.assertIn("### Telemetry Schema Violation (Malformed JSON payload envelope) (Affecting 1 Tests)", report1)
        self.assertIn("### General Unclassified Regression Signature (Affecting 1 Tests)", report1)

        # 2. Test custom classifiers
        custom_classifiers = {
            "TLS Configuration Error": ".*TLS.*|.*Certificates.*",
            "Timeout Failures": ".*time.*",
            "Schema Discrepancy": ".*schema.*"
        }
        reporter2 = UDMITriageReporter(target="//mqtt/localhost", site_id="virtual-site", out_dir="/tmp", failure_classifiers=custom_classifiers)
        report2 = reporter2.compile_summary_report(triage_summaries)

        self.assertIn("### Timeout Failures (Affecting 1 Tests)", report2)
        self.assertIn("### Schema Discrepancy (Affecting 1 Tests)", report2)
        self.assertIn("### TLS Configuration Error (Affecting 1 Tests)", report2)


if __name__ == "__main__":
    unittest.main()
