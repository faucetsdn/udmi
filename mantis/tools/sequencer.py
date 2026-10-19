"""Sequencer test execution tool for Mantis."""

import json
import os
import re
import subprocess
from typing import Any, Dict, Optional

from mantis.session import SessionManager
from mantis.tools.process import start_session_process as launch_process
from mantis.project_spec import is_cloud_spec, normalize_project_spec, resolve_target_spec


def run_sequencer_test(
    session_mgr: SessionManager,
    test_name: str,
    device_id: str,
    target_spec: Optional[str] = None,
    site_model: str = "sites/udmi_site_model",
    session_id: Optional[str] = None,
) -> Dict[str, Any]:
    """Launch a sequencer test against a local or cloud endpoint."""
    # Handle accidental swap where user or model passed //... or protocol URI as site_model
    if site_model.startswith(("//", "mqtt://", "mqtts://", "ssl://")) and not target_spec:
        target_spec = site_model
        site_model = "sites/udmi_site_model"

    site_model_path = os.path.abspath(os.path.join(session_mgr.udmi_root, site_model))
    if not os.path.isdir(site_model_path):
        site_model_path = os.path.abspath(site_model)
    if not os.path.isdir(site_model_path):
        raise ValueError(f"Site model directory not found: {site_model}")

    # Determine active session info if available
    active_info = None
    if session_id:
        active_info = session_mgr.get_session_info(session_id)
    if not active_info:
        active_setups = session_mgr.list_test_setups()
        if active_setups:
            active_info = session_mgr.get_session_info(active_setups[0].get("test_id", ""))

    # Resolve target project spec using complete UDMI hierarchy
    resolved_target = resolve_target_spec(
        target_spec=target_spec,
        site_model=site_model_path,
        session_info=active_info,
    )
    is_cloud = is_cloud_spec(resolved_target)

    # Determine session name and ensure local setup if needed
    if is_cloud:
        sess_name = session_mgr.sanitize_session_name(session_id or f"cloud_{device_id}_{test_name}")
    else:
        active_sess_id = (active_info.get("test_id") or active_info.get("session_name", "").replace("udmi_", "")) if active_info else None
        if active_sess_id and session_mgr.is_session_active(session_mgr.sanitize_session_name(active_sess_id)):
            sess_name = session_mgr.sanitize_session_name(active_sess_id)
            if not target_spec:
                resolved_target = active_info.get("project_spec") or resolved_target
        else:
            sess_name = session_mgr.sanitize_session_name(session_id or f"test_{device_id}_{test_name}")
            setup_info = session_mgr.ensure_test_setup(
                test_id=sess_name,
                site_model=site_model,
                dut_device_id=device_id,
                project_spec=resolved_target if resolved_target != "//mqtt/localhost" else None,
            )
            resolved_target = setup_info["project_spec"]

    # Ensure session exists (for cloud or background execution)
    run_dir = os.path.join(session_mgr.instances_dir, sess_name)
    os.makedirs(os.path.join(run_dir, "out"), exist_ok=True)
    os.makedirs(os.path.join(run_dir, "var"), exist_ok=True)

    if not session_mgr.is_session_active(sess_name):
        subprocess.run(
            ["tmux", "new-session", "-d", "-s", sess_name, "-n", "sequencer"],
            check=True,
        )
        subprocess.run(
            ["tmux", "set-option", "-t", sess_name, "remain-on-exit", "on"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

    cmd = f"bin/sequencer '{site_model_path}' '{resolved_target}' '{device_id}' '{test_name}'"
    res = launch_process(session_mgr=session_mgr, test_id=sess_name, window="sequencer", command=cmd)

    return {
        "status": "LAUNCHED",
        "test_name": test_name,
        "device_id": device_id,
        "site_model": site_model,
        "target_spec": resolved_target,
        "session_id": sess_name,
        "window": "sequencer",
        "command": cmd,
        "is_cloud": is_cloud,
        "message": (
            f"Launched sequencer test '{test_name}' for device '{device_id}' against '{resolved_target}' "
            f"in session '{sess_name}' (window: 'sequencer')."
        ),
    }
