#!/usr/bin/env python3
"""Script to parse JSON stream from stdin and save messages to databases.

Uses Butler ingestion engine with consistent UDMI DB schema.
"""
import os
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, REPO_ROOT)

# pylint: disable=wrong-import-position
from butler.src.service import ButlerService


def main():
  """Main entry point for pull_capture."""
  service = ButlerService(always_save_raw=True)
  service.run_stdin()


if __name__ == "__main__":
  main()

