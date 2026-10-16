/**
 * Layer 2 — Sequencer workspace view.
 *
 * Orchestrates discovery, selection, execution, and streaming for
 * `bin/sequencer`. This is the only module that knows how the sequencer screen
 * fits together; the components below it stay presentational and the API layer
 * above it owns all I/O.
 */

import { api } from '../core/api.js';
import { desktopNotify } from '../core/desktop-notify.js';
import { DiscoveryLoader } from '../core/discovery-loader.js';
import { RunSession } from '../core/run-session.js';
import { runVerdict } from '../core/run-verdict.js';
import { StageGate } from '../core/stage-gate.js';
import { store } from '../core/store.js';
import { ArtifactModal } from '../components/artifact-modal.js';
import { LocalSetupDrawer } from '../components/local-setup-drawer.js';
import { LogViewer } from '../components/log-viewer.js';
import { RunControls } from '../components/run-controls.js';
import { RunSummary } from '../components/run-summary.js';
import { attachResizer, clampSize } from '../components/resizer.js';
import { SiteRootsDialog } from '../components/site-roots.js';
import { TestList } from '../components/test-list.js';

const RESULT_TO_STATUS = { pass: 'pass', fail: 'fail', skip: 'skip' };

/** Toolbar plus a few readable lines; below this the console tells you nothing. */
const LOG_PANEL_MIN_PX = 120;
/** Vertical space the sequence list must keep, so the log can never swallow the workspace. */
const TESTS_PANEL_FLOOR_PX = 240;

export class SequencerView {
  constructor(root) {
    this.root = root;
    this.stageGate = new StageGate();
    this.render();
    this.discovery = new DiscoveryLoader({
      onNotice: (message, level) => this.appendNotice(message, level),
    });
    this.session = new RunSession({
      onLog: (text) => this.appendLog(text),
      onTestEvent: (event) => this.handleTestEvent(event),
      onComplete: (outcome) => this.finishRun(outcome),
      onError: (message) => this.appendNotice(message, 'error'),
      onNotice: (message, level) => this.appendNotice(message, level),
    });
  }

