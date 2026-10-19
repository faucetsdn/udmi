"""Unit tests for mantis.models Pydantic contracts."""

import pytest
from pydantic import ValidationError
from mantis.models import (
    ClaimStatus,
    MessageRole,
    PatchStatus,
    SchemaInspectRequest,
    SchemaInspectResponse,
    SiteModelInspectRequest,
    SiteModelInspectResponse,
    PatchSiteModelRequest,
    PatchSiteModelResponse,
    TimelineRequest,
    TimelineResponse,
    DifferentialRequest,
    DifferentialResponse,
    EnsureSetupRequest,
    EnsureSetupResponse,
    StartProcessRequest,
    GetLogsRequest,
    TerminateSetupRequest,
    QueryDatabaseRequest,
    PublishMqttRequest,
    DiagnoseFailureRequest,
    DatabaseType,
    VerificationClaim,
    DiagnosticResult,
    ChatMessage,
    SessionContext,
)


def test_schema_models():
    req = SchemaInspectRequest(schema_name="pointset", sub_path="properties.points")
    assert req.schema_name == "pointset"
    assert req.sub_path == "properties.points"

    resp = SchemaInspectResponse(
        status="SUCCESS",
        schema_name="pointset",
        title="Pointset Schema",
        required=["timestamp"],
    )
    assert resp.status == "SUCCESS"
    assert resp.schema_name == "pointset"
    assert resp.required == ["timestamp"]


def test_site_model_models():
    req = SiteModelInspectRequest(site_model="sites/udmi_site_model", device_id="AHU-1")
    assert req.site_model == "sites/udmi_site_model"
    assert req.device_id == "AHU-1"

    patch_req = PatchSiteModelRequest(
        site_model="sites/udmi_site_model",
        device_id="AHU-1",
        patch_data={"pointset": {"sample_rate_sec": 10}},
        dry_run=True,
    )
    assert patch_req.dry_run is True

    patch_resp = PatchSiteModelResponse(
        status=PatchStatus.DRY_RUN,
        site_model="sites/udmi_site_model",
        device_id="AHU-1",
        applied=False,
    )
    assert patch_resp.status == PatchStatus.DRY_RUN


def test_timeline_models():
    req = TimelineRequest(test_id="pointset_publish", device_id="AHU-1")
    assert req.test_id == "pointset_publish"

    resp = TimelineResponse(
        test_id="pointset_publish",
        device_id="AHU-1",
        run_dir="/tmp/run",
        result="PASS",
        transactions=["RC:12345"],
        events=[],
    )
    assert resp.result == "PASS"
    assert resp.transactions == ["RC:12345"]


def test_diagnostic_models():
    claim = VerificationClaim(
        claim="Sequencer cutoff threshold rejected stale update",
        status=ClaimStatus.CONFIRMED,
        evidence="Cutoff set at 12:00:00Z; update at 11:59:00Z rejected",
    )
    assert claim.status == ClaimStatus.CONFIRMED

    diag = DiagnosticResult(
        test_id="pointset_publish",
        device_id="AHU-1",
        site_model="sites/udmi_site_model",
        root_cause="Stale cutoff rejection",
        evidence=["Log entry line 84"],
        fix=['bin/mantis "Set sample_rate_sec to 10"'],
        verification_matrix=[claim],
    )
    assert diag.status == "SUCCESS"
    assert len(diag.verification_matrix) == 1


def test_session_context_models():
    ctx = SessionContext(
        active_site_model="sites/udmi_site_model",
        active_device_id="AHU-1",
        active_test_id="pointset_publish",
    )
    msg = ChatMessage(
        role=MessageRole.USER,
        content="Why did it fail?",
        timestamp="2026-08-26T12:00:00Z",
    )
    ctx.history.append(msg)
    assert len(ctx.history) == 1
    assert ctx.history[0].role == MessageRole.USER


def test_lifecycle_and_mutation_models():
    req_start = StartProcessRequest(test_id="run_1", window="main", command="ls -la")
    assert req_start.test_id == "run_1"
    assert req_start.window == "main"

    req_logs = GetLogsRequest(test_id="run_1", lines=50)
    assert req_logs.window == "main"
    assert req_logs.lines == 50

    req_term = TerminateSetupRequest(test_id="run_1")
    assert req_term.clean_workspace is True

    req_db = QueryDatabaseRequest(test_id="run_1", database_type=DatabaseType.POSTGRES, query="SELECT 1;")
    assert req_db.database_type == DatabaseType.POSTGRES

    req_mqtt = PublishMqttRequest(test_id="run_1", topic="devices/AHU-1/state", payload="{}")
    assert req_mqtt.topic == "devices/AHU-1/state"

    req_diag = DiagnoseFailureRequest(test_id="pointset_publish", device_id="AHU-1")
    assert req_diag.site_model == "sites/udmi_site_model"
