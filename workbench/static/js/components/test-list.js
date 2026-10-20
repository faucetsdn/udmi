/**
 * Layer 1 — Sequence selection list.
 *
 * Rows are built once and then patched in place. v1 rebuilt the entire list
 * with innerHTML on every status change, which reset the search filter, wiped
 * focus, and churned the DOM twice per test. This implementation keeps a node
 * index and only touches the parts that actually changed.
 *
 * Every compiled sequence is always listed, whatever its feature stage: the
 * minimum stage only decides what can be selected and run, never what can be
 * read. Each row expands to show the @Summary, the recorded steps, and the
 * governing spec pages, so a device maker can learn what an unimplemented
 * (ALPHA) feature expects.
 *
 * Purely presentational: it receives data and emits semantic callbacks.
 */

import { docHref } from './mantis-markdown.js';

const STATUS_GLYPH = {
  pass: '\u2713',
  fail: '\u2717',
  skip: '\u2013',
  running: '\u25CF',
  queued: '\u25CB',
  unknown: '\u00B7',
};

const STATUS_LABEL = {
  pass: 'Passed',
  fail: 'Failed',
  skip: 'Skipped',
  running: 'Running',
  queued: 'Queued',
  unknown: 'Not run',
};

export class TestList {
  constructor(container, { onToggle, onOpenArtifacts, onDiagnose = null, onExplain } = {}) {
    if (typeof onExplain !== 'function') {
      throw new Error('TestList requires an onExplain callback for the "Explain this test" action.');
    }
    this.container = container;
    this.onToggle = onToggle;
    this.onOpenArtifacts = onOpenArtifacts;
    this.onDiagnose = onDiagnose;
    this.onExplain = onExplain;
    this.rows = new Map();
    this.sequences = [];
    this.running = false;
  }

  /** Builds the row set. Called only when the sequence catalog itself changes. */
  setSequences(sequences) {
    this.sequences = sequences;
    this.rows.clear();
    this.container.textContent = '';

    if (sequences.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'empty-note';
      empty.textContent = 'The compiled validator reports no sequences.';
      this.container.appendChild(empty);
      return;
    }

    let currentBucket = null;
    const fragment = document.createDocumentFragment();

    for (const sequence of sequences) {
      const bucket = sequence.bucket || 'uncategorised';
      if (bucket !== currentBucket) {
        currentBucket = bucket;
        const header = document.createElement('div');
        header.className = 'bucket-header';
        header.textContent = bucket;
        header.dataset.bucket = bucket;
        fragment.appendChild(header);
      }
      fragment.appendChild(this._buildRow(sequence));
    }
    this.container.appendChild(fragment);
  }

  /** Replaces the list with an explicit catalog failure; no partial list is shown. */
  setError(message) {
    this.sequences = [];
    this.rows.clear();
    this.container.textContent = '';
    const error = document.createElement('div');
    error.className = 'catalog-error';
    error.setAttribute('role', 'alert');
    const title = document.createElement('strong');
    title.textContent = 'Sequence catalog unavailable';
    const detail = document.createElement('pre');
    detail.textContent = message;
    error.append(title, detail);
    this.container.appendChild(error);
  }

