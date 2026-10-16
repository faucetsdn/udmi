"""Tests for the Workbench compliance module.

The point of the module is that it never invents a result, so these tests pin
genuinely recorded verdicts rather than accepting any well-shaped payload.

Site-wide invariants (every device appears, reports list only files that exist,
totals aggregate) are asserted against the real `sites/udmi_site_model`, since
those hold whatever has been run. The verdict-level assertions instead use
`RECORDED_RUN` below, a run captured verbatim from that same site model. They
used to read the working tree directly, which meant the suite started failing
the moment an operator ran a sequencer -- the recorded verdict is not a fixed
property of the repository, it is whatever was run last.

Synthetic site models cover the cases the repository cannot provide: a device
with no sequencer json, and a device whose sequencer json is corrupt.
"""

import json
import os

import pytest

from workbench.server import compliance, discovery
from workbench.server.compliance import ComplianceError

SITE_MODEL = "sites/udmi_site_model"
UDMI_ROOT = os.path.abspath(
    os.path.join(os.path.dirname(__file__), os.pardir, os.pardir)
)

#: A real `bin/sequencer` run against AHU-1, captured verbatim from
#: `sites/udmi_site_model/out/sequencer_AHU-1.json`. Held here so the verdict
#: assertions below survive later runs against that site model.
RECORDED_RUN = {
    "cloud_version": {
        "deployed_at": "2026-09-21T10:59:17Z",
        "deployed_by": "giraffe@safari.com",
        "functions_max": 18,
        "functions_min": 18,
        "udmi_ref": "g123456789",
        "udmi_version": "1.4.1",
    },
    "features": {
        "system": {
            "sequences": {
                "broken_config": {
                    "capabilities": {},
                    "result": "fail",
                    "scoring": {"total": 8, "value": 0},
                    "stage": "stable",
                    "status": {
                        "category": "validation.feature.sequence",
                        "level": 500,
                        "message": "Timeout waiting for initial device state",
                        "timestamp": "2026-09-21T15:40:16Z",
                    },
                    "summary": (
                        "Check that the device correctly handles a broken "
                        "(non-json) config message."
                    ),
                }
            }
        }
    },
    "schemas": {},
    "start_time": "2026-09-21T15:35:16Z",
    "status": {
        "category": "validation.feature.sequence",
        "level": 300,
        "message": "Run completed",
        "timestamp": "2026-09-21T15:40:16Z",
    },
    "timestamp": "2026-09-21T15:40:16Z",
    "udmi_version": "1.5.5-176-g1be0eb2ca-dirty",
}

#: The RESULT.log the same run wrote, byte for byte.
RECORDED_RESULT_LOG = (
    "CPBLTY skip system broken_config.status ALPHA 0/0 Never executed\n"
    "CPBLTY skip system broken_config.logging ALPHA 0/0 Never executed\n"
    "RESULT fail system broken_config STABLE 0/8 "
    "Timeout waiting for initial device state\n"
)


#: An aborted `bin/sequencer` run against AHU-22, captured verbatim from
#: `sites/udmi_site_model/out/sequencer_AHU-22.json`.
RECORDED_ABORTED_RUN = {
    "features": {
        "system": {
            "sequences": {
                "broken_config": {
                    "capabilities": {},
                    "result": "errr",
                    "scoring": {"total": 8, "value": 0},
                    "stage": "stable",
                    "status": {
                        "category": "validation.feature.sequence",
                        "level": 500,
                        "message": "Reflector is not currently active",
                        "timestamp": "2026-09-21T15:40:16Z",
                    },
                    "summary": (
                        "Check that the device correctly handles a broken "
                        "(non-json) config message."
                    ),
                }
            }
        }
    },
    "schemas": {},
    "start_time": "2026-09-21T15:35:16Z",
    "status": {
        "category": "validation.feature.sequence",
        "level": 500,
        "message": "Reflector is not currently active",
        "timestamp": "2026-09-21T15:40:16Z",
    },
    "timestamp": "2026-09-21T15:40:16Z",
    "udmi_version": "1.5.5-176-g1be0eb2ca-dirty",
}


@pytest.fixture(scope="module")
def site_report():
    return compliance.site_compliance(UDMI_ROOT, SITE_MODEL)


