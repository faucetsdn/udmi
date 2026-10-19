"""Canonical Pydantic v2 data models and type contracts for Mantis.

Defines Layer 0 data structures for tool parameters, responses, diagnostic
verification matrices, and session context.
"""

from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field


# ------------------------------------------------------------------------------
# Enums
# ------------------------------------------------------------------------------

class ClaimStatus(str, Enum):
    CONFIRMED = "CONFIRMED"
    REFUTED = "REFUTED"
    UNVERIFIED_ASSUMPTION = "UNVERIFIED ASSUMPTION"
    # No observation bears on the claim either way. Distinct from REFUTED,
    # which requires a positive observation that excludes the claim. Not
    # finding an error string in a log is not such an observation: a device
    # that never connected logs no connection error either.
    NOT_ASSESSED = "NOT ASSESSED"


class MessageRole(str, Enum):
    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"
    TOOL = "tool"


class DatabaseType(str, Enum):
    INFLUX = "influx"
    POSTGRES = "postgres"


class PatchStatus(str, Enum):
    PATCHED = "PATCHED"
    DRY_RUN = "DRY_RUN"
    ERROR = "ERROR"


# ------------------------------------------------------------------------------
# Layer 0: Tool Contracts - Specification & Site Model Grounding
# ------------------------------------------------------------------------------

class SchemaInspectRequest(BaseModel):
    schema_name: str = Field(..., description="Schema name or filename without extension (e.g. 'pointset')")
    sub_path: Optional[str] = Field(None, description="Dot-delimited property sub-path (e.g. 'properties.points')")
    resolve_refs: bool = Field(False, description="Recursively resolve $ref pointers in schema")


class SchemaSummary(BaseModel):
    name: str
    filename: str
    title: str = ""
    description: str = ""


class SchemaInspectResponse(BaseModel):
    model_config = ConfigDict(extra="allow")
    status: str = "SUCCESS"
    schema_name: str
    schema_file: Optional[str] = None
    id: Optional[str] = None
    title: Optional[str] = None
    description: Optional[str] = None
    required: List[str] = Field(default_factory=list)
    sub_path: Optional[str] = None
    schema_: Optional[Any] = Field(None, alias="schema")
    count: Optional[int] = None
    schemas: Optional[List[SchemaSummary]] = None
    error: Optional[str] = None
    available_schemas: Optional[List[str]] = None


class SiteModelInspectRequest(BaseModel):
    site_model: str = Field("sites/udmi_site_model", description="Path to site model directory")
    device_id: Optional[str] = Field(None, description="Optional target device identifier")


class SiteModelInspectResponse(BaseModel):
    model_config = ConfigDict(extra="allow")
    status: str = "SUCCESS"
    site_model: str
    device_id: Optional[str] = None
    metadata_file: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None
    system: Optional[Dict[str, Any]] = None
    gateway: Optional[Dict[str, Any]] = None
    point_count: Optional[int] = None
    points: Optional[List[str]] = None
    cloud_iot_config: Optional[Dict[str, Any]] = None
    device_count: Optional[int] = None
    devices: Optional[List[str]] = None
    error: Optional[str] = None
    available_devices: Optional[List[str]] = None


class PatchSiteModelRequest(BaseModel):
    site_model: str = Field("sites/udmi_site_model", description="Path to site model directory")
    device_id: str = Field(..., description="Target device identifier")
    patch_data: Dict[str, Any] = Field(..., description="Key-value dictionary to deep merge into metadata.json")
    dry_run: bool = Field(False, description="Preview diff without writing changes to disk")


class PatchSiteModelResponse(BaseModel):
    status: PatchStatus = PatchStatus.PATCHED
    site_model: str
    device_id: str
    file: Optional[str] = None
    backup: Optional[str] = None
    diff: Optional[str] = None
    applied: bool = False
    error: Optional[str] = None


# ------------------------------------------------------------------------------
# Layer 0: Tool Contracts - Diagnostics & Timelines
# ------------------------------------------------------------------------------

class TimelineEvent(BaseModel):
    step: int
    source: str
    line: int
    checkpoint: str
    timestamp: Optional[str] = None
    cutoff: Optional[str] = None
    status: Optional[str] = None
    description: str


class TimelineRequest(BaseModel):
    test_id: str
    device_id: str
    run_dir: Optional[str] = None


class TimelineResponse(BaseModel):
    model_config = ConfigDict(extra="allow")
    status: str = "SUCCESS"
    test_id: str
    device_id: str
    run_dir: str
    result: str = "UNKNOWN"
    transactions: List[str] = Field(default_factory=list)
    cutoff_threshold: Optional[str] = None
    stale_state_detected: bool = False
    stale_state_timestamp: Optional[str] = None
    jackson_error: Optional[str] = None
    timeout_error: Optional[str] = None
    avg_sample_rate_sec: Optional[float] = None
    events: List[TimelineEvent] = Field(default_factory=list)
    error: Optional[str] = None


class DifferentialStep(BaseModel):
    step: int
    checkpoint: str
    baseline: str
    target: str
    delta: str


class DifferentialRequest(BaseModel):
    target_run: str
    baseline_run: Optional[str] = None


class DifferentialResponse(BaseModel):
    status: str = "SUCCESS"
    target_run: str
    baseline_run: Optional[str] = None
    divergence_point: Optional[str] = None
    target_result: str = "UNKNOWN"
    baseline_result: str = "PASS"
    aligned_steps: List[DifferentialStep] = Field(default_factory=list)
    differential_table: str = ""
    error: Optional[str] = None


