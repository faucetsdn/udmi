/**
 * Layer 2 — Device compliance view.
 *
 * Shows what the sequencer actually recorded for every device in a site model:
 * the feature-bucket score matrix, the target each run was actually pointed at,
 * and the reports that exist on disk. The previous version of this screen
 * rendered metadata.json and nothing else, which told an operator nothing about
 * whether a device had been tested.
 *
 * Four honesty rules govern the rendering. All four are properties of the
 * compliance model that a prettier summary would destroy:
 *
 *   1. A score belongs to a (feature bucket, stage) pair, never to a device.
 *      This screen used to print one summed `Score: value / total` per device.
 *      That figure does not exist in UDMI: it added up buckets that are scored
 *      independently and folded unreleased alpha tests into what looked like a
 *      certification result. The matrix below is the same one
 *      `bin/sequencer_report` writes into `results.md`, so the screen and the
 *      downloadable report can never disagree.
 *
 *   2. `0/0` means not applicable, not zero percent. A skipped sequence
 *      contributes nothing to either side of the fraction, so a bucket nobody
 *      exercised is rendered as a dash, never as a failure.
 *
 *   3. Only `stable` and `beta` decide a verdict. A bucket whose only results
 *      are `alpha` or `preview` is NOT ASSESSED even at full marks, which is
 *      why a bucket can read `10/10` and still carry a dash.
 *
 *   4. The test target is a property of a run, not of the site model. Devices
 *      in one site model are routinely run against different projects, so the
 *      target is read back from the envelopes the run actually captured and
 *      shown per device. Where a device shows more than one, all are listed.
 */

import { api } from '../core/api.js';
import { store } from '../core/store.js';
import { SiteRootsDialog } from '../components/site-roots.js';
import { CommitDialog, requirePayloadFields } from '../components/commit-dialog.js';

/** The buckets compliance.py tallies. Anything else is the remainder. */
const RESULT_BUCKETS = ['pass', 'fail', 'skip'];

/** Report kind -> the file name it downloads as, for the button label. */
const REPORT_LABELS = {
  results_md: 'results.md',
  result_log: 'RESULT.log',
  sequencer_json: 'sequencer.json',
};

/**
 * The three states a bucket verdict can hold. `null` is not a missing value:
 * it is the model's own "not assessed", and it is rendered as its own glyph so
 * it can never be mistaken for a pass or a failure.
 * Mirrors TemplateHelper.result_icon in bin/sequencer_report.
 */
const VERDICT_GLYPH = { true: '\u2713', false: '\u2715', null: '\u2013' };
const VERDICT_LABEL = {
  true: 'Passed',
  false: 'Failed',
  null: 'Not assessed at a releasable stage',
};
const VERDICT_TONE = { true: 'pass', false: 'fail', null: 'none' };

const DEVICE_FIELDS = [
  'device_id',
  'has_results',
  'reason',
  'last_run',
  'start_time',
  'udmi_version',
  'status_message',
  'counts',
  'features',
  'stages',
  'verdict',
  'sequences',
  'unscored',
  'targets',
  'provenance',
  'reports',
];

const COUNT_FIELDS = ['pass', 'fail', 'skip', 'total'];

export class DevicesView {
  constructor(root) {
    this.root = root;
    this.siteModels = [];
    this.devices = [];
    this.render();
  }

