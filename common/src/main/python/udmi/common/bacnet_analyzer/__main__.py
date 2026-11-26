"""CLI entry point for `python3 -m udmi.common.bacnet_analyzer`."""

import argparse
import json
from pathlib import Path
import sys
from typing import List, Optional

from udmi.common.bacnet_analyzer import (
    analyze_pcap,
    build_analysis_json,
    render_markdown_report,
    write_report_bundle,
)


def main(argv: Optional[List[str]] = None) -> int:
  """Parses CLI arguments, analyzes a PCAP/PCAPNG file, and emits reports."""
  parser = argparse.ArgumentParser(
      prog="bacnet_analyzer",
      description="Analyze a BACnet/IP PCAP or PCAPNG capture file.",
  )
  parser.add_argument("pcap", help="Path to the .pcap or .pcapng capture file.")
  parser.add_argument(
      "-f",
      "--format",
      choices=("json", "markdown"),
      default="json",
      help="Output format for stdout or --output (default: json).",
  )
  parser.add_argument(
      "-o",
      "--output",
      help="Optional output file path (defaults to stdout).",
  )
  parser.add_argument(
      "--report-dir",
      help="Write both canonical analysis.json and report.md into a directory.",
  )
  parser.add_argument(
      "--capture-id",
      help="Optional capture identifier for report metadata.",
  )
  args = parser.parse_args(argv)

  pcap_path = Path(args.pcap)
  if not pcap_path.is_file():
    print(f"Error: capture file not found: {pcap_path}", file=sys.stderr)
    return 2

  result = analyze_pcap(str(pcap_path))
  if args.report_dir:
    paths = write_report_bundle(
        result, args.report_dir, capture_id=args.capture_id
    )
    sys.stdout.write(json.dumps(paths, indent=2) + "\n")
    return 0

  if args.format == "markdown":
    rendered = render_markdown_report(result, capture_id=args.capture_id)
  else:
    rendered = (
        json.dumps(
            build_analysis_json(result, capture_id=args.capture_id), indent=2
        )
        + "\n"
    )

  if args.output:
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(rendered, encoding="utf-8")
  else:
    sys.stdout.write(rendered)
  return 0


if __name__ == "__main__":
  sys.exit(main())
