/**
 * Layer 2 — Workbench Logs drawer.
 *
 * Joins the browser ring buffer and the server ring buffer into one table keyed
 * by correlationId, so a single user action can be traced across the UI, the
 * gateway, and the subprocess layer. This is the observability surface required
 * by section 7 of workbench/GEMINI.md.
 *
 * It was a routed tab. Reading the logs meant leaving the screen that produced
 * them, so it is now a left-edge drawer over whichever view is mounted; Mantis
 * holds the right edge.
 */

import { api } from '../core/api.js';
import { logger } from '../core/logger.js';

const LEVELS = ['ERROR', 'WARN', 'INFO'];

export class LogsDrawer {
  constructor(mountEl, { onVisibilityChange } = {}) {
    if (!mountEl) {
      throw new Error(
        'LogsDrawer requires a mount element: [data-role="logs-mount"] is missing from index.html.'
      );
    }
    if (typeof onVisibilityChange !== 'function') {
      throw new Error('LogsDrawer requires an onVisibilityChange(open) callback.');
    }
    this.mountEl = mountEl;
    this.onVisibilityChange = onVisibilityChange;
    this.entries = [];
    this.serverError = null;
    this.isOpen = false;
    this.rootEl = null;
  }

  /** Builds the drawer and binds its filters. Safe to call more than once. */
  mount() {
    if (this.rootEl) return;

    this.mountEl.innerHTML = `
      <aside class="logs-drawer" data-role="drawer" aria-label="Workbench Logs" aria-hidden="true" inert>
        <div class="logs-drawer-header">
          <span class="material-symbols-outlined" aria-hidden="true">terminal</span>
          <h2 class="logs-drawer-title">Workbench Logs</h2>
          <span class="count" data-role="count"></span>
          <button type="button" class="btn-icon" data-act="close" aria-label="Close Workbench Logs">
            <span class="material-symbols-outlined" aria-hidden="true">close</span>
          </button>
        </div>
        <div class="logs-drawer-toolbar">
          <input type="search" data-role="search" placeholder="Filter by module, event, or correlation id"
                 aria-label="Filter log entries" />
          <select data-role="level" aria-label="Minimum level">
            <option value="">All levels</option>
            ${LEVELS.map((level) => `<option value="${level}">${level} and above</option>`).join('')}
          </select>
          <select data-role="source" aria-label="Source">
            <option value="">Both sources</option>
            <option value="browser">Browser</option>
            <option value="server">Server</option>
          </select>
          <button type="button" class="btn btn-ghost" data-act="refresh">Refresh</button>
        </div>
        <p class="logs-drawer-alert" data-role="alert" hidden></p>
        <div class="logs-drawer-body">
          <table class="log-table">
            <thead>
              <tr>
                <th>Time</th><th>Level</th><th>Source</th><th>Module</th>
                <th>Event</th><th>Correlation</th><th>Duration</th><th>Detail</th>
              </tr>
            </thead>
            <tbody data-role="rows"></tbody>
          </table>
        </div>
      </aside>
    `;

    this.rootEl = this.mountEl.querySelector('[data-role="drawer"]');
    this.rowsEl = this.mountEl.querySelector('[data-role="rows"]');
    this.countEl = this.mountEl.querySelector('[data-role="count"]');
    this.alertEl = this.mountEl.querySelector('[data-role="alert"]');
    this.searchEl = this.mountEl.querySelector('[data-role="search"]');
    this.levelEl = this.mountEl.querySelector('[data-role="level"]');
    this.sourceEl = this.mountEl.querySelector('[data-role="source"]');

    for (const el of [this.searchEl, this.levelEl, this.sourceEl]) {
      el.addEventListener('input', () => this.paint());
    }
    this.mountEl.querySelector('[data-act="refresh"]').addEventListener('click', () => this.refresh());
    this.mountEl.querySelector('[data-act="close"]').addEventListener('click', () => this.close());
  }

