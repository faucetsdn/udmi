"""Multi-path artifact discovery, log slicing, timeline extraction, and bundle ingestion."""

import json
import os
import re
import shutil
import tarfile
import zipfile
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

from mantis.tools.site_models import sanitize_credentials


def _get_udmi_root(udmi_root: Optional[str] = None) -> str:
    if udmi_root is not None:
        return os.path.abspath(udmi_root)
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


# Top-level blocks a device itself populates in a UDMI state message.
#
# `configAcked` is deliberately absent: the backend injects it when echoing a
# config transaction, so a state payload containing only that proves the
# *backend* spoke, not the device. Treating it as a device reply is what let a
# device that never said anything be reported as one that replied badly.
DEVICE_AUTHORED_STATE_KEYS = frozenset({
    "timestamp", "version", "system", "pointset", "discovery",
    "gateway", "localnet", "blobset", "dispatch", "cloud",
})

# Payload keys known to be added by the backend rather than the device.
BACKEND_INJECTED_STATE_KEYS = frozenset({"configAcked", "upgraded_from"})

# The sequencer writes its verdict via SequenceBase.RESULT_FORMAT
# ("RESULT %s %s %s %s %s/%s %s"), e.g.
#   RESULT fail system broken_config STABLE 0/8 Timeout waiting for initial device state
# There is no colon. Matching "RESULT:" instead -- as this module used to --
# matches nothing any UDMI component emits, so every real run was read as
# having no verdict at all.
RESULT_PATTERN = re.compile(r"\bRESULT\s+(pass|fail|skip|errr)\b", re.IGNORECASE)


def device_response_evidence(run_dir: str) -> Dict[str, Any]:
    """Reports whether the device under test published any message of its own.

    This is deliberately agnostic to what the device *is*. A physical device that
    is unplugged and an emulator that was never started produce an identical
    signature on the wire: nothing arrives on the device's state topic. The
    question this answers is therefore "did the device respond", not "is pubber
    running" -- Mantis has no way to know which kind of device it is looking at,
    and a diagnosis that assumes one is wrong half the time.

    Returns the payload files inspected and the device-authored keys found, so a
    caller can quote the basis of the finding rather than assert it.
    """
    inspected: List[str] = []
    device_keys: set = set()
    backend_keys: set = set()

    if not run_dir or not os.path.isdir(run_dir):
        return {
            "device_responded": None,
            "reason": f"Run directory not available for inspection: {run_dir!r}",
            "payload_files_inspected": [],
            "device_authored_keys": [],
            "backend_injected_keys": [],
        }

    for filename in sorted(os.listdir(run_dir)):
        # Only messages the DEVICE publishes count. `config_update.json` and
        # `local_update.json` travel cloud-to-device and carry the very same
        # blocks (system, pointset, ...), so counting them as evidence of a
        # reply reports a silent device as a responsive one -- the exact error
        # this function exists to prevent.
        if not (filename.startswith(("state", "events"))
                and filename.endswith(".json")):
            continue
        full = os.path.join(run_dir, filename)
        try:
            with open(full, encoding="utf-8") as handle:
                payload = json.load(handle)
        except (OSError, ValueError):
            # An unreadable payload is not evidence of silence; skip it rather
            # than let a parse error masquerade as a quiet device.
            continue
        inspected.append(filename)
        if not isinstance(payload, dict):
            continue
        device_keys |= (set(payload) & DEVICE_AUTHORED_STATE_KEYS)
        backend_keys |= (set(payload) & BACKEND_INJECTED_STATE_KEYS)

    if not inspected:
        reason = (
            "No message payloads were captured in the run directory, so the "
            "device published nothing the sequencer could record."
        )
        responded = False
    elif device_keys:
        reason = (
            "Device-authored content present in "
            f"{', '.join(inspected)}: {', '.join(sorted(device_keys))}."
        )
        responded = True
    else:
        detail = f" Only backend-injected fields were present: {', '.join(sorted(backend_keys))}." if backend_keys else ""
        reason = (
            f"Payloads were captured ({', '.join(inspected)}) but none carried any "
            f"device-authored block ({', '.join(sorted(DEVICE_AUTHORED_STATE_KEYS))})."
            f"{detail}"
        )
        responded = False

    return {
        "device_responded": responded,
        "reason": reason,
        "payload_files_inspected": inspected,
        "device_authored_keys": sorted(device_keys),
        "backend_injected_keys": sorted(backend_keys),
    }


