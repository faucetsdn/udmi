"""Behaviour of workbench/static/js/components/test-list.js under Node.

A minimal DOM shim stands in for the browser (no jsdom in this repo). It
covers only what TestList touches, and asserts the contract that matters:
every stage stays visible and searchable, the minimum stage only locks
selection, and expanded rows show steps, spec links and the Explain action.
"""

import json
import os
import shutil
import subprocess

import pytest

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEST_LIST_MODULE = os.path.join(REPO, "workbench", "static", "js", "components", "test-list.js")

DOM_SHIM = r"""
class ClassList {
  constructor(el) { this.el = el; }
  _get() { return new Set((this.el.className || '').split(/\s+/).filter(Boolean)); }
  _set(s) { this.el.className = [...s].join(' '); }
  add(c) { const s = this._get(); s.add(c); this._set(s); }
  remove(c) { const s = this._get(); s.delete(c); this._set(s); }
  contains(c) { return this._get().has(c); }
  toggle(c, force) {
    const on = force === undefined ? !this.contains(c) : Boolean(force);
    on ? this.add(c) : this.remove(c);
    return on;
  }
}
class El {
  constructor(tag) {
    this.tagName = tag.toUpperCase(); this.children = []; this.attrs = {}; this.dataset = {};
    this.className = ''; this.classList = new ClassList(this); this.hidden = false;
    this.listeners = {}; this._text = '';
  }
  set textContent(v) { this.children = []; this._text = String(v); }
  get textContent() { return this._text + this.children.map((c) => c.textContent).join(''); }
  set innerHTML(v) {
    this.children = [];
    const m = /^<span[^>]*>([^<]*)<\/span>$/.exec(v);
    if (!m) throw new Error('shim innerHTML supports a single <span> only: ' + v);
    const span = new El('span'); span.textContent = m[1]; this.children.push(span);
  }
  append(...nodes) {
    for (const n of nodes) {
      if (typeof n === 'string') { const t = new El('#text'); t.textContent = n; this.children.push(t); }
      else if (n.tagName === '#FRAGMENT') this.children.push(...n.children);
      else this.children.push(n);
    }
  }
  appendChild(n) { this.append(n); return n; }
  hasChildNodes() { return this.children.length > 0; }
  setAttribute(k, v) { this.attrs[k] = String(v); }
  getAttribute(k) { return this.attrs[k] ?? null; }
  removeAttribute(k) { delete this.attrs[k]; }
  addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
  click() { for (const fn of this.listeners.click || []) fn({ stopPropagation() {} }); }
  *walk() { for (const c of this.children) { yield c; yield* c.walk(); } }
  querySelectorAll(sel) {
    const match = sel.startsWith('.')
      ? (e) => e.classList.contains(sel.slice(1))
      : (e) => e.tagName === sel.toUpperCase();
    return [...this.walk()].filter(match);
  }
  querySelector(sel) { return this.querySelectorAll(sel)[0] ?? null; }
}
globalThis.document = {
  createElement: (tag) => new El(tag),
  createDocumentFragment: () => new El('#fragment'),
};
"""

SEQUENCES = [
    {"name": "broken_config", "bucket": "system", "stage": "STABLE", "score": 10,
     "nostate": False, "ignored": False, "facets": None, "capabilities": [],
     "summary": "Check broken config handling.",
     "declaring_class": "com.google.daq.mqtt.sequencer.sequences.ConfigSequences",
     "steps": [], "steps_path": "validator/sequences/broken_config/sequence.md",
     "reference_outcome": "Test skipped: Not a proxied device",
     "spec_path": "docs/specs/sequences/config.md", "message_path": "docs/messages/system.md"},
    {"name": "writeback_success", "bucket": "writeback", "stage": "ALPHA", "score": 10,
     "nostate": False, "ignored": False, "facets": None, "capabilities": [],
     "summary": "Implements UDMI writeback and can successfully writeback to a point",
     "declaring_class": "com.google.daq.mqtt.sequencer.sequences.WritebackSequences",
     "steps": [{"text": "Update config before target point has value_state applied",
                "details": ["Add `pointset.points.x.set_value` = `60`"]},
               {"text": "Wait until target point has value_state applied", "details": []}],
     "steps_path": "validator/sequences/writeback_success/sequence.md",
     "reference_outcome": "Test passed.",
     "spec_path": "docs/specs/sequences/writeback.md", "message_path": "docs/messages/pointset.md"},
    {"name": "writeback_invalid", "bucket": "writeback", "stage": "ALPHA", "score": 10,
     "nostate": True, "ignored": False, "facets": None, "capabilities": [],
     "summary": None,
     "declaring_class": "com.google.daq.mqtt.sequencer.sequences.WritebackSequences",
     "steps": None, "steps_path": None, "reference_outcome": None,
     "spec_path": "docs/specs/sequences/writeback.md", "message_path": None},
]

