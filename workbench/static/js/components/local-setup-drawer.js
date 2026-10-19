/**
 * Layer 1 — Local Test Setup Drawer & Topology Visualizer.
 *
 * Provides a collapsible slide-out drawer on the right side of the Sequencer tab.
 * Allows test operators to:
 *   1. Start / stop / restart the local test substrate (Mosquitto, UDMIS, etcd)
 *      in unprivileged isolated user-space mode (//mqtt/localhost:<port>, entered in the drawer).
 *   2. View an interactive topology graph with real-time component health.
 *   3. Launch / stop simulated Pubber instances for the active device.
 *   4. Inspect live component logs (Setup, UDMIS, Mosquitto, Pubber).
 */

import { attachResizer, clampSize } from './resizer.js';
import { renderConnectionCard } from './device-connection-card.js';

/** Below this the topology cards and the four log tabs start wrapping illegibly. */
const DRAWER_MIN_WIDTH_PX = 320;
/** Widest useful drawer; past this the service logs are mostly whitespace. */
const DRAWER_MAX_WIDTH_PX = 900;
/** Workspace width the drawer must always leave behind, so it can never cover the app. */
const WORKSPACE_FLOOR_PX = 360;
/**
 * Hard stop for the substrate to report `overall === 'UP'` after a start or
 * restart, matching the 90 s `bin/udmi start` readiness rule in GEMINI.md.
 */
export const SUBSTRATE_READY_TIMEOUT_MS = 90000;
/** Interval between readiness polls of `/api/testbed/status`. */
export const SUBSTRATE_READY_POLL_MS = 2000;

/**
 * Resolves with the status once the substrate reports `overall === 'UP'`.
 *
 * Rejects with an explicit error when the backend reports ERROR (quoting its
 * `last_error`) or when `timeoutMs` elapses first. Nothing downstream --
 * notably Pubber -- may launch until this resolves. Timing is injected so the
 * behaviour can be exercised without a browser.
 */
export async function waitForSubstrateUp({
  getStatus,
  timeoutMs = SUBSTRATE_READY_TIMEOUT_MS,
  intervalMs = SUBSTRATE_READY_POLL_MS,
  sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  now = () => Date.now(),
}) {
  const deadline = now() + timeoutMs;
  let last = 'no status received';
  for (;;) {
    const status = await getStatus();
    const overall = status?.overall || 'UNKNOWN';
    if (overall === 'UP') return status;
    if (overall === 'ERROR') {
      throw new Error(`Local substrate failed to start: ${status.last_error || 'no error detail reported'}`);
    }
    last = overall;
    if (now() >= deadline) {
      throw new Error(
        `Local substrate not UP after ${Math.round(timeoutMs / 1000)}s (last status: ${last}). ` +
        'Check the Setup log; Pubber was not launched.'
      );
    }
    await sleep(intervalMs);
  }
}

/** localStorage key holding the drawer's own local project spec. */
export const LOCAL_SPEC_STORAGE_KEY = 'udmi_testbed_project_spec';
/** Shape of a spec the local substrate accepts (explicit unprivileged port). */
const LOCAL_SPEC_SHAPE = /^\/\/mqtt\/localhost:\d+$/;

/**
 * Warning text when the Sequencer targets a *different* local broker than the
 * drawer manages, or '' when there is nothing to warn about. A cloud or blank
 * Sequencer spec is not a mismatch: it is deliberately a different target.
 */
export function specMismatch(sequencerSpec, localSpec) {
  const seq = (sequencerSpec || '').trim();
  if (!LOCAL_SPEC_SHAPE.test(seq) || !localSpec || seq === localSpec) return '';
  return `Sequencer targets ${seq}, but this local setup manages ${localSpec}.`;
}

/** Health badge markup for a component status. */
export function healthBadge(status) {
  const s = (status || 'DOWN').toUpperCase();
  const cls = s === 'UP' ? 'badge-up' : s === 'INITIALIZING' ? 'badge-init' : s === 'ERROR' ? 'badge-error' : 'badge-down';
  return `<span class="health-badge ${cls}">${s === 'INITIALIZING' ? '<span class="spinner-inline"></span>' : ''}${s}</span>`;
}