def discover_test_runs(
    base_dir: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """Discover available test run directories and log artifacts."""
    root = _get_udmi_root(udmi_root)
    search_dirs = []
    if base_dir:
        search_dirs.append(os.path.abspath(base_dir))
    else:
        search_dirs.extend([
            os.path.join(root, "out", "runs"),
            os.path.join(root, "out"),
            os.path.join(root, "var", "instances"),
        ])

    runs = []
    for sdir in search_dirs:
        if not os.path.isdir(sdir):
            continue
        for entry in sorted(os.listdir(sdir)):
            full_path = os.path.join(sdir, entry)
            if os.path.isdir(full_path):
                # Check for log files
                log_files = [f for f in os.listdir(full_path) if f.endswith(".log") or f.endswith(".out")]
                if log_files or os.path.isfile(os.path.join(full_path, "session_info.json")):
                    runs.append({
                        "run_id": entry,
                        "path": full_path,
                        "logs": log_files,
                    })
    return runs


def extract_log_slice(
    log_file: str,
    test_id: Optional[str] = None,
    start_pattern: Optional[str] = None,
    end_pattern: Optional[str] = None,
    max_lines: int = 1000,
) -> List[str]:
    """Slice raw log content to lines matching test execution bounds."""
    if not os.path.isfile(log_file):
        return []

    lines = []
    try:
        with open(log_file, "r", encoding="utf-8", errors="replace") as f:
            all_lines = f.readlines()
    except Exception:
        return []

    in_slice = (test_id is None and start_pattern is None)
    for line in all_lines:
        line_str = line.rstrip("\n")
        if not in_slice:
            if test_id and test_id in line_str and ("Starting test" in line_str or "start_test" in line_str):
                in_slice = True
            elif start_pattern and re.search(start_pattern, line_str):
                in_slice = True

        if in_slice:
            lines.append(line_str)
            if len(lines) >= max_lines:
                break
            if test_id and test_id in line_str and (RESULT_PATTERN.search(line_str) or "terminating_test" in line_str or "finished test" in line_str):
                break
            elif end_pattern and re.search(end_pattern, line_str):
                break

    if not lines and all_lines:
        # Fallback to last max_lines if no explicit slice bounds were found
        lines = [l.rstrip("\n") for l in all_lines[-max_lines:]]

    return lines


def _extract_multiline_block(raw_lines: List[str], start_idx: int, max_lines: int = 15) -> str:
    """Extract a multi-line Java exception stack trace, Caused by block, or structured error output."""
    if start_idx >= len(raw_lines):
        return ""
    
    block = [raw_lines[start_idx].rstrip("\r\n")]
    for j in range(start_idx + 1, min(len(raw_lines), start_idx + max_lines)):
        line = raw_lines[j].rstrip("\r\n")
        stripped = line.strip()
        if not stripped:
            break
        # Lines that belong to stack traces, causes, references, or source locations
        if (
            line.startswith("\t")
            or line.startswith("  ")
            or stripped.startswith("at ")
            or stripped.startswith("Caused by:")
            or stripped.startswith("... ")
            or "line:" in stripped.lower()
            or "column:" in stripped.lower()
            or "(through reference chain:" in stripped
            or "expected" in stripped.lower()
            or "schema validation" in stripped.lower()
        ):
            block.append(stripped)
            if "Starting test" in stripped or RESULT_PATTERN.search(stripped):
                break
        else:
            break
    return "\n".join(block)


def extract_timeline(
    test_id: str,
    device_id: str,
    run_dir: Optional[str] = None,
    udmi_root: Optional[str] = None,
    site_model: Optional[str] = None,
) -> Dict[str, Any]:
    """Deterministically extracts chronological timestamps, transactions (RC:...), cutoffs, and multi-line status transitions.

    When run_dir is None, only directories specific to this device and test are
    considered: out/runs/<device>_<test>, out/runs/<test>, and, when site_model is
    given, <site_model>/out/devices/<device>/tests/<test>. Anything broader (the
    repository's out/ or an arbitrary var/instances/* session) would harvest logs
    from an unrelated run, so a miss raises FileNotFoundError naming every path tried.
    """
    root = _get_udmi_root(udmi_root)

    # Locate run directory
    target_dir = None
    if run_dir is not None:
        resolved_run_dir = run_dir if os.path.isabs(run_dir) else os.path.join(root, run_dir)
        resolved_run_dir = os.path.abspath(resolved_run_dir)
        if not os.path.isdir(resolved_run_dir):
            raise FileNotFoundError(f"Run directory not found: '{run_dir}'")
        target_dir = resolved_run_dir
    else:
        candidates = [
            os.path.join(root, "out", "runs", f"{device_id}_{test_id}"),
            os.path.join(root, "out", "runs", test_id),
        ]
        if site_model:
            site_path = site_model if os.path.isabs(site_model) else os.path.join(root, site_model)
            candidates.append(
                os.path.join(os.path.abspath(site_path), "out", "devices", device_id, "tests", test_id)
            )

        for c in candidates:
            if os.path.isdir(c):
                target_dir = c
                break

        if not target_dir:
            searched = ", ".join(f"'{c}'" for c in candidates)
            raise FileNotFoundError(
                f"No run directory found for test_id='{test_id}', device_id='{device_id}'. "
                f"Searched: {searched}. Pass run_dir explicitly"
                + ("." if site_model else " or supply site_model.")
            )

    events: List[Dict[str, Any]] = []
    transactions: List[str] = []
    cutoff_threshold: Optional[str] = None
    stale_state_detected = False
    stale_state_timestamp: Optional[str] = None
    jackson_error: Optional[str] = None
    timeout_error: Optional[str] = None
    pointset_timestamps: List[str] = []
    result_status = "UNKNOWN"

    transport_error: Optional[str] = None
    auth_error: Optional[str] = None
    schema_error: Optional[str] = None
    gateway_error: Optional[str] = None
    broker_congestion: Optional[str] = None
    gateway_tx_missing_echo: Optional[str] = None

    log_files = {
        "sequence": os.path.join(target_dir, "sequence.log"),
        "pubber": os.path.join(target_dir, "pubber.log"),
        "device_system": os.path.join(target_dir, "device_system.log"),
        "udmis": os.path.join(target_dir, "udmis.log"),
        "validator": os.path.join(target_dir, "validator.log"),
    }

    # Regex patterns
    rc_pattern = re.compile(r"RC:([a-zA-Z0-9_\-\.]+)")
    cutoff_pattern = re.compile(r"(?:Cutoff set:|Setting (?:state )?cutoff to|cutoff threshold is)\s*([0-9T:\-\.Z]+)", re.IGNORECASE)
    stale_pattern = re.compile(r"ignoring stale state update(?:\s+timestamp\s+)?([0-9T:\-\.Z]+)?", re.IGNORECASE)
    jackson_pattern = re.compile(r"(?:UnrecognizedPropertyException|JsonParseException|JsonMappingException|MismatchedInputException|Cannot deserialize|Jackson error|InvalidFormatException)", re.IGNORECASE)
    timeout_pattern = re.compile(r"(?:Stage timeout after|Timeout waiting for|timed out after|TimeoutException|AssertionError:\s*Sequence failed)", re.IGNORECASE)
    transport_pattern = re.compile(r"(?:ConnectionRefusedError|ConnectException|SSLHandshakeException|SSL_ERROR|Connection refused(?!:\s*not author)|Broker unreachable|MqttException)", re.IGNORECASE)
    auth_pattern = re.compile(r"(?:Bad username or password|Not authorized to connect|Connection Refused:\s*not authorised|Authentication failure|NotAuthorizedException)", re.IGNORECASE)
    schema_pattern = re.compile(r"(?:Schema validation error|missing required (?:point|property)|point not defined in model|Pointset schema violation)", re.IGNORECASE)
    gateway_pattern = re.compile(r"(?:gateway_proxy_error|CRC error|serial frame error|RS-485 framing error|bus timeout|field bus error)", re.IGNORECASE)
    congestion_pattern = re.compile(r"(?:inFlight tokens|broker congestion|socket drop|queue full|client queue overflow)", re.IGNORECASE)
    gateway_tx_missing_echo_pattern = re.compile(r"(?:omitted sub-device echo|missing proxy sub-device echo|gateway dropped proxy state)", re.IGNORECASE)
    stage_wait_pattern = re.compile(r"(?:Waiting for (?:config sync|state update|telemetry|device state|condition)|Starting stage|Stage wait\b)", re.IGNORECASE)
    ts_pattern = re.compile(r"([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]+)?Z?)")

    raw_events: List[Tuple[float, int, int, Dict[str, Any]]] = []
    logs_found: List[str] = []
    logs_missing: List[str] = []

    for file_order, (log_name, log_path) in enumerate(log_files.items()):
        if not os.path.isfile(log_path):
            logs_missing.append(log_name)
            continue
        logs_found.append(log_name)

        with open(log_path, "r", encoding="utf-8", errors="replace") as f:
            raw_lines = f.readlines()

        last_known_ts = 0.0
        for line_no, raw_line in enumerate(raw_lines, 1):
            line = raw_line.strip()
            if not line:
                continue

            # Look for timestamps in line
            ts_match = ts_pattern.search(line)
            line_ts = ts_match.group(1) if ts_match else None
            if line_ts:
                try:
                    clean_ts = line_ts.rstrip("Z")
                    last_known_ts = datetime.fromisoformat(clean_ts).timestamp()
                except Exception:
                    pass
            event_time = last_known_ts if last_known_ts > 0 else (line_no * 0.00001)

            # Look for transactions
            for m in rc_pattern.finditer(line):
                rc_id = f"RC:{m.group(1)}"
                if rc_id not in transactions:
                    transactions.append(rc_id)

            # Check for test start
            if ("Starting test" in line or "start_test" in line) and test_id in line:
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "TEST_START",
                        "timestamp": line_ts,
                        "description": f"Starting test {test_id} for {device_id}",
                    }
                ))

            # Check for config dispatch
            if "Dispatched config" in line or "Publishing config" in line or "Sending config" in line:
                m = rc_pattern.search(line)
                rc_str = f" ({m.group(0)})" if m else ""
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "CONFIG_DISPATCH",
                        "timestamp": line_ts,
                        "description": f"Dispatched config update{rc_str}",
                    }
                ))

            # Check for stage wait start
            if stage_wait_pattern.search(line):
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "STAGE_WAIT_START",
                        "timestamp": line_ts,
                        "description": f"Waiting for stage sync: {line[:80]}",
                    }
                ))

            # Check for state cutoff
            c_match = cutoff_pattern.search(line)
            if c_match:
                cutoff_threshold = c_match.group(1)
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "STATE_CUTOFF_SET",
                        "timestamp": line_ts,
                        "cutoff": cutoff_threshold,
                        "description": f"Sequencer cutoff set to {cutoff_threshold}",
                    }
                ))

            # Check for state received
            if "Received state" in line or "Handling device state" in line:
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "STATE_RECEIVED",
                        "timestamp": line_ts,
                        "description": f"Received device state update",
                    }
                ))

            # Check for stale state.
            #
            # Only an actual rejection counts. Every run opens by announcing its
            # threshold ("Stale state cutoff threshold is <ts>"), so matching the
            # bare phrase "stale state" flagged a rejection on every single run --
            # including runs where the device never sent a state at all. That false
            # positive is what let a silent device be reported as a late one.
            s_match = stale_pattern.search(line)
            if s_match:
                stale_state_detected = True
                if s_match.group(1):
                    stale_state_timestamp = s_match.group(1)
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "STALE_STATE_IGNORED",
                        "timestamp": line_ts,
                        "description": "Sequencer ignored stale state update (lagging cutoff)",
                    }
                ))

            # Check for Jackson parse error (including multi-line / Caused by)
            if (jackson_pattern.search(line) or "caused by: com.fasterxml.jackson" in line.lower()) and not jackson_error:
                jackson_block = _extract_multiline_block(raw_lines, line_no - 1)
                jackson_error = jackson_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "JACKSON_DESERIALIZATION_FAILURE",
                        "timestamp": line_ts,
                        "description": f"Jackson parser error: {line[:80]}",
                    }
                ))

            # Check for timeout error
            if timeout_pattern.search(line) and not timeout_error:
                timeout_block = _extract_multiline_block(raw_lines, line_no - 1, max_lines=6)
                timeout_error = timeout_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "TIMEOUT_FAILURE",
                        "timestamp": line_ts,
                        "description": f"Timeout failure: {line[:80]}",
                    }
                ))

            # Check for transport error
            if transport_pattern.search(line) and not transport_error:
                tr_block = _extract_multiline_block(raw_lines, line_no - 1, max_lines=6)
                transport_error = tr_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "TRANSPORT_FAILURE",
                        "timestamp": line_ts,
                        "description": f"Transport failure: {line[:80]}",
                    }
                ))

            # Check for auth error
            if auth_pattern.search(line) and not auth_error:
                auth_block = _extract_multiline_block(raw_lines, line_no - 1, max_lines=6)
                auth_error = auth_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "AUTH_FAILURE",
                        "timestamp": line_ts,
                        "description": f"Authentication failure: {line[:80]}",
                    }
                ))

            # Check for schema error
            if schema_pattern.search(line) and not schema_error:
                schema_block = _extract_multiline_block(raw_lines, line_no - 1, max_lines=6)
                schema_error = schema_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "SCHEMA_VALIDATION_FAILURE",
                        "timestamp": line_ts,
                        "description": f"Schema validation error: {line[:80]}",
                    }
                ))

            # Check for gateway proxy / field bus error
            if gateway_pattern.search(line) and not gateway_error:
                gw_block = _extract_multiline_block(raw_lines, line_no - 1, max_lines=6)
                gateway_error = gw_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "GATEWAY_BUS_ERROR",
                        "timestamp": line_ts,
                        "description": f"Gateway/Field bus error: {line[:80]}",
                    }
                ))

            # Check for broker / transport congestion
            if congestion_pattern.search(line) and not broker_congestion:
                cg_block = _extract_multiline_block(raw_lines, line_no - 1, max_lines=6)
                broker_congestion = cg_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "BROKER_CONGESTION",
                        "timestamp": line_ts,
                        "description": f"Broker congestion: {line[:80]}",
                    }
                ))

            # Check for gateway missing echo
            if gateway_tx_missing_echo_pattern.search(line) and not gateway_tx_missing_echo:
                echo_block = _extract_multiline_block(raw_lines, line_no - 1, max_lines=6)
                gateway_tx_missing_echo = echo_block or line
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "GATEWAY_MISSING_ECHO",
                        "timestamp": line_ts,
                        "description": f"Gateway state missing sub-device echo: {line[:80]}",
                    }
                ))

            # Check for pointset events (for cadence calculation)
            if "events/pointset" in line or "events_pointset" in line or "pointset telemetry" in line.lower():
                if line_ts:
                    pointset_timestamps.append(line_ts)

            # Check for test result. A missed verdict let the synthesis fall
            # through to its weakest branch and describe a failed run as merely
            # "ended", so this must track the sequencer's real output format
            # (see RESULT_PATTERN).
            verdict_match = RESULT_PATTERN.search(line)
            if verdict_match:
                verdict = verdict_match.group(1).upper()
                result_status = "PASS" if verdict == "PASS" else "FAIL"
                raw_events.append((
                    event_time, file_order, line_no,
                    {
                        "step": 0,
                        "source": log_name,
                        "line": line_no,
                        "checkpoint": "TEST_RESULT",
                        "timestamp": line_ts,
                        "status": result_status,
                        "result": result_status,
                        "description": line.strip(),
                    }
                ))

    # Chronological sort across all log files and re-indexing
    raw_events.sort(key=lambda x: (x[0], x[1], x[2]))
    events: List[Dict[str, Any]] = [item[3] for item in raw_events]
    for idx, ev in enumerate(events, 1):
        ev["step"] = idx

    # Cadence calculation
    avg_sample_rate_sec = None
    if len(pointset_timestamps) >= 2:
        try:
            parsed_ts = []
            for t in pointset_timestamps:
                clean_t = t.rstrip("Z")
                dt = datetime.fromisoformat(clean_t)
                parsed_ts.append(dt)
            deltas = [(parsed_ts[i+1] - parsed_ts[i]).total_seconds() for i in range(len(parsed_ts)-1)]
            if deltas:
                avg_sample_rate_sec = sum(deltas) / len(deltas)
        except Exception:
            pass

    # Whether the device said anything at all is the first thing any diagnosis
    # needs, and it cannot be inferred from the absence of error strings: a
    # device that never connects logs no transport error either.
    response = device_response_evidence(target_dir)

    # A rejection is itself proof of a transmission. If the sequencer logged
    # discarding a device state update as stale, the device sent one, whether or
    # not it survived into the run directory. Leaving the payload-derived
    # verdict untouched here would let the timeline assert both that the device
    # said nothing and that its state was rejected.
    if response["device_responded"] is not True and stale_state_detected:
        rejected = stale_state_timestamp or "an unrecorded timestamp"
        response = dict(response)
        response["device_responded"] = True
        response["reason"] = (
            f"Sequencer logged rejecting a device state update ({rejected}) as stale, which "
            f"requires the device to have sent one. Payload evidence: {response['reason']}"
        )

    return {
        "status": "SUCCESS",
        "test_id": test_id,
        "device_id": device_id,
        "run_dir": target_dir,
        "logs_found": logs_found,
        "logs_missing": logs_missing,
        "result": result_status,
        "transactions": transactions,
        "cutoff_threshold": cutoff_threshold,
        "stale_state_detected": stale_state_detected,
        "stale_state_timestamp": stale_state_timestamp,
        "device_responded": response["device_responded"],
        "device_response_evidence": response["reason"],
        "device_authored_keys": response["device_authored_keys"],
        "payload_files_inspected": response["payload_files_inspected"],
        "jackson_error": jackson_error,
        "timeout_error": timeout_error,
        "transport_error": transport_error,
        "auth_error": auth_error,
        "schema_error": schema_error,
        "gateway_error": gateway_error,
        "broker_congestion": broker_congestion,
        "gateway_tx_missing_echo": gateway_tx_missing_echo,
        "avg_sample_rate_sec": avg_sample_rate_sec,
        "events": events,
    }


