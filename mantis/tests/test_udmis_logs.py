"""Unit tests for mantis.tools.udmis_logs evidence-tier resolution."""

import os
from datetime import datetime, timedelta, timezone

import pytest

from mantis.tools import udmis_logs
from mantis.tools.udmis_logs import (
    TIER_CLOUD,
    TIER_INDETERMINATE,
    TIER_LOCAL_FILE,
    TIER_UNAVAILABLE,
    get_udmis_runtime_logs,
)


@pytest.fixture(autouse=True)
def _clear_log_env(monkeypatch):
    """The tool honours UDMIS_LOG; tests must not inherit a developer's value."""
    monkeypatch.delenv("UDMIS_LOG", raising=False)


@pytest.fixture(autouse=True)
def _forbid_real_cloud_queries(monkeypatch):
    """No unit test may reach Cloud Logging.

    This was previously enforced by accident: google-cloud-logging was not
    installed, so any test falling through to the CLOUD tier got an instant
    ImportError instead of a network call. Installing the dependency turned
    test_local_log_is_not_evidence_for_a_cloud_incident into a real
    authenticated request that hung the suite. A test that exercises the cloud
    tier must patch this itself; reaching the real client now fails loudly.
    """

    def _refuse(**kwargs):
        raise AssertionError(
            "This test reached the real query_cloud_logs and would hit the "
            "network. Patch 'mantis.tools.cloud_logs.query_cloud_logs' in the test."
        )

    monkeypatch.setattr("mantis.tools.cloud_logs.query_cloud_logs", _refuse)


def _write_local_log(root, text):
    out_dir = root / "out"
    out_dir.mkdir(parents=True, exist_ok=True)
    log = out_dir / "udmis.log"
    log.write_text(text)
    return log


def test_local_log_is_the_first_tier(tmp_path):
    _write_local_log(
        tmp_path,
        "starting pod\nSimpleMqttPipe queue saturation 1.000\nheartbeat dropped\n",
    )

    res = get_udmis_runtime_logs(pattern="saturation", udmi_root=str(tmp_path))

    assert res["status"] == "SUCCESS"
    assert res["tier"] == TIER_LOCAL_FILE
    assert res["total_matching_lines"] == 1
    assert "1.000" in res["lines"][0]["text"]
    assert res["required_declaration"] == "RUNTIME_EVIDENCE: LOCAL_FILE"


def test_available_log_with_no_match_is_labelled_as_such(tmp_path):
    """Absence of a pattern in an available log is not absence of evidence."""
    _write_local_log(tmp_path, "pod started cleanly\n")

    res = get_udmis_runtime_logs(pattern="saturation", udmi_root=str(tmp_path))

    assert res["tier"] == TIER_LOCAL_FILE
    assert res["total_matching_lines"] == 0
    assert "evidence of absence" in res["note"]


def test_local_log_predating_the_window_is_rejected(tmp_path):
    """A log written before the incident cannot contain it."""
    log = _write_local_log(tmp_path, "old run\n")
    stale = (datetime.now(timezone.utc) - timedelta(days=10)).timestamp()
    os.utime(log, (stale, stale))
    window_start = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()

    res = get_udmis_runtime_logs(
        pattern="saturation", window_start=window_start, udmi_root=str(tmp_path)
    )

    assert res["tier"] == TIER_UNAVAILABLE
    assert any("before the requested window start" in r for r in res["tier_resolution"])


def test_unavailable_reports_every_rejected_tier(tmp_path):
    res = get_udmis_runtime_logs(pattern="saturation", udmi_root=str(tmp_path))

    assert res["status"] == TIER_UNAVAILABLE
    assert res["tier"] == TIER_UNAVAILABLE
    assert len(res["tier_resolution"]) == 2
    assert any("no UDMIS log file" in r for r in res["tier_resolution"])
    assert any("no project_spec" in r for r in res["tier_resolution"])
    assert res["required_declaration"] == "RUNTIME_EVIDENCE: NONE (source inference only)"
    assert "source inference only" in res["guidance"]


