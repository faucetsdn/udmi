"""Tests for the compiled sequence catalog (workbench/server/sequences.py).

A throwaway UDMI root stands in for the checkout: a placeholder validator jar,
a fake `bin/sequencer_catalog` that replays the fixture JSON (and counts its
invocations), recorded step docs, and the spec pages BUCKET_DOCS points at.
"""

import json
import os
import shutil
import stat

import pytest

from workbench.server import sequences
from workbench.server.sequences import SequenceCatalogError

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "sequencer_catalog.json")

WRITEBACK_DOC = """
## writeback_success (ALPHA)

Implements UDMI writeback and can successfully writeback to a point

1. Update config before target point has value_state default (null)
    * Set `pointset.sample_rate_sec` = `10`
    * Remove `pointset.points.filter_differential_pressure_setpoint.set_value`
1. Wait until target point has value_state default (null)
1. Update config before target point has value_state applied
    * Add `pointset.points.filter_differential_pressure_setpoint.set_value` = `60`
1. Wait until target point has value_state applied
1. Wait until target point to have target expected value

Test passed.
"""

SCAN_DOC = """
## scan_single_targeted+vendor (ALPHA)

Check results of a single scan targeting specific devices

1. Wait for discovery families defined
1. Check that discovery events were valid

Test passed.
"""

SKIPPED_DOC = """
## broken_config (STABLE)

Check that the device correctly handles a broken (non-json) config message.


Test skipped: Not a proxied device
"""


def _write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


@pytest.fixture(autouse=True)
def clear_cache():
    sequences._compiled_cache.clear()
    yield
    sequences._compiled_cache.clear()


@pytest.fixture
def udmi_root(tmp_path):
    root = tmp_path / "udmi"
    _write(str(root / sequences.VALIDATOR_JAR), "jar")
    shutil.copy(FIXTURE, str(tmp_path / "catalog.json"))
    script = root / sequences.CATALOG_SCRIPT
    _write(str(script), (
        "#!/bin/bash -e\n"
        f"echo run >> {tmp_path}/invocations\n"
        f"cat {tmp_path}/catalog.json\n"
    ))
    script.chmod(script.stat().st_mode | stat.S_IEXEC)
    for rel_path in {
        path for docs in sequences.BUCKET_DOCS.values() for path in docs.values() if path
    }:
        _write(str(root / rel_path), "# spec\n")
    steps = root / sequences.STEP_DOCS_DIR
    _write(str(steps / "writeback_success" / "sequence.md"), WRITEBACK_DOC)
    _write(str(steps / "scan_single_targeted" / "sequence.md"), SCAN_DOC)
    _write(str(steps / "broken_config" / "sequence.md"), SKIPPED_DOC)
    return str(root)


def _invocations(udmi_root):
    path = os.path.join(os.path.dirname(udmi_root), "invocations")
    with open(path, encoding="utf-8") as fh:
        return len(fh.read().split())


def _by_name(udmi_root):
    return {entry["name"]: entry for entry in sequences.list_sequences(udmi_root)}


def test_compiled_entries_all_listed_with_summaries(udmi_root):
    catalog = _by_name(udmi_root)

    assert set(catalog) == {
        "broken_config", "writeback_success", "scan_single_targeted", "writeback_invalid"
    }
    writeback = catalog["writeback_success"]
    assert writeback["stage"] == "ALPHA"
    assert writeback["summary"] == "Implements UDMI writeback and can successfully writeback to a point"
    assert writeback["declaring_class"].endswith("WritebackSequences")
    assert catalog["writeback_invalid"]["summary"] is None
    assert catalog["writeback_invalid"]["nostate"] is True
    assert catalog["scan_single_targeted"]["facets"] == "discovery"
    assert catalog["scan_single_targeted"]["capabilities"] == [{"name": "Enumerate", "stage": "ALPHA"}]


def test_full_numbered_steps_are_joined(udmi_root):
    writeback = _by_name(udmi_root)["writeback_success"]

    assert [step["text"] for step in writeback["steps"]] == [
        "Update config before target point has value_state default (null)",
        "Wait until target point has value_state default (null)",
        "Update config before target point has value_state applied",
        "Wait until target point has value_state applied",
        "Wait until target point to have target expected value",
    ]
    assert writeback["steps"][0]["details"] == [
        "Set `pointset.sample_rate_sec` = `10`",
        "Remove `pointset.points.filter_differential_pressure_setpoint.set_value`",
    ]
    assert writeback["steps_path"] == "validator/sequences/writeback_success/sequence.md"
    assert writeback["reference_outcome"] == "Test passed."


def test_step_doc_states_are_distinct(udmi_root):
    catalog = _by_name(udmi_root)

    assert catalog["scan_single_targeted"]["steps"][1]["text"] == "Check that discovery events were valid"
    # Recorded run with no steps (skipped) is an empty list, not "no doc".
    assert catalog["broken_config"]["steps"] == []
    assert catalog["broken_config"]["reference_outcome"] == "Test skipped: Not a proxied device"
    assert catalog["writeback_invalid"]["steps"] is None
    assert catalog["writeback_invalid"]["steps_path"] is None


