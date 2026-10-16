/**
 * Layer 4 — Workbench Settings dialog (email notifications).
 *
 * Consent is given here once, and only here. It is stored server-side in
 * `~/.config/udmi/workbench.json`, bound to the address of the operator's own
 * Google credentials, and that address is the only recipient the server will
 * ever send to. Each run and each Mantis message still has its own "Notify me
 * when done" toggle, off by default; this dialog only makes that toggle usable.
 *
 * Everything shown comes from `GET /api/notifications`: whether the credentials
 * can send (gmail.send scope), the exact setup command when they cannot, and
 * the outcome of every recent delivery, so a failed email is visible here
 * instead of disappearing in a server thread.
 */

import { api } from '../core/api.js';

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), ' +
  'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

const KIND_LABELS = { mantis: 'Mantis', sequencer: 'Sequencer run', test: 'Test email' };

export class SettingsDialog {
  constructor(container) {
    this.container = container;
    this.previousFocus = null;
    this.status = null;
    this._build();
  }

  _build() {
    this.container.className = 'modal-backdrop';
    this.container.hidden = true;
    this.container.innerHTML = `
      <div class="modal modal-narrow settings-modal" role="dialog" aria-modal="true"
           aria-labelledby="settings-dialog-title">
        <header class="modal-header">
          <div>
            <h2 class="modal-title" id="settings-dialog-title">Settings</h2>
            <p class="modal-subtitle">Stored outside the repository, in ~/.config/udmi/workbench.json</p>
          </div>
          <div class="modal-header-actions">
            <button type="button" class="btn btn-ghost" data-act="close"
                    aria-label="Close settings">Close</button>
          </div>
        </header>
        <div class="modal-body settings-body">
          <section aria-labelledby="settings-email-title">
            <h3 class="settings-section-title" id="settings-email-title">Email notifications</h3>
            <p class="settings-help">
              When you turn on <strong>Email me when done</strong> for a sequencer run or a
              Mantis message, the full result is emailed to you when it finishes, even if you
              close this tab. Mail is sent through the Gmail API with your own Google
              credentials, from and to your own address only.
            </p>
            <p class="settings-status" data-role="status" role="status" aria-live="polite">Loading…</p>
            <label class="settings-consent">
              <input type="checkbox" data-role="consent" disabled />
              <span data-role="consent-label">Allow Workbench to email me results</span>
            </label>
            <div class="settings-setup" data-role="setup" hidden>
              <p>Run this in a terminal, then press Refresh:</p>
              <pre class="settings-command" data-role="setup-command"></pre>
            </div>
            <div class="settings-actions">
              <button type="button" class="btn btn-ghost" data-act="refresh">Refresh</button>
              <button type="button" class="btn btn-primary" data-act="test" disabled>Send test email</button>
            </div>
            <p class="settings-error" data-role="error" role="alert" hidden></p>
          </section>
          <section aria-labelledby="settings-deliveries-title">
            <h3 class="settings-section-title" id="settings-deliveries-title">Recent deliveries</h3>
            <p class="settings-help">Since this Workbench server started.</p>
            <ul class="settings-deliveries" data-role="deliveries"></ul>
          </section>
        </div>
      </div>
    `;

    this.dialog = this.container.querySelector('.modal');
    this.statusEl = this.container.querySelector('[data-role="status"]');
    this.consentInput = this.container.querySelector('[data-role="consent"]');
    this.consentLabel = this.container.querySelector('[data-role="consent-label"]');
    this.setupEl = this.container.querySelector('[data-role="setup"]');
    this.setupCommandEl = this.container.querySelector('[data-role="setup-command"]');
    this.errorEl = this.container.querySelector('[data-role="error"]');
    this.deliveriesEl = this.container.querySelector('[data-role="deliveries"]');
    this.testBtn = this.container.querySelector('[data-act="test"]');
    this.refreshBtn = this.container.querySelector('[data-act="refresh"]');

    this.container.querySelector('[data-act="close"]').addEventListener('click', () => this.close());
    this.refreshBtn.addEventListener('click', () => this.refresh());
    this.testBtn.addEventListener('click', () => this._sendTest());
    this.consentInput.addEventListener('change', () => this._setConsent(this.consentInput.checked));
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
    const focusable = [...this.dialog.querySelectorAll(FOCUSABLE)].filter((el) => !el.closest('[hidden]'));
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

  open() {
    this.previousFocus = document.activeElement;
    this.container.hidden = false;
    this.container.querySelector('[data-act="close"]').focus();
    this.refresh();
  }

  close() {
    if (this.container.hidden) return;
    this.container.hidden = true;
    this.previousFocus?.focus?.();
  }

  async refresh() {
    this._setError(null);
    this.refreshBtn.disabled = true;
    try {
      this._paint(await api.getNotifications());
    } catch (cause) {
      this.statusEl.textContent = 'Notification status is unavailable.';
      this._setError(cause.message);
    } finally {
      this.refreshBtn.disabled = false;
    }
  }

  async _setConsent(consent) {
    this._setError(null);
    this.consentInput.disabled = true;
    try {
      this._paint(await api.setNotificationConsent(consent));
    } catch (cause) {
      this.consentInput.checked = !consent;
      this.consentInput.disabled = false;
      this._setError(cause.message);
    }
  }

  async _sendTest() {
    this._setError(null);
    this.testBtn.disabled = true;
    try {
      const delivery = await api.sendTestNotification();
      this.statusEl.textContent = `Test email sent to ${delivery.address}. Check your inbox.`;
    } catch (cause) {
      this._setError(`Test email failed: ${cause.message}`);
    }
    await this._refreshDeliveries();
  }

  async _refreshDeliveries() {
    try {
      const status = await api.getNotifications();
      this.status = status;
      this._paintDeliveries(status.deliveries);
      this.testBtn.disabled = !status.email.ready;
    } catch (cause) {
      this._setError(cause.message);
    }
  }

  _paint(status) {
    this.status = status;
    const email = status.email;
    this.consentInput.checked = email.consented;
    // Consent can always be withdrawn; it can only be given when sending is possible now.
    this.consentInput.disabled = !email.consented && (!email.address || !email.scope_ok);
    this.consentLabel.textContent = email.address
      ? `Allow Workbench to email results to ${email.address}`
      : 'Allow Workbench to email me results';

    if (email.ready) {
      this.statusEl.textContent = `Enabled. Results go to ${email.consented_address}.`;
    } else if (email.problem) {
      this.statusEl.textContent = email.problem;
    } else {
      this.statusEl.textContent = `Not enabled. ${email.address} can receive notifications once you allow it.`;
    }
    this.statusEl.classList.toggle('is-error', Boolean(email.problem));

    const needsSetup = Boolean(email.problem) && (!email.address || !email.scope_ok);
    this.setupEl.hidden = !needsSetup;
    this.setupCommandEl.textContent = email.setup_command;
    this.testBtn.disabled = !email.ready;
    this._paintDeliveries(status.deliveries);
  }

  _paintDeliveries(deliveries) {
    this.deliveriesEl.textContent = '';
    if (deliveries.length === 0) {
      const empty = document.createElement('li');
      empty.className = 'settings-delivery-empty';
      empty.textContent = 'Nothing sent yet.';
      this.deliveriesEl.appendChild(empty);
      return;
    }
    for (const delivery of deliveries) {
      const item = document.createElement('li');
      item.className = `settings-delivery is-${delivery.status.toLowerCase()}`;
      const head = document.createElement('div');
      head.className = 'settings-delivery-head';
      head.textContent =
        `${delivery.status} · ${KIND_LABELS[delivery.kind] || delivery.kind} · ` +
        new Date(delivery.at).toLocaleString();
      const subject = document.createElement('div');
      subject.className = 'settings-delivery-subject';
      subject.textContent = delivery.subject || '(not composed)';
      item.append(head, subject);
      if (delivery.error) {
        const error = document.createElement('div');
        error.className = 'settings-delivery-error';
        error.textContent = delivery.error;
        item.appendChild(error);
      }
      this.deliveriesEl.appendChild(item);
    }
  }

  _setError(message) {
    this.errorEl.hidden = !message;
    this.errorEl.textContent = message || '';
  }
}
