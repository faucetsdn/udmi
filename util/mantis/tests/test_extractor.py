import os
import unittest
from unittest.mock import AsyncMock, MagicMock

from mantis.agent.extractor import UDMIEntityExtractor
from mantis.agent.chat import MantisChatSession


class TestUDMIEntityExtractor(unittest.IsolatedAsyncioTestCase):
    """Tests for natural language entity extraction and dynamic context updates."""

    def setUp(self):
        self.udmi_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
        self.extractor = UDMIEntityExtractor(self.udmi_root)

    def test_extract_all_entities_from_query(self):
        query = "why did the test pointset_publish fail for AHU-1 in sites/udmi_site_model"
        entities = self.extractor.extract_entities(query)
        self.assertEqual(entities["site_model"], "sites/udmi_site_model")
        self.assertEqual(entities["device_id"], "AHU-1")
        self.assertEqual(entities["test_id"], "pointset_publish")

    def test_extract_device_and_test_without_site(self):
        query = "What caused AHU-1 to fail system_min_loglevel?"
        entities = self.extractor.extract_entities(query)
        self.assertEqual(entities["device_id"], "AHU-1")
        self.assertEqual(entities["test_id"], "system_min_loglevel")

    def test_extract_site_and_device(self):
        query = "Inspect metadata for AHU-1 in udmi_site_model"
        entities = self.extractor.extract_entities(query)
        self.assertEqual(entities["site_model"], "sites/udmi_site_model")
        self.assertEqual(entities["device_id"], "AHU-1")

    def test_extract_parameterized_discovery_test(self):
        query = "Yes, investigate scan_periodic_now_enumerate+bacnet for CGW-501"
        entities = self.extractor.extract_entities(query)
        self.assertEqual(entities["device_id"], "CGW-501")
        self.assertEqual(entities["test_id"], "scan_periodic_now_enumerate+bacnet")

    async def test_chat_session_auto_updates_context_on_message(self):
        mock_client = MagicMock()
        session = MantisChatSession(
            udmi_root=self.udmi_root,
            client=mock_client
        )
        session.engine.execute_loop = AsyncMock(return_value="Diagnostic response")

        self.assertIsNone(session.active_site_model)
        self.assertIsNone(session.active_device)
        self.assertIsNone(session.active_test)

        await session.send_message("why did pointset_publish fail for AHU-1 in sites/udmi_site_model")

        self.assertEqual(session.active_site_model, "sites/udmi_site_model")
        self.assertEqual(session.active_device, "AHU-1")
        self.assertEqual(session.active_test, "pointset_publish")
        self.assertIn("udmi_site_model", session.system_prompt)
        self.assertIn("AHU-1", session.system_prompt)


if __name__ == "__main__":
    unittest.main()


