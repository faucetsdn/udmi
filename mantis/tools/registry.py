"""Centralized Tool Registry, Schemas, and Unified Execution Dispatcher for Mantis and MCP."""

from dataclasses import dataclass
import inspect
from typing import Any, Callable, Dict, List, Optional, Type
from pydantic import BaseModel

from mantis.session import SessionManager
from mantis.models import (
    DiagnoseFailureRequest,
    DifferentialRequest,
    EnsureSetupRequest,
    GetLogsRequest,
    PatchSiteModelRequest,
    PublishMqttRequest,
    QueryDatabaseRequest,
    RunSequencerTestRequest,
    SchemaInspectRequest,
    SiteModelInspectRequest,
    StabilityEvaluationRequest,
    StartProcessRequest,
    TerminateSetupRequest,
    TimelineRequest,
)
from mantis.tools.artifacts import extract_timeline
from mantis.tools.diagnostics import diagnose_test_failure, evaluate_test_stability
from mantis.tools.differential import compare_test_runs
from mantis.tools.patcher import patch_site_model
from mantis.tools.schemas import inspect_udmi_schema
from mantis.tools.site_models import inspect_site_model


@dataclass
class ToolDefinition:
    name: str
    description: str
    func: Callable[..., Any]
    request_model: Optional[Type[BaseModel]] = None
    manual_schema: Optional[Dict[str, Any]] = None


_REGISTERED_TOOLS: Dict[str, ToolDefinition] = {}


def register_tool(
    name: Optional[str] = None,
    description: Optional[str] = None,
    request_model: Optional[Type[BaseModel]] = None,
    manual_schema: Optional[Dict[str, Any]] = None,
):
    """Decorator to register a tool function with automatic Pydantic validation and schema exposure."""
    def decorator(fn: Callable[..., Any]):
        tool_name = name or fn.__name__
        tool_desc = description or (fn.__doc__ or "").strip()
        _REGISTERED_TOOLS[tool_name] = ToolDefinition(
            name=tool_name,
            description=tool_desc,
            func=fn,
            request_model=request_model,
            manual_schema=manual_schema,
        )
        return fn
    return decorator


# ------------------------------------------------------------------------------
# Tool Registrations (Tier 1 - Tier 4)
# ------------------------------------------------------------------------------

# Tier 1: Session & Process Lifecycle
@register_tool(
    name="ensure_test_setup",
    description="Ensures that an isolated local UDMI test infrastructure stack (Mosquitto broker, UDMIS control plane, etcd, InfluxDB, PostgreSQL, and optional DUT) is running inside a tmux session, healthy, and ready for client traffic.",
    request_model=EnsureSetupRequest,
)
def _tool_ensure_test_setup(session_mgr: SessionManager, **kwargs):
    return session_mgr.ensure_test_setup(**kwargs)


@register_tool(
    name="run_sequencer_test",
    description="Launches a sequencer test sequence (e.g. 'pointset_publish', 'system_last_update') against a local broker or remote cloud endpoint (e.g. '//gbos/bos-platform-dev/faucetsdn', '//gcp/project/registry', '//mqtt/localhost:28430'). Does not require local Docker/infrastructure setup for cloud endpoints.",
    request_model=RunSequencerTestRequest,
)
def _tool_run_sequencer_test(
    session_mgr: SessionManager,
    test_name: str,
    device_id: str,
    target_spec: Optional[str] = None,
    site_model: str = "sites/udmi_site_model",
    session_id: Optional[str] = None,
):
    from mantis.tools.sequencer import run_sequencer_test
    return run_sequencer_test(
        session_mgr=session_mgr,
        test_name=test_name,
        device_id=device_id,
        target_spec=target_spec,
        site_model=site_model,
        session_id=session_id,
    )


@register_tool(
    name="start_session_process",
    description="Launches a command or test process inside a named semantic window of an active UDMI session (e.g. launching sequencer tests, custom Pubber devices, or monitoring scripts).",
    request_model=StartProcessRequest,
)
def _tool_start_session_process(session_mgr: SessionManager, test_id: str, window: str, command: str):
    from mantis.tools.process import start_session_process
    return start_session_process(session_mgr=session_mgr, test_id=test_id, window=window, command=command)


