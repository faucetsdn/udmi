"""Unit tests for mantis.tools.sequencer."""

from unittest.mock import MagicMock
import pytest
from mantis.session import SessionManager
from mantis.tools.sequencer import run_sequencer_test


def test_run_sequencer_test_cloud_endpoint():
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="pointset_publish",
        device_id="AHU-1",
        target_spec="//gbos/bos-platform-dev/faucetsdn",
        site_model="sites/udmi_site_model",
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is True
    assert res["target_spec"] == "//gbos/bos-platform-dev/faucetsdn"
    assert "pointset_publish" in res["command"]
    assert "sites/udmi_site_model" in res["command"]
    mgr.start_session_process.assert_called_once()


def test_run_sequencer_test_auto_swaps_uri_in_site_model():
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    # User or LLM accidentally passes //gbos/... as site_model
    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="pointset_publish",
        device_id="AHU-1",
        site_model="//gbos/bos-platform-dev/faucetsdn",
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is True
    assert res["target_spec"] == "//gbos/bos-platform-dev/faucetsdn"
    assert "sites/udmi_site_model" in res["site_model"]


def test_run_sequencer_test_gref_with_plus_suffix():
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="pointset_publish",
        device_id="AHU-1",
        target_spec="//gref/bos-platform-staging+dev_user",
        site_model="sites/udmi_site_model",
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is True
    assert res["target_spec"] == "//gref/bos-platform-staging+dev_user"
    assert "//gref/bos-platform-staging+dev_user" in res["command"]
    mgr.start_session_process.assert_called_once()


def test_run_sequencer_test_pubsub_endpoint():
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="system_last_update",
        device_id="AHU-1",
        target_spec="//pubsub/bos-platform-dev/faucetsdn+debug",
        site_model="sites/udmi_site_model",
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is True
    assert res["target_spec"] == "//pubsub/bos-platform-dev/faucetsdn+debug"
    assert "//pubsub/bos-platform-dev/faucetsdn+debug" in res["command"]


def test_run_sequencer_test_clearblade_endpoint():
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="pointset_publish",
        device_id="AHU-1",
        target_spec="//clearblade/my-cb-proj/my-reg",
        site_model="sites/udmi_site_model",
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is True
    assert res["target_spec"] == "//clearblade/my-cb-proj/my-reg"


def test_run_sequencer_test_url_normalization():
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})
    mgr.ensure_test_setup = MagicMock(return_value={"project_spec": "//mqtt/localhost:18833"})

    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="pointset_publish",
        device_id="AHU-1",
        target_spec="mqtt://localhost:18833",
        site_model="sites/udmi_site_model",
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is False
    assert res["target_spec"] == "//mqtt/localhost:18833"


def test_run_sequencer_test_resolves_target_project_env(monkeypatch):
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    monkeypatch.setenv("TARGET_PROJECT", "//gbos/bos-platform-prod/faucetsdn")
    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="pointset_publish",
        device_id="AHU-1",
        site_model="sites/udmi_site_model",
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is True
    assert res["target_spec"] == "//gbos/bos-platform-prod/faucetsdn"


def test_run_sequencer_test_resolves_cloud_iot_config(tmp_path):
    import json
    mgr = SessionManager()
    mgr.is_session_active = MagicMock(return_value=True)
    mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    cloud_site = tmp_path / "custom_cloud_site"
    cloud_site.mkdir()
    (cloud_site / "devices").mkdir()
    cfg_file = cloud_site / "cloud_iot_config.json"
    cfg_file.write_text(json.dumps({
        "iot_provider": "gbos",
        "project_id": "bos-platform-staging",
        "udmi_namespace": "faucetsdn",
    }), encoding="utf-8")

    res = run_sequencer_test(
        session_mgr=mgr,
        test_name="pointset_publish",
        device_id="AHU-1",
        site_model=str(cloud_site),
    )
    assert res["status"] == "LAUNCHED"
    assert res["is_cloud"] is True
    assert res["target_spec"] == "//gbos/bos-platform-staging/faucetsdn"
