import asyncio
import json
import os
import tempfile
import unittest
from unittest.mock import AsyncMock, MagicMock, patch
from google.genai import types

from mantis.agent.chat import MantisChatSession
from mantis.cli import UDMITriageCLI


class TestMantisChat(unittest.TestCase):
    """Tests for the Mantis interactive chat mode and session orchestrator."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.mock_client = MagicMock()

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_cli_chat_flags(self):
        cli = UDMITriageCLI()
        args = cli.parse(["--chat"])
        self.assertTrue(args.chat)
        self.assertIsNone(args.query)

        args2 = cli.parse(["-c", "-q", "Why did AHU-1 fail?"])
        self.assertTrue(args2.chat)
        self.assertEqual(args2.query, "Why did AHU-1 fail?")

    def test_session_init_and_context(self):
        session = MantisChatSession(
            udmi_root=self.temp_dir.name,
            client=self.mock_client,
            device_id="AHU-1",
            test_id="system_min_loglevel",
            site_model="sites/udmi_site_model"
        )
        self.assertEqual(session.active_device, "AHU-1")
        self.assertEqual(session.active_test, "system_min_loglevel")
        self.assertEqual(session.active_site_model, "sites/udmi_site_model")
        self.assertIn("list_directory", session.tools_map)
        self.assertIn("get_triage_manifest_summary", session.tools_map)
        self.assertIn("get_test_source_code", session.tools_map)

        ctx_json = session.get_active_session_context()
        ctx_data = json.loads(ctx_json)
        self.assertEqual(ctx_data["active_device"], "AHU-1")
        self.assertEqual(ctx_data["active_test"], "system_min_loglevel")

    def test_slash_commands(self):
        session = MantisChatSession(
            udmi_root=self.temp_dir.name,
            client=self.mock_client
        )

        # /help
        handled, out = session.handle_slash_command("/help")
        self.assertTrue(handled)
        self.assertIn("Mantis Quick Commands:", out)
        self.assertIn("/diagnose", out)
        self.assertIn("/diff", out)
        self.assertIn("/fact-check", out)


        # /device
        handled, out = session.handle_slash_command("/device PUMP-2")
        self.assertTrue(handled)
        self.assertEqual(session.active_device, "PUMP-2")

        # /test
        handled, out = session.handle_slash_command("/test pointset_publish")
        self.assertTrue(handled)
        self.assertEqual(session.active_test, "pointset_publish")

        # /tools
        handled, out = session.handle_slash_command("/tools")
        self.assertTrue(handled)
        self.assertIn("Registered Diagnostic Tools", out)

        # /clear
        session.history.append(MagicMock())
        self.assertEqual(len(session.history), 1)
        handled, out = session.handle_slash_command("/clear")
        self.assertTrue(handled)
        self.assertEqual(len(session.history), 0)

        # /exit
        handled, out = session.handle_slash_command("/exit")
        self.assertTrue(handled)
        self.assertEqual(out, "__EXIT__")

    def test_load_manifest(self):
        manifest_path = os.path.join(self.temp_dir.name, "triage_manifest.json")
        sample_manifest = {
            "metadata": {
                "target_project": "//mqtt/localhost",
                "site_id": "test_site"
            },
            "failures": [
                {
                    "failure_id": "test_site:DEV-1:valid_serial_no",
                    "device_id": "DEV-1",
                    "test_name": "valid_serial_no",
                    "flake_rate": "100%",
                    "log_path": "/tmp/logs/log.log"
                }
            ]
        }
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump(sample_manifest, f)

        session = MantisChatSession(
            udmi_root=self.temp_dir.name,
            client=self.mock_client,
            manifest_path=manifest_path
        )
        self.assertEqual(session.active_device, "DEV-1")
        self.assertEqual(session.active_test, "valid_serial_no")
        self.assertEqual(session.active_site_model, "test_site")

        summary = session.get_triage_manifest_summary()
        self.assertIn("DEV-1", summary)
        self.assertIn("valid_serial_no", summary)

    def test_export_transcript(self):
        session = MantisChatSession(
            udmi_root=self.temp_dir.name,
            client=self.mock_client,
            device_id="DEV-1"
        )
        export_file = os.path.join(self.temp_dir.name, "chat_export.md")
        handled, out = session.handle_slash_command(f"/export {export_file}")
        self.assertTrue(handled)
        self.assertTrue(os.path.exists(export_file))
        with open(export_file, "r") as f:
            content = f.read()
        self.assertIn("Mantis Diagnostic Chat Transcript", content)
        self.assertIn("DEV-1", content)

    @patch("mantis.engine.engine.AsyncTriageEngine.execute_loop", new_callable=AsyncMock)
    def test_send_message_execution(self, mock_execute_loop):
        mock_execute_loop.return_value = "Diagnostic findings: The device timed out during TLS handshake."

        session = MantisChatSession(
            udmi_root=self.temp_dir.name,
            client=self.mock_client
        )

        response = asyncio.run(session.send_message("What caused the failure?"))
        self.assertIn("Diagnostic findings", response)
        self.assertEqual(len(session.history), 1)
        self.assertEqual(session.history[0].role, "user")

    @patch("mantis.engine.engine.AsyncTriageEngine.execute_loop", new_callable=AsyncMock)
    def test_run_critique_execution(self, mock_execute_loop):
        mock_execute_loop.return_value = "Critique: The previous report incorrectly attributed the failure to DiscoveryFacetResolver."

        session = MantisChatSession(
            udmi_root=self.temp_dir.name,
            client=self.mock_client
        )

        # 1. When no previous response exists
        empty_critique = asyncio.run(session.run_critique())
        self.assertIn("No previous diagnostic report found", empty_critique)

        # 2. When previous response exists in history
        session.history.append(types.Content(
            role="model",
            parts=[types.Part.from_text(text="Initial Diagnosis: DiscoveryFacetResolver NullPointerException")]
        ))

        critique_response = asyncio.run(session.run_critique(focus_notes="Check pointset_publish logs"))
        self.assertIn("Critique: The previous report incorrectly", critique_response)
        mock_execute_loop.assert_called_once()

    @patch("mantis.tools.differential.get_historical_site_state")
    def test_get_historical_site_state_tool(self, mock_hist_impl):
        mock_hist_impl.return_value = "### Historical State (Commit: 59352bf1d4)"
        session = MantisChatSession(
            udmi_root=self.temp_dir.name,
            client=self.mock_client,
            site_model="sites/udmi_site_model",
            device_id="AHU-1"
        )
        res = session.get_historical_site_state(timestamp="2026-07-24T09:24:02Z")
        self.assertIn("Historical State", res)
        mock_hist_impl.assert_called_once_with(
            udmi_root=self.temp_dir.name,
            site_name="sites/udmi_site_model",
            timestamp="2026-07-24T09:24:02Z",
            device_id="AHU-1"
        )


if __name__ == "__main__":
    unittest.main()

