import argparse
from typing import Any, List

class UDMITriageCLI:
    """Encapsulates all command-line arguments parsing and flag configurations for Mantis."""

    def __init__(self):
        self.parser = argparse.ArgumentParser(
            description="Mantis Triage Agent - AI-Powered Diagnostics"
        )
        self._setup_arguments()

    def _setup_arguments(self):
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


    def parse(self, args_list: List[str] = None) -> argparse.Namespace:
        """Parses command-line arguments into Namespace."""
        return self.parser.parse_args(args_list)
