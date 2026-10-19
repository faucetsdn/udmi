/**
 * Layer 1 — Sequencer run configuration panel.
 *
 * Owns every user input that shapes the `bin/sequencer` invocation. All option
 * sets are injected from real discovery (site models, devices, backend option
 * whitelists); nothing here is a hardcoded list.
 *
 * v1 parity notes: keeps the ⚙ progressive-disclosure popover and the hard
 * run-gating, but the gate is now recomputed from a single validate() pass and
 * every control is disabled for the duration of a run (v1 left the device
 * select live, so you could desync the label from the running process).
 */

import { DeviceCombobox } from './device-combobox.js';

export class RunControls {
  constructor(container, { onChange, onRun, onStop, onManagePaths }) {
    this.container = container;
    this.onChange = onChange;
    this.onRun = onRun;
    this.onStop = onStop;
    this.onManagePaths = onManagePaths;
    this._build();
  }

  _build() {
    this.container.innerHTML = `
      <div class="field">
        <label for="ctl-site-model">Site model</label>
        <select id="ctl-site-model" data-key="siteModel"></select>
        <button type="button" class="link-button" data-act="manage-paths">
          Site model somewhere else? Add its path…
        </button>
        <p class="field-hint" data-hint="siteModel"></p>
      </div>

      <div class="field">
        <label for="ctl-device">Device</label>
        <div data-mount="device"></div>
        <p class="field-hint" data-hint="deviceId"></p>
      </div>

      <div class="field">
        <label for="ctl-project-spec">Project spec</label>
        <input id="ctl-project-spec" data-key="projectSpec" list="ctl-project-spec-options"
               placeholder="e.g. //mqtt/localhost:18833"
               autocomplete="off" spellcheck="false" />
        <datalist id="ctl-project-spec-options"></datalist>
      </div>

      <div class="field-row">
        <button type="button" class="btn btn-ghost" data-act="settings" aria-expanded="false"
                aria-controls="ctl-advanced" title="Advanced run options">&#9881; Options</button>
        <span class="option-summary" data-role="option-summary"></span>
      </div>

      <div class="popover" id="ctl-advanced" hidden>
        <div class="field">
          <label for="ctl-log-level">Log level</label>
          <select id="ctl-log-level" data-key="logLevel"></select>
        </div>
        <div class="field">
          <label for="ctl-serial">Serial number</label>
          <input id="ctl-serial" data-key="serialNo" placeholder="optional" autocomplete="off" />
        </div>
      </div>

      <div class="run-actions">
        <button type="button" class="btn btn-primary" data-act="run">Run selected</button>
        <button type="button" class="btn btn-danger" data-act="stop" hidden>Stop</button>
      </div>
      <label class="notify-toggle" title="Email me the results when this run finishes, even if this tab is closed. Enable email in Settings first.">
        <input type="checkbox" data-role="notify" /> Email me when done
      </label>
      <p class="run-gate" data-role="gate"></p>
    `;

    this.siteSelect = this.container.querySelector('[data-key="siteModel"]');
    this.deviceBox = new DeviceCombobox(this.container.querySelector('[data-mount="device"]'), {
      id: 'ctl-device',
      label: 'Devices',
      onSelect: (value) => this.onChange('deviceId', value),
    });
    this.projectInput = this.container.querySelector('[data-key="projectSpec"]');
    this.projectOptions = this.container.querySelector('#ctl-project-spec-options');
    this.logLevelSelect = this.container.querySelector('[data-key="logLevel"]');
    this.serialInput = this.container.querySelector('[data-key="serialNo"]');
    this.popover = this.container.querySelector('#ctl-advanced');
    this.settingsBtn = this.container.querySelector('[data-act="settings"]');
    this.runBtn = this.container.querySelector('[data-act="run"]');
    this.stopBtn = this.container.querySelector('[data-act="stop"]');
    this.notifyInput = this.container.querySelector('[data-role="notify"]');
    this.gateEl = this.container.querySelector('[data-role="gate"]');
    this.optionSummary = this.container.querySelector('[data-role="option-summary"]');

    for (const el of [
      this.siteSelect,
      this.logLevelSelect,
    ]) {
      el.addEventListener('change', () => this.onChange(el.dataset.key, el.value));
    }
    for (const el of [this.projectInput, this.serialInput]) {
      el.addEventListener('input', () => this.onChange(el.dataset.key, el.value));
    }

    this.settingsBtn.addEventListener('click', () => this._togglePopover());
    this.runBtn.addEventListener('click', () => this.onRun());
    this.stopBtn.addEventListener('click', () => this.onStop());
    this.container
      .querySelector('[data-act="manage-paths"]')
      .addEventListener('click', () => this.onManagePaths());

    // Close the popover on any outside interaction, matching v1's behaviour.
    document.addEventListener('mousedown', (event) => {
      if (this.popover.hidden) return;
      if (this.popover.contains(event.target) || this.settingsBtn.contains(event.target)) return;
      this._togglePopover(false);
    });
  }

  /**
   * Whether the operator asked to be emailed about the run being started. Read
   * once per run and cleared, so the toggle is per run and off by default.
   */
  takeNotifyRequest() {
    const requested = this.notifyInput.checked;
    this.notifyInput.checked = false;
    return requested;
  }