  open() {
    if (this.isOpen) return;
    this.isOpen = true;
    this.rootEl.classList.add('is-open');
    this.rootEl.removeAttribute('inert');
    this.rootEl.setAttribute('aria-hidden', 'false');
    // The buffers move constantly; a drawer opened onto a stale snapshot would
    // be read as the current state of the system.
    this.refresh();
    this.onVisibilityChange(true);
  }

  close() {
    if (!this.isOpen) return;
    this.isOpen = false;
    if (this.rootEl.contains(document.activeElement)) {
      document.activeElement.blur();
    }
    this.rootEl.classList.remove('is-open');
    this.rootEl.setAttribute('inert', '');
    this.rootEl.setAttribute('aria-hidden', 'true');
    this.onVisibilityChange(false);
  }

  toggle() {
    if (this.isOpen) {
      this.close();
    } else {
      this.open();
    }
  }

  async refresh() {
    const browser = logger.getEntries(1000).map((entry) => ({ ...entry, source: 'browser' }));
    let server = [];
    this.serverError = null;
    try {
      const payload = await api.diagnosticsLogs(1000);
      server = payload.entries.map((entry) => ({ ...entry, source: 'server' }));
    } catch (cause) {
      // Half a table is not a table. The gap is named rather than hidden behind
      // a browser-only view that looks complete.
      this.serverError = cause.message;
      logger.error('LogsDrawer', 'server_logs.unavailable', {
        error: { code: 'FETCH', message: cause.message },
      });
    }

    this.entries = [...browser, ...server].sort((a, b) =>
      String(b.timestamp).localeCompare(String(a.timestamp))
    );
    this.paint();
  }

  paint() {
    if (this.serverError) {
      this.alertEl.hidden = false;
      this.alertEl.textContent =
        `Server log buffer unavailable (${this.serverError}). Only browser entries are listed below.`;
    } else {
      this.alertEl.hidden = true;
      this.alertEl.textContent = '';
    }

    const needle = this.searchEl.value.trim().toLowerCase();
    const minLevel = this.levelEl.value;
    const source = this.sourceEl.value;
    const allowed = minLevel ? LEVELS.slice(0, LEVELS.indexOf(minLevel) + 1) : null;

    const visible = this.entries.filter((entry) => {
      if (source && entry.source !== source) return false;
      if (allowed && !allowed.includes(entry.level)) return false;
      if (!needle) return true;
      return [entry.module, entry.event, entry.correlationId, entry.layer]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(needle));
    });

    this.countEl.textContent = `${visible.length} of ${this.entries.length}`;
    this.rowsEl.textContent = '';

    if (visible.length === 0) {
      const row = this.rowsEl.insertRow();
      const cell = row.insertCell();
      cell.colSpan = 8;
      cell.className = 'empty-note';
      cell.textContent = 'No log entries match the current filters.';
      return;
    }

    const fragment = document.createDocumentFragment();
    for (const entry of visible) fragment.appendChild(this.row(entry));
    this.rowsEl.appendChild(fragment);
  }

  row(entry) {
    const tr = document.createElement('tr');
    tr.className = `log-row log-${entry.level.toLowerCase()}`;

    const detail = entry.error
      ? `${entry.error.code}: ${entry.error.message}`
      : this.summarise(entry.details) || this.summarise(entry.context) || '';

    const cells = [
      String(entry.timestamp).slice(11, 23),
      entry.level,
      entry.source,
      entry.module,
      entry.event,
      entry.correlationId || '',
      entry.durationMs !== undefined ? `${entry.durationMs} ms` : '',
      detail,
    ];

    for (const value of cells) {
      const td = document.createElement('td');
      td.textContent = value;
      tr.appendChild(td);
    }
    return tr;
  }

  summarise(record) {
    if (!record || typeof record !== 'object') return '';
    return Object.entries(record)
      .map(([key, value]) => `${key}=${Array.isArray(value) ? value.join(',') : value}`)
      .join(' ');
  }
}
