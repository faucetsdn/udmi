"""Unit tests for mantis.tools.diagnostics and empirical stability metrics."""

import os
import pytest
from mantis.models import ClaimStatus
from mantis.tools.diagnostics import diagnose_test_failure, evaluate_test_stability


def test_diagnose_schema_violation_failure(tmp_path):
    run_dir = tmp_path / "run_schema_err"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:02Z Schema validation error: missing required point 'filter_alarm'
2026-08-26T12:45:03Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Schema Point Violation / Telemetry Malformation"]["status"] == ClaimStatus.CONFIRMED.value
    assert "missing required point" in res["root_cause"]


def test_diagnose_transport_failure(tmp_path):
    run_dir = tmp_path / "run_transport"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:02Z ConnectionRefusedError: Connection refused by broker on port 46432
2026-08-26T12:45:03Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Transport / TLS Connection Failure"]["status"] == ClaimStatus.CONFIRMED.value
    assert "Network transport" in res["root_cause"]


def test_diagnose_auth_failure(tmp_path):
    run_dir = tmp_path / "run_auth"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:02Z Connection Refused: not authorised (Bad username or password)
2026-08-26T12:45:03Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Authentication / Authorization Rejection"]["status"] == ClaimStatus.CONFIRMED.value
    assert "Authentication rejected" in res["root_cause"]


def test_diagnose_generic_stage_timeout(tmp_path):
    run_dir = tmp_path / "run_timeout"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")
    # The device answered; the stage timed out anyway. Without this payload the
    # run is one where nothing was ever heard from the device, and the timeout
    # is a symptom of that rather than a failure in its own right.
    (run_dir / "state_update.json").write_text(
        '{"timestamp": "2026-08-26T12:45:10Z", "version": "1.5.2", "system": {"operation": {}}}'
    )

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Device Did Not Respond"]["status"] == ClaimStatus.REFUTED.value
    assert res["competing_hypotheses"]["Stage Timeout Execution Failure"]["status"] == ClaimStatus.CONFIRMED.value


def test_diagnose_silent_device_outranks_its_own_timeout(tmp_path):
    """A run with no device traffic must be diagnosed as a silent device.

    The timeout is the sequencer giving up, not an independent fault, and the
    remediation must hold whether the device is physical or emulated.
    """
    run_dir = tmp_path / "run_silent"
    run_dir.mkdir()
    (run_dir / "sequence.log").write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")
    # Only the backend-injected ack, exactly as a real run against an absent
    # device records it.
    (run_dir / "state_update.json").write_text('{"configAcked": false}')

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    hypotheses = res["competing_hypotheses"]
    assert hypotheses["Device Did Not Respond"]["status"] == ClaimStatus.CONFIRMED.value
    assert "did not respond" in res["root_cause"]
    # The timeout is reported as downstream of the silence, not as the cause.
    assert hypotheses["Stage Timeout Execution Failure"]["status"] == ClaimStatus.REFUTED.value

    # Nothing reached the broker, so no subsystem may be declared healthy.
    for name in (
        "Transport / TLS Connection Failure",
        "Authentication / Authorization Rejection",
        "Transport / Broker Congestion",
    ):
        assert hypotheses[name]["status"] == ClaimStatus.NOT_ASSESSED.value

    joined = " ".join(res["fix"]).lower()
    # Covers both deployments without naming an implementation: the device
    # under test is external and is not necessarily pubber.
    assert "physical device" in joined and "emulated device" in joined
    assert "pubber" not in joined


