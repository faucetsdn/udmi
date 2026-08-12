"""
Interactive and Programmatic Playbook Generator for Mantis.
"""

import os
import sys
from pathlib import Path
from typing import Any, Dict, Optional

import yaml

from mantis.engine.constants import get_udmi_root


def prompt(question: str, default: str = "") -> str:
    """Helper to prompt user for input with a default value."""
    if default:
        ans = input(f"{question} [{default}]: ")
        return ans if ans else default
    return input(f"{question}: ")


def prompt_multiline(question: str, default: str = "") -> str:
    """Helper to prompt user for multiline text input."""
    print(f"{question} (Enter an empty line to finish):")
    lines = []
    while True:
        try:
            line = input()
            if not line.strip():
                break
            lines.append(line)
        except EOFError:
            break
    if not lines and default:
        return default
    return "\n".join(lines)


def prompt_yes_no(question: str, default: str = "y") -> bool:
    """Helper to prompt user for boolean yes/no confirmation."""
    ans = prompt(question, default).lower()
    return ans.startswith("y")


def generate_custom_playbook(
    name: str,
    description: str,
    analysis_instructions: Optional[str] = None,
    enable_critique: bool = True,
    concurrency: int = 3,
    output_path: Optional[str] = None
) -> str:
    """Programmatically generates a new Mantis YAML playbook file."""
    default_playbook_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "engine", "config", "default_playbook.yaml")
    if not os.path.exists(default_playbook_path):
        default_playbook_path = os.path.join(udmi_root, "util/mantis/v2/engine/config/default_playbook.yaml")

    playbook_data: Dict[str, Any] = {}
    if os.path.exists(default_playbook_path):
        with open(default_playbook_path, "r", encoding="utf-8") as f:
            playbook_data = yaml.safe_load(f) or {}

    playbook_data["metadata"] = {
        "name": name,
        "description": description,
        "version": "1.0.0"
    }

    playbook_data["pipeline"] = {
        "concurrency": concurrency
    }

    if "stages" not in playbook_data:
        playbook_data["stages"] = {}

    if analysis_instructions:
        if "analysis" not in playbook_data["stages"]:
            playbook_data["stages"]["analysis"] = {}
        playbook_data["stages"]["analysis"]["system_instruction"] = analysis_instructions

    if "critique" in playbook_data["stages"]:
        playbook_data["stages"]["critique"]["enabled"] = enable_critique

    out_file = output_path or f"{name.lower().replace(' ', '_')}.yaml"
    out_path = os.path.abspath(out_file)

    with open(out_path, "w", encoding="utf-8") as f:
        yaml.safe_dump(playbook_data, f, default_flow_style=False, sort_keys=False)

    return out_path


def create_playbook_interactive(output_path: Optional[str] = None) -> str:
    """Interactive terminal wizard to generate a customized YAML playbook."""
    print("=========================================")
    print("       🦗 Mantis Playbook Generator       ")
    print("=========================================")
    print("This utility will help you generate a customized YAML playbook.")
    print("Press Enter to accept default values.\n")

    name = prompt("Playbook Name", "Custom Playbook")
    description = prompt("Description", "Custom diagnostic behavior for Mantis triage.")
    concurrency = int(prompt("Max parallel triage jobs (concurrency)", "3"))

    print("\n--- Stages Configuration ---")
    analysis_sys = prompt_multiline("Analysis Stage System Instructions", "")
    enable_critique = prompt_yes_no("Enable Critique Peer-Review Stage? (y/n)", "y")

    default_out = output_path or f"{name.lower().replace(' ', '_')}.yaml"
    out_file = prompt("\nSave playbook to file", default_out)

    saved_path = generate_custom_playbook(
        name=name,
        description=description,
        analysis_instructions=analysis_sys if analysis_sys.strip() else None,
        enable_critique=enable_critique,
        concurrency=concurrency,
        output_path=out_file
    )

    print("\n=========================================")
    print(f"Playbook successfully generated: {saved_path}")
    print("To use this playbook, run:")
    print(f"  bin/mantis triage -i <test_runs> --playbook {saved_path}")
    print("=========================================\n")
    return saved_path


def main(args_list: Optional[list] = None):
    """Entrypoint for create-playbook CLI invocation."""
    out = args_list[0] if args_list else None
    create_playbook_interactive(output_path=out)


if __name__ == "__main__":
    main(sys.argv[1:])