  render() {
    this.root.innerHTML = `
      <div class="workspace">
        <aside class="panel panel-config">
          <h2 class="panel-title">Run configuration</h2>
          <div data-mount="controls"></div>
          <div data-mount="summary" class="summary"></div>
        </aside>

        <section class="panel panel-tests">
          <div class="panel-toolbar">
            <h2 class="panel-title">Sequences <span class="count" data-role="count"></span></h2>
            <input type="search" data-role="search" placeholder="Filter sequences..."
                   aria-label="Filter sequences" />
            <select data-role="bucket" aria-label="Filter by feature bucket"></select>
            <button type="button" class="btn btn-sm btn-ghost testbed-toggle-btn" id="btn-toggle-testbed" title="Toggle Local Test Setup Drawer (Cmd+Shift+L)">
              <span class="status-dot dot-down" id="testbed-indicator-dot"></span>
              <span class="material-symbols-outlined icon-inline" style="font-size:16px;">dns</span>
              <span>Start Local Setup</span>
            </button>
          </div>
          <div class="bulk-actions" role="group" aria-label="Bulk selection and filters">
            <button type="button" class="btn btn-ghost" data-bulk="all">Select all</button>
            <button type="button" class="btn btn-ghost" data-bulk="none">Clear</button>
            <span class="bulk-divider"></span>
            <button type="button" class="btn btn-ghost" data-bulk="pass" title="Select sequences that previously passed">&#10003; Passed</button>
            <button type="button" class="btn btn-ghost" data-bulk="skip" title="Select sequences that were previously skipped">&#8211; Skipped</button>
            <button type="button" class="btn btn-ghost" data-bulk="fail" title="Select sequences that previously failed">&#10007; Failed</button>
            <span class="bulk-divider"></span>
            <select data-role="stage" class="stage-filter-select" aria-label="Filter by minimum stage"></select>
          </div>
          <div class="test-list" data-mount="tests"></div>
        </section>

        <section class="panel panel-logs" data-mount="logs"></section>
      </div>
      <div data-mount="modal"></div>
      <div data-mount="site-roots"></div>
      <div data-mount="testbed-drawer"></div>
    `;

    const mount = (name) => this.root.querySelector(`[data-mount="${name}"]`);

    this.controls = new RunControls(mount('controls'), {
      onChange: (key, value) => this.handleChange(key, value),
      onRun: () => this.startRun(),
      onStop: () => this.stopRun(),
      onManagePaths: () => this.siteRoots.open(),
    });
    this.summary = new RunSummary(mount('summary'));
    this.testList = new TestList(mount('tests'), {
      onToggle: (name, checked) => this.toggleTest(name, checked),
      onOpenArtifacts: (name) => this.openArtifacts(name),
      onDiagnose: (name) => this.diagnoseTest(name),
      onExplain: (name) => this.explainTest(name),
    });
    this.logs = new LogViewer(mount('logs'), {
      initialLines: store.getState().consoleLogs || [],
    });
    // LogViewer owns the panel's innerHTML, so the handle is added afterwards.
    this.mountLogResizer(mount('logs'));
    this.modal = new ArtifactModal(mount('modal'), {
      loadFile: (path) => api.readFile(path),
      onDiagnose: (name) => this.diagnoseTest(name),
    });
    this.siteRoots = new SiteRootsDialog(mount('site-roots'), {
      loadRoots: () => api.listSiteRoots(),
      addRoot: (path) => api.addSiteRoot(path),
      removeRoot: (path) => api.removeSiteRoot(path),
      onChange: () => this.reloadSiteModels(),
    });
    this.localSetupDrawer = new LocalSetupDrawer(mount('testbed-drawer'), {
      store,
      api,
      onNotify: ({ action, isOpen }) => {
        this.root.querySelector('.workspace')?.classList.toggle('drawer-open', isOpen);
      },
    });

    const btnToggleTestbed = this.root.querySelector('#btn-toggle-testbed');
    btnToggleTestbed?.addEventListener('click', () => {
      this.localSetupDrawer.toggle();
    });

    this.searchInput = this.root.querySelector('[data-role="search"]');
    this.bucketSelect = this.root.querySelector('[data-role="bucket"]');
    this.stageSelect = this.root.querySelector('[data-role="stage"]');
    this.countEl = this.root.querySelector('[data-role="count"]');

    this.searchInput.addEventListener('input', () => {
      store.update('filter.search', { searchQuery: this.searchInput.value }, { silent: true });
      this.applyFilters();
    });
    this.bucketSelect.addEventListener('change', () => {
      store.update('filter.bucket', { bucketFilter: this.bucketSelect.value });
      this.applyFilters();
    });
    this.stageSelect.addEventListener('change', () => {
      const value = this.stageSelect.value;
      store.update('filter.stage', { minStage: value });
      this.stageGate.apply(value);
      this.applyFilters();
    });
    for (const button of this.root.querySelectorAll('[data-bulk]')) {
      button.addEventListener('click', () => this.bulkSelect(button.dataset.bulk));
    }

    this.runTimer = null;
    this.unsubscribe = store.subscribe((state) => this.syncFromStore(state));
  }

  destroy() {
    if (this.runTimer) {
      clearInterval(this.runTimer);
      this.runTimer = null;
    }
    this.unsubscribe?.();
    this.session.abort();
    this.detachLogResizer?.();
    this.localSetupDrawer?.stopPolling();
  }

  // --------------------------------------------------------- log resizing ---
  /**
   * Bounds are recomputed from the live viewport rather than stored, so a
   * height persisted on a taller monitor cannot leave the sequence list
   * squeezed off-screen after a reload.
   */
  logPanelBounds() {
    const available = window.innerHeight
      - parseInt(getComputedStyle(document.documentElement).getPropertyValue('--app-bar-height'), 10)
      - TESTS_PANEL_FLOOR_PX;
    return { min: LOG_PANEL_MIN_PX, max: Math.max(LOG_PANEL_MIN_PX, available) };
  }

  mountLogResizer(panel) {
    const { min, max } = this.logPanelBounds();
    // The persisted value is clamped before it is ever painted.
    const height = clampSize(store.getState().logPanelHeight, min, max);
    this.applyLogPanelHeight(height);

    panel.insertAdjacentHTML('afterbegin', '<div class="resize-handle resize-handle-y"></div>');
    this.detachLogResizer = attachResizer(panel.firstElementChild, {
      axis: 'y',
      // The panel is anchored to the bottom of the grid, so its top edge moving
      // up is what makes it taller.
      direction: -1,
      label: 'Resize log console',
      min,
      max,
      value: height,
      onResize: (target) => this.applyLogPanelHeight(clampSize(target, min, max)),
      onCommit: (applied) => store.update('layout.logPanelHeight', { logPanelHeight: applied }),
    });
  }