@register_tool(
    name="terminate_test_setup",
    description="Terminates an active UDMI isolated tmux session and optionally cleans instance files.",
    request_model=TerminateSetupRequest,
)
def _tool_terminate_test_setup(session_mgr: SessionManager, test_id: str, clean_workspace: bool = True):
    return session_mgr.terminate_test_setup(test_id=test_id, clean_workspace=clean_workspace)


@register_tool(
    name="list_test_setups",
    description="Lists all currently active isolated UDMI test infrastructure sessions.",
)
def _tool_list_test_setups(session_mgr: SessionManager):
    return session_mgr.list_test_setups()


@register_tool(
    name="list_test_windows",
    description="Lists all active named semantic windows (e.g. main, dut, sequencer, butler, validator) within a test session.",
    manual_schema={
        "type": "object",
        "required": ["test_id"],
        "properties": {
            "test_id": {"type": "string", "description": "Identifier of the active test session."}
        },
    },
)
def _tool_list_test_windows(session_mgr: SessionManager, test_id: str):
    if not test_id:
        raise ValueError("Parameter 'test_id' is required for list_test_windows")
    return session_mgr.list_test_windows(test_id=test_id)


@register_tool(
    name="get_test_logs",
    description="Captures live console output from a named semantic tmux window (e.g. 'main', 'dut', 'sequencer', 'butler', 'validator') for an active test session.",
    request_model=GetLogsRequest,
)
def _tool_get_test_logs(session_mgr: SessionManager, test_id: str, window: str = "main", lines: int = 100):
    return session_mgr.get_test_logs(test_id=test_id, window=window, lines=lines)


# Tier 2: Real-time Inspection & Mutation
@register_tool(
    name="query_database",
    description="Executes a read-only SQL or Flux query against the isolated PostgreSQL or InfluxDB database instance.",
    request_model=QueryDatabaseRequest,
)
def _tool_query_database(session_mgr: SessionManager, test_id: str, database_type: Any, query: str):
    from mantis.tools.database import query_database
    db_type_val = database_type.value if hasattr(database_type, "value") else str(database_type)
    return query_database(session_mgr=session_mgr, test_id=test_id, database_type=db_type_val, query=query)


@register_tool(
    name="publish_mqtt_message",
    description="Publishes a raw payload directly to an MQTT topic on the isolated local Mosquitto broker or remote cloud endpoint.",
    request_model=PublishMqttRequest,
)
def _tool_publish_mqtt_message(
    session_mgr: SessionManager,
    test_id: str,
    topic: str,
    payload: str,
    target_spec: Optional[str] = None,
    site_model: str = "sites/udmi_site_model",
    device_id: Optional[str] = None,
):
    from mantis.tools.mqtt import publish_mqtt_message
    return publish_mqtt_message(
        session_mgr=session_mgr,
        test_id=test_id,
        topic=topic,
        payload=payload,
        target_spec=target_spec,
        site_model=site_model,
        device_id=device_id,
    )


# Tier 3: Specification & Site Model Grounding
@register_tool(
    name="inspect_udmi_schema",
    description="Resolves and inspects official UDMI JSON schemas under schema/.",
    request_model=SchemaInspectRequest,
)
def _tool_inspect_udmi_schema(
    schema_name: str,
    sub_path: Optional[str] = None,
    resolve_refs: bool = False,
    udmi_root: Optional[str] = None,
):
    return inspect_udmi_schema(
        schema_name=schema_name,
        sub_path=sub_path,
        resolve_refs=resolve_refs,
        udmi_root=udmi_root,
    )


@register_tool(
    name="inspect_site_model",
    description="Inspects and validates site model directories and device metadata definitions.",
    request_model=SiteModelInspectRequest,
)
def _tool_inspect_site_model(site_model: str = "sites/udmi_site_model", device_id: Optional[str] = None, udmi_root: Optional[str] = None):
    return inspect_site_model(site_model=site_model, device_id=device_id, udmi_root=udmi_root)


@register_tool(
    name="patch_site_model",
    description="Safely mutates device metadata.json with atomic backup and unified diff preview.",
    request_model=PatchSiteModelRequest,
)
def _tool_patch_site_model(site_model: str, device_id: str, patch_data: Dict[str, Any], dry_run: bool = False, udmi_root: Optional[str] = None):
    return patch_site_model(site_model=site_model, device_id=device_id, patch_data=patch_data, dry_run=dry_run, udmi_root=udmi_root)


