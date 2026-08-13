import asyncio
import unittest
from unittest.mock import AsyncMock, MagicMock, patch
from google.genai import types
from engine.engine import AsyncTriageEngine, _clean_error_message


class TestAsyncTriageEngineRetry(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.mock_client = MagicMock()
        self.mock_generate = AsyncMock()
        self.mock_client.aio.models.generate_content = self.mock_generate
        self.engine = AsyncTriageEngine(client=self.mock_client, max_retries=3, base_wait_seconds=0.01)

    async def test_successful_call_on_first_try(self):
        mock_resp = MagicMock()
        self.mock_generate.return_value = mock_resp
        res = await self.engine._call_generate_content_with_retry("gemini-pro", "hello")
        self.assertEqual(res, mock_resp)
        self.mock_generate.assert_called_once()

    @patch("asyncio.sleep", new_callable=AsyncMock)
    async def test_transient_retry_success(self, mock_sleep):
        mock_resp = MagicMock()
        self.mock_generate.side_effect = [Exception("429 RESOURCE_EXHAUSTED"), mock_resp]
        res = await self.engine._call_generate_content_with_retry("gemini-pro", "hello")
        self.assertEqual(res, mock_resp)
        self.assertEqual(self.mock_generate.call_count, 2)
        mock_sleep.assert_called_once()

    async def test_non_transient_error_fails_immediately(self):
        self.mock_generate.side_effect = Exception("400 INVALID_ARGUMENT")
        with self.assertRaises(Exception) as ctx:
            await self.engine._call_generate_content_with_retry("gemini-pro", "hello")
        self.assertIn("400 INVALID_ARGUMENT", str(ctx.exception))
        self.mock_generate.assert_called_once()

    @patch("asyncio.sleep", new_callable=AsyncMock)
    async def test_transient_retry_exhaustion(self, mock_sleep):
        self.mock_generate.side_effect = Exception("503 Service Unavailable")
        with self.assertRaises(Exception) as ctx:
            await self.engine._call_generate_content_with_retry("gemini-pro", "hello")
        self.assertEqual(self.mock_generate.call_count, 3)


class TestAsyncTriageEngineCondensation(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.mock_client = MagicMock()
        self.engine = AsyncTriageEngine(client=self.mock_client, enable_condensation=True)

    async def test_no_condensation_if_disabled_or_short(self):
        self.engine.enable_condensation = False
        res = await self.engine._condense_tool_result("test_tool", "short output")
        self.assertEqual(res, "short output")

        self.engine.enable_condensation = True
        res_short = await self.engine._condense_tool_result("test_tool", "x" * 1000)
        self.assertEqual(res_short, "x" * 1000)

    @patch.object(AsyncTriageEngine, "_call_generate_content_with_retry", new_callable=AsyncMock)
    async def test_condensation_triggered(self, mock_retry):
        mock_resp = MagicMock()
        mock_resp.text = "Condensed summary output"
        mock_retry.return_value = mock_resp
        large_input = "y" * 30000
        res = await self.engine._condense_tool_result("test_tool", large_input)
        self.assertEqual(res, "Condensed summary output")
        mock_retry.assert_called_once()


class TestAsyncTriageEngineHistoryCompaction(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.mock_client = MagicMock()
        self.engine = AsyncTriageEngine(client=self.mock_client, enable_history_compaction=True)

    async def test_no_compaction_if_short_history(self):
        history = ["turn"] * 5
        await self.engine._compact_history_if_bloated(history)
        self.assertEqual(len(history), 5)

    @patch.object(AsyncTriageEngine, "_call_generate_content_with_retry", new_callable=AsyncMock)
    async def test_history_compacted(self, mock_retry):
        mock_resp = MagicMock()
        mock_resp.text = "Checkpoint summary of past events"
        mock_retry.return_value = mock_resp
        history = [types.Content(role="user", parts=[types.Part.from_text(text="Initial prompt")])] + ["turn"] * 15
        await self.engine._compact_history_if_bloated(history)
        self.assertEqual(len(history), 2)
        self.assertIn("Checkpoint summary of past events", history[1].parts[0].text)


class TestAsyncTriageEngineExecuteLoop(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.mock_client = MagicMock()
        self.engine = AsyncTriageEngine(client=self.mock_client)

    @patch.object(AsyncTriageEngine, "_call_generate_content_with_retry", new_callable=AsyncMock)
    async def test_execute_loop_simple_text_response(self, mock_retry):
        mock_part = MagicMock()
        mock_part.text = "Final triage report result"
        mock_part.function_call = None
        mock_cand = MagicMock()
        mock_cand.content.parts = [mock_part]
        mock_resp = MagicMock()
        mock_resp.candidates = [mock_cand]
        mock_retry.return_value = mock_resp

        history = [types.Content(role="user", parts=[types.Part.from_text(text="Initial user prompt")])]
        res = await self.engine.execute_loop(
            system_instruction="sys inst",
            history=history,
            tools_map={}
        )
        self.assertEqual(res, "Final triage report result")


if __name__ == "__main__":
    unittest.main()
