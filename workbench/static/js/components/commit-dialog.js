/**
 * Layer 4 — Commit sequencer results dialog.
 *
 * Every control in this dialog is built from the response of
 * `GET /api/results/commit/preview`. Nothing about the repository is assumed in
 * the browser: the branch list, the remote list, the file list, the affected
 * devices, the default message, and any blocking reason are all real git state
 * read server-side. A dialog that guessed (offering `origin`, or the branch the
 * operator used last) would let a commit be aimed at a repository that does not
 * have it.
 *
 * Scope is the SITE MODEL DIRECTORY, which is the pathspec the server restricts
 * `git add`/`git commit` to. It is not the repository (a lab repository can
 * hold the site model in a subdirectory, as `<repo>/udmi` does) and it is no
 * longer one device's results directory: a run also writes
 * `out/sequencer_<id>.json` and `devices/<id>/out/**`, which the old per-device
 * scope silently left behind.
 *
 * Because the scope is now wide, the dialog LEADS with which devices' recorded
 * results are about to be rewritten. That warning is the first thing read and
 * is built only from `preview.devices`; the flat file list is kept but demoted
 * behind a disclosure, since 'CGW-2 (14 files)' is the fact an operator can act
 * on and 400 paths is not.
 */

import { api } from '../core/api.js';

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), ' +
  'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Every key `results_commit.preview()` guarantees. They are checked rather than
 * read optimistically because a short response means the contract changed, and
 * silently rendering an empty branch dropdown from a missing `branches` key
 * would look identical to a repository that genuinely has no branches. The
 * device keys are checked for the same reason: an absent `devices` array must
 * not render as "no devices affected" for a commit that touches all of them.
 */
const PREVIEW_FIELDS = [
  'committable',
  'reason',
  'repo',
  'path',
  'changes',
  'change_count',
  'repo_is_udmi',
  'current_branch',
  'branches',
  'remotes',
  'default_message',
  'devices',
  'site_changes',
  'device_count',
];

/** Every key each entry of `preview.devices` guarantees. */
const DEVICE_FIELDS = ['device_id', 'files', 'file_count', 'known'];

/**
 * Fails with an actionable message naming the absent keys.
 *
 * Exported because the devices view validates compliance records the same way;
 * one copy of the fail-fast rule keeps the two payload readers consistent.
 */
export function requirePayloadFields(payload, fields, what) {
  if (payload === null || typeof payload !== 'object') {
    throw new Error(`${what} is not an object, so it cannot be rendered.`);
  }
  const missing = fields.filter((field) => !(field in payload));
  if (missing.length > 0) {
    throw new Error(
      `${what} is missing required field(s): ${missing.join(', ')}. ` +
        'The Workbench will not render git controls from an incomplete response.'
    );
  }
  return payload;
}

export class CommitDialog {
  constructor() {
    this.container = document.createElement('div');
    this.container.className = 'modal-backdrop';
    this.container.hidden = true;
    document.body.appendChild(this.container);

    this.siteModel = null;
    this.preview = null;
    this.committed = null;
    this.previousFocus = null;
    this._resolve = null;

    this._build();
  }