  _buildRow(sequence) {
    const row = document.createElement('div');
    row.className = 'test-row';
    row.dataset.test = sequence.name;
    row.dataset.bucket = sequence.bucket || 'uncategorised';

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'test-check';
    checkbox.id = `test-${sequence.name}`;
    checkbox.addEventListener('change', () => this.onToggle(sequence.name, checkbox.checked));

    const label = document.createElement('label');
    label.className = 'test-label';
    label.htmlFor = checkbox.id;

    const nameEl = document.createElement('span');
    nameEl.className = 'test-name';
    nameEl.textContent = sequence.name;

    const stageEl = document.createElement('span');
    stageEl.className = `stage-chip stage-${(sequence.stage || 'unknown').toLowerCase()}`;
    stageEl.textContent = sequence.stage || 'UNKNOWN';

    const title = document.createElement('div');
    title.className = 'test-title-line';
    title.append(nameEl, stageEl);

    const description = document.createElement('div');
    description.className = 'test-desc';
    if (sequence.summary) {
      description.textContent = sequence.summary;
    } else {
      description.classList.add('is-missing');
      description.textContent = `No @Summary on ${shortClass(sequence.declaring_class)}#${sequence.name}`;
    }

    const provenance = document.createElement('div');
    provenance.className = 'test-provenance';

    label.append(title, description, provenance);

    const actions = document.createElement('div');
    actions.className = 'test-actions';

    const diagnoseButton = document.createElement('button');
    diagnoseButton.type = 'button';
    diagnoseButton.className = 'btn-diagnose';
    diagnoseButton.textContent = '🔍 Diagnose with Mantis';
    diagnoseButton.title = 'Diagnose test failure with Mantis Assistant';
    diagnoseButton.hidden = true;
    diagnoseButton.addEventListener('click', (event) => {
      event.stopPropagation();
      this.onDiagnose?.(sequence.name);
    });

    const statusButton = document.createElement('button');
    statusButton.type = 'button';
    statusButton.className = 'status-chip status-unknown';
    statusButton.textContent = STATUS_GLYPH.unknown;
    statusButton.title = STATUS_LABEL.unknown;
    statusButton.disabled = true;
    statusButton.addEventListener('click', () => this.onOpenArtifacts(sequence.name));

    const expandButton = document.createElement('button');
    expandButton.type = 'button';
    expandButton.className = 'btn-icon test-expand';
    expandButton.setAttribute('aria-expanded', 'false');
    expandButton.setAttribute('aria-label', `Show details for ${sequence.name}`);
    expandButton.title = 'Show summary, steps and spec';
    expandButton.innerHTML =
      '<span class="material-symbols-outlined" aria-hidden="true">expand_more</span>';

    const details = document.createElement('div');
    details.className = 'test-details';
    details.hidden = true;
    details.id = `test-details-${sequence.name}`;
    expandButton.setAttribute('aria-controls', details.id);
    expandButton.addEventListener('click', (event) => {
      event.stopPropagation();
      this.toggleDetails(sequence.name);
    });

    actions.append(diagnoseButton, expandButton, statusButton);
    row.append(checkbox, label, actions, details);
    this.rows.set(sequence.name, {
      row, checkbox, statusButton, diagnoseButton, provenance, expandButton, details,
      sequence, stageExcluded: false,
    });
    return row;
  }

  /** Expands or collapses one row; details are built on first expand. */
  toggleDetails(testName) {
    const refs = this.rows.get(testName);
    if (!refs) throw new Error(`Unknown sequence '${testName}'`);
    const open = refs.details.hidden;
    if (open && !refs.details.hasChildNodes()) {
      refs.details.appendChild(this._buildDetails(refs.sequence));
    }
    refs.details.hidden = !open;
    refs.row.classList.toggle('is-expanded', open);
    refs.expandButton.setAttribute('aria-expanded', String(open));
    refs.expandButton.querySelector('span').textContent = open ? 'expand_less' : 'expand_more';
  }

