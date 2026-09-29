"""Behavioral differential sequence alignment between test runs."""

import os
import re
from typing import Any, Dict, List, Optional, Tuple

from mantis.tools.artifacts import extract_timeline


def _align_event_sequences(
    b_events: List[Dict[str, Any]],
    t_events: List[Dict[str, Any]],
) -> List[Tuple[Optional[Dict[str, Any]], Optional[Dict[str, Any]]]]:
    """Needleman-Wunsch sequence alignment between baseline and target events.
    Preserves all multi-stage occurrences of repeated checkpoints."""
    n = len(b_events)
    m = len(t_events)

    if n == 0 and m == 0:
        return []
    if n == 0:
        return [(None, t) for t in t_events]
    if m == 0:
        return [(b, None) for b in b_events]

    # Initialize DP table
    dp = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1):
        dp[i][0] = -i
    for j in range(m + 1):
        dp[0][j] = -j

    def score_match(b: Dict[str, Any], t: Dict[str, Any]) -> int:
        b_cp = b.get("checkpoint")
        t_cp = t.get("checkpoint")
        if b_cp == t_cp:
            # Check if transaction IDs match
            b_desc = b.get("description", "")
            t_desc = t.get("description", "")
            if "RC:" in b_desc and "RC:" in t_desc:
                b_rc = re.search(r"RC:([a-zA-Z0-9_\-\.]+)", b_desc)
                t_rc = re.search(r"RC:([a-zA-Z0-9_\-\.]+)", t_desc)
                if b_rc and t_rc and b_rc.group(1) == t_rc.group(1):
                    return 5
            return 3
        # Partial match if both are related to state/config transition
        if {b_cp, t_cp}.issubset({"STATE_RECEIVED", "STALE_STATE_IGNORED", "TIMEOUT_FAILURE"}):
            return 0
        return -2

    # Fill DP table
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            match_score = dp[i - 1][j - 1] + score_match(b_events[i - 1], t_events[j - 1])
            delete_score = dp[i - 1][j] - 1
            insert_score = dp[i][j - 1] - 1
            dp[i][j] = max(match_score, delete_score, insert_score)

    # Backtrack alignment
    aligned: List[Tuple[Optional[Dict[str, Any]], Optional[Dict[str, Any]]]] = []
    i, j = n, m
    while i > 0 or j > 0:
        if i > 0 and j > 0 and dp[i][j] == dp[i - 1][j - 1] + score_match(b_events[i - 1], t_events[j - 1]):
            aligned.append((b_events[i - 1], t_events[j - 1]))
            i -= 1
            j -= 1
        elif i > 0 and (j == 0 or dp[i][j] == dp[i - 1][j] - 1):
            aligned.append((b_events[i - 1], None))
            i -= 1
        else:
            aligned.append((None, t_events[j - 1]))
            j -= 1

    aligned.reverse()
    return aligned