def test_local_project_spec_cannot_reach_the_cloud_tier(tmp_path):
    res = get_udmis_runtime_logs(
        pattern="saturation",
        project_spec="//mqtt/localhost:18833",
        udmi_root=str(tmp_path),
    )

    assert res["tier"] == TIER_UNAVAILABLE
    assert any("resolves to a local target" in r for r in res["tier_resolution"])


def test_cloud_tier_refuses_to_guess_a_filter(tmp_path):
    """No default resource filter is invented for an unknown deployment shape."""
    res = get_udmis_runtime_logs(
        project_spec="//gbos/bos-platform-dev", udmi_root=str(tmp_path)
    )

    assert res["status"] == "ERROR"
    assert "pattern" in res["error"] and "log_filter" in res["error"]


def test_cloud_tier_returns_entries(tmp_path, monkeypatch):
    captured = {}

    def fake_query(project_id, filter_query, start_time=None, end_time=None, limit=100):
        captured.update(
            project_id=project_id, filter_query=filter_query, start_time=start_time
        )
        return {
            "status": "SUCCESS",
            "filter": filter_query,
            "count": 1,
            "entries": [{"timestamp": "2026-09-14T18:00:00Z", "payload": "saturation"}],
        }

    monkeypatch.setattr("mantis.tools.cloud_logs.query_cloud_logs", fake_query)

    res = get_udmis_runtime_logs(
        pattern="saturation",
        window_start="2026-09-14T17:00:00Z",
        project_spec="//gbos/bos-platform-dev",
        udmi_root=str(tmp_path),
    )

    assert res["tier"] == TIER_CLOUD
    assert res["required_declaration"] == "RUNTIME_EVIDENCE: CLOUD"
    assert captured["project_id"] == "bos-platform-dev"
    assert captured["filter_query"] == '"saturation"'


def test_empty_cloud_result_past_retention_says_so(tmp_path, monkeypatch):
    monkeypatch.setattr(
        "mantis.tools.cloud_logs.query_cloud_logs",
        lambda **kwargs: {"status": "SUCCESS", "count": 0, "entries": []},
    )
    stale_window = (datetime.now(timezone.utc) - timedelta(days=90)).isoformat()

    res = get_udmis_runtime_logs(
        pattern="saturation",
        window_start=stale_window,
        project_spec="//gbos/bos-platform-dev",
        udmi_root=str(tmp_path),
    )

    assert res["tier"] == TIER_UNAVAILABLE
    assert any("default Cloud Logging retention" in r for r in res["tier_resolution"])


def test_cloud_query_failure_is_indeterminate_not_unavailable(tmp_path, monkeypatch):
    """Trial I regression.

    This test previously asserted TIER_UNAVAILABLE, which encoded the defect: a
    query that never ran was reported as proof that no runtime evidence exists.
    In Trial I the cloud client was missing from the environment, the tool
    answered UNAVAILABLE, and the Actor wrote "the proving UDMIS runtime logs
    have outlived retention" -- a claim about the world produced entirely by a
    missing import. The expectation is corrected, not the implementation.
    """
    monkeypatch.setattr(
        "mantis.tools.cloud_logs.query_cloud_logs",
        lambda **kwargs: {"status": "ERROR", "error": "permission denied", "entries": []},
    )

    res = get_udmis_runtime_logs(
        pattern="saturation",
        project_spec="//gbos/bos-platform-dev",
        udmi_root=str(tmp_path),
    )

    assert res["tier"] == TIER_INDETERMINATE
    assert res["tier"] != TIER_UNAVAILABLE
    assert any("permission denied" in r for r in res["tier_resolution"])


