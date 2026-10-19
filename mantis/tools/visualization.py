"""Dual Visualization Engine for Mantis (Graphviz DOT and Mermaid diagram generation)."""

import json
import os
import re
import subprocess
from typing import Any, Dict, List, Optional

from mantis.tools.artifacts import extract_timeline
from mantis.tools.site_models import list_site_model_devices, resolve_site_model_path


def _get_udmi_root(udmi_root: Optional[str] = None) -> str:
    if udmi_root is not None:
        return os.path.abspath(udmi_root)
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def render_dot_to_svg(dot_content: str) -> Dict[str, Any]:
    """Compiles Graphviz DOT syntax to SVG using the local /usr/bin/dot utility."""
    dot_path = "/usr/bin/dot"
    if not os.path.isfile(dot_path) or not os.access(dot_path, os.X_OK):
        # Check in PATH
        import shutil
        dot_path = shutil.which("dot")

    if not dot_path:
        return {
            "status": "ERROR",
            "error": "Graphviz 'dot' executable not found on system PATH.",
        }

    try:
        proc = subprocess.run(
            [dot_path, "-Tsvg"],
            input=dot_content,
            text=True,
            capture_output=True,
            timeout=10,
        )
        if proc.returncode != 0:
            return {
                "status": "ERROR",
                "error": f"Graphviz dot compilation failed: {proc.stderr.strip()}",
            }
        return {
            "status": "SUCCESS",
            "svg": proc.stdout,
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "error": f"Failed to execute dot: {e}",
        }