def _make_site_model(root, device_ids):
    """Builds a minimal but genuine site model directory under `root`."""
    root.mkdir(parents=True, exist_ok=True)
    (root / "cloud_iot_config.json").write_text(
        json.dumps({
            "site_name": "COMPLIANCE-FIXTURE",
            "registry_id": "CF-1",
            "iot_provider": "mqtt",
            "project_id": "//mqtt/localhost:18833",
        }),
        encoding="utf-8",
    )
    for device_id in device_ids:
        device_dir = root / "devices" / device_id
        device_dir.mkdir(parents=True)
        (device_dir / "metadata.json").write_text(
            json.dumps({"system": {"hardware": {"make": "ACME", "model": "X"}}}),
            encoding="utf-8",
        )
    (root / "out").mkdir()
    return root


@pytest.fixture
def recorded_site(tmp_path):
    """A site model holding `RECORDED_RUN` and `RECORDED_ABORTED_RUN`."""
    root = _make_site_model(tmp_path / "recorded_site", ["AHU-1", "AHU-22"])
    (root / "out" / "sequencer_AHU-1.json").write_text(
        json.dumps(RECORDED_RUN, indent=2), encoding="utf-8"
    )
    (root / "out" / "sequencer_AHU-22.json").write_text(
        json.dumps(RECORDED_ABORTED_RUN, indent=2), encoding="utf-8"
    )
    device_out = root / "out" / "devices" / "AHU-1"
    device_out.mkdir(parents=True)
    (device_out / "RESULT.log").write_text(RECORDED_RESULT_LOG, encoding="utf-8")
    (device_out / "results.md").write_text(
        "| Device | AHU-1 |\n| Result | fail |\n", encoding="utf-8"
    )
    return root


@pytest.fixture
def recorded_report(recorded_site):
    return compliance.site_compliance(str(recorded_site.parent), recorded_site.name)


# ------------------------------------------------------------- site scope ---
def test_every_discovered_device_appears_in_the_matrix(site_report):
    """A device missing from the matrix would read as full coverage."""
    discovered = [d["device_id"] for d in discovery.list_devices(UDMI_ROOT, SITE_MODEL)]
    reported = [d["device_id"] for d in site_report["devices"]]

    assert discovered, "udmi_site_model must contain devices"
    assert sorted(reported) == sorted(discovered)
    assert site_report["site_model"] == SITE_MODEL


def test_broken_config_is_reported_exactly_as_recorded(recorded_report):
    device = next(d for d in recorded_report["devices"] if d["device_id"] == "AHU-1")

    assert device["has_results"] is True
    assert device["reason"] is None
    assert device["last_run"]
    assert device["udmi_version"]
    assert device["status_message"] == "Run completed"

    sequence = next(s for s in device["sequences"] if s["name"] == "broken_config")
    assert sequence["feature"] == "system"
    assert sequence["result"] == "fail"
    assert sequence["stage"] == "stable"
    assert sequence["score"] == {"value": 0, "total": 8}
    assert sequence["message"] == "Timeout waiting for initial device state"
    assert sequence["summary"] == (
        "Check that the device correctly handles a broken (non-json) config message."
    )
    assert sequence["timestamp"]

    assert device["counts"]["fail"] >= 1

    # The score belongs to the (bucket, stage) cell, not to the device. This is
    # the row bin/sequencer_report would print as `| x | system | 0/8 |`.
    assert device["stages"] == ["stable"]
    assert device["features"]["system"]["stages"]["stable"] == {
        "scored": 0, "total": 8, "sequences": 1
    }
    assert device["features"]["system"]["overall"] is False
    assert device["verdict"] == "fail"
    assert "score" not in device, (
        "A summed per-device score conflates independently scored buckets and "
        "must never come back"
    )


