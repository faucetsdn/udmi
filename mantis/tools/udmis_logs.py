"""UDMIS backend runtime log retrieval with explicit evidence-tier resolution.

Mantis could previously see the client side of an incident (sequencer, pubber,
validator output) but had no way at all to see what the UDMIS backend actually
did at runtime: `get_test_logs` captures tmux panes of a *live* session, and the
cloud query helper was never registered as a tool. Backend hypotheses were
therefore argued from source alone, with nothing marking that distinction.

This module resolves backend runtime evidence into exactly one declared tier:

  LOCAL_FILE    - the stack ran locally and `out/udmis.log` still holds the window
  CLOUD         - the stack ran in GCP and the window is still in Cloud Logging
  UNAVAILABLE   - the query ran and there is no runtime evidence to be had
  INDETERMINATE - the query could not run, so nothing was established either way

The tier is always reported, along with why every higher tier was rejected. An
UNAVAILABLE result is a failure, not an empty success: it never substitutes
source code for runtime evidence, and it never returns silently empty. The
caller is told exactly which declaration its conclusions must carry.

UNAVAILABLE and INDETERMINATE are kept apart on purpose. The first is a finding
about the incident; the second is a fault in this machine. Reporting the second
as the first invites the caller to assert that the logs expired when all that
actually happened is that the query never executed.
"""

import os
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional

TIER_LOCAL_FILE = "LOCAL_FILE"
TIER_CLOUD = "CLOUD"
TIER_UNAVAILABLE = "UNAVAILABLE"
# A query that could not be executed at all. This is deliberately NOT the same as
# UNAVAILABLE: "the logs do not exist" is a claim about the incident, while "the
# query could not run" is a fact about this machine. Collapsing the two let a
# missing client library be reported to the Actor as evidence that the logs had
# outlived their retention, which is a fabricated claim about the world.
TIER_INDETERMINATE = "INDETERMINATE"

DEFAULT_LOCAL_LOG = os.path.join("out", "udmis.log")

# Cloud Logging's _Default bucket retains entries for 30 days unless the project
# overrides it. Used only to explain an empty result, never to skip the query.
DEFAULT_RETENTION_DAYS = 30

DECLARATIONS = {
    TIER_LOCAL_FILE: "RUNTIME_EVIDENCE: LOCAL_FILE",
    TIER_CLOUD: "RUNTIME_EVIDENCE: CLOUD",
    TIER_UNAVAILABLE: "RUNTIME_EVIDENCE: NONE (source inference only)",
    TIER_INDETERMINATE: "RUNTIME_EVIDENCE: NONE (source inference only)",
}

_MAX_LINE_CHARS = 400


def _parse_timestamp(value: str, label: str) -> datetime:
    """Parses an ISO-8601 timestamp, failing loudly on anything else."""
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (ValueError, AttributeError) as exc:
        raise ValueError(
            f"{label} must be an ISO-8601 timestamp (e.g. '2026-09-14T18:00:00Z'), got {value!r}: {exc}"
        ) from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


def _read_local_log(
    log_path: str, pattern: Optional[str], max_lines: int
) -> Dict[str, Any]:
    """Extracts matching lines from a local UDMIS log file."""
    with open(log_path, "r", encoding="utf-8", errors="ignore") as handle:
        all_lines = handle.readlines()

    if pattern:
        needle = pattern.lower()
        matching = [
            (number, text.rstrip("\n"))
            for number, text in enumerate(all_lines, 1)
            if needle in text.lower()
        ]
    else:
        matching = [
            (number, text.rstrip("\n"))
            for number, text in enumerate(all_lines, 1)
        ]

    # Keep the tail: the end of a run holds the failure, the head holds startup.
    selected = matching[-max_lines:]
    return {
        "total_lines_in_file": len(all_lines),
        "total_matching_lines": len(matching),
        "lines": [
            {"line": number, "text": text[:_MAX_LINE_CHARS]}
            for number, text in selected
        ],
    }