# Tier 4: Diagnostic Intelligence
@register_tool(
    name="get_test_timeline",
    description="Extracts chronological timestamps, transaction IDs (RC:...), cutoffs, and status transitions from test logs.",
    request_model=TimelineRequest,
)
def _tool_get_test_timeline(test_id: str, device_id: str, run_dir: Optional[str] = None, udmi_root: Optional[str] = None):
    return extract_timeline(test_id=test_id, device_id=device_id, run_dir=run_dir, udmi_root=udmi_root)


@register_tool(
    name="compare_test_runs",
    description="Performs behavioral differential sequence alignment between a target test run and a reference baseline.",
    request_model=DifferentialRequest,
)
def _tool_compare_test_runs(target_run: str, baseline_run: Optional[str] = None, udmi_root: Optional[str] = None):
    return compare_test_runs(target_run=target_run, baseline_run=baseline_run, udmi_root=udmi_root)


@register_tool(
    name="diagnose_test_failure",
    description="Performs complete root-cause analysis on a failed test execution using the built-in adversarial critique loop.",
    request_model=DiagnoseFailureRequest,
)
def _tool_diagnose_test_failure(test_id: str, device_id: str, site_model: str = "sites/udmi_site_model", run_dir: Optional[str] = None, udmi_root: Optional[str] = None):
    return diagnose_test_failure(test_id=test_id, device_id=device_id, site_model=site_model, run_dir=run_dir, udmi_root=udmi_root)


@register_tool(
    name="evaluate_test_stability",
    description="Calculates empirical reliability score, flakiness index, and failure mode distribution across test runs.",
    request_model=StabilityEvaluationRequest,
)
def _tool_evaluate_test_stability(test_id: Optional[str] = None, site_model: str = "sites/udmi_site_model", base_dir: Optional[str] = None, udmi_root: Optional[str] = None):
    return evaluate_test_stability(test_id=test_id, site_model=site_model, base_dir=base_dir, udmi_root=udmi_root)


@register_tool(
    name="verify_golden_baseline",
    description="Validates test run outputs against golden expectation baselines in etc/ (e.g. 'validator', 'sequencer') and enforces anti-cheating integrity rules.",
    manual_schema={
        "type": "object",
        "properties": {
            "baseline_name": {
                "type": "string",
                "description": "Name of golden baseline file in etc/ (e.g. 'validator', 'sequencer', 'schema_nostate'). Default is 'validator'.",
            },
            "test_output_path": {
                "type": "string",
                "description": "Optional path to actual test output file to verify. Defaults to out/<baseline_name>.out.",
            },
        },
    },
)
def _tool_verify_golden_baseline(
    baseline_name: str = "validator",
    test_output_path: Optional[str] = None,
    udmi_root: Optional[str] = None,
):
    from mantis.tools.golden import verify_golden_baseline
    return verify_golden_baseline(
        baseline_name=baseline_name,
        test_output_path=test_output_path,
        udmi_root=udmi_root,
    )


# Codebase & Specification Traversal
@register_tool(
    name="read_udmi_file",
    description="Reads exact lines from any source file, schema, or specification document in the UDMI repository.",
    manual_schema={
        "type": "object",
        "required": ["file_path"],
        "properties": {
            "file_path": {"type": "string", "description": "Relative or absolute path to the file within the UDMI repository."},
            "start_line": {"type": "integer", "description": "Optional 1-based start line index."},
            "end_line": {"type": "integer", "description": "Optional 1-based end line index (inclusive)."},
        },
    },
)
def _tool_read_udmi_file(file_path: str, start_line: Optional[int] = None, end_line: Optional[int] = None, udmi_root: Optional[str] = None):
    from mantis.tools.codebase import read_udmi_file
    return read_udmi_file(file_path=file_path, start_line=start_line, end_line=end_line, udmi_root=udmi_root)


