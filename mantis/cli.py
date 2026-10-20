"""Universal CLI Dispatcher for Mantis (Interactive / Headless / --mcp)."""

import os
import sys
from typing import List, Optional

from mantis import preflight, provider_setup

SETUP_USAGE = """Usage:
  bin/mantis setup --vertex=<project>[/<region>]   Save Vertex AI as the model provider (region default: global)
  bin/mantis setup --clear                         Remove the saved provider
  bin/mantis setup                                 Show the saved provider

The choice is saved in ~/.config/udmi/workbench.json and applied by bin/mantis and
bin/workbench at startup. Vertex AI uses your application-default credentials:
  gcloud auth application-default login
"""


def setup(args: List[str]) -> int:
    """`bin/mantis setup ...`: the one-time provider setup."""
    try:
        if not args:
            record = provider_setup.load()
            if record is None:
                print(f"No saved Mantis provider in {provider_setup.config_path()}.\n")
            else:
                print(
                    f"Mantis uses Vertex AI project '{record['project']}', region "
                    f"'{record['region']}' (saved in {provider_setup.config_path()}).\n"
                )
            print(SETUP_USAGE)
            return 0
        if args == ["--clear"]:
            removed = provider_setup.clear()
            where = provider_setup.config_path()
            print(f"Removed the saved Mantis provider from {where}." if removed
                  else f"No saved Mantis provider in {where}; nothing to remove.")
            return 0
        if len(args) == 1 and args[0].startswith("--vertex="):
            record = provider_setup.parse_vertex_spec(args[0].split("=", 1)[1])
            adc_problem = preflight.adc_problem()
            if adc_problem:
                print(f"Error: Vertex AI needs application-default credentials ({adc_problem}).\n"
                      "Run: gcloud auth application-default login", file=sys.stderr)
                return 1
            where = provider_setup.save(record)
            print(f"Saved: Mantis uses Vertex AI project '{record['project']}', region "
                  f"'{record['region']}' (in {where}).")
            clash = provider_setup.conflict(record)
            if clash:
                print(f"Note: bin/mantis and bin/workbench will refuse to start until this is resolved. {clash}",
                      file=sys.stderr)
            return 0
    except provider_setup.ProviderSetupError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1
    print(f"Error: unrecognised setup arguments: {' '.join(args)}\n", file=sys.stderr)
    print(SETUP_USAGE, file=sys.stderr)
    return 2


def main(argv: Optional[List[str]] = None) -> int:
    """Universal entrypoint for Mantis."""
    if argv is None:
        argv = sys.argv[1:]

    if argv and argv[0] == "setup":
        return setup(argv[1:])

    # Check for --mcp flag
    if "--mcp" in argv:
        if not preflight.report(preflight.run(preflight.mantis_checks("mcp")), "The Mantis MCP server"):
            return 1
        from mantis import mcp_server
        mcp_server.main()
        return 0

    # Check for --offline flag
    if "--offline" in argv:
        os.environ["MANTIS_OFFLINE"] = "true"
        argv = [a for a in argv if a != "--offline"]

    # Check for --vertex flag
    vertex_args = [a for a in argv if a.startswith("--vertex")]
    if vertex_args:
        for v in vertex_args:
            argv = [a for a in argv if a != v]
            spec = v.split("=", 1)[1] if "=" in v else "AUTO"
            if spec != "AUTO" and "/" in spec:
                p, r = spec.split("/", 1)
                if p:
                    os.environ["GOOGLE_CLOUD_PROJECT"] = p
                if r:
                    os.environ["GOOGLE_CLOUD_REGION"] = r
            elif spec != "AUTO" and spec:
                os.environ["GOOGLE_CLOUD_PROJECT"] = spec

    # Check for help
    if "-h" in argv or "--help" in argv:
        print("""Mantis: Autonomous UDMI Agent & Diagnostic Management Control Plane

Usage:
  bin/mantis                     Launch interactive diagnostic session
  bin/mantis "<instruction>"     Execute instruction or query headless
  bin/mantis <bundle.zip>        Autonomously triage support bundle
  bin/mantis --mcp               Run stdio Model Context Protocol (MCP) server
  bin/mantis --offline           Force offline deterministic mode
  bin/mantis setup --vertex=<project>[/<region>]
                                 Save Vertex AI as the provider (one-time setup)

Provider Configuration:
  Default: Google Cloud Vertex AI in the project saved by `bin/mantis setup`, or
  named by GOOGLE_CLOUD_PROJECT (or GCP_PROJECT), Region: global. There is no
  built-in project.
  One-time setup:
    bin/mantis setup --vertex=my-project/us-central1
  To set GCP Project/Region for one shell:
    export GCP_PROJECT="my-project"
    export GCP_REGION="us-central1"
    or CLI: bin/mantis --vertex=my-project/us-central1
  To use Google AI Studio:
    export GEMINI_API_KEY="AIzaSy..."
  To force offline mode:
    export MANTIS_OFFLINE=true (or pass --offline)

Examples:
  bin/mantis "Bring up the local sequencer setup for sites/udmi_site_model"
  bin/mantis "Why did pointset_publish fail for AHU-1?"
  bin/mantis "What are the required fields in pointset schema?"
  bin/mantis "Start an isolated local environment with DUT AHU-1"
  bin/mantis "Set sample_rate_sec to 10 for AHU-1 in sites/udmi_site_model"
  bin/mantis support_bundle.zip
""")
        return 0

    # Check for version
    if "--version" in argv or "-v" in argv:
        print("Mantis v2.0.0 (Unified UDMI Diagnostic Engine)")
        return 0

    # Checked after --offline/--vertex have set the provider environment, and
    # before importing the agent, so a missing module is reported with its fix.
    if not preflight.report(preflight.run(preflight.mantis_checks("cli")), "Mantis"):
        return 1

    from mantis.agent import MantisAgent
    from mantis.chat import ChatConsole

    # Filter any empty args
    cleaned_args = [a for a in argv if a.strip()]

    # Case 1: No arguments -> Interactive REPL
    if not cleaned_args:
        console = ChatConsole()
        console.start()
        return 0

    # Case 2: Headless Single-Shot Execution
    instruction = " ".join(cleaned_args)
    agent = MantisAgent()
    output = agent.run_headless(instruction)
    print(output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
