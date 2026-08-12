import json
import os
from typing import Any, Dict, List, Optional
from mantis.tools.artifacts import locate_test_artifacts


def inspect_message_trace(
    udmi_root: str,
    site_name: Optional[str] = None,
    device_id: Optional[str] = None,
    test_name: Optional[str] = None,
    message_type: Optional[str] = None
) -> str:
    """
    Inspects recorded MQTT message payloads (events, state, config, validation)
    captured during a specific test execution.
    """
    artifacts_data = locate_test_artifacts(
        udmi_root=udmi_root,
        site=site_name,
        device=device_id,
        test=test_name
    )
    art = artifacts_data.get("artifacts", {})

    output = [
        f"### Message Traces for Test: `{test_name or 'All'}` (Device: `{device_id or 'All'}`, Site: `{site_name or 'Default'}`)"
    ]

    all_trace_files = []
    for category in ("events_json", "state_json", "config_json"):
        for rel_path in art.get(category, []):
            all_trace_files.append(rel_path)

    if not all_trace_files:
        return f"No recorded message trace files found for site='{site_name}', device='{device_id}', test='{test_name}'."

    for rel_path in all_trace_files:
        fname = os.path.basename(rel_path)
        if message_type and message_type.lower() not in fname.lower():
            continue

        full_path = os.path.join(udmi_root, rel_path)
        if not os.path.exists(full_path):
            continue

        try:
            with open(full_path, "r", encoding="utf-8") as f:
                content = f.read().strip()
                # Try parsing as JSON
                try:
                    parsed = json.loads(content)
                    formatted = json.dumps(parsed, indent=2)
                except Exception:
                    formatted = content

                output.append(f"\n#### Payload: `{fname}` (`{rel_path}`)")
                output.append(f"```json\n{formatted}\n```")
        except Exception as e:
            output.append(f"\n*(Error reading {fname}: {e})*")

    return "\n".join(output)