  _buildDetails(sequence) {
    const fragment = document.createDocumentFragment();

    const summary = document.createElement('p');
    summary.className = 'test-details-summary';
    summary.textContent = sequence.summary ||
      `This test has no @Summary annotation. Add one to ${sequence.declaring_class}#${sequence.name}.`;
    fragment.appendChild(summary);

    const meta = document.createElement('dl');
    meta.className = 'test-details-meta';
    const facts = [
      ['Stage', sequence.stage],
      ['Bucket', sequence.bucket],
      ['Score', String(sequence.score)],
      ['No-state', sequence.nostate ? 'yes (runs without state updates)' : 'no'],
      ['Facets', sequence.facets || 'none'],
      ['Class', sequence.declaring_class],
    ];
    if (sequence.capabilities.length > 0) {
      facts.push(['Capabilities',
        sequence.capabilities.map((cap) => `${cap.name} (${cap.stage})`).join(', ')]);
    }
    if (sequence.ignored) facts.push(['Ignored', '@Ignore: JUnit will not execute it']);
    for (const [term, value] of facts) {
      const dt = document.createElement('dt');
      dt.textContent = term;
      const dd = document.createElement('dd');
      dd.textContent = value;
      meta.append(dt, dd);
    }
    fragment.appendChild(meta);

    fragment.appendChild(this._buildSteps(sequence));

    const links = document.createElement('div');
    links.className = 'test-details-links';
    for (const [label, path] of [['Spec', sequence.spec_path], ['Message', sequence.message_path]]) {
      if (!path) continue;
      links.appendChild(docLink(label, path));
    }

    const explain = document.createElement('button');
    explain.type = 'button';
    explain.className = 'btn btn-sm btn-tonal test-explain';
    explain.innerHTML =
      '<span class="material-symbols-outlined icon-inline" aria-hidden="true">neurology</span>';
    explain.append(' Explain this test');
    explain.title = 'Ask Mantis how this test works and what a device must do to pass it';
    explain.addEventListener('click', (event) => {
      event.stopPropagation();
      this.onExplain(sequence.name);
    });
    links.appendChild(explain);
    fragment.appendChild(links);
    return fragment;
  }

  _buildSteps(sequence) {
    const section = document.createElement('div');
    section.className = 'test-details-steps';
    const heading = document.createElement('h4');
    heading.textContent = 'Recorded steps';
    section.appendChild(heading);

    if (sequence.steps === null) {
      const note = document.createElement('p');
      note.className = 'empty-note';
      note.textContent =
        `No recorded steps: validator/sequences/${sequence.name}/sequence.md does not exist yet ` +
        '(it is written by bin/sequencer_cache after a sequencer run).';
      section.appendChild(note);
      return section;
    }

    if (sequence.steps.length > 0) {
      const list = document.createElement('ol');
      for (const step of sequence.steps) {
        const item = document.createElement('li');
        item.textContent = step.text;
        if (step.details.length > 0) {
          const sub = document.createElement('ul');
          for (const detail of step.details) {
            const subItem = document.createElement('li');
            subItem.textContent = detail;
            sub.appendChild(subItem);
          }
          item.appendChild(sub);
        }
        list.appendChild(item);
      }
      section.appendChild(list);
    }

    const source = document.createElement('p');
    source.className = 'test-details-source';
    source.textContent = [
      sequence.steps.length === 0 ? 'The reference run recorded no steps.' : null,
      sequence.reference_outcome ? `Reference outcome: ${sequence.reference_outcome}` : null,
      `Source: ${sequence.steps_path}`,
    ].filter(Boolean).join(' ');
    section.appendChild(source);
    return section;
  }

  /** Patches checkbox state without rebuilding rows. */
  setSelection(selectedTests) {
    const selected = new Set(selectedTests);
    for (const [name, refs] of this.rows) {
      refs.checkbox.checked = selected.has(name);
      this._syncCheckbox(refs);
    }
  }

  setDisabled(disabled) {
    this.running = Boolean(disabled);
    for (const refs of this.rows.values()) {
      this._syncCheckbox(refs);
    }
  }

  /**
   * A stage-excluded row cannot be newly selected, but a stale selection can
   * still be cleared, so it is only locked while unchecked.
   */
  _syncCheckbox(refs) {
    refs.checkbox.disabled = this.running || (refs.stageExcluded && !refs.checkbox.checked);
  }

