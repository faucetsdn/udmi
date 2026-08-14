"""
Mantis Unified Command-Line Interface and Request Dispatcher.
"""

import argparse
import asyncio
import os
import sys
from typing import Any, List, Optional, Tuple

from mantis.engine.constants import get_udmi_root


def parse_vertex_spec(spec: Optional[str]) -> Tuple[bool, Optional[str], Optional[str]]:
    """
    Parses the --vertex argument into (use_vertex, project_id, location).

    Supported Formats:
      - None (flag omitted) -> (False, None, None)
      - "AUTO" or "" (flag present with no value, e.g. `bin/mantis --vertex`) -> (True, None, "global")
      - "<project_id>" (e.g. `bin/mantis --vertex my-gcp-project`) -> (True, "my-gcp-project", "global")
      - "<project_id>/<region>" (e.g. `bin/mantis --vertex my-gcp-project/us-central1`) -> (True, "my-gcp-project", "us-central1")
      - "/<region>" (e.g. `bin/mantis --vertex /us-central1`) -> (True, None, "us-central1")
    """
    if spec is None:
        return False, None, None

    spec_str = spec.strip()
    if spec_str in ("AUTO", "", "true", "True", "1"):
        return True, None, "global"

    if "/" in spec_str:
        parts = spec_str.split("/", 1)
        project = parts[0].strip() or None
        location = parts[1].strip() or "global"
        return True, project, location

    return True, spec_str, "global"


class UDMITriageCLI:
    """Encapsulates argument parsing, subcommands, and flag configuration for Mantis."""

    def __init__(self):
        self.parser = argparse.ArgumentParser(
            prog="mantis",
            description="Mantis: AI-Powered Autonomous Diagnostics and Triage for UDMI",
            formatter_class=argparse.RawDescriptionHelpFormatter,
            epilog="""
Examples:
  # 1. Interactive Chat Mode (Default)
  bin/mantis
  bin/mantis chat --site sites/udmi_site_model
  bin/mantis --vertex my-gcp-project/us-central1

  # 2. Targeted Diagnostics
  bin/mantis sites/udmi_site_model AHU-1 pointset_publish
  bin/mantis diagnose --site sites/udmi_site_model --device AHU-1 --test pointset_publish

  # 3. One-Shot Natural Language Query
  bin/mantis "How does UDMI pointset validation handle missing units?"
  bin/mantis -q "Why did AHU-1 fail pointset_publish?"

  # 4. Multi-Run Stability & Bundle Triage
  bin/mantis eval -i out/mantis/run_1/ out/mantis/run_2/
  bin/mantis triage -i bundle1.zip bundle2.zip

  # 5. Active Test Loop Collection
  bin/mantis collect --target //mqtt/localhost:46432 --runs 3

  # 6. Generate Custom Playbook
  bin/mantis create-playbook my_custom_playbook.yaml
"""
        )
        self._setup_arguments()

    def _setup_arguments(self):
        self.parser.add_argument(
            "targets", nargs="*", default=[],
            help="Subcommand (chat, diagnose, eval, collect, triage, create-playbook), target spec (site, device, test), or query"
        )
        self.parser.add_argument(
            "--site", "-s", "--site-model", dest="site_model",
            help="Target site model directory or name (e.g. sites/udmi_site_model)"
        )
        self.parser.add_argument(
            "--test-runs", "-i", dest="test_runs",
            help="Input directory containing run backups (single or multi-run)"
        )
        self.parser.add_argument(
            "--manifest", "-m", dest="manifest",
            help="Path to an intermediate JSON manifest file compiled during stability evaluation"
        )
        self.parser.add_argument(
            "--id", nargs="+",
            help="Specific failure ID(s) to triage (from triage_manifest.json)"
        )
        self.parser.add_argument(
            "--test", "-t", nargs="+",
            help="Specific test case(s) to triage (sweeps all failures if omitted)"
        )
        self.parser.add_argument(
            "--device", "-d", nargs="+",
            help="Specific device ID(s) to triage"
        )
        self.parser.add_argument(
            "--force", "-f", action="store_true",
            help="Force execution by bypassing the semantic cache completely"
        )
        self.parser.add_argument(
            "--playbook",
            help="Path to a custom Playbook YAML configuration"
        )
        self.parser.add_argument(
            "--project-path", "-p",
            help="Path to the UDMI project root (default: auto-detected)"
        )
        self.parser.add_argument(
            "--chat", "-c", action="store_true",
            help="Launch Mantis in interactive Chat Mode REPL"
        )
        self.parser.add_argument(
            "--query", "-q",
            help="Execute a one-shot natural language query against Mantis and exit"
        )
        self.parser.add_argument(
            "--vertex", nargs="?", const="AUTO", default=None,
            help="Enable Google Cloud Vertex AI with optional project/region spec (e.g. --vertex or --vertex <gcp-project-id>/<region>)"
        )
        self.parser.add_argument(
            "--all", action="store_true",
            help="Triage all identified failures in manifest/directory"
        )

    def parse(self, args_list: Optional[List[str]] = None) -> argparse.Namespace:
        """Parses command-line arguments into a Namespace."""
        args, _ = self.parser.parse_known_args(args_list)
        return args


