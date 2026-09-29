/**
 * Layer 1 — Run status badge, metrics, and command echo.
 *
 * Preserves v1's two strongest signals: a badge that distinguishes freshly
 * produced results from results read off disk, and a verbatim echo of the
 * command that was executed so the run is reproducible from a terminal.
 */

import { SupportBundleButton } from './support-bundle-button.js';

const BADGE_TONE = {
  Idle: 'neutral',
  'Historical runs': 'info',
  Running: 'info',
  Compliant: 'success',
  Failed: 'error',
  Error: 'error',
  Blocked: 'error',
  Incomplete: 'warning',
  Aborted: 'warning',
};

export class RunSummary {
  constructor(container) {
    this.container = container;
    this.container.innerHTML = `
      <div class="summary-head">
        <span class="badge" data-role="badge">Idle</span>
        <span class="summary-progress" data-role="progress"></span>
      </div>
      <div class="metrics">
        <div class="metric metric-pass"><span data-role="pass">0</span><label>Passed</label></div>
        <div class="metric metric-fail"><span data-role="fail">0</span><label>Failed</label></div>
        <div class="metric metric-skip"><span data-role="skip">0</span><label>Skipped</label></div>
        <div class="metric metric-pending"><span data-role="pending">0</span><label>Pending</label></div>
        <div class="metric metric-time"><span data-role="time">00:00</span><label>Elapsed</label></div>
      </div>
      <div class="progress-track"><div class="progress-fill" data-role="bar"></div></div>
      <pre class="command-echo" data-role="command" hidden></pre>
      <div class="summary-actions" data-role="support-bundle"></div>
    `;

    this.badge = this.container.querySelector('[data-role="badge"]');
    this.progress = this.container.querySelector('[data-role="progress"]');
    this.bar = this.container.querySelector('[data-role="bar"]');
    this.command = this.container.querySelector('[data-role="command"]');
    this.timeEl = this.container.querySelector('[data-role="time"]');
    this.counters = {
      pass: this.container.querySelector('[data-role="pass"]'),
      fail: this.container.querySelector('[data-role="fail"]'),
      skip: this.container.querySelector('[data-role="skip"]'),
      pending: this.container.querySelector('[data-role="pending"]'),
    };

    // The selected site model arrives with every update(); the bundle is
    // always exported for the site model currently shown in the summary.
    this.siteModel = '';
    this.supportBundle = new SupportBundleButton(
      this.container.querySelector('[data-role="support-bundle"]'),
      { getSiteModel: () => this.siteModel }
    );
  }

  setTime(timeStr) {
    if (this.timeEl) this.timeEl.textContent = timeStr;
  }

  update(state, metrics) {
    this.siteModel = state.siteModel || '';
    this.badge.textContent = state.statusLabel;
    this.badge.className = `badge badge-${BADGE_TONE[state.statusLabel] || 'neutral'}`;

    this.counters.pass.textContent = metrics.pass;
    this.counters.fail.textContent = metrics.fail;
    this.counters.skip.textContent = metrics.skip;
    this.counters.pending.textContent = metrics.pending;

    if (this.timeEl) {
      if (state.running && state.startedAt) {
        const sec = Math.floor((Date.now() - state.startedAt) / 1000);
        const mm = String(Math.floor(sec / 60)).padStart(2, '0');
        const ss = String(sec % 60).padStart(2, '0');
        this.timeEl.textContent = `${mm}:${ss}`;
      } else if (state.elapsedDuration) {
        this.timeEl.textContent = state.elapsedDuration;
      } else {
        this.timeEl.textContent = '00:00';
      }
    }

    if (!metrics.hasLiveRun && metrics.historical?.total > 0) {
      this.progress.textContent = `Ready · Last disk run: ${metrics.historical.fail} failed, ${metrics.historical.pass} passed`;
    } else {
      this.progress.textContent = metrics.total
        ? `${metrics.total - metrics.pending}/${metrics.total} settled`
        : 'No sequences selected';
    }
    this.bar.style.width = `${metrics.percent}%`;

    this.command.hidden = !state.commandLine;
    this.command.textContent = state.commandLine;
  }
}
