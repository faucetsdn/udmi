"""Real sequencer compliance and report downloads for UDMI Workbench (Layer 4).

Every number reported here is read out of an artifact that `bin/sequencer`
actually wrote. This module never infers, interpolates, or defaults a verdict:
a device that has not been tested is reported as untested, not as zero percent
compliant. Scoring an untested device as 0/0 would put a real-looking red row
in the compliance grid for a device nobody ever ran, which is the single most
damaging thing a certification view can do.

Compliance sources:
  * Structured results -> `<site_model>/out/sequencer_<device>.json`
  * Human report       -> `<site_model>/out/devices/<device>/results.md`
  * Raw result lines   -> `<site_model>/out/devices/<device>/RESULT.log`

The structured json is authoritative for counts and scores; the other two
files are download-only artifacts. Which of the three exist varies per device
in practice (a device can have a sequencer json with no markdown report, or a
RESULT.log with no json), so presence is probed per file rather than assumed
from a single marker.

Scoring model
-------------
Scores are NOT a device-level quantity. UDMI scores a (feature bucket, stage)
pair, and this module reproduces `bin/sequencer_report` exactly so the Workbench
and the `results.md` an operator downloads can never disagree:

  * A sequence contributes `scoring.value` / `scoring.total` to its own
    (bucket, stage) cell only.                  bin/sequencer_report:299-301
  * `total == 0` means the sequence was skipped, i.e. NOT APPLICABLE. It is not
    a zero score. SequenceBase sets `total = isSkip ? 0 : base`.
                                                SequenceBase.java:1001
  * A cell counts as exercised only when `total > 0`.  bin/sequencer_report:303
  * A cell passes only on full marks, `scored == total`. bin/sequencer_report:308
  * A bucket's verdict considers ONLY the `stable` and `beta` stages, and only
    the exercised ones. A bucket whose sole results are alpha or preview is
    NOT ASSESSED, even at full marks.           bin/sequencer_report:27,456-463
  * A stage column is shown only when some bucket exercised it.
                                                bin/sequencer_report:417-420,465

Summing every sequence's score into one device figure -- which this module used
to do -- conflates buckets, mixes unreleased alpha tests into a certification
number, and makes "skipped" indistinguishable from "failed". No such figure is
produced any more.

Defensive reading
-----------------
`schema/state_validation.json` declares no required properties, and the
sequencer legitimately omits fields: a run killed mid-test leaves
`result: "start"` with no `scoring` and no `summary` at all. Every field is
therefore read as optional, and a sequence that cannot be placed on the matrix
is surfaced in `unscored` with the reason rather than silently dropped or
defaulted to zero.
"""

import json
import os
import re
from typing import Any, Dict, List, Optional, Tuple

from workbench.server.discovery import (
    collect_run_targets,
    list_devices,
    resolve_site_model,
)
from workbench.server.paths import display

# Matches artifacts.MAX_READ_BYTES. A report that exceeds it is refused rather
# than truncated: a half-sent RESULT.log looks like a short run instead of a
# failed download, and an operator would have no way to tell the difference.
MAX_REPORT_BYTES = 4 * 1024 * 1024

# The canonical result buckets. Anything the sequencer emits that is not one of
# these (`errr` for an aborted run, `start` for one that never finished) is
# deliberately left unbucketed so it shows up as a discrepancy between `total`
# and pass+fail+skip instead of being quietly folded into one of them.
RESULT_BUCKETS = ("pass", "fail", "skip")

#: Stage columns, most-released first. Mirrors bin/sequencer_report:23.
STAGE_ORDER = ("stable", "beta", "preview", "alpha")

#: Only these stages decide whether a feature bucket passed. A bucket tested
#: solely at alpha or preview is reported as not assessed, never as a pass or a
#: failure. Mirrors bin/sequencer_report:27.
STAGES_FOR_PASS = ("stable", "beta")

# kind -> (relative path template, download suffix, content type)
REPORT_KINDS: Dict[str, Tuple[str, str, str]] = {
    "results_md": (os.path.join("out", "devices", "{device}", "results.md"),
                   "results.md", "text/markdown"),
    "result_log": (os.path.join("out", "devices", "{device}", "RESULT.log"),
                   "RESULT.log", "text/plain"),
    "sequencer_json": (os.path.join("out", "sequencer_{device}.json"),
                       "sequencer.json", "application/json"),
}


