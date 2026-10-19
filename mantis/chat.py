"""Interactive REPL console and canonical session control commands for Mantis."""

import os
import sys
from datetime import datetime
from typing import List, Optional

from mantis.agent import MantisAgent
from mantis.context import ContextManager
from mantis.models import SessionContext


HELP_TEXT = """
Mantis Canonical Session Commands:
  /status        Display current active context, environments, and port assignments.
  /logs <window> Capture console buffer from a named tmux window (main, dut, sequencer, butler, validator).
  /clear         Reset conversation history while preserving active sessions.
  /export [file] Export conversation history and diagnostic reports to a Markdown file.
  /help          Display this help message.
  /exit          Terminate the interactive session.

Natural Language Routing:
  Simply type your instruction to run tests, provision environments, triage failures,
  inspect schemas, or patch site models (e.g. "Why did pointset_publish fail for AHU-1?").
"""


class ChatConsole:
    """Multi-turn interactive diagnostic REPL console."""

    def __init__(self, agent: Optional[MantisAgent] = None, context: Optional[SessionContext] = None):
        self.agent = agent or MantisAgent()
        if context is not None:
            self.context_mgr = ContextManager(context=context, udmi_root=self.agent.udmi_root)
        else:
            self.context_mgr = ContextManager(persist=True, udmi_root=self.agent.udmi_root)

    @property
    def history(self) -> List[dict]:
        """Backward compatibility for history list."""
        return [
            {"role": msg.role.value, "content": msg.content, "timestamp": msg.timestamp}
            for msg in self.context_mgr.context.history
        ]

    def start(self) -> None:
        """Starts the interactive session REPL loop."""
        active_sess = self.context_mgr.context.active_session_id or self.agent.active_session_id or "None"
        from mantis.config import ProviderType
        if self.agent.config.provider == ProviderType.VERTEX_AI:
            proj = os.getenv("GOOGLE_CLOUD_PROJECT", os.getenv("GCP_PROJECT", self.agent.config.default_gcp_project))
            loc = os.getenv("GOOGLE_CLOUD_REGION", os.getenv("GCP_REGION", self.agent.config.default_gcp_location))
            prov_str = f"Vertex AI ({proj}:{loc})"
        elif self.agent.config.provider == ProviderType.AI_STUDIO:
            prov_str = "Google AI Studio"
        else:
            prov_str = "Offline Deterministic"

        print("Mantis: Autonomous UDMI Agent & Diagnostic Console")
        print(f"[Provider: {prov_str} | Context: {self.context_mgr.context.active_site_model} | Active Session: {active_sess}]\n")

        while True:
            try:
                user_input = input("you > ").strip()
            except (KeyboardInterrupt, EOFError):
                print("\nExiting Mantis session.")
                break

            if not user_input:
                continue

            if user_input.startswith("/"):
                handled = self.handle_command(user_input)
                if handled == "EXIT":
                    break
                continue

            # Route natural language instruction through cognitive agent with persistent context
            print()
            response_chunks = []

            def stream_printer(chunk: str) -> None:
                sys.stdout.write(chunk)
                sys.stdout.flush()
                response_chunks.append(chunk)

            self.agent.run(
                user_input,
                context=self.context_mgr.context,
                stream_callback=stream_printer,
            )
            print("\n")

    def handle_command(self, cmd_line: str) -> Optional[str]:
        """Handles canonical slash commands."""
        parts = cmd_line.strip().split()
        cmd = parts[0].lower()
        args = parts[1:]

        if cmd in ("/exit", "/quit"):
            print("Terminating Mantis session.")
            return "EXIT"

        elif cmd == "/help":
            print(HELP_TEXT)

        elif cmd == "/status":
            setups = self.agent.session_mgr.list_test_setups()
            skills = self.agent.skills.list_skills()
            ctx = self.context_mgr.context
            from mantis.config import ProviderType
            if self.agent.config.provider == ProviderType.VERTEX_AI:
                proj = os.getenv("GOOGLE_CLOUD_PROJECT", os.getenv("GCP_PROJECT", self.agent.config.default_gcp_project))
                loc = os.getenv("GOOGLE_CLOUD_REGION", os.getenv("GCP_REGION", self.agent.config.default_gcp_location))
                prov_str = f"Vertex AI (Project: {proj}, Region: {loc})"
            elif self.agent.config.provider == ProviderType.AI_STUDIO:
                prov_str = "Google AI Studio"
            else:
                prov_str = "Offline Deterministic"

            print("### Mantis System Status")
            print(f"* **AI Provider**: `{prov_str}`")
            print(f"* **Pro Model**: `{self.agent.config.pro_model}`")
            print(f"* **Flash Model**: `{self.agent.config.flash_model}`")
            print(f"* **Active Site Model**: `{ctx.active_site_model}`")
            print(f"* **Active Session ID**: `{ctx.active_session_id or self.agent.active_session_id or 'None'}`")
            print(f"* **Active Target Device**: `{ctx.active_device_id or 'None'}`")
            print(f"* **Active Test Sequence**: `{ctx.active_test_id or 'None'}`")
            print(f"* **Conversation Turns**: {len(ctx.history)}")
            print(f"* **Loaded Domain Skills**: {len(skills)} skills ({', '.join(s['name'] for s in skills)})")
            print(f"* **Active Local Stacks**: {len(setups)}")
            for s in setups:
                print(f"  - Session `{s.get('session_name')}`: URL={s.get('connection_url')} Windows={s.get('windows')}")

        elif cmd == "/logs":
            window = args[0] if args else "main"
            test_id = self.context_mgr.context.active_session_id or self.agent.active_session_id or "dev_1"
            try:
                logs = self.agent.session_mgr.get_test_logs(test_id=test_id, window=window, lines=60)
                print(f"### Recent Logs [{test_id}:{window}]:\n{logs}")
            except Exception as e:
                print(f"Error capturing logs: {e}")

        elif cmd == "/clear":
            self.context_mgr.clear_history()
            print("Conversation history cleared. Active session environments preserved.")

        elif cmd == "/export":
            filename = args[0] if args else f"mantis_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.md"
            try:
                with open(filename, "w", encoding="utf-8") as f:
                    f.write(f"# Mantis Diagnostic Session Export\n\nGenerated: {datetime.now().isoformat()}\n\n")
                    for entry in self.context_mgr.context.history:
                        f.write(f"### {entry.role.value.upper()} ({entry.timestamp})\n\n{entry.content}\n\n---\n\n")
                print(f"Exported session transcript to `{filename}`")
            except Exception as e:
                print(f"Error exporting transcript: {e}")

        else:
            print(f"Unrecognized command '{cmd}'. Type `/help` for available commands or enter a natural language instruction.")

        return None
