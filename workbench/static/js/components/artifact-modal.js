/**
 * Layer 1 — Accessible artifact viewer modal.
 *
 * v1's modal had no role, no ESC handler, no focus trap, and dumped sequence.md
 * into a raw <pre>. This one is a proper dialog: focus is trapped while open,
 * ESC and backdrop dismiss it, and focus returns to the invoking element.
 *
 * Stays presentational — it never fetches. The view supplies a `loadFile`
 * callback so all network access remains in the API layer.
 *
 * The one exception is the support bundle action: SupportBundleButton calls
 * the API layer itself and exports the site model currently selected in the
 * store (the site model whose results this modal is showing).
 */

import { JSONViewer } from './json-viewer.js';
import { SupportBundleButton } from './support-bundle-button.js';
import { store } from '../core/store.js';

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])';

export class ArtifactModal {
  constructor(container, { loadFile, onDiagnose = null } = {}) {
    this.container = container;
    this.loadFile = loadFile;
    this.onDiagnose = onDiagnose;
    this.artifacts = [];
    this.activeName = null;
    this.currentTestName = null;
    this.previousFocus = null;
    this.cache = new Map();
    this._build();
  }

  _build() {
    this.container.className = 'modal-backdrop';
    this.container.hidden = true;
    this.container.innerHTML = `
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="artifact-modal-title">
        <header class="modal-header">
          <div>
            <h2 class="modal-title" id="artifact-modal-title"></h2>
            <p class="modal-subtitle"></p>
          </div>
          <div class="modal-header-actions">
            <button type="button" class="btn btn-diagnose" data-act="diagnose">🔍 Diagnose with Mantis</button>
            <div data-role="support-bundle"></div>
            <button type="button" class="btn btn-ghost" data-act="close" aria-label="Close artifact viewer">Close</button>
          </div>
        </header>
        <div class="modal-tabs" role="tablist"></div>
        <div class="modal-body" role="tabpanel" tabindex="0"></div>
      </div>
    `;

    this.dialog = this.container.querySelector('.modal');
    this.titleEl = this.container.querySelector('.modal-title');
    this.subtitleEl = this.container.querySelector('.modal-subtitle');
    this.tabsEl = this.container.querySelector('.modal-tabs');
    this.bodyEl = this.container.querySelector('.modal-body');
    this.diagnoseBtn = this.container.querySelector('[data-act="diagnose"]');
    this.supportBundle = new SupportBundleButton(
      this.container.querySelector('[data-role="support-bundle"]'),
      { getSiteModel: () => store.getState().siteModel }
    );

    this.diagnoseBtn.addEventListener('click', () => {
      const name = this.currentTestName;
      this.close();
      if (name) this.onDiagnose?.(name);
    });

    this.container.querySelector('[data-act="close"]').addEventListener('click', () => this.close());
    this.container.addEventListener('mousedown', (event) => {
      if (event.target === this.container) this.close();
    });
    this.container.addEventListener('keydown', (event) => this._onKeydown(event));
  }

  _onKeydown(event) {
    if (event.key === 'Escape') {
      event.stopPropagation();
      this.close();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusable = [...this.dialog.querySelectorAll(FOCUSABLE)].filter((el) => !el.hidden);
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  /**
   * Opens the viewer for one test's recorded artifacts.
   * @param {{testName: string, artifactDir: string, artifacts: string[], summary?: string}} detail
   */
  open(detail) {
    this.artifacts = detail.artifacts || [];
    this.currentTestName = detail.testName;
    this.cache.clear();
    this.previousFocus = document.activeElement;

    this.titleEl.textContent = detail.testName;
    this.subtitleEl.textContent = detail.summary
      ? `${detail.artifactDir} — ${detail.summary}`
      : detail.artifactDir;
    this.artifactDir = detail.artifactDir;

    this._renderTabs();
    this.container.hidden = false;

    if (this.artifacts.length > 0) {
      this._select(this._preferredArtifact());
    } else {
      this._setNotice('No artifacts were recorded on disk for this test.');
    }
    this.container.querySelector('[data-act="close"]').focus();
  }

  close() {
    if (this.container.hidden) return;
    this.container.hidden = true;
    this.bodyEl.textContent = '';
    this.previousFocus?.focus?.();
  }

  /** Prefers the human-readable report, then the log, then whatever exists. */
  _preferredArtifact() {
    return (
      this.artifacts.find((name) => name === 'sequence.md') ||
      this.artifacts.find((name) => name === 'sequence.log') ||
      this.artifacts[0]
    );
  }

  _renderTabs() {
    this.tabsEl.textContent = '';
    for (const name of this.artifacts) {
      const tab = document.createElement('button');
      tab.type = 'button';
      tab.className = 'modal-tab';
      tab.role = 'tab';
      tab.dataset.artifact = name;
      tab.textContent = name;
      tab.addEventListener('click', () => this._select(name));
      this.tabsEl.appendChild(tab);
    }
  }

  async _select(name) {
    this.activeName = name;
    for (const tab of this.tabsEl.querySelectorAll('.modal-tab')) {
      const active = tab.dataset.artifact === name;
      tab.classList.toggle('is-active', active);
      tab.setAttribute('aria-selected', String(active));
    }

    if (this.cache.has(name)) {
      this._paint(name, this.cache.get(name));
      return;
    }

    this._setNotice(`Loading ${name}…`);
    try {
      const payload = await this.loadFile(`${this.artifactDir}/${name}`);
      this.cache.set(name, payload.content);
      if (this.activeName === name) this._paint(name, payload.content);
    } catch (cause) {
      if (this.activeName === name) this._setNotice(cause.message, 'error');
    }
  }

  _paint(name, content) {
    this.bodyEl.textContent = '';

    if (name.endsWith('.json') || name.endsWith('.attr')) {
      try {
        const mount = document.createElement('div');
        this.bodyEl.appendChild(mount);
        new JSONViewer(mount).render(JSON.parse(content));
        return;
      } catch {
        // Malformed JSON is shown verbatim rather than hidden behind an error.
      }
    }

    const pre = document.createElement('pre');
    pre.className = 'artifact-text';
    pre.textContent = content;
    this.bodyEl.appendChild(pre);
  }

  _setNotice(message, level = 'info') {
    this.bodyEl.textContent = '';
    const note = document.createElement('p');
    note.className = `empty-note ${level === 'error' ? 'is-error' : ''}`;
    note.textContent = message;
    this.bodyEl.appendChild(note);
  }
}
