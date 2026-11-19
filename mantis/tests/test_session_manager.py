"""Unit tests for mcp.session_manager."""

import os
import pytest
from mantis.session import SessionManager


def test_derive_port_block():
    mgr = SessionManager()
    port1 = mgr.derive_port_block("test_run_1")
    port2 = mgr.derive_port_block("test_run_2")
    assert port1 >= 20000
    assert port2 >= 20000
    assert port1 + 1001 < 32768
    assert port2 + 1001 < 32768
    assert port1 % 10 == 0
    assert port2 % 10 == 0


def test_is_port_block_available_checks_bridge_mqtt_port(monkeypatch):
    mgr = SessionManager()
    monkeypatch.setattr(mgr, "is_port_available", lambda p: p != 25100)
    assert mgr.is_port_block_available(25000) is False
    monkeypatch.setattr(mgr, "is_port_available", lambda p: True)
    assert mgr.is_port_block_available(25000) is True


def test_sanitize_session_name():
    mgr = SessionManager()
    assert mgr.sanitize_session_name("dev-1") == "udmi_dev-1"
    assert mgr.sanitize_session_name("udmi_test") == "udmi_test"
    assert mgr.sanitize_session_name("my test @#$") == "udmi_my_test____"


def test_ensure_test_setup_rejects_project_spec_as_site_model():
    mgr = SessionManager()
    with pytest.raises(ValueError, match="Site model directory not found"):
        mgr.ensure_test_setup(test_id="test_cloud", site_model="//gbos/bos-platform-dev/faucetsdn")


def test_persisted_session_info(tmp_path, monkeypatch):
    import json
    mgr = SessionManager(udmi_root=str(tmp_path))

    # Mock subprocess to avoid real tmux calls
    from unittest.mock import MagicMock
    mock_run = MagicMock()
    mock_run.return_value.return_value = 0
    monkeypatch.setattr("subprocess.run", mock_run)
    monkeypatch.setattr("mcp.infra.session_manager.subprocess.run", mock_run)
    monkeypatch.setattr(mgr, "_wait_for_readiness", lambda **kwargs: True)
    monkeypatch.setattr(mgr, "list_test_windows", lambda test_id: ["main"])

    # Create dummy site model
    site_dir = tmp_path / "sites" / "test_model"
    site_dir.mkdir(parents=True)
    (site_dir / "cloud_iot_config.json").write_text('{"project_id": "localhost"}')

    res = mgr.ensure_test_setup(
        test_id="test_session_persist",
        site_model=str(site_dir),
    )
    assert res["status"] == "READY"

    # Check on-disk json
    info_path = tmp_path / "var" / "instances" / "udmi_test_session_persist" / "session_info.json"
    assert info_path.is_file()
    saved = json.loads(info_path.read_text(encoding="utf-8"))
    assert saved["credentials"]["username"] == "rocket"
    assert "password" in saved["credentials"]


def test_ensure_passes_no_build_and_terminate_stops_udmi(tmp_path, monkeypatch):
    from unittest.mock import MagicMock
    mgr = SessionManager(udmi_root=str(tmp_path))

    udmi_bin = tmp_path / "bin" / "udmi"
    udmi_bin.parent.mkdir(parents=True)
    udmi_bin.write_text("#!/bin/bash\nexit 0\n")
    udmi_bin.chmod(0o755)

    site_dir = tmp_path / "sites" / "test_model"
    site_dir.mkdir(parents=True)
    (site_dir / "cloud_iot_config.json").write_text('{"project_id": "localhost"}')

    calls = []

    def fake_run(cmd, **kwargs):
        calls.append((cmd, kwargs))
        res = MagicMock()
        res.returncode = 0
        res.stdout = ""
        return res

    monkeypatch.setenv("UDMI_NO_BUILD", "1")
    monkeypatch.setattr("mcp.infra.session_manager.subprocess.run", fake_run)
    monkeypatch.setattr(mgr, "_wait_for_readiness", lambda **kwargs: True)
    monkeypatch.setattr(mgr, "list_test_windows", lambda test_id: ["main"])

    res = mgr.ensure_test_setup(test_id="test_nb", site_model=str(site_dir))
    tmux_new = [c for c, _ in calls if len(c) >= 2 and c[0] == "tmux" and c[1] == "new-session"]
    assert tmux_new, "Expected tmux new-session call"
    assert "bin/udmi start -n block" in tmux_new[-1][-1]

    calls.clear()
    mgr.terminate_test_setup("test_nb", clean_workspace=True)
    stop_calls = [(c, kw) for c, kw in calls if len(c) >= 2 and c[0] == str(udmi_bin) and c[1] == "stop"]
    assert len(stop_calls) == 1
    assert stop_calls[0][0][2] == res["project_spec"]
    assert stop_calls[0][1]["env"]["UDMI_RUN_DIR"] == res["run_dir"]