def get_udmis_runtime_logs(
    pattern: Optional[str] = None,
    window_start: Optional[str] = None,
    window_end: Optional[str] = None,
    project_spec: Optional[str] = None,
    log_filter: Optional[str] = None,
    max_lines: int = 200,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Retrieves UDMIS backend runtime logs and declares which evidence tier they came from.

    Resolution order is fixed: the local UDMIS log file, then GCP Cloud Logging,
    then UNAVAILABLE. Every rejected tier is reported with its reason, so an
    UNAVAILABLE result states why no runtime evidence exists rather than failing
    quietly. Conclusions drawn must carry the returned 'required_declaration'.

    Args:
        pattern: Literal substring to match (case-insensitive) in log lines. For the
            cloud tier this becomes a Cloud Logging free-text search.
        window_start: Optional ISO-8601 start of the incident window.
        window_end: Optional ISO-8601 end of the incident window.
        project_spec: Target spec of the run under investigation (e.g.
            '//gbos/my-project'). Required to reach the cloud tier.
        log_filter: Explicit Cloud Logging filter, for when a free-text search on
            'pattern' is too coarse. Deployment shape is never assumed.
        max_lines: Maximum number of log lines or entries to return.
        udmi_root: Optional override for the UDMI repository root directory.
    """
    root = os.path.abspath(
        udmi_root
        if udmi_root is not None
        else os.path.join(os.path.dirname(__file__), "..", "..")
    )

    try:
        start_dt = _parse_timestamp(window_start, "window_start") if window_start else None
        end_dt = _parse_timestamp(window_end, "window_end") if window_end else None
    except ValueError as exc:
        return {"status": "ERROR", "error": str(exc)}

    if start_dt and end_dt and end_dt < start_dt:
        return {
            "status": "ERROR",
            "error": f"window_end ({window_end}) precedes window_start ({window_start}).",
        }

    tier_resolution: List[str] = []

    # Whether the incident under investigation happened against a cloud target. A
    # local log file records a locally-run stack, so it is not evidence about a
    # cloud run no matter how recent it is: offering it invites exactly the error
    # of citing an unrelated local run to rule on a cloud incident.
    parsed_spec = None
    if project_spec:
        from mantis.project_spec import parse_project_spec

        parsed_spec = parse_project_spec(project_spec)
    targets_cloud = bool(parsed_spec and parsed_spec.get("is_cloud"))

    # --- Tier 1: local UDMIS log written by `bin/udmi start` -------------------
    log_path = os.environ.get("UDMIS_LOG") or os.path.join(root, DEFAULT_LOCAL_LOG)
    if not os.path.isabs(log_path):
        log_path = os.path.join(root, log_path)

    if targets_cloud:
        tier_resolution.append(
            f"{TIER_LOCAL_FILE} rejected: the incident targets cloud project "
            f"'{parsed_spec.get('project')}' via provider '{parsed_spec.get('provider')}'. "
            f"Any local UDMIS log records a locally-run stack and cannot contain that run."
        )
    elif not os.path.isfile(log_path):
        tier_resolution.append(
            f"{TIER_LOCAL_FILE} rejected: no UDMIS log file at {log_path}. The stack was "
            f"not run locally, or its output directory has been cleaned."
        )
    elif os.path.getsize(log_path) == 0:
        tier_resolution.append(
            f"{TIER_LOCAL_FILE} rejected: {log_path} is empty."
        )
    else:
        modified = datetime.fromtimestamp(os.path.getmtime(log_path), tz=timezone.utc)
        if start_dt and modified < start_dt:
            tier_resolution.append(
                f"{TIER_LOCAL_FILE} rejected: {log_path} was last written at "
                f"{modified.isoformat()}, before the requested window start "
                f"{start_dt.isoformat()}. It cannot contain the incident."
            )
        else:
            extracted = _read_local_log(log_path, pattern, max_lines)
            result = {
                "status": "SUCCESS",
                "tier": TIER_LOCAL_FILE,
                "source": os.path.relpath(log_path, root),
                "file_modified": modified.isoformat(),
                "pattern": pattern,
                "required_declaration": DECLARATIONS[TIER_LOCAL_FILE],
                "tier_resolution": tier_resolution,
                **extracted,
            }
            if pattern and extracted["total_matching_lines"] == 0:
                # The distinction matters: the log was readable and matched nothing,
                # which is evidence about the pattern, not absence of runtime evidence.
                result["note"] = (
                    f"The UDMIS log is available and was searched in full "
                    f"({extracted['total_lines_in_file']} lines), but no line contains "
                    f"{pattern!r}. This is runtime evidence of absence for that pattern, not "
                    f"an absence of runtime evidence."
                )
            return result

    # --- Tier 2: GCP Cloud Logging --------------------------------------------
    if not project_spec:
        tier_resolution.append(
            f"{TIER_CLOUD} rejected: no project_spec was supplied, so there is no project "
            f"whose logs could be queried."
        )
    else:
        parsed = parsed_spec
        project_id = parsed.get("project")
        if not targets_cloud:
            tier_resolution.append(
                f"{TIER_CLOUD} rejected: project_spec '{project_spec}' resolves to a local "
                f"target (provider '{parsed.get('provider')}', project '{project_id}'). "
                f"A local run writes no cloud logs."
            )
        elif not (pattern or log_filter):
            return {
                "status": "ERROR",
                "error": (
                    "Querying cloud logs requires either 'pattern' (free-text search) or "
                    "'log_filter' (explicit Cloud Logging filter). UDMIS deployment shape "
                    "is not assumed, so no default resource filter is applied."
                ),
                "tier_resolution": tier_resolution,
            }
        else:
            from mantis.tools.cloud_logs import query_cloud_logs

            effective_filter = log_filter if log_filter else f'"{pattern}"'
            result = query_cloud_logs(
                project_id=project_id,
                filter_query=effective_filter,
                start_time=start_dt.isoformat() if start_dt else None,
                end_time=end_dt.isoformat() if end_dt else None,
                limit=max_lines,
            )
            if result.get("status") != "SUCCESS":
                # The query did not run. Returning UNAVAILABLE here would tell the
                # Actor the logs do not exist, which it cannot distinguish from a
                # broken client and will narrate as retention expiry.
                return {
                    "status": TIER_INDETERMINATE,
                    "tier": TIER_INDETERMINATE,
                    "pattern": pattern,
                    "error": result.get("error"),
                    "tier_resolution": tier_resolution
                    + [
                        f"{TIER_CLOUD} could not be queried: the query against project "
                        f"'{project_id}' raised: {result.get('error')}"
                    ],
                    "required_declaration": DECLARATIONS[TIER_INDETERMINATE],
                    "guidance": (
                        "The cloud log query FAILED TO EXECUTE. This says nothing about "
                        "whether the logs exist: it is a fault in this environment "
                        "(missing client library, absent credentials, or no network). "
                        "Do NOT report this as the logs being absent, expired, or "
                        "outside their retention window; you have not established "
                        "that. Proceed by reasoning from the backend source and "
                        f"declare '{DECLARATIONS[TIER_INDETERMINATE]}', or report the "
                        "environment fault so it can be repaired."
                    ),
                }
            elif result.get("count", 0) == 0:
                reason = (
                    f"{TIER_CLOUD} rejected: query against project '{project_id}' with filter "
                    f"{effective_filter} returned no entries."
                )
                if start_dt:
                    age = datetime.now(timezone.utc) - start_dt
                    if age > timedelta(days=DEFAULT_RETENTION_DAYS):
                        reason += (
                            f" The window starts {age.days} days ago, beyond the "
                            f"{DEFAULT_RETENTION_DAYS}-day default Cloud Logging retention, "
                            f"so the entries have most likely expired."
                        )
                tier_resolution.append(reason)
            else:
                return {
                    "status": "SUCCESS",
                    "tier": TIER_CLOUD,
                    "source": f"cloud-logging:{project_id}",
                    "filter": result.get("filter"),
                    "pattern": pattern,
                    "total_matching_lines": result.get("count"),
                    "entries": result.get("entries"),
                    "required_declaration": DECLARATIONS[TIER_CLOUD],
                    "tier_resolution": tier_resolution,
                }

    # --- Tier 3: no runtime evidence exists ------------------------------------
    return {
        "status": TIER_UNAVAILABLE,
        "tier": TIER_UNAVAILABLE,
        "pattern": pattern,
        "tier_resolution": tier_resolution,
        "required_declaration": DECLARATIONS[TIER_UNAVAILABLE],
        "guidance": (
            "No UDMIS runtime evidence is obtainable for this incident. Reason through the "
            "backend source instead, and label every backend conclusion with "
            f"'{DECLARATIONS[TIER_UNAVAILABLE]}' stating that the logs needed to prove it are "
            "not available and that the mechanism is plausible from the source. Do not present "
            "an inference as an observation."
        ),
    }
