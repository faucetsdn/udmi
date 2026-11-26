"""CLI entry point for `python3 -m udmi.common.bacnet_analyzer`."""

import argparse
import json
from pathlib import Path
import sys
from typing import List, Optional

from udmi.common.bacnet_analyzer import analyze_pcap


def main(argv: Optional[List[str]] = None) -> int:
  """Parses CLI arguments, analyzes a PCAP/PCAPNG file, and prints JSON."""
  parser = argparse.ArgumentParser(
      prog="bacnet_analyzer",
      description="Analyze a BACnet/IP PCAP or PCAPNG capture file.",
  )
  parser.add_argument("pcap", help="Path to the .pcap or .pcapng capture file.")
  parser.add_argument(
      "-o",
      "--output",
      help="Optional output path for JSON results (defaults to stdout).",
  )
  args = parser.parse_args(argv)

  pcap_path = Path(args.pcap)
  if not pcap_path.is_file():
    print(f"Error: capture file not found: {pcap_path}", file=sys.stderr)
    return 2

  result = analyze_pcap(str(pcap_path))
  rendered = json.dumps(result.to_dict(), indent=2) + "\n"
  if args.output:
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(rendered, encoding="utf-8")
  else:
    sys.stdout.write(rendered)
  return 0


if __name__ == "__main__":
  sys.exit(main())