def sanitize_extracted_bundle_directory(bundle_dir: str) -> Dict[str, int]:
    """Recursively sanitizes private keys, passwords, and tokens in an extracted bundle."""
    stats = {"keys_stripped": 0, "json_sanitized": 0, "logs_sanitized": 0, "errors": []}
    if not os.path.isdir(bundle_dir):
        return stats

    for root, _, files in os.walk(bundle_dir):
        for fname in files:
            fpath = os.path.join(root, fname)
            f_lower = fname.lower()

            # 1. Strip RSA/EC Private Key files (.pem, .key, .pkcs8, or containing private key blocks)
            if f_lower.endswith((".pem", ".key", ".pkcs8")) or f_lower in ("rsa_private.crt", "private_key.json"):
                try:
                    with open(fpath, "w", encoding="utf-8") as f:
                        f.write("[REDACTED_RSA_KEY]\n")
                    stats["keys_stripped"] += 1
                except Exception as e:
                    stats["errors"].append(f"Failed to redact key file {fpath}: {e}")
                continue

            # 2. Sanitize JSON files (cloud_iot_config.json, metadata.json, etc.)
            if f_lower.endswith(".json"):
                try:
                    with open(fpath, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    sanitized = sanitize_credentials(data)
                    with open(fpath, "w", encoding="utf-8") as f:
                        json.dump(sanitized, f, indent=2)
                        f.write("\n")
                    stats["json_sanitized"] += 1
                except Exception as e:
                    stats["errors"].append(f"Failed to sanitize JSON file {fpath}: {e}")
                continue

            # 3. Sanitize Log and Text files
            if f_lower.endswith((".log", ".out", ".txt")):
                try:
                    with open(fpath, "r", encoding="utf-8", errors="replace") as f:
                        content = f.read()

                    # Strip PEM private key blocks
                    sanitized = re.sub(
                        r"-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----[\s\S]*?-----END (?:[A-Z ]+)?PRIVATE KEY-----",
                        "[REDACTED_RSA_KEY]",
                        content,
                    )
                    # Mask URL credentials
                    sanitized = re.sub(r"://([^:]+):([^@]+)@", r"://\1:***@", sanitized)
                    # Mask GCP bearer / OAuth tokens
                    sanitized = re.sub(r"ya29\.[a-zA-Z0-9_\-]+", "[REDACTED_GCP_TOKEN]", sanitized)
                    # Mask GCP private service account keys
                    sanitized = re.sub(r'"private_key"\s*:\s*"[^"]+"', '"private_key": "***REDACTED***"', sanitized)

                    if sanitized != content:
                        with open(fpath, "w", encoding="utf-8") as f:
                            f.write(sanitized)
                        stats["logs_sanitized"] += 1
                except Exception as e:
                    stats["errors"].append(f"Failed to sanitize log/text file {fpath}: {e}")

    return stats


def ingest_support_bundle(
    bundle_path: str,
    extract_to: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Ingests a support bundle zip or tarball, extracts manifest and logs, and redacts credentials."""
    root = _get_udmi_root(udmi_root)
    bundle_file = os.path.abspath(bundle_path)
    if not os.path.isfile(bundle_file):
        return {
            "status": "ERROR",
            "error": f"Support bundle file not found: {bundle_path}",
        }

    if extract_to is None:
        extract_to = os.path.join(root, "out", "extracted_bundles", os.path.basename(bundle_file).replace(".", "_"))
    os.makedirs(extract_to, exist_ok=True)

    # Extract with path traversal / Zip Slip prevention
    resolved_extract_to = os.path.realpath(extract_to)
    if zipfile.is_zipfile(bundle_file):
        with zipfile.ZipFile(bundle_file, "r") as z:
            for member in z.infolist():
                target_path = os.path.realpath(os.path.join(resolved_extract_to, member.filename))
                if target_path != resolved_extract_to and not target_path.startswith(resolved_extract_to + os.sep):
                    return {
                        "status": "ERROR",
                        "error": f"Security violation: Archive member '{member.filename}' attempts directory traversal outside extraction directory.",
                    }
            z.extractall(extract_to)
    elif tarfile.is_tarfile(bundle_file):
        with tarfile.open(bundle_file, "r:*") as t:
            for member in t.getmembers():
                target_path = os.path.realpath(os.path.join(resolved_extract_to, member.name))
                if target_path != resolved_extract_to and not target_path.startswith(resolved_extract_to + os.sep):
                    return {
                        "status": "ERROR",
                        "error": f"Security violation: Archive member '{member.name}' attempts directory traversal outside extraction directory.",
                    }
            t.extractall(extract_to)
    else:
        return {
            "status": "ERROR",
            "error": f"Unsupported bundle format for {bundle_file}. Expected .zip, .tar.gz, or .tgz",
        }

    # Sanitize all extracted files (private keys, passwords, bearer tokens)
    sanitization_stats = sanitize_extracted_bundle_directory(extract_to)

    # Search for triage_manifest.json
    manifest = {}
    manifest_file = os.path.join(extract_to, "triage_manifest.json")
    if not os.path.isfile(manifest_file):
        # Check subdirectories
        for r, _, files in os.walk(extract_to):
            if "triage_manifest.json" in files:
                manifest_file = os.path.join(r, "triage_manifest.json")
                break

    if os.path.isfile(manifest_file):
        try:
            with open(manifest_file, "r", encoding="utf-8") as f:
                manifest = sanitize_credentials(json.load(f))
        except Exception as e:
            manifest = {"error": f"Failed to parse triage_manifest.json: {e}"}

    # Find logs directory
    logs_found = []
    for r, _, files in os.walk(extract_to):
        for f in files:
            if f.endswith(".log") or f.endswith(".out"):
                logs_found.append(os.path.relpath(os.path.join(r, f), extract_to))

    return {
        "status": "SUCCESS",
        "bundle_file": bundle_file,
        "extracted_dir": extract_to,
        "manifest": manifest,
        "logs": logs_found,
        "sanitization": sanitization_stats,
    }


def inspect_message_trace(
    run_dir: str,
    message_type: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Inspects recorded MQTT message payloads (events, state, config, validation) captured during test execution."""
    root = _get_udmi_root(udmi_root)
    target_dir = run_dir if os.path.isabs(run_dir) else os.path.join(root, run_dir)
    target_dir = os.path.abspath(target_dir)

    if not os.path.isdir(target_dir):
        return {
            "status": "ERROR",
            "error": f"Run directory not found: '{run_dir}'",
        }

    trace_files = []
    # Search run_dir and its subdirectories for JSON message payloads
    for dirpath, _, filenames in os.walk(target_dir):
        for f in filenames:
            if not f.endswith(".json") or f == "triage_manifest.json":
                continue
            # Check for UDMI message trace categories
            f_lower = f.lower()
            if any(
                cat in f_lower
                for cat in (
                    "events", "state", "config", "pointset", "system", "discovery",
                    "validation", "alarmset", "localnet", "blobset", "model"
                )
            ):
                trace_files.append(os.path.join(dirpath, f))

    if not trace_files:
        return {
            "status": "SUCCESS",
            "run_dir": run_dir,
            "traces_count": 0,
            "traces": [],
            "message": f"No message trace JSON files found in '{run_dir}'.",
        }

    results = []
    for tf in sorted(trace_files):
        fname = os.path.basename(tf)
        if message_type and message_type.lower() not in fname.lower():
            continue

        rel_path = os.path.relpath(tf, root)
        try:
            with open(tf, "r", encoding="utf-8", errors="replace") as f:
                content = f.read().strip()
            parsed = json.loads(content)
            results.append({
                "filename": fname,
                "file_path": rel_path,
                "payload": parsed,
            })
        except Exception as e:
            results.append({
                "filename": fname,
                "file_path": rel_path,
                "error": f"Failed to parse JSON: {e}",
            })

    return {
        "status": "SUCCESS",
        "run_dir": run_dir,
        "traces_count": len(results),
        "traces": results,
    }


def detect_log_anomalies(
    run_dir: str,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Scans test execution logs for timing anomalies, deserialization errors, framing drops, and broker issues."""
    root = _get_udmi_root(udmi_root)
    target_dir = run_dir if os.path.isabs(run_dir) else os.path.join(root, run_dir)
    target_dir = os.path.abspath(target_dir)

    if not os.path.isdir(target_dir):
        return {
            "status": "ERROR",
            "error": f"Run directory not found: '{run_dir}'",
        }

    log_candidates = {
        "sequence": ["sequence.log", "out/sequence.log"],
        "pubber": ["pubber.log", "out/pubber.log"],
        "device_system": ["device_system.log", "out/device_system.log"],
        "udmis": ["udmis.log", "out/udmis.log"],
        "validator": ["validator.log", "out/validator.log"],
    }

    # Anomaly patterns with category, severity, and regex
    anomaly_definitions = [
        # Timing & Cutoffs
        {
            "category": "STALE_STATE_CUTOFF",
            "severity": "HIGH",
            "pattern": re.compile(r"ignoring stale state update(?:\s+timestamp\s+)?([0-9T:\-\.Z]+)?", re.IGNORECASE),
            "description": "Device state update timestamp was older than the sequencer cutoff threshold.",
        },
        {
            "category": "STAGE_TIMEOUT",
            "severity": "HIGH",
            "pattern": re.compile(r"(?:Stage timeout after|Timeout waiting for|timed out after|TimeoutException|AssertionError:\s*Sequence failed)", re.IGNORECASE),
            "description": "Sequencer stage timed out waiting for device condition or state sync.",
        },
        # Serialization & Schema
        {
            "category": "JACKSON_DESERIALIZATION",
            "severity": "HIGH",
            "pattern": re.compile(r"(?:UnrecognizedPropertyException|JsonParseException|JsonMappingException|MismatchedInputException|Cannot deserialize|InvalidFormatException)", re.IGNORECASE),
            "description": "Jackson JSON deserialization failure (unrecognized field or mismatched property type).",
        },
        {
            "category": "SCHEMA_VIOLATION",
            "severity": "HIGH",
            "pattern": re.compile(r"(?:Schema validation error|missing required (?:point|property)|point not defined in model|Pointset schema violation)", re.IGNORECASE),
            "description": "Schema validation failure against authoritative UDMI schema definition.",
        },
        # Fieldbus & Gateway
        {
            "category": "FIELDBUS_ERROR",
            "severity": "HIGH",
            "pattern": re.compile(r"(?:gateway_proxy_error|CRC error|serial frame error|RS-485 framing error|bus timeout|field bus error)", re.IGNORECASE),
            "description": "Field bus framing, CRC, or serial communication error between gateway and sub-device.",
        },
        {
            "category": "GATEWAY_OMITTED_ECHO",
            "severity": "HIGH",
            "pattern": re.compile(r"(?:omitted sub-device echo|missing proxy sub-device echo|gateway dropped proxy state)", re.IGNORECASE),
            "description": "Gateway omitted sub-device state echo in aggregated gateway state update.",
        },
        # Transport & Network
        {
            "category": "TRANSPORT_CONNECTION_REFUSED",
            "severity": "HIGH",
            "pattern": re.compile(r"(?:ConnectionRefusedError|ConnectException|Connection refused(?!:\s*not author)|Broker unreachable|MqttException)", re.IGNORECASE),
            "description": "Network transport failure or MQTT broker connection refused.",
        },
        {
            "category": "AUTHENTICATION_FAILURE",
            "severity": "HIGH",
            "pattern": re.compile(r"(?:Bad username or password|Not authorized to connect|Connection Refused:\s*not authorised|Authentication failure|NotAuthorizedException)", re.IGNORECASE),
            "description": "MQTT client credentials or TLS certificate rejected by broker.",
        },
        {
            "category": "BROKER_CONGESTION",
            "severity": "MEDIUM",
            "pattern": re.compile(r"(?:inFlight tokens|broker congestion|socket drop|queue full|client queue overflow)", re.IGNORECASE),
            "description": "Broker or client queue overflow; inFlight token starvation detected.",
        },
    ]

    ts_pattern = re.compile(r"([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]+)?Z?)")
    anomalies = []

    for log_name, candidate_rel_paths in log_candidates.items():
        log_file = None
        for rel in candidate_rel_paths:
            candidate_path = os.path.join(target_dir, rel)
            if os.path.isfile(candidate_path):
                log_file = candidate_path
                break

        if not log_file:
            continue

        try:
            with open(log_file, "r", encoding="utf-8", errors="replace") as f:
                lines = f.readlines()
        except Exception:
            continue

        for line_no, raw_line in enumerate(lines, 1):
            line = raw_line.strip()
            if not line:
                continue

            ts_m = ts_pattern.search(line)
            timestamp = ts_m.group(1) if ts_m else None

            for adef in anomaly_definitions:
                if adef["pattern"].search(line):
                    # Multi-line context for exceptions
                    context_snippet = line
                    if "exception" in line.lower() or "error" in line.lower():
                        extra = [lines[i].strip() for i in range(line_no, min(len(lines), line_no + 3))]
                        context_snippet = "\n".join([line] + [x for x in extra if x])

                    anomalies.append({
                        "category": adef["category"],
                        "severity": adef["severity"],
                        "source_log": os.path.basename(log_file),
                        "line": line_no,
                        "timestamp": timestamp,
                        "message": line[:160],
                        "description": adef["description"],
                        "context": context_snippet[:400],
                    })
                    break

    summary = f"Detected {len(anomalies)} anomalies across logs in '{run_dir}'."
    if anomalies:
        categories: Dict[str, int] = {}
        for a in anomalies:
            categories[a["category"]] = categories.get(a["category"], 0) + 1
        summary += " Breakdown: " + ", ".join(f"{k}: {v}" for k, v in categories.items())

    return {
        "status": "SUCCESS",
        "run_dir": run_dir,
        "total_anomalies": len(anomalies),
        "anomalies": anomalies,
        "summary": summary,
    }
