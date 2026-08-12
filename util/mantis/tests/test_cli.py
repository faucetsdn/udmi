import asyncio
import os
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from mantis.cli import UDMITriageCLI, async_main, parse_vertex_spec


class TestMantisUnifiedCLI(unittest.IsolatedAsyncioTestCase):
    """Tests for the Mantis unified CLI router, subcommands, and argument dispatcher."""

    def test_parse_vertex_spec(self):
        # 1. Flag omitted
        use_v, proj, loc = parse_vertex_spec(None)
        self.assertFalse(use_v)
        self.assertIsNone(proj)
        self.assertIsNone(loc)

        # 2. Flag present without argument
        use_v, proj, loc = parse_vertex_spec("AUTO")
        self.assertTrue(use_v)
        self.assertIsNone(proj)
        self.assertEqual(loc, "global")

        # 3. Project only
        use_v, proj, loc = parse_vertex_spec("my-gcp-project")
        self.assertTrue(use_v)
        self.assertEqual(proj, "my-gcp-project")
        self.assertEqual(loc, "global")

        # 4. Project and region
        use_v, proj, loc = parse_vertex_spec("my-gcp-project/us-central1")
        self.assertTrue(use_v)
        self.assertEqual(proj, "my-gcp-project")
        self.assertEqual(loc, "us-central1")

        # 5. Region only
        use_v, proj, loc = parse_vertex_spec("/us-east1")
        self.assertTrue(use_v)
        self.assertIsNone(proj)
        self.assertEqual(loc, "us-east1")

    def test_cli_positional_targets(self):
        cli = UDMITriageCLI()
        args = cli.parse(["sites/udmi_site_model", "AHU-1", "pointset_publish"])
        self.assertEqual(args.targets, ["sites/udmi_site_model", "AHU-1", "pointset_publish"])

    def test_cli_subcommands(self):
        cli = UDMITriageCLI()
        args_chat = cli.parse(["chat", "--site", "sites/udmi_site_model"])
        self.assertEqual(args_chat.targets[0], "chat")
        self.assertEqual(args_chat.site_model, "sites/udmi_site_model")

        args_eval = cli.parse(["eval", "-i", "out/runs/"])
        self.assertEqual(args_eval.targets[0], "eval")
        self.assertEqual(args_eval.test_runs, "out/runs/")

        args_collect = cli.parse(["collect", "--target", "//mqtt/localhost:46432"])
        self.assertEqual(args_collect.targets[0], "collect")

        args_playbook = cli.parse(["create-playbook", "my_playbook.yaml"])
        self.assertEqual(args_playbook.targets[0], "create-playbook")

    def test_cli_flags(self):
        cli = UDMITriageCLI()
        args = cli.parse([
            "-s", "sites/udmi_site_model",
            "-d", "AHU-1",
            "-t", "pointset_publish",
            "-q", "Explain the failure",
            "--vertex", "my-gcp-project/us-central1",
            "--force"
        ])
        self.assertEqual(args.site_model, "sites/udmi_site_model")
        self.assertEqual(args.device, ["AHU-1"])
        self.assertEqual(args.test, ["pointset_publish"])
        self.assertEqual(args.query, "Explain the failure")
        self.assertEqual(args.vertex, "my-gcp-project/us-central1")
        self.assertTrue(args.force)

    @patch("mantis.engine.harness.credentials.EnvCredentialsProvider.get_client", MagicMock(return_value=MagicMock()))
    @patch("mantis.agent.chat.MantisChatSession.run_interactive_repl", new_callable=AsyncMock)
    async def test_async_main_defaults_to_chat(self, mock_repl):
        await async_main([])
        mock_repl.assert_awaited_once()

    @patch("mantis.engine.harness.credentials.EnvCredentialsProvider.get_client", MagicMock(return_value=MagicMock()))
    @patch("mantis.agent.chat.MantisChatSession.run_interactive_repl", new_callable=AsyncMock)
    async def test_async_main_subcommand_chat(self, mock_repl):
        await async_main(["chat", "sites/udmi_site_model", "AHU-1"])
        mock_repl.assert_awaited_once()

    @patch("mantis.engine.harness.credentials.EnvCredentialsProvider.get_client", MagicMock(return_value=MagicMock()))
    @patch("mantis.agent.chat.MantisChatSession.send_message", new_callable=AsyncMock)
    async def test_async_main_natural_language_query(self, mock_send):
        mock_send.return_value = "Mocked diagnostic answer"
        await async_main(["why", "did", "pointset_publish", "fail?"])
        mock_send.assert_awaited_once_with("why did pointset_publish fail?")

    @patch("mantis.workflows.diagnose.UDMITriageRunner.run_triage", new_callable=AsyncMock)
    async def test_async_main_positional_target_diagnose(self, mock_run_triage):
        await async_main(["diagnose", "sites/udmi_site_model", "AHU-1", "pointset_publish"])
        mock_run_triage.assert_awaited_once()
        args_passed = mock_run_triage.call_args[0][0]
        self.assertEqual(args_passed.device, ["AHU-1"])
        self.assertEqual(args_passed.test, ["pointset_publish"])

    @patch("mantis.workflows.stability.main.main")
    async def test_async_main_subcommand_eval(self, mock_eval_main):
        await async_main(["eval", "-i", "out/runs/"])
        mock_eval_main.assert_called_once_with(["-i", "out/runs/"])

    @patch("mantis.workflows.collector.main")
    async def test_async_main_subcommand_collect(self, mock_collect_main):
        await async_main(["collect", "--runs", "3"])
        mock_collect_main.assert_called_once_with(["--runs", "3"])

    @patch("mantis.workflows.playbook_builder.main")
    async def test_async_main_subcommand_create_playbook(self, mock_pb_main):
        await async_main(["create-playbook", "test.yaml"])
        mock_pb_main.assert_called_once_with(["test.yaml"])


if __name__ == "__main__":
    unittest.main()
