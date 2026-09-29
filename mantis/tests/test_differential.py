"""Unit tests for mantis.tools.differential."""

import os
import pytest
from mantis.tools.differential import compare_test_runs


def test_compare_test_runs_basic(tmp_path):
    # Setup mock target run logs
    target_dir = tmp_path / "target_run"
    target_dir.mkdir()
    seq_log = target_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:02Z Dispatched config RC:9a6ddf.00000134
2026-08-26T12:45:08Z Cutoff set: 2026-08-26T12:45:08Z
2026-08-26T12:45:09Z ignoring stale state update 2026-08-26T12:45:06Z
2026-08-26T12:47:08Z Stage timeout after 120s
2026-08-26T12:47:09Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    res = compare_test_runs(target_run=str(target_dir))
    assert res["status"] == "SUCCESS"
    assert res["target_result"] == "FAIL"
    assert res["divergence_point"] == "STALE_STATE_IGNORED"
    assert "DIVERGENCE POINT" in res["differential_table"]
    assert "Cascading Failure" in res["differential_table"]


def test_compare_test_runs_multi_stage_alignment(tmp_path):
    target_dir = tmp_path / "target_multi_stage"
    baseline_dir = tmp_path / "baseline_multi_stage"
    target_dir.mkdir()
    baseline_dir.mkdir()

    # Multi-stage baseline: stage 1 passes, stage 2 passes
    (baseline_dir / "sequence.log").write_text("""
2026-08-26T12:45:00Z Starting test multi_stage for AHU-1
2026-08-26T12:45:02Z Dispatched config (RC:stage1.001)
2026-08-26T12:45:03Z Waiting for config sync
2026-08-26T12:45:05Z Received state update
2026-08-26T12:45:10Z Dispatched config (RC:stage2.002)
2026-08-26T12:45:12Z Waiting for config sync
2026-08-26T12:45:15Z Received state update
2026-08-26T12:45:20Z RESULT pass pointset multi_stage STABLE 1/8 Sequence complete
""")

    # Multi-stage target: stage 1 passes, stage 2 fails with stale state
    (target_dir / "sequence.log").write_text("""
2026-08-26T12:45:00Z Starting test multi_stage for AHU-1
2026-08-26T12:45:02Z Dispatched config (RC:stage1.001)
2026-08-26T12:45:03Z Waiting for config sync
2026-08-26T12:45:05Z Received state update
2026-08-26T12:45:10Z Dispatched config (RC:stage2.002)
2026-08-26T12:45:12Z Waiting for config sync
2026-08-26T12:45:15Z Cutoff set: 12:45:15Z
2026-08-26T12:45:16Z ignoring stale state update 12:45:14Z
2026-08-26T12:47:15Z Stage timeout after 120s
2026-08-26T12:47:16Z RESULT fail pointset multi_stage STABLE 0/8 Sequence failed
""")

    res = compare_test_runs(target_run=str(target_dir), baseline_run=str(baseline_dir))

    assert res["status"] == "SUCCESS"
    assert res["target_result"] == "FAIL"
    assert res["baseline_result"] == "PASS"

    steps = res["aligned_steps"]
    # Verify multi-stage events were NOT overwritten:
    # There should be at least two CONFIG_DISPATCH steps in the aligned table
    dispatch_steps = [s for s in steps if s["checkpoint"] == "CONFIG_DISPATCH"]
    assert len(dispatch_steps) == 2, f"Expected 2 CONFIG_DISPATCH steps, found {len(dispatch_steps)}"

    # Stage 1 dispatch and state update should be Aligned
    assert dispatch_steps[0]["delta"] == "Aligned"
    assert "stage1.001" in dispatch_steps[0]["target"]

    # Stage 2 dispatch should also be Aligned
    assert dispatch_steps[1]["delta"] == "Aligned"
    assert "stage2.002" in dispatch_steps[1]["target"]

    # STALE_STATE_IGNORED should be marked DIVERGENCE POINT
    stale_steps = [s for s in steps if s["checkpoint"] == "STALE_STATE_IGNORED"]
    assert len(stale_steps) == 1
    assert stale_steps[0]["delta"] == "DIVERGENCE POINT"

    # TIMEOUT_FAILURE should be Cascading Failure
    timeout_steps = [s for s in steps if s["checkpoint"] == "TIMEOUT_FAILURE"]
    assert len(timeout_steps) == 1
    assert timeout_steps[0]["delta"] == "Cascading Failure"

