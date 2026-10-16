"""Unit tests for mantis.skills."""

import pytest
from mantis.skills import SkillManager


def test_skills_discovery():
    mgr = SkillManager()
    skills = mgr.list_skills()
    assert len(skills) >= 5
    names = [s["name"] for s in skills]
    assert "udmi-spec-navigation" in names
    assert "state-machine-investigation" in names
    assert "sequencer-test-anatomy" in names
    assert "gateway-fieldbus-triage" in names
    assert "log-anomaly-hunting" in names
    assert "database-inspection" in names


def test_get_skill():
    mgr = SkillManager()
    skill = mgr.get_skill("log-anomaly-hunting")
    assert skill is not None
    assert skill.name == "log-anomaly-hunting"
    assert "Distributed Log Observability" in skill.content
    assert skill.is_builtin is True

    skill_spec = mgr.get_skill("udmi-spec-navigation")
    assert skill_spec is not None
    assert "Specification & Schema Navigation" in skill_spec.content


def test_match_skills():
    mgr = SkillManager()
    matched = mgr.match_skills("How does the state machine handle cutoff synchronization?")
    assert len(matched) > 0
    names = [s.name for s in matched]
    assert "state-machine-investigation" in names

    matched_gw = mgr.match_skills("Triage gateway proxy and fieldbus communication")
    assert len(matched_gw) > 0
    names_gw = [s.name for s in matched_gw]
    assert "gateway-fieldbus-triage" in names_gw


def test_get_system_prompt_catalog():
    mgr = SkillManager()
    catalog = mgr.get_system_prompt_catalog()
    assert "Available Domain Skills Catalog" in catalog
    assert "udmi-spec-navigation" in catalog
    assert "state-machine-investigation" in catalog


def test_skills_yaml_multiline_frontmatter(tmp_path):
    skill_dir = tmp_path / "mantis" / "skills" / "folded-skill"
    skill_dir.mkdir(parents=True)
    skill_file = skill_dir / "SKILL.md"
    skill_file.write_text("""---
name: folded-skill
description: >
  This is a multi-line folded YAML description
  that spans multiple indented lines
  and describes advanced protocol operations.
---
# Body
This is the skill body.
""")

    mgr = SkillManager(udmi_root=str(tmp_path))
    skill = mgr.get_skill("folded-skill")
    assert skill is not None
    assert skill.name == "folded-skill"
    assert "multi-line folded YAML description" in skill.description
    assert "advanced protocol operations" in skill.description
    assert "This is the skill body." in skill.content


def test_skills_no_arbitrary_fallback():
    mgr = SkillManager()
    # Completely irrelevant query should NOT return arbitrary fallback skills
    res = mgr.load_skills_for_context("What is the recipe for chocolate cake?")
    assert res == "", f"Expected empty string for irrelevant query, got: {res}"


def test_skills_stop_words_no_false_positive():
    mgr = SkillManager()
    # Query with generic stop words (where, which, using, error, point, times)
    # should NOT trigger false positive matches for all skills
    matched = mgr.match_skills("Where which using error point times these those about?")
    assert len(matched) == 0, f"Expected 0 matches for stop-word soup, got: {[s.name for s in matched]}"


def test_skills_conjunction_and_generic_phrases_no_false_positive():
    mgr = SkillManager()
    # Conjunctions like 'and' and generic verbs must not trigger false positives
    matched = mgr.match_skills("Diagnose the failure and report")
    assert len(matched) == 0, f"Expected 0 matches for generic phrase with 'and', got: {[s.name for s in matched]}"


