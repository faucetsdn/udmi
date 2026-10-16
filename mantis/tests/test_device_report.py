"""Device-facing triage report: audit section split, rationale parsing, DUT framing.

Workbench triage answers are read by lab operators and external device
manufacturers. The hypothesis audit is still enforced by the gate, but it is
written as a trailing `## Hypothesis Resolution Audit` section so the report
leads and the audit can be shown collapsed. These tests pin the parsing of that
section, the rationale that used to arrive as null, and the scoping text that
previously claimed the device under test lives in this repository.
"""

import pytest

from mantis.agent import (
    AUDIT_SECTION_TITLE,
    SCOPING_DIRECTIVE,
    MantisAgent,
    parse_audit_entries,
    split_audit_section,
)
from mantis.config import ModelTier
from mantis.tools.diagnostics import diagnose_test_failure

REPORT = (
    "## What the device did vs what UDMI expects\n"
    "- Config sent at 12:45:05Z: `system.min_loglevel` = 200.\n"
    "- The device published no state after 12:45:05Z.\n"
    "- Governing spec: `docs/specs/sequences/config.md`, schema `schema/state.json`.\n"
    "\n"
    "## What to tell the manufacturer / what to fix on the device\n"
    "Publish a state update after every config.\n"
)

AUDIT = (
    "## Hypothesis Resolution Audit\n"
    "- **H1: The device never published state after the config.**\n"
    "  - Verdict: PRIMARY\n"
    "  - Rationale: sequence.log shows no state after 12:45:05Z; contrast with H2.\n"
    "- **H2: udmis dropped the state message.**\n"
    "  - Verdict: REFUTED\n"
    "  - RUNTIME_EVIDENCE: NONE (source inference only)\n"
    "  - Rationale: the device payload directory has no state file to drop.\n"
)


def test_split_audit_section_separates_report_from_trailing_audit():
    report, audit = split_audit_section(REPORT + "\n" + AUDIT)
    assert report == REPORT.rstrip()
    assert audit.startswith(f"## {AUDIT_SECTION_TITLE}")
    assert "H1" not in report and "PRIMARY" not in report


def test_split_audit_section_without_heading_returns_answer_unsplit():
    answer = "H1: PRIMARY because X.\nH2: REFUTED because Y."
    assert split_audit_section(answer) == (answer, None)


def test_split_audit_section_uses_the_last_heading():
    """An arbitrator that mentions the heading earlier must not lose its report."""
    answer = "### Hypothesis Resolution Audit (summary below)\nearly\n\n" + AUDIT
    report, audit = split_audit_section(answer)
    assert report.endswith("early")
    assert audit == AUDIT.strip()


def test_parse_audit_entries_extracts_rationale_and_runtime_tier():
    entries = parse_audit_entries(AUDIT, 2)
    assert entries[1] == {
        "rationale": "sequence.log shows no state after 12:45:05Z; contrast with H2.",
        "evidence_tier": None,
    }
    assert entries[2] == {
        "rationale": "the device payload directory has no state file to drop.",
        "evidence_tier": "NONE",
    }


def test_parse_audit_entries_reads_inline_verdict_bullets():
    """The format models actually emit: the verdict leads the rationale bullet."""
    audit = (
        "### Hypothesis Resolution Audit\n"
        "* **H1: The device failed to report the required error status.**\n"
        "  * **REFUTED**: The test timed out during setUp() before the broken\n"
        "    configuration was ever sent.\n"
        "* **H2: The sequencer ignored the state update as stale.**\n"
        "  * **PRIMARY**: The state timestamp lagged the cutoff threshold.\n"
    )
    entries = parse_audit_entries(audit, 2)
    assert entries[1]["rationale"] == (
        "The test timed out during setUp() before the broken configuration was ever sent."
    )
    assert entries[2]["rationale"] == "The state timestamp lagged the cutoff threshold."


def test_parse_audit_entries_single_line_entries_and_missing_hypotheses():
    entries = parse_audit_entries("H1: UNRESOLVED needs broker logs.\n", 2)
    assert entries[1]["rationale"] == "needs broker logs."
    assert entries[2] == {"rationale": None, "evidence_tier": None}