def generate_topology_diagram(
    site_model: str = "sites/udmi_site_model",
    focus_device: Optional[str] = None,
    format: str = "both",
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Generates visual architecture and network topology diagrams in Graphviz DOT and Mermaid formats.
    
    Args:
        site_model: Path to the site model directory.
        focus_device: Optional device ID to focus or highlight in the topology.
        format: Diagram format to return ('dot', 'mermaid', or 'both').
        udmi_root: Optional override for the UDMI repository root directory.
    """
    site_path = resolve_site_model_path(site_model, udmi_root)
    if not os.path.isdir(site_path):
        return {
            "status": "ERROR",
            "error": f"Site model directory not found: {site_model} (resolved to {site_path})",
        }

    devices_dir = os.path.join(site_path, "devices")
    all_devices = list_site_model_devices(site_path)

    # Inspect each device metadata
    device_data: Dict[str, Dict[str, Any]] = {}
    gateways: Dict[str, List[str]] = {}  # gw_id -> list of sub_device_ids
    direct_devices: List[str] = []

    for dev in all_devices:
        meta_file = os.path.join(devices_dir, dev, "metadata.json")
        meta: Dict[str, Any] = {}
        if os.path.isfile(meta_file):
            try:
                with open(meta_file, "r", encoding="utf-8", errors="replace") as f:
                    meta = json.load(f)
            except Exception:
                pass

        gw_id = meta.get("gateway", {}).get("gateway_id")
        points = meta.get("pointset", {}).get("points", {})
        point_count = len(points) if isinstance(points, dict) else 0
        make_model = meta.get("system", {}).get("make_model", "")

        device_data[dev] = {
            "device_id": dev,
            "gateway_id": gw_id,
            "point_count": point_count,
            "make_model": make_model,
        }

        if gw_id:
            gateways.setdefault(gw_id, []).append(dev)
        else:
            direct_devices.append(dev)

    # 1. Build Graphviz DOT
    dot_lines = [
        "digraph SiteTopology {",
        "  rankdir=LR;",
        "  compound=true;",
        '  graph [fontname="Helvetica", fontsize=11, bgcolor="#FAFAFA", style="rounded", color="#CCCCCC"];',
        '  node [fontname="Helvetica", fontsize=10, shape=box, style="rounded,filled", fillcolor="#FFFFFF", color="#4285F4"];',
        '  edge [fontname="Helvetica", fontsize=9, color="#5F6368"];',
        "",
        '  subgraph cluster_transport {',
        '    label="Transport / Broker Layer";',
        '    style="dashed,rounded"; color="#4285F4"; bgcolor="#F8FAFD";',
        '    broker [label="MQTT Broker\\n(//mqtt/localhost)", shape=box3d, fillcolor="#E8F0FE", color="#1A73E8", penwidth=2];',
        "  }",
        "",
    ]

    # Gateways cluster
    if gateways:
        dot_lines.append('  subgraph cluster_gateways {')
        dot_lines.append('    label="Gateways";')
        dot_lines.append('    style="dashed,rounded"; color="#34A853"; bgcolor="#F6FAF7";')
        for gw in sorted(gateways.keys()):
            gw_info = device_data.get(gw, {})
            gw_label = f"Gateway: {gw}"
            if gw_info.get("make_model"):
                gw_label += f"\\n({gw_info['make_model']})"
            is_focus = (focus_device == gw)
            pen = "3" if is_focus else "1.5"
            border_color = "#EA4335" if is_focus else "#1E8E3E"
            dot_lines.append(
                f'    "{gw}" [label="{gw_label}", shape=component, fillcolor="#CEEAD6", color="{border_color}", penwidth={pen}];'
            )
        dot_lines.append("  }")
        dot_lines.append("")

    # Proxy Sub-Devices cluster
    if any(gateways.values()):
        dot_lines.append('  subgraph cluster_proxies {')
        dot_lines.append('    label="Proxy Sub-Devices (Fieldbus: BACnet / Modbus)";')
        dot_lines.append('    style="dashed,rounded"; color="#FBBC04"; bgcolor="#FEFDF0";')
        for gw, sub_devs in sorted(gateways.items()):
            for sd in sorted(sub_devs):
                sd_info = device_data.get(sd, {})
                sd_label = f"{sd}\\n({sd_info.get('point_count', 0)} points)"
                if sd_info.get("make_model"):
                    sd_label += f"\\n{sd_info['make_model']}"
                is_focus = (focus_device == sd)
                pen = "3" if is_focus else "1"
                border_color = "#EA4335" if is_focus else "#F29900"
                dot_lines.append(
                    f'    "{sd}" [label="{sd_label}", fillcolor="#FEF7E0", color="{border_color}", penwidth={pen}];'
                )
        dot_lines.append("  }")
        dot_lines.append("")

    # Direct Devices cluster
    standalone = [d for d in direct_devices if d not in gateways]
    if standalone:
        dot_lines.append('  subgraph cluster_direct {')
        dot_lines.append('    label="Direct MQTT Devices";')
        dot_lines.append('    style="dashed,rounded"; color="#1A73E8"; bgcolor="#F8FAFD";')
        for dd in sorted(standalone):
            dd_info = device_data.get(dd, {})
            dd_label = f"{dd}\\n({dd_info.get('point_count', 0)} points)"
            if dd_info.get("make_model"):
                dd_label += f"\\n{dd_info['make_model']}"
            is_focus = (focus_device == dd)
            pen = "3" if is_focus else "1"
            border_color = "#EA4335" if is_focus else "#1A73E8"
            dot_lines.append(
                f'    "{dd}" [label="{dd_label}", fillcolor="#E8F0FE", color="{border_color}", penwidth={pen}];'
            )
        dot_lines.append("  }")
        dot_lines.append("")

    # Edges
    for gw in sorted(gateways.keys()):
        dot_lines.append(f'  broker -> "{gw}" [label="MQTT", color="#1A73E8", penwidth=1.5];')
        for sd in sorted(gateways[gw]):
            dot_lines.append(f'  "{gw}" -> "{sd}" [label="Fieldbus", style=bold, color="#1E8E3E"];')

    for dd in sorted(standalone):
        dot_lines.append(f'  broker -> "{dd}" [label="MQTT", color="#1A73E8", penwidth=1.5];')

    dot_lines.append("}")
    dot_content = "\n".join(dot_lines)

    # 2. Build Mermaid
    mm_lines = [
        "graph LR",
        '  subgraph Transport["Transport / Broker Layer"]',
        '    Broker["MQTT Broker (//mqtt/localhost)"]',
        "  end",
    ]

    if gateways:
        mm_lines.append('  subgraph Gateways["Gateways"]')
        for gw in sorted(gateways.keys()):
            gw_info = device_data.get(gw, {})
            model_str = f" ({gw_info.get('make_model')})" if gw_info.get("make_model") else ""
            mm_lines.append(f'    GW_{gw}["Gateway: {gw}{model_str}"]')
        mm_lines.append("  end")

    if any(gateways.values()):
        mm_lines.append('  subgraph SubDevices["Proxy Sub-Devices (BACnet / Modbus)"]')
        for gw, sub_devs in sorted(gateways.items()):
            for sd in sorted(sub_devs):
                sd_info = device_data.get(sd, {})
                mm_lines.append(f'    DEV_{sd}["{sd} ({sd_info.get("point_count", 0)} pts)"]')
        mm_lines.append("  end")

    if standalone:
        mm_lines.append('  subgraph DirectDevices["Direct MQTT Devices"]')
        for dd in sorted(standalone):
            dd_info = device_data.get(dd, {})
            mm_lines.append(f'    DIR_{dd}["{dd} ({dd_info.get("point_count", 0)} pts)"]')
        mm_lines.append("  end")

    for gw in sorted(gateways.keys()):
        mm_lines.append(f"  Broker -->|MQTT| GW_{gw}")
        for sd in sorted(gateways[gw]):
            mm_lines.append(f"  GW_{gw} ==>|Fieldbus| DEV_{sd}")

    for dd in sorted(standalone):
        mm_lines.append(f"  Broker -->|MQTT| DIR_{dd}")

    mermaid_content = "\n".join(mm_lines)

    rendered_parts = []
    if format in ("dot", "both"):
        rendered_parts.append(f"```dot\n{dot_content}\n```")
    if format in ("mermaid", "both"):
        rendered_parts.append(f"```mermaid\n{mermaid_content}\n```")

    return {
        "status": "SUCCESS",
        "site_model": site_model,
        "device_count": len(all_devices),
        "gateway_count": len(gateways),
        "direct_count": len(standalone),
        "dot": dot_content,
        "mermaid": mermaid_content,
        "rendered": "\n\n".join(rendered_parts),
    }


def generate_sequence_diagram(
    run_dir: str,
    title: Optional[str] = None,
    format: str = "both",
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Generates sequence diagrams in Graphviz DOT and Mermaid from test execution timeline logs.
    
    Args:
        run_dir: Path to the test execution directory.
        title: Optional diagram title.
        format: Diagram format to return ('dot', 'mermaid', or 'both').
        udmi_root: Optional override for the UDMI repository root directory.
    """
    root = _get_udmi_root(udmi_root)
    target_dir = run_dir if os.path.isabs(run_dir) else os.path.join(root, run_dir)
    target_dir = os.path.abspath(target_dir)

    if not os.path.isdir(target_dir):
        return {
            "status": "ERROR",
            "error": f"Run directory not found: {run_dir}",
        }

    # Extract timeline
    test_name = os.path.basename(target_dir)
    timeline = extract_timeline(test_id=test_name, device_id="DUT", run_dir=target_dir, udmi_root=root)
    events = timeline.get("events", [])

    diag_title = title or f"UDMI Sequence Execution: {test_name}"

    # 1. Build Mermaid sequenceDiagram
    mm_lines = [
        "sequenceDiagram",
        "  autonumber",
        "  actor S as Sequencer",
        "  participant B as MQTT Broker",
        "  participant D as Device (DUT)",
        f"  Note over S,D: {diag_title}",
    ]

    has_gateway = any("gateway" in e.get("description", "").lower() for e in events)
    if has_gateway:
        mm_lines.insert(4, "  participant G as Gateway Proxy")

    for e in events:
        cp = e.get("checkpoint", "")
        desc = e.get("description", "")
        ts = e.get("timestamp", "")
        ts_str = f" [{ts}]" if ts else ""

        if cp == "TEST_START":
            mm_lines.append(f"  Note over S,D: Starting Test{ts_str}")
        elif cp == "CONFIG_DISPATCH":
            m_rc = re.search(r"RC:([a-zA-Z0-9_\-\.]+)", desc)
            rc_info = f" ({m_rc.group(0)})" if m_rc else ""
            mm_lines.append(f"  S->>B: Dispatched Config{rc_info}{ts_str}")
            if has_gateway:
                mm_lines.append(f"  B->>G: Routes Config Packet")
                mm_lines.append(f"  G->>D: Fieldbus Forwarding")
            else:
                mm_lines.append(f"  B->>D: Delivered Config Packet")
        elif cp == "STAGE_WAIT_START":
            mm_lines.append(f"  Note over S: Stage Wait: {desc[:40]}{ts_str}")
        elif cp == "STATE_CUTOFF_SET":
            cutoff = e.get("cutoff", "threshold")
            mm_lines.append(f"  Note over S: Cutoff set to {cutoff}")
        elif cp == "STATE_RECEIVED":
            if has_gateway:
                mm_lines.append(f"  D-->>G: Fieldbus State Telemetry{ts_str}")
                mm_lines.append(f"  G-->>B: Aggregated State Update")
                mm_lines.append(f"  B-->>S: State Packet Delivered")
            else:
                mm_lines.append(f"  D-->>B: State Update Published{ts_str}")
                mm_lines.append(f"  B-->>S: Delivered State Update")
        elif cp == "STALE_STATE_IGNORED":
            mm_lines.append(f"  Note over S: Stale state ignored (< cutoff){ts_str}")
        elif cp == "TIMEOUT_FAILURE":
            mm_lines.append(f"  Note over S: Stage Timeout Failure (120s expired){ts_str}")
        elif cp == "JACKSON_DESERIALIZATION_FAILURE":
            mm_lines.append(f"  Note over S: Jackson Deserialization Error{ts_str}")
        elif cp == "GATEWAY_BUS_ERROR":
            mm_lines.append(f"  Note over G,D: Fieldbus Framing / CRC Error{ts_str}")
        elif cp == "TEST_RESULT":
            res = e.get("status") or e.get("result") or "UNKNOWN"
            mm_lines.append(f"  Note over S,D: Final Result: {res}{ts_str}")

    mermaid_content = "\n".join(mm_lines)

    # 2. Build Graphviz DOT Sequence / State Flow
    dot_lines = [
        "digraph SequenceFlow {",
        "  rankdir=TB;",
        '  graph [fontname="Helvetica", fontsize=11, bgcolor="#FAFAFA", style="rounded", color="#CCCCCC"];',
        '  node [fontname="Helvetica", fontsize=10, shape=box, style="rounded,filled", fillcolor="#FFFFFF", color="#4285F4"];',
        '  edge [fontname="Helvetica", fontsize=9, color="#5F6368"];',
        f'  label="{diag_title}";',
        "",
        '  start [label="Test Start", shape=ellipse, fillcolor="#E8F0FE", color="#1A73E8"];',
    ]

    prev_node = "start"
    for idx, e in enumerate(events, 1):
        node_name = f"step_{idx}"
        cp = e.get("checkpoint", "EVENT")
        desc = e.get("description", "")
        ts = e.get("timestamp", "")
        ts_label = f"\\n[{ts}]" if ts else ""

        fillcolor = "#FFFFFF"
        color = "#5F6368"
        shape = "box"

        if "DISPATCH" in cp:
            fillcolor = "#E8F0FE"
            color = "#1A73E8"
        elif "WAIT" in cp or "CUTOFF" in cp:
            fillcolor = "#FEF7E0"
            color = "#F29900"
        elif "RECEIVED" in cp or "PASS" in cp:
            fillcolor = "#E6F4EA"
            color = "#1E8E3E"
        elif "STALE" in cp or "TIMEOUT" in cp or "ERROR" in cp or "FAIL" in cp:
            fillcolor = "#FCE8E6"
            color = "#D93025"

        clean_desc = desc.replace('"', '\\"')[:80]
        dot_lines.append(
            f'  {node_name} [label="{cp}\\n{clean_desc}{ts_label}", fillcolor="{fillcolor}", color="{color}", shape="{shape}"];'
        )
        dot_lines.append(f"  {prev_node} -> {node_name};")
        prev_node = node_name

    dot_lines.append("}")
    dot_content = "\n".join(dot_lines)

    rendered_parts = []
    if format in ("dot", "both"):
        rendered_parts.append(f"```dot\n{dot_content}\n```")
    if format in ("mermaid", "both"):
        rendered_parts.append(f"```mermaid\n{mermaid_content}\n```")

    return {
        "status": "SUCCESS",
        "run_dir": run_dir,
        "event_count": len(events),
        "dot": dot_content,
        "mermaid": mermaid_content,
        "rendered": "\n\n".join(rendered_parts),
    }