  render() {
    this.root.innerHTML = `
      <div class="workspace workspace-split">
        <aside class="panel panel-config">
          <h2 class="panel-title">Workspace</h2>
          <div class="field">
            <label class="visually-hidden" for="dev-site-model">Site model</label>
            <select id="dev-site-model" data-role="site"></select>
            <button type="button" class="link-button" data-act="manage-paths">
              Site model somewhere else? Add its path…
            </button>
          </div>
          <p class="site-facts" data-role="facts"></p>
          <h2 class="panel-title">Site rollup</h2>
          <dl class="compliance-totals" data-role="totals"></dl>
          <p class="field-hint">
            Scores come from <code>out/sequencer_&lt;device&gt;.json</code> and are counted per
            feature bucket, exactly as <code>results.md</code> reports them. A bucket showing
            <code>0/0</code> was not exercised; it is not a zero score.
          </p>
          <div class="site-commit" data-role="site-commit"></div>
        </aside>

        <section class="panel">
          <div class="panel-toolbar">
            <h2 class="panel-title">
              Sequencer compliance <span class="count" data-role="count"></span>
            </h2>
          </div>
          <div class="compliance-list" data-role="devices"></div>
        </section>
      </div>
      <div data-mount="site-roots"></div>
    `;

    this.siteSelect = this.root.querySelector('[data-role="site"]');
    this.factsEl = this.root.querySelector('[data-role="facts"]');
    this.totalsEl = this.root.querySelector('[data-role="totals"]');
    this.countEl = this.root.querySelector('[data-role="count"]');
    this.deviceListEl = this.root.querySelector('[data-role="devices"]');
    this.siteCommitEl = this.root.querySelector('[data-role="site-commit"]');

    this.commitDialog = new CommitDialog();

    this.siteRoots = new SiteRootsDialog(this.root.querySelector('[data-mount="site-roots"]'), {
      loadRoots: () => api.listSiteRoots(),
      addRoot: (path) => api.addSiteRoot(path),
      removeRoot: (path) => api.removeSiteRoot(path),
      onChange: () => this.init(),
    });

    this.root
      .querySelector('[data-act="manage-paths"]')
      .addEventListener('click', () => this.siteRoots.open());

    this.siteSelect.addEventListener('change', () => {
      store.update('devices.site', { siteModel: this.siteSelect.value, deviceId: '' });
      this.loadCompliance();
    });
  }

  destroy() {}

  async init() {
    try {
      const {
        site_models: models,
        unavailable_roots: unavailable,
        invalid_models: invalid,
      } = await api.listSiteModels();
      this.siteModels = models;
      this._fillSiteSelect(models);

      const { siteModel } = store.getState();
      if (siteModel && models.some((m) => m.path === siteModel)) {
        this.siteSelect.value = siteModel;
      } else {
        this.siteSelect.value = '';
      }
      await this.loadCompliance();

      // A registered path that has gone away, or a site model whose config
      // cannot be parsed, is reported, never quietly dropped. setNotice()
      // replaces its target, so every problem goes into one notice.
      const problems = [
        ...(unavailable || []).map(
          (root) => `Registered path ${root.path} is unavailable: ${root.reason}`
        ),
        ...(invalid || []).map(
          (model) => `Site model ${model.path} was skipped: ${model.error}`
        ),
      ];
      if (problems.length) this.setNotice(problems.join(' — '), true);
    } catch (cause) {
      this.setNotice(cause.message, true);
    }
  }

  /** Groups models under the origin they were discovered in. */
  _fillSiteSelect(models) {
    this.siteSelect.textContent = '';
    const blank = document.createElement('option');
    blank.value = '';
    blank.textContent = 'Select a site model…';
    this.siteSelect.appendChild(blank);

    const groups = new Map();
    for (const model of models) {
      const key = model.source === 'repository' ? 'This repository' : model.source;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(model);
    }

    for (const [heading, entries] of groups) {
      const group = document.createElement('optgroup');
      group.label = heading;
      for (const model of entries) {
        const option = document.createElement('option');
        option.value = model.path;
        option.textContent = `${model.name} (${model.device_count})`;
        group.appendChild(option);
      }
      this.siteSelect.appendChild(group);
    }
  }

