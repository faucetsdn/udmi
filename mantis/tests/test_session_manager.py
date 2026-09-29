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
    assert port1 % 10 == 0
    assert port2 % 10 == 0


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