@register_tool(
    name="search_codebase",
    description=(
        "Search source, docs, config, and log files in the UDMI repository (.java, .py, .json, "
        ".md, .yaml, .yml, .sh, .txt, .log). Returns matching snippets and a summary of the "
        "locations where matches were found. Run-output directories (out/, out_*/, var/) are not "
        "traversed by default; any that were skipped come back in 'skipped_artifact_dirs', and a "
        "test run directory can be searched by passing it as path_prefix. A file_pattern selecting "
        "an unreadable extension returns an error rather than an empty result."
    ),
    manual_schema={
        "type": "object",
        "required": ["query"],
        "properties": {
            "query": {"type": "string", "description": "Text or regex pattern to search for."},
            "path_prefix": {"type": "string", "description": "Optional subdirectory prefix to restrict search (e.g. 'udmis', 'validator', 'pubber', 'common', 'docs', or a test run directory such as 'sites/<site>/out/devices/<device>/tests/<test>')."},
            "file_pattern": {"type": "string", "description": "Optional glob pattern to filter files (e.g. '*.java', '*.json', '*.log')."},
            "max_results": {"type": "integer", "description": "Maximum number of match snippets to sample (default 25)."},
            "max_per_file": {"type": "integer", "description": "Maximum number of matches sampled per file (default 3) to prevent single-file flooding."},
            "is_regex": {"type": "boolean", "description": "Whether to treat query as a regular expression."},
        },
    },
)
def _tool_search_codebase(
    query: str,
    path_prefix: Optional[str] = None,
    file_pattern: Optional[str] = None,
    max_results: int = 25,
    max_per_file: int = 3,
    is_regex: bool = False,
    udmi_root: Optional[str] = None,
):
    from mantis.tools.codebase import search_codebase
    return search_codebase(
        query=query,
        path_prefix=path_prefix,
        file_pattern=file_pattern,
        max_results=max_results,
        max_per_file=max_per_file,
        is_regex=is_regex,
        udmi_root=udmi_root,
    )


@register_tool(
    name="get_udmis_runtime_logs",
    description=(
        "Retrieves UDMIS backend runtime logs for an incident window and declares which "
        "evidence tier they came from: LOCAL_FILE (out/udmis.log from a local stack), CLOUD "
        "(GCP Cloud Logging, within retention), or UNAVAILABLE. This is the only source of "
        "UDMIS runtime behavior; backend source code shows intent, not what happened. An "
        "UNAVAILABLE result reports why each tier was rejected and returns the declaration "
        "that backend conclusions must then carry."
    ),
    manual_schema={
        "type": "object",
        "required": [],
        "properties": {
            "pattern": {"type": "string", "description": "Literal substring to match (case-insensitive) in log lines; becomes a free-text search for the cloud tier."},
            "window_start": {"type": "string", "description": "Optional ISO-8601 start of the incident window (e.g. '2026-09-14T18:00:00Z')."},
            "window_end": {"type": "string", "description": "Optional ISO-8601 end of the incident window."},
            "project_spec": {"type": "string", "description": "Target spec of the run under investigation (e.g. '//gbos/my-project'). Required to reach the cloud tier."},
            "log_filter": {"type": "string", "description": "Explicit Cloud Logging filter, for when free-text search on 'pattern' is too coarse."},
            "max_lines": {"type": "integer", "description": "Maximum number of log lines or entries to return (default 200)."},
        },
    },
)
def _tool_get_udmis_runtime_logs(
    pattern: Optional[str] = None,
    window_start: Optional[str] = None,
    window_end: Optional[str] = None,
    project_spec: Optional[str] = None,
    log_filter: Optional[str] = None,
    max_lines: int = 200,
    udmi_root: Optional[str] = None,
):
    from mantis.tools.udmis_logs import get_udmis_runtime_logs
    return get_udmis_runtime_logs(
        pattern=pattern,
        window_start=window_start,
        window_end=window_end,
        project_spec=project_spec,
        log_filter=log_filter,
        max_lines=max_lines,
        udmi_root=udmi_root,
    )


@register_tool(
    name="locate_udmi_doc",
    description="Finds authoritative markdown guides and specs in docs/ matching a topic (e.g. 'writeback', 'gateway', 'state', 'pointset').",
    manual_schema={
        "type": "object",
        "required": ["topic"],
        "properties": {
            "topic": {"type": "string", "description": "Topic or keyword (e.g. 'writeback', 'gateway', 'state', 'pointset', 'discovery', 'bacnet')."},
        },
    },
)
def _tool_locate_udmi_doc(topic: str, udmi_root: Optional[str] = None):
    from mantis.tools.codebase import locate_udmi_doc
    return locate_udmi_doc(topic=topic, udmi_root=udmi_root)