  async loadCompliance() {
    const siteModel = this.siteSelect.value;
    this.deviceListEl.textContent = '';
    this.totalsEl.textContent = '';
    this.siteCommitEl.textContent = '';

    this._renderSiteFacts(siteModel);

    if (!siteModel) {
      this.countEl.textContent = '';
      this.setNotice('Choose a site model above to see its sequencer compliance.');
      return;
    }

    this.setNotice('Reading recorded sequencer results…');

    try {
      const payload = await api.siteCompliance(siteModel);
      requirePayloadFields(
        payload,
        ['site_model', 'devices', 'totals', 'stages', 'stages_for_pass'],
        'Compliance response'
      );

      this.devices = payload.devices;
      this.stagesForPass = payload.stages_for_pass;
      this.countEl.textContent =
        `${payload.totals.devices_with_results} of ${payload.totals.devices} with results`;
      this._renderTotals(payload.totals);
      this._renderSiteCommit(siteModel);

      this.deviceListEl.textContent = '';
      if (payload.devices.length === 0) {
        this.setNotice('This site model contains no devices.');
        return;
      }
      for (const device of payload.devices) {
        this.deviceListEl.appendChild(this.deviceRow(siteModel, device));
      }
    } catch (cause) {
      this.setNotice(cause.message, true);
    }
  }

  /**
   * Site facts, restricted to what is genuinely true of the site model.
   *
   * The registry, provider and project that `cloud_iot_config.json` declares
   * describe where the site model currently points, which is not necessarily
   * where any recorded run went. Printing them here read as "this whole site
   * was tested against this target", an assertion nothing on disk supports.
   * The target a run actually used is shown on each device instead.
   */
  _renderSiteFacts(siteModel) {
    const model = this.siteModels.find((m) => m.path === siteModel);
    if (!model) {
      this.factsEl.textContent = '';
      return;
    }
    const facts = [model.site_name, `${model.device_count} devices`].filter(Boolean);
    this.factsEl.textContent = facts.join(' \u00b7 ');
  }

  _renderSiteCommit(siteModel) {
    this.siteCommitEl.textContent = '';

    const commit = document.createElement('button');
    commit.type = 'button';
    commit.className = 'btn btn-primary';
    commit.dataset.act = 'commit-site';
    commit.textContent = 'Commit results…';
    commit.setAttribute('aria-label', `Commit sequencer results for ${siteModel}`);
    commit.addEventListener('click', () => this.openCommit(siteModel));

    const hint = document.createElement('p');
    hint.className = 'field-hint';
    hint.textContent =
      'Commits every change a sequencer run made to this site model. ' +
      'The dialog lists which devices are affected before anything is written.';

    this.siteCommitEl.append(commit, hint);
  }

  _renderTotals(totals) {
    requirePayloadFields(
      totals,
      ['devices', 'devices_with_results', 'devices_passing', 'devices_failing',
       'devices_not_evaluated', ...COUNT_FIELDS],
      'Compliance totals'
    );

    const remainder = this._remainderCount(totals);
    const rows = [
      ['Devices', `${totals.devices_with_results} of ${totals.devices} with results`],
      ['Passing', String(totals.devices_passing)],
      ['Failing', String(totals.devices_failing)],
      ['Not assessed', String(totals.devices_not_evaluated)],
      ['Sequences recorded', String(totals.total)],
      ['Pass', String(totals.pass)],
      ['Fail', String(totals.fail)],
      ['Skip', String(totals.skip)],
      ['Errored / not run', String(remainder)],
    ];

    this.totalsEl.textContent = '';
    for (const [term, value] of rows) {
      const wrapper = document.createElement('div');
      const dt = document.createElement('dt');
      dt.textContent = term;
      const dd = document.createElement('dd');
      dd.textContent = value;
      wrapper.append(dt, dd);
      this.totalsEl.appendChild(wrapper);
    }
  }

  /** Sequences carrying a verdict outside pass/fail/skip. Never negative. */
  _remainderCount(counts) {
    return counts.total - counts.pass - counts.fail - counts.skip;
  }