async def async_main(args_list: Optional[List[str]] = None):
    """Main asynchronous execution router."""
    udmi_root = get_udmi_root()
    mantis_dir = os.path.join(udmi_root, "util", "mantis")

    raw_args = list(sys.argv[1:] if args_list is None else args_list)

    # 1. Handle explicit Subcommands before general flag parsing
    if raw_args and raw_args[0].lower() in ("eval", "evaluate", "stability"):
        from mantis.workflows.stability.main import main as eval_main
        eval_main(raw_args[1:])
        return

    if raw_args and raw_args[0].lower() == "collect":
        from mantis.workflows.collector import main as collect_main
        collect_main(raw_args[1:])
        return

    if raw_args and raw_args[0].lower() in ("create-playbook", "new-playbook", "playbook-builder"):
        from mantis.workflows.playbook_builder import main as playbook_main
        playbook_main(raw_args[1:])
        return

    cli = UDMITriageCLI()
    args = cli.parse(args_list)

    if getattr(args, "project_path", None):
        udmi_root = os.path.abspath(os.path.expanduser(args.project_path))
        mantis_dir = os.path.join(udmi_root, "util", "mantis")

    # Parse --vertex spec (e.g. None, "AUTO", "my-project", "my-project/us-central1")
    use_vertex, gcp_project, gcp_location = parse_vertex_spec(args.vertex)

    targets = list(args.targets or [])

    # Handle chat / repl / diagnose subcommand keywords
    if targets and targets[0].lower() in ("chat", "repl"):
        targets.pop(0)
        from mantis.agent.chat import MantisChatSession
        site_model = args.site_model or (targets[0] if len(targets) > 0 else None)
        device_id = (args.device[0] if getattr(args, "device", None) else None) or (targets[1] if len(targets) > 1 else None)
        test_id = (args.test[0] if getattr(args, "test", None) else None) or (targets[2] if len(targets) > 2 else None)

        chat_session = MantisChatSession(
            udmi_root=udmi_root,
            mantis_dir=mantis_dir,
            site_model=site_model,
            device_id=device_id,
            test_id=test_id,
            manifest_path=getattr(args, "manifest", None),
            test_runs_dir=getattr(args, "test_runs", None),
            playbook_path=getattr(args, "playbook", None),
            use_vertex=use_vertex or None,
            gcp_project=gcp_project,
            gcp_location=gcp_location
        )
        await chat_session.run_interactive_repl()
        return

    if targets and targets[0].lower() == "diagnose":
        targets.pop(0)

    # 2. Extract positional arguments if provided
    site_model = args.site_model
    device_id = args.device[0] if getattr(args, "device", None) else None
    test_id = args.test[0] if getattr(args, "test", None) else None
    query = args.query

    if targets:
        first_target = targets[0]
        # Check if first target is a natural language question
        if " " in first_target or first_target.endswith("?") or first_target.lower().startswith(("why", "how", "what", "where", "explain")):
            query = " ".join(targets)
        # Check if first target is a bundle archive or directory containing runs
        elif first_target.endswith((".zip", ".tgz", ".tar.gz")):
            from mantis.workflows.stability.main import main as eval_main
            eval_main(["--test-runs", first_target])
            return
        # Positional targets: [site_model] [device_id] [test_id]
        else:
            if not site_model:
                site_model = targets[0]
            if len(targets) > 1 and not device_id:
                device_id = targets[1]
            if len(targets) > 2 and not test_id:
                test_id = targets[2]

    # 3. Route to Interactive Chat or One-Shot Query Mode
    is_no_args = not targets and not args.test_runs and not args.manifest and not site_model and not device_id and not test_id and not query
    if is_no_args or args.chat or query:
        from mantis.agent.chat import MantisChatSession
        chat_session = MantisChatSession(
            udmi_root=udmi_root,
            mantis_dir=mantis_dir,
            site_model=site_model,
            device_id=device_id,
            test_id=test_id,
            manifest_path=getattr(args, "manifest", None),
            test_runs_dir=getattr(args, "test_runs", None),
            playbook_path=getattr(args, "playbook", None),
            use_vertex=use_vertex or None,
            gcp_project=gcp_project,
            gcp_location=gcp_location
        )
        if query:
            response = await chat_session.send_message(query)
            print(response)
        else:
            await chat_session.run_interactive_repl()
        return

    # 4. Standard / Targeted Diagnostics Workflow
    out_dir = args.test_runs or (os.path.dirname(os.path.abspath(args.manifest)) if args.manifest else None)
    from mantis.workflows.diagnose import UDMITriageRunner
    runner = UDMITriageRunner(udmi_root=udmi_root, mantis_dir=mantis_dir, out_dir=out_dir)

    if device_id and not getattr(args, "device", None):
        args.device = [device_id]
    if test_id and not getattr(args, "test", None):
        args.test = [test_id]

    await runner.run_triage(args)


def main(args_list: Optional[List[str]] = None):
    """Synchronous entry point for console_scripts."""
    try:
        asyncio.run(async_main(args_list))
    except KeyboardInterrupt:
        print("\nMantis execution cancelled by user.")
        sys.exit(1)


if __name__ == "__main__":
    main()