SCENARIO = r"""
const { TestList } = await import(MODULE);
const container = document.createElement('div');
const explained = [];
const list = new TestList(container, {
  onToggle() {}, onOpenArtifacts() {}, onExplain: (name) => explained.push(name),
});
list.setSequences(SEQUENCES);
const out = {};

// Default: nothing filtered, ALPHA included, even with an ALPHA-excluding gate.
list.setStageExcluded('writeback_success', true, 'PREVIEW');
list.setStageExcluded('writeback_invalid', true, 'PREVIEW');
list.applyFilters('', '');
out.visibleDefault = list.visibleTestNames();

// Search reaches ALPHA tests by summary and by class.
list.applyFilters('successfully writeback', '');
out.searchSummary = list.visibleTestNames();
list.applyFilters('WritebackSequences', '');
out.searchClass = list.visibleTestNames();
list.applyFilters('', '');

// Stage gate locks selection only; a stale selection can still be cleared.
const refs = list.rows.get('writeback_success');
out.excludedDisabled = refs.checkbox.disabled;
out.excludedHidden = refs.row.hidden;
list.setSelection(['writeback_success']);
out.staleSelectedDisabled = refs.checkbox.disabled;
list.setSelection([]);
list.setStageExcluded('writeback_success', false, 'ALPHA');
out.admittedDisabled = refs.checkbox.disabled;
out.isExcluded = list.isStageExcluded('writeback_invalid');

// Missing summary is explicit.
out.missingSummary = list.rows.get('writeback_invalid').row.querySelector('.test-desc').textContent;

// Expand: steps, sub-bullets, doc links, explain button.
list.toggleDetails('writeback_success');
const details = refs.details;
out.detailsHidden = details.hidden;
out.steps = details.querySelectorAll('li').map((li) => li._text);
out.links = details.querySelectorAll('.test-doc-link').map((a) => [a.textContent, a.href]);
details.querySelector('.test-explain').click();
list.toggleDetails('writeback_invalid');
out.noSteps = list.rows.get('writeback_invalid').details.querySelector('.empty-note').textContent;
list.toggleDetails('broken_config');
list.rows.get('broken_config').details.querySelector('.test-explain').click();
out.skippedSource = list.rows.get('broken_config').details.querySelector('.test-details-source').textContent;
out.explained = explained;

list.setError('bin/sequencer_catalog failed: ERROR: validator jar is stale');
out.error = container.querySelector('.catalog-error').textContent;
out.rowsAfterError = list.rows.size;

let threw = null;
try { new TestList(container, { onToggle() {}, onOpenArtifacts() {} }); } catch (e) { threw = e.message; }
out.requiresExplain = threw;
process.stdout.write(JSON.stringify(out));
"""


def _run():
    node = shutil.which("node")
    if node is None:
        pytest.fail("node is required to test workbench/static/js modules and is not on PATH.")
    script = (
        DOM_SHIM
        + f"const MODULE = {json.dumps('file://' + TEST_LIST_MODULE)};"
        + f"const SEQUENCES = {json.dumps(SEQUENCES)};"
        + SCENARIO
    )
    done = subprocess.run(
        [node, "--input-type=module", "-e", script],
        capture_output=True, text=True, timeout=60, check=False,
    )
    assert done.returncode == 0, done.stderr
    return json.loads(done.stdout)


@pytest.fixture(scope="module")
def result():
    return _run()


def test_all_stages_visible_by_default(result):
    assert result["visibleDefault"] == ["broken_config", "writeback_success", "writeback_invalid"]
    assert result["excludedHidden"] is False


def test_search_finds_alpha_tests(result):
    assert result["searchSummary"] == ["writeback_success"]
    assert result["searchClass"] == ["writeback_success", "writeback_invalid"]


def test_min_stage_gates_selection_not_visibility(result):
    assert result["excludedDisabled"] is True
    assert result["staleSelectedDisabled"] is False
    assert result["admittedDisabled"] is False
    assert result["isExcluded"] is True


def test_missing_summary_is_explicit(result):
    assert result["missingSummary"] == "No @Summary on WritebackSequences#writeback_invalid"


def test_expanded_row_shows_steps_links_and_explain(result):
    assert result["detailsHidden"] is False
    assert result["steps"] == [
        "Update config before target point has value_state applied",
        "Add `pointset.points.x.set_value` = `60`",
        "Wait until target point has value_state applied",
    ]
    assert result["links"] == [
        ["Spec: docs/specs/sequences/writeback.md",
         "/api/repo-doc?path=docs%2Fspecs%2Fsequences%2Fwriteback.md"],
        ["Message: docs/messages/pointset.md",
         "/api/repo-doc?path=docs%2Fmessages%2Fpointset.md"],
    ]
    assert "does not exist yet" in result["noSteps"]
    assert "recorded no steps" in result["skippedSource"]
    assert "Test skipped: Not a proxied device" in result["skippedSource"]
    # Explain works for a never-run ALPHA test and a skipped STABLE one alike.
    assert result["explained"] == ["writeback_success", "broken_config"]


def test_catalog_error_replaces_list(result):
    assert "Sequence catalog unavailable" in result["error"]
    assert "validator jar is stale" in result["error"]
    assert result["rowsAfterError"] == 0
    assert "onExplain" in result["requiresExplain"]
