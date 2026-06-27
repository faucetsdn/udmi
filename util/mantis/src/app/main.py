#!/usr/bin/env python3
import asyncio
import os
import sys

from app.cli import UDMITriageCLI
from app.runner import UDMITriageRunner

IMPL_DIR = os.path.dirname(os.path.abspath(__file__))  # src/app
SRC_DIR = os.path.dirname(IMPL_DIR)                  # src
MANTIS_DIR = os.path.dirname(SRC_DIR)                  # mantis root
UDMI_ROOT = os.path.dirname(os.path.dirname(MANTIS_DIR))                # udmi root


async def async_main():
    cli = UDMITriageCLI()
    args = cli.parse()
    
    out_dir = None
    if getattr(args, "test_runs", None):
        out_dir = args.test_runs
    elif getattr(args, "manifest", None):
        out_dir = os.path.dirname(os.path.abspath(args.manifest))

    udmi_root_dir = UDMI_ROOT
    if getattr(args, "project_path", None):
        udmi_root_dir = os.path.abspath(os.path.expanduser(args.project_path))
        
    runner = UDMITriageRunner(udmi_root=udmi_root_dir, mantis_dir=MANTIS_DIR, out_dir=out_dir)
    await runner.run_triage(args)

def main():
    try:
        asyncio.run(async_main())
    except KeyboardInterrupt:
        print("\nTriage run cancelled by user.")
        sys.exit(1)

if __name__ == "__main__":
    main()