  applyLogPanelHeight(height) {
    document.documentElement.style.setProperty('--log-panel-height', `${Math.round(height)}px`);
    return height;
  }

  // ------------------------------------------------------------ bootstrap ---
  async init() {
    try {
      const { siteModels, sequences, sequencesError, options } = await this.discovery.loadCatalog();
      this.stageGate.setOptions(options.min_stages);
      this.controls.setSiteModels(siteModels);
      this.controls.setRunOptions(options);
      this.setStageOptions(options.min_stages);
      this.setSequences(sequences);
      if (sequencesError) {
        this.testList.setError(sequencesError);
        this.logs.appendNotice(`Sequence catalog unavailable: ${sequencesError}`, 'error');
      }
      this.stageGate.apply(store.getState().minStage);

      const { siteModel } = store.getState();
      if (this.discovery.validateSiteModel(siteModel)) {
        await this.loadDevices(siteModel);
      }
      await this.recoverActiveSession();
    } catch (cause) {
      this.logs.appendNotice(cause.message, 'error');
    }
    this.syncFromStore(store.getState());
  }

  setStageOptions(minStages) {
    this.stageSelect.textContent = '';
    for (const option of minStages || []) {
      const el = document.createElement('option');
      el.value = option.value;
      el.textContent = option.label || option.value;
      this.stageSelect.appendChild(el);
    }
  }

  /**
   * Re-reads the site model list after a path is registered or removed. A
   * selection that just became unreachable is cleared here, because leaving it
   * in place would let a run be launched against a directory the server is no
   * longer permitted to read.
   */
  async reloadSiteModels() {
    try {
      const { siteModels } = await this.discovery.loadCatalog();
      this.controls.setSiteModels(siteModels);

      const { siteModel } = store.getState();
      if (siteModel && this.discovery.validateSiteModel(siteModel)) {
        await this.loadDevices(siteModel);
      } else if (siteModel) {
        this.controls.setDevices([]);
      }
    } catch (cause) {
      this.logs.appendNotice(cause.message, 'error');
    }
    this.syncFromStore(store.getState());
  }

  setSequences(sequences) {
    this.sequences = sequences;
    this.stageGate.setSequences(sequences);
    this.testList.setSequences(sequences);

    const buckets = [...new Set(sequences.map((s) => s.bucket || 'uncategorised'))].sort();
    this.bucketSelect.textContent = '';
    const all = document.createElement('option');
    all.value = '';
    all.textContent = `All buckets (${buckets.length})`;
    this.bucketSelect.appendChild(all);
    for (const bucket of buckets) {
      const option = document.createElement('option');
      option.value = bucket;
      option.textContent = bucket;
      this.bucketSelect.appendChild(option);
    }

    const state = store.getState();
    this.searchInput.value = state.searchQuery;
    this.bucketSelect.value = state.bucketFilter;
    this.applyFilters();
  }

  /** Reattaches to a run still in flight after a page reload. */
  async recoverActiveSession() {
    const active = await this.session.recover();
    if (!active) return;

    store.update('run.recover', {
      deviceId: active.device_id,
      sessionId: active.session_id,
      running: true,
      statusLabel: 'Running',
      commandLine: active.command_line,
    });
    this.logs.appendNotice(`Reattached to running session ${active.session_id}`, 'notice');
  }


  // ------------------------------------------------------------ selection ---
  handleChange(key, value) {
    store.update(`control.${key}`, { [key]: value });
    if (key === 'siteModel') {
      // The spec targeted the previous site's provider/project; carrying it
      // over would silently run the new site against the old destination.
      // loadDevices() re-populates the suggestions for the new site.
      store.update('site.change', { deviceId: '', projectSpec: '', results: {}, testStatus: {} });
      this.loadDevices(value);
    } else if (key === 'deviceId') {
      this.loadResults();
    } else if (key === 'minStage') {
      this.stageGate.apply(value);
    }
  }

  async loadDevices(siteModel) {
    const devices = await this.discovery.loadDevices(siteModel);
    this.controls.setDevices(devices);
    this.refreshProjectSuggestions();

    const state = store.getState();
    if (state.deviceId && devices.some((device) => device.device_id === state.deviceId)) {
      this.controls.syncFrom(state);
      await this.loadResults();
    }
  }