  deviceRow(siteModel, device) {
    requirePayloadFields(device, DEVICE_FIELDS, 'Compliance device record');
    requirePayloadFields(device.counts, COUNT_FIELDS, `Counts for device ${device.device_id}`);

    const row = document.createElement('article');
    row.className = device.has_results ? 'compliance-row' : 'compliance-row is-untested';
    row.dataset.device = device.device_id;
    row.dataset.verdict = device.verdict;

    const head = document.createElement('header');
    head.className = 'compliance-head';

    const name = document.createElement('h3');
    name.className = 'device-name';
    name.textContent = device.device_id;

    const tags = document.createElement('span');
    tags.className = 'device-tags';
    if (device.has_results) {
      tags.appendChild(this.verdictTag(device.verdict));
    } else {
      tags.appendChild(this.tag('not tested', 'warning'));
    }
    if (device.udmi_version) tags.appendChild(this.tag(device.udmi_version, 'neutral'));

    head.append(name, tags);
    row.appendChild(head);

    row.appendChild(this._targets(device));

    if (device.has_results) {
      row.append(...this._resultsBody(device));
    } else {
      // The server names the missing artifact; that sentence is the whole
      // answer to "why is this device blank?", so it is shown as written.
      const reason = document.createElement('p');
      reason.className = 'compliance-reason';
      reason.textContent = device.reason;
      row.appendChild(reason);
    }

    row.appendChild(this._actions(siteModel, device));
    return row;
  }

  /**
   * The target(s) this device's recorded runs actually used.
   *
   * Read from the message envelopes the run captured, not from the site model's
   * configuration. Two entries here is not a defect: it means different
   * sequences were run against different projects, and an operator reading the
   * scores needs to know that before quoting them.
   */
  _targets(device) {
    const el = document.createElement('p');
    el.className = 'compliance-targets';

    if (device.targets.length === 0) {
      el.classList.add('is-unknown');
      el.textContent = 'Target not recorded — no captured messages to read it from.';
      return el;
    }

    if (device.targets.length > 1) el.classList.add('is-mixed');

    const label = document.createElement('span');
    label.className = 'targets-label';
    label.textContent = device.targets.length > 1 ? 'Targets (mixed)' : 'Target';
    el.appendChild(label);

    for (const target of device.targets) {
      const chip = document.createElement('span');
      chip.className = 'target-chip';
      chip.textContent = [target.project_id, target.registry_id].filter(Boolean).join(' / ');
      chip.title =
        `${target.sequences.length} sequence(s) ran against this target: ` +
        target.sequences.join(', ');
      el.appendChild(chip);
    }
    return el;
  }

  /** The score matrix, verdict counts, and run facts for a tested device. */
  _resultsBody(device) {
    const parts = [this._matrix(device)];

    const unscored = this._unscored(device);
    if (unscored) parts.push(unscored);

    parts.push(this._counts(device));

    const drift = this._provenanceWarning(device);
    if (drift) parts.push(drift);

    const facts = document.createElement('p');
    facts.className = 'compliance-facts';
    facts.textContent = [
      device.last_run ? `Last run ${device.last_run}` : 'Run time not recorded',
      device.status_message,
    ]
      .filter(Boolean)
      .join(' \u00b7 ');
    parts.push(facts);

    return parts;
  }