  _togglePopover(force) {
    const next = force === undefined ? this.popover.hidden : force;
    this.popover.hidden = !next;
    this.settingsBtn.setAttribute('aria-expanded', String(next));
  }

  /** Populates the backend-supplied option whitelists. */
  setRunOptions({ log_levels = [] }) {
    this._fillSelect(this.logLevelSelect, log_levels.map((o) => ({ value: o.value, label: o.value })));
  }

  /**
   * Groups models by origin. Two site models can easily share a directory name
   * across a checkout and a registered path, so the group heading is what makes
   * the choice unambiguous.
   */
  setSiteModels(models) {
    const previous = this.siteSelect.value;
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
        option.textContent = model.device_count
          ? `${model.name} (${model.device_count} devices)`
          : `${model.name} (empty)`;
        group.appendChild(option);
      }
      this.siteSelect.appendChild(group);
    }

    if ([...this.siteSelect.options].some((o) => o.value === previous)) {
      this.siteSelect.value = previous;
    }
  }

  setDevices(devices) {
    this.deviceBox.setOptions(
      devices.map((device) => ({
        value: device.device_id,
        label: device.error
          ? `${device.device_id} — unreadable metadata.json`
          : device.is_gateway
            ? `${device.device_id} — gateway`
            : device.gateway_id
              ? `${device.device_id} — proxied by ${device.gateway_id}`
              : device.device_id,
      })),
      'No devices in this site model'
    );
  }

  /** Suggestions come from the site config and previously recorded results. */
  setProjectSpecSuggestions(values) {
    this.projectOptions.textContent = '';
    for (const value of values) {
      const option = document.createElement('option');
      option.value = value;
      this.projectOptions.appendChild(option);
    }
  }

  _fillSelect(select, options, placeholder = null) {
    const previous = select.value;
    select.textContent = '';
    if (placeholder !== null) {
      const blank = document.createElement('option');
      blank.value = '';
      blank.textContent = placeholder;
      select.appendChild(blank);
    }
    for (const option of options) {
      const el = document.createElement('option');
      el.value = option.value;
      el.textContent = option.label;
      select.appendChild(el);
    }
    if ([...select.options].some((o) => o.value === previous)) select.value = previous;
  }

  /** Mirrors store state into the controls without emitting change events. */
  syncFrom(state) {
    if (this.siteSelect.value !== state.siteModel) this.siteSelect.value = state.siteModel;
    if (this.deviceBox.value !== state.deviceId) this.deviceBox.value = state.deviceId;
    if (this.projectInput.value !== state.projectSpec) this.projectInput.value = state.projectSpec;
    if (this.logLevelSelect.value !== state.logLevel) this.logLevelSelect.value = state.logLevel;
    if (this.serialInput.value !== state.serialNo) this.serialInput.value = state.serialNo;

    const bits = [state.logLevel];
    if (state.serialNo) bits.push(`serial ${state.serialNo}`);
    this.optionSummary.textContent = bits.filter(Boolean).join(' · ');

    this._applyRunState(state);
  }

  _applyRunState(state) {
    const running = state.running;
    for (const el of [
      this.siteSelect,
      this.deviceBox,
      this.projectInput,
      this.logLevelSelect,
      this.serialInput,
      this.settingsBtn,
    ]) {
      el.disabled = running;
    }
    if (running) this._togglePopover(false);

    this.runBtn.hidden = running;
    this.stopBtn.hidden = !running;

    const problem = RunControls.validate(state);
    this.runBtn.disabled = running || problem !== null;
    this.gateEl.textContent = running ? '' : problem || '';
  }

  /** Single source of truth for whether a run may start. Returns null if valid. */
  static validate(state) {
    if (!state.siteModel) return 'Select a site model to continue.';
    if (!state.deviceId) return 'Select a device to continue.';
    const spec = state.projectSpec.trim();
    if (!spec) {
      return 'Enter a project spec: //mqtt/localhost:18833 (local) or //gbos/<project_id> (cloud).';
    }
    // bin/sequencer fails any spec not starting with '//' ("Unrecognized
    // project spec"), so catch it here instead of after the process launches.
    if (!spec.startsWith('//')) {
      return (
        `Project spec "${spec}" is not valid: it must start with // as ` +
        '//<provider>/<project>[/<namespace>], e.g. //mqtt/localhost:18833 (local) ' +
        'or //gbos/bos-platform-staging (cloud).'
      );
    }
    if (state.selectedTests.length === 0) return 'Select at least one sequence to run.';

    // A sequence below the minimum stage never reports a result; the runner
    // just skips it. Block the run and say so rather than let it vanish.
    const excluded = state.stageExcluded || [];
    if (excluded.length > 0) {
      const names = excluded.slice(0, 3).join(', ');
      const more = excluded.length > 3 ? ` and ${excluded.length - 3} more` : '';
      return (
        `${excluded.length} selected sequence(s) are below the "${state.minStage}" ` +
        `minimum stage and would not run: ${names}${more}. ` +
        `Adjust the minimum stage filter, or deselect them.`
      );
    }
    return null;
  }
}