/**
 * Subtitle and badge for the device-under-test card. A physical device is
 * never probed by the Workbench, so it is labelled as unmonitored rather than
 * being shown with a health state nobody measured.
 */
export function describeDut({ isPubber, pubberComp = {} }) {
  if (!isPubber) {
    return {
      subtitle: 'Physical Hardware — external device, not monitored',
      badge: '<span class="health-badge badge-neutral">NOT MONITORED</span>',
    };
  }
  return {
    subtitle: pubberComp.status === 'UP'
      ? `Pubber Emulator (PID: ${pubberComp.pid || 'Active'})`
      : 'Pubber Emulator (starts once substrate is UP)',
    badge: healthBadge(pubberComp.status),
  };
}

export class LocalSetupDrawer {
  constructor(container, { store, api, onNotify }) {
    this.container = container;
    this.store = store;
    this.api = api;
    this.onNotify = onNotify || (() => {});

    this.activeLogComponent = 'setup';
    this.pollInterval = null;
    this.logInterval = null;
    this.isOpen = false;
    // The drawer's own spec: the single source of every port it shows or
    // probes. Independent of the Sequencer's projectSpec, never defaulted.
    this.localSpec = (localStorage.getItem(LOCAL_SPEC_STORAGE_KEY) || '').trim();
    this.connection = { key: '', data: null, error: null, loading: false };

    this._build();
    this._bindEvents();
    this._mountResizer();
    this.unsubscribe = this.store.subscribe(() => this.render());
    this.startPolling();
  }

  _build() {
    this.container.innerHTML = `
      <aside class="local-setup-drawer" id="local-setup-drawer" aria-label="Local Test Setup Drawer">
        <div class="resize-handle resize-handle-x" id="local-setup-drawer-resizer"></div>

        <!-- Drawer Header -->
        <div class="drawer-header">
          <div class="drawer-title-group">
            <span class="material-symbols-outlined drawer-title-icon">account_tree</span>
            <div class="drawer-title-text">
              <h3 class="drawer-title">Local Test Setup</h3>
              <div class="drawer-subtitle">
                <span class="status-dot dot-down" id="drawer-header-dot"></span>
                <span id="drawer-header-status-text">DOWN</span>
              </div>
            </div>
          </div>
          <button type="button" class="btn-icon" id="btn-close-testbed-drawer" title="Close Drawer (Cmd+Shift+L)">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>

        <!-- Action Bar -->
        <div class="drawer-toolbar">
          <button type="button" class="btn btn-sm btn-primary" id="btn-testbed-start" title="Start local substrate (Mosquitto, UDMIS, etcd)">
            <span class="material-symbols-outlined">play_arrow</span>
            <span>Start Setup</span>
          </button>
          <button type="button" class="btn btn-sm btn-outlined btn-danger" id="btn-testbed-stop" title="Stop all local substrate services">
            <span class="material-symbols-outlined">stop</span>
            <span>Stop</span>
          </button>
          <button type="button" class="btn btn-sm btn-outlined" id="btn-testbed-restart" title="Clean restart of local substrate">
            <span class="material-symbols-outlined">refresh</span>
            <span>Restart</span>
          </button>
        </div>

        <!-- Local project spec (drawer-owned) -->
        <div class="drawer-spec-field">
          <label for="drawer-spec-input" class="drawer-spec-label">Local project spec</label>
          <input id="drawer-spec-input" class="drawer-spec-input" type="text" spellcheck="false"
                 placeholder="//mqtt/localhost:&lt;port&gt;" autocomplete="off" />
          <p class="field-hint" id="drawer-spec-hint"></p>
          <p class="field-hint drawer-spec-mismatch" id="drawer-spec-mismatch" role="status" hidden></p>
        </div>

        <!-- Notification Banner -->
        <div class="drawer-alert" id="drawer-alert" hidden>
          <span class="material-symbols-outlined alert-icon">info</span>
          <span class="alert-text" id="drawer-alert-text"></span>
        </div>

        <!-- Drawer Body -->
        <div class="drawer-body">
          <!-- Topology Section -->
          <div class="drawer-section">
            <div class="section-header">
              <div class="section-title-group">
                <span class="material-symbols-outlined section-icon">schema</span>
                <span class="section-title">Substrate Topology</span>
              </div>
              <span class="target-spec-tag" id="drawer-spec-tag">No local spec set</span>
            </div>

            <div class="topology-canvas" id="topology-canvas">
              <!-- Rendered Nodes & Flow Connections -->
            </div>
            <div id="drawer-connection-card"></div>
          </div>

          <!-- Component Logs Console -->
          <div class="drawer-section logs-section">
            <div class="section-header">
              <div class="section-title-group">
                <span class="material-symbols-outlined section-icon">terminal</span>
                <span class="section-title">Service Logs</span>
              </div>
              <div class="log-tabs">
                <button type="button" class="log-tab active" data-component="setup">Setup</button>
                <button type="button" class="log-tab" data-component="udmis">UDMIS</button>
                <button type="button" class="log-tab" data-component="mosquitto">Mosquitto</button>
                <button type="button" class="log-tab" data-component="pubber">Pubber</button>
              </div>
            </div>
            <div class="testbed-terminal">
              <pre class="terminal-body" id="testbed-terminal-body">Loading service logs...</pre>
            </div>
          </div>
        </div>
      </aside>
    `;
  }