  /**
   * The feature-bucket score matrix: one row per bucket, one column per stage
   * the device actually exercised.
   *
   * The stage columns come from the device's own `stages`, so a device that
   * only ran preview tests gets a Preview column and no empty Stable one. The
   * verdict glyph and the score cells deliberately disagree in one case worth
   * understanding: the glyph ignores alpha and preview entirely, so a bucket
   * can show `10/10` under Preview and still be marked not assessed.
   */
  _matrix(device) {
    const wrapper = document.createElement('div');
    wrapper.className = 'matrix-wrapper';

    const buckets = Object.keys(device.features).sort();
    if (buckets.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'compliance-reason';
      empty.textContent =
        'The sequencer results record no feature buckets, so there is nothing to score.';
      wrapper.appendChild(empty);
      return wrapper;
    }

    const table = document.createElement('table');
    table.className = 'compliance-matrix';

    const caption = document.createElement('caption');
    caption.className = 'visually-hidden';
    caption.textContent = `Feature bucket scores for ${device.device_id}`;
    table.appendChild(caption);

    const thead = document.createElement('thead');
    const headRow = document.createElement('tr');
    for (const heading of ['', 'Feature', ...device.stages.map(this._stageLabel)]) {
      const th = document.createElement('th');
      th.scope = 'col';
      th.textContent = heading;
      if (heading && !['', 'Feature'].includes(heading)) {
        th.className = this.stagesForPass.includes(heading.toLowerCase())
          ? 'stage-col is-releasable'
          : 'stage-col';
        th.title = this.stagesForPass.includes(heading.toLowerCase())
          ? `${heading} counts towards the verdict.`
          : `${heading} is informational; it does not count towards the verdict.`;
      }
      headRow.appendChild(th);
    }
    thead.appendChild(headRow);
    table.appendChild(thead);

    const tbody = document.createElement('tbody');
    for (const bucket of buckets) {
      const cells = device.features[bucket];
      const tr = document.createElement('tr');
      tr.dataset.bucket = bucket;

      const verdict = document.createElement('td');
      const key = String(cells.overall);
      verdict.className = `matrix-verdict verdict-${VERDICT_TONE[key]}`;
      verdict.textContent = VERDICT_GLYPH[key];
      verdict.title = VERDICT_LABEL[key];
      tr.appendChild(verdict);

      const name = document.createElement('th');
      name.scope = 'row';
      name.className = 'matrix-bucket';
      name.textContent = bucket;
      tr.appendChild(name);

      for (const stage of device.stages) {
        const cell = cells.stages[stage];
        const td = document.createElement('td');
        td.className = 'matrix-score';
        td.textContent = `${cell.scored}/${cell.total}`;
        if (cell.total === 0) {
          // 0/0 is "not exercised". Styling it like a zero score would make an
          // untested bucket look like a failed one.
          td.classList.add('is-na');
          td.title = 'Not exercised at this stage.';
        } else if (cell.scored === cell.total) {
          td.classList.add('is-full');
          td.title = `Full marks across ${cell.sequences} sequence(s).`;
        } else {
          td.classList.add('is-partial');
          td.title = `${cell.scored} of ${cell.total} across ${cell.sequences} sequence(s).`;
        }
        tr.appendChild(td);
      }
      tbody.appendChild(tr);
    }
    table.appendChild(tbody);
    wrapper.appendChild(table);

    if (device.stages.length === 0) {
      const note = document.createElement('p');
      note.className = 'matrix-note';
      note.textContent =
        'No stage was exercised: every recorded sequence was skipped, so nothing is scored.';
      wrapper.appendChild(note);
    }

    return wrapper;
  }

  _stageLabel(stage) {
    return stage.charAt(0).toUpperCase() + stage.slice(1);
  }

  /** Sequences the sequencer could not place on the matrix, and why. */
  _unscored(device) {
    if (device.unscored.length === 0) return null;

    const details = document.createElement('details');
    details.className = 'compliance-unscored';

    const summary = document.createElement('summary');
    summary.textContent =
      `${device.unscored.length} sequence(s) could not be scored`;
    details.appendChild(summary);

    const list = document.createElement('ul');
    for (const entry of device.unscored) {
      const item = document.createElement('li');
      item.textContent = `${entry.feature} / ${entry.name}: ${entry.reason}`;
      list.appendChild(item);
    }
    details.appendChild(list);
    return details;
  }

