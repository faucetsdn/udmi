"""MQTT recorded trace and envelope inspector."""

import json
import os
from typing import Any, Dict, List, Optional


def _get_udmi_root(udmi_root: Optional[str] = None) -> str:
    if udmi_root is not None:
        return os.path.abspath(udmi_root)
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def inspect_traces(
    trace_dir: Optional[str] = None,
    test_id: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Inspect recorded MQTT packet traces and envelope payloads."""
    root = _get_udmi_root(udmi_root)
    target_dir = trace_dir
    if not target_dir:
        candidates = [
            os.path.join(root, "trace"),
            os.path.join(root, "out", "trace"),
            os.path.join(root, "var", "trace"),
        ]
        for c in candidates:
            if os.path.isdir(c):
                target_dir = c
                break

    if not target_dir or not os.path.isdir(target_dir):
        return {
            "status": "SUCCESS",
            "trace_dir": target_dir or "none",
            "count": 0,
            "packets": [],
        }

    packets = []
    for root_path, _, files in os.walk(target_dir):
        for f in sorted(files):
            if f.endswith(".json") or f.endswith(".trace") or f.endswith(".out"):
                full_path = os.path.join(root_path, f)
                try:
                    with open(full_path, "r", encoding="utf-8", errors="replace") as fh:
                        content = fh.read(4096)
                        parsed = None
                        try:
                            parsed = json.loads(content)
                        except Exception:
                            pass
                        packets.append({
                            "file": os.path.relpath(full_path, target_dir),
                            "size": len(content),
                            "parsed": parsed is not None,
                            "preview": content[:200],
                        })
                except Exception:
                    pass

    return {
        "status": "SUCCESS",
        "trace_dir": target_dir,
        "count": len(packets),
        "packets": packets[:50],
    }