  _bindEvents() {
    const el = (sel) => this.container.querySelector(sel);

    // Close button
    el('#btn-close-testbed-drawer')?.addEventListener('click', () => {
      this.close();
    });

    // Drawer-owned spec input
    const specInput = el('#drawer-spec-input');
    if (specInput) {
      specInput.value = this.localSpec;
      specInput.addEventListener('input', () => {
        this.localSpec = specInput.value.trim();
        localStorage.setItem(LOCAL_SPEC_STORAGE_KEY, this.localSpec);
        this.render();
        this.pollStatus();
      });
    }

    // Start setup
    el('#btn-testbed-start')?.addEventListener('click', () => this.handleStart());

    // Stop setup
    el('#btn-testbed-stop')?.addEventListener('click', () => this.handleStop());

    // Restart setup
    el('#btn-testbed-restart')?.addEventListener('click', () => this.handleRestart());

    // Toggle Pubber mode (delegated for inline topology card switch)
    this.container.addEventListener('change', async (e) => {
      if (e.target && e.target.id === 'toggle-pubber-mode') {
        const isPubber = e.target.checked;
        this.store.update('SET_PUBBER_MODE', { pubberMode: isPubber });
        const state = this.store.getState();
        const testbed = state.testbedStatus || {};
        if (testbed.overall === 'UP') {
          if (isPubber && state.deviceId) {
            try {
              this.setAlert(`Launching Pubber emulator for ${state.deviceId}...`);
              await this.api.startPubber({
                siteModel: state.siteModel,
                deviceId: state.deviceId,
                projectSpec: this.localSpec,
                serialNo: state.serialNo || '1234',
              });
              this.setAlert(`Pubber emulator running for ${state.deviceId}`);
              this.pollStatus();
              setTimeout(() => this.setAlert(''), 3000);
            } catch (pubErr) {
              this.setAlert(`Failed to launch Pubber: ${pubErr.message}`, true);
            }
          } else if (!isPubber && state.deviceId) {
            try {
              await this.api.stopPubber({ deviceId: state.deviceId, projectSpec: this.localSpec });
              this.setAlert('Switched to Physical Hardware mode. Pubber stopped.');
              this.pollStatus();
              setTimeout(() => this.setAlert(''), 3000);
            } catch (stopErr) {
              this.setAlert(`Failed to stop Pubber: ${stopErr.message}`, true);
            }
          }
        }
      }
    });

    // Log component tabs
    this.container.querySelectorAll('.log-tab').forEach((tab) => {
      tab.addEventListener('click', (e) => {
        const comp = e.currentTarget.dataset.component;
        this.activeLogComponent = comp;
        this.container.querySelectorAll('.log-tab').forEach((t) => t.classList.toggle('active', t === e.currentTarget));
        this.fetchLogs();
      });
    });

    // Keyboard shortcut Cmd+Shift+L / Ctrl+Shift+L
    window.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && e.shiftKey && (e.key === 'L' || e.key === 'l')) {
        e.preventDefault();
        this.toggle();
      }
    });
  }

  /**
   * Bounds come from the live viewport, never from storage, so a width
   * persisted on a wide monitor cannot reopen as a drawer that buries the
   * workspace on a narrow one.
   */
  _drawerBounds() {
    const fits = Math.min(DRAWER_MAX_WIDTH_PX, window.innerWidth - WORKSPACE_FLOOR_PX);
    return { min: DRAWER_MIN_WIDTH_PX, max: Math.max(DRAWER_MIN_WIDTH_PX, fits) };
  }

  _mountResizer() {
    const { min, max } = this._drawerBounds();
    const width = clampSize(this.store.getState().localSetupDrawerWidth, min, max);
    this._applyDrawerWidth(width);

    this.detachResizer = attachResizer(
      this.container.querySelector('#local-setup-drawer-resizer'),
      {
        axis: 'x',
        // The drawer is pinned to the right edge, so its left edge moving left
        // is what makes it wider.
        direction: -1,
        label: 'Resize Local Test Setup drawer',
        min,
        max,
        value: width,
        onResize: (target) => this._applyDrawerWidth(clampSize(target, min, max)),
        onCommit: (applied) =>
          this.store.update('layout.localSetupDrawerWidth', { localSetupDrawerWidth: applied }),
      }
    );
  }

  _applyDrawerWidth(width) {
    // Written on the document element so the drawer and the workspace offset in
    // components.css stay in lockstep off a single property.
    document.documentElement.style.setProperty(
      '--local-setup-drawer-width',
      `${Math.round(width)}px`
    );
    return width;
  }

  startPolling() {
    if (this.pollInterval) clearInterval(this.pollInterval);
    this.pollStatus();
    // Poll every 3.5s
    this.pollInterval = setInterval(() => this.pollStatus(), 3500);

    // Poll logs every 3s if open
    if (this.logInterval) clearInterval(this.logInterval);
    this.logInterval = setInterval(() => {
      if (this.isOpen) {
        this.fetchLogs();
      }
    }, 3000);
  }

  stopPolling() {
    this.unsubscribe?.();
    if (this.pollInterval) clearInterval(this.pollInterval);
    if (this.logInterval) clearInterval(this.logInterval);
    // This is the drawer's only teardown hook, so the resize listeners go here.
    this.detachResizer?.();
  }

  /**
   * Probes the substrate named by the drawer's spec. Without a valid spec
   * nothing is probed and the state is UNKNOWN, never a borrowed default.
   */
  async pollStatus() {
    let status;
    if (!this.localSpec) {
      status = { overall: 'UNKNOWN', components: {}, spec_error: 'Enter a local project spec (//mqtt/localhost:<port>).' };
    } else {
      try {
        status = await this.api.getTestbedStatus(this.localSpec);
      } catch (e) {
        if (e.status !== 400) return; // Gateway reloading or unreachable; keep last state.
        status = { overall: 'UNKNOWN', components: {}, spec_error: e.message };
      }
    }
    this.store.update('SET_TESTBED_STATUS', { testbedStatus: status }, { silent: true });
    this.render();
  }

  async fetchLogs() {
    const term = this.container.querySelector('#testbed-terminal-body');
    if (!term) return;
    try {
      const res = await this.api.getTestbedLogs(this.activeLogComponent, 150);
      term.textContent = res.logs || `(Empty log for ${this.activeLogComponent})`;
    } catch (e) {
      term.textContent = `Error reading ${this.activeLogComponent} logs: ${e.message}`;
    }
  }

  open() {
    if (this.isOpen) return;
    // Mantis shares this edge; the shell decides who gets it.
    window.workbenchApp?.claimRightEdge?.(this);
    this.isOpen = true;
    const drawer = this.container.querySelector('#local-setup-drawer');
    drawer?.classList.add('open');
    localStorage.setItem('udmi_testbed_drawer_open', 'true');
    this.fetchLogs();
    this.pollStatus();
    this.onNotify({ action: 'DRAWER_TOGGLED', isOpen: true });
  }

  close() {
    if (!this.isOpen) return;
    this.isOpen = false;
    const drawer = this.container.querySelector('#local-setup-drawer');
    drawer?.classList.remove('open');
    localStorage.setItem('udmi_testbed_drawer_open', 'false');
    this.onNotify({ action: 'DRAWER_TOGGLED', isOpen: false });
  }

  toggle() {
    if (this.isOpen) this.close();
    else this.open();
  }

  setAlert(text, isError = false) {
    const alertBox = this.container.querySelector('#drawer-alert');
    const alertText = this.container.querySelector('#drawer-alert-text');
    if (!alertBox || !alertText) return;
    if (!text) {
      alertBox.hidden = true;
      return;
    }
    alertText.textContent = text;
    alertBox.classList.toggle('error', isError);
    alertBox.hidden = false;
  }

  async handleStart() {
    await this._bringUpSubstrate({
      verb: 'start',
      launch: (siteModel, projectSpec) =>
        this.api.startTestbed({ siteModel, projectSpec, clean: false }),
    });
  }

  async handleStop() {
    this.setAlert('Stopping local services and Pubber...');
    try {
      await this.api.stopTestbed(this.localSpec);
      this.pollStatus();
      setTimeout(() => this.setAlert(''), 3000);
    } catch (e) {
      this.setAlert(`Stop failed: ${e.message}`, true);
    }
  }

  async handleRestart() {
    await this._bringUpSubstrate({
      verb: 'restart',
      launch: (siteModel, projectSpec) => this.api.restartTestbed({ siteModel, projectSpec }),
    });
  }

  /**
   * Starts (or restarts) the substrate, waits for `overall === 'UP'`, and only
   * then launches Pubber. The spec is sent exactly as entered: the backend
   * requires an explicit `//mqtt/localhost:<port>` and rejects anything else
   * with an actionable message, so no default is substituted here.
   */
  async _bringUpSubstrate({ verb, launch }) {
    const state = this.store.getState();
    if (!state.siteModel) {
      this.setAlert('Please select a Site Model in the Sequencer controls first.', true);
      return;
    }
    const projectSpec = this.localSpec;
    const label = verb === 'restart' ? 'Restart' : 'Startup';
    if (!projectSpec) {
      this.setAlert(`${label} needs a local project spec: enter //mqtt/localhost:<port> above.`, true);
      return;
    }
    this.setAlert(`Local substrate ${verb} requested (Mosquitto, UDMIS, etcd)...`);
    this.activeLogComponent = 'setup';

    try {
      await launch(state.siteModel, projectSpec);
    } catch (e) {
      this.setAlert(`${label} failed: ${e.message}`, true);
      return;
    }

    this.fetchLogs();
    this.setAlert(`Waiting up to ${SUBSTRATE_READY_TIMEOUT_MS / 1000}s for the local substrate to report UP...`);
    try {
      const status = await waitForSubstrateUp({
        getStatus: async () => {
          const s = await this.api.getTestbedStatus(projectSpec);
          this.store.update('SET_TESTBED_STATUS', { testbedStatus: s }, { silent: true });
          this.render();
          return s;
        },
      });
      this.store.update('SET_TESTBED_STATUS', { testbedStatus: status }, { silent: true });
    } catch (e) {
      this.fetchLogs();
      this.setAlert(`${label} failed: ${e.message}`, true);
      return;
    }

    const isPubberMode = state.pubberMode === true;
    if (isPubberMode && state.deviceId) {
      this.setAlert(`Substrate UP. Launching Pubber for device ${state.deviceId}...`);
      try {
        await this.api.startPubber({
          siteModel: state.siteModel,
          deviceId: state.deviceId,
          projectSpec,
          serialNo: state.serialNo || '1234',
        });
        this.setAlert(`Local substrate UP and Pubber emulator (${state.deviceId}) launched.`);
      } catch (pubErr) {
        this.setAlert(`Substrate UP, but Pubber launch failed: ${pubErr.message}`, true);
        return;
      }
    } else if (isPubberMode) {
      this.setAlert('Local substrate UP. Select a device in Sequencer to launch Pubber.');
    } else {
      this.setAlert('Local substrate UP (Physical Device mode; the device itself is not monitored).');
    }

    this.fetchLogs();
    this.pollStatus();
    setTimeout(() => this.setAlert(''), 5000);
  }

  render() {
    const state = this.store.getState();
    const testbed = state.testbedStatus || {};
    const overall = testbed.overall || 'DOWN';
    const components = testbed.components || {};

    // Header Status
    const dot = this.container.querySelector('#drawer-header-dot');
    const statusText = this.container.querySelector('#drawer-header-status-text');
    if (dot && statusText) {
      dot.className = `status-dot dot-${overall.toLowerCase()}`;
      statusText.textContent = overall;
    }

    // Spec tag, input hint, and actions all follow the drawer's own spec.
    const specTag = this.container.querySelector('#drawer-spec-tag');
    if (specTag) specTag.textContent = this.localSpec || 'No local spec set';
    const hint = this.container.querySelector('#drawer-spec-hint');
    if (hint) {
      hint.textContent = !this.localSpec
        ? 'Required: //mqtt/localhost:<port> (explicit unprivileged port).'
        : testbed.spec_error || '';
    }
    const mismatch = this.container.querySelector('#drawer-spec-mismatch');
    if (mismatch) {
      const text = specMismatch(state.projectSpec, this.localSpec);
      mismatch.textContent = text;
      mismatch.hidden = !text;
    }
    for (const id of ['#btn-testbed-start', '#btn-testbed-stop', '#btn-testbed-restart']) {
      const btn = this.container.querySelector(id);
      if (btn) btn.disabled = !this.localSpec;
    }

    // Render Topology Canvas
    this.renderTopology(state, components);
    this.renderConnection(state);
  }

  renderTopology(state, components) {
    const canvas = this.container.querySelector('#topology-canvas');
    if (!canvas) return;

    const dutDev = state.deviceId || '(No Device)';
    const isPubber = state.pubberMode === true;
    const pubberComp = components.pubber || {};
    // Ports come only from the status of the drawer's spec; absent means unknown.
    const mqttComp = components.mqtt_broker || { status: 'UNKNOWN' };
    const udmisComp = components.udmis || { status: 'UNKNOWN' };
    const etcdComp = components.etcd || { status: 'UNKNOWN' };

    const getBadge = healthBadge;
    const { subtitle: dutSubtitle, badge: dutBadge } = describeDut({ isPubber, pubberComp });

    canvas.innerHTML = `
      <div class="topology-stack">
        <!-- Node 1: DUT / Device with inline Pubber toggle -->
        <div class="topology-card ${state.deviceId ? 'active-device' : 'empty-device'}">
          <div class="node-icon-box">
            <span class="material-symbols-outlined">${isPubber ? 'smart_toy' : 'home_iot_device'}</span>
          </div>
          <div class="node-info">
            <div class="node-title">${dutDev}</div>
            <div class="node-subtitle">${dutSubtitle}</div>
          </div>
          <div class="node-controls-group">
            <div class="pubber-switch-container" title="Toggle Pubber Simulation vs Physical Hardware">
              <label class="m3-switch" title="Toggle Pubber simulation">
                <input type="checkbox" id="toggle-pubber-mode" ${isPubber ? 'checked' : ''} />
                <span class="m3-switch-track">
                  <span class="m3-switch-thumb"></span>
                </span>
              </label>
              <span class="mode-text-sm" id="pubber-mode-text">${isPubber ? 'Pubber' : 'Physical'}</span>
            </div>
            <div class="node-status">
              ${dutBadge}
            </div>
          </div>
        </div>

        <!-- Connection 1 -->
        <div class="topology-flow-edge">
          <div class="flow-line"></div>
          <span class="flow-pill">MQTT :${mqttComp.port ?? '—'} (Telemetry & State)</span>
          <span class="flow-arrow">▼</span>
        </div>

        <!-- Node 2: Mosquitto Broker -->
        <div class="topology-card">
          <div class="node-icon-box">
            <span class="material-symbols-outlined">cell_tower</span>
          </div>
          <div class="node-info">
            <div class="node-title">Local Mosquitto</div>
            <div class="node-subtitle">Port ${mqttComp.port ?? '—'} (Isolated User Mode)</div>
          </div>
          <div class="node-status">
            ${getBadge(mqttComp.status)}
          </div>
        </div>

        <!-- Connection 2 -->
        <div class="topology-flow-edge">
          <div class="flow-line"></div>
          <span class="flow-pill">Reflective Message Sync</span>
          <span class="flow-arrow">▼</span>
        </div>

        <!-- Node 3: UDMIS Pod -->
        <div class="topology-card">
          <div class="node-icon-box">
            <span class="material-symbols-outlined">dns</span>
          </div>
          <div class="node-info">
            <div class="node-title">Local UDMIS Pod</div>
            <div class="node-subtitle">Message Ingest & Schema Validation</div>
          </div>
          <div class="node-status">
            ${getBadge(udmisComp.status)}
          </div>
        </div>

        <!-- Connection 3 -->
        <div class="topology-flow-edge">
          <div class="flow-line"></div>
          <span class="flow-pill">KV Device State & Metadata</span>
          <span class="flow-arrow">▼</span>
        </div>

        <!-- Node 4: etcd State Store -->
        <div class="topology-card">
          <div class="node-icon-box">
            <span class="material-symbols-outlined">database</span>
          </div>
          <div class="node-info">
            <div class="node-title">etcd State Store</div>
            <div class="node-subtitle">Port ${etcdComp.port ?? '—'}</div>
          </div>
          <div class="node-status">
            ${getBadge(etcdComp.status)}
          </div>
        </div>
      </div>
    `;
  }

  /**
   * Physical mode only: what an external device needs to reach this broker.
   * Fetched once per (spec, site, device) and rendered from the backend's
   * derived facts; nothing here is filled in client-side.
   */
  renderConnection(state) {
    const host = this.container.querySelector('#drawer-connection-card');
    if (!host) return;
    if (state.pubberMode === true) {
      host.innerHTML = '';
      return;
    }
    let reason = '';
    if (!this.localSpec) reason = 'Enter a local project spec to see how an external device connects.';
    else if (!state.siteModel || !state.deviceId) reason = 'Select a site model and device to see its connection details.';
    if (reason) {
      host.innerHTML = renderConnectionCard({ reason });
      return;
    }
    const key = `${this.localSpec}|${state.siteModel}|${state.deviceId}`;
    if (this.connection.key !== key) {
      this.connection = { key, data: null, error: null, loading: true };
      this.api
        .getTestbedConnection({ projectSpec: this.localSpec, siteModel: state.siteModel, deviceId: state.deviceId })
        .then((data) => { if (this.connection.key === key) this.connection = { key, data, error: null, loading: false }; })
        .catch((e) => { if (this.connection.key === key) this.connection = { key, data: null, error: e.message, loading: false }; })
        .finally(() => this.renderConnection(this.store.getState()));
    }
    host.innerHTML = renderConnectionCard(this.connection);
  }
}