def test_unexpected_verdicts_are_not_bucketed_as_pass_or_fail(recorded_report):
    """`errr` runs must stay visible instead of being folded into a bucket.

    The site model really does contain aborted runs recorded as `errr`. Counting
    them as failures would report a device that never got to run as a device
    that ran and failed every sequence, so each bucket is pinned to the exact
    number of sequences carrying that verdict.
    """
    verdicts = set()
    for device in recorded_report["devices"]:
        counts = device["counts"]
        results = [s["result"] for s in device["sequences"]]
        verdicts.update(results)

        assert counts["total"] == len(results)
        for bucket in ("pass", "fail", "skip"):
            assert counts[bucket] == results.count(bucket), (
                f"{device['device_id']}: '{bucket}' count must be exactly the "
                "sequences reporting that verdict, with no unknown verdicts added"
            )
        for result in results:
            assert result == result.lower()

    unexpected = verdicts - set(compliance.RESULT_BUCKETS)
    assert "errr" in unexpected, (
        "This test is only meaningful while the site model retains its real "
        "'errr' results; they are the verdicts at risk of being mis-bucketed"
    )


def test_aborted_runs_are_excluded_from_every_bucket(recorded_report):
    """AHU-22's run never reached the device; nothing may be scored pass/fail/skip."""
    device = next(d for d in recorded_report["devices"] if d["device_id"] == "AHU-22")

    assert device["has_results"] is True
    assert device["counts"]["total"] > 0
    assert {s["result"] for s in device["sequences"]} == {"errr"}
    assert device["counts"]["pass"] == 0
    assert device["counts"]["fail"] == 0
    assert device["counts"]["skip"] == 0
    assert device["sequences"][0]["message"] == "Reflector is not currently active"


def test_totals_aggregate_the_per_device_counts(recorded_report):
    totals = recorded_report["totals"]
    devices = recorded_report["devices"]

    assert totals["devices"] == len(devices)
    assert totals["devices_with_results"] == sum(
        1 for d in devices if d["has_results"]
    )
    for bucket in ("pass", "fail", "skip", "total"):
        assert totals[bucket] == sum(d["counts"][bucket] for d in devices)
    assert totals["total"] > 0, "The recorded site model has recorded sequences"


def test_reports_list_only_files_that_exist(recorded_site, recorded_report):
    site_dir = str(recorded_site)

    for device in recorded_report["devices"]:
        assert set(device["reports"]) <= set(compliance.REPORT_KINDS)
        for kind in compliance.REPORT_KINDS:
            path = compliance._report_path(site_dir, device["device_id"], kind)
            assert os.path.isfile(path) == (kind in device["reports"]), (
                f"{device['device_id']}/{kind} presence must match disk"
            )

    ahu1 = next(d for d in recorded_report["devices"] if d["device_id"] == "AHU-1")
    assert set(ahu1["reports"]) == {"results_md", "result_log", "sequencer_json"}


# --------------------------------------------------------- honest absence ---
def test_device_without_sequencer_json_is_untested_not_zero_percent(tmp_path):
    site = _make_site_model(tmp_path / "fixture_site", ["NEW-1"])

    report = compliance.site_compliance(UDMI_ROOT, str(site))
    device = report["devices"][0]

    assert device["device_id"] == "NEW-1"
    assert device["has_results"] is False
    assert device["sequences"] == []
    assert device["counts"] == {"pass": 0, "fail": 0, "skip": 0, "total": 0}
    assert device["features"] == {}
    assert device["stages"] == []
    assert device["verdict"] == "not_evaluated"
    assert "sequencer_NEW-1.json" in device["reason"]
    assert str(site / "out") in device["reason"]
    assert device["reports"] == []

    assert report["totals"]["devices"] == 1
    assert report["totals"]["devices_with_results"] == 0


def test_report_outliving_its_scoring_json_is_not_called_missing(tmp_path):
    """A results.md without sequencer_<device>.json must not be denied.

    This is the common state in a real lab model: out/ is pruned per run, and
    results.md lives at a different path than the scoring file, so a rendered
    report routinely survives the JSON it came from. Telling the operator "no
    sequencer results found" while the report sits on disk is false, but the
    device still cannot be scored from it.
    """
    site = _make_site_model(tmp_path / "orphan_site", ["OLD-1"])
    report_dir = site / "out" / "devices" / "OLD-1"
    report_dir.mkdir(parents=True, exist_ok=True)
    (report_dir / "results.md").write_text(
        "# OLD-1\n- Start 2026-05-01T00:00:00Z\n", encoding="utf-8"
    )

    device = compliance.site_compliance(UDMI_ROOT, str(site))["devices"][0]

    assert device["has_results"] is False
    assert device["verdict"] == "not_evaluated"
    assert device["features"] == {}
    assert "results_md" in device["reports"]
    # The wording must acknowledge the surviving report...
    assert "No sequencer results found" not in device["reason"]
    assert "still on disk" in device["reason"]
    # ...while naming the file that is actually absent.
    assert "sequencer_OLD-1.json" in device["reason"]


