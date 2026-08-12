import json
import os
from typing import Any, Dict, List, Optional


def _resolve_site_dir(udmi_root: str, site_name: str) -> Optional[str]:
    """Resolves the site model root directory under sites/."""
    clean_site = site_name.replace("sites/", "").strip("/")
    base_sites = os.path.join(os.path.abspath(udmi_root), "sites")
    
    candidate = os.path.join(base_sites, clean_site)
    if os.path.exists(candidate):
        return candidate
    
    # Try with or without udmi suffix
    if os.path.exists(os.path.join(base_sites, clean_site, "udmi")):
        return os.path.join(base_sites, clean_site, "udmi")
    return None


def inspect_site_model(
    udmi_root: str,
    site_name: str,
    device_id: Optional[str] = None
) -> str:
    """
    Inspects site configuration (cloud_iot_config.json) or device metadata (metadata.json)
    for a specified site model and device.
    """
    site_dir = _resolve_site_dir(udmi_root, site_name)
    if not site_dir:
        return f"Error: Site model '{site_name}' not found under 'sites/'."

    output = [f"### Site Model Inspection: `{site_name}`"]

    # 1. Read cloud_iot_config.json if present
    for cfg_name in ("cloud_iot_config.json", "cloud_iot_config.json.old"):
        cfg_path = os.path.join(site_dir, cfg_name)
        if not os.path.exists(cfg_path) and os.path.exists(os.path.join(site_dir, "udmi", cfg_name)):
            cfg_path = os.path.join(site_dir, "udmi", cfg_name)

        if os.path.exists(cfg_path):
            try:
                with open(cfg_path, "r", encoding="utf-8") as f:
                    cfg_data = json.load(f)
                output.append(f"#### Cloud IoT Config (`{cfg_name}`)")
                output.append(f"```json\n{json.dumps(cfg_data, indent=2)}\n```")
                break
            except Exception as e:
                output.append(f"*(Error reading {cfg_name}: {e})*")

    # 2. If device_id is specified, read device metadata.json
    if device_id:
        dev_dirs = [
            os.path.join(site_dir, "devices", device_id),
            os.path.join(site_dir, "udmi", "devices", device_id)
        ]
        meta_found = False
        for d_path in dev_dirs:
            meta_path = os.path.join(d_path, "metadata.json")
            if os.path.exists(meta_path):
                meta_found = True
                output.append(f"\n#### Device Metadata (`{device_id}/metadata.json`)")
                try:
                    with open(meta_path, "r", encoding="utf-8") as mf:
                        raw_content = mf.read()
                    meta_data = json.loads(raw_content)
                    output.append(f"```json\n{json.dumps(meta_data, indent=2)}\n```")
                    
                    # Highlight testing configuration if present
                    testing_cfg = meta_data.get("testing", {})
                    if testing_cfg:
                        output.append(f"\n> ⚙️ **Testing Configuration**: `{json.dumps(testing_cfg)}`")
                        if testing_cfg.get("nostate"):
                            output.append("> ℹ️ `nostate: true` is configured: State checks should be bypassed for this device.")
                except json.JSONDecodeError as jde:
                    output.append(f"\n🚨 **CRITICAL SYNTAX ERROR in `{device_id}/metadata.json`**:")
                    output.append(f"- **Error**: `{jde.msg}` at line {jde.lineno}, column {jde.colno}")
                    output.append("- **Impact**: Broken JSON prevents the Java Sequencer (Jackson parser) from reading `testing: { nostate: true }` or device configs, causing the sequencer to fall back to full state synchronization and fail setup!")
                except Exception as e:
                    output.append(f"*(Error reading metadata.json for {device_id}: {e})*")
                break

        if not meta_found:
            output.append(f"\n*(No metadata.json found for device '{device_id}' in site '{site_name}')*")


    return "\n".join(output)


def list_site_devices(udmi_root: str, site_name: str) -> str:
    """
    Lists all devices configured under a site model along with summary info
    (e.g., number of points, system description).
    """
    site_dir = _resolve_site_dir(udmi_root, site_name)
    if not site_dir:
        return f"Error: Site model '{site_name}' not found under 'sites/'."

    dev_root = os.path.join(site_dir, "devices")
    if not os.path.exists(dev_root):
        dev_root = os.path.join(site_dir, "udmi", "devices")

    if not os.path.exists(dev_root):
        return f"Error: No devices directory found for site '{site_name}'."

    devices = sorted([d for d in os.listdir(dev_root) if os.path.isdir(os.path.join(dev_root, d))])
    lines = [
        f"### Devices in Site `{site_name}` ({len(devices)} total)",
        "| Device ID | Points Count | Gateway / Proxy | Metadata Status |",
        "| :--- | :--- | :--- | :--- |"
    ]

    for dev in devices:
        meta_p = os.path.join(dev_root, dev, "metadata.json")
        pts_count = 0
        gateway = "-"
        status = "Missing"
        if os.path.exists(meta_p):
            status = "Present"
            try:
                with open(meta_p, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    pts = data.get("pointset", {}).get("points", {})
                    pts_count = len(pts)
                    gw = data.get("gateway", {}).get("gateway_id")
                    if gw:
                        gateway = gw
            except Exception:
                status = "Parse Error"
        lines.append(f"| `{dev}` | {pts_count} points | `{gateway}` | {status} |")

    return "\n".join(lines)
