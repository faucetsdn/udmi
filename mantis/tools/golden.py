"""Golden baseline verification and anti-cheating audit engine for UDMI."""

import difflib
import os
import re
from typing import Any, Dict, List, Optional, Set


def _get_udmi_root(udmi_root: Optional[str] = None) -> str:
    if udmi_root is not None:
        return os.path.abspath(udmi_root)
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


# Standard critical event streams that must never be truncated or omitted
CRITICAL_EVENT_PATTERNS = [
    r"events_blobset",
    r"events_discovery",
    r"events_invalid",
    r"events_pointset",
    r"events_system",
]


def verify_golden_baseline(
    baseline_name: str = "validator",
    test_output_path: Optional[str] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Validates test run outputs against golden expectation baselines in etc/ and enforces anti-cheating integrity rules.

    Args:
        baseline_name: Name of golden file (e.g. 'validator', 'sequencer', 'schema_nostate').
        test_output_path: Optional path to actual test output file. Defaults to out/<baseline_name>.out.
        udmi_root: Path to repository root.

    Returns:
        Dict containing match status, line differences, anti-cheating audit results, and diff.
    """
    root = _get_udmi_root(udmi_root)
    clean_name = baseline_name.replace(".out", "")

    # Locate golden file in etc/ with path containment
    etc_dir = os.path.realpath(os.path.join(root, "etc"))
    golden_file = os.path.realpath(os.path.join(etc_dir, f"{clean_name}.out"))
    if not (golden_file.startswith(etc_dir + os.sep) or (os.path.isfile(baseline_name) and os.path.realpath(baseline_name).startswith(root + os.sep))):
        err_msg = f"Security violation: Invalid baseline_name '{baseline_name}' attempts path traversal."
        return {
            "status": "ERROR",
            "error": err_msg,
            "report": f"### Golden Baseline Verification Error\n\n* **Error**: {err_msg}",
        }

    if not os.path.isfile(golden_file):
        # Check if full path was provided
        if os.path.isfile(baseline_name) and os.path.realpath(baseline_name).startswith(root + os.sep):
            golden_file = os.path.realpath(baseline_name)
        else:
            err_msg = f"Golden baseline file not found: {golden_file}"
            return {
                "status": "ERROR",
                "error": err_msg,
                "report": f"### Golden Baseline Verification Error\n\n* **Error**: {err_msg}",
            }

    # Locate actual test output with path containment
    if test_output_path:
        actual_file = os.path.realpath(test_output_path)
    else:
        actual_file = os.path.realpath(os.path.join(root, "out", f"{clean_name}.out"))

    if not actual_file.startswith(root + os.sep):
        err_msg = f"Security violation: Invalid test_output_path '{test_output_path}' attempts path traversal outside repository."
        return {
            "status": "ERROR",
            "error": err_msg,
            "report": f"### Golden Baseline Verification Error\n\n* **Error**: {err_msg}",
        }

    if not os.path.isfile(actual_file):
        err_msg = f"Test output file not found: {actual_file}. Run the test suite first."
        return {
            "status": "ERROR",
            "error": err_msg,
            "golden_file": golden_file,
            "report": f"### Golden Baseline Verification Error\n\n* **Error**: {err_msg}\n* **Golden File**: `{golden_file}`",
        }

    with open(golden_file, "r", encoding="utf-8", errors="replace") as f:
        golden_lines = [l.strip() for l in f if l.strip()]

    with open(actual_file, "r", encoding="utf-8", errors="replace") as f:
        actual_lines = [l.strip() for l in f if l.strip()]

    # Unified diff
    diff_lines = list(
        difflib.unified_diff(
            [l + "\n" for l in golden_lines],
            [l + "\n" for l in actual_lines],
            fromfile=f"etc/{os.path.basename(golden_file)}",
            tofile=f"out/{os.path.basename(actual_file)}",
        )
    )
    diff_str = "".join(diff_lines)

    missing_from_actual = [l[1:].strip() for l in diff_lines if l.startswith("-") and not l.startswith("---")]
    extra_in_actual = [l[1:].strip() for l in diff_lines if l.startswith("+") and not l.startswith("+++")]

    # Anti-cheating audit: Check if critical expected events or devices were dropped/truncated
    cheating_violations: List[str] = []
    for pattern in CRITICAL_EVENT_PATTERNS:
        expected_matches = [l for l in golden_lines if re.search(pattern, l)]
        if expected_matches:
            actual_matches = [l for l in actual_lines if re.search(pattern, l)]
            if not actual_matches:
                cheating_violations.append(
                    f"ANTI-CHEATING VIOLATION: Complete omission of critical event pattern '{pattern}' "
                    f"({len(expected_matches)} entries in golden baseline missing from output)."
                )
            elif len(actual_matches) < len(expected_matches):
                cheating_violations.append(
                    f"ANTI-CHEATING WARNING: Truncation of '{pattern}' events: "
                    f"expected {len(expected_matches)}, found only {len(actual_matches)}."
                )

    # Compute line match percentage using ordered sequence similarity
    total_golden = len(golden_lines)
    matcher = difflib.SequenceMatcher(None, golden_lines, actual_lines)
    match_pct = round(matcher.ratio() * 100.0, 2) if total_golden else 100.0

    is_clean = len(missing_from_actual) == 0 and len(extra_in_actual) == 0
    has_violations = len(cheating_violations) > 0

    if is_clean:
        overall_status = "PASS"
        summary = f"Golden baseline '{clean_name}' matches test output perfectly (100.0% parity)."
    elif has_violations:
        overall_status = "VIOLATION"
        summary = f"Anti-cheating audit failed for '{clean_name}': {len(cheating_violations)} violation(s) detected."
    else:
        overall_status = "MISMATCH"
        summary = f"Baseline mismatch for '{clean_name}': {match_pct}% match ({len(missing_from_actual)} missing, {len(extra_in_actual)} extra)."

    report_lines = [
        f"### Golden Baseline Verification: `{clean_name}`\n",
        f"* **Audit Status**: {overall_status}",
        f"* **Match Percentage**: {match_pct}%",
        f"* **Golden Line Count**: {total_golden}",
        f"* **Actual Line Count**: {len(actual_lines)}",
        f"* **Anti-Cheating Audit**: {'PASSED' if not has_violations else 'FAILED'}",
        f"* **Summary**: {summary}",
    ]
    if cheating_violations:
        report_lines.append("\n#### Anti-Cheating Violations:")
        for v in cheating_violations:
            report_lines.append(f"  - {v}")
    if diff_str:
        report_lines.append(f"\n#### Unified Diff:\n```diff\n{diff_str[:1500]}\n```")

    report = "\n".join(report_lines)

    return {
        "status": overall_status,
        "baseline_name": clean_name,
        "golden_file": golden_file,
        "actual_file": actual_file,
        "golden_line_count": total_golden,
        "actual_line_count": len(actual_lines),
        "match_pct": match_pct,
        "missing_count": len(missing_from_actual),
        "extra_count": len(extra_in_actual),
        "missing_lines": missing_from_actual[:20],
        "extra_lines": extra_in_actual[:20],
        "cheating_violations": cheating_violations,
        "anti_cheating_audit": "FAILED" if has_violations else "PASSED",
        "diff": diff_str,
        "summary": summary,
        "report": report,
    }