@register_tool(
    name="inspect_sequencer_test",
    description="Dynamically locates and parses Java test classes in validator/.../sequencer/sequences/, extracting method body, @Feature annotations, stage, and assertions.",
    manual_schema={
        "type": "object",
        "required": ["test_name"],
        "properties": {
            "test_name": {"type": "string", "description": "Name of the sequencer test (e.g. 'pointset_publish', 'system_last_start')."},
        },
    },
)
def _tool_inspect_sequencer_test(test_name: str, udmi_root: Optional[str] = None):
    from mantis.tools.codebase import inspect_sequencer_test
    return inspect_sequencer_test(test_name=test_name, udmi_root=udmi_root)


@register_tool(
    name="inspect_message_trace",
    description="Inspects recorded MQTT message payloads (events, state, config, validation) captured during test execution.",
    manual_schema={
        "type": "object",
        "required": ["run_dir"],
        "properties": {
            "run_dir": {"type": "string", "description": "Path to test run directory containing trace JSON files."},
            "message_type": {"type": "string", "description": "Optional message filter (e.g. 'events_pointset', 'state', 'config')."},
        },
    },
)
def _tool_inspect_message_trace(run_dir: str, message_type: Optional[str] = None, udmi_root: Optional[str] = None):
    from mantis.tools.artifacts import inspect_message_trace
    return inspect_message_trace(run_dir=run_dir, message_type=message_type, udmi_root=udmi_root)


@register_tool(
    name="detect_log_anomalies",
    description="Scans test execution logs for timing anomalies, deserialization errors, framing drops, and broker issues.",
    manual_schema={
        "type": "object",
        "required": ["run_dir"],
        "properties": {
            "run_dir": {"type": "string", "description": "Path to test run directory containing sequence.log, pubber.log, etc."},
        },
    },
)
def _tool_detect_log_anomalies(run_dir: str, udmi_root: Optional[str] = None):
    from mantis.tools.artifacts import detect_log_anomalies
    return detect_log_anomalies(run_dir=run_dir, udmi_root=udmi_root)


# Tier 5: Dual Visualization Engine (Graphviz DOT & Mermaid)
@register_tool(
    name="generate_topology_diagram",
    description="Generates visual architecture and network topology diagrams in Graphviz DOT and Mermaid formats from a site model.",
    manual_schema={
        "type": "object",
        "properties": {
            "site_model": {"type": "string", "description": "Path to site model directory (e.g. 'sites/udmi_site_model'). Default is 'sites/udmi_site_model'."},
            "focus_device": {"type": "string", "description": "Optional device ID to highlight in the topology diagram."},
            "format": {"type": "string", "enum": ["dot", "mermaid", "both"], "description": "Diagram format to generate ('dot', 'mermaid', or 'both'). Default is 'both'."},
        },
    },
)
def _tool_generate_topology_diagram(
    site_model: str = "sites/udmi_site_model",
    focus_device: Optional[str] = None,
    format: str = "both",
    udmi_root: Optional[str] = None,
):
    from mantis.tools.visualization import generate_topology_diagram
    return generate_topology_diagram(site_model=site_model, focus_device=focus_device, format=format, udmi_root=udmi_root)


@register_tool(
    name="generate_sequence_diagram",
    description="Generates sequence diagrams in Graphviz DOT and Mermaid from test execution timeline logs.",
    manual_schema={
        "type": "object",
        "required": ["run_dir"],
        "properties": {
            "run_dir": {"type": "string", "description": "Path to test run directory containing sequence.log, pubber.log, etc."},
            "title": {"type": "string", "description": "Optional title for the sequence diagram."},
            "format": {"type": "string", "enum": ["dot", "mermaid", "both"], "description": "Diagram format to generate ('dot', 'mermaid', or 'both'). Default is 'both'."},
        },
    },
)
def _tool_generate_sequence_diagram(
    run_dir: str,
    title: Optional[str] = None,
    format: str = "both",
    udmi_root: Optional[str] = None,
):
    from mantis.tools.visualization import generate_sequence_diagram
    return generate_sequence_diagram(run_dir=run_dir, title=title, format=format, udmi_root=udmi_root)


@register_tool(
    name="render_dot_to_svg",
    description="Compiles Graphviz DOT syntax to SVG using the local /usr/bin/dot utility.",
    manual_schema={
        "type": "object",
        "required": ["dot_content"],
        "properties": {
            "dot_content": {"type": "string", "description": "Valid Graphviz DOT graph definition string."},
        },
    },
)
def _tool_render_dot_to_svg(dot_content: str):
    from mantis.tools.visualization import render_dot_to_svg
    return render_dot_to_svg(dot_content=dot_content)


