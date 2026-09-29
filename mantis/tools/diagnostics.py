"""Pure deterministic diagnostic analysis and failure triage tool adapter."""

import json
import os
import re
from typing import Any, Dict, List, Optional, Tuple

from mantis.models import ClaimStatus, DiagnosticResult, HypothesisEvaluation, VerificationClaim
from mantis.tools.artifacts import discover_test_runs, extract_timeline
from mantis.tools.site_models import inspect_site_model, resolve_site_model_path


def _reprobe_metadata_json(site_model_path: str, device_id: str) -> Tuple[bool, Optional[str]]:
    """Active re-probing helper: inspects metadata.json with strict json parser to check line/column syntax errors."""
    meta_path = os.path.join(site_model_path, "devices", device_id, "metadata.json")
    if not os.path.isfile(meta_path):
        return False, f"metadata.json missing at {meta_path}"
    try:
        with open(meta_path, "r", encoding="utf-8") as f:
            json.load(f)
        return True, None
    except json.JSONDecodeError as e:
        return False, f"JSONDecodeError at line {e.lineno}, col {e.colno}: {e.msg}"
    except Exception as e:
        return False, str(e)


def _sibling_device_run_dir(run_dir: Optional[str], device_id: str, other_id: str) -> Optional[str]:
    """Locate another device's run directory for the same test.

    Sequencer output is laid out as `.../devices/<device_id>/tests/<test_id>`,
    so a peer device's artifacts sit at the same path with the device segment
    swapped. Returns None when no such directory exists rather than falling
    back to the caller's directory: reading one device's payloads as another's
    is how a gateway that published nothing was reported as online.
    """
    if not run_dir or not device_id or not other_id or device_id == other_id:
        return None
    parts = os.path.normpath(run_dir).split(os.sep)
    try:
        index = len(parts) - 1 - parts[::-1].index(device_id)
    except ValueError:
        return None
    if index == 0 or parts[index - 1] != "devices":
        return None
    candidate = os.sep.join(parts[:index] + [other_id] + parts[index + 1:])
    return candidate if os.path.isdir(candidate) else None