  /** Patches one row's status glyph and artifact affordance. */
  setStatus(testName, status, recorded = null) {
    const refs = this.rows.get(testName);
    if (!refs) return;

    const resolved = status || 'unknown';
    refs.statusButton.className = `status-chip status-${resolved}`;
    refs.statusButton.textContent = STATUS_GLYPH[resolved] ?? STATUS_GLYPH.unknown;
    refs.statusButton.title = recorded?.artifacts?.length
      ? `${STATUS_LABEL[resolved] ?? 'Unknown'} — view artifacts`
      : STATUS_LABEL[resolved] ?? 'Unknown';
    refs.statusButton.disabled = !recorded?.artifacts?.length;

    if (refs.diagnoseButton) {
      refs.diagnoseButton.hidden = resolved !== 'fail';
    }

    refs.provenance.textContent = '';
    if (recorded?.timestamp) {
      const when = document.createElement('span');
      when.textContent = new Date(recorded.timestamp).toLocaleString();
      refs.provenance.appendChild(when);
    }
    if (recorded?.project_id) {
      const target = document.createElement('span');
      target.className = 'provenance-target';
      target.textContent = recorded.project_id;
      refs.provenance.appendChild(target);
    }
  }

  /**
   * Flags a row whose feature stage is below the active minimum stage.
   * Such a sequence is skipped by the runner without reporting any result,
   * so it stays visible and readable but cannot be selected to run.
   */
  setStageExcluded(testName, excluded, minStage) {
    const refs = this.rows.get(testName);
    if (!refs) return;

    refs.stageExcluded = Boolean(excluded);
    refs.row.classList.toggle('is-stage-excluded', refs.stageExcluded);
    if (excluded) {
      refs.checkbox.title =
        `Below the "${minStage}" minimum stage — it cannot be selected to run. ` +
        'Lower the minimum stage to run it; its details stay readable either way.';
    } else {
      refs.checkbox.removeAttribute('title');
    }
    this._syncCheckbox(refs);
  }

  /** Whether a row is currently blocked by the minimum stage. */
  isStageExcluded(testName) {
    return Boolean(this.rows.get(testName)?.stageExcluded);
  }

  /** Scrolls the active test into view so the running row is never off-screen. */
  revealTest(testName) {
    this.rows.get(testName)?.row.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  /**
   * Applies search and bucket filters to row visibility. Feature stage is
   * deliberately not a visibility filter: every test stays findable.
   */
  applyFilters(query, bucket) {
    const needle = (query || '').trim().toLowerCase();
    const visibleBuckets = new Set();

    for (const sequence of this.sequences) {
      const refs = this.rows.get(sequence.name);
      if (!refs) continue;
      const haystack = [
        sequence.name, sequence.summary, sequence.bucket, sequence.stage, sequence.declaring_class,
      ].filter(Boolean).join('\n').toLowerCase();
      const matchesQuery = !needle || haystack.includes(needle);
      const matchesBucket = !bucket || (sequence.bucket || 'uncategorised') === bucket;

      const visible = matchesQuery && matchesBucket;
      refs.row.hidden = !visible;
      if (visible) visibleBuckets.add(sequence.bucket || 'uncategorised');
    }

    for (const header of this.container.querySelectorAll('.bucket-header')) {
      header.hidden = !visibleBuckets.has(header.dataset.bucket);
    }
    return visibleBuckets.size;
  }

  /** Names of rows currently passing the active filters. */
  visibleTestNames() {
    return this.sequences
      .filter((sequence) => this.rows.get(sequence.name)?.row.hidden === false)
      .map((sequence) => sequence.name);
  }
}

function shortClass(className) {
  return String(className).split('.').pop();
}

/** Opens a repository doc through the same /api/repo-doc route Mantis citations use. */
function docLink(label, path) {
  const href = docHref(path);
  if (!href) {
    throw new Error(`Sequence catalog doc path '${path}' is not a servable docs/*.md path.`);
  }
  const link = document.createElement('a');
  link.className = 'test-doc-link';
  link.href = href;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.textContent = `${label}: ${path}`;
  link.addEventListener('click', (event) => event.stopPropagation());
  return link;
}
