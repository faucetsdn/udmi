"""Unit tests for mantis.tools.golden baseline verification and anti-cheating audit."""

import os
from mantis.tools.golden import verify_golden_baseline


def test_golden_baseline_exact_match(tmp_path):
    etc_dir = tmp_path / "etc"
    out_dir = tmp_path / "out"
    etc_dir.mkdir()
    out_dir.mkdir()

    content = """
AHU-1 events_pointset {"temperature": 21.5}
AHU-1 events_system {"status": "operational"}
AHU-1 events_blobset {"blob": "xyz"}
AHU-1 events_discovery {"discovered": true}
AHU-1 events_invalid {"error": "bad_point"}
"""
    (etc_dir / "validator.out").write_text(content.strip())
    (out_dir / "validator.out").write_text(content.strip())

    res = verify_golden_baseline(
        baseline_name="validator",
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "PASS"
    assert res["match_pct"] == 100.0
    assert res["anti_cheating_audit"] == "PASSED"
    assert len(res["cheating_violations"]) == 0
    assert res["missing_count"] == 0
    assert res["extra_count"] == 0
    assert "100.0% parity" in res["summary"]


def test_golden_baseline_mismatch_diff(tmp_path):
    etc_dir = tmp_path / "etc"
    out_dir = tmp_path / "out"
    etc_dir.mkdir()
    out_dir.mkdir()

    golden = """
line 1: initial setup
line 2: test execution
line 3: pointset update
line 4: final validation
"""
    actual = """
line 1: initial setup
line 2: test execution
line 3: modified pointset
line 4: final validation
"""
    (etc_dir / "sequencer.out").write_text(golden.strip())
    (out_dir / "sequencer.out").write_text(actual.strip())

    res = verify_golden_baseline(
        baseline_name="sequencer",
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "MISMATCH"
    assert res["match_pct"] < 100.0
    assert res["missing_count"] == 1
    assert res["extra_count"] == 1
    assert "modified pointset" in res["diff"]
    assert "pointset update" in res["diff"]


def test_golden_baseline_anti_cheating_violation(tmp_path):
    etc_dir = tmp_path / "etc"
    out_dir = tmp_path / "out"
    etc_dir.mkdir()
    out_dir.mkdir()

    golden = """
Device AHU-1 events_pointset telemetry sample
Device AHU-1 events_system status update
Device AHU-1 events_blobset blob upload
Device AHU-1 events_discovery scan result
Device AHU-1 events_invalid rejected payload
"""
    # Actual omits events_blobset and events_discovery
    actual = """
Device AHU-1 events_pointset telemetry sample
Device AHU-1 events_system status update
Device AHU-1 events_invalid rejected payload
"""
    (etc_dir / "validator.out").write_text(golden.strip())
    (out_dir / "validator.out").write_text(actual.strip())

    res = verify_golden_baseline(
        baseline_name="validator",
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "VIOLATION"
    assert res["anti_cheating_audit"] == "FAILED"
    assert len(res["cheating_violations"]) >= 2
    violations_text = " ".join(res["cheating_violations"])
    assert "events_blobset" in violations_text
    assert "events_discovery" in violations_text
    assert "ANTI-CHEATING VIOLATION" in violations_text
    assert "ANTI-CHEATING VIOLATION" in res["report"]


def test_golden_baseline_missing_file(tmp_path):
    res = verify_golden_baseline(
        baseline_name="non_existent_baseline",
        udmi_root=str(tmp_path),
    )

    assert res["status"] == "ERROR"
    assert "not found" in res["error"]


def test_golden_baseline_reordered_lines_detected(tmp_path):
    """Ensure that reordered lines are detected as a mismatch and do not score 100% parity."""
    etc_dir = tmp_path / "etc"
    out_dir = tmp_path / "out"
    etc_dir.mkdir()
    out_dir.mkdir()

    golden = "line 1\nline 2\nline 3\n"
    actual = "line 3\nline 2\nline 1\n"
    (etc_dir / "test.out").write_text(golden)
    (out_dir / "test.out").write_text(actual)

    res = verify_golden_baseline(baseline_name="test", udmi_root=str(tmp_path))
    assert res["status"] == "MISMATCH"
    assert res["match_pct"] < 100.0
    assert len(res["diff"]) > 0

