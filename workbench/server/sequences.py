"""Sequence catalog discovery and stage gating (Layer 4).

The catalog comes from the COMPILED validator, via `bin/sequencer_catalog`,
which reflects over every `@Test` method in the sequence classes and reports
its `@Feature` (bucket, stage, score, nostate, facets) and `@Summary`. That is
the same set of methods `bin/sequencer` can run, so new and ALPHA-stage tests
are never missing, which they were when the catalog was scraped from
`docs/specs/sequences/generated.md` (ALPHA is excluded there by
`bin/gencode_seq`) and `etc/sequencer.out`.

Per-test documentation is joined onto each compiled entry:
  * steps  -> `validator/sequences/<name>/sequence.md`, the per-test cache that
              `bin/sequencer_cache` maintains and that `bin/gencode_seq`
              concatenates into generated.md. It is the only source that also
              covers ALPHA tests, so generated.md itself is not read.
  * spec   -> a specification page chosen from the feature bucket
              (`BUCKET_DOCS`); every mapped path is verified to exist.

The compiled part is cached against the validator jar's mtime; step docs are
cheap and are re-read on every call so an updated cache shows up immediately.
If the catalog cannot be built the error is raised, never replaced by a
partial list from the static files.
"""

import json
import os
import re
import subprocess
import threading
from typing import Any, Dict, List, Optional, Tuple

from workbench.server.runner import stages_admitted

CATALOG_SCRIPT = os.path.join("bin", "sequencer_catalog")
VALIDATOR_JAR = os.path.join("validator", "build", "libs", "validator-1.0-SNAPSHOT-all.jar")
STEP_DOCS_DIR = os.path.join("validator", "sequences")
STEP_DOC_FILE = "sequence.md"
CATALOG_VERSION = 1
CATALOG_TIMEOUT_SEC = 120

REQUIRED_ENTRY_KEYS = (
    "name", "bucket", "stage", "score", "nostate", "ignored",
    "timeout_ms", "declaring_class", "capabilities",
)

SEQUENCE_HEADING_RE = re.compile(r"^##\s+([A-Za-z0-9_+]+)\s+\(([^)]+)\)")
STEP_RE = re.compile(r"^\d+\.\s+(.*)$")
STEP_DETAIL_RE = re.compile(r"^\s+[*\-]\s+(.*)$")
OUTCOME_RE = re.compile(r"^Test\s+(?:passed|failed|skipped)\b.*$", re.IGNORECASE)

# Feature bucket (longest dotted prefix wins) -> governing documentation.
# `spec` is the behavioural specification, `message` the UDMI message page a
# device reads/writes for that feature. None means no such page exists.
BUCKET_DOCS: Dict[str, Dict[str, Optional[str]]] = {
    "system": {"spec": "docs/specs/sequences/config.md", "message": "docs/messages/system.md"},
    "system.mode": {"spec": "docs/specs/system_mode.md", "message": "docs/messages/system.md"},
    "system.software.updates": {"spec": "docs/specs/blob_updates.md",
                                "message": "docs/messages/config.md"},
    "endpoint": {"spec": "docs/specs/sequences/endpoint_reconfiguration.md",
                 "message": "docs/messages/config.md"},
    "discovery": {"spec": "docs/specs/sequences/discovery.md", "message": None},
    "enumeration": {"spec": "docs/specs/sequences/discovery.md", "message": None},
    "gateway": {"spec": "docs/specs/gateway.md", "message": None},
    "pointset": {"spec": None, "message": "docs/messages/pointset.md"},
    "writeback": {"spec": "docs/specs/sequences/writeback.md",
                  "message": "docs/messages/pointset.md"},
    "unknown": {"spec": None, "message": None},
}

_cache_lock = threading.Lock()
_compiled_cache: Dict[str, Tuple[float, List[Dict[str, Any]]]] = {}


class SequenceCatalogError(Exception):
    """Raised when the compiled sequence catalog cannot be produced or trusted."""