def test_evaluate_test_stability(tmp_path):
    # Create 3 test runs: 2 PASS, 1 FAIL
    runs_dir = tmp_path / "runs"
    runs_dir.mkdir()

    run1 = runs_dir / "run_pass_1"
    run1.mkdir()
    (run1 / "sequence.log").write_text("RESULT pass pointset pointset_publish STABLE 1/8 Sequence complete\n")

    run2 = runs_dir / "run_pass_2"
    run2.mkdir()
    (run2 / "sequence.log").write_text("RESULT pass pointset pointset_publish STABLE 1/8 Sequence complete\n")

    run3 = runs_dir / "run_fail_1"
    run3.mkdir()
    (run3 / "sequence.log").write_text("""
UnrecognizedPropertyException: Unrecognized field "bad_prop"
RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    stab = evaluate_test_stability(base_dir=str(runs_dir))
    assert stab["status"] == "SUCCESS"
    assert stab["total_runs"] == 3
    assert stab["pass_count"] == 2
    assert stab["fail_count"] == 1
    assert pytest.approx(stab["pass_rate_pct"], 0.1) == 66.7
    assert stab["failure_breakdown"].get("Jackson Deserialization Failure") == 1
    assert "System Stability Score" in stab["summary_report"]


def test_diagnose_multiline_jackson_with_caused_by(tmp_path):
    run_dir = tmp_path / "run_multiline_jackson"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
java.lang.RuntimeException: Failed to load device config
\tat com.google.daq.mqtt.sequencer.SequenceBase.setup(SequenceBase.java:145)
Caused by: com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field "extra_key" (class udmi.schema.Metadata)
 at [Source: (String)"{\\n  \\"extra_key\\": 123\\n}"; line: 2, column: 15]
\tat com.fasterxml.jackson.databind.exc.PropertyBindingException.from(PropertyBindingException.java:62)
2026-08-26T12:45:03Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Jackson Deserialization Failure"]["status"] == ClaimStatus.CONFIRMED.value
    assert "extra_key" in res["root_cause"] or "Unrecognized field" in res["root_cause"]


def test_diagnose_multiline_assertion_timeout(tmp_path):
    run_dir = tmp_path / "run_multiline_timeout"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
java.lang.AssertionError: Sequence failed: Timeout waiting for state sync (stage 2) after 120s
\tat org.junit.Assert.fail(Assert.java:89)
\tat com.google.daq.mqtt.sequencer.SequenceRunner.waitForState(SequenceRunner.java:312)
2026-08-26T12:47:03Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")
    # The device was present and publishing; the stage assertion is what failed.
    (run_dir / "state_update.json").write_text(
        '{"timestamp": "2026-08-26T12:45:10Z", "version": "1.5.2", "pointset": {"points": {}}}'
    )

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Stage Timeout Execution Failure"]["status"] == ClaimStatus.CONFIRMED.value


def test_diagnose_gateway_field_bus_failure(tmp_path):
    # Setup site model with gateway GAT-1 and proxy sub-device VAV-1
    site_dir = tmp_path / "sites" / "test_site"
    gat_dir = site_dir / "devices" / "GAT-1"
    vav_dir = site_dir / "devices" / "VAV-1"
    gat_dir.mkdir(parents=True)
    vav_dir.mkdir(parents=True)

    (gat_dir / "metadata.json").write_text('{"system": {"make_model": "Gateway-X"}}')
    (vav_dir / "metadata.json").write_text('{"system": {"make_model": "VAV-Unit"}, "gateway": {"gateway_id": "GAT-1"}}')

    run_dir = tmp_path / "run_gw_bus_err"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for VAV-1
2026-08-26T12:45:05Z Dispatched config (RC:9a6ddf.001)
2026-08-26T12:45:10Z gateway_proxy_error: RS-485 framing error / serial frame CRC error on port /dev/ttyS1
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="VAV-1",
        site_model=str(site_dir),
        run_dir=str(run_dir),
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Field Bus Communication Failure"]["status"] == ClaimStatus.CONFIRMED.value
    # Assert Critic marked timeout as cascading, not an independent root cause
    assert res["competing_hypotheses"]["Stage Timeout Execution Failure"]["status"] == ClaimStatus.REFUTED.value
    assert "Field bus communication failure" in res["root_cause"]
    assert any("RS-485" in fix for fix in res["fix"])
    assert "```mermaid" in res["report"]


def test_diagnose_gateway_firmware_driver_bug(tmp_path):
    site_dir = tmp_path / "sites" / "test_site"
    gat_dir = site_dir / "devices" / "GAT-1"
    vav_dir = site_dir / "devices" / "VAV-1"
    gat_dir.mkdir(parents=True)
    vav_dir.mkdir(parents=True)

    (gat_dir / "metadata.json").write_text('{"system": {"make_model": "Gateway-X"}}')
    (vav_dir / "metadata.json").write_text('{"system": {"make_model": "VAV-Unit"}, "gateway": {"gateway_id": "GAT-1"}}')

    run_dir = tmp_path / "run_gw_driver_bug"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for VAV-1
2026-08-26T12:45:05Z Dispatched config (RC:9a6ddf.001)
2026-08-26T12:45:20Z gateway GAT-1 omitted sub-device echo for VAV-1 in aggregated state
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="VAV-1",
        site_model=str(site_dir),
        run_dir=str(run_dir),
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Gateway Firmware / Driver State Translation Bug"]["status"] == ClaimStatus.CONFIRMED.value
    assert "Gateway firmware / driver state translation bug" in res["root_cause"]
    assert any("driver/firmware" in fix for fix in res["fix"])


def test_diagnose_proxy_binding_failure(tmp_path):
    site_dir = tmp_path / "sites" / "test_site"
    vav_dir = site_dir / "devices" / "VAV-1"
    vav_dir.mkdir(parents=True)

    # Sub-device references MISSING-GAT-99 which is NOT in site model
    (vav_dir / "metadata.json").write_text('{"system": {"make_model": "VAV-Unit"}, "gateway": {"gateway_id": "MISSING-GAT-99"}}')

    run_dir = tmp_path / "run_proxy_binding_fail"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for VAV-1
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="VAV-1",
        site_model=str(site_dir),
        run_dir=str(run_dir),
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Proxy Binding Failure"]["status"] == ClaimStatus.CONFIRMED.value
    assert "Proxy binding configuration error" in res["root_cause"]


def _gateway_site(tmp_path):
    """Site model with gateway GAT-1 and proxy sub-device VAV-1."""
    site_dir = tmp_path / "sites" / "test_site"
    gat_dir = site_dir / "devices" / "GAT-1"
    vav_dir = site_dir / "devices" / "VAV-1"
    gat_dir.mkdir(parents=True)
    vav_dir.mkdir(parents=True)
    (gat_dir / "metadata.json").write_text('{"system": {"make_model": "Gateway-X"}}')
    (vav_dir / "metadata.json").write_text(
        '{"system": {"make_model": "VAV-Unit"}, "gateway": {"gateway_id": "GAT-1"}}'
    )
    return site_dir


_PROXY_SEQ_LOG = """
2026-08-26T12:45:00Z Starting test pointset_publish for VAV-1
2026-08-26T12:45:05Z Dispatched config (RC:9a6ddf.001)
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
"""


def test_diagnose_gateway_proxy_bus_drop(tmp_path):
    site_dir = _gateway_site(tmp_path)

    # Real sequencer layout: each device gets its own run directory, which is
    # what lets the gateway be judged on its own traffic rather than the
    # sub-device's.
    vav_run = site_dir / "out" / "devices" / "VAV-1" / "tests" / "pointset_publish"
    gat_run = site_dir / "out" / "devices" / "GAT-1" / "tests" / "pointset_publish"
    vav_run.mkdir(parents=True)
    gat_run.mkdir(parents=True)

    (vav_run / "sequence.log").write_text(_PROXY_SEQ_LOG)
    # Sub-device: backend ack only, nothing it authored itself.
    (vav_run / "state_update.json").write_text('{"configAcked": true}')
    # Gateway: publishing normally, so the drop is isolated to the proxy path.
    (gat_run / "state_update.json").write_text(
        '{"timestamp": "2026-08-26T12:45:10Z", "version": "1.5.2", "gateway": {"devices": {}}}'
    )

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="VAV-1",
        site_model=str(site_dir),
        run_dir=str(vav_run),
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Gateway Proxy Bus Drop"]["status"] == ClaimStatus.CONFIRMED.value
    assert "Gateway proxy bus drop" in res["root_cause"]


def test_gateway_proxy_bus_drop_not_claimed_when_gateway_also_silent(tmp_path):
    """A silent gateway must not be reported as an online one.

    Confirming a proxy bus drop says the gateway was up while the sub-device
    was not. With no gateway traffic observed, that localisation is unsupported
    and the finding is that the sub-device did not respond.
    """
    site_dir = _gateway_site(tmp_path)

    vav_run = site_dir / "out" / "devices" / "VAV-1" / "tests" / "pointset_publish"
    gat_run = site_dir / "out" / "devices" / "GAT-1" / "tests" / "pointset_publish"
    vav_run.mkdir(parents=True)
    gat_run.mkdir(parents=True)

    (vav_run / "sequence.log").write_text(_PROXY_SEQ_LOG)
    (vav_run / "state_update.json").write_text('{"configAcked": true}')
    # Gateway run directory exists but the gateway published nothing either.
    (gat_run / "state_update.json").write_text('{"configAcked": false}')

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="VAV-1",
        site_model=str(site_dir),
        run_dir=str(vav_run),
        udmi_root=str(tmp_path),
    )

    hypotheses = res["competing_hypotheses"]
    assert hypotheses["Gateway Proxy Bus Drop"]["status"] == ClaimStatus.NOT_ASSESSED.value
    assert hypotheses["Gateway Connection Drop"]["status"] == ClaimStatus.NOT_ASSESSED.value
    assert hypotheses["Device Did Not Respond"]["status"] == ClaimStatus.CONFIRMED.value
    assert "did not respond" in res["root_cause"]


def test_critic_rival_hypothesis_invalidation(tmp_path):
    run_dir = tmp_path / "run_critic_audit"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:05Z Cutoff set: 12:45:08Z
2026-08-26T12:45:07Z ignoring stale state update timestamp 12:45:06Z
2026-08-26T12:47:00Z Stage timeout after 120s waiting for telemetry echo
2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Stale State Cutoff Rejection"]["status"] == ClaimStatus.CONFIRMED.value

    # Refutations must rest on a positive observation. metadata.json was
    # re-parsed and accepted; the sequencer logged rejecting a state update,
    # which is only possible if the device sent one; and the timeout is
    # accounted for by the confirmed root cause.
    assert res["competing_hypotheses"]["Jackson Deserialization Failure"]["status"] == ClaimStatus.REFUTED.value
    assert res["competing_hypotheses"]["Device Did Not Respond"]["status"] == ClaimStatus.REFUTED.value
    assert res["competing_hypotheses"]["Stage Timeout Execution Failure"]["status"] == ClaimStatus.REFUTED.value

    # These were never exercised. Marking them REFUTED asserted that the
    # broker, credentials and schema were checked and found healthy, none of
    # which happened.
    for name in (
        "Schema Point Violation / Telemetry Malformation",
        "Transport / TLS Connection Failure",
        "Authentication / Authorization Rejection",
        "Telemetry Cadence Mismatch",
        "Transport / Broker Congestion",
    ):
        assert res["competing_hypotheses"][name]["status"] == ClaimStatus.NOT_ASSESSED.value

    # The exact all-clears this tool used to invent and present as evidence.
    evidence_blob = " ".join(m["evidence"] for m in res["verification_matrix"]).lower()
    for invented in (
        "transport connection healthy",
        "client authenticated successfully",
        "verified healthy",
        "verified nominal",
        "within normal limits",
    ):
        assert invented not in evidence_blob

    # Verify verification matrix integrity
    matrix = res["verification_matrix"]
    assert len(matrix) >= 7
    claims = [m["claim"] for m in matrix]
    assert any("lagged sequencer cutoff" in c for c in claims)


def test_evaluate_test_stability_device_detection(tmp_path):
    from unittest.mock import patch

    runs_dir = tmp_path / "runs"
    runs_dir.mkdir()

    # Case 1: triage_manifest.json specifies device_id
    run1 = runs_dir / "run_manifest"
    run1.mkdir()
    (run1 / "triage_manifest.json").write_text('{"device_id": "PUMP-1"}')
    (run1 / "sequence.log").write_text("RESULT pass pointset pointset_publish STABLE 1/8 Sequence complete\n")

    # Case 2: folder name has <device>_<test> pattern
    run2 = runs_dir / "FCU-1_pointset_publish"
    run2.mkdir()
    (run2 / "sequence.log").write_text("RESULT pass pointset pointset_publish STABLE 1/8 Sequence complete\n")

    # Case 3: sequence.log specifies Starting test for <device>
    run3 = runs_dir / "run_from_log"
    run3.mkdir()
    (run3 / "sequence.log").write_text("2026-08-26T12:45:00Z Starting test pointset_publish for CHILLER-1\nRESULT pass pointset pointset_publish STABLE 1/8 Sequence complete\n")

    # Case 4: Fallback
    run4 = runs_dir / "run_fallback"
    run4.mkdir()
    (run4 / "sequence.log").write_text("RESULT pass pointset pointset_publish STABLE 1/8 Sequence complete\n")

    with patch("mantis.tools.diagnostics.diagnose_test_failure") as mock_diag:
        mock_diag.return_value = {
            "status": "SUCCESS",
            "timeline": {"result": "PASS"},
            "competing_hypotheses": {},
        }

        # 1. Test auto-detection
        res = evaluate_test_stability(base_dir=str(runs_dir))
        assert res["status"] == "SUCCESS"
        assert res["total_runs"] == 4

        devices_called = {call.kwargs.get("device_id") for call in mock_diag.call_args_list}
        assert "PUMP-1" in devices_called
        assert "FCU-1" in devices_called
        assert "CHILLER-1" in devices_called
        assert "UNKNOWN_DEVICE" in devices_called

    # 2. Test explicit device_id override
    with patch("mantis.tools.diagnostics.diagnose_test_failure") as mock_diag:
        mock_diag.return_value = {
            "status": "SUCCESS",
            "timeline": {"result": "PASS"},
            "competing_hypotheses": {},
        }
        res = evaluate_test_stability(base_dir=str(runs_dir), device_id="CUSTOM-DEVICE")
        assert res["status"] == "SUCCESS"
        for call in mock_diag.call_args_list:
            assert call.kwargs.get("device_id") == "CUSTOM-DEVICE"


