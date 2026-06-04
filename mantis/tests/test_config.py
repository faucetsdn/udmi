import asyncio
import unittest
from unittest.mock import AsyncMock, MagicMock
from pathlib import Path
import tempfile
import json
import shutil
import os

from mantis.src.triage.harness.config.playbook import Playbook
from mantis.src.triage.harness.config.cache import SemanticCache


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
        from mantis.src.triage.harness.pipeline import TriagePipeline

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
        from mantis.src.triage.harness.pipeline import TriagePipeline
        
        pipeline = TriagePipeline(client=self.mock_client, playbook=None)
        self.assertIsNotNone(pipeline.playbook)
        self.assertEqual(pipeline.playbook.metadata.get("name"), "Default Mantis Triage Playbook")
        self.assertIn("timeline", pipeline.playbook.stages)
        self.assertIn("intent", pipeline.playbook.stages)
        self.assertIn("analysis", pipeline.playbook.stages)
        self.assertIn("critique", pipeline.playbook.stages)

    async def test_dynamic_deterministic_bypass(self):
        from mantis.src.triage.harness.pipeline import TriagePipeline
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
        from mantis.src.triage.harness.pipeline import run_triage_session_async, TriagePipeline
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


if __name__ == "__main__":
    unittest.main()
