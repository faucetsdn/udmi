/**
 * Layer 2 — Workspace state store.
 *
 * Holds user selections, persists the tedious ones to localStorage, and mirrors
 * the addressable ones into the URL so any view is shareable and reloadable.
 * v1 persisted almost nothing and had no deep links at all.
 */

import { logger } from './logger.js';

const STORAGE_KEY = 'udmi_workbench_state_v3';

/** Only these keys survive a reload; volatile run state deliberately does not. */
const PERSISTED_KEYS = [
  'siteModel',
  'deviceId',
  'projectSpec',
  'logLevel',
  'minStage',
  'serialNo',
  'selectedTests',
  'bucketFilter',
  'logPanelHeight',
  'localSetupDrawerWidth',
  'mantisDrawerWidth',
];

/** These keys are mirrored into the query string for deep linking. */
const URL_KEYS = ['siteModel', 'deviceId'];

const DEFAULT_STATE = {
  siteModel: '',
  deviceId: '',
  projectSpec: '',
  logLevel: 'INFO',
  minStage: 'PREVIEW',
  serialNo: '',
  selectedTests: [],
  bucketFilter: '',
  searchQuery: '',

  // Panel geometry, in pixels. These seed --log-panel-height and
  // --local-setup-drawer-width; keep them in step with theme.css :root.
  logPanelHeight: 220,
  localSetupDrawerWidth: 440,
  // The Mantis drawer's own default is the responsive fallback in drawers.css,
  // which a fixed pixel token cannot express; this is the pixel value used once
  // the operator has dragged it.
  mantisDrawerWidth: 760,

  siteModels: [],
  devices: [],
  sequences: [],
  results: {},
  resultsDirExists: false,

  stagesAdmitted: [],

  sessionId: null,
  running: false,
  startedAt: null,
  elapsedDuration: '00:00',
  exitCode: null,
  statusLabel: 'Idle',
  testStatus: {},
  commandLine: '',
  activeTestId: '',
  consoleLogs: [],

  // Testbed & Local Setup State
  testbedDrawerOpen: false,
  testbedStatus: {
    overall: 'DOWN',
    components: {
      mqtt_broker: { name: 'Local Mosquitto Broker', status: 'DOWN', port: 18833 },
      udmis: { name: 'Local UDMIS Pod', status: 'DOWN' },
      etcd: { name: 'etcd State Store', status: 'DOWN', port: 2379 },
      pubber: { name: 'Pubber Emulator', status: 'DOWN' },
    },
  },
  pubberMode: false,
};

export class WorkspaceStore {
  constructor() {
    this.state = { ...DEFAULT_STATE };
    this._subscribers = new Set();
    this._restore();
  }

  getState() {
    return this.state;
  }

  subscribe(listener) {
    this._subscribers.add(listener);
    return () => this._subscribers.delete(listener);
  }

  /** Applies a patch, persists, syncs the URL, and notifies subscribers. */
  update(action, patch, { silent = false } = {}) {
    const changedKeys = Object.keys(patch).filter(
      (key) => this.state[key] !== patch[key]
    );
    if (changedKeys.length === 0) return;

    this.state = { ...this.state, ...patch };

    if (!silent) {
      logger.info('WorkspaceStore', 'store.mutation', {
        layer: 'STORE',
        details: { action, changed: changedKeys },
      });
    }
    this._persist();
    this._syncUrl();
    this._notify();
  }

  /** Records a per-test lifecycle transition without rebuilding other state. */
  setTestStatus(testName, status) {
    if (this.state.testStatus[testName] === status) return;
    this.state.testStatus = { ...this.state.testStatus, [testName]: status };
    this._notify();
  }

  resetRun(selectedTests) {
    const queued = {};
    for (const name of selectedTests) queued[name] = 'queued';
    this.update('run.reset', {
      testStatus: queued,
      exitCode: null,
      statusLabel: 'Running',
      running: true,
      startedAt: Date.now(),
    });
  }

  /** Aggregates counters for the current run, isolating live run state from historical disk results. */
  computeMetrics() {
    const { selectedTests, testStatus, results, running } = this.state;
    const counts = { pass: 0, fail: 0, skip: 0, pending: 0 };
    const hasLiveRun = running || Object.keys(testStatus).length > 0;

    for (const name of selectedTests) {
      const status = hasLiveRun ? testStatus[name] : null;
      if (status === 'pass') counts.pass += 1;
      else if (status === 'fail') counts.fail += 1;
      else if (status === 'skip') counts.skip += 1;
      else counts.pending += 1;
    }
    const total = selectedTests.length;
    const settled = counts.pass + counts.fail + counts.skip;

    // Historical counts for informative secondary display
    let histPass = 0, histFail = 0, histSkip = 0;
    for (const name of selectedTests) {
      const s = results[name]?.status;
      if (s === 'pass') histPass += 1;
      else if (s === 'fail') histFail += 1;
      else if (s === 'skip') histSkip += 1;
    }

    return {
      ...counts,
      total,
      percent: total ? Math.round((settled / total) * 100) : 0,
      hasLiveRun,
      historical: { pass: histPass, fail: histFail, skip: histSkip, total: histPass + histFail + histSkip },
    };
  }

  _persist() {
    const snapshot = {};
    for (const key of PERSISTED_KEYS) snapshot[key] = this.state[key];
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
    } catch (cause) {
      logger.warn('WorkspaceStore', 'persist.failed', {
        layer: 'STORE',
        details: { reason: cause.message },
      });
    }
  }

  _restore() {
    try {
      const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
      for (const key of PERSISTED_KEYS) {
        if (stored[key] !== undefined) this.state[key] = stored[key];
      }
    } catch {
      /* corrupt storage is ignored in favour of defaults */
    }

    const params = new URLSearchParams(window.location.search);
    for (const key of URL_KEYS) {
      const param = key === 'siteModel' ? 'site_model' : 'device';
      const value = params.get(param);
      if (value) this.state[key] = value;
    }
  }

  _syncUrl() {
    const params = new URLSearchParams(window.location.search);
    for (const key of URL_KEYS) {
      const param = key === 'siteModel' ? 'site_model' : 'device';
      if (this.state[key]) params.set(param, this.state[key]);
      else params.delete(param);
    }
    const query = params.toString();
    const next = `${window.location.pathname}${query ? `?${query}` : ''}`;
    window.history.replaceState({}, '', next);
  }

  _notify() {
    for (const listener of this._subscribers) listener(this.state);
  }
}

export const store = new WorkspaceStore();