def test_malformed_sequencer_json_surfaces_the_parse_error(tmp_path):
    site = _make_site_model(tmp_path / "broken_site", ["BAD-1"])
    (site / "out" / "sequencer_BAD-1.json").write_text("{not json", encoding="utf-8")

    report = compliance.site_compliance(UDMI_ROOT, str(site))
    device = report["devices"][0]

    assert device["has_results"] is False
    assert "Invalid sequencer results" in device["reason"]
    assert "sequencer_BAD-1.json" in device["reason"]
    # The json module's own diagnostic, not a generic message.
    assert "Expecting" in device["reason"]
    assert device["counts"]["total"] == 0
    # Still downloadable for inspection even though it cannot be scored.
    assert device["reports"] == ["sequencer_json"]


def test_non_object_sequencer_json_is_rejected_not_scored(tmp_path):
    site = _make_site_model(tmp_path / "list_site", ["ODD-1"])
    (site / "out" / "sequencer_ODD-1.json").write_text("[]", encoding="utf-8")

    device = compliance.site_compliance(UDMI_ROOT, str(site))["devices"][0]

    assert device["has_results"] is False
    assert "expected a JSON object" in device["reason"]


def test_unknown_site_model_fails_fast():
    with pytest.raises(discovery.DiscoveryError, match="not found"):
        compliance.site_compliance(UDMI_ROOT, "sites/definitely_not_a_site_model")


# --------------------------------------------------------------- reports ----
@pytest.mark.parametrize(
    "kind,content_type,suffix",
    [
        ("results_md", "text/markdown", "results.md"),
        ("result_log", "text/plain", "RESULT.log"),
        ("sequencer_json", "application/json", "sequencer.json"),
    ],
)
def test_device_report_returns_real_bytes(recorded_site, kind, content_type, suffix):
    report = compliance.device_report(
        str(recorded_site.parent), recorded_site.name, "AHU-1", kind
    )

    assert report["content_type"] == content_type
    assert report["filename"] == f"AHU-1_{suffix}"
    assert "AHU-1" in report["filename"]
    assert isinstance(report["content"], bytes)
    assert report["size"] == len(report["content"]) > 0
    assert report["path"].startswith(recorded_site.name)
    assert not os.path.isabs(report["path"]), "In-repo paths are repo-relative"


def test_report_content_is_the_file_verbatim(recorded_site):
    root, name = str(recorded_site.parent), recorded_site.name
    on_disk = open(
        os.path.join(str(recorded_site), "out", "devices", "AHU-1", "RESULT.log"), "rb"
    ).read()

    report = compliance.device_report(root, name, "AHU-1", "result_log")

    assert report["content"] == on_disk
    assert b"RESULT fail system broken_config STABLE 0/8" in report["content"]


def test_sequencer_json_report_parses_back_to_the_source_document(recorded_site):
    report = compliance.device_report(
        str(recorded_site.parent), recorded_site.name, "AHU-1", "sequencer_json"
    )
    document = json.loads(report["content"].decode("utf-8"))

    assert document == RECORDED_RUN
    assert document["features"]["system"]["sequences"]["broken_config"]["result"] == "fail"


def test_unknown_report_kind_lists_the_valid_kinds():
    with pytest.raises(ComplianceError) as excinfo:
        compliance.device_report(UDMI_ROOT, SITE_MODEL, "AHU-1", "pdf")

    message = str(excinfo.value)
    assert "pdf" in message
    for kind in ("results_md", "result_log", "sequencer_json"):
        assert kind in message


def test_missing_report_names_the_path_it_looked_for():
    """AHU-22 has no results.md; the error must say exactly what is absent."""
    with pytest.raises(ComplianceError) as excinfo:
        compliance.device_report(UDMI_ROOT, SITE_MODEL, "AHU-22", "results_md")

    message = str(excinfo.value)
    assert "out/devices/AHU-22/results.md" in message
    assert "does not exist" in message