  _build() {
    this.container.innerHTML = `
      <div class="modal commit-modal" role="dialog" aria-modal="true"
           aria-labelledby="commit-dialog-title">
        <header class="modal-header">
          <div>
            <h2 class="modal-title" id="commit-dialog-title">Commit sequencer results</h2>
            <p class="modal-subtitle" data-role="subtitle"></p>
          </div>
          <button type="button" class="btn btn-ghost" data-act="close"
                  aria-label="Close commit results">Close</button>
        </header>

        <div class="modal-body commit-body">
          <p class="empty-note" data-role="loading">Reading git state for this site model…</p>

          <div class="commit-content" data-role="content" hidden>
            <p class="commit-block" data-role="block" hidden></p>

            <section class="commit-impact" data-role="impact" hidden>
              <h3 class="commit-section-title">What this commit changes</h3>
              <p class="commit-impact-lead" data-role="impact-lead"></p>
              <ul class="commit-device-list" data-role="impact-devices"></ul>
              <p class="commit-impact-site" data-role="impact-site"></p>
            </section>

            <dl class="commit-facts">
              <div><dt>Repository</dt><dd data-role="fact-repo"></dd></div>
              <div><dt>Committed path</dt><dd data-role="fact-path"></dd></div>
              <div><dt>Current branch</dt><dd data-role="fact-branch"></dd></div>
              <div><dt>Pending changes</dt><dd data-role="fact-count"></dd></div>
            </dl>

            <details class="commit-changes">
              <summary class="commit-section-title" data-role="changes-summary">
                Every file this commit will contain
              </summary>
              <ul class="commit-change-list" data-role="changes"></ul>
            </details>

            <form class="commit-form" data-role="form">
              <div class="field">
                <label for="commit-message">Commit message</label>
                <textarea id="commit-message" data-role="message" rows="7"
                          spellcheck="false"></textarea>
                <p class="field-hint">
                  Pre-filled with the message the server generated for this site model and
                  the devices listed above. Replace it with a descriptive message if this
                  run needs one.
                </p>
              </div>

              <div class="field">
                <label for="commit-branch">Commit onto branch</label>
                <select id="commit-branch" data-role="branch"></select>
                <p class="field-hint" data-role="branch-note"></p>
              </div>

              <div class="field commit-toggle">
                <label class="commit-check">
                  <input type="checkbox" data-role="create-branch" />
                  Create a new branch instead
                </label>
                <label class="visually-hidden" for="commit-new-branch">New branch name</label>
                <input id="commit-new-branch" data-role="new-branch" type="text"
                       autocomplete="off" spellcheck="false"
                       placeholder="New branch name" />
              </div>

              <div class="field commit-toggle">
                <label class="commit-check">
                  <input type="checkbox" data-role="push" />
                  Push after committing
                </label>
                <label class="visually-hidden" for="commit-remote">Remote to push to</label>
                <select id="commit-remote" data-role="remote"></select>
                <p class="field-hint" data-role="remote-note"></p>
              </div>
            </form>
          </div>

          <p class="commit-status" data-role="status" hidden></p>
        </div>

        <footer class="commit-footer">
          <button type="button" class="btn btn-ghost" data-act="cancel">Cancel</button>
          <button type="button" class="btn btn-primary" data-act="commit" disabled>
            Commit results
          </button>
        </footer>
      </div>
    `;

    this.dialog = this.container.querySelector('.modal');
    this.subtitleEl = this.container.querySelector('[data-role="subtitle"]');
    this.loadingEl = this.container.querySelector('[data-role="loading"]');
    this.contentEl = this.container.querySelector('[data-role="content"]');
    this.blockEl = this.container.querySelector('[data-role="block"]');
    this.impactEl = this.container.querySelector('[data-role="impact"]');
    this.impactLeadEl = this.container.querySelector('[data-role="impact-lead"]');
    this.impactDevicesEl = this.container.querySelector('[data-role="impact-devices"]');
    this.impactSiteEl = this.container.querySelector('[data-role="impact-site"]');
    this.factRepoEl = this.container.querySelector('[data-role="fact-repo"]');
    this.factPathEl = this.container.querySelector('[data-role="fact-path"]');
    this.factBranchEl = this.container.querySelector('[data-role="fact-branch"]');
    this.factCountEl = this.container.querySelector('[data-role="fact-count"]');
    this.changesSummaryEl = this.container.querySelector('[data-role="changes-summary"]');
    this.changesEl = this.container.querySelector('[data-role="changes"]');
    this.messageEl = this.container.querySelector('[data-role="message"]');
    this.branchSelectEl = this.container.querySelector('[data-role="branch"]');
    this.branchNoteEl = this.container.querySelector('[data-role="branch-note"]');
    this.createBranchEl = this.container.querySelector('[data-role="create-branch"]');
    this.newBranchEl = this.container.querySelector('[data-role="new-branch"]');
    this.pushEl = this.container.querySelector('[data-role="push"]');
    this.remoteEl = this.container.querySelector('[data-role="remote"]');
    this.remoteNoteEl = this.container.querySelector('[data-role="remote-note"]');
    this.statusEl = this.container.querySelector('[data-role="status"]');
    this.cancelBtn = this.container.querySelector('[data-act="cancel"]');
    this.commitBtn = this.container.querySelector('[data-act="commit"]');

    this.container.querySelector('[data-act="close"]').addEventListener('click', () => this.close());
    this.cancelBtn.addEventListener('click', () => this.close());
    this.commitBtn.addEventListener('click', () => this._submit());
    this.createBranchEl.addEventListener('change', () => this._applyControlState());
    this.pushEl.addEventListener('change', () => this._applyControlState());
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
   * Opens the dialog for one site model and resolves once it is closed.
   *
   * Resolves with the commit result when a commit was made, and with null when
   * the operator closed without committing, so the caller can refresh only
   * after something actually changed on disk.
   */
  async open({ siteModel }) {
    if (!this.container.hidden) {
      throw new Error(`The commit dialog is already open for ${this.siteModel}.`);
    }
    if (!siteModel) {
      throw new Error("CommitDialog.open requires 'siteModel'; no site model was given.");
    }

    this.siteModel = siteModel;
    this.preview = null;
    this.committed = null;
    this.previousFocus = document.activeElement;

    this.subtitleEl.textContent = siteModel;
    this.loadingEl.hidden = false;
    this.contentEl.hidden = true;
    this.commitBtn.hidden = false;
    this.commitBtn.disabled = true;
    this.cancelBtn.textContent = 'Cancel';
    this._setStatus(null);

    this.container.hidden = false;
    this.cancelBtn.focus();

    const closed = new Promise((resolve) => {
      this._resolve = resolve;
    });

    try {
      const preview = await api.commitPreview(siteModel);
      requirePayloadFields(preview, PREVIEW_FIELDS, 'Commit preview response');
      preview.devices.forEach((device, index) =>
        requirePayloadFields(device, DEVICE_FIELDS, `Commit preview device ${index}`)
      );
      this.preview = preview;
      this._renderPreview(preview);
    } catch (cause) {
      this.loadingEl.hidden = true;
      this._setStatus(cause.message, 'error');
    }

    return closed;
  }

  close() {
    if (this.container.hidden) return;
    this.container.hidden = true;
    const result = this.committed;
    const resolve = this._resolve;
    this._resolve = null;
    if (this.previousFocus instanceof HTMLElement) this.previousFocus.focus();
    if (resolve) resolve(result);
  }

  _renderPreview(preview) {
    this.loadingEl.hidden = true;
    this.contentEl.hidden = false;

    // The reason is the server's own wording and is shown verbatim. The UDMI
    // checkout block in particular is authored once, in
    // results_commit.udmi_repo_reason(), and must never be re-stated here.
    this.blockEl.hidden = preview.committable === true;
    this.blockEl.textContent = preview.committable === true ? '' : (preview.reason || '');
    this.blockEl.classList.toggle('is-udmi-repo', preview.repo_is_udmi === true);
    if (preview.repo_is_udmi === true) {
      this.blockEl.setAttribute('role', 'alert');
    } else {
      this.blockEl.removeAttribute('role');
    }

    this.factRepoEl.textContent = preview.repo || 'No git repository contains this path.';
    this.factPathEl.textContent = preview.path || 'Not resolvable without a repository.';
    this.factBranchEl.textContent =
      preview.current_branch ||
      'None — HEAD is detached in this repository, so there is no current branch.';
    this.factCountEl.textContent = `${preview.change_count} file(s) under the site model`;

    this._renderImpact(preview);
    this._renderChanges(preview.changes);
    this._renderBranches(preview);
    this._renderRemotes(preview);

    this.messageEl.value = preview.default_message;
    this.createBranchEl.checked = false;
    this.newBranchEl.value = '';
    this.pushEl.checked = false;
    this._applyControlState();
  }

  /**
   * The per-device warning, which is the point of the site-level dialog.
   *
   * Hidden entirely when the commit is blocked: there is nothing to warn about
   * for a commit that cannot happen, and the server's blocking reason is the
   * only thing worth reading in that state.
   */
  _renderImpact(preview) {
    this.impactEl.hidden = preview.committable !== true;
    this.impactDevicesEl.textContent = '';
    if (preview.committable !== true) return;

    this.impactLeadEl.textContent =
      preview.device_count > 0
        ? `This commit will update recorded results for ${preview.device_count} device(s):`
        : 'No device result files changed. This commit contains site-level files only.';

    for (const device of preview.devices) {
      const item = document.createElement('li');
      item.className = 'commit-device';
      const name = document.createElement('span');
      name.className = 'commit-device-name';
      name.textContent = device.device_id;
      const count = document.createElement('span');
      count.className = 'commit-device-count';
      count.textContent = `${device.file_count} file(s)`;
      item.append(name, count);
      // A changed path can name a device the site model no longer contains.
      // The files are still committed, so the operator is told rather than the
      // discrepancy being smoothed over.
      if (device.known !== true) {
        const unknown = document.createElement('span');
        unknown.className = 'commit-device-unknown';
        unknown.textContent = 'not in this site model\u2019s devices/ directory';
        item.appendChild(unknown);
      }
      this.impactDevicesEl.appendChild(item);
    }

    this.impactSiteEl.textContent =
      preview.site_changes.length > 0
        ? `Plus ${preview.site_changes.length} site-level file(s) that belong to no device, ` +
          'such as the registration summary and site configuration.'
        : 'No site-level files outside the device directories are affected.';
  }

  _renderChanges(changes) {
    this.changesSummaryEl.textContent =
      `Every file this commit will contain (${changes.length}, relative to the site model)`;
    this.changesEl.textContent = '';
    if (changes.length === 0) {
      const empty = document.createElement('li');
      empty.className = 'commit-change is-empty';
      empty.textContent = 'No pending changes under the site model.';
      this.changesEl.appendChild(empty);
      return;
    }
    for (const entry of changes) {
      const item = document.createElement('li');
      item.className = 'commit-change';
      item.textContent = entry;
      this.changesEl.appendChild(item);
    }
  }

  _renderBranches(preview) {
    this.branchSelectEl.textContent = '';

    if (preview.branches.length === 0) {
      const none = document.createElement('option');
      none.value = '';
      none.textContent = 'No local branches in this repository';
      this.branchSelectEl.appendChild(none);
      this.branchNoteEl.textContent =
        'This repository has no local branches. Tick "Create a new branch instead" and name one.';
      return;
    }

    // No option is pre-selected when HEAD is detached: there is no branch the
    // operator implicitly meant, and picking one for them would commit the
    // results somewhere they never chose.
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Select a branch…';
    this.branchSelectEl.appendChild(placeholder);

    for (const branch of preview.branches) {
      const option = document.createElement('option');
      option.value = branch;
      option.textContent = branch === preview.current_branch ? `${branch} (current)` : branch;
      this.branchSelectEl.appendChild(option);
    }

    this.branchSelectEl.value = preview.current_branch || '';
    this.branchNoteEl.textContent = preview.current_branch
      ? `Currently on ${preview.current_branch}. Choosing another branch switches to it before committing.`
      : 'HEAD is detached, so there is no current branch. Choose the branch to commit onto, or create one.';
  }

  _renderRemotes(preview) {
    this.remoteEl.textContent = '';

    if (preview.remotes.length === 0) {
      const none = document.createElement('option');
      none.value = '';
      none.textContent = 'No remotes configured';
      this.remoteEl.appendChild(none);
      this.remoteNoteEl.textContent = preview.repo
        ? `No remotes are configured in ${preview.repo}, so these results can only be committed locally.`
        : 'No repository, so there is nothing to push to.';
      return;
    }

    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Select a remote…';
    this.remoteEl.appendChild(placeholder);

    for (const remote of preview.remotes) {
      const option = document.createElement('option');
      option.value = remote;
      option.textContent = remote;
      this.remoteEl.appendChild(option);
    }
    this.remoteNoteEl.textContent =
      'Pushing is optional. The remote is taken from this repository\u2019s configuration.';
  }

  /** Enables exactly the controls the previewed repository state permits. */
  _applyControlState() {
    const live = this.preview !== null && this.preview.committable === true &&
      this.committed === null;
    const creating = live && this.createBranchEl.checked;
    const hasBranches = live && this.preview.branches.length > 0;
    const hasRemotes = live && this.preview.remotes.length > 0;

    this.messageEl.disabled = !live;
    this.createBranchEl.disabled = !live;
    this.branchSelectEl.disabled = !hasBranches || creating;
    this.newBranchEl.disabled = !creating;
    this.pushEl.disabled = !hasRemotes;
    this.remoteEl.disabled = !hasRemotes || !this.pushEl.checked;
    this.commitBtn.disabled = !live;
  }

  async _submit() {
    const message = this.messageEl.value.trim();
    if (!message) {
      this._setStatus(
        'The commit message is empty. Type a message, or reopen the dialog to restore the ' +
          'default message the server generated.',
        'error'
      );
      return;
    }

    const createBranch = this.createBranchEl.checked;
    const branch = createBranch ? this.newBranchEl.value.trim() : this.branchSelectEl.value;
    if (createBranch && !branch) {
      this._setStatus('Branch creation is selected but no new branch name was entered.', 'error');
      return;
    }
    if (!createBranch && !branch) {
      this._setStatus(
        this.preview.current_branch
          ? 'Select the branch to commit onto.'
          : 'HEAD is detached in this repository, so there is no branch to commit onto. ' +
            'Select an existing branch or create a new one.',
        'error'
      );
      return;
    }

    const push = this.pushEl.checked;
    const remote = push ? this.remoteEl.value : null;
    if (push && !remote) {
      this._setStatus('Push is selected but no remote was chosen.', 'error');
      return;
    }

    this.commitBtn.disabled = true;
    this._setStatus(
      `Committing ${this.preview.change_count} file(s) for ${this.preview.device_count} ` +
        `device(s) to ${branch}…`,
      'busy'
    );

    try {
      const result = await api.commitResults({
        siteModel: this.siteModel,
        message,
        branch,
        createBranch,
        push,
        remote,
      });
      this.committed = result;
      this._renderCommitted(result);
    } catch (cause) {
      this._setStatus(cause.message, 'error');
      this._applyControlState();
    }
  }

  /** Reports what the server actually did, from the commit response only. */
  _renderCommitted(result) {
    const lines = [
      `Committed ${result.file_count} file(s) for ${result.device_count} device(s) as ` +
        `${result.short_revision} on branch ${result.branch}` +
        `${result.branch_created ? ' (created)' : ''}.`,
      result.pushed ? `Pushed to ${result.remote}.` : 'Not pushed.',
    ];
    if (result.push_output) lines.push(result.push_output);
    this._setStatus(lines.join(' '), 'ok');

    this.commitBtn.hidden = true;
    this.cancelBtn.textContent = 'Close';
    this.cancelBtn.focus();
    this._applyControlState();
  }

  _setStatus(text, tone = 'info') {
    this.statusEl.hidden = text === null;
    this.statusEl.textContent = text || '';
    this.statusEl.className = `commit-status is-${tone}`;
  }
}
