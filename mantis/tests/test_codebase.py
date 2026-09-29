"""Unit tests for mantis.tools.codebase traversal tools."""

import os
import pytest
from mantis.tools.codebase import (
    _is_test_path,
    inspect_sequencer_test,
    locate_udmi_doc,
    read_udmi_file,
    search_codebase,
)


def test_read_udmi_file(tmp_path):
    test_file = tmp_path / "sample.txt"
    test_file.write_text("Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n")

    # Read entire file
    res = read_udmi_file("sample.txt", udmi_root=str(tmp_path))
    assert res["status"] == "SUCCESS"
    assert res["total_lines"] == 5
    assert "Line 1" in res["content"]
    assert "Line 5" in res["content"]

    # Read line slice
    res_slice = read_udmi_file("sample.txt", start_line=2, end_line=4, udmi_root=str(tmp_path))
    assert res_slice["status"] == "SUCCESS"
    assert res_slice["start_line"] == 2
    assert res_slice["end_line"] == 4
    assert res_slice["content"] == "Line 2\nLine 3\nLine 4\n"

    # Non-existent file
    res_err = read_udmi_file("missing.txt", udmi_root=str(tmp_path))
    assert res_err["status"] == "ERROR"
    assert "not found" in res_err["error"].lower()

    # Truncation cap when no range specified
    large_file = tmp_path / "large.txt"
    large_file.write_text("\n".join(f"Line {i}" for i in range(1, 451)) + "\n")
    res_large = read_udmi_file("large.txt", udmi_root=str(tmp_path))
    assert res_large["status"] == "SUCCESS"
    assert res_large["truncated"] is True
    assert res_large["end_line"] == 400
    assert "[TRUNCATED: showing lines 1-400 of 450" in res_large["content"]

    # Directory traversal prevention
    res_trav = read_udmi_file("../outside.txt", udmi_root=str(tmp_path))
    assert res_trav["status"] == "ERROR"
    assert "outside" in res_trav["error"].lower()


def test_search_codebase(tmp_path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "Test.java").write_text("public class Test {\n  String marker = \"TARGET_MARKER_123\";\n  String m2 = \"TARGET_MARKER_123\";\n  String m3 = \"TARGET_MARKER_123\";\n  String m4 = \"TARGET_MARKER_123\";\n}\n")
    (tmp_path / "docs").mkdir()
    (tmp_path / "docs" / "doc.md").write_text("# Documentation\nReference to TARGET_MARKER_123 in doc.\n")
    (tmp_path / "build").mkdir()
    (tmp_path / "build" / "Ignored.java").write_text("TARGET_MARKER_123 in build dir\n")

    # Search without pattern
    res = search_codebase("TARGET_MARKER_123", udmi_root=str(tmp_path))
    assert res["status"] == "SUCCESS"
    assert "locations_summary" in res
    assert res["locations_summary"]["src"] == 4
    assert res["locations_summary"]["docs"] == 1
    assert res["total_matches_found"] == 5
    # Capped at default max_per_file=3 for Test.java + 1 for doc.md = 4
    assert res["matches_count"] == 4
    files = [m["file"] for m in res["matches"]]
    assert any("Test.java" in f for f in files)
    assert any("doc.md" in f for f in files)
    # Ensure build dir was excluded
    assert not any("build" in f for f in files)
    assert "excluded_paths" in res
    assert "bin" in res["excluded_paths"]

    # Search with path_prefix
    res_prefix = search_codebase("TARGET_MARKER_123", path_prefix="src", udmi_root=str(tmp_path))
    assert res_prefix["status"] == "SUCCESS"
    assert res_prefix["path_prefix"] == "src"
    assert "docs" not in res_prefix["locations_summary"]
    assert res_prefix["locations_summary"]["src"] == 4

    # Search with max_per_file=1
    res_capped = search_codebase("TARGET_MARKER_123", max_per_file=1, udmi_root=str(tmp_path))
    assert res_capped["status"] == "SUCCESS"
    assert res_capped["matches_count"] == 2

    # Search with pattern filter
    res_filtered = search_codebase("TARGET_MARKER_123", file_pattern="*.java", udmi_root=str(tmp_path))
    assert res_filtered["status"] == "SUCCESS"
    assert res_filtered["matches_count"] == 3
    assert "Test.java" in res_filtered["matches"][0]["file"]

    # Search with regex
    res_regex = search_codebase(r"TARGET_\w+_\d+", is_regex=True, udmi_root=str(tmp_path))
    assert res_regex["status"] == "SUCCESS"
    assert res_regex["total_matches_found"] == 5


