/**
 * Layer 2 — SupportBundleButton molecule.
 *
 * Exports a support/evidence bundle (private keys excluded) for
 * the site model returned by `getSiteModel`, then hands the archive to the
 * browser's download machinery. Progress and failures are always visible:
 * the server's error text is shown
 * verbatim rather than collapsed into a generic message.
 *
 * Props: { getSiteModel: () => string, label?: string }
 * States: default, loading (disabled + progress text), success, error.
 */

import { api } from '../core/api.js';

function formatBytes(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${bytes} B`;
}

export class SupportBundleButton {
  constructor(container, { getSiteModel, label = 'Export support bundle' }) {
    this.container = container;
    this.getSiteModel = getSiteModel;
    this.label = label;
    this.busy = false;

    this.container.classList.add('support-bundle');
    this.container.innerHTML = `
      <button type="button" class="btn btn-outlined" data-act="export"
              title="Package the site model, out/ and cached tool configs; private keys are excluded"></button>
      <span class="support-bundle-status" data-role="status" role="status" aria-live="polite" hidden></span>
    `;
    this.button = this.container.querySelector('[data-act="export"]');
    this.statusEl = this.container.querySelector('[data-role="status"]');
    this.button.textContent = this.label;
    this.button.addEventListener('click', () => this.export());
  }

  _status(message, tone) {
    this.statusEl.hidden = false;
    this.statusEl.textContent = '';
    this.statusEl.className = `support-bundle-status is-${tone}`;
    this.statusEl.setAttribute('role', tone === 'error' ? 'alert' : 'status');
    this.statusEl.append(message);
  }

  async export() {
    if (this.busy) return;
    const siteModel = this.getSiteModel();
    if (!siteModel) {
      this._status('Select a site model before exporting a support bundle.', 'error');
      return;
    }

    this.busy = true;
    this.button.disabled = true;
    this.button.textContent = 'Creating bundle…';
    this._status(
      `Packaging ${siteModel} (private keys excluded). Large site models take minutes.`,
      'info'
    );
    try {
      const bundle = await api.createSupportBundle(siteModel);
      const link = document.createElement('a');
      link.href = api.supportBundleUrl(bundle.bundle_id);
      link.download = bundle.filename;
      link.textContent = bundle.filename;
      const message = document.createElement('span');
      message.append(
        'Bundle ready: ',
        link,
        ` (${formatBytes(bundle.size_bytes)}, ${bundle.member_count} files, ` +
          `${bundle.excluded_secret_count} private key files excluded).`
      );
      this._status(message, 'success');
      link.click();
    } catch (cause) {
      this._status(`Support bundle export failed: ${cause.message}`, 'error');
    } finally {
      this.busy = false;
      this.button.disabled = false;
      this.button.textContent = this.label;
    }
  }
}