def test_gate_is_still_enforced_on_a_report_with_trailing_audit():
    """Moving the audit to a trailing section must not weaken the gate."""
    agent = MantisAgent()
    assert agent._audit_violations(REPORT + "\n" + AUDIT, 2) == []
    double_primary = AUDIT.replace("Verdict: REFUTED", "Verdict: PRIMARY")
    assert any("PRIMARY" in v for v in agent._audit_violations(REPORT + double_primary, 2))
    assert any("No verdict" in v for v in agent._audit_violations(REPORT, 2))


def test_scoping_directive_treats_the_device_under_test_as_external():
    assert "both live in this repository" not in SCOPING_DIRECTIVE
    assert "device under test is NOT in this repository" in SCOPING_DIRECTIVE
    assert "Never cite pubber source as evidence" in SCOPING_DIRECTIVE
    assert "## Hypothesis Resolution Audit" in SCOPING_DIRECTIVE
    assert "Rationale:" in SCOPING_DIRECTIVE


class _Response:
    def __init__(self, text):
        self.text = text
        self.function_calls = []
        self.candidates = []


class _Models:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def generate_content(self, model, contents, config):
        self.calls.append((model, contents, config))
        return self.responses.pop(0)


class _Client:
    def __init__(self, responses):
        self.models = _Models(responses)


PLAN = (
    "FAILURE_SCOPE: SINGLE_TARGET\n"
    "SCOPE_JUSTIFICATION: One device failed one test.\n"
    "COMPETING_HYPOTHESES:\n"
    "- H1: The device never published state after the config.\n"
    "- H2: The sequencer rejected the state as stale.\n"
    "REQUIRED_SUBSYSTEMS: validator\n"
)

ACTOR_AUDIT = (
    "## Hypothesis Resolution Audit\n"
    "- **H1: The device never published state after the config.**\n"
    "  - Verdict: PRIMARY\n"
    "  - Rationale: no state payload exists after the config at 12:45:05Z.\n"
    "- **H2: The sequencer rejected the state as stale.**\n"
    "  - Verdict: REFUTED\n"
    "  - Rationale: sequence.log records no stale-state rejection.\n"
)


def test_audit_record_carries_parsed_rationale_and_the_arbitrator_keeps_the_loop():
    """The tripartite loop still runs, and the audit record reports the real
    per-hypothesis rationale rather than null."""
    client = _Client([
        _Response(PLAN),
        _Response(REPORT + "\n" + ACTOR_AUDIT),
        _Response("- Verified Claims: all."),
        _Response(REPORT + "\n" + ACTOR_AUDIT),
    ])
    events = []
    answer = MantisAgent(client=client)._run_llm(
        prompt="Diagnose why test x failed",
        tier=ModelTier.PRO,
        enable_tripartite=True,
        event_callback=events.append,
    )
    assert answer.startswith("## What the device did vs what UDMI expects")
    phases = [e["phase"] for e in events if e["type"] == "phase"]
    assert "CRITIC" in phases and "ARBITRATOR" in phases

    _, _, arbitrator_cfg = client.models.calls[3]
    assert "LAST section" in arbitrator_cfg.system_instruction
    assert "## Hypothesis Resolution Audit" in arbitrator_cfg.system_instruction

    audit = [e for e in events if e["type"] == "audit"][0]
    rows = audit["hypotheses"]
    assert [r["verdict"] for r in rows] == ["PRIMARY", "REFUTED"]
    assert rows[0]["rationale"] == "no state payload exists after the config at 12:45:05Z."
    assert rows[1]["rationale"] == "sequence.log records no stale-state rejection."
    assert audit["audit_section"] == ACTOR_AUDIT.strip()


def test_stale_state_report_names_the_device_under_test_not_pubber(tmp_path):
    run_dir = tmp_path / "run_stale"
    run_dir.mkdir()
    (run_dir / "sequence.log").write_text(
        "2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1\n"
        "2026-08-26T12:45:05Z Cutoff set: 12:45:08Z\n"
        "2026-08-26T12:45:07Z ignoring stale state update timestamp 12:45:06Z\n"
        "2026-08-26T12:47:01Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed\n"
    )
    res = diagnose_test_failure(test_id="pointset_publish", device_id="AHU-1", run_dir=str(run_dir))
    report = res["report"]
    assert "participant D as Device under test" in report
    assert "Pubber" not in report
    assert "bin/mantis" not in report
    assert not any("bin/mantis" in fix for fix in res["fix"])