class ComplianceError(Exception):
    """Raised when a site model, device, report kind, or report file is absent."""


def _validate_device_id(device_id: str, site_dir: str) -> str:
    """Proves `device_id` names a real device directory and cannot traverse.

    The device id is concatenated into artifact paths, so it is checked as a
    bare directory name before it is ever joined: a separator or `..` inside it
    would let a caller address files anywhere on disk through what looks like a
    device lookup. Existence is then confirmed against the site model's
    `devices/` directory, which is the only authoritative list of device ids.
    """
    if not device_id:
        raise ComplianceError("device_id parameter is required")
    if os.sep in device_id or "/" in device_id or "\\" in device_id or ".." in device_id:
        raise ComplianceError(
            f"Invalid device_id '{device_id}': must be a plain device name with no "
            "path separators or parent references."
        )

    device_dir = os.path.join(site_dir, "devices", device_id)
    if not os.path.isdir(device_dir):
        raise ComplianceError(f"Device '{device_id}' not found: {device_dir}")
    return device_id


def _sequencer_json_path(site_dir: str, device_id: str) -> str:
    return os.path.join(site_dir, "out", f"sequencer_{device_id}.json")


def _report_path(site_dir: str, device_id: str, kind: str) -> str:
    template = REPORT_KINDS[kind][0]
    return os.path.join(site_dir, template.format(device=device_id))


def _available_reports(site_dir: str, device_id: str) -> List[str]:
    """Lists the report kinds that are genuinely present for this device."""
    return [
        kind
        for kind in REPORT_KINDS
        if os.path.isfile(_report_path(site_dir, device_id, kind))
    ]


def _normalise(raw: Any) -> str:
    """Lowercases a sequencer enum value, leaving unrecognised values intact.

    Case is the only thing normalised. An unexpected verdict is passed through
    verbatim so it reaches the UI as an unfamiliar value that demands attention,
    rather than being coerced into `fail` (which hides a broken run as a test
    failure) or `skip` (which hides it entirely).
    """
    if raw is None:
        return ""
    return str(raw).strip().lower()


def _scoring(entry: Dict[str, Any]) -> Optional[Dict[str, int]]:
    """Reads a sequence's score, or None when the sequencer did not record one.

    A missing `scoring` block is a real state, not a zero: `startSequenceStatus`
    writes `result: "start"` with no scoring, so every sequence of a run that
    was killed mid-flight would otherwise look exactly like a sequence that
    scored nothing. Returning None keeps those two apart.
    """
    scoring = entry.get("scoring")
    if not isinstance(scoring, dict):
        return None
    try:
        return {
            "value": int(scoring.get("value") or 0),
            "total": int(scoring.get("total") or 0),
        }
    except (TypeError, ValueError):
        return None


def _capabilities(entry: Dict[str, Any]) -> Dict[str, str]:
    """Per-capability verdicts, the only capability data the sequencer writes.

    `CapabilityValidationState` declares score and total, but
    `collectCapabilityResult` only ever populates `result` (and `status` on
    failure), so no capability arithmetic is attempted here. The capability
    contribution is already folded into the sequence's own `scoring`.
    """
    capabilities = entry.get("capabilities")
    if not isinstance(capabilities, dict):
        return {}
    return {
        name: _normalise((value or {}).get("result"))
        for name, value in capabilities.items()
        if isinstance(value, dict)
    }