  async loadResults({ relabel = true } = {}) {
    const { siteModel, deviceId } = store.getState();
    await this.discovery.loadResults(siteModel, deviceId, { relabel });
    this.refreshProjectSuggestions();
  }

  refreshProjectSuggestions() {
    const { siteModel } = store.getState();
    this.controls.setProjectSpecSuggestions(this.discovery.projectSpecSuggestions(siteModel));
  }

  toggleTest(name, checked) {
    const selected = new Set(store.getState().selectedTests);
    if (checked) selected.add(name);
    else selected.delete(name);
    store.update('tests.toggle', { selectedTests: [...selected] });
    this.applyFilters();
  }

  /**
   * Bulk actions operate only on rows passing the active filters, and never
   * pick up a sequence the minimum stage would stop from running.
   */
  bulkSelect(mode) {
    const visible = this.testList.visibleTestNames()
      .filter((name) => !this.testList.isStageExcluded(name));
    if (mode === 'none') {
      store.update('tests.clear', { selectedTests: [] });
      this.applyFilters();
      return;
    }
    if (mode === 'all') {
      store.update('tests.selectAll', { selectedTests: visible });
      this.applyFilters();
      return;
    }

    const { results, testStatus } = store.getState();
    const matching = visible.filter(
      (name) => (testStatus[name] || results[name]?.status) === mode
    );
    store.update('tests.selectByStatus', { selectedTests: matching });
    this.applyFilters();
  }

  applyFilters() {
    const { searchQuery, bucketFilter } = store.getState();
    this.testList.applyFilters(searchQuery, bucketFilter);
    const visible = this.testList.visibleTestNames().length;
    this.countEl.textContent = `${visible} of ${this.sequences?.length ?? 0}`;
  }

  // ------------------------------------------------------------ execution ---
  async startRun() {
    const state = this.stageGate.state();
    const problem = RunControls.validate(state);
    if (problem) {
      this.logs.appendNotice(problem, 'error');
      return;
    }
    // Still inside the Run click, the only moment a browser shows the prompt.
    desktopNotify.requestFromGesture();

    store.resetRun(state.selectedTests);
    if (this.runTimer) clearInterval(this.runTimer);
    this.runTimer = setInterval(() => {
      const s = store.getState();
      if (s.running && s.startedAt) {
        const sec = Math.floor((Date.now() - s.startedAt) / 1000);
        const mm = String(Math.floor(sec / 60)).padStart(2, '0');
        const ss = String(sec % 60).padStart(2, '0');
        this.summary.setTime(`${mm}:${ss}`);
      }
    }, 1000);

    const notify = this.controls.takeNotifyRequest();
    try {
      const started = await this.session.start(state, { notify });
      store.update('run.started', {
        sessionId: started.session_id,
        commandLine: started.command_line,
        startedAt: Date.now(),
        elapsedDuration: '00:00',
      });
      this.logs.clear();
      this.logs.appendNotice(`$ ${started.command_line}`, 'notice');
      if (started.notify) {
        this.logs.appendNotice(
          'You will get an email when this run finishes, even if you close this tab. ' +
          'Stopping the run sends nothing.',
          'notice'
        );
      }
    } catch (cause) {
      if (this.runTimer) {
        clearInterval(this.runTimer);
        this.runTimer = null;
      }
      // 409: the server refused because another session is still running
      // (bin/sequencer's shared files make concurrent runs unsafe). Nothing
      // was launched, so this is 'Blocked', not a failed run.
      const blocked = cause.status === 409;
      store.update('run.failed', { running: false, statusLabel: blocked ? 'Blocked' : 'Failed' });
      this.logs.appendNotice(
        blocked ? `Run not started — ${cause.message}` : cause.message,
        'error'
      );
    }
  }

  handleTestEvent(event) {
    if (event.type === 'started') {
      store.setTestStatus(event.test, 'running');
      this.testList.revealTest(event.test);
    } else if (event.type === 'result') {
      const status = RESULT_TO_STATUS[event.result] || 'unknown';
      store.setTestStatus(event.test, status);
    }
  }