def test_locate_udmi_doc(tmp_path):
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    specs_dir = docs_dir / "specs"
    specs_dir.mkdir()

    (specs_dir / "gateway.md").write_text("""# Gateway Specification
## Architecture
This document specifies how gateways proxy sub-devices.
## Writeback
Writeback details for gateways.
""")
    (specs_dir / "pointset.md").write_text("""# Pointset Specification
## Telemetry
Pointset telemetry details.
""")

    # Locate gateway doc
    res_gw = locate_udmi_doc("gateway proxy", udmi_root=str(tmp_path))
    assert res_gw["status"] == "SUCCESS"
    assert res_gw["matches_count"] >= 1
    top_doc = res_gw["documents"][0]
    assert "gateway.md" in top_doc["file"]
    assert "Architecture" in top_doc["sections"]

    # Locate pointset doc
    res_ps = locate_udmi_doc("pointset", udmi_root=str(tmp_path))
    assert res_ps["status"] == "SUCCESS"
    assert "pointset.md" in res_ps["documents"][0]["file"]


def test_inspect_sequencer_test(tmp_path):
    seq_dir = tmp_path / "validator" / "src" / "main" / "java" / "com" / "google" / "daq" / "mqtt" / "sequencer" / "sequences"
    seq_dir.mkdir(parents=True)

    java_code = """package com.google.daq.mqtt.sequencer.sequences;

import org.junit.Test;
import com.google.daq.mqtt.sequencer.Feature;

public class SampleSequences {

  /**
   * Validates that a device publishes pointset messages.
   */
  @Test
  @Feature(stage = Feature.Stage.ALPHA, description = "Check pointset telemetry")
  public void sample_test_case() {
    waitForState("pointset");
    assertTrue("Should be valid", true);
  }
}
"""
    (seq_dir / "SampleSequences.java").write_text(java_code)

    res = inspect_sequencer_test("sample_test_case", udmi_root=str(tmp_path))
    assert res["status"] == "SUCCESS"
    assert res["test_name"] == "sample_test_case"
    assert res["class_name"] == "SampleSequences"
    assert res["stage"] == "ALPHA"
    assert "Check pointset telemetry" in res["description"]
    assert "waitForState" in res["source_code"]
    assert any("assertTrue" in a for a in res["assertions"])

    # Non-existent test
    res_missing = inspect_sequencer_test("non_existent_test", udmi_root=str(tmp_path))
    assert res_missing["status"] == "ERROR"
    assert "sample_test_case" in res_missing["available_tests"]


def test_is_test_path_classification():
    assert _is_test_path("udmis/src/test/java/com/google/ReflectProcessorTest.java")
    assert _is_test_path("udmis/src/main/java/com/google/FooTest.java")
    assert _is_test_path("mantis/tests/test_codebase.py")
    assert _is_test_path("mantis/tools/codebase_test.py")
    assert not _is_test_path("udmis/src/main/java/com/google/ReflectProcessor.java")
    assert not _is_test_path("validator/src/main/java/com/google/Validator.java")
    # 'test' must match a whole path segment, not a substring of one
    assert not _is_test_path("udmis/src/main/java/com/google/latest/Thing.java")