def test_indeterminate_result_forbids_claiming_the_logs_expired(tmp_path, monkeypatch):
    """The guidance must actively block the inference the Actor drew in Trial I."""
    monkeypatch.setattr(
        "mantis.tools.cloud_logs.query_cloud_logs",
        lambda **kwargs: {
            "status": "ERROR",
            "error": "cannot import name 'logging_v2' from 'google.cloud'",
            "entries": [],
        },
    )

    res = get_udmis_runtime_logs(
        pattern="saturation",
        project_spec="//gbos/bos-platform-dev",
        udmi_root=str(tmp_path),
    )

    guidance = res["guidance"].lower()
    assert "failed to execute" in guidance
    assert "retention" in guidance
    assert "do not report this as the logs being absent" in guidance
    # It must still tell the caller how to proceed honestly.
    assert res["required_declaration"] == "RUNTIME_EVIDENCE: NONE (source inference only)"


def test_unparseable_window_fails_immediately(tmp_path):
    res = get_udmis_runtime_logs(window_start="last tuesday", udmi_root=str(tmp_path))

    assert res["status"] == "ERROR"
    assert "ISO-8601" in res["error"]


def test_inverted_window_fails_immediately(tmp_path):
    res = get_udmis_runtime_logs(
        window_start="2026-09-14T18:00:00Z",
        window_end="2026-09-14T17:00:00Z",
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "ERROR"
    assert "precedes" in res["error"]


def test_udmis_log_env_override_is_honoured(tmp_path, monkeypatch):
    custom = tmp_path / "custom" / "pod.log"
    custom.parent.mkdir(parents=True)
    custom.write_text("queue saturation observed\n")
    monkeypatch.setenv("UDMIS_LOG", str(custom))

    res = get_udmis_runtime_logs(pattern="saturation", udmi_root=str(tmp_path))

    assert res["tier"] == TIER_LOCAL_FILE
    assert res["total_matching_lines"] == 1


def test_local_log_is_not_evidence_for_a_cloud_incident(tmp_path, monkeypatch):
    """Trial G regression: a localhost run's log was offered for a cloud incident.

    out/udmis.log existed from an unrelated `//mqtt/localhost:18833` run, so the
    tool returned SUCCESS/LOCAL_FILE for a `//gbos/bos-platform-staging` incident
    and the Actor cited it to refute a backend hypothesis.

    The cloud query is stubbed because this test is about the LOCAL_FILE
    rejection; letting it fall through to a real query is what hung the suite.
    """
    _write_local_log(tmp_path, "Launching LOCAL setup pipeline (//mqtt/localhost:18833)...\n")
    monkeypatch.setattr(
        "mantis.tools.cloud_logs.query_cloud_logs",
        lambda **kwargs: {"status": "SUCCESS", "count": 0, "entries": []},
    )

    res = get_udmis_runtime_logs(
        pattern="saturation",
        project_spec="//gbos/bos-platform-staging",
        udmi_root=str(tmp_path),
    )

    assert res["tier"] != TIER_LOCAL_FILE
    assert any(
        "targets cloud project 'bos-platform-staging'" in r for r in res["tier_resolution"]
    )


def test_local_log_is_not_evidence_for_gcp_cloud_incident(tmp_path, monkeypatch):
    """Ensure //gcp/... provider is recognized as cloud and rejects local logs."""
    _write_local_log(tmp_path, "Launching LOCAL setup pipeline...\n")
    monkeypatch.setattr(
        "mantis.tools.cloud_logs.query_cloud_logs",
        lambda **kwargs: {"status": "SUCCESS", "count": 0, "entries": []},
    )

    res = get_udmis_runtime_logs(
        pattern="saturation",
        project_spec="//gcp/my-prod-project/my-registry",
        udmi_root=str(tmp_path),
    )

    assert res["tier"] != TIER_LOCAL_FILE
    assert any(
        "targets cloud project 'my-prod-project'" in r for r in res["tier_resolution"]
    )



def test_local_log_still_serves_a_local_incident(tmp_path):
    _write_local_log(tmp_path, "queue saturation observed\n")

    res = get_udmis_runtime_logs(
        pattern="saturation",
        project_spec="//mqtt/localhost:18833",
        udmi_root=str(tmp_path),
    )

    assert res["tier"] == TIER_LOCAL_FILE
    assert res["total_matching_lines"] == 1