  /**
   * Settles the run. Sequences that never reported stay pending — v1 rewrote
   * them to 'fail', manufacturing failures that never happened.
   */
  finishRun({ exitCode = null, aborted = false } = {}) {
    if (this.runTimer) {
      clearInterval(this.runTimer);
      this.runTimer = null;
    }
    const s = store.getState();
    let finalDuration = s.elapsedDuration || '00:00';
    if (s.startedAt) {
      const sec = Math.floor((Date.now() - s.startedAt) / 1000);
      const mm = String(Math.floor(sec / 60)).padStart(2, '0');
      const ss = String(sec % 60).padStart(2, '0');
      finalDuration = `${mm}:${ss}`;
      this.summary.setTime(finalDuration);
    }

    const metrics = store.computeMetrics();
    const label = runVerdict({ exitCode, aborted, metrics });

    store.update('run.finished', {
      running: false,
      sessionId: null,
      exitCode,
      statusLabel: label,
      elapsedDuration: finalDuration,
    });
    this.logs.appendNotice(
      `Run ${label.toLowerCase()} — exit code ${exitCode ?? 'n/a'}; ` +
        `${metrics.pass} passed, ${metrics.fail} failed, ${metrics.skip} skipped, ` +
        `${metrics.pending} never reported (${finalDuration} elapsed).`,
      ['Aborted', 'Incomplete', 'Error'].includes(label) ? 'warn' : 'notice'
    );
    desktopNotify.notify({
      title: `Sequencer run ${label.toLowerCase()}`,
      body:
        `${s.deviceId || 'Device'}: ${metrics.pass} passed, ${metrics.fail} failed, ` +
        `${metrics.skip} skipped, ${metrics.pending} never reported (${finalDuration}).`,
      tag: 'workbench-sequencer-run',
    });
    // Refresh the on-disk artifacts this run just produced, but keep the
    // verdict above: relabelling here would replace it with 'Historical runs'
    // the instant the run finished.
    this.loadResults({ relabel: false });
  }

  async stopRun() {
    const { sessionId } = store.getState();
    if (!sessionId) return;
    try {
      await this.session.stop(sessionId);
    } catch (cause) {
      this.logs.appendNotice(cause.message, 'error');
    }
  }

  // ------------------------------------------------------------ artifacts ---
  openArtifacts(testName) {
    const record = store.getState().results[testName];
    if (!record) return;
    this.modal.open({
      testName,
      artifactDir: record.artifact_dir,
      artifacts: record.artifacts,
      summary: record.summary,
    });
  }

  appendLog(text) {
    this.logs.appendChunk(text);
    store.state.consoleLogs = this.logs.getLines();
  }

  appendNotice(text, level = 'notice') {
    this.logs.appendNotice(text, level);
    store.state.consoleLogs = this.logs.getLines();
  }

  onActivate() {
    this.syncFromStore();
    this.localSetupDrawer?.render();
  }

  diagnoseTest(testName) {
    store.update('triage.select', { activeTestId: testName });
    // Triage used to navigate to a separate Assistant tab, which hid the run
    // being diagnosed. The drawer overlays this view instead.
    window.workbenchApp?.mantis?.openTriage({
      siteModel: store.state.siteModel,
      deviceId: store.state.deviceId,
      testId: testName,
    });
  }

  /** Informational Mantis question about any test, failed or not. */
  explainTest(testName) {
    const mantis = window.workbenchApp?.mantis;
    if (!mantis) {
      this.appendNotice('Cannot explain test: the Mantis drawer is not mounted.', 'error');
      return;
    }
    mantis.explainTest(testName);
  }

  // ----------------------------------------------------------- reconcile ---
  syncFromStore() {
    const state = this.stageGate.state();
    if (this.stageSelect && this.stageSelect.value !== state.minStage) {
      this.stageSelect.value = state.minStage;
    }
    if (this.stageSelect) {
      this.stageSelect.disabled = state.running;
    }
    this.controls.syncFrom(state);
    this.testList.setSelection(state.selectedTests);
    this.testList.setDisabled(state.running);
    this.summary.update(state, store.computeMetrics());

    const admitted = state.stagesAdmitted || [];
    for (const sequence of this.sequences ?? []) {
      const recorded = state.results[sequence.name] || null;
      const status = state.testStatus[sequence.name] || recorded?.status || 'unknown';
      this.testList.setStatus(sequence.name, status, recorded);

      this.testList.setStageExcluded(
        sequence.name,
        this.stageGate.excludes(sequence.stage, admitted),
        state.minStage
      );
    }

    const testbedStatus = store.getState().testbedStatus;
    const testbedDot = this.root.querySelector('#testbed-indicator-dot');
    if (testbedDot && testbedStatus) {
      const overall = (testbedStatus.overall || 'down').toLowerCase();
      testbedDot.className = `status-dot dot-${overall}`;
    }

    this.applyFilters();
  }
}