def _jar_mtime(udmi_root: str) -> float:
    jar = os.path.join(udmi_root, VALIDATOR_JAR)
    if not os.path.isfile(jar):
        raise SequenceCatalogError(
            f"Validator jar {jar} is missing, so the sequence catalog cannot be built. "
            "Run 'validator/bin/build' in the UDMI checkout."
        )
    return os.path.getmtime(jar)


def _validate_compiled(payload: Any) -> List[Dict[str, Any]]:
    if not isinstance(payload, dict) or payload.get("catalog_version") != CATALOG_VERSION:
        raise SequenceCatalogError(
            f"Unexpected sequence catalog format (want catalog_version {CATALOG_VERSION}); "
            "rebuild with 'validator/bin/build'."
        )
    entries = payload.get("sequences")
    if not isinstance(entries, list) or not entries:
        raise SequenceCatalogError("Compiled sequence catalog lists no sequences.")
    for entry in entries:
        missing = [key for key in REQUIRED_ENTRY_KEYS if key not in entry]
        if missing:
            raise SequenceCatalogError(
                f"Compiled catalog entry {entry.get('name')!r} is missing {', '.join(missing)}."
            )
    return entries


def parse_compiled_catalog(text: str) -> List[Dict[str, Any]]:
    """Parses and validates `bin/sequencer_catalog` JSON output."""
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise SequenceCatalogError(f"bin/sequencer_catalog emitted invalid JSON: {exc}") from exc
    return _validate_compiled(payload)