def compare_test_runs(
    target_run: str,
    baseline_run: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Performs behavioral differential sequence alignment between a target run and a reference baseline."""
    # Extract timelines
    target_timeline = extract_timeline(
        test_id=os.path.basename(target_run),
        device_id="target",
        run_dir=target_run,
        udmi_root=udmi_root,
    )

    baseline_timeline = None
    if baseline_run:
        baseline_timeline = extract_timeline(
            test_id=os.path.basename(baseline_run),
            device_id="baseline",
            run_dir=baseline_run,
            udmi_root=udmi_root,
        )

    target_events = target_timeline.get("events", [])
    baseline_events = baseline_timeline.get("events", []) if baseline_timeline else []

    aligned_steps: List[Dict[str, Any]] = []
    divergence_found = False
    divergence_point = None

    if not baseline_events:
        # If no baseline run is provided, evaluate target events against expected normal behavior
        step_num = 1
        for t_event in target_events:
            cp = t_event.get("checkpoint", "UNKNOWN")
            t_desc = t_event.get("description", "(None)")

            if cp in ("STALE_STATE_IGNORED", "JACKSON_DESERIALIZATION_FAILURE", "GATEWAY_BUS_ERROR"):
                if not divergence_found:
                    delta = "DIVERGENCE POINT"
                    divergence_found = True
                    divergence_point = cp
                else:
                    delta = "Cascading Failure"
                b_desc = "(None - accepted by cutoff)" if cp == "STALE_STATE_IGNORED" else "(None - nominal parse expected)"
            elif cp == "TIMEOUT_FAILURE":
                delta = "Cascading Failure" if divergence_found else "DIVERGENCE POINT"
                if not divergence_found:
                    divergence_found = True
                    divergence_point = cp
                b_desc = "(None - nominal completion)"
            elif cp == "TEST_RESULT":
                delta = "Final Outcome"
                # No baseline run was supplied, so this column states the
                # expected outcome, exactly like the branches above. Printing a
                # verdict string here read as an observation of a passing run
                # that was never performed.
                b_desc = "(None - nominal pass expected)"
            else:
                delta = "Aligned"
                b_desc = t_desc

            aligned_steps.append({
                "step": step_num,
                "checkpoint": cp,
                "baseline": b_desc,
                "target": t_desc,
                "delta": delta,
            })
            step_num += 1
    else:
        # Needleman-Wunsch sequence alignment preserving all occurrences
        aligned_pairs = _align_event_sequences(baseline_events, target_events)
        step_num = 1
        for b_event, t_event in aligned_pairs:
            b_desc = b_event.get("description", "(None - absent)") if b_event else "(Baseline: Pass expected)"
            t_desc = t_event.get("description", "(None)") if t_event else "(None)"
            cp = (t_event or b_event).get("checkpoint", "UNKNOWN")

            if b_event and t_event:
                b_cp = b_event.get("checkpoint")
                t_cp = t_event.get("checkpoint")

                if b_cp == t_cp:
                    if cp == "STATE_CUTOFF_SET":
                        t_cutoff = t_event.get("cutoff")
                        b_cutoff = b_event.get("cutoff")
                        if t_cutoff and b_cutoff and t_cutoff != b_cutoff:
                            if not divergence_found:
                                delta = "DIVERGENCE POINT"
                                divergence_found = True
                                divergence_point = cp
                            else:
                                delta = "Cascading Failure"
                        else:
                            delta = "Aligned"
                    elif cp in ("STALE_STATE_IGNORED", "JACKSON_DESERIALIZATION_FAILURE", "GATEWAY_BUS_ERROR"):
                        delta = "Cascading Failure" if divergence_found else "DIVERGENCE POINT"
                        if not divergence_found:
                            divergence_found = True
                            divergence_point = cp
                    elif cp == "TIMEOUT_FAILURE":
                        delta = "Cascading Failure" if divergence_found else "DIVERGENCE POINT"
                        if not divergence_found:
                            divergence_found = True
                            divergence_point = cp
                    elif cp == "TEST_RESULT":
                        delta = "Final Outcome"
                    else:
                        delta = "Aligned"
                else:
                    delta = "Cascading Failure" if divergence_found else "DIVERGENCE POINT"
                    if not divergence_found:
                        divergence_found = True
                        divergence_point = t_cp
            elif t_event and not b_event:
                if cp in ("STALE_STATE_IGNORED", "JACKSON_DESERIALIZATION_FAILURE", "TIMEOUT_FAILURE", "GATEWAY_BUS_ERROR"):
                    delta = "Cascading Failure" if divergence_found else "DIVERGENCE POINT"
                    if not divergence_found:
                        divergence_found = True
                        divergence_point = cp
                else:
                    delta = "Target Extra"
            else:
                # b_event and not t_event
                delta = "Omitted / Cascading" if divergence_found else "Omitted / Divergence"
                if not divergence_found:
                    divergence_found = True
                    divergence_point = f"Omitted {cp}"

            aligned_steps.append({
                "step": step_num,
                "checkpoint": cp,
                "baseline": b_desc,
                "target": t_desc,
                "delta": delta,
            })
            step_num += 1

    # Render formatted markdown differential table
    baseline_header = "Baseline Run" if baseline_timeline else "Nominal Expectation"
    table_lines = [
        f"| Step | Protocol Checkpoint | {baseline_header} | Target Run | Delta |",
        "| :--- | :--- | :--- | :--- | :--- |",
    ]
    for step in aligned_steps:
        table_lines.append(
            f"| {step['step']} | `{step['checkpoint']}` | {step['baseline']} | {step['target']} | **{step['delta']}** |"
        )
    diff_table_md = "\n".join(table_lines)

    return {
        "status": "SUCCESS",
        "target_run": target_run,
        "baseline_run": baseline_run,
        "divergence_point": divergence_point or ("TIMEOUT" if target_timeline.get("result") == "FAIL" else None),
        "target_result": target_timeline.get("result", "UNKNOWN"),
        "baseline_result": baseline_timeline.get("result", "UNKNOWN") if baseline_timeline else None,
        "aligned_steps": aligned_steps,
        "differential_table": diff_table_md,
    }