@pytest.mark.parametrize(
    "device_id",
    ["../AHU-1", "..", "devices/AHU-1", "AHU-1/../../etc", "..\\AHU-1"],
)
def test_device_id_cannot_traverse_out_of_the_site_model(device_id):
    with pytest.raises(ComplianceError, match="Invalid device_id"):
        compliance.device_report(UDMI_ROOT, SITE_MODEL, device_id, "results_md")


def test_empty_device_id_is_rejected():
    with pytest.raises(ComplianceError, match="device_id parameter is required"):
        compliance.device_report(UDMI_ROOT, SITE_MODEL, "", "results_md")


def test_unknown_device_is_reported_by_name():
    with pytest.raises(ComplianceError, match="NOPE-9"):
        compliance.device_report(UDMI_ROOT, SITE_MODEL, "NOPE-9", "results_md")


def test_oversized_report_is_refused_rather_than_truncated(tmp_path, monkeypatch):
    site = _make_site_model(tmp_path / "big_site", ["BIG-1"])
    (site / "out" / "sequencer_BIG-1.json").write_text("{}" + " " * 64, encoding="utf-8")
    monkeypatch.setattr(compliance, "MAX_REPORT_BYTES", 8)

    with pytest.raises(ComplianceError, match="exceeding the 8 byte download limit"):
        compliance.device_report(UDMI_ROOT, str(site), "BIG-1", "sequencer_json")


# ------------------------------------------------- bucket x stage scoring ---
#
# These pin the rules that make the score meaningful. Each one corresponds to a
# way the previous implementation was wrong, and each is reproduced from
# bin/sequencer_report, which is the authority for what results.md will say.


def _run(features, **top):
    """A minimal sequencer json carrying the given feature -> sequence tree."""
    document = {
        "features": features,
        "schemas": {},
        "start_time": "2026-01-01T00:00:00Z",
        "status": {
            "category": "validation.feature.sequence",
            "level": 300,
            "message": "Run completed",
            "timestamp": "2026-01-01T00:10:00Z",
        },
        "timestamp": "2026-01-01T00:10:00Z",
        "udmi_version": "1.5.5",
    }
    document.update(top)
    return document


def _sequence(result, stage, value, total, **extra):
    entry = {
        "capabilities": {},
        "result": result,
        "stage": stage,
        "scoring": {"value": value, "total": total},
        "status": {
            "category": "validation.feature.sequence",
            "level": 300 if result == "pass" else 500,
            "message": f"{result} message",
            "timestamp": "2026-01-01T00:05:00Z",
        },
    }
    entry.update(extra)
    return entry


def _site_with(tmp_path, name, document, device_id="AHU-1"):
    root = _make_site_model(tmp_path / name, [device_id])
    (root / "out").mkdir(exist_ok=True)
    (root / "out" / f"sequencer_{device_id}.json").write_text(
        json.dumps(document), encoding="utf-8"
    )
    return compliance._device_compliance(str(root.parent), str(root), device_id)


def test_skipped_bucket_is_not_assessed_rather_than_failed(tmp_path):
    """A 0/0 bucket was never exercised; calling it a failure invents a defect."""
    device = _site_with(tmp_path, "skipped", _run({
        "gateway": {"sequences": {
            "gateway_proxy_state": _sequence("skip", "stable", 0, 0),
        }},
    }))

    cell = device["features"]["gateway"]["stages"]["stable"]
    assert cell == {"scored": 0, "total": 0, "sequences": 1}
    assert device["features"]["gateway"]["overall"] is None, (
        "0/0 must be 'not assessed'; None is what renders as a dash, and False "
        "would render as a cross against a device nobody tested"
    )
    assert device["verdict"] == "not_evaluated"
    assert device["stages"] == [], "A stage nothing exercised earns no column"


def test_attempted_and_failed_is_distinguishable_from_skipped(tmp_path):
    """0/10 and 0/0 must not collapse onto each other."""
    device = _site_with(tmp_path, "attempted", _run({
        "gateway": {"sequences": {
            "gateway_proxy_state": _sequence("fail", "stable", 0, 10),
        }},
    }))

    assert device["features"]["gateway"]["stages"]["stable"]["total"] == 10
    assert device["features"]["gateway"]["overall"] is False
    assert device["verdict"] == "fail"