def test_spec_links_follow_bucket(udmi_root):
    catalog = _by_name(udmi_root)

    assert catalog["writeback_success"]["spec_path"] == "docs/specs/sequences/writeback.md"
    assert catalog["writeback_success"]["message_path"] == "docs/messages/pointset.md"
    assert catalog["scan_single_targeted"]["spec_path"] == "docs/specs/sequences/discovery.md"
    assert catalog["scan_single_targeted"]["message_path"] is None
    assert catalog["broken_config"]["spec_path"] == "docs/specs/sequences/config.md"


def test_bucket_docs_map_to_real_repository_files():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    for bucket in sequences.BUCKET_DOCS:
        sequences.bucket_docs(repo_root, bucket)


def test_cache_keyed_on_jar_mtime(udmi_root):
    sequences.list_sequences(udmi_root)
    sequences.list_sequences(udmi_root)
    assert _invocations(udmi_root) == 1

    jar = os.path.join(udmi_root, sequences.VALIDATOR_JAR)
    stamp = os.path.getmtime(jar) + 10
    os.utime(jar, (stamp, stamp))
    sequences.list_sequences(udmi_root)
    assert _invocations(udmi_root) == 2


def test_missing_jar_fails_with_build_instruction(udmi_root):
    os.remove(os.path.join(udmi_root, sequences.VALIDATOR_JAR))
    with pytest.raises(SequenceCatalogError, match="validator/bin/build"):
        sequences.list_sequences(udmi_root)


def test_script_failure_is_surfaced_not_replaced(udmi_root):
    _write(os.path.join(udmi_root, sequences.CATALOG_SCRIPT),
           "#!/bin/bash\necho 'ERROR: validator jar is stale; run validator/bin/build' >&2\nexit 1\n")
    with pytest.raises(SequenceCatalogError, match="stale; run validator/bin/build"):
        sequences.list_sequences(udmi_root)


def test_invalid_catalog_json_is_rejected():
    with pytest.raises(SequenceCatalogError, match="invalid JSON"):
        sequences.parse_compiled_catalog("not json")
    with pytest.raises(SequenceCatalogError, match="catalog_version"):
        sequences.parse_compiled_catalog(json.dumps({"catalog_version": 99, "sequences": []}))
    with pytest.raises(SequenceCatalogError, match="missing"):
        sequences.parse_compiled_catalog(
            json.dumps({"catalog_version": 1, "sequences": [{"name": "x"}]})
        )


def test_step_doc_for_wrong_test_is_rejected(udmi_root):
    _write(os.path.join(udmi_root, sequences.STEP_DOCS_DIR, "writeback_success", "sequence.md"),
           SCAN_DOC)
    with pytest.raises(SequenceCatalogError, match="not 'writeback_success'"):
        sequences.list_sequences(udmi_root)


def test_unmapped_bucket_is_rejected(udmi_root):
    with pytest.raises(SequenceCatalogError, match="BUCKET_DOCS"):
        sequences.bucket_docs(udmi_root, "brand_new_bucket")


def test_stage_gate_uses_compiled_stage(udmi_root):
    with pytest.raises(ValueError, match=r"writeback_success \(ALPHA\)"):
        sequences.reject_stage_excluded(udmi_root, ["writeback_success"], "PREVIEW")
    sequences.reject_stage_excluded(udmi_root, ["writeback_success"], "ALPHA")
    sequences.reject_stage_excluded(udmi_root, ["broken_config"], "PREVIEW")


def test_real_catalog_reads_the_compiled_validator():
    """Runs bin/sequencer_catalog (workbench/tools/SequenceCatalog.java) against the jar.

    The reader lives outside validator/ and uses only public validator APIs; this
    guards against it depending on anything that is not in the compiled jar.
    """
    import json
    import subprocess

    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    jar = os.path.join(root, "validator", "build", "libs", "validator-1.0-SNAPSHOT-all.jar")
    if not os.path.isfile(jar):
        pytest.skip(f"validator jar {jar} is not built; run validator/bin/build")
    proc = subprocess.run(
        [os.path.join(root, "bin", "sequencer_catalog")],
        capture_output=True, text=True, timeout=180, check=False,
    )
    if proc.returncode != 0 and "stale" in proc.stderr:
        pytest.skip(f"validator jar is stale: {proc.stderr.strip()}")
    assert proc.returncode == 0, proc.stderr
    catalog = json.loads(proc.stdout)
    by_name = {entry["name"]: entry for entry in catalog["sequences"]}
    entry = by_name["scan_single_future"]
    assert entry["bucket"] == "discovery.scan"
    assert entry["stage"] == "PREVIEW"
    assert entry["facets"] == "discovery"
    assert entry["summary"] == "Check results of a single scan scheduled soon"
    assert all(e["declaring_class"].startswith("com.google.daq.mqtt.sequencer.sequences.")
               for e in catalog["sequences"])