# ------------------------------------------------------------------------------
# Layer 0: Tool Contracts - Session & Infrastructure
# ------------------------------------------------------------------------------

class PortAllocations(BaseModel):
    mqtt: int
    etcd: int
    influx: int
    postgres: int


class EnsureSetupRequest(BaseModel):
    test_id: str
    site_model: str = "sites/udmi_site_model"
    dut_device_id: Optional[str] = None
    dut_serial_no: Optional[str] = None
    exclude: Optional[List[str]] = None
    added: Optional[List[str]] = None
    clean: bool = True
    timeout_seconds: int = 150


class RunSequencerTestRequest(BaseModel):
    test_name: str = Field(..., description="Name of the sequencer test sequence (e.g. 'pointset_publish', 'system_last_update', 'empty')")
    device_id: str = Field(..., description="Device ID under test (e.g. 'AHU-1', 'GAT-1')")
    target_spec: Optional[str] = Field(None, description="Target endpoint or cloud project specification (e.g. '//gbos/bos-platform-dev/faucetsdn', '//gcp/my-project/my-registry', '//mqtt/localhost:28430'). If omitted for local tests, uses active session or defaults to local isolated broker.")
    site_model: str = Field("sites/udmi_site_model", description="Local path to the site model directory containing device configurations (default: 'sites/udmi_site_model'). NOTE: This is always a local directory, not a '//...' URI.")
    session_id: Optional[str] = Field(None, description="Optional session name to execute within. If omitted, Mantis manages test sessions automatically.")


class StartProcessRequest(BaseModel):
    test_id: str
    window: str
    command: str


class GetLogsRequest(BaseModel):
    test_id: str
    window: str = "main"
    lines: int = 100


class TerminateSetupRequest(BaseModel):
    test_id: str
    clean_workspace: bool = True


class QueryDatabaseRequest(BaseModel):
    test_id: str
    database_type: DatabaseType
    query: str


class PublishMqttRequest(BaseModel):
    test_id: str
    topic: str
    payload: str
    target_spec: Optional[str] = None
    site_model: str = "sites/udmi_site_model"
    device_id: Optional[str] = None


class DiagnoseFailureRequest(BaseModel):
    test_id: str
    device_id: str
    site_model: str = "sites/udmi_site_model"
    run_dir: Optional[str] = None


class StabilityEvaluationRequest(BaseModel):
    test_id: Optional[str] = None
    site_model: str = "sites/udmi_site_model"
    base_dir: Optional[str] = None


class StabilityEvaluationResponse(BaseModel):
    model_config = ConfigDict(extra="allow")
    status: str = "SUCCESS"
    total_runs: int = 0
    pass_count: int = 0
    fail_count: int = 0
    pass_rate_pct: float = 0.0
    stability_score: float = 100.0
    flakiness_index: float = 0.0
    failure_breakdown: Dict[str, int] = Field(default_factory=dict)
    summary_report: str = ""
    error: Optional[str] = None


class EnsureSetupResponse(BaseModel):
    model_config = ConfigDict(extra="allow")
    status: str = "READY"
    test_id: str
    session_name: str
    connection_url: str
    project_spec: str
    windows: List[str] = Field(default_factory=list)
    ports: Optional[PortAllocations] = None
    site_model: Optional[str] = None
    run_dir: Optional[str] = None
    error: Optional[str] = None


# ------------------------------------------------------------------------------
# Layer 0: Diagnostic Verification Matrix & Adversarial Models
# ------------------------------------------------------------------------------

class VerificationClaim(BaseModel):
    claim: str
    status: ClaimStatus
    evidence: str


class HypothesisEvaluation(BaseModel):
    status: ClaimStatus
    evidence: str


class DiagnosticResult(BaseModel):
    status: str = "SUCCESS"
    test_id: str
    device_id: str
    site_model: str
    root_cause: str
    evidence: List[str] = Field(default_factory=list)
    fix: List[str] = Field(default_factory=list)
    verification_matrix: List[VerificationClaim] = Field(default_factory=list)
    competing_hypotheses: Dict[str, HypothesisEvaluation] = Field(default_factory=dict)
    timeline: Optional[Dict[str, Any]] = None
    report: str = ""
    error: Optional[str] = None


# ------------------------------------------------------------------------------
# Layer 0: Conversation & Multi-Turn Context
# ------------------------------------------------------------------------------

class ExecutionMetrics(BaseModel):
    total_steps: int = 0
    total_duration_sec: float = 0.0
    api_calls_count: int = 0
    prompt_tokens: int = 0
    candidates_tokens: int = 0
    total_tokens: int = 0
    tool_calls: Dict[str, int] = Field(default_factory=dict)
    retry_count: int = 0
    tripartite_degraded: bool = False
    tripartite_status: str = "SUCCESS"


class ChatMessage(BaseModel):
    role: MessageRole
    content: str
    timestamp: str
    tool_name: Optional[str] = None
    tool_call_id: Optional[str] = None


class SessionContext(BaseModel):
    active_site_model: str = "sites/udmi_site_model"
    active_session_id: Optional[str] = None
    active_device_id: Optional[str] = None
    active_test_id: Optional[str] = None
    history: List[ChatMessage] = Field(default_factory=list)
    metrics: Optional[ExecutionMetrics] = None
