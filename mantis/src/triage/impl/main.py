#!/usr/bin/env python3
import asyncio
import os
import sys

from triage.impl.cli import UDMITriageCLI
from triage.impl.runner import UDMITriageRunner

IMPL_DIR = os.path.dirname(os.path.abspath(__file__))  # src/triage/impl
TRIAGE_DIR = os.path.dirname(IMPL_DIR)                 # src/triage
SRC_DIR = os.path.dirname(TRIAGE_DIR)                  # src
MANTIS_DIR = os.path.dirname(SRC_DIR)                  # mantis root
UDMI_ROOT = os.path.dirname(MANTIS_DIR)                # udmi root

async def async_main():
    cli = UDMITriageCLI()
    args = cli.parse()
    runner = UDMITriageRunner(udmi_root=UDMI_ROOT, mantis_dir=MANTIS_DIR)
    await runner.run_triage(args)

def main():
    try:
        asyncio.run(async_main())
    except KeyboardInterrupt:
        print("\nTriage run cancelled by user.")
        sys.exit(1)

if __name__ == "__main__":
    main()
