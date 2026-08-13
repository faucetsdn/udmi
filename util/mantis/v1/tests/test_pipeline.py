import unittest
from unittest.mock import MagicMock, AsyncMock, patch
import os
import tempfile
import shutil

from engine.pipeline import TriagePipeline, run_triage_session_async
from engine.harness.rate_limiter import RateLimitTimeoutError


class TestTriagePipelineExceptionHandling(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_client = MagicMock()
        self.temp_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    async def test_run_generic_stage_async_rate_limit_re_raised(self):
        pipeline = TriagePipeline(client=self.mock_client, playbook=None)
        pipeline.engine.execute_loop = AsyncMock(side_effect=RateLimitTimeoutError("Rate limit budget exhausted"))

        with self.assertRaises(RateLimitTimeoutError):
            await pipeline.run_generic_stage_async(
                stage_name="timeline",
                prompt_payload="test payload",
                available_tools={}
            )

    async def test_run_generic_stage_async_runtime_error_wrapped(self):
        pipeline = TriagePipeline(client=self.mock_client, playbook=None)
        pipeline.engine.execute_loop = AsyncMock(side_effect=ValueError("Generic LLM failure"))

        with self.assertRaises(RuntimeError) as ctx:
            await pipeline.run_generic_stage_async(
                stage_name="timeline",
                prompt_payload="test payload",
                available_tools={}
            )
        self.assertIn("Generic LLM failure", str(ctx.exception))

    async def test_fail_open_partial_report_compilation(self):
        pipeline = TriagePipeline(client=self.mock_client, playbook=None)
        pipeline.context["timeline"] = "Partial timeline content"
        
        report = pipeline._compile_partial_report()
        self.assertIn("Partial Triage Report (Fail-Open)", report)
        self.assertIn("Partial timeline content", report)

    async def test_run_dynamic_pipeline_fail_open_on_rate_limit(self):
        pipeline = TriagePipeline(client=self.mock_client, playbook=None)
        pipeline.engine.execute_loop = AsyncMock(side_effect=[
            "Stage 1 success",
            RateLimitTimeoutError("Rate limit hit")
        ])

        with patch("engine.models.extract_structured_report", AsyncMock(side_effect=Exception("Structured extraction skipped on rate limit test"))):
            report = await pipeline.run_dynamic_pipeline_async(
                target_id="test_target",
                prompt_payload="Start prompt",
                available_tools={},
                out_dir=self.temp_dir
            )
            self.assertIn("Partial Triage Report (Fail-Open)", report)
            self.assertIn("Stage 1 success", report)


if __name__ == "__main__":
    unittest.main()