# ------------------------------------------------------------------------------
# Dispatch & Schema Inspection
# ------------------------------------------------------------------------------

def execute_tool(
    name: str,
    args: Dict[str, Any],
    session_mgr: Optional[SessionManager] = None,
    udmi_root: Optional[str] = None,
) -> Any:
    """Executes a registered tool with automatic Pydantic validation and dependency injection."""
    if name not in _REGISTERED_TOOLS:
        raise ValueError(f"Unknown tool: '{name}'")

    tool_def = _REGISTERED_TOOLS[name]
    func = tool_def.func

    if tool_def.request_model:
        validated = tool_def.request_model(**args)
        call_kwargs = validated.model_dump()
    else:
        call_kwargs = dict(args)

    sig = inspect.signature(func)
    if "session_mgr" in sig.parameters and (session_mgr is not None or "session_mgr" not in call_kwargs):
        call_kwargs["session_mgr"] = session_mgr or SessionManager(udmi_root=call_kwargs.get("udmi_root", udmi_root))
    if "udmi_root" in sig.parameters and (udmi_root is not None or "udmi_root" not in call_kwargs):
        call_kwargs["udmi_root"] = udmi_root

    filtered_kwargs = {}
    for param_name, param in sig.parameters.items():
        if param.kind == inspect.Parameter.VAR_KEYWORD:
            filtered_kwargs = call_kwargs
            break
        if param_name in call_kwargs:
            filtered_kwargs[param_name] = call_kwargs[param_name]

    return func(**filtered_kwargs)


def _clean_json_schema(raw_schema: Dict[str, Any]) -> Dict[str, Any]:
    """Inlines $defs and removes Pydantic-internal fields to ensure compatibility with GenAI and MCP."""
    import copy
    schema = copy.deepcopy(raw_schema)
    defs = schema.pop("$defs", {})
    schema.pop("title", None)

    def _resolve_refs(node: Any) -> Any:
        if isinstance(node, dict):
            if "$ref" in node:
                ref_key = node["$ref"].split("/")[-1]
                if ref_key in defs:
                    resolved = copy.deepcopy(defs[ref_key])
                    resolved.pop("title", None)
                    return _resolve_refs(resolved)
            return {k: _resolve_refs(v) for k, v in node.items() if k != "title"}
        elif isinstance(node, list):
            return [_resolve_refs(item) for item in node]
        return node

    return _resolve_refs(schema)


def get_mcp_tools() -> List[Dict[str, Any]]:
    """Returns all registered tool schemas formatted for MCP tools/list."""
    tools = []
    for name, tool_def in _REGISTERED_TOOLS.items():
        if tool_def.manual_schema:
            schema = _clean_json_schema(tool_def.manual_schema)
        elif tool_def.request_model:
            schema = _clean_json_schema(tool_def.request_model.model_json_schema())
        else:
            schema = {"type": "object", "properties": {}}

        tools.append({
            "name": name,
            "description": tool_def.description,
            "inputSchema": schema,
        })
    return tools


def get_tool_schemas() -> List[Dict[str, Any]]:
    """Returns tool schemas formatted for standard function declarations."""
    schemas = []
    for name, tool_def in _REGISTERED_TOOLS.items():
        if tool_def.manual_schema:
            params = _clean_json_schema(tool_def.manual_schema)
        elif tool_def.request_model:
            params = _clean_json_schema(tool_def.request_model.model_json_schema())
        else:
            params = {"type": "object", "properties": {}}

        schemas.append({
            "name": name,
            "description": tool_def.description,
            "parameters": params,
        })
    return schemas


def get_genai_tools() -> List[Any]:
    """Generates google.genai.types.Tool containing all FunctionDeclarations."""
    try:
        from google.genai import types
    except ImportError as e:
        raise RuntimeError(
            "google-genai package is required to generate GenAI tools but is not installed."
        ) from e

    function_declarations = []
    for schema in get_tool_schemas():
        try:
            fd = types.FunctionDeclaration(
                name=schema["name"],
                description=schema["description"],
                parameters=schema["parameters"],
            )
            function_declarations.append(fd)
        except Exception as e:
            raise RuntimeError(
                f"Failed to create FunctionDeclaration for tool '{schema.get('name')}': {e}"
            ) from e
    return [types.Tool(function_declarations=function_declarations)]


TOOL_SCHEMAS = get_tool_schemas()