def _parse_sequences(document: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Flattens the feature -> sequences tree into one row per sequence.

    The feature bucket and stage are carried onto every row because they are
    what the score belongs to; a row on its own is not a scorable unit.
    """
    rows: List[Dict[str, Any]] = []
    features = document.get("features")
    if not isinstance(features, dict):
        return rows

    for feature in sorted(features):
        container = features[feature]
        sequences = (container or {}).get("sequences") if isinstance(container, dict) else None
        if not isinstance(sequences, dict):
            continue
        for name in sorted(sequences):
            entry = sequences[name] if isinstance(sequences[name], dict) else {}
            status = entry.get("status") if isinstance(entry.get("status"), dict) else {}
            rows.append({
                "name": name,
                "feature": feature,
                "result": _normalise(entry.get("result")),
                "stage": _normalise(entry.get("stage")),
                "score": _scoring(entry),
                "capabilities": _capabilities(entry),
                "message": status.get("message"),
                "summary": entry.get("summary"),
                "timestamp": status.get("timestamp"),
            })
    return rows


def _empty_counts() -> Dict[str, int]:
    return {"pass": 0, "fail": 0, "skip": 0, "total": 0}


def _tally(rows: List[Dict[str, Any]]) -> Dict[str, int]:
    """Counts sequences per verdict. Deliberately not a score."""
    counts = _empty_counts()
    for row in rows:
        counts["total"] += 1
        if row["result"] in RESULT_BUCKETS:
            counts[row["result"]] += 1
    return counts


#: The `- Start <timestamp>` line `bin/sequencer_report` writes as the third line
#: of every report. etc/sequencer_report.md.template:3
_REPORT_START_RE = re.compile(r"^-\s*Start\s+(\S+)\s*$", re.MULTILINE)


def _report_provenance(
    site_dir: str, device_id: str, run_start: Optional[str]
) -> Dict[str, Any]:
    """Checks whether `results.md` describes the run the json describes.

    `results.md` is only rewritten when someone runs `bin/sequencer_report`, but
    `sequencer_<device>.json` is rewritten by every sequencer run. The two
    therefore drift apart in real lab site models, and an operator who downloads
    the markdown can end up holding a report of a completely different run from
    the one the compliance grid is showing. Both timestamps are reported so the
    UI can say so out loud instead of letting the download look authoritative.
    """
    provenance: Dict[str, Any] = {
        "run_start": run_start,
        "results_md_start": None,
        "results_md_matches_run": None,
    }

    path = _report_path(site_dir, device_id, "results_md")
    if not os.path.isfile(path):
        return provenance

    try:
        # The marker is on line 3; reading the head is enough and keeps this
        # cheap across a site model with dozens of devices.
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            head = fh.read(512)
    except OSError:
        return provenance

    match = _REPORT_START_RE.search(head)
    if not match:
        return provenance

    provenance["results_md_start"] = match.group(1)
    if run_start:
        provenance["results_md_matches_run"] = match.group(1) == run_start
    return provenance


def _all_or_none(verdicts: List[bool]) -> Optional[bool]:
    """`all()` over the list, or None when there is nothing to judge.

    None is the third outcome the compliance model genuinely has: not assessed.
    Mirrors bin/sequencer_report.all_or_none.
    """
    if not verdicts:
        return None
    return all(verdicts)


def _empty_stage_cells() -> Dict[str, Dict[str, int]]:
    return {stage: {"scored": 0, "total": 0, "sequences": 0} for stage in STAGE_ORDER}


def _build_matrix(
    rows: List[Dict[str, Any]]
) -> Tuple[Dict[str, Any], List[str], List[Dict[str, Any]]]:
    """Builds the bucket x stage score matrix, the stage columns, and the strays.

    Returns `(features, stages_present, unscored)` where `features` maps each
    bucket to its per-stage cells plus that bucket's overall verdict, and
    `unscored` names every sequence that could not be placed, with the reason.
    """
    features: Dict[str, Any] = {}
    unscored: List[Dict[str, Any]] = []

    for row in rows:
        bucket = row["feature"]
        cells = features.setdefault(
            bucket, {"stages": _empty_stage_cells(), "overall": None}
        )

        if row["stage"] not in STAGE_ORDER:
            unscored.append({
                "name": row["name"], "feature": bucket, "stage": row["stage"],
                "result": row["result"],
                "reason": (
                    f"Stage '{row['stage']}' is not one of {', '.join(STAGE_ORDER)}, "
                    "so this sequence has no column to be scored in."
                    if row["stage"]
                    else "The sequencer recorded no stage for this sequence."
                ),
            })
            continue

        if row["score"] is None:
            unscored.append({
                "name": row["name"], "feature": bucket, "stage": row["stage"],
                "result": row["result"],
                "reason": (
                    "The sequencer recorded no score for this sequence, which "
                    f"reported '{row['result'] or 'no result'}'. A run that was "
                    "interrupted leaves its sequences in this state."
                ),
            })
            continue

        cell = cells["stages"][row["stage"]]
        cell["scored"] += row["score"]["value"]
        cell["total"] += row["score"]["total"]
        cell["sequences"] += 1

    # A stage earns a column only once some bucket actually exercised it, so a
    # site whose beta tests all skipped does not grow an empty Beta column.
    stages_present = [
        stage
        for stage in STAGE_ORDER
        if any(cells["stages"][stage]["total"] > 0 for cells in features.values())
    ]

    for cells in features.values():
        cells["overall"] = _all_or_none([
            cells["stages"][stage]["scored"] == cells["stages"][stage]["total"]
            for stage in STAGES_FOR_PASS
            if cells["stages"][stage]["total"] > 0
        ])

    return features, stages_present, unscored


def _device_verdict(features: Dict[str, Any]) -> str:
    """Rolls the bucket verdicts into one word, preserving 'not assessed'.

    Buckets never exercised at a releasable stage contribute nothing. If no
    bucket was, the device is `not_evaluated` -- which is emphatically not a
    pass, and equally not a failure.
    """
    verdicts = [
        cells["overall"] for cells in features.values() if cells["overall"] is not None
    ]
    decided = _all_or_none(verdicts)
    if decided is None:
        return "not_evaluated"
    return "pass" if decided else "fail"


def _untested(device_id: str, reason: str, reports: List[str],
              targets: List[Dict[str, Any]],
              provenance: Dict[str, Any]) -> Dict[str, Any]:
    """Builds the record for a device with no usable structured results.

    The quantities are present but empty purely so the shape stays uniform for
    the caller; `has_results` is the field that decides whether they mean
    anything, and `reason` always names the concrete obstacle.
    """
    return {
        "device_id": device_id,
        "has_results": False,
        "reason": reason,
        "last_run": None,
        "start_time": None,
        "udmi_version": None,
        "status_message": None,
        "counts": _empty_counts(),
        "features": {},
        "stages": [],
        "verdict": "not_evaluated",
        "sequences": [],
        "unscored": [],
        "targets": targets,
        "provenance": provenance,
        "reports": reports,
    }


def _device_compliance(
    udmi_root: str, site_dir: str, device_id: str
) -> Dict[str, Any]:
    """Reads one device's compliance record, reporting absence as absence."""
    reports = _available_reports(site_dir, device_id)
    json_path = _sequencer_json_path(site_dir, device_id)

    # Targets record what this device was actually pointed at, so they are read
    # even when the structured results are missing or corrupt: knowing which
    # registry a run went to is most useful precisely when it will not parse.
    targets = collect_run_targets(site_dir, device_id)

    if not os.path.isfile(json_path):
        # A rendered report can outlive the scoring file it came from: out/ is
        # pruned per-run, and results.md is written to a different path than
        # sequencer_<device>.json. Saying "no results" in that case denies a
        # report the operator can see on disk, so the two cases are worded
        # differently. Neither one is ever scored.
        if reports:
            reason = (
                f"No scored results at {display(json_path, udmi_root)}, so this "
                "device cannot be assessed. An earlier report is still on disk "
                "and is linked below, but it carries no per-sequence scoring "
                "to rebuild the matrix from. Re-run bin/sequencer to score it."
            )
        else:
            reason = (
                f"No sequencer results found at {display(json_path, udmi_root)}. "
                "Run bin/sequencer for this device to produce them."
            )
        return _untested(
            device_id,
            reason,
            reports,
            targets,
            _report_provenance(site_dir, device_id, None),
        )

    try:
        with open(json_path, "r", encoding="utf-8") as fh:
            document = json.load(fh)
    except json.JSONDecodeError as exc:
        # A corrupt results file is surfaced as "no trustworthy results" rather
        # than raised: one unreadable device must not blank out the whole site's
        # compliance view, but it must never be scored either.
        return _untested(
            device_id,
            f"Invalid sequencer results in {display(json_path, udmi_root)}: {exc}",
            reports,
            targets,
            _report_provenance(site_dir, device_id, None),
        )
    except OSError as exc:
        return _untested(
            device_id,
            f"Cannot read {display(json_path, udmi_root)}: {exc}",
            reports,
            targets,
            _report_provenance(site_dir, device_id, None),
        )

    if not isinstance(document, dict):
        return _untested(
            device_id,
            f"Invalid sequencer results in {display(json_path, udmi_root)}: "
            f"expected a JSON object, found {type(document).__name__}.",
            reports,
            targets,
            _report_provenance(site_dir, device_id, None),
        )

    rows = _parse_sequences(document)
    features, stages, unscored = _build_matrix(rows)
    status = document.get("status") if isinstance(document.get("status"), dict) else {}

    return {
        "device_id": device_id,
        "has_results": True,
        "reason": None,
        "last_run": document.get("timestamp"),
        "start_time": document.get("start_time"),
        "udmi_version": document.get("udmi_version"),
        "status_message": status.get("message"),
        "counts": _tally(rows),
        "features": features,
        "stages": stages,
        "verdict": _device_verdict(features),
        "sequences": rows,
        "unscored": unscored,
        "targets": targets,
        "provenance": _report_provenance(
            site_dir, device_id, document.get("start_time")
        ),
        "reports": reports,
    }


def site_compliance(udmi_root: str, site_model: str) -> Dict[str, Any]:
    """Builds the compliance matrix for every device in a site model.

    Every device that discovery finds appears in the result, tested or not.
    Omitting untested devices would make a site look fully covered when only a
    subset was ever run, so absence is reported explicitly and counted in
    `totals.devices` while being excluded from `totals.devices_with_results`.

    The site totals count devices by verdict and sequences by result. They carry
    no score, because a score spanning devices is not a quantity UDMI defines.
    """
    site_dir = resolve_site_model(udmi_root, site_model)

    devices: List[Dict[str, Any]] = []
    totals = {"devices": 0, "devices_with_results": 0, "pass": 0, "fail": 0,
              "skip": 0, "total": 0, "devices_passing": 0, "devices_failing": 0,
              "devices_not_evaluated": 0}

    for device in list_devices(udmi_root, site_model):
        record = _device_compliance(udmi_root, site_dir, device["device_id"])
        devices.append(record)

        totals["devices"] += 1
        if record["has_results"]:
            totals["devices_with_results"] += 1
        for bucket in (*RESULT_BUCKETS, "total"):
            totals[bucket] += record["counts"][bucket]

        if record["verdict"] == "pass":
            totals["devices_passing"] += 1
        elif record["verdict"] == "fail":
            totals["devices_failing"] += 1
        else:
            totals["devices_not_evaluated"] += 1

    # The union of stages any device exercised, so the site view offers the same
    # columns as the per-device matrices without inventing empty ones.
    stages = [
        stage
        for stage in STAGE_ORDER
        if any(stage in record["stages"] for record in devices)
    ]

    return {
        "site_model": site_model,
        "devices": devices,
        "totals": totals,
        "stages": stages,
        "stages_for_pass": list(STAGES_FOR_PASS),
    }


def device_report(
    udmi_root: str, site_model: str, device_id: str, kind: str
) -> Dict[str, Any]:
    """Reads one downloadable sequencer report for a device.

    Bytes are returned rather than text: `content_type` already states how the
    payload should be interpreted, and re-encoding a report on the way out would
    let this module alter a file an operator may be attaching to a certification
    submission.
    """
    if kind not in REPORT_KINDS:
        raise ComplianceError(
            f"Unknown report kind '{kind}'. Valid kinds: "
            f"{', '.join(sorted(REPORT_KINDS))}."
        )

    site_dir = resolve_site_model(udmi_root, site_model)
    _validate_device_id(device_id, site_dir)

    _, suffix, content_type = REPORT_KINDS[kind]
    target = _report_path(site_dir, device_id, kind)
    if not os.path.isfile(target):
        raise ComplianceError(
            f"No '{kind}' report for device '{device_id}': {display(target, udmi_root)} "
            "does not exist."
        )

    size = os.path.getsize(target)
    if size > MAX_REPORT_BYTES:
        raise ComplianceError(
            f"Report {display(target, udmi_root)} is {size} bytes, exceeding the "
            f"{MAX_REPORT_BYTES} byte download limit."
        )

    with open(target, "rb") as fh:
        content = fh.read()

    return {
        "filename": f"{device_id}_{suffix}",
        "content_type": content_type,
        "content": content,
        "size": size,
        "path": display(target, udmi_root),
    }
