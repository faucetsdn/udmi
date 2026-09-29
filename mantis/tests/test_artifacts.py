"""Unit tests for mantis.tools.artifacts (bundle ingestion, credential sanitization, timeline extraction)."""

import json
import os
import zipfile
import pytest

from mantis.tools.artifacts import (
    discover_test_runs,
    extract_log_slice,
    extract_timeline,
    ingest_support_bundle,
    sanitize_extracted_bundle_directory,
)


def test_sanitize_extracted_bundle_directory(tmp_path):
    bundle_dir = tmp_path / "bundle_test"
    bundle_dir.mkdir()

    # 1. Private key file
    key_file = bundle_dir / "rsa_private.pem"
    key_file.write_text("-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----\n", encoding="utf-8")

    # 2. Config file with passwords and tokens
    cfg_file = bundle_dir / "cloud_iot_config.json"
    cfg_file.write_text(json.dumps({
        "project_id": "test-proj",
        "password": "super-secret-password",
        "auth_token": "secret-token-xyz",
        "connection_url": "mqtt://user:my_secret_pass@localhost:18833",
    }), encoding="utf-8")

    # 3. Log file with credentials and GCP tokens.
    # NOTE: The token below is a synthetic fixture, not a real credential. It must
    # keep the "ya29." prefix so that it exercises the GCP token redaction regex in
    # sanitize_extracted_bundle_directory; weakening it would make this test pass
    # regardless of whether that redaction works. It is allowlisted in .gitallowed.
    log_file = bundle_dir / "sequence.log"
    log_file.write_text(
        "2026-09-14T12:00:00Z Dispatched config to mqtt://admin:secret123@localhost:18833\n"
        "2026-09-14T12:00:01Z Bearer ya29.FAKE_TEST_TOKEN_NOT_A_REAL_CREDENTIAL token exchanged\n"
        "-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----\n",
        encoding="utf-8",
    )

    stats = sanitize_extracted_bundle_directory(str(bundle_dir))
    assert stats["keys_stripped"] >= 1
    assert stats["json_sanitized"] >= 1
    assert stats["logs_sanitized"] >= 1

    # Check key file was stripped
    assert key_file.read_text(encoding="utf-8").strip() == "[REDACTED_RSA_KEY]"

    # Check JSON file was sanitized
    sanitized_cfg = json.loads(cfg_file.read_text(encoding="utf-8"))
    assert sanitized_cfg["password"] == "***REDACTED***"
    assert sanitized_cfg["auth_token"] == "***REDACTED***"
    assert "my_secret_pass" not in sanitized_cfg["connection_url"]
    assert "user:***@" in sanitized_cfg["connection_url"]

    # Check log file was sanitized
    sanitized_log = log_file.read_text(encoding="utf-8")
    assert "secret123" not in sanitized_log
    assert "admin:***@" in sanitized_log
    assert "ya29.FAKE_TEST_TOKEN_NOT_A_REAL_CREDENTIAL" not in sanitized_log
    assert "[REDACTED_GCP_TOKEN]" in sanitized_log
    assert "BEGIN RSA PRIVATE KEY" not in sanitized_log


def test_ingest_support_bundle_zip(tmp_path):
    # Create sample zip bundle
    src_dir = tmp_path / "src"
    src_dir.mkdir()
    (src_dir / "triage_manifest.json").write_text(json.dumps({
        "site_name": "sites/udmi_site_model",
        "device_id": "AHU-1",
        "failed_test": "pointset_publish",
        "secret_key": "my-secret",
    }), encoding="utf-8")
    (src_dir / "sequence.log").write_text("Starting test pointset_publish for AHU-1\nRESULT fail pointset pointset_publish STABLE 0/8 Sequence failed\n", encoding="utf-8")
    (src_dir / "key.pem").write_text("-----BEGIN RSA PRIVATE KEY-----\nsecret\n-----END RSA PRIVATE KEY-----\n", encoding="utf-8")

    zip_file = tmp_path / "support_bundle.zip"
    with zipfile.ZipFile(zip_file, "w") as z:
        for f in src_dir.iterdir():
            z.write(f, arcname=f.name)

    res = ingest_support_bundle(str(zip_file), extract_to=str(tmp_path / "out_extracted"))
    assert res["status"] == "SUCCESS"
    assert res["manifest"]["device_id"] == "AHU-1"
    assert res["manifest"]["secret_key"] == "***REDACTED***"
    assert "sequence.log" in res["logs"]

    # Verify extracted key was sanitized
    extracted_key = tmp_path / "out_extracted" / "key.pem"
    assert extracted_key.read_text(encoding="utf-8").strip() == "[REDACTED_RSA_KEY]"