def test_preview_only_bucket_is_not_assessed_even_at_full_marks(tmp_path):
    """Only stable and beta decide a verdict. bin/sequencer_report:27

    This is the rule behind the real GAT-123 report, where a bucket scoring
    0/10 at preview still renders as a dash rather than a cross.
    """
    device = _site_with(tmp_path, "preview_only", _run({
        "discovery.scan": {"sequences": {
            "scan_single_now": _sequence("pass", "preview", 10, 10),
        }},
    }))

    assert device["features"]["discovery.scan"]["stages"]["preview"] == {
        "scored": 10, "total": 10, "sequences": 1
    }
    assert device["features"]["discovery.scan"]["overall"] is None, (
        "Full marks at preview is still not a pass: preview is informational"
    )
    assert device["verdict"] == "not_evaluated"
    assert device["stages"] == ["preview"], "The column still appears; only the verdict abstains"


def test_a_bucket_passes_only_on_full_marks(tmp_path):
    """bin/sequencer_report:308 requires scored == total, not merely scored > 0."""
    device = _site_with(tmp_path, "partial", _run({
        "pointset": {"sequences": {
            "pointset_publish": _sequence("pass", "stable", 10, 10),
            "pointset_remove_point": _sequence("fail", "stable", 0, 10),
        }},
    }))

    assert device["features"]["pointset"]["stages"]["stable"] == {
        "scored": 10, "total": 20, "sequences": 2
    }
    assert device["features"]["pointset"]["overall"] is False
    assert device["verdict"] == "fail"


def test_stage_columns_follow_what_was_exercised_and_keep_canonical_order(tmp_path):
    """Real lab devices do span several stages at once; N columns must work.

    A single real lab report can carry Stable, Beta and Preview columns together.
    """
    device = _site_with(tmp_path, "multistage", _run({
        "system": {"sequences": {
            "valid_serial_no": _sequence("pass", "stable", 10, 10),
            "system_mode_restart": _sequence("pass", "preview", 10, 10),
        }},
        "gateway": {"sequences": {
            "gateway_proxy_events": _sequence("pass", "beta", 10, 10),
        }},
    }))

    assert device["stages"] == ["stable", "beta", "preview"], (
        "Columns must come out in released-first order regardless of input order"
    )
    assert device["features"]["system"]["overall"] is True
    assert device["features"]["gateway"]["overall"] is True
    assert device["verdict"] == "pass"


def test_one_failing_bucket_fails_the_device_but_abstentions_do_not(tmp_path):
    device = _site_with(tmp_path, "mixed", _run({
        "system": {"sequences": {"a": _sequence("pass", "stable", 10, 10)}},
        "pointset": {"sequences": {"b": _sequence("fail", "stable", 0, 10)}},
        "discovery.scan": {"sequences": {"c": _sequence("skip", "preview", 0, 0)}},
    }))

    assert device["features"]["system"]["overall"] is True
    assert device["features"]["pointset"]["overall"] is False
    assert device["features"]["discovery.scan"]["overall"] is None
    assert device["verdict"] == "fail"


def test_sequence_without_scoring_is_reported_not_scored_as_zero(tmp_path):
    """An interrupted run leaves `result: start` and no scoring at all.

    Defaulting that to 0/0 would silently turn a run nobody finished into a
    tidy set of 'not applicable' buckets. Both real DDC-29 and DDC-501 in the
    lab site model contain exactly one such sequence.
    """
    device = _site_with(tmp_path, "interrupted", _run({
        "system.software.updates": {"sequences": {
            "blob_update_invalid_hash": {"result": "start", "stage": "preview"},
        }},
    }))

    assert device["features"]["system.software.updates"]["stages"]["preview"] == {
        "scored": 0, "total": 0, "sequences": 0
    }, "An unscored sequence must not be counted into the cell at all"

    assert len(device["unscored"]) == 1
    stray = device["unscored"][0]
    assert stray["name"] == "blob_update_invalid_hash"
    assert stray["result"] == "start"
    assert "no score" in stray["reason"]
    assert device["counts"]["total"] == 1, "It is still a recorded sequence"