def _run_catalog_script(udmi_root: str) -> List[Dict[str, Any]]:
    script = os.path.join(udmi_root, CATALOG_SCRIPT)
    try:
        proc = subprocess.run(
            [script], cwd=udmi_root, capture_output=True, text=True,
            timeout=CATALOG_TIMEOUT_SEC, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise SequenceCatalogError(f"Could not run {script}: {exc}") from exc
    if proc.returncode != 0:
        detail = (proc.stderr or proc.stdout).strip() or f"exit code {proc.returncode}"
        raise SequenceCatalogError(f"bin/sequencer_catalog failed: {detail}")
    return parse_compiled_catalog(proc.stdout)


def _compiled_entries(udmi_root: str) -> List[Dict[str, Any]]:
    mtime = _jar_mtime(udmi_root)
    key = os.path.realpath(udmi_root)
    with _cache_lock:
        cached = _compiled_cache.get(key)
        if cached and cached[0] == mtime:
            return cached[1]
        entries = _run_catalog_script(udmi_root)
        _compiled_cache[key] = (mtime, entries)
        return entries


def parse_step_doc(text: str) -> Dict[str, Any]:
    """Parses one recorded `sequence.md` into heading, prose, steps and outcome.

    Steps keep their full text and their indented sub-bullets (the concrete
    config changes), numbered in document order.
    """
    heading = None
    documented_stage = None
    prose: List[str] = []
    steps: List[Dict[str, Any]] = []
    outcome = None

    for raw in text.split("\n"):
        line = raw.rstrip()
        if not line.strip():
            continue
        match = SEQUENCE_HEADING_RE.match(line.strip())
        if match:
            if heading is not None:
                raise SequenceCatalogError(f"Step doc has more than one heading: {line!r}")
            heading, documented_stage = match.group(1), match.group(2).strip()
            continue
        step = STEP_RE.match(line)
        if step:
            steps.append({"text": step.group(1).strip(), "details": []})
            continue
        detail = STEP_DETAIL_RE.match(line)
        if detail and steps:
            steps[-1]["details"].append(detail.group(1).strip())
            continue
        if OUTCOME_RE.match(line.strip()):
            outcome = line.strip()
            continue
        if not steps:
            prose.append(line.strip())

    if heading is None:
        raise SequenceCatalogError("Step doc has no '## <name> (<STAGE>)' heading.")
    return {
        "heading": heading,
        "documented_stage": documented_stage,
        "prose": " ".join(prose),
        "steps": steps,
        "reference_outcome": outcome,
    }


def _load_step_doc(udmi_root: str, name: str) -> Optional[Dict[str, Any]]:
    rel_path = os.path.join(STEP_DOCS_DIR, name, STEP_DOC_FILE)
    path = os.path.join(udmi_root, rel_path)
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8") as fh:
        try:
            parsed = parse_step_doc(fh.read())
        except SequenceCatalogError as exc:
            raise SequenceCatalogError(f"{rel_path}: {exc}") from exc
    base, _, facet = parsed["heading"].partition("+")
    if base != name:
        raise SequenceCatalogError(
            f"{rel_path} documents '{parsed['heading']}', not '{name}'; "
            "refresh it with bin/sequencer_cache."
        )
    parsed["facet"] = facet or None
    parsed["path"] = rel_path.replace(os.sep, "/")
    return parsed


def bucket_docs(udmi_root: str, bucket: str) -> Dict[str, Optional[str]]:
    """Returns the spec/message pages for a bucket by longest dotted prefix."""
    parts = bucket.split(".")
    for size in range(len(parts), 0, -1):
        docs = BUCKET_DOCS.get(".".join(parts[:size]))
        if docs is None:
            continue
        for kind, rel_path in docs.items():
            if rel_path and not os.path.isfile(os.path.join(udmi_root, rel_path)):
                raise SequenceCatalogError(
                    f"BUCKET_DOCS maps '{bucket}' {kind} to {rel_path}, which does not exist."
                )
        return dict(docs)
    raise SequenceCatalogError(
        f"No documentation mapping for feature bucket '{bucket}'; add it to "
        "workbench/server/sequences.py BUCKET_DOCS."
    )


def build_catalog(udmi_root: str, compiled: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Joins compiled entries with their step docs and spec links."""
    sequences = []
    for entry in compiled:
        step_doc = _load_step_doc(udmi_root, entry["name"])
        docs = bucket_docs(udmi_root, entry["bucket"])
        sequences.append({
            "name": entry["name"],
            "bucket": entry["bucket"],
            "stage": entry["stage"],
            "score": entry["score"],
            "nostate": entry["nostate"],
            "ignored": entry["ignored"],
            "timeout_ms": entry["timeout_ms"],
            "facets": entry.get("facets"),
            "summary": entry.get("summary"),
            "declaring_class": entry["declaring_class"],
            "capabilities": entry["capabilities"],
            "steps": step_doc["steps"] if step_doc else None,
            "steps_path": step_doc["path"] if step_doc else None,
            "reference_outcome": step_doc["reference_outcome"] if step_doc else None,
            "spec_path": docs["spec"],
            "message_path": docs["message"],
        })
    sequences.sort(key=lambda s: (s["bucket"], s["name"]))
    return sequences


def list_sequences(udmi_root: str) -> List[Dict[str, Any]]:
    """The full sequence catalog, from the compiled validator plus step docs."""
    return build_catalog(udmi_root, _compiled_entries(udmi_root))


def reject_stage_excluded(udmi_root: str, tests: List[str], min_stage: str) -> None:
    """Fails a run that names sequences the stage gate would silently skip.

    `bin/sequencer` defaults to min_stage=PREVIEW, and SequenceRunner only runs
    a test when its stage orders at or above that gate. Alpha-stage sequences
    (notably the `scan_*` discovery set) therefore produce no result at all
    rather than an error, which reads as "the test did not run" with no
    explanation.
    """
    if not tests:
        return

    admitted = set(stages_admitted(min_stage))
    catalog = {
        sequence["name"]: sequence["stage"]
        for sequence in _compiled_entries(udmi_root)
    }

    excluded = {}
    for name in tests:
        stage = catalog.get(name.split("+")[0])
        if stage and stage.upper() not in admitted:
            excluded[name] = stage.upper()
    if not excluded:
        return

    needed = sorted({stage for stage in excluded.values()})
    remedy = "ALPHA" if "ALPHA" in needed else needed[0]
    listing = ", ".join(f"{name} ({stage})" for name, stage in sorted(excluded.items()))
    raise ValueError(
        f"{len(excluded)} selected sequence(s) are below the '{min_stage}' minimum stage "
        f"and would be skipped without reporting a result: {listing}. "
        f"Set min_stage to '{remedy}' to include them, or deselect them."
    )