def _write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def test_search_ranks_implementation_above_tests(tmp_path):
    """Implementation sources must outrank tests for the same symbol.

    Regression guard: a repo search for 'ReflectProcessor' previously returned
    the test files first and the implementation last, so the result cap hid the
    file the caller actually needed. The fixture deliberately places the test
    source earlier in traversal order ('it' sorts before 'main'), so this test
    fails if the ranking is removed.
    """
    _write(
        tmp_path / "udmis/src/it/java/com/google/ReflectProcessorIT.java",
        "class ReflectProcessorIT {}\n",
    )
    _write(
        tmp_path / "udmis/src/main/java/com/google/ReflectProcessor.java",
        "class ReflectProcessor {}\n",
    )

    res = search_codebase("ReflectProcessor", udmi_root=str(tmp_path))
    assert res["status"] == "SUCCESS"
    assert res["matches_count"] == 2
    assert "src/main" in res["matches"][0]["file"]
    assert res["matches"][0]["is_test"] is False
    assert "src/it" in res["matches"][1]["file"]
    assert res["matches"][1]["is_test"] is True


def test_result_cap_drops_tests_not_implementations(tmp_path):
    """Truncation must discard test matches before implementation matches.

    The test sources sort before the implementation in traversal order, so
    without ranking the cap would keep only tests.
    """
    for i in range(4):
        _write(
            tmp_path / f"udmis/src/it/java/com/google/Widget{i}IT.java",
            "class WidgetIT { Widget w; }\n",
        )
    _write(
        tmp_path / "udmis/src/main/java/com/google/Widget.java",
        "class Widget {}\n",
    )

    res = search_codebase("Widget", max_results=2, udmi_root=str(tmp_path))
    assert res["status"] == "SUCCESS"
    assert res["matches_count"] == 2
    assert res["matches"][0]["file"].replace(os.sep, "/").endswith(
        "udmis/src/main/java/com/google/Widget.java"
    )
    assert res["test_matches_dropped"] == 3
    assert res["truncated"] is True


def test_search_reads_log_files(tmp_path):
    """Logs are the record of what a run did and must be searchable.

    They were previously filtered out by extension, so a caller asking for a
    log line got SUCCESS with zero matches and no indication the file had
    never been opened.
    """
    run_dir = tmp_path / "sites/test_site/out/devices/AHU-1/tests/broken_config"
    _write(run_dir / "sequence.log", "NOTICE Timeout waiting for initial device state\n")

    res = search_codebase(
        "Timeout waiting for initial device state",
        path_prefix="sites/test_site/out/devices/AHU-1/tests/broken_config",
        udmi_root=str(tmp_path),
    )
    assert res["status"] == "SUCCESS"
    assert res["matches_count"] == 1
    assert res["matches"][0]["file"].replace(os.sep, "/").endswith("sequence.log")


def test_search_reports_skipped_run_output_dirs(tmp_path):
    """A zero-match result must be distinguishable from an unsearched tree."""
    _write(
        tmp_path / "sites/test_site/out/devices/AHU-1/tests/broken_config/sequence.log",
        "NOTICE Timeout waiting for initial device state\n",
    )
    _write(tmp_path / "sites/test_site/cloud_iot_config.json", "{}\n")

    res = search_codebase(
        "Timeout waiting for initial device state",
        path_prefix="sites/test_site",
        udmi_root=str(tmp_path),
    )
    assert res["status"] == "SUCCESS"
    assert res["matches_count"] == 0
    skipped = [p.replace(os.sep, "/") for p in res["skipped_artifact_dirs"]]
    assert "sites/test_site/out" in skipped
    assert "path_prefix" in res["skipped_artifact_dirs_note"]


def test_search_rejects_unreadable_file_pattern(tmp_path):
    """An unsatisfiable filter is a caller error, not an empty result."""
    _write(tmp_path / "src/Thing.java", "class Thing {}\n")

    res = search_codebase("Thing", file_pattern="*.xml", udmi_root=str(tmp_path))
    assert res["status"] == "ERROR"
    assert "*.xml" in res["error"]
    assert ".log" in res["error"]
