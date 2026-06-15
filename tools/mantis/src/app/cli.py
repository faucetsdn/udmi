import argparse
from typing import Any, List

class UDMITriageCLI:
    """Encapsulates all command-line arguments parsing and flag configurations for Mantis."""

    def __init__(self):
        self.parser = argparse.ArgumentParser(
            description="Mantis Triage Agent (Diagnose) - AI-Powered Diagnostics"
        )
        self._setup_arguments()

    def _setup_arguments(self):
        self.parser.add_argument(
            "--bundles-dir", "-i", dest="bundles_dir",
            help="Input bundles directory containing run backups (single or multi-run)"
        )
        self.parser.add_argument("--run-dir", help=argparse.SUPPRESS)  # Suppressed legacy compatibility alias
        self.parser.add_argument(
            "--manifest", "-m", dest="manifest",
            help="Path to an intermediate JSON manifest file compiled during stability evaluation"
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
            "--sequence-log", "-sl",
            help="Direct path to sequencer test log file (sequence.log / sequencer.log)"
        )
        self.parser.add_argument(
            "--device-log", "-dl",
            help="Direct path to device/pubber log file"
        )
        self.parser.add_argument(
            "--udmis-log", "-ul",
            help="Direct path to UDMIS log file"
        )
        self.parser.add_argument(
            "--success-log", "-scl",
            help="Direct path to previous successful sequencer log file"
        )
        self.parser.add_argument(
            "--target",
            help="Target project (default: auto-detected from bundles-dir)"
        )
        self.parser.add_argument(
            "--site-dir",
            help="Path to site model folder (default: auto-detected)"
        )
        self.parser.add_argument(
            "--concurrency", "-c", type=int, default=3,
            help="Maximum parallel triage jobs (default: 3)"
        )
        self.parser.add_argument(
            "--force", "-f", action="store_true",
            help="Force execution by bypassing the semantic cache completely"
        )
        self.parser.add_argument(
            "--oem", action="store_true",
            help="Use the oem_integrator playbook instead of the default playbook"
        )

    def parse(self, args_list: List[str] = None) -> argparse.Namespace:
        """Parses command-line arguments into Namespace."""
        return self.parser.parse_args(args_list)