def diagnose_test_failure(
    test_id: str,
    device_id: str,
    site_model: str = "sites/udmi_site_model",
    run_dir: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Deterministically extracts timelines, checks competing failure hypotheses,
    performs cognitive adversarial self-audit (Critic) with active re-probing,
    and synthesizes a verified diagnostic report."""
    if udmi_root is None:
        udmi_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

    # Phase 1: Evidence Harvesting & Deterministic Extraction
    timeline = extract_timeline(
        test_id=test_id,
        device_id=device_id,
        run_dir=run_dir,
        udmi_root=udmi_root,
        site_model=site_model,
    )
    site_info = inspect_site_model(
        site_model=site_model,
        device_id=device_id,
        udmi_root=udmi_root,
    )
    site_path = resolve_site_model_path(site_model, udmi_root)

    # Gateway / Proxy Inspection
    gw_info = site_info.get("gateway", {}) if isinstance(site_info.get("gateway"), dict) else {}
    gateway_id = gw_info.get("gateway_id")
    gateway_site_info = None
    gateway_timeline = None
    gateway_artifacts_note = None
    if gateway_id:
        gateway_site_info = inspect_site_model(
            site_model=site_model,
            device_id=gateway_id,
            udmi_root=udmi_root,
        )
        # The gateway must be judged on its own artifacts. Harvesting the
        # sub-device's run directory for it makes the sub-device's payloads
        # look like proof the gateway was alive, which is how a silent gateway
        # came to be described as "online and communicating".
        gateway_run_dir = _sibling_device_run_dir(timeline.get("run_dir"), device_id, gateway_id)
        if gateway_run_dir:
            gateway_timeline = extract_timeline(
                test_id=test_id,
                device_id=gateway_id,
                run_dir=gateway_run_dir,
                udmi_root=udmi_root,
            )
        else:
            gateway_artifacts_note = (
                f"No run directory of its own was found for gateway '{gateway_id}' alongside "
                f"{timeline.get('run_dir')!r}, so nothing about the gateway could be observed."
            )

    # Phase 2: Built-in Adversarial Self-Audit (Critic)
    matrix: List[Dict[str, str]] = []
    competing_hypotheses: Dict[str, Dict[str, str]] = {}

    def add_claim(hyp_name: str, claim: str, status: ClaimStatus, evidence: str) -> None:
        competing_hypotheses[hyp_name] = {
            "status": status.value,
            "evidence": evidence,
        }
        matrix.append({
            "claim": claim,
            "status": status.value,
            "evidence": evidence,
        })

    # Whether the device under test published anything at all. Deliberately
    # says nothing about what the device *is*: an unplugged physical device and
    # an emulator that was never started look identical here, and Mantis has no
    # way to tell them apart.
    device_responded = timeline.get("device_responded")
    device_reason = timeline.get("device_response_evidence") or "Device response not evaluated."
    logs_harvested = timeline.get("logs_found") or []

    def not_assessed(error_kind: str) -> str:
        """Evidence text for a hypothesis no observation bears on.

        A subsystem cannot be declared healthy because its errors are missing
        from a log. If the device never spoke, every subsystem downstream of it
        is equally silent, and reporting each one as "verified healthy" -- as
        this function replaced -- hands the reader a wall of fabricated
        all-clears that hides the one thing that did happen.
        """
        note = (
            f"Not assessed: no {error_kind} appears in the captured logs, which "
            "records only what was attempted, not what is healthy."
        )
        if device_responded is False:
            note += (
                " Nothing was received from the device during this run, so this"
                " path was never exercised and could not have logged an error."
            )
        return note


    # 1. Jackson Deserialization Failure (with active re-probing on metadata)
    meta_ok, meta_err = _reprobe_metadata_json(site_path, device_id)
    if timeline.get("jackson_error"):
        add_claim(
            "Jackson Deserialization Failure",
            "Jackson JSON parser failed to deserialize message or metadata",
            ClaimStatus.CONFIRMED,
            f"Jackson parser error: {timeline['jackson_error']}",
        )
    elif not meta_ok and meta_err:
        add_claim(
            "Jackson Deserialization Failure",
            f"JSON parser error in {device_id} metadata.json",
            ClaimStatus.CONFIRMED,
            meta_err,
        )
    else:
        # This refutation is earned, not assumed: metadata.json was re-parsed
        # above with a strict parser and accepted.
        add_claim(
            "Jackson Deserialization Failure",
            "Jackson JSON parser encountered deserialization or syntax error",
            ClaimStatus.REFUTED,
            f"{device_id} metadata.json re-parsed cleanly under a strict JSON parser, "
            "and no Jackson deserialization error appears in the captured logs",
        )

    # 2. Device Did Not Respond
    #
    # Asks only whether anything authored by the device arrived. An unplugged
    # physical device and an emulator that was never started produce the same
    # signature, so this holds for either without guessing which is deployed.
    silence_claim = f"Device '{device_id}' published nothing the sequencer could evaluate"
    if device_responded is False:
        add_claim("Device Did Not Respond", silence_claim, ClaimStatus.CONFIRMED, device_reason)
    elif device_responded is True:
        add_claim("Device Did Not Respond", silence_claim, ClaimStatus.REFUTED, device_reason)
    else:
        add_claim("Device Did Not Respond", silence_claim, ClaimStatus.NOT_ASSESSED, device_reason)

    # 3. Stale State Cutoff Rejection
    if timeline.get("stale_state_detected"):
        cutoff = timeline.get("cutoff_threshold", "unknown")
        ts = timeline.get("stale_state_timestamp", "lagging")
        add_claim(
            "Stale State Cutoff Rejection",
            f"Device state update ({ts}) lagged sequencer cutoff threshold ({cutoff})",
            ClaimStatus.CONFIRMED,
            f"Sequencer cutoff set at {cutoff}; device state update at {ts} rejected as stale",
        )
    elif device_responded is True:
        # The sequencer logs each rejection, and the device did send state, so
        # the absence of a rejection entry genuinely excludes this.
        add_claim(
            "Stale State Cutoff Rejection",
            "Device state update was rejected as lagging sequencer cutoff threshold",
            ClaimStatus.REFUTED,
            "Device state was received and no stale state rejection entry appears in the sequence logs",
        )
    else:
        # Nothing arrived, so nothing could be rejected as stale. Calling that
        # a refutation implies a timely update was seen.
        add_claim(
            "Stale State Cutoff Rejection",
            "Device state update was rejected as lagging sequencer cutoff threshold",
            ClaimStatus.NOT_ASSESSED,
            not_assessed("stale state rejection entry"),
        )

    # 4. Schema Point Violation / Telemetry Malformation
    if timeline.get("schema_error"):
        add_claim(
            "Schema Point Violation / Telemetry Malformation",
            "Telemetry payload violated JSON schema specification or missing required points",
            ClaimStatus.CONFIRMED,
            f"Schema validator error: {timeline['schema_error']}",
        )
    else:
        add_claim(
            "Schema Point Violation / Telemetry Malformation",
            "Telemetry payload violated JSON schema specification or missing required points",
            ClaimStatus.NOT_ASSESSED,
            not_assessed("schema validation or malformed telemetry error"),
        )

    # 5. Transport / TLS Connection Failure
    if timeline.get("transport_error"):
        add_claim(
            "Transport / TLS Connection Failure",
            "Network transport or TLS handshake connection failure",
            ClaimStatus.CONFIRMED,
            f"Transport connection error: {timeline['transport_error']}",
        )
    else:
        # Previously reported "Transport connection healthy; broker reachable".
        # Nothing here reaches the broker, so that was an invention -- and the
        # most misleading one available when the true fault is a silent device.
        add_claim(
            "Transport / TLS Connection Failure",
            "Network transport or TLS handshake connection failure",
            ClaimStatus.NOT_ASSESSED,
            not_assessed("transport or TLS handshake error"),
        )

    # 6. Authentication / Authorization Rejection
    if timeline.get("auth_error"):
        add_claim(
            "Authentication / Authorization Rejection",
            "MQTT credentials or client certificate authorization rejected",
            ClaimStatus.CONFIRMED,
            f"Authentication failure: {timeline['auth_error']}",
        )
    else:
        # Previously reported "Client authenticated successfully". No client
        # was observed authenticating; a device that never connects is rejected
        # by nothing.
        add_claim(
            "Authentication / Authorization Rejection",
            "MQTT credentials or client certificate authorization rejected",
            ClaimStatus.NOT_ASSESSED,
            not_assessed("authentication or authorization rejection"),
        )

    # 7. Telemetry Cadence Mismatch
    avg_rate = timeline.get("avg_sample_rate_sec")
    if avg_rate and avg_rate > 120.0:
        add_claim(
            "Telemetry Cadence Mismatch",
            f"Telemetry sample interval ({avg_rate:.1f}s) causes stage wait timeouts",
            ClaimStatus.CONFIRMED,
            f"Average sample rate is {avg_rate:.1f}s, exceeding standard 120s test wait threshold",
        )
    elif avg_rate:
        add_claim(
            "Telemetry Cadence Mismatch",
            "Telemetry sample interval causes stage wait timeouts",
            ClaimStatus.REFUTED,
            f"Measured telemetry interval is {avg_rate:.1f}s, within the 120s stage wait threshold",
        )
    else:
        # No interval could be measured. Rendering that as "(N/A)s within
        # normal limits" reported an absence of telemetry as good cadence.
        add_claim(
            "Telemetry Cadence Mismatch",
            "Telemetry sample interval causes stage wait timeouts",
            ClaimStatus.NOT_ASSESSED,
            "Not assessed: no telemetry was captured, so no sample interval could be measured.",
        )

    # 8. Transport / Broker Congestion
    if timeline.get("broker_congestion"):
        add_claim(
            "Transport / Broker Congestion",
            "Broker outbound channel queue drop or inFlight tokens congestion",
            ClaimStatus.CONFIRMED,
            f"Broker congestion: {timeline['broker_congestion']}",
        )
    else:
        add_claim(
            "Transport / Broker Congestion",
            "Broker outbound channel queue drop or inFlight tokens congestion",
            ClaimStatus.NOT_ASSESSED,
            not_assessed("broker queue overflow or inFlight token congestion"),
        )

    # 8. Gateway & Proxy Failure Heuristics (3-Step Isolation Rule)
    if not gateway_id:
        add_claim(
            "Gateway Proxy Bus Drop",
            f"Proxy communication failure between gateway and sub-device {device_id}",
            ClaimStatus.REFUTED,
            f"Device {device_id} is a direct MQTT endpoint, not a proxy sub-device",
        )
    else:
        # Step 1: Gateway Connectivity
        gw_model_ok = gateway_site_info and gateway_site_info.get("status") == "SUCCESS"
        if not gw_model_ok:
            add_claim(
                "Proxy Binding Failure",
                f"Sub-device {device_id} bound to non-existent gateway '{gateway_id}'",
                ClaimStatus.CONFIRMED,
                f"Gateway '{gateway_id}' specified in metadata.json is not found in site model '{site_model}'",
            )
        else:
            add_claim(
                "Proxy Binding Failure",
                f"Sub-device {device_id} bound to gateway '{gateway_id}' in site model",
                ClaimStatus.REFUTED,
                f"Gateway '{gateway_id}' is defined and valid in site model",
            )

        gw_transport_err = gateway_timeline.get("transport_error") if gateway_timeline else None
        gw_auth_err = gateway_timeline.get("auth_error") if gateway_timeline else None
        # Did the gateway itself publish anything? Every claim below about the
        # gateway being "healthy" or "online" depends on this, and none of them
        # used to check it.
        gw_responded = gateway_timeline.get("device_responded") if gateway_timeline else None
        gw_reason = (gateway_timeline.get("device_response_evidence") if gateway_timeline else None) or \
            gateway_artifacts_note or \
            f"No run artifacts were harvested for gateway '{gateway_id}'."

        if gw_transport_err or gw_auth_err:
            add_claim(
                "Gateway Connection Drop",
                f"Gateway '{gateway_id}' disconnected from broker or transport failed",
                ClaimStatus.CONFIRMED,
                f"Gateway '{gateway_id}' connection error: {gw_transport_err or gw_auth_err}",
            )
        elif gw_responded is True:
            add_claim(
                "Gateway Connection Drop",
                f"Gateway '{gateway_id}' disconnected from broker or transport failed",
                ClaimStatus.REFUTED,
                f"Gateway '{gateway_id}' published during this run, so it reached the broker: {gw_reason}",
            )
        else:
            # Previously "transport and authentication verified healthy" -- an
            # all-clear issued for a gateway that may never have connected.
            add_claim(
                "Gateway Connection Drop",
                f"Gateway '{gateway_id}' disconnected from broker or transport failed",
                ClaimStatus.NOT_ASSESSED,
                f"Not assessed: no connection error is recorded for gateway '{gateway_id}', "
                f"but nothing was observed from it either. {gw_reason}",
            )

        # Step 2 & 3: Field Bus vs Protocol Drop vs Driver Bug
        gw_bus_err = timeline.get("gateway_error") or (gateway_timeline.get("gateway_error") if gateway_timeline else None)
        gw_missing_echo = timeline.get("gateway_tx_missing_echo") or (gateway_timeline.get("gateway_tx_missing_echo") if gateway_timeline else None)

        if gw_bus_err:
            add_claim(
                "Field Bus Communication Failure",
                f"Field bus communication error on gateway '{gateway_id}' for sub-device '{device_id}'",
                ClaimStatus.CONFIRMED,
                f"Field bus error: {gw_bus_err}",
            )
        else:
            # The field bus is behind the gateway; a silent gateway reports no
            # CRC errors precisely because it reported nothing.
            add_claim(
                "Field Bus Communication Failure",
                f"Field bus communication error on gateway '{gateway_id}' for sub-device '{device_id}'",
                ClaimStatus.NOT_ASSESSED,
                not_assessed("serial frame CRC error, framing error, or bus timeout"),
            )

        if gw_missing_echo:
            add_claim(
                "Gateway Firmware / Driver State Translation Bug",
                f"Gateway '{gateway_id}' omitted sub-device '{device_id}' state echo",
                ClaimStatus.CONFIRMED,
                f"Gateway received config transaction but omitted sub-device state echo: {gw_missing_echo}",
            )
        elif device_responded is True:
            add_claim(
                "Gateway Firmware / Driver State Translation Bug",
                f"Gateway '{gateway_id}' omitted sub-device '{device_id}' state echo",
                ClaimStatus.REFUTED,
                f"Sub-device '{device_id}' state reached the sequencer, so the gateway did translate it: {device_reason}",
            )
        else:
            add_claim(
                "Gateway Firmware / Driver State Translation Bug",
                f"Gateway '{gateway_id}' omitted sub-device '{device_id}' state echo",
                ClaimStatus.NOT_ASSESSED,
                not_assessed("omitted sub-device state echo"),
            )

        # A proxy bus drop means the gateway is up while the sub-device stream
        # is not. That requires having actually heard the gateway: confirming it
        # from the absence of other errors described a silent gateway as
        # "online and communicating".
        if (
            gw_responded is True
            and device_responded is False
            and not gw_bus_err
            and not gw_missing_echo
            and not gw_transport_err
            and not gw_auth_err
            and gw_model_ok
            and timeline.get("result") != "PASS"
            and not timeline.get("stale_state_detected")
            and not timeline.get("schema_error")
            and not timeline.get("jackson_error")
        ):
            add_claim(
                "Gateway Proxy Bus Drop",
                f"Gateway '{gateway_id}' online, but sub-device '{device_id}' stream absent",
                ClaimStatus.CONFIRMED,
                f"Gateway '{gateway_id}' published during this run while sub-device '{device_id}' "
                f"published nothing. Gateway: {gw_reason} Sub-device: {device_reason}",
            )
        elif device_responded is True:
            add_claim(
                "Gateway Proxy Bus Drop",
                f"Gateway '{gateway_id}' online, but sub-device '{device_id}' stream absent",
                ClaimStatus.REFUTED,
                f"Sub-device '{device_id}' stream was delivered: {device_reason}",
            )
        else:
            add_claim(
                "Gateway Proxy Bus Drop",
                f"Gateway '{gateway_id}' online, but sub-device '{device_id}' stream absent",
                ClaimStatus.NOT_ASSESSED,
                f"Not assessed: sub-device '{device_id}' published nothing, but the gateway was not "
                f"observed publishing either, so the drop cannot be localised to the proxy path. {gw_reason}",
            )

    # 9. Stage Timeout Execution Failure (Critic Cascading Check)
    has_specific_root_cause = any(
        competing_hypotheses.get(h, {}).get("status") == ClaimStatus.CONFIRMED.value
        for h in [
            "Jackson Deserialization Failure",
            "Device Did Not Respond",
            "Stale State Cutoff Rejection",
            "Schema Point Violation / Telemetry Malformation",
            "Transport / TLS Connection Failure",
            "Authentication / Authorization Rejection",
            "Telemetry Cadence Mismatch",
            "Transport / Broker Congestion",
            "Field Bus Communication Failure",
            "Gateway Firmware / Driver State Translation Bug",
            "Gateway Connection Drop",
            "Proxy Binding Failure",
            "Gateway Proxy Bus Drop",
        ]
    )

    if timeline.get("timeout_error"):
        if has_specific_root_cause:
            add_claim(
                "Stage Timeout Execution Failure",
                "Sequencer stage timed out waiting for condition",
                ClaimStatus.REFUTED,
                "Timeout is a cascading symptom of primary root cause, not an independent execution failure",
            )
        else:
            add_claim(
                "Stage Timeout Execution Failure",
                "Sequencer stage timed out waiting for condition",
                ClaimStatus.CONFIRMED,
                f"Test stage timed out waiting for condition: {timeline['timeout_error']}",
            )
    elif logs_harvested:
        # The sequencer records its own timeouts, so a harvested log without one
        # does exclude this. Without a log there is nothing to conclude from.
        add_claim(
            "Stage Timeout Execution Failure",
            "Sequencer stage timed out waiting for condition",
            ClaimStatus.REFUTED,
            f"No stage timeout appears in the harvested sequencer logs ({', '.join(logs_harvested)})",
        )
    else:
        add_claim(
            "Stage Timeout Execution Failure",
            "Sequencer stage timed out waiting for condition",
            ClaimStatus.NOT_ASSESSED,
            "Not assessed: no logs were harvested for this run, so the sequencer's own record is unavailable.",
        )

    # Phase 3: Verified Synthesis
    root_cause = ""
    evidence_lines: List[str] = []
    fix_suggestions: List[str] = []
    stale_mermaid_needed = False
    gateway_mermaid_needed = False

    if competing_hypotheses.get("Jackson Deserialization Failure", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Jackson parser failed to deserialize device metadata: {timeline.get('jackson_error') or meta_err}"
        evidence_lines.append(f"Jackson parser error: `{timeline.get('jackson_error') or meta_err}`")
        fix_suggestions.append(f"Correct the device metadata so it parses: validate {site_model}/devices/{device_id}/metadata.json against schema/metadata.json.")

    elif competing_hypotheses.get("Proxy Binding Failure", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Proxy binding configuration error: Device '{device_id}' specifies gateway_id '{gateway_id}' which does not exist in site model '{site_model}'."
        evidence_lines.append(f"Site model error: Gateway '{gateway_id}' not found in {site_model}/devices")
        fix_suggestions.append(f"Correct the gateway binding in the site model: either add gateway '{gateway_id}' under {site_model}/devices, or change the gateway_id in the metadata of '{device_id}' to an existing gateway.")

    elif competing_hypotheses.get("Gateway Connection Drop", {}).get("status") == ClaimStatus.CONFIRMED.value:
        ev = competing_hypotheses["Gateway Connection Drop"]["evidence"]
        root_cause = f"Gateway connection drop: Gateway '{gateway_id}' is offline or disconnected from MQTT broker."
        evidence_lines.append(f"Gateway connection error: {ev}")
        fix_suggestions.append(f"Restore the gateway's broker connection: confirm gateway '{gateway_id}' is powered, on the network, and using the credentials registered in {site_model}/devices/{gateway_id}, then check the broker connection log for its client.")

    elif competing_hypotheses.get("Field Bus Communication Failure", {}).get("status") == ClaimStatus.CONFIRMED.value:
        ev = competing_hypotheses["Field Bus Communication Failure"]["evidence"]
        root_cause = f"Field bus communication failure: Gateway '{gateway_id}' encountered serial frame CRC or bus timeout communicating with proxy device '{device_id}'."
        evidence_lines.append(f"Field bus error: {ev}")
        fix_suggestions.append(f"Check physical RS-485 termination, wiring, and baud rate for {device_id} on {gateway_id}")
        gateway_mermaid_needed = True

    elif competing_hypotheses.get("Gateway Firmware / Driver State Translation Bug", {}).get("status") == ClaimStatus.CONFIRMED.value:
        ev = competing_hypotheses["Gateway Firmware / Driver State Translation Bug"]["evidence"]
        root_cause = f"Gateway firmware / driver state translation bug: Gateway '{gateway_id}' received config transaction but omitted sub-device '{device_id}' state echo in aggregated state."
        evidence_lines.append(f"Translation bug: {ev}")
        fix_suggestions.append(f"Update gateway {gateway_id} driver/firmware to properly aggregate proxy sub-device state")
        gateway_mermaid_needed = True

    elif competing_hypotheses.get("Gateway Proxy Bus Drop", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Gateway proxy bus drop: Gateway '{gateway_id}' is online, but proxy sub-device '{device_id}' pointset stream is absent."
        evidence_lines.append(f"Proxy pointset stream absent: {competing_hypotheses['Gateway Proxy Bus Drop']['evidence']}")
        fix_suggestions.append(f"Check gateway '{gateway_id}' is polling proxy device '{device_id}' and publishing its pointset events on the device's behalf.")
        gateway_mermaid_needed = True

    elif competing_hypotheses.get("Stale State Cutoff Rejection", {}).get("status") == ClaimStatus.CONFIRMED.value:
        cutoff = timeline.get("cutoff_threshold")
        ts = timeline.get("stale_state_timestamp")
        tx = timeline.get("transactions", [])
        ts_str = f"`{ts}`" if ts else "an unrecorded timestamp"
        cutoff_str = f"`{cutoff}`" if cutoff else "an unrecorded cutoff threshold"
        root_cause = (
            f"Sequencer timed out waiting for state update because the device state update timestamp ({ts_str}) "
            f"lagged the sequencer cutoff threshold ({cutoff_str}), triggering stale state rejection."
        )
        if cutoff:
            evidence_lines.append(f"Cutoff set at `{cutoff}` (sequence.log)")
        else:
            evidence_lines.append("Cutoff threshold set (sequence.log)")
        if ts:
            evidence_lines.append(f"Stale state update `{ts}` ignored (sequence.log / device_system.log)")
        else:
            evidence_lines.append("Stale state update ignored (sequence.log / device_system.log)")
        if tx:
            evidence_lines.append(f"Config transaction `{tx[0]}` dispatched and pending state echo")
        fix_suggestions.append(f"The device under test reported state with a timestamp ({ts_str}) older than the sequencer's cutoff ({cutoff_str}); it must publish a fresh state update, timestamped after the cutoff, in response to the config it received.")
        fix_suggestions.append(f"Re-run {test_id} on {device_id} once the device's state reporting is corrected.")
        stale_mermaid_needed = True

    elif competing_hypotheses.get("Schema Point Violation / Telemetry Malformation", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Telemetry message violated schema rules: {timeline.get('schema_error')}"
        evidence_lines.append(f"Schema violation: `{timeline.get('schema_error')}`")
        fix_suggestions.append("Correct the message the device under test published so that it validates against the UDMI schema named in the violation (schema/*.json).")

    elif competing_hypotheses.get("Authentication / Authorization Rejection", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Authentication rejected by broker: {timeline.get('auth_error')}"
        evidence_lines.append(f"Auth error: `{timeline.get('auth_error')}`")
        fix_suggestions.append(f"Make the device's credentials match the key registered for '{device_id}' in {site_model}/devices/{device_id}.")

    elif competing_hypotheses.get("Transport / TLS Connection Failure", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Network transport / TLS connection failure: {timeline.get('transport_error')}"
        evidence_lines.append(f"Transport error: `{timeline.get('transport_error')}`")
        fix_suggestions.append(f"Confirm the device under test can reach the broker endpoint configured for '{device_id}' and completes the TLS handshake with it.")

    elif competing_hypotheses.get("Transport / Broker Congestion", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Broker congestion / transport queue drop: {timeline.get('broker_congestion')}"
        evidence_lines.append(f"Broker congestion: `{timeline.get('broker_congestion')}`")
        fix_suggestions.append("Inspect Mosquitto broker queue limits and network throughput")

    elif competing_hypotheses.get("Device Did Not Respond", {}).get("status") == ClaimStatus.CONFIRMED.value:
        # Reached only when no more specific cause explains the silence. The
        # wording stays deployment-agnostic on purpose: the evidence shows the
        # device said nothing, and nothing in a run directory reveals whether
        # that device is hardware on a bench or an emulator on a laptop.
        root_cause = (
            f"Device '{device_id}' did not respond. No message authored by the device was "
            f"captured during the run, so the sequencer had nothing to evaluate and the test "
            f"could not proceed."
        )
        evidence_lines.append(device_reason)
        inspected = timeline.get("payload_files_inspected") or []
        evidence_lines.append(
            f"Payload files inspected: {', '.join(inspected)}" if inspected
            else "No message payload files were written to the run directory"
        )
        if timeline.get("timeout_error"):
            evidence_lines.append(
                f"Sequencer gave up waiting: `{timeline['timeout_error']}` (a consequence of the "
                "silence, not a separate fault)"
            )
        fix_suggestions.append(
            f"Confirm something is publishing as '{device_id}': a physical device must be powered, "
            f"on the network, and using the credentials in {site_model}/devices/{device_id}; an "
            f"emulated device must be running as '{device_id}' against the same broker."
        )
        fix_suggestions.append(
            f"Check the broker/UDMIS connection log for a client connecting as '{device_id}' to "
            "tell a device that never connected from one that connected and then went quiet."
        )
        fix_suggestions.append(f"Re-run {test_id} on {device_id} once the device is publishing.")

    elif competing_hypotheses.get("Stage Timeout Execution Failure", {}).get("status") == ClaimStatus.CONFIRMED.value:
        root_cause = f"Test stage timed out waiting for condition: {timeline.get('timeout_error')}"
        evidence_lines.append(f"Timeout message: `{timeline.get('timeout_error')}`")
        fix_suggestions.append(f"Compare what the device under test published with the condition the timed-out stage waits for, then re-run {test_id} on {device_id}.")

    else:
        res = timeline.get("result", "UNKNOWN")
        if res == "PASS":
            root_cause = "Test execution completed successfully with all assertions passing."
            evidence_lines.append(f"Final test result: {res}")
            fix_suggestions.append("No fix needed. System is operating nominally.")
        else:
            # Say what was actually established rather than implying the
            # subsystems listed as NOT ASSESSED were checked and found clean.
            unassessed = [
                name for name, entry in competing_hypotheses.items()
                if entry.get("status") == ClaimStatus.NOT_ASSESSED.value
            ]
            root_cause = (
                f"Test ended with status {res}, and no hypothesis was confirmed by the available "
                f"artifacts. The device did respond, so the failure lies in what it sent or when."
                if device_responded is True else
                f"Test ended with status {res}, and no hypothesis was confirmed by the available artifacts."
            )
            evidence_lines.append(f"Final test result: {res}")
            evidence_lines.append(device_reason)
            if unassessed:
                evidence_lines.append(
                    f"Not assessed (no evidence either way): {', '.join(unassessed)}"
                )
            fix_suggestions.append(f"Inspect the run's sequence.log and device message payloads for '{device_id}' to find what it sent against what the failing check expects.")

    # Format concise markdown report
    report_lines = [
        f"### Failure Diagnosis: `{device_id}` / `{test_id}`\n",
        f"* **Root Cause**: {root_cause}",
        "* **Evidence**:",
    ]
    for ev in evidence_lines:
        report_lines.append(f"  - {ev}")

    report_lines.append("* **Fix**:")
    for fix in fix_suggestions:
        report_lines.append(f"  - {fix}")

    # Sequence diagrams
    if stale_mermaid_needed:
        cutoff_disp = timeline.get("cutoff_threshold") or "CUTOFF_THRESHOLD"
        ts_disp = timeline.get("stale_state_timestamp") or "STALE_TIMESTAMP"
        tx_disp = timeline.get("transactions", ["config_update"])[0] if timeline.get("transactions") else "config_update"
        report_lines.append(f"""
```mermaid
sequenceDiagram
    participant S as Sequencer
    participant U as UDMIS
    participant B as Mosquitto Broker
    participant D as Device under test

    S->>U: Dispatches config ({tx_disp})
    U->>B: Routes config packet
    B->>D: Delivers config update
    D-->>B: Publishes state update (ts={ts_disp})
    B-->>U: Forwards state packet
    U-->>S: Relays state to Sequencer
    Note over S: State timestamp ({ts_disp}) < Cutoff ({cutoff_disp})<br/>STALE STATE REJECTED
```
""")

    if gateway_mermaid_needed and gateway_id:
        tx = timeline.get("transactions", ["RC:config_update"])[0] if timeline.get("transactions") else "RC:config_update"
        report_lines.append(f"""
```mermaid
sequenceDiagram
    participant S as Sequencer
    participant B as Mosquitto Broker
    participant G as Gateway ({gateway_id})
    participant P as Proxy Sub-Device ({device_id})

    S->>B: Dispatches config ({tx})
    B->>G: Routes config packet
    Note over G,P: Field Bus Communication (RS-485/IP)
    alt Field Bus / Translation Failure
        G--xP: Bus Timeout / CRC Error / Missing Echo
    end
    Note over S: Stage Timeout Failure (120s expired)
```
""")

    formatted_report = "\n".join(report_lines)

    return {
        "status": "SUCCESS",
        "test_id": test_id,
        "device_id": device_id,
        "site_model": site_model,
        "root_cause": root_cause,
        "evidence": evidence_lines,
        "fix": fix_suggestions,
        "verification_matrix": matrix,
        "competing_hypotheses": competing_hypotheses,
        "timeline": timeline,
        "report": formatted_report,
    }


def evaluate_test_stability(
    test_id: Optional[str] = None,
    device_id: Optional[str] = None,
    site_model: str = "sites/udmi_site_model",
    base_dir: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Calculates empirical reliability score, flakiness index, and failure mode distribution across test runs."""
    runs = discover_test_runs(base_dir=base_dir, udmi_root=udmi_root)
    total_runs = len(runs)
    if total_runs == 0:
        return {
            "status": "SUCCESS",
            "total_runs": 0,
            "pass_count": 0,
            "fail_count": 0,
            "pass_rate_pct": 0.0,
            "stability_score": 100.0,
            "flakiness_index": 0.0,
            "failure_breakdown": {},
            "summary_report": "No archived test runs discovered.",
        }

    pass_count = 0
    fail_count = 0
    failure_breakdown: Dict[str, int] = {}

    for r in runs:
        target_test = test_id or r["run_id"]
        target_dev = device_id

        if not target_dev:
            # 1. Check triage_manifest.json in run directory
            manifest_file = os.path.join(r["path"], "triage_manifest.json")
            if os.path.isfile(manifest_file):
                try:
                    with open(manifest_file, "r", encoding="utf-8") as mf:
                        m_data = json.load(mf)
                    target_dev = m_data.get("device_id")
                except Exception:
                    pass

            # 2. Check if run directory name has <device>_<test> pattern
            if not target_dev:
                folder_name = os.path.basename(r["path"])
                m_dev = re.match(r"^([A-Z0-9_-]{3,})_(?:pointset|system|empty|[a-z0-9_]+)", folder_name)
                if m_dev:
                    target_dev = m_dev.group(1)

            # 3. Check sequence.log for device ID
            if not target_dev:
                seq_path = os.path.join(r["path"], "sequence.log")
                if os.path.isfile(seq_path):
                    try:
                        with open(seq_path, "r", encoding="utf-8", errors="replace") as sf:
                            for _ in range(50):
                                s_line = sf.readline()
                                if not s_line:
                                    break
                                dev_m = re.search(r"(?:Starting test \S+ for|Device)\s+([A-Za-z0-9_-]{3,})", s_line)
                                if dev_m:
                                    target_dev = dev_m.group(1)
                                    break
                    except Exception:
                        pass

            # 4. Fallback if all auto-detection fails
            if not target_dev:
                target_dev = "UNKNOWN_DEVICE"

        diag = diagnose_test_failure(
            test_id=target_test,
            device_id=target_dev,
            site_model=site_model,
            run_dir=r["path"],
            udmi_root=udmi_root,
        )
        res = diag.get("timeline", {}).get("result", "UNKNOWN")
        if res == "PASS":
            pass_count += 1
        else:
            fail_count += 1
            confirmed_mode = "Stage Timeout Execution Failure"
            for hyp_name, hyp_eval in diag.get("competing_hypotheses", {}).items():
                if hyp_eval.get("status") == ClaimStatus.CONFIRMED.value:
                    confirmed_mode = hyp_name
                    break
            failure_breakdown[confirmed_mode] = failure_breakdown.get(confirmed_mode, 0) + 1

    pass_rate_pct = (pass_count / total_runs) * 100.0
    p = pass_count / total_runs
    flakiness_index = 4 * p * (1 - p)
    stability_score = round(pass_rate_pct * (1.0 - (flakiness_index * 0.2)), 2)

    report_lines = [
        "### Empirical Test Run Stability Evaluation\n",
        f"* **Total Runs Evaluated**: {total_runs}",
        f"* **Pass Count**: {pass_count} ({pass_rate_pct:.1f}%)",
        f"* **Fail Count**: {fail_count}",
        f"* **System Stability Score**: {stability_score:.1f} / 100.0",
        f"* **Flakiness Index**: {flakiness_index:.2f} (0.00 = deterministic, 1.00 = maximum flakiness)",
        "\n#### Failure Mode Distribution:",
    ]
    if failure_breakdown:
        for mode, count in failure_breakdown.items():
            pct = (count / fail_count) * 100.0
            report_lines.append(f"  - **{mode}**: {count} runs ({pct:.1f}%)")
    else:
        report_lines.append("  - (Zero test failures detected)")

    summary_report = "\n".join(report_lines)

    return {
        "status": "SUCCESS",
        "total_runs": total_runs,
        "pass_count": pass_count,
        "fail_count": fail_count,
        "pass_rate_pct": pass_rate_pct,
        "stability_score": stability_score,
        "flakiness_index": round(flakiness_index, 3),
        "failure_breakdown": failure_breakdown,
        "summary_report": summary_report,
    }