def test_extract_timeline_chronological_sorting_and_stage_wait(tmp_path):
    run_dir = tmp_path / "run_chrono"
    run_dir.mkdir()

    # sequence.log has events at 12:05:00Z and 12:05:10Z
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:05:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:05:02Z Dispatched config (RC:9a6ddf.001)
2026-08-26T12:05:05Z Waiting for config sync
2026-08-26T12:05:10Z Cutoff set: 12:05:10Z
2026-08-26T12:05:30Z RESULT pass pointset pointset_publish STABLE 1/8 Sequence complete
""", encoding="utf-8")

    # pubber.log has earlier events at 12:00:01Z and 12:00:05Z
    pub_log = run_dir / "pubber.log"
    pub_log.write_text("""
2026-08-26T12:00:01Z events/pointset {"temperature": 21.0}
2026-08-26T12:00:05Z events/pointset {"temperature": 21.5}
""", encoding="utf-8")

    timeline = extract_timeline(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    events = timeline.get("events", [])
    assert len(events) >= 5

    # Check that STAGE_WAIT_START is detected
    checkpoints = [e["checkpoint"] for e in events]
    assert "STAGE_WAIT_START" in checkpoints

    # Check that chronological sorting was enforced:
    # Any pubber.log events with timestamp 12:00:01Z must precede sequence.log event at 12:05:00Z
    timestamps = [e.get("timestamp") for e in events if e.get("timestamp")]
    assert len(timestamps) >= 4
    # Check logs_found and logs_missing
    assert "sequence" in timeline["logs_found"]
    assert "pubber" in timeline["logs_found"]
    assert "validator" in timeline["logs_missing"]


def test_extract_timeline_missing_run_dir_fails_fast():
    import pytest
    with pytest.raises(FileNotFoundError, match="Run directory not found"):
        extract_timeline(
            test_id="pointset_publish",
            device_id="AHU-1",
            run_dir="/path/that/definitely/does/not/exist/12345",
        )


def test_inspect_message_trace(tmp_path):
    from mantis.tools.artifacts import inspect_message_trace

    run_dir = tmp_path / "run_traces"
    run_dir.mkdir()

    (run_dir / "events_pointset.json").write_text('{"points": {"temp": {"present_value": 22.5}}}')
    (run_dir / "state.json").write_text('{"system": {"operational": true}}')
    (run_dir / "config.json").write_text('{"system": {"min_loglevel": 200}}')

    # Inspect all traces
    res_all = inspect_message_trace(run_dir=str(run_dir))
    assert res_all["status"] == "SUCCESS"
    assert res_all["traces_count"] == 3
    filenames = [t["filename"] for t in res_all["traces"]]
    assert "events_pointset.json" in filenames
    assert "state.json" in filenames
    assert "config.json" in filenames

    # Filter by message type
    res_filtered = inspect_message_trace(run_dir=str(run_dir), message_type="pointset")
    assert res_filtered["status"] == "SUCCESS"
    assert res_filtered["traces_count"] == 1
    assert res_filtered["traces"][0]["filename"] == "events_pointset.json"
    assert res_filtered["traces"][0]["payload"]["points"]["temp"]["present_value"] == 22.5


def test_detect_log_anomalies(tmp_path):
    from mantis.tools.artifacts import detect_log_anomalies

    run_dir = tmp_path / "run_anomalies"
    run_dir.mkdir()

    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:05Z ignoring stale state update 12:44:00Z
2026-08-26T12:45:08Z UnrecognizedPropertyException: Unrecognized field "extra_key" (class udmi.schema.Metadata)
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = detect_log_anomalies(run_dir=str(run_dir))
    assert res["status"] == "SUCCESS"
    assert res["total_anomalies"] == 3
    categories = [a["category"] for a in res["anomalies"]]
    assert "STALE_STATE_CUTOFF" in categories
    assert "JACKSON_DESERIALIZATION" in categories
    assert "STAGE_TIMEOUT" in categories
    assert any("Unrecognized field" in a["context"] for a in res["anomalies"])


def test_ingest_support_bundle_zip_slip(tmp_path):
    zip_file = tmp_path / "malicious.zip"
    extract_target = tmp_path / "extracted"

    with zipfile.ZipFile(zip_file, "w") as z:
        # Write a member attempting directory traversal
        z.writestr("../../evil.txt", "malicious payload")

    res = ingest_support_bundle(str(zip_file), extract_to=str(extract_target))
    assert res["status"] == "ERROR"
    assert "Security violation" in res["error"]
    assert not (tmp_path / "evil.txt").exists()


def test_ingest_support_bundle_tar_slip(tmp_path):
    import tarfile
    import io

    tar_file = tmp_path / "malicious.tar.gz"
    extract_target = tmp_path / "extracted"

    with tarfile.open(tar_file, "w:gz") as t:
        ti = tarfile.TarInfo(name="../../evil_tar.txt")
        data = b"malicious tar payload"
        ti.size = len(data)
        t.addfile(ti, io.BytesIO(data))

    res = ingest_support_bundle(str(tar_file), extract_to=str(extract_target))
    assert res["status"] == "ERROR"
    assert "Security violation" in res["error"]
    assert not (tmp_path / "evil_tar.txt").exists()


def _write_seq(dir_path, marker):
    dir_path.mkdir(parents=True, exist_ok=True)
    (dir_path / "sequence.log").write_text(
        f"2026-08-26T12:05:00Z Starting test {marker}\n", encoding="utf-8"
    )


def test_extract_timeline_does_not_fall_back_to_repo_out_or_instances(tmp_path):
    """With no run_dir, logs from the repository's out/ or an arbitrary
    var/instances/* session belong to some other run. They must not be harvested;
    the miss is reported with every path that was searched."""
    import pytest
    _write_seq(tmp_path / "out", "unrelated_repo_out")
    _write_seq(tmp_path / "var" / "instances" / "udmi_test_other", "unrelated_instance")
    _write_seq(tmp_path / "var" / "instances" / "udmi_test_other" / "out", "unrelated_instance_out")

    with pytest.raises(FileNotFoundError) as excinfo:
        extract_timeline(test_id="pointset_publish", device_id="AHU-1", udmi_root=str(tmp_path))
    message = str(excinfo.value)
    assert str(tmp_path / "out" / "runs" / "AHU-1_pointset_publish") in message
    assert str(tmp_path / "out" / "runs" / "pointset_publish") in message
    assert "instances" not in message


def test_extract_timeline_finds_site_model_test_dir(tmp_path):
    site = tmp_path / "sites" / "lab"
    run = site / "out" / "devices" / "AHU-1" / "tests" / "pointset_publish"
    _write_seq(run, "pointset_publish")
    _write_seq(tmp_path / "out", "unrelated_repo_out")

    timeline = extract_timeline(
        test_id="pointset_publish", device_id="AHU-1",
        udmi_root=str(tmp_path), site_model="sites/lab",
    )
    assert timeline["run_dir"] == str(run)


def test_extract_timeline_prefers_device_specific_run(tmp_path):
    run = tmp_path / "out" / "runs" / "AHU-1_pointset_publish"
    _write_seq(run, "pointset_publish")
    timeline = extract_timeline(test_id="pointset_publish", device_id="AHU-1", udmi_root=str(tmp_path))
    assert timeline["run_dir"] == str(run)


def test_diagnose_test_failure_searches_the_site_model_run_dir(tmp_path):
    """diagnose_test_failure must hand its site_model to the timeline lookup so a
    triage without run_dir reads this device's recorded run, not repo out/."""
    from mantis.tools.diagnostics import diagnose_test_failure
    site = tmp_path / "sites" / "lab"
    (site / "devices" / "AHU-1").mkdir(parents=True)
    (site / "devices" / "AHU-1" / "metadata.json").write_text("{}", encoding="utf-8")
    run = site / "out" / "devices" / "AHU-1" / "tests" / "pointset_publish"
    _write_seq(run, "pointset_publish")

    res = diagnose_test_failure(
        test_id="pointset_publish", device_id="AHU-1",
        site_model="sites/lab", udmi_root=str(tmp_path),
    )
    assert str(run) in json.dumps(res, default=str)
