/**
 * Layer 1 — Site model path consent dialog.
 *
 * Site models are routinely stored outside the UDMI checkout, and under WSL
 * they are often on a different filesystem entirely. Rather than letting the
 * server roam the filesystem the way v1 did, the operator names the
 * directories the Workbench may look in. This dialog is where that consent is
 * given and withdrawn.
 *
 * Presentational only: it never calls fetch(). The view supplies loadRoots,
 * addRoot, and removeRoot so all network access stays in the API layer.
 */

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])';

export class SiteRootsDialog {
  constructor(container, { loadRoots, addRoot, removeRoot, onChange }) {
    this.container = container;
    this.loadRoots = loadRoots;
    this.addRoot = addRoot;
    this.removeRoot = removeRoot;
    this.onChange = onChange;
    this.previousFocus = null;
    this._build();
  }

  _build() {
    this.container.className = 'modal-backdrop';
    this.container.hidden = true;
    this.container.innerHTML = `
      <div class="modal modal-narrow" role="dialog" aria-modal="true"
           aria-labelledby="site-roots-title" aria-describedby="site-roots-consent">
        <header class="modal-header">
          <div>
            <h2 class="modal-title" id="site-roots-title">Site model paths</h2>
            <p class="modal-subtitle" data-role="config-path"></p>
          </div>
          <button type="button" class="btn btn-ghost" data-act="close"
                  aria-label="Close site model paths">Close</button>
        </header>
        <div class="modal-body">
          <p class="consent-note" id="site-roots-consent">
            The Workbench reads site models from <code>sites/</code> in this repository.
            To use models stored anywhere else, name the directory below. Nothing outside
            this repository is read, listed, or scanned until you add it here, and removing
            an entry withdraws that access immediately.
          </p>

          <ul class="site-root-list" data-role="list"></ul>

          <form class="site-root-add" data-role="add-form">
            <label class="visually-hidden" for="site-root-input">Directory to add</label>
            <input id="site-root-input" data-role="input" type="text" autocomplete="off"
                   spellcheck="false"
                   placeholder="Absolute path to a site model, or a folder of them" />
            <button type="submit" class="btn btn-primary" data-act="add">Add path</button>
          </form>
          <p class="field-hint">
            Point at a single site model (a directory containing
            <code>cloud_iot_config.json</code>) or at a folder holding several of them.
            Subdirectories one level deep are scanned; nothing deeper is touched.
          </p>
          <p class="site-root-message" data-role="message" hidden></p>
        </div>
      </div>
    `;

    this.dialog = this.container.querySelector('.modal');
    this.configPathEl = this.container.querySelector('[data-role="config-path"]');
    this.listEl = this.container.querySelector('[data-role="list"]');
    this.formEl = this.container.querySelector('[data-role="add-form"]');
    this.inputEl = this.container.querySelector('[data-role="input"]');
    this.addBtn = this.container.querySelector('[data-act="add"]');
    this.messageEl = this.container.querySelector('[data-role="message"]');

    this.container.querySelector('[data-act="close"]').addEventListener('click', () => this.close());
    this.container.addEventListener('mousedown', (event) => {
      if (event.target === this.container) this.close();
    });
    this.container.addEventListener('keydown', (event) => this._onKeydown(event));
    this.formEl.addEventListener('submit', (event) => {
      event.preventDefault();
      this._submit();
    });
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

  async open() {
    this.previousFocus = document.activeElement;
    this.container.hidden = false;
    this._setMessage(null);
    await this.refresh();
    this.inputEl.focus();
  }

  close() {
    this.container.hidden = true;
    if (this.previousFocus instanceof HTMLElement) this.previousFocus.focus();
  }

  async refresh() {
    try {
      const { site_roots: roots, config_path: configPath } = await this.loadRoots();
      this.configPathEl.textContent = `Saved in ${configPath}`;
      this._renderList(roots);
    } catch (cause) {
      this._setMessage(cause.message, true);
    }
  }

  _renderList(roots) {
    this.listEl.textContent = '';

    if (roots.length === 0) {
      const empty = document.createElement('li');
      empty.className = 'empty-note';
      empty.textContent = 'No additional paths. Only this repository is being read.';
      this.listEl.appendChild(empty);
      return;
    }

    for (const root of roots) {
      this.listEl.appendChild(this._row(root));
    }
  }

  _row(root) {
    const item = document.createElement('li');
    item.className = `site-root-row ${root.available ? '' : 'is-unavailable'}`;

    const text = document.createElement('div');
    const path = document.createElement('code');
    path.className = 'site-root-path';
    path.textContent = root.path;

    const detail = document.createElement('span');
    detail.className = 'site-root-detail';
    detail.textContent = root.available
      ? `${root.site_model_count} site model${root.site_model_count === 1 ? '' : 's'}`
      : root.reason;

    text.append(path, detail);

    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'btn btn-ghost';
    remove.textContent = 'Remove';
    remove.setAttribute('aria-label', `Stop reading site models from ${root.path}`);
    remove.addEventListener('click', () => this._remove(root.path, remove));

    item.append(text, remove);
    return item;
  }

  async _submit() {
    const value = this.inputEl.value.trim();
    if (!value) {
      this._setMessage('Enter a directory path to add.', true);
      return;
    }

    this.addBtn.disabled = true;
    try {
      const added = await this.addRoot(value);
      this.inputEl.value = '';
      this._setMessage(
        `Added ${added.path} — ${added.site_model_count} site model` +
          `${added.site_model_count === 1 ? '' : 's'} now available.`
      );
      await this.refresh();
      this.onChange();
    } catch (cause) {
      this._setMessage(cause.message, true);
    } finally {
      this.addBtn.disabled = false;
    }
  }

  async _remove(path, trigger) {
    trigger.disabled = true;
    try {
      await this.removeRoot(path);
      this._setMessage(`Removed ${path}. Its site models are no longer readable.`);
      await this.refresh();
      this.onChange();
    } catch (cause) {
      trigger.disabled = false;
      this._setMessage(cause.message, true);
    }
  }

  _setMessage(text, isError = false) {
    this.messageEl.hidden = text === null || text === undefined;
    this.messageEl.textContent = text || '';
    this.messageEl.classList.toggle('is-error', Boolean(isError));
  }
}