def test_sequence_with_unknown_stage_is_surfaced_not_dropped(tmp_path):
    device = _site_with(tmp_path, "badstage", _run({
        "system": {"sequences": {"odd": _sequence("pass", "experimental", 10, 10)}},
    }))

    assert device["stages"] == []
    assert len(device["unscored"]) == 1
    assert "experimental" in device["unscored"][0]["reason"]


def test_site_totals_count_devices_by_verdict(tmp_path):
    root = _make_site_model(tmp_path / "verdicts", ["PASS-1", "FAIL-1", "NONE-1"])
    (root / "out" / "sequencer_PASS-1.json").write_text(json.dumps(_run({
        "system": {"sequences": {"a": _sequence("pass", "stable", 10, 10)}},
    })), encoding="utf-8")
    (root / "out" / "sequencer_FAIL-1.json").write_text(json.dumps(_run({
        "system": {"sequences": {"a": _sequence("fail", "stable", 0, 10)}},
    })), encoding="utf-8")
    (root / "out" / "sequencer_NONE-1.json").write_text(json.dumps(_run({
        "system": {"sequences": {"a": _sequence("skip", "stable", 0, 0)}},
    })), encoding="utf-8")

    report = compliance.site_compliance(str(root.parent), root.name)
    totals = report["totals"]

    assert totals["devices_passing"] == 1
    assert totals["devices_failing"] == 1
    assert totals["devices_not_evaluated"] == 1
    assert "score" not in totals


def test_stale_results_md_is_flagged_against_the_authoritative_json(tmp_path):
    """results.md and the json drift apart in real site models.

    DDC-29 in the lab site model carries a report describing a run that started
    41 seconds before the one the json records. An operator downloading that
    report gets a different run from the one on screen, so the mismatch is
    reported rather than left for them to discover.
    """
    root = _make_site_model(tmp_path / "drift", ["AHU-1"])
    (root / "out" / "sequencer_AHU-1.json").write_text(json.dumps(_run({
        "system": {"sequences": {"a": _sequence("pass", "stable", 10, 10)}},
    }, start_time="2026-07-23T09:34:47Z")), encoding="utf-8")

    device_out = root / "out" / "devices" / "AHU-1"
    device_out.mkdir(parents=True)
    (device_out / "results.md").write_text(
        "# AHU-1\n\n- Start 2026-07-23T09:34:06Z\n- End: 2026-07-23T09:34:31Z\n",
        encoding="utf-8",
    )

    device = compliance._device_compliance(str(root.parent), str(root), "AHU-1")
    assert device["provenance"]["run_start"] == "2026-07-23T09:34:47Z"
    assert device["provenance"]["results_md_start"] == "2026-07-23T09:34:06Z"
    assert device["provenance"]["results_md_matches_run"] is False


def test_matching_results_md_is_not_flagged(tmp_path):
    root = _make_site_model(tmp_path / "nodrift", ["AHU-1"])
    (root / "out" / "sequencer_AHU-1.json").write_text(json.dumps(_run({
        "system": {"sequences": {"a": _sequence("pass", "stable", 10, 10)}},
    }, start_time="2026-07-20T21:15:32Z")), encoding="utf-8")

    device_out = root / "out" / "devices" / "AHU-1"
    device_out.mkdir(parents=True)
    (device_out / "results.md").write_text(
        "# AHU-1\n\n- Start 2026-07-20T21:15:32Z\n- End: 2026-07-20T21:20:00Z\n",
        encoding="utf-8",
    )

    device = compliance._device_compliance(str(root.parent), str(root), "AHU-1")
    assert device["provenance"]["results_md_matches_run"] is True


def test_capabilities_are_read_as_verdicts_only(tmp_path):
    """The sequencer only ever writes `result` per capability.

    Score and total are declared in the schema but never populated, so no
    capability arithmetic may be attempted; the contribution is already inside
    the sequence's own scoring.
    """
    device = _site_with(tmp_path, "caps", _run({
        "system": {"sequences": {
            "broken_config": _sequence("pass", "stable", 9, 10, capabilities={
                "Logging": {"result": "pass"},
                "Status": {"result": "fail", "status": {"message": "nope"}},
            }),
        }},
    }))

    sequence = device["sequences"][0]
    assert sequence["capabilities"] == {"Logging": "pass", "Status": "fail"}