  /** Per-verdict sequence counts. A count, deliberately never a percentage. */
  _counts(device) {
    const counts = device.counts;
    const remainder = this._remainderCount(counts);

    const metrics = document.createElement('dl');
    metrics.className = 'verdict-counts';
    const entries = [
      ['Pass', String(counts.pass), 'pass'],
      ['Fail', String(counts.fail), 'fail'],
      ['Skip', String(counts.skip), 'skip'],
      ['Errored / not run', String(remainder), 'other'],
      ['Sequences', String(counts.total), 'total'],
    ];
    for (const [term, value, tone] of entries) {
      const wrapper = document.createElement('div');
      wrapper.className = `verdict-count verdict-${tone}`;
      const dt = document.createElement('dt');
      dt.textContent = term;
      const dd = document.createElement('dd');
      dd.textContent = value;
      wrapper.append(dt, dd);
      metrics.appendChild(wrapper);
    }
    return metrics;
  }

  /**
   * Warns when `results.md` was generated from a different run than the scores.
   *
   * `results.md` is only rewritten by `bin/sequencer_report`, while the json is
   * rewritten by every run, so the two drift. Without this an operator can
   * download a report that contradicts the matrix immediately above it and have
   * no way to tell which is current.
   */
  _provenanceWarning(device) {
    if (device.provenance.results_md_matches_run !== false) return null;

    const warning = document.createElement('p');
    warning.className = 'compliance-drift';
    warning.setAttribute('role', 'note');
    warning.textContent =
      `The results.md on disk describes an earlier run (started ` +
      `${device.provenance.results_md_start}), not the scores above (started ` +
      `${device.provenance.run_start}). Re-run bin/sequencer_report for this ` +
      `device to regenerate it.`;
    return warning;
  }

  /**
   * Download links for the reports that exist.
   *
   * Each link is a plain anchor: the server sends the report as an attachment,
   * so the browser's own download machinery names the file and streams it
   * without the report ever being held in memory by the page.
   *
   * Committing is deliberately absent here. Results are committed for the whole
   * site model at once, because one sequencer run dirties per-device results,
   * per-device generated config, and site-level summaries together; committing
   * one device's slice would leave the site model in a state no run produced.
   */
  _actions(siteModel, device) {
    const actions = document.createElement('div');
    actions.className = 'compliance-actions';

    if (device.reports.length === 0) {
      const none = document.createElement('span');
      none.className = 'compliance-no-reports';
      none.textContent = 'No report files on disk';
      actions.appendChild(none);
    }

    for (const kind of device.reports) {
      const label = REPORT_LABELS[kind];
      if (!label) {
        throw new Error(
          `Device ${device.device_id} reports an unknown report kind '${kind}'. ` +
            `Known kinds: ${Object.keys(REPORT_LABELS).join(', ')}.`
        );
      }
      const link = document.createElement('a');
      link.className = 'btn btn-ghost report-link';
      link.href = api.deviceReportUrl(siteModel, device.device_id, kind);
      link.setAttribute('download', '');
      link.textContent = label;
      link.setAttribute('aria-label', `Download ${label} for ${device.device_id}`);
      actions.appendChild(link);
    }

    return actions;
  }

  async openCommit(siteModel) {
    try {
      const result = await this.commitDialog.open({ siteModel });
      // Only a real commit changes what the compliance read would return.
      if (result) await this.loadCompliance();
    } catch (cause) {
      this.setNotice(cause.message, true);
    }
  }

  verdictTag(verdict) {
    const tones = { pass: 'success', fail: 'error', not_evaluated: 'neutral' };
    const labels = {
      pass: 'compliant',
      fail: 'failing',
      not_evaluated: 'not assessed',
    };
    return this.tag(labels[verdict] ?? verdict, tones[verdict] ?? 'neutral');
  }

  tag(text, tone) {
    const el = document.createElement('span');
    el.className = `badge badge-${tone}`;
    el.textContent = text;
    return el;
  }

  note(text, isError = false) {
    const el = document.createElement('p');
    el.className = `empty-note ${isError ? 'is-error' : ''}`;
    el.textContent = text;
    return el;
  }

  setNotice(text, isError = false) {
    this.deviceListEl.textContent = '';
    this.deviceListEl.appendChild(this.note(text, isError));
  }
}
