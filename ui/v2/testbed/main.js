// ==========================================================================
// TESTBED INTERACTIVE TOPOLOGY, COMPLIANCE MATRIX & LIVE LOG ANALYZER
// ==========================================================================
import { stateStore } from '../shared/state-store.js?v=3.0';
import { LogViewer } from '../shared/components/log-viewer.js?v=3.0';
import { NotificationManager } from '../shared/components/notification-toast.js?v=3.0';

// Dynamic Sequencer Test Catalog (loaded live from docs/specs/sequences/generated.md)
export let SEQUENCER_TEST_CATALOG = [];

export class TestbedGraphController {
  constructor() {
    this.siteModel = stateStore.get('siteModel') || '';
    // Explicit unprivileged port 18833 automatically triggers isolated mode in shell_common.sh without sudo
    this.projectSpec = stateStore.get('projectSpec') || '//mqtt/localhost:18833';

    this.setupMode = null; // 'LOCAL' or 'CLOUD' once chosen by user
    this.activeViewMode = 'canvas'; // 'canvas', 'matrix', or 'logs'
    this.deviceNodes = [];
    this.infraNodes = [];

    this.selectedNodeId = null;
    this.selectedNodeIds = new Set();
    this.draggedNodeId = null;
    this.dragOffset = { x: 0, y: 0 };

    // Subprocess execution state
    this.activePolls = new Map(); // sessionId -> interval
    this.logOffsets = new Map(); // sessionId -> offset
    this.setupLogsInterval = null;
    this.latestSetupSessionId = null;
    this.testTimerInterval = null;
    this.testStartTime = null;

    // Site Model Devices & Execution Modes State
    this.discoveredDevices = [];
    this.deviceMetadata = {}; // deviceId -> { version: string }
    this.deviceConfigs = new Map(); // deviceId -> { enabled: boolean, mode: 'pubber' | 'actual_device' | 'ancillary' }
    this.deviceSearchQuery = '';

    this.initElements();
    this.initComponents();
    this.initEvents();
    this.initStoreListeners();
    this.updateSetupButtons();
    this.renderGraph();
    this.loadSiteModelDevices();
    this.fetchSequencerCatalog();
    setTimeout(() => this.updateTabIndicator(), 50);
    setTimeout(() => this.checkAndRecoverBackgroundJobs(), 1000);
  }

  get nodes() {
    return [...this.deviceNodes, ...this.infraNodes];
  }

  initElements() {
    this.btnDefaultSetup = document.getElementById('btn-default-setup');
    this.btnCloudSetup = document.getElementById('btn-cloud-setup');
    this.btnStopPipeline = document.getElementById('btn-stop-pipeline');
    this.btnViewSetupLogs = document.getElementById('btn-view-setup-logs');

    // Cloud Setup Modal
    this.cloudSetupModal = document.getElementById('cloud-setup-modal');
    this.cloudProjectSpecInput = document.getElementById('cloud-project-spec-input');
    if (this.cloudProjectSpecInput) {
      const cachedCloudSpec = localStorage.getItem('udmi_cloud_project_spec');
      if (cachedCloudSpec) {
        this.cloudProjectSpecInput.value = cachedCloudSpec;
      }
    }
    this.btnCloseCloudModal = document.getElementById('btn-close-cloud-modal');
    this.btnSubmitCloudSetup = document.getElementById('btn-submit-cloud-setup');

    // View toggles & workspaces
    this.btnViewCanvas = document.getElementById('btn-view-canvas');
    this.btnViewMatrix = document.getElementById('btn-view-matrix');
    this.btnViewLogs = document.getElementById('btn-view-logs');
    this.canvasWorkspace = document.getElementById('testbed-canvas-workspace');
    this.matrixWorkspace = document.getElementById('testbed-matrix-workspace');
    this.logsWorkspace = document.getElementById('testbed-logs-workspace');

    // Canvas elements
    this.canvasContainer = document.getElementById('canvas-container');
    this.graphCanvas = document.getElementById('graph-canvas');
    this.canvasSvg = document.getElementById('canvas-svg');
    this.nodesLayer = document.getElementById('nodes-layer');

    // Inspector
    this.inspectorPanel = document.getElementById('inspector-panel');
    this.inspectorTitle = document.getElementById('inspector-title');
    this.inspectorIcon = document.getElementById('inspector-icon');
    this.inspectorBody = document.getElementById('inspector-body');
    this.btnCloseInspector = document.getElementById('btn-close-inspector');
    this.isInspectorOpen = false;

    // Matrix Dashboard Elements
    this.matrixKpiTotal = document.getElementById('matrix-kpi-total');
    this.matrixKpiScore = document.getElementById('matrix-kpi-score');
    this.matrixKpiPassed = document.getElementById('matrix-kpi-passed');
    this.matrixKpiFailed = document.getElementById('matrix-kpi-failed');
    this.matrixTableBody = document.getElementById('matrix-table-body');
    this.btnMatrixRunAll = document.getElementById('btn-matrix-run-all');
    this.btnMatrixStopAll = document.getElementById('btn-matrix-stop-all');

    // Log & Diff Controls
    this.tabLogLive = document.getElementById('tab-log-live');
    this.tabLogDiff = document.getElementById('tab-log-diff');
    this.containerLogLive = document.getElementById('container-log-live');
    this.containerLogDiff = document.getElementById('container-log-diff');
    this.diffControls = document.getElementById('diff-controls');
    this.diffBaselineSelect = document.getElementById('diff-baseline-select');
    this.btnCompareDiff = document.getElementById('btn-compare-diff');
    this.diffViewerBody = document.getElementById('diff-viewer-body');
    this.btnLogsStop = document.getElementById('btn-logs-stop');
    this.suiteStatusBadge = document.getElementById('suite-status');
    this.suiteProgressFill = document.getElementById('suite-progress-fill');
    this.metricPassed = document.getElementById('metric-passed');
    this.metricSkipped = document.getElementById('metric-skipped');
    this.metricFailed = document.getElementById('metric-failed');
    this.metricTime = document.getElementById('metric-time');

    // Git & Email Alert buttons and modals
    this.btnGitSave = document.getElementById('btn-git-save-results');
    this.btnNotifSettings = document.getElementById('btn-notification-settings');
    this.btnCloseGitModal = document.getElementById('btn-close-git-modal');
    this.btnCancelGitModal = document.getElementById('btn-cancel-git-modal');
    this.btnSubmitGitSave = document.getElementById('btn-submit-git-save');
    this.btnCloseEmailModal = document.getElementById('btn-close-email-modal');
    this.btnCancelEmailModal = document.getElementById('btn-cancel-email-modal');
    this.btnSaveEmailSettings = document.getElementById('btn-save-email-settings');
    this.btnTestSendEmail = document.getElementById('btn-test-send-email');

    // Node Configuration Modal
    this.nodeModal = document.getElementById('node-config-modal');
    this.btnCloseNode = document.getElementById('btn-close-node-modal');
    this.btnCancelNode = document.getElementById('btn-cancel-node-modal');
    this.btnSaveNode = document.getElementById('btn-save-node-config');
    this.nodeConfigFormBody = document.getElementById('node-config-form-body');
    this.nodeModalTitle = document.getElementById('node-modal-title');
    this.nodeModalSub = document.getElementById('node-modal-sub');

    // Site Model Devices Left Sidebar Elements
    this.siteDevicesCountBadge = document.getElementById('site-devices-count-badge');
    this.siteDevicesSelectedSummary = document.getElementById('site-devices-selected-summary');
    this.inputDeviceSearch = document.getElementById('input-device-search');
    this.btnDeviceSearchClear = document.getElementById('btn-device-search-clear');
    this.btnDevicesSelectAll = document.getElementById('btn-devices-select-all');
    this.btnDevicesClearAll = document.getElementById('btn-devices-clear-all');
    this.siteDevicesList = document.getElementById('site-devices-list');
  }

  initComponents() {
    const logsContainer = document.getElementById('sequencer-logs');
    if (logsContainer) {
      this.logViewer = new LogViewer(logsContainer);
      this.logViewer.append('UDMI Workbench Live Log Analyzer ready. Select devices and start tests to view streaming output...', 'info');
    }
  }

  parseProjectSpec(spec) {
    if (!spec) {
      return { provider: 'mqtt', project: 'localhost', namespace: null, effectiveNamespace: 'udmis', user: null, isCloud: false };
    }
    let s = spec.trim();
    let provider = 'pubsub';
    if (s.startsWith('//')) {
      s = s.slice(2);
      if (s.includes('/')) {
        const parts = s.split('/', 2);
        provider = parts[0];
        s = s.slice(parts[0].length + 1);
      } else {
        provider = s;
        s = '';
      }
    }
    let user = null;
    if (s.includes('+')) {
      const parts = s.split('+', 2);
      s = parts[0];
      user = parts[1];
    }
    let namespace = null;
    let project = s;
    if (s.includes('/')) {
      const parts = s.split('/', 2);
      project = parts[0];
      namespace = parts[1];
    }
    const effectiveNamespace = namespace || 'udmis';
    const isCloud = provider === 'gbos' || provider === 'gref' || provider === 'pubsub' || provider === 'clearblade' || (project && project.includes('bos-platform'));

    return {
      provider,
      project: project || 'bos-platform-dev',
      namespace,
      effectiveNamespace,
      user,
      isCloud
    };
  }

  syncProjectSpecToNodes(val) {
    this.projectSpec = val;
    const parsed = this.parseProjectSpec(val);
    if (parsed.isCloud) {
      this.setupMode = 'CLOUD';
      const targetNs = parsed.effectiveNamespace;
      const envProject = parsed.project;
      const userSuffix = parsed.user ? `+${parsed.user}` : '';

      const ingressNode = this.infraNodes.find(n => n.type === 'zanzara_ingress');
      if (ingressNode) {
        ingressNode.inputs.project_id = envProject;
        ingressNode.inputs.endpoint = `${envProject}.corp.goog`;
        ingressNode.inputs.namespace = targetNs;
      }

      const fabricNode = this.infraNodes.find(n => n.type === 'zanzara_fabric');
      if (fabricNode) {
        fabricNode.inputs.pubsub_project = envProject;
        fabricNode.inputs.namespace = targetNs;
      }

      const cloudUdmisNode = this.infraNodes.find(n => n.type === 'cloud_udmis');
      if (cloudUdmisNode) {
        cloudUdmisNode.inputs.topic = `projects/${envProject}/topics/${targetNs !== 'udmis' ? `${targetNs}~` : ''}udmi_target`;
        cloudUdmisNode.inputs.subscription = `${targetNs !== 'udmis' ? `${targetNs}~` : ''}udmi_target-udmis${userSuffix}`;
        cloudUdmisNode.inputs.namespace = targetNs;
        if (parsed.user) cloudUdmisNode.inputs.user = parsed.user;
      }

      const etcdNode = this.infraNodes.find(n => n.type === 'etcd');
      if (etcdNode) {
        etcdNode.inputs.host = `${envProject}-etcd`;
        etcdNode.inputs.namespace = targetNs;
      }

      this.updateSetupButtons();
      this.renderGraph();
      if (this.selectedNodeId) this.renderInspector();
    }
  }

  initStoreListeners() {
    stateStore.on('change:siteModel', (val) => {
      this.siteModel = val;
      this.loadSiteModelDevices();
      this.runAllHealthChecks();
    });

    stateStore.on('change:projectSpec', (val) => {
      this.syncProjectSpecToNodes(val);
      this.runAllHealthChecks();
    });

    stateStore.on('change:devices', (devices) => {
      const meta = stateStore.get('deviceMetadata') || {};
      this.onDiscoveredDevices(devices, meta);
    });

    stateStore.on('change:deviceMetadata', (meta) => {
      if (meta) {
        this.deviceMetadata = meta;
        this.renderGraph();
      }
    });
  }

  async checkAndRecoverBackgroundJobs() {
    try {
      const res = await fetch('/api/testbed/jobs');
      if (!res.ok) return;
      const data = await res.json();
      if (!data.jobs || data.jobs.length === 0) return;

      data.jobs.forEach(job => {
        if (job.type === 'sequencer' && job.running && job.device_id) {
          const targetDev = this.deviceNodes.find(n => n.inputs.device_id === job.device_id) || this.deviceNodes[0];
          if (targetDev) {
            targetDev.isTestingRunning = true;
            targetDev.lastSessionId = job.session_id;
            this.logOffsets.set(job.session_id, 0);
            if (!this.activePolls.has(job.session_id)) {
              const interval = setInterval(() => this.pollSequencerStatus(targetDev, job.session_id), 600);
              this.activePolls.set(job.session_id, interval);
            }
            NotificationManager.showToast({
              title: "🔄 Resumed Active Test Session",
              message: `Reconnected to running background test suite for device [${targetDev.inputs.device_id}] (Session: ${job.session_id}).`,
              type: 'info'
            });
          }
        }
      });
      this.renderGraph();
      this.renderInspector();
      if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
      this.updateExecutionControlsState();
    } catch (e) {
      console.warn("Background job recovery check failed:", e);
    }
  }

  async loadSiteModelDevices() {
    if (!this.siteModel) {
      this.discoveredDevices = [];
      this.deviceNodes = [];
      if (this.siteDevicesCountBadge) {
        this.siteDevicesCountBadge.textContent = '0';
      }
      if (this.siteDevicesList) {
        this.siteDevicesList.innerHTML = `
          <div style="font-size: 11px; color: #70757a; text-align: center; padding: 24px 8px; line-height: 1.5;">
            <div style="font-weight: 600; color: #3c4043; margin-bottom: 4px;">No Site Model Selected</div>
            <div>Select a UDMI site model directory to view and configure devices.</div>
            <button id="btn-empty-browse-site" class="btn btn-outlined btn-sm" style="margin-top: 8px; font-size: 11px; padding: 3px 10px;">📁 Select Site Model</button>
          </div>
        `;
        const btnEmpty = document.getElementById('btn-empty-browse-site');
        if (btnEmpty) {
          btnEmpty.addEventListener('click', () => {
            stateStore.emit('open_folder_browser');
            window.postMessage({ type: 'open_folder_browser' }, '*');
          });
        }
      }
      this.renderGraph();
      return;
    }

    if (this.siteDevicesList) {
      this.siteDevicesList.innerHTML = `
        <div style="font-size: 11px; color: #70757a; text-align: center; padding: 20px 8px;">
          <span class="spinner-sm" style="margin-bottom: 6px;"></span>
          <div>Loading devices from site model...</div>
        </div>
      `;
    }
    try {
      const res = await fetch(`/api/devices?site_model=${encodeURIComponent(this.siteModel)}`);
      if (res.ok) {
        const data = await res.json();
        this.deviceMetadata = data.device_metadata || {};
        this.onDiscoveredDevices(data.devices || [], this.deviceMetadata);
      } else {
        if (this.siteDevicesList) {
          this.siteDevicesList.innerHTML = `
            <div style="font-size: 11px; color: #c5221f; text-align: center; padding: 16px 8px;">
              ⚠️ Could not load devices from "${this.siteModel}"
            </div>
          `;
        }
      }
    } catch (e) {
      console.warn("Failed to load site model devices:", e);
      if (this.siteDevicesList) {
        this.siteDevicesList.innerHTML = `
          <div style="font-size: 11px; color: #c5221f; text-align: center; padding: 16px 8px;">
            ⚠️ Network error loading devices
          </div>
        `;
      }
    }
  }

  async fetchSequencerCatalog() {
    try {
      const res = await fetch('/api/sequences/catalog');
      if (res.ok) {
        const data = await res.json();
        if (data.catalog && Array.isArray(data.catalog) && data.catalog.length > 0) {
          SEQUENCER_TEST_CATALOG = data.catalog;
          this.deviceNodes.forEach(node => {
            if (!node.selectedTests || node.selectedTests.size === 0) {
              node.selectedTests = new Set(this.getSmokeTestIds());
            }
          });
          if (this.isInspectorOpen) {
            this.renderInspector();
          }
          if (this.activeViewMode === 'matrix') {
            this.renderComplianceMatrix();
          }
        }
      }
    } catch (e) {
      console.warn("Could not dynamically fetch sequencer catalog:", e);
    }
  }

  getSmokeTestIds() {
    const stable = [];
    SEQUENCER_TEST_CATALOG.forEach(c => (c.tests || []).forEach(t => {
      if ((t.stage || 'STABLE').toUpperCase() === 'STABLE') {
        stable.push(t.id);
      }
    }));
    return stable.length > 0 ? stable.slice(0, 6) : (SEQUENCER_TEST_CATALOG[0]?.tests?.map(t => t.id) || []);
  }

  onDiscoveredDevices(devices, metadata = {}) {
    this.discoveredDevices = Array.isArray(devices) ? devices : [];
    if (metadata && Object.keys(metadata).length > 0) {
      this.deviceMetadata = metadata;
    }
    if (this.siteDevicesCountBadge) {
      this.siteDevicesCountBadge.textContent = this.discoveredDevices.length;
    }

    // Keep existing configurations, initialize new ones
    this.discoveredDevices.forEach(devId => {
      if (!this.deviceConfigs.has(devId)) {
        const existingNode = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
        if (existingNode) {
          this.deviceConfigs.set(devId, { enabled: true, mode: existingNode.type });
        } else {
          this.deviceConfigs.set(devId, { enabled: false, mode: 'actual_device' });
        }
      }
    });

    // Synchronize existing device nodes with discovered devices
    this.deviceNodes.forEach(node => {
      const devId = node.inputs.device_id;
      if (devId && this.deviceConfigs.has(devId)) {
        const cfg = this.deviceConfigs.get(devId);
        cfg.enabled = true;
      }
    });

    this.renderSiteDevicesList();
    this.renderGraph();
  }

  renderSiteDevicesList() {
    if (!this.siteDevicesList) return;

    const totalCount = this.discoveredDevices.length;
    const activeCanvasDevCount = this.deviceNodes.length;

    if (this.siteDevicesCountBadge) {
      this.siteDevicesCountBadge.textContent = totalCount;
    }
    if (this.siteDevicesSelectedSummary) {
      this.siteDevicesSelectedSummary.textContent = `${activeCanvasDevCount} selected`;
    }

    if (!this.siteModel) {
      this.loadSiteModelDevices();
      return;
    }

    if (!this.discoveredDevices || this.discoveredDevices.length === 0) {
      this.siteDevicesList.innerHTML = `
        <div style="font-size: 11px; color: #70757a; text-align: center; padding: 24px 8px; width: 100%;">
          No devices found in "${this.siteModel}".
        </div>
      `;
      return;
    }

    const query = (this.deviceSearchQuery || '').toLowerCase();
    const filteredDevices = this.discoveredDevices.filter(devId => devId.toLowerCase().includes(query));

    if (filteredDevices.length === 0) {
      this.siteDevicesList.innerHTML = `
        <div style="font-size: 11px; color: #70757a; text-align: center; padding: 24px 8px; width: 100%;">
          No devices matching "<strong>${this.deviceSearchQuery}</strong>"
        </div>
      `;
      return;
    }

    let html = '';
    filteredDevices.forEach(devId => {
      const node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
      const isOnCanvas = Boolean(node);
      const isPubber = node ? node.type === 'pubber' : false;

      html += `
        <button class="device-pill ${isOnCanvas ? 'selected' : ''} ${isPubber ? 'is-pubber' : ''}" 
                data-device-id="${devId}" 
                draggable="true" 
                title="${isOnCanvas ? `Click to remove ${devId} from canvas (${isPubber ? 'Pubber emulator' : 'Physical device'})` : `Click to add ${devId} to canvas`}">
          <span class="material-symbols-outlined pill-icon">${isOnCanvas ? 'check_circle' : 'add'}</span>
          <span class="pill-name">${devId}</span>
          ${isOnCanvas ? `<span class="pill-mode-icon material-symbols-outlined" title="${isPubber ? 'Pubber emulator' : 'Physical device'}">${isPubber ? 'robot_2' : 'home_iot_device'}</span>` : ''}
        </button>
      `;
    });

    this.siteDevicesList.innerHTML = html;

    // Attach click and drag listeners to pills
    this.siteDevicesList.querySelectorAll('.device-pill').forEach(pill => {
      const devId = pill.getAttribute('data-device-id');

      pill.addEventListener('click', (e) => {
        e.stopPropagation();
        const node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
        if (node) {
          this.removeDeviceFromCanvas(devId);
        } else {
          this.addDeviceToCanvas(devId, 'actual_device');
        }
        this.renderSiteDevicesList();
        this.renderGraph();
        this.renderInspector();
        if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
      });

      pill.addEventListener('dragstart', (e) => {
        e.dataTransfer.setData('application/json', JSON.stringify({ type: 'site_device', deviceId: devId }));
      });
    });
  }

  initEvents() {
    if (this.btnDefaultSetup) this.btnDefaultSetup.addEventListener('click', () => this.loadDefaultSetup(false, true));
    if (this.btnCloudSetup) this.btnCloudSetup.addEventListener('click', () => this.promptCloudSetup());
    if (this.btnStopPipeline) this.btnStopPipeline.addEventListener('click', () => this.stopPipeline());
    if (this.btnViewSetupLogs) this.btnViewSetupLogs.addEventListener('click', () => this.openSetupLogsModal(this.latestSetupSessionId, this.setupMode || 'LOCAL', this.projectSpec, this.isSetupReused));
    if (this.btnCloseInspector) this.btnCloseInspector.addEventListener('click', () => this.minimizeInspector());

    // Cloud Setup Modal
    if (this.btnCloseCloudModal) this.btnCloseCloudModal.addEventListener('click', () => this.closeCloudSetupModal());
    if (this.btnSubmitCloudSetup) this.btnSubmitCloudSetup.addEventListener('click', () => this.submitCloudSetup());
    if (this.cloudProjectSpecInput) {
      this.cloudProjectSpecInput.addEventListener('input', () => {
        const val = this.cloudProjectSpecInput.value.trim();
        if (val) {
          localStorage.setItem('udmi_cloud_project_spec', val);
        }
      });
      this.cloudProjectSpecInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') this.submitCloudSetup();
        if (e.key === 'Escape') this.closeCloudSetupModal();
      });
    }

    // Node Config Modal
    if (this.btnCloseNode) this.btnCloseNode.addEventListener('click', () => this.closeNodeModal());
    if (this.btnCancelNode) this.btnCancelNode.addEventListener('click', () => this.closeNodeModal());
    if (this.btnSaveNode) this.btnSaveNode.addEventListener('click', () => this.saveNodeFromModal());

    // Search & Bulk Toolbar Event Handlers
    if (this.inputDeviceSearch) {
      this.inputDeviceSearch.addEventListener('input', (e) => {
        this.deviceSearchQuery = e.target.value.toLowerCase().trim();
        this.renderSiteDevicesList();
        if (this.btnDeviceSearchClear) {
          this.btnDeviceSearchClear.style.display = this.deviceSearchQuery ? 'block' : 'none';
        }
      });
    }

    if (this.btnDeviceSearchClear) {
      this.btnDeviceSearchClear.addEventListener('click', () => {
        if (this.inputDeviceSearch) this.inputDeviceSearch.value = '';
        this.deviceSearchQuery = '';
        this.renderSiteDevicesList();
        this.btnDeviceSearchClear.style.display = 'none';
      });
    }

    if (this.btnDevicesSelectAll) this.btnDevicesSelectAll.addEventListener('click', () => this.selectAllDevices());
    if (this.btnDevicesClearAll) this.btnDevicesClearAll.addEventListener('click', () => this.clearAllDevices());

    // Git Save & Email Settings Modals
    if (this.btnGitSave) this.btnGitSave.addEventListener('click', () => this.openGitSaveModal());
    if (this.btnCloseGitModal) this.btnCloseGitModal.addEventListener('click', () => this.closeGitSaveModal());
    if (this.btnCancelGitModal) this.btnCancelGitModal.addEventListener('click', () => this.closeGitSaveModal());
    if (this.btnSubmitGitSave) this.btnSubmitGitSave.addEventListener('click', () => this.executeGitSave());

    if (this.btnNotifSettings) this.btnNotifSettings.addEventListener('click', () => this.openEmailSettingsModal());
    if (this.btnCloseEmailModal) this.btnCloseEmailModal.addEventListener('click', () => this.closeEmailSettingsModal());
    if (this.btnCancelEmailModal) this.btnCancelEmailModal.addEventListener('click', () => this.closeEmailSettingsModal());
    if (this.btnSaveEmailSettings) this.btnSaveEmailSettings.addEventListener('click', () => this.saveEmailSettings());
    if (this.btnTestSendEmail) this.btnTestSendEmail.addEventListener('click', () => this.sendTestEmail());

    // View Switching
    if (this.btnViewCanvas) this.btnViewCanvas.addEventListener('click', () => this.switchViewMode('canvas'));
    if (this.btnViewMatrix) this.btnViewMatrix.addEventListener('click', () => this.switchViewMode('matrix'));
    if (this.btnViewLogs) this.btnViewLogs.addEventListener('click', () => this.switchViewMode('logs'));

    if (this.btnMatrixRunAll) this.btnMatrixRunAll.addEventListener('click', () => this.runAllMatrixTests());
    if (this.btnMatrixStopAll) this.btnMatrixStopAll.addEventListener('click', () => this.stopAllRunningTests());
    if (this.btnLogsStop) this.btnLogsStop.addEventListener('click', () => this.stopAllRunningTests());

    // Log Analyzer Tabs & Diff
    if (this.tabLogLive) this.tabLogLive.addEventListener('click', () => this.switchLogTab('live'));
    if (this.tabLogDiff) this.tabLogDiff.addEventListener('click', () => this.switchLogTab('diff'));
    if (this.btnCompareDiff) this.btnCompareDiff.addEventListener('click', () => this.runLogDiff());

    // Drag & Drop to Canvas
    if (this.canvasContainer) {
      this.canvasContainer.addEventListener('dragover', (e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'copy';
      });

      this.canvasContainer.addEventListener('drop', (e) => {
        e.preventDefault();
        const rawData = e.dataTransfer.getData('application/json') || e.dataTransfer.getData('text/plain');
        if (!rawData) return;
        try {
          const data = JSON.parse(rawData);
          if (data.type === 'site_device' && data.deviceId) {
            const rect = this.canvasContainer.getBoundingClientRect();
            const x = Math.max(20, e.clientX - rect.left - 90);
            const y = Math.max(20, e.clientY - rect.top - 40);
            const cfg = this.deviceConfigs.get(data.deviceId) || { mode: 'actual_device' };
            this.addDeviceToCanvas(data.deviceId, cfg.mode || 'actual_device', x, y);
            this.renderSiteDevicesList();
            this.renderGraph();
            this.renderInspector();
            if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
          }
        } catch (err) {
          // Fallback if plain text string
          if (rawData === 'pubber' || rawData === 'actual_device') {
            const rect = this.canvasContainer.getBoundingClientRect();
            const x = Math.max(20, e.clientX - rect.left - 90);
            const y = Math.max(20, e.clientY - rect.top - 40);
            this.openNodeModal({ type: rawData, x, y, isNew: true });
          }
        }
      });
    }

    window.addEventListener('mousemove', (e) => {
      if (this.draggedNodeId && this.canvasContainer) {
        const rect = this.canvasContainer.getBoundingClientRect();
        const x = Math.max(10, Math.min(rect.width - 190, e.clientX - rect.left - this.dragOffset.x));
        const y = Math.max(10, Math.min(rect.height - 90, e.clientY - rect.top - this.dragOffset.y));

        const node = this.nodes.find(n => n.id === this.draggedNodeId);
        if (node) {
          node.x = x;
          node.y = y;
          this.renderGraph();
        }
      }
    });

    window.addEventListener('mouseup', () => {
      this.draggedNodeId = null;
    });

    setInterval(() => {
      this.runAllHealthChecks();
    }, 8000);

    window.addEventListener('resize', () => this.updateTabIndicator());
    this.initInspectorDraggable();
  }

  openInspector() {
    this.isInspectorOpen = true;
    if (this.inspectorPanel) {
      this.inspectorPanel.classList.remove('minimized');
      this.inspectorPanel.classList.add('open');
    }
    this.renderInspector();
  }

  minimizeInspector() {
    this.isInspectorOpen = false;
    if (this.inspectorPanel) {
      this.inspectorPanel.classList.add('minimized');
      this.inspectorPanel.classList.remove('open');
    }
  }

  toggleInspector() {
    if (this.isInspectorOpen) {
      this.minimizeInspector();
    } else {
      this.openInspector();
    }
  }

  initInspectorDraggable() {
    const panel = this.inspectorPanel;
    const header = panel ? panel.querySelector('.inspector-header') : null;
    if (!panel || !header) return;

    let isDragging = false;
    let startX = 0;
    let startY = 0;
    let initialLeft = 0;
    let initialTop = 0;

    header.addEventListener('mousedown', (e) => {
      // Don't drag if clicking close button, minimize button, or any other button inside header
      if (e.target.closest('#btn-close-inspector') || e.target.closest('#btn-minimize-inspector') || e.target.closest('button')) return;

      isDragging = true;
      startX = e.clientX;
      startY = e.clientY;

      const rect = panel.getBoundingClientRect();
      const parentRect = (panel.offsetParent || panel.parentElement || document.body).getBoundingClientRect();

      initialLeft = rect.left - parentRect.left;
      initialTop = rect.top - parentRect.top;

      panel.style.left = `${initialLeft}px`;
      panel.style.top = `${initialTop}px`;
      panel.style.right = 'auto';
      panel.style.bottom = 'auto';

      const onMouseMove = (moveEvent) => {
        if (!isDragging) return;
        const dx = moveEvent.clientX - startX;
        const dy = moveEvent.clientY - startY;

        const currentParentRect = (panel.offsetParent || panel.parentElement || document.body).getBoundingClientRect();
        const maxLeft = currentParentRect.width - panel.offsetWidth - 8;
        const maxTop = currentParentRect.height - panel.offsetHeight - 8;

        const newLeft = Math.max(8, Math.min(maxLeft, initialLeft + dx));
        const newTop = Math.max(8, Math.min(maxTop, initialTop + dy));

        panel.style.left = `${newLeft}px`;
        panel.style.top = `${newTop}px`;
      };

      const onMouseUp = () => {
        isDragging = false;
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
      };

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
      e.preventDefault();
    });
  }

  updateTabIndicator() {
    const bar = document.getElementById('testbed-nav-tabs');
    const indicator = document.getElementById('testbed-tab-indicator');
    const activeBtn = bar ? bar.querySelector('.md-tab-item.active') : null;
    if (bar && indicator && activeBtn) {
      const left = activeBtn.offsetLeft;
      const width = activeBtn.offsetWidth;
      indicator.style.transform = `translateX(${left}px)`;
      indicator.style.width = `${width}px`;
    }
  }

  switchViewMode(mode) {
    this.activeViewMode = mode;
    if (this.btnViewCanvas) {
      this.btnViewCanvas.classList.toggle('active', mode === 'canvas');
      this.btnViewCanvas.setAttribute('aria-selected', mode === 'canvas' ? 'true' : 'false');
    }
    if (this.btnViewMatrix) {
      this.btnViewMatrix.classList.toggle('active', mode === 'matrix');
      this.btnViewMatrix.setAttribute('aria-selected', mode === 'matrix' ? 'true' : 'false');
    }
    if (this.btnViewLogs) {
      this.btnViewLogs.classList.toggle('active', mode === 'logs');
      this.btnViewLogs.setAttribute('aria-selected', mode === 'logs' ? 'true' : 'false');
    }

    if (this.canvasWorkspace) this.canvasWorkspace.style.display = mode === 'canvas' ? 'grid' : 'none';
    if (this.matrixWorkspace) this.matrixWorkspace.style.display = mode === 'matrix' ? 'flex' : 'none';
    if (this.logsWorkspace) this.logsWorkspace.style.display = mode === 'logs' ? 'flex' : 'none';

    this.updateTabIndicator();

    if (mode === 'matrix') {
      this.renderComplianceMatrix();
    } else if (mode === 'canvas') {
      this.renderGraph();
    } else if (mode === 'logs') {
      const activeNode = this.deviceNodes.find(n => n.isTestingRunning) || this.nodes.find(n => n.id === this.selectedNodeId) || this.deviceNodes[0];
      if (activeNode) {
        this.updateActiveExecutionProgress(activeNode);
      }
    }
  }

  switchLogTab(tab) {
    if (this.tabLogLive) this.tabLogLive.classList.toggle('active', tab === 'live');
    if (this.tabLogDiff) this.tabLogDiff.classList.toggle('active', tab === 'diff');
    if (this.containerLogLive) this.containerLogLive.style.display = tab === 'live' ? 'block' : 'none';
    if (this.containerLogDiff) this.containerLogDiff.style.display = tab === 'diff' ? 'block' : 'none';
    if (this.diffControls) this.diffControls.style.display = tab === 'diff' ? 'flex' : 'none';
    if (tab === 'diff') this.populateDiffBaselines();
  }

  generateDeviceSerialNo(devId) {
    if (!devId) return `${Math.floor(10000 + Math.random() * 90000)}`;

    const existingSerials = new Set();
    (this.deviceNodes || []).forEach(n => {
      if (n.inputs && n.inputs.serial_no && n.inputs.device_id !== devId) {
        existingSerials.add(String(n.inputs.serial_no));
      }
    });
    (this.nodes || []).forEach(n => {
      if (n.inputs && n.inputs.serial_no && n.inputs.device_id !== devId) {
        existingSerials.add(String(n.inputs.serial_no));
      }
    });

    const metaSerial = this.deviceMetadata?.[devId]?.serial_no;
    if (metaSerial && !existingSerials.has(String(metaSerial))) {
      return String(metaSerial);
    }

    const numMatch = devId.match(/\d+/);
    let baseNum = numMatch ? (10490 + parseInt(numMatch[0], 10)) : 0;
    if (!baseNum || isNaN(baseNum)) {
      let hash = 0;
      for (let i = 0; i < devId.length; i++) {
        hash = (hash * 31 + devId.charCodeAt(i)) % 90000;
      }
      baseNum = 10000 + Math.abs(hash);
    }

    let candidate = `${baseNum}`;
    let inc = 0;
    while (existingSerials.has(candidate)) {
      inc++;
      candidate = `${baseNum + inc}`;
    }
    return candidate;
  }

  // --- NODE TYPE SPECIFICATIONS ---
  getNodeSpec(type, devId = null) {
    const parsed = this.parseProjectSpec(this.projectSpec);
    const envProject = parsed.project || 'bos-platform-dev';
    const envNamespace = parsed.effectiveNamespace || 'udmis';
    const isCloud = parsed.isCloud;
    const targetDevId = devId || 'AHU-1';
    const serialNo = this.generateDeviceSerialNo(targetDevId);

    const specs = {
      pubber: {
        type: 'pubber',
        label: 'Device Emulator (Pubber)',
        icon: 'robot_2',
        inputs: { device_id: targetDevId, serial_no: serialNo, interval_sec: '10' },
        subText: (n) => {
          const dId = n.inputs.device_id || n.label;
          const meta = this.deviceMetadata && this.deviceMetadata[dId];
          const ver = meta?.version || n.inputs.version;
          return ver ? `v${ver.replace(/^v/, '')}` : 'UDMI Ready';
        },
        runConfig: (n) => `out/pubber_config.json (site: ${this.siteModel}, dev: ${n.inputs.device_id || 'AHU-1'})`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/pubber ${this.siteModel} ${this.projectSpec || '//mqtt/localhost:18833'} ${n.inputs.device_id || 'AHU-1'} ${n.inputs.serial_no || this.generateDeviceSerialNo(n.inputs.device_id || 'AHU-1')}`,
        healthProbe: (n) => `Message Probe: bin/pull_mqtt for /r/+/d/${n.inputs.device_id || 'AHU-1'}/state`
      },
      spotter: {
        type: 'spotter',
        label: 'Device Emulator (Spotter)',
        icon: 'radar',
        disabled: true,
        inputs: {},
        subText: () => 'Coming Soon',
        runConfig: () => 'N/A',
        runCommand: () => 'N/A',
        healthProbe: () => 'N/A'
      },
      actual_device: {
        type: 'actual_device',
        label: 'Actual Device',
        icon: 'home_iot_device',
        inputs: { device_id: targetDevId || 'AHU-22', address: '192.168.1.105', protocol: 'BACnet/IP' },
        subText: (n) => {
          const dId = n.inputs.device_id || n.label;
          const meta = this.deviceMetadata && this.deviceMetadata[dId];
          const ver = meta?.version || n.inputs.version;
          return ver ? `v${ver.replace(/^v/, '')}` : 'UDMI Ready';
        },
        runConfig: (n) => `${this.siteModel}/devices/${n.inputs.device_id || 'AHU-22'}/metadata.json`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/registrar ${this.siteModel} ${this.projectSpec || '//mqtt/localhost:18833'} ${n.inputs.device_id || 'AHU-22'}`,
        healthProbe: (n) => `Message Heartbeat: bin/pull_mqtt for /r/+/d/${n.inputs.device_id || 'AHU-22'}/state`
      },
      mqtt_broker: {
        type: 'mqtt_broker',
        label: 'Local Mosquitto Broker',
        icon: 'cell_tower',
        inputs: { port: '18833', use_tls: 'false' },
        subText: (n) => `Port: ${n.inputs.port || '18833'} (Isolated Mode)`,
        runConfig: () => `var/mosquitto/mosquitto.conf & var/mosquitto/conf.d/udmi.conf`,
        runCommand: (n) => `UDMI_NO_SUDO=true MQTT_PORT=${n.inputs.port || '18833'} bin/start_mosquitto`,
        healthProbe: (n) => `System Metrics Probe: mosquitto_sub -p ${n.inputs.port || '18833'} -t '$SYS/broker/uptime'`
      },
      udmis: {
        type: 'udmis',
        label: 'Local UDMIS',
        icon: 'dns',
        inputs: { mode: 'LOCAL', site_model: this.siteModel },
        subText: (n) => `Mode: ${n.inputs.mode || 'LOCAL'}`,
        runConfig: () => `var/local_pod.json (extends udmis/etc/prod_pod.json)`,
        runCommand: () => `UDMI_NO_SUDO=true bin/start_udmis`,
        healthProbe: () => `Readiness Sentinel: test -f var/pod_ready.txt & out/udmis.log`
      },
      zanzara_ingress: {
        type: 'zanzara_ingress',
        label: 'Zanzara Ingress',
        icon: 'vpn_lock',
        inputs: { endpoint: `${envProject}.corp.goog`, port: '8883', namespace: envNamespace, project_id: envProject },
        subText: () => 'Auth Proxy',
        runConfig: (n) => `k8s/auth/ (namespace: ${n.inputs.namespace || envNamespace})`,
        runCommand: (n) => `kubectl get deployment auth -n ${n.inputs.namespace || envNamespace}`,
        healthProbe: (n) => `mTLS Ingress Probe: nc -zv ${n.inputs.endpoint || `${envProject}.corp.goog`} ${n.inputs.port || '8883'}`
      },
      zanzara_fabric: {
        type: 'zanzara_fabric',
        label: 'Message Fabric',
        icon: 'hub',
        inputs: { pubsub_project: envProject, namespace: envNamespace, topics: 'udmi_target, udmi_state, udmi_reflect' },
        subText: () => 'Mosquitto+Bridges+Pub/Sub',
        runConfig: (n) => `k8s/bridge/ (namespace: ${n.inputs.namespace || envNamespace})`,
        runCommand: (n) => `kubectl get statefulset -l app=bridge -n ${n.inputs.namespace || envNamespace}`,
        healthProbe: (n) => `Pub/Sub Topics: gcloud pubsub topics list --project=${n.inputs.pubsub_project || envProject}`
      },
      cloud_udmis: {
        type: 'cloud_udmis',
        label: 'Cloud UDMIS',
        icon: 'cloud_done',
        inputs: { topic: `projects/${envProject}/topics/udmi_target`, subscription: 'udmi_target-udmis', namespace: envNamespace },
        subText: () => 'Schema & State Engine',
        runConfig: (n) => `udmis/etc/prod_pod.json (namespace: ${n.inputs.namespace || envNamespace})`,
        runCommand: (n) => `kubectl get deployment udmis-pods -n ${n.inputs.namespace || envNamespace}`,
        healthProbe: (n) => `GCP Pub/Sub Pull: gcloud pubsub subscriptions pull ${n.inputs.subscription || 'udmi_target-udmis'} --limit=1`
      },
      etcd: {
        type: 'etcd',
        label: 'etcd State Store',
        icon: 'database',
        inputs: { port: '2379', host: '127.0.0.1' },
        subText: () => 'Device Registry & State',
        runConfig: (n) => isCloud || (n.inputs.host && n.inputs.host.includes('bos-platform')) ? `k8s/udmis/etcd (Service: etcd.udmis.svc.cluster.local:2379)` : 'var/etcd/',
        runCommand: (n) => isCloud || (n.inputs.host && n.inputs.host.includes('bos-platform')) ? `https://${envProject}.corp.goog/` : 'bin/start_etcd',
        healthProbe: () => 'pgrep -f etcd'
      },
      influx: {
        type: 'influx',
        label: 'InfluxDB Metrics',
        icon: 'bar_chart',
        inputs: { port: '8086', host: '127.0.0.1' },
        subText: (n) => `Port: ${n.inputs.port || '8086'} (Telemetry)`,
        runConfig: () => 'var/influx/',
        runCommand: () => 'bin/start_influx',
        healthProbe: () => 'pgrep -f influx'
      },
      postgresql: {
        type: 'postgresql',
        label: 'PostgreSQL Database',
        icon: 'table_rows',
        inputs: { port: '5432', host: '127.0.0.1' },
        subText: (n) => `Port: ${n.inputs.port || '5432'} (Database)`,
        runConfig: () => 'var/postgresql/',
        runCommand: () => 'bin/start_postgresql',
        healthProbe: () => 'pgrep -f postgres'
      },
      ancillary: {
        type: 'ancillary',
        label: 'Ancillary Test Node',
        icon: 'extension',
        disabled: true,
        inputs: {},
        subText: () => 'Coming Soon',
        runConfig: () => 'N/A',
        runCommand: () => 'N/A',
        healthProbe: () => 'N/A'
      }
    };
    return specs[type] || specs.pubber;
  }

  loadDefaultSetup(silent = false, autoStart = false) {
    if (!this.siteModel) {
      NotificationManager.showToast({
        title: "📁 Site Model Required",
        message: "Please select a Site Model Path before starting Local Setup.",
        type: "warning"
      });
      stateStore.emit('open_folder_browser');
      window.postMessage({ type: 'open_folder_browser' }, '*');
      return;
    }

    this.setupMode = 'LOCAL';
    this.projectSpec = '//mqtt/localhost:18833'; // Unprivileged isolated mode
    stateStore.set('projectSpec', this.projectSpec);
    this.updateSetupButtons();

    // Replace infrastructure nodes with Local wave topology (Device at y:60, Broker at y:190, UDMIS at y:60, etcd at y:190)
    this.infraNodes = [
      this.createNodeObject('mqtt_broker', 250, 190),
      this.createNodeObject('udmis', 460, 60),
      this.createNodeObject('etcd', 670, 190)
    ];

    // Layout existing device nodes if any were explicitly added
    this.deviceNodes.forEach((node, idx) => {
      node.x = 40;
      node.y = 60 + idx * 105;
    });

    this.renderGraph();
    this.runAllHealthChecks();
    this.renderSiteDevicesList();
    if (!silent) {
      NotificationManager.showToast({ title: "Local Setup Active", message: "Setting up local environment (//mqtt/localhost:18833).", type: "info" });
    }
    if (autoStart) {
      this.startPipeline();
    }
  }

  promptCloudSetup() {
    if (!this.siteModel) {
      NotificationManager.showToast({
        title: "📁 Site Model Required",
        message: "Please select a Site Model Path before starting Cloud Setup.",
        type: "warning"
      });
      stateStore.emit('open_folder_browser');
      window.postMessage({ type: 'open_folder_browser' }, '*');
      return;
    }

    if (this.cloudProjectSpecInput) {
      const cached = localStorage.getItem('udmi_cloud_project_spec');
      const cur = cached || (this.projectSpec && !this.projectSpec.includes('localhost') ? this.projectSpec : '//gref/bos-platform-dev/heykhyati');
      this.cloudProjectSpecInput.value = cur;
    }

    if (this.cloudSetupModal) {
      this.cloudSetupModal.classList.add('active');
      setTimeout(() => {
        if (this.cloudProjectSpecInput) {
          this.cloudProjectSpecInput.focus();
          this.cloudProjectSpecInput.select();
        }
      }, 50);
    }
  }

  closeCloudSetupModal() {
    if (this.cloudSetupModal) {
      this.cloudSetupModal.classList.remove('active');
    }
  }

  submitCloudSetup() {
    const spec = this.cloudProjectSpecInput ? this.cloudProjectSpecInput.value.trim() : '';
    if (!spec) {
      NotificationManager.showToast({
        title: "Project Spec Required",
        message: "Please enter a valid Project Spec (e.g. //gref/bos-platform-dev).",
        type: "warning"
      });
      return;
    }
    localStorage.setItem('udmi_cloud_project_spec', spec);
    this.closeCloudSetupModal();
    this.loadCloudSetup(false, true, spec);
  }

  loadCloudSetup(silent = false, autoStart = false, customProjectSpec = null) {
    if (!this.siteModel) {
      NotificationManager.showToast({
        title: "📁 Site Model Required",
        message: "Please select a Site Model Path before starting Cloud Setup.",
        type: "warning"
      });
      stateStore.emit('open_folder_browser');
      window.postMessage({ type: 'open_folder_browser' }, '*');
      return;
    }

    this.setupMode = 'CLOUD';
    this.latestSetupSessionId = null;
    const cached = localStorage.getItem('udmi_cloud_project_spec');
    if (customProjectSpec) {
      this.projectSpec = customProjectSpec;
      localStorage.setItem('udmi_cloud_project_spec', customProjectSpec);
    } else if (cached) {
      this.projectSpec = cached;
    } else if (!this.projectSpec || this.projectSpec.includes('localhost')) {
      this.projectSpec = '//gref/bos-platform-dev/heykhyati';
    }
    stateStore.set('projectSpec', this.projectSpec);
    this.updateSetupButtons();

    // Extract target project / namespace from projectSpec
    const parsed = this.parseProjectSpec(this.projectSpec);
    const envProject = parsed.project;
    const targetNs = parsed.effectiveNamespace;
    const userSuffix = parsed.user ? `+${parsed.user}` : '';

    // Replace infrastructure nodes with Zanzara Cloud wave topology (Device: y:60, Ingress: y:190, Fabric: y:60, UDMIS: y:190, etcd: y:60)
    const ingressNode = this.createNodeObject('zanzara_ingress', 210, 190);
    ingressNode.inputs.project_id = envProject;
    ingressNode.inputs.endpoint = `${envProject}.corp.goog`;
    ingressNode.inputs.namespace = targetNs;

    const fabricNode = this.createNodeObject('zanzara_fabric', 390, 60);
    fabricNode.inputs.pubsub_project = envProject;
    fabricNode.inputs.namespace = targetNs;

    const cloudUdmisNode = this.createNodeObject('cloud_udmis', 570, 190);
    cloudUdmisNode.inputs.topic = `projects/${envProject}/topics/${targetNs !== 'udmis' ? `${targetNs}~` : ''}udmi_target`;
    cloudUdmisNode.inputs.subscription = `${targetNs !== 'udmis' ? `${targetNs}~` : ''}udmi_target-udmis${userSuffix}`;
    cloudUdmisNode.inputs.namespace = targetNs;
    if (parsed.user) {
      cloudUdmisNode.inputs.user = parsed.user;
    }

    const etcdNode = this.createNodeObject('etcd', 750, 60);
    etcdNode.inputs.host = `${envProject}-etcd`;
    etcdNode.inputs.namespace = targetNs;

    this.infraNodes = [
      ingressNode,
      fabricNode,
      cloudUdmisNode,
      etcdNode
    ];

    // Layout existing device nodes if any were explicitly added
    this.deviceNodes.forEach((node, idx) => {
      node.x = 30;
      node.y = 60 + idx * 105;
    });

    this.renderGraph();
    this.runAllHealthChecks();
    this.renderSiteDevicesList();
    if (!silent) {
      NotificationManager.showToast({ title: "Zanzara Setup Active", message: `Configured Zanzara cloud environment (${this.projectSpec}).`, type: "info" });
    }
  }

  updateSetupButtons() {
    if (this.btnDefaultSetup) {
      this.btnDefaultSetup.classList.toggle('active', this.setupMode === 'LOCAL');
      this.btnDefaultSetup.disabled = this.setupMode === 'LOCAL';
    }
    if (this.btnCloudSetup) {
      this.btnCloudSetup.classList.toggle('active', this.setupMode === 'CLOUD');
      this.btnCloudSetup.disabled = this.setupMode === 'CLOUD';
    }
    if (this.btnViewSetupLogs) {
      this.btnViewSetupLogs.style.display = (this.setupMode === 'LOCAL' && this.latestSetupSessionId) ? 'inline-flex' : 'none';
    }
    if (this.btnStopPipeline) {
      this.btnStopPipeline.style.display = 'inline-flex';
    }
  }

  async startPipeline() {
    if (this.setupMode !== 'LOCAL') {
      this.runAllHealthChecks();
      return;
    }

    this.nodes.forEach(n => { if (n.type !== 'actual_device') n.status = 'INITIALIZING'; });
    this.renderGraph();
    try {
      const res = await fetch('/api/testbed/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ site_model: this.siteModel, project_spec: this.projectSpec })
      });
      if (res.ok) {
        const data = await res.json();
        if (data.project_spec) {
          this.projectSpec = data.project_spec;
          stateStore.set('projectSpec', data.project_spec);
        }
        if (data.port) {
          const mqttNode = this.infraNodes.find(n => n.type === 'mqtt_broker');
          if (mqttNode) {
            mqttNode.inputs.port = String(data.port);
          }
        }
        if (data.already_running) {
          this.nodes.forEach(n => { if (n.type !== 'actual_device') n.status = 'UP'; });
          this.renderGraph();
          this.runAllHealthChecks();
          NotificationManager.showToast({
            title: "Local Setup Active",
            message: `Active local environment found (${this.projectSpec}).`,
            type: "info"
          });
          if (data.session_id) {
            this.latestSetupSessionId = data.session_id;
          }
          this.isSetupReused = true;
        } else if (data.session_id) {
          this.isSetupReused = false;
          this.latestSetupSessionId = data.session_id;
          this.openSetupLogsModal(data.session_id, 'LOCAL', data.project_spec || this.projectSpec);
        }
        this.updateSetupButtons();
      } else {
        const errData = await res.json().catch(() => ({}));
        NotificationManager.showToast({
          title: "Startup Failed",
          message: errData.error || "Could not launch local environment.",
          type: "error"
        });
      }
    } catch (e) {
      console.error("Failed to start pipeline:", e);
      NotificationManager.showToast({
        title: "Startup Error",
        message: "Failed to communicate with backend testbed service.",
        type: "error"
      });
    }
    setTimeout(() => this.runAllHealthChecks(), 2500);
    setTimeout(() => this.runAllHealthChecks(), 6000);
  }

  async stopPipeline() {
    this.nodes.forEach(n => {
      if (n.type !== 'actual_device') {
        n.status = 'DOWN';
      }
      if (n.isTestingRunning) {
        n.isTestingRunning = false;
      }
    });
    this.deviceNodes.forEach(n => {
      if (n.isTestingRunning) {
        n.isTestingRunning = false;
      }
    });
    this.renderGraph();
    this.renderInspector();
    NotificationManager.showToast({
      title: "Stopping Local Services",
      message: "Stopping all local pipeline and pubber components...",
      type: "info"
    });
    try {
      const res = await fetch('/api/testbed/stop', { method: 'POST' });
      if (res.ok) {
        NotificationManager.showToast({
          title: "Pipeline Stopped",
          message: "All local pipeline and pubber components have been stopped.",
          type: "success"
        });
      }
    } catch (e) {
      console.error("Failed to stop pipeline:", e);
    }
    this.setupMode = null;
    this.latestSetupSessionId = null;
    this.updateSetupButtons();
    setTimeout(() => this.runAllHealthChecks(), 1500);
  }

  createNodeObject(type, x, y, deviceId = null) {
    const devId = deviceId || null;
    const spec = this.getNodeSpec(type, devId);
    const isDevice = type === 'pubber' || type === 'actual_device' || type === 'spotter' || type === 'ancillary';
    const inputs = { ...spec.inputs };
    if (devId) {
      inputs.device_id = devId;
      if (type === 'pubber') {
        inputs.serial_no = this.generateDeviceSerialNo(devId);
      }
    }
    const isLocalUp = this.infraNodes.some(n => n.type === 'mqtt_broker' && n.status === 'UP');
    let initStatus = '';
    if (type === 'pubber') {
      initStatus = isLocalUp ? 'UP' : 'DOWN';
    } else if (type !== 'actual_device') {
      initStatus = 'UP';
    }
    return {
      id: devId ? `device_${devId}` : ('node_' + Math.random().toString(36).substr(2, 7)),
      type: spec.type,
      label: devId || spec.label,
      icon: spec.icon,
      status: initStatus,
      x,
      y,
      inputs,
      selectedTests: isDevice ? new Set(this.getSmokeTestIds()) : new Set(),
      testResults: null,
      isTestingRunning: false,
      lastSessionId: null
    };
  }

  addNode(type, x, y) {
    if (type !== 'pubber' && type !== 'actual_device') return;
    const node = this.createNodeObject(type, x, y);
    this.deviceNodes.push(node);
    this.selectNode(node.id, false);
    this.renderGraph();
    this.runHealthCheckForNode(node);
  }

  deleteNode(id) {
    const isDevice = this.deviceNodes.some(n => n.id === id);
    if (!isDevice) return;

    const node = this.deviceNodes.find(n => n.id === id);
    if (node && node.inputs && node.inputs.device_id) {
      const devId = node.inputs.device_id;
      if (this.deviceConfigs.has(devId)) {
        this.deviceConfigs.get(devId).enabled = false;
      }
    }

    this.deviceNodes = this.deviceNodes.filter(n => n.id !== id);
    this.selectedNodeIds.delete(id);
    if (this.selectedNodeId === id) {
      const remaining = Array.from(this.selectedNodeIds);
      this.selectedNodeId = remaining.length > 0 ? remaining[0] : (this.deviceNodes[0]?.id || null);
    }
    this.renderSiteDevicesList();
    this.renderGraph();
    this.renderInspector();
  }

  updateCanvasSelectionClasses() {
    if (!this.nodesLayer) return;
    const cards = this.nodesLayer.querySelectorAll('.canvas-node');
    cards.forEach(card => {
      const nodeId = card.dataset.nodeId;
      const isSelected = nodeId && (nodeId === this.selectedNodeId || this.selectedNodeIds.has(nodeId));
      const isMulti = this.selectedNodeIds.size > 1 && this.selectedNodeIds.has(nodeId);
      card.classList.toggle('selected', Boolean(isSelected));
      card.classList.toggle('multi-selected', Boolean(isMulti));
    });
  }

  selectNode(id, isMulti = false, openInspectorPanel = false) {
    if (!id) {
      this.selectedNodeId = null;
      this.selectedNodeIds.clear();
      this.updateCanvasSelectionClasses();
      this.renderInspector();
      this.renderSiteDevicesList();
      return;
    }

    if (isMulti) {
      if (this.selectedNodeIds.has(id) && this.selectedNodeIds.size > 1) {
        this.selectedNodeIds.delete(id);
        if (this.selectedNodeId === id) {
          this.selectedNodeId = Array.from(this.selectedNodeIds)[0];
        }
      } else {
        this.selectedNodeIds.add(id);
        this.selectedNodeId = id;
      }
    } else {
      this.selectedNodeIds.clear();
      this.selectedNodeIds.add(id);
      this.selectedNodeId = id;
    }

    const primaryNode = this.nodes.find(n => n.id === this.selectedNodeId);
    if (primaryNode && (primaryNode.type === 'pubber' || primaryNode.type === 'actual_device')) {
      stateStore.set('activeDevice', primaryNode.inputs.device_id);
    }

    if (openInspectorPanel) {
      this.openInspector();
    }

    this.updateCanvasSelectionClasses();
    this.renderInspector();
    this.renderSiteDevicesList();
    if (primaryNode) {
      this.updateActiveExecutionProgress(primaryNode);
    }
  }

  getNodeLayer(type) {
    if (type === 'pubber' || type === 'actual_device' || type === 'spotter') return 1;
    if (type === 'mqtt_broker' || type === 'zanzara_ingress' || type === 'clearblade_broker') return 2;
    if (type === 'zanzara_fabric') return 3;
    if (type === 'udmis' || type === 'cloud_udmis') return 4;
    return 5;
  }

  getLogicalEdges() {
    const edges = [];
    const devices = this.nodes.filter(n => this.getNodeLayer(n.type) === 1);
    const ingressNodes = this.nodes.filter(n => this.getNodeLayer(n.type) === 2);
    const fabricNodes = this.nodes.filter(n => this.getNodeLayer(n.type) === 3);
    const cores = this.nodes.filter(n => this.getNodeLayer(n.type) === 4);
    const dbNodes = this.nodes.filter(n => this.getNodeLayer(n.type) === 5 && n.type !== 'ancillary');

    // Devices -> Ingress / Brokers
    devices.forEach(dev => {
      ingressNodes.forEach(ing => {
        edges.push({ source: dev, target: ing, label: 'Telemetry / State' });
      });
    });

    if (fabricNodes.length > 0) {
      // Ingress -> Fabric -> Cores (Zanzara Cloud mode)
      ingressNodes.forEach(ing => {
        fabricNodes.forEach(fab => {
          edges.push({ source: ing, target: fab, label: 'MQTT Proxy' });
        });
      });
      fabricNodes.forEach(fab => {
        cores.forEach(core => {
          edges.push({ source: fab, target: core, label: 'Pub/Sub Bridge' });
        });
      });
    } else {
      // Ingress/Broker -> Cores directly (Local mode)
      ingressNodes.forEach(ing => {
        cores.forEach(core => {
          edges.push({ source: ing, target: core, label: 'Reflective Sync' });
        });
      });
    }

    // Cores -> Databases
    cores.forEach(core => {
      dbNodes.forEach(db => {
        const label = db.type === 'etcd' ? 'KV State' : (db.type === 'influx' ? 'Metrics' : 'Relational');
        edges.push({ source: core, target: db, label: label });
      });
    });

    if (ingressNodes.length === 0 && fabricNodes.length === 0) {
      devices.forEach(dev => {
        cores.forEach(core => {
          edges.push({ source: dev, target: core, label: 'Direct Sync' });
        });
      });
    }

    return edges;
  }

  checkTopologyCompleteness() {
    const badge = document.getElementById('topology-completeness-badge');
    if (badge) {
      if (!this.setupMode) {
        badge.className = 'badge badge-warning';
        badge.style.backgroundColor = '#f1f3f4';
        badge.style.color = '#5f6368';
        badge.innerHTML = `
          <span class="material-symbols-outlined" style="font-size:14px;">info</span>
          <span>Select Setup: Local or Cloud</span>
        `;
      } else if (this.deviceNodes.length > 0) {
        badge.className = 'badge badge-success';
        badge.style.backgroundColor = '#c8e6c9';
        badge.style.color = '#1b5e20';
        badge.innerHTML = `
          <span class="material-symbols-outlined" style="font-size:14px;">check_circle</span>
          <span>COMPLETE SETUP (${this.setupMode})</span>
        `;
      } else {
        badge.className = 'badge badge-warning';
        badge.style.backgroundColor = '#fff3e0';
        badge.style.color = '#e65100';
        badge.innerHTML = `
          <span class="material-symbols-outlined" style="font-size:14px;">warning</span>
          <span>INCOMPLETE: Drag a device onto the canvas</span>
        `;
      }
    }
    stateStore.set('activeDeviceNodesCount', this.deviceNodes.length);
    stateStore.emit('canvas_devices_changed', { count: this.deviceNodes.length });
  }

  renderGraph() {
    if (!this.canvasSvg || !this.nodesLayer) return;

    let svgHtml = `
      <defs>
        <marker id="arrow" viewBox="0 0 10 10" refX="28" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M 0 0 L 10 5 L 0 10 z" fill="#0b57d0" />
        </marker>
      </defs>
    `;

    const logicalEdges = this.getLogicalEdges();
    logicalEdges.forEach(edge => {
      const src = edge.source;
      const tgt = edge.target;
      const x1 = src.x + 97.5;
      const y1 = src.y + 41;
      const x2 = tgt.x + 97.5;
      const y2 = tgt.y + 41;
      const midX = (x1 + x2) / 2;
      const midY = (y1 + y2) / 2;
      const textLen = (edge.label || '').length;
      const pillWidth = Math.max(72, textLen * 6.5 + 16);

      svgHtml += `
        <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" 
              stroke="#0b57d0" stroke-width="2" stroke-dasharray="6,4" marker-end="url(#arrow)" />
        <g transform="translate(${midX}, ${midY})">
          <rect x="-${pillWidth / 2}" y="-10" width="${pillWidth}" height="20" rx="10" 
                fill="#ffffff" stroke="#c2e7ff" stroke-width="1.5" filter="drop-shadow(0 1px 2px rgba(0,0,0,0.06))" />
          <text x="0" y="0" dominant-baseline="central" text-anchor="middle" 
                fill="#0b57d0" font-size="10" font-weight="600" font-family="system-ui, -apple-system, sans-serif">${edge.label}</text>
        </g>
      `;
    });

    this.canvasSvg.innerHTML = svgHtml;
    this.checkTopologyCompleteness();

    this.nodesLayer.innerHTML = '';

    this.nodes.forEach(node => {
      const spec = this.getNodeSpec(node.type);
      const isSelected = node.id === this.selectedNodeId;
      const isMultiSelected = this.selectedNodeIds.size > 1 && this.selectedNodeIds.has(node.id);
      const isDeviceNode = node.type === 'actual_device' || node.type === 'pubber' || this.deviceNodes.some(n => n.id === node.id);
      const devId = node.inputs.device_id || node.label;

      let badgeClass = 'badge-up';
      let badgeContent = node.status || 'DOWN';

      if (node.isTestingRunning) {
        badgeClass = 'badge-init';
        badgeContent = `<span class="spinner-sm"></span> TESTING`;
      } else if (node.status === 'DOWN') {
        badgeClass = 'badge-down';
        badgeContent = 'DOWN';
      } else if (node.status === 'UP') {
        badgeClass = 'badge-up';
        badgeContent = 'UP';
      } else if (node.status === 'INITIALIZING') {
        badgeClass = 'badge-init';
        badgeContent = `<span class="spinner-sm"></span>`;
      } else if (node.status === 'DISABLED') {
        badgeClass = 'badge-disabled';
        badgeContent = 'DISABLED';
      }

      const showBadge = (node.type !== 'actual_device' || node.isTestingRunning) && Boolean(node.status) && node.status !== 'UNAVAILABLE' && node.status !== 'UNKNOWN';

      const card = document.createElement('div');
      const modeNodeClass = isDeviceNode ? (node.type === 'pubber' ? 'is-pubber-node' : 'is-actual-node') : '';
      card.className = `canvas-node ${isDeviceNode ? 'is-device ' + modeNodeClass : ''} ${isSelected ? 'selected' : ''} ${isMultiSelected ? 'multi-selected' : ''}`;
      card.dataset.nodeId = node.id;
      card.style.left = `${node.x}px`;
      card.style.top = `${node.y}px`;

      let etcdUrl = null;
      if (node.type === 'etcd') {
        etcdUrl = window.location && window.location.origin ? `${window.location.origin}/etcd_explorer/` : 'http://localhost:8085';
        if (this.setupMode === 'CLOUD' || (this.projectSpec && !this.projectSpec.includes('localhost'))) {
          const parsed = this.parseProjectSpec(this.projectSpec);
          const proj = parsed.project || 'bos-platform-dev';
          etcdUrl = `https://${proj}.corp.goog/`;
        }
      }

      let headerActionsHtml = '';
      if (isDeviceNode) {
        headerActionsHtml = `
          <button class="btn-node-remove" data-device-id="${devId}" title="Remove device from topology" aria-label="Remove device">
            <span class="material-symbols-outlined">close</span>
          </button>
        `;
      } else if (etcdUrl) {
        headerActionsHtml = `
          <a href="${etcdUrl}" target="_blank" rel="noopener noreferrer" class="btn-node-link" title="Open ETCD Explorer" aria-label="Open ETCD Explorer">
            <span class="material-symbols-outlined" style="font-size: 13px;">open_in_new</span>
          </a>
        `;
      }

      if (isDeviceNode) {
        const isActual = node.type === 'actual_device';
        const isPub = node.type === 'pubber';
        card.innerHTML = `
          <div class="canvas-node-header">
            <span class="material-symbols-outlined canvas-node-icon">${node.icon}</span>
            <span class="canvas-node-title" title="${node.label}">${node.label}</span>
            ${headerActionsHtml}
          </div>
          <div class="canvas-node-footer">
            <div class="canvas-node-footer-left">
              ${showBadge ? `<span class="node-status-badge ${badgeClass}">${badgeContent}</span>` : ''}
              <span class="canvas-node-sub" title="${spec.subText(node)}">${spec.subText(node)}</span>
            </div>
            <button class="node-m3-switch ${isPub ? 'is-pubber' : 'is-physical'}" 
                    data-device-id="${devId}" 
                    data-current-mode="${node.type}"
                    role="switch" 
                    aria-checked="${isPub ? 'true' : 'false'}" 
                    title="${isPub ? 'Switch to Physical Device mode' : 'Switch to Pubber Emulator mode'}"
                    aria-label="${isPub ? 'Switch to Physical Device mode' : 'Switch to Pubber Emulator mode'}">
              <div class="m3-switch-track">
                <div class="m3-switch-thumb">
                  <span class="material-symbols-outlined">${isPub ? 'robot_2' : 'home_iot_device'}</span>
                </div>
              </div>
            </button>
          </div>
        `;
      } else {
        card.innerHTML = `
          <div class="canvas-node-header">
            <span class="material-symbols-outlined canvas-node-icon">${node.icon}</span>
            <span class="canvas-node-title" title="${node.label}">${node.label}</span>
            ${headerActionsHtml}
          </div>
          ${showBadge ? `<span class="node-status-badge ${badgeClass}">${badgeContent}</span>` : ''}
          <span class="canvas-node-sub" title="${spec.subText(node)}">${spec.subText(node)}</span>
        `;
      }

      // Event listeners for remove and switch toggle
      const btnRemove = card.querySelector('.btn-node-remove');
      if (btnRemove) {
        btnRemove.addEventListener('click', (e) => {
          e.stopPropagation();
          this.removeDeviceFromCanvas(devId);
          this.renderSiteDevicesList();
          this.renderGraph();
          this.renderInspector();
          if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
        });
      }

      const btnSwitch = card.querySelector('.node-m3-switch');
      if (btnSwitch) {
        btnSwitch.addEventListener('click', (e) => {
          e.stopPropagation();
          const curMode = btnSwitch.getAttribute('data-current-mode');
          const targetMode = curMode === 'pubber' ? 'actual_device' : 'pubber';
          this.changeDeviceMode(devId, targetMode);
        });
      }

      card.addEventListener('mousedown', (e) => {
        if (e.target.closest('.btn-node-link') || e.target.closest('.btn-node-remove') || e.target.closest('.node-m3-switch')) return;
        e.stopPropagation();
        const rect = card.getBoundingClientRect();
        this.dragOffset = { x: e.clientX - rect.left, y: e.clientY - rect.top };
        this.draggedNodeId = node.id;
        const isMulti = e.shiftKey || e.ctrlKey || e.metaKey;
        this.selectNode(node.id, isMulti, false);
      });

      card.addEventListener('dblclick', (e) => {
        if (e.target.closest('.btn-node-link') || e.target.closest('.btn-node-remove') || e.target.closest('.node-m3-switch')) return;
        e.stopPropagation();
        this.selectNode(node.id, false, true);
      });

      this.nodesLayer.appendChild(card);
    });
  }

  // --- INSPECTOR SIDE PANEL FORM RENDERING ---
  renderInspector() {
    if (!this.inspectorBody) return;

    if (this.selectedNodeIds.size === 0) {
      this.inspectorBody.innerHTML = `
        <div class="inspector-empty">
          <span class="material-symbols-outlined">touch_app</span>
          <p>Select one or more nodes (Shift-click) on the canvas to configure parameters and check health status.</p>
        </div>
      `;
      if (this.inspectorTitle) this.inspectorTitle.textContent = 'Node Inspector';
      if (this.inspectorIcon) this.inspectorIcon.textContent = 'tune';
      return;
    }

    // BATCH MULTI-DEVICE INSPECTOR
    if (this.selectedNodeIds.size > 1) {
      const selectedDevices = Array.from(this.selectedNodeIds).map(id => this.deviceNodes.find(n => n.id === id)).filter(n => n);
      if (this.inspectorTitle) this.inspectorTitle.textContent = `Batch Selection (${selectedDevices.length})`;
      if (this.inspectorIcon) this.inspectorIcon.textContent = 'library_add_check';

      const anyRunning = selectedDevices.some(d => d.isTestingRunning);

      let batchHtml = `
        <div class="inspector-form" style="padding: 4px 0;">
          <div style="font-size: 11px; color: var(--text-secondary); margin-bottom: 6px;">Selected Devices:</div>
          <div style="display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px;">
      `;

      selectedDevices.forEach(d => {
        batchHtml += `
          <div class="batch-chip">
            <span class="material-symbols-outlined" style="font-size: 13px;">${d.icon}</span>
            <span>${d.inputs.device_id || d.label}</span>
            <span class="btn-remove-chip" data-node-id="${d.id}" title="Remove from batch">&times;</span>
          </div>
        `;
      });

      batchHtml += `
          </div>
        </div>
        <div style="margin-top: 6px; padding: 10px; background: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;">
          <strong style="font-size:11px; color:#202124; display:flex; align-items:center; gap:4px; margin-bottom: 6px;">
            <span class="material-symbols-outlined" style="font-size:15px; color:#0b57d0;">checklist</span>
            Batch Sequencer Test Suite
          </strong>
          <div style="display:flex; gap:4px; margin-bottom:8px; flex-wrap:wrap;">
            <button id="btn-batch-smoke" class="btn btn-outlined" style="padding:2px 6px; font-size:10px; height:22px;">⚡ Smoke Test</button>
            <button id="btn-batch-all" class="btn btn-outlined" style="padding:2px 6px; font-size:10px; height:22px;">Select All</button>
            <button id="btn-batch-none" class="btn btn-outlined" style="padding:2px 6px; font-size:10px; height:22px;">Clear</button>
          </div>
          ${!anyRunning ? `
          <button id="btn-run-batch-tests" class="btn btn-primary" style="width: 100%; margin-top: 8px; justify-content: center;">
            <span class="material-symbols-outlined">play_arrow</span>
            <span>Run Tests on ${selectedDevices.length} Selected Devices</span>
          </button>
          ` : `
          <button id="btn-stop-batch-tests" class="btn btn-danger-outlined" style="width: 100%; margin-top: 8px; justify-content: center; font-weight: 600;">
            <span class="material-symbols-outlined">stop</span>
            <span>Stop Running Tests (${selectedDevices.length} Devices)</span>
          </button>
          `}
        </div>
      `;

      this.inspectorBody.innerHTML = batchHtml;

      this.inspectorBody.querySelectorAll('.btn-remove-chip').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const id = btn.getAttribute('data-node-id');
          this.selectNode(id, true);
        });
      });

      const btnBatchRun = document.getElementById('btn-run-batch-tests');
      if (btnBatchRun) {
        btnBatchRun.addEventListener('click', () => {
          selectedDevices.forEach(d => this.runSelectedDeviceTests(d));
        });
      }

      const btnBatchStop = document.getElementById('btn-stop-batch-tests');
      if (btnBatchStop) {
        btnBatchStop.addEventListener('click', () => {
          selectedDevices.forEach(d => this.stopDeviceTests(d));
        });
      }

      const btnSmoke = document.getElementById('btn-batch-smoke');
      if (btnSmoke) {
        btnSmoke.addEventListener('click', () => {
          selectedDevices.forEach(d => { d.selectedTests = new Set(this.getSmokeTestIds()); });
        });
      }
      return;
    }

    // SINGLE NODE INSPECTOR
    const node = this.nodes.find(n => n.id === this.selectedNodeId);
    if (!node) return;

    const spec = this.getNodeSpec(node.type);
    const isDeviceNode = this.deviceNodes.some(n => n.id === node.id);
    const isActualDevice = node.type === 'actual_device';
    const isCloudComponent = node.type === 'zanzara_ingress' || node.type === 'zanzara_fabric' || node.type === 'cloud_udmis' || (this.setupMode === 'CLOUD' && node.type === 'etcd');

    if (this.inspectorTitle) this.inspectorTitle.textContent = node.label;
    if (this.inspectorIcon) this.inspectorIcon.textContent = node.icon;

    let formHtml = `
      <div class="inspector-form" style="padding: 4px 0;">
        ${node.healthDetails && node.status && node.status !== 'UNAVAILABLE' ? `
        <div style="margin-bottom: 8px; padding: 6px 8px; border-radius: 6px; font-size: 11px; display: flex; align-items: flex-start; gap: 6px; background: ${node.status === 'UP' ? '#e6f4ea' : '#fce8e6'}; color: ${node.status === 'UP' ? '#137333' : '#c5221f'}; border: 1px solid ${node.status === 'UP' ? '#ceead6' : '#fad2cf'};">
          <span class="material-symbols-outlined" style="font-size: 15px; margin-top: -1px;">${node.status === 'UP' ? 'check_circle' : 'error'}</span>
          <span style="line-height: 1.3; font-weight: 500;">${node.healthDetails}</span>
        </div>
        ` : ''}
        <div style="background: #f8f9fa; padding: 8px 10px; border: 1px solid #e0e0e0; border-radius: 8px; margin-bottom: 8px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 6px;">
            <span style="font-size:10px; font-weight:700; color:#5f6368; text-transform:uppercase;">Node Config</span>
            <div style="display:flex; align-items:center; gap:4px;">
              <button id="btn-inspector-edit-inputs" class="btn btn-icon-xs btn-outlined" title="Edit Node Inputs / Parameters" aria-label="Edit Inputs" style="width:24px; height:24px; padding:0; justify-content:center;">
                <span class="material-symbols-outlined" style="font-size:14px;">tune</span>
              </button>
              ${!isActualDevice ? `
              <button id="btn-node-health" class="btn btn-icon-xs btn-outlined" title="Run Node Health Check" aria-label="Health Check" style="width:24px; height:24px; padding:0; justify-content:center;">
                <span class="material-symbols-outlined" style="font-size:14px;">health_and_safety</span>
              </button>
              <button id="btn-toggle-node" class="btn btn-icon-xs btn-outlined ${isCloudComponent ? 'disabled' : ''}" ${isCloudComponent ? 'disabled' : ''} title="${isCloudComponent ? 'Remote Cloud Service (Managed remotely)' : (node.status === 'UP' ? 'Stop Node' : 'Start Node')}" aria-label="${node.status === 'UP' ? 'Stop Node' : 'Start Node'}" style="width:24px; height:24px; padding:0; justify-content:center; ${isCloudComponent ? 'opacity: 0.4; cursor: not-allowed;' : ''}">
                <span class="material-symbols-outlined" style="font-size:14px;">${node.status === 'UP' ? 'power_settings_new' : 'play_arrow'}</span>
              </button>
              ` : ''}
              ${isDeviceNode ? `
              <button id="btn-delete-node" class="btn btn-icon-xs btn-danger-outlined" title="Delete Device Node from Canvas" aria-label="Delete Node" style="width:24px; height:24px; padding:0; justify-content:center;">
                <span class="material-symbols-outlined" style="font-size:14px;">delete</span>
              </button>
              ` : ''}
            </div>
          </div>
          <div style="font-size: 11px; color: #202124; line-height: 1.4;">
            ${Object.entries(node.inputs).map(([k, v]) => `<div><span style="color:#5f6368;">${k.replace(/_/g, ' ')}:</span> <strong style="font-family:monospace;">${v}</strong></div>`).join('')}
          </div>
        </div>
      </div>
    `;

    if (isDeviceNode) {
      if (!node.selectedTests || node.selectedTests.size === 0) {
        node.selectedTests = new Set(this.getSmokeTestIds());
      }

      let testSuiteHtml = `
        <div style="margin-top: 16px; padding: 12px; background: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 8px;">
            <strong style="font-size:12px; color:#202124; display:flex; align-items:center; gap:4px;">
              <span class="material-symbols-outlined" style="font-size:16px; color:#0b57d0;">checklist</span>
              Sequencer Test Selection (${node.selectedTests.size})
            </strong>
          </div>

          <div style="display:flex; gap:4px; margin-bottom:10px; flex-wrap:wrap;">
            <button id="btn-preset-smoke" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;" title="Select Core Smoke Tests">⚡ Smoke</button>
            <button id="btn-preset-stable" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;" title="Select All STABLE Tests">⭐ Stable</button>
            <button id="btn-preset-rerun" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;" ${(!node.testResults || !Object.values(node.testResults).some(r => r.status === 'FAIL' || r.status === 'FAILED')) ? 'disabled' : ''}>🔄 Re-run Failures</button>
            <button id="btn-preset-all" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">All</button>
            <button id="btn-preset-none" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">Clear</button>
          </div>

          <input type="text" id="inp-test-filter" placeholder="Filter sequences (e.g. extra_config, pointset)..." class="form-input" style="font-size:11px; padding:4px 8px; margin-bottom:8px; width:100%; box-sizing:border-box;" />

          <div id="test-tree-container" style="max-height: 220px; overflow-y: auto; border: 1px solid #f1f3f4; border-radius: 6px; padding: 6px; background: #fafafa;">
            ${SEQUENCER_TEST_CATALOG.length === 0 ? `
              <div style="font-size: 11px; color: #5f6368; padding: 16px 8px; text-align: center;">
                <span class="spinner-sm" style="margin-bottom: 4px;"></span>
                <div>Loading sequence test catalog from generated.md...</div>
              </div>
            ` : ''}
      `;

      SEQUENCER_TEST_CATALOG.forEach(cat => {
        testSuiteHtml += `
          <div class="test-category-block" style="margin-bottom: 8px;">
            <div style="font-size: 11px; font-weight: 700; color: #5f6368; display: flex; align-items: center; justify-content: space-between; padding-bottom: 3px; border-bottom: 1px solid #e8eaed;">
              <div style="display: flex; align-items: center; gap: 4px;">
                <span class="material-symbols-outlined" style="font-size:14px; color:#5f6368;">${cat.icon}</span>
                <span>${cat.category}</span>
              </div>
              <span style="font-size: 10px; color: #80868b; font-weight: normal;">(${cat.tests.length})</span>
            </div>
            <div style="padding-left: 4px; margin-top: 4px; display: flex; flex-direction: column; gap: 2px;">
        `;

        cat.tests.forEach(test => {
          const isChecked = node.selectedTests.has(test.id);
          const stage = (test.stage || 'STABLE').toUpperCase();
          const stageStyle = stage === 'STABLE'
            ? 'background: #e6f4ea; color: #137333; border: 1px solid #ceead6;'
            : (stage === 'BETA'
              ? 'background: #fef7e0; color: #b06000; border: 1px solid #feefc3;'
              : 'background: #f3e8fd; color: #7c3aed; border: 1px solid #e9d5ff;');

          testSuiteHtml += `
            <label class="test-item-row" data-test-id="${test.id}" data-category="${cat.category}" style="display: flex; align-items: center; justify-content: space-between; gap: 6px; font-size: 11px; color: #3c4043; cursor: pointer; padding: 2px 4px; border-radius: 4px; background: #ffffff; border: 1px solid #f1f3f4;" title="${test.desc}">
              <div style="display: flex; align-items: center; gap: 6px; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                <input type="checkbox" class="chk-test-item" value="${test.id}" ${isChecked ? 'checked' : ''} />
                <span style="font-family: monospace; font-size: 11px; color: #202124;">${test.name}</span>
              </div>
              <span style="font-size: 9px; padding: 1px 4px; border-radius: 3px; font-weight: 700; flex-shrink: 0; ${stageStyle}">${stage}</span>
            </label>
          `;
        });

        testSuiteHtml += `</div></div>`;
      });

      testSuiteHtml += `</div>`;

      if (!node.isTestingRunning) {
        testSuiteHtml += `
          <button id="btn-run-device-tests" class="btn btn-primary" style="width: 100%; margin-top: 10px; justify-content: center;">
            <span class="material-symbols-outlined">play_arrow</span>
            <span>Run ${node.selectedTests.size} Selected Tests</span>
          </button>
        `;
      } else {
        testSuiteHtml += `
          <button id="btn-stop-device-tests" class="btn btn-danger-outlined" style="width: 100%; margin-top: 10px; justify-content: center; font-weight: 600;">
            <span class="material-symbols-outlined">stop</span>
            <span>Stop Running Tests (Abort)</span>
          </button>
        `;
      }
      testSuiteHtml += `</div>`;

      if (node.testResults) {
        const results = Object.entries(node.testResults);
        const failCount = results.filter(([_, r]) => r.status === 'FAIL' || r.status === 'FAILED').length;
        const passCount = results.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;

        testSuiteHtml += `
          <div style="margin-top: 12px; padding: 12px; background: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
              <strong style="font-size: 12px; color: #202124;">Test Results</strong>
              <span class="badge ${failCount > 0 ? 'badge-down' : 'badge-up'}">
                ${failCount > 0 ? `⚠️ ${failCount} Failed, ${passCount} Passed` : `✅ ${passCount} Passed`}
              </span>
            </div>
            <div style="display: flex; flex-direction: column; gap: 6px; max-height: 200px; overflow-y: auto;">
        `;

        results.forEach(([tId, r]) => {
          const isFail = r.status === 'FAIL' || r.status === 'FAILED';
          testSuiteHtml += `
            <div style="padding: 6px 8px; background: ${isFail ? '#fce8e6' : '#e6f4ea'}; border-radius: 6px; font-size: 11px;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span style="font-weight: 600; font-family: monospace; color: ${isFail ? '#c5221f' : '#137333'};">
                  ${isFail ? '❌' : '✅'} ${tId}
                </span>
                <span style="color: #5f6368; font-size: 10px;">${r.duration || r.timestamp || ''}</span>
              </div>
              ${r.message ? `<div style="font-size: 10px; color: #5f6368; margin-top: 2px;">${r.message}</div>` : ''}
              ${isFail ? `
                <div style="margin-top: 6px; display: flex; gap: 6px;">
                  <button class="btn btn-outlined btn-diagnose-mantis" data-test-id="${tId}" style="padding: 2px 8px; font-size: 10px; height: 22px; color: #6d28d9; border-color: #d8b4fe; background: #fdf4ff; width: 100%; justify-content: center;">
                    <span class="material-symbols-outlined" style="font-size: 13px;">auto_awesome</span>
                    <span>Diagnose with Mantis AI</span>
                  </button>
                </div>
              ` : ''}
            </div>
          `;
        });

        testSuiteHtml += `</div></div>`;
      }

      formHtml += testSuiteHtml;
    }

    this.inspectorBody.innerHTML = formHtml;

    const btnEditInputs = document.getElementById('btn-inspector-edit-inputs');
    if (btnEditInputs) {
      btnEditInputs.addEventListener('click', () => this.openNodeModal(node));
    }

    if (isDeviceNode) {
      const chkItems = document.querySelectorAll('.chk-test-item');
      chkItems.forEach(chk => {
        chk.addEventListener('change', (e) => {
          if (e.target.checked) node.selectedTests.add(e.target.value);
          else node.selectedTests.delete(e.target.value);
          this.renderInspector();
        });
      });

      const inpFilter = document.getElementById('inp-test-filter');
      if (inpFilter) {
        inpFilter.addEventListener('input', (e) => {
          const q = e.target.value.toLowerCase().trim();
          document.querySelectorAll('.test-item-row').forEach(row => {
            const tId = (row.getAttribute('data-test-id') || '').toLowerCase();
            const title = (row.getAttribute('title') || '').toLowerCase();
            row.style.display = (tId.includes(q) || title.includes(q)) ? 'flex' : 'none';
          });
        });
      }

      const btnPresetSmoke = document.getElementById('btn-preset-smoke');
      if (btnPresetSmoke) {
        btnPresetSmoke.addEventListener('click', () => {
          node.selectedTests = new Set(this.getSmokeTestIds());
          this.renderInspector();
        });
      }

      const btnPresetStable = document.getElementById('btn-preset-stable');
      if (btnPresetStable) {
        btnPresetStable.addEventListener('click', () => {
          const stableIds = [];
          SEQUENCER_TEST_CATALOG.forEach(c => c.tests.forEach(t => {
            if ((t.stage || 'STABLE').toUpperCase() === 'STABLE') stableIds.push(t.id);
          }));
          node.selectedTests = new Set(stableIds);
          this.renderInspector();
        });
      }

      const btnPresetRerun = document.getElementById('btn-preset-rerun');
      if (btnPresetRerun) {
        btnPresetRerun.addEventListener('click', () => {
          if (node.testResults) {
            const failedIds = Object.entries(node.testResults).filter(([_, r]) => r.status === 'FAIL' || r.status === 'FAILED').map(([id]) => id);
            node.selectedTests = new Set(failedIds);
            this.renderInspector();
          }
        });
      }

      const btnPresetAll = document.getElementById('btn-preset-all');
      if (btnPresetAll) {
        btnPresetAll.addEventListener('click', () => {
          const allIds = [];
          SEQUENCER_TEST_CATALOG.forEach(c => c.tests.forEach(t => allIds.push(t.id)));
          node.selectedTests = new Set(allIds);
          this.renderInspector();
        });
      }

      const btnPresetNone = document.getElementById('btn-preset-none');
      if (btnPresetNone) {
        btnPresetNone.addEventListener('click', () => {
          node.selectedTests.clear();
          this.renderInspector();
        });
      }

      const btnRunTests = document.getElementById('btn-run-device-tests');
      if (btnRunTests) btnRunTests.addEventListener('click', () => this.runSelectedDeviceTests(node));

      const btnStopTests = document.getElementById('btn-stop-device-tests');
      if (btnStopTests) btnStopTests.addEventListener('click', () => this.stopDeviceTests(node));

      const btnDiagnoseMantis = document.querySelectorAll('.btn-diagnose-mantis');
      btnDiagnoseMantis.forEach(btn => {
        btn.addEventListener('click', (e) => {
          const testId = btn.getAttribute('data-test-id');
          this.triggerMantisForTest(node, testId);
        });
      });
    }

    const btnHealth = document.getElementById('btn-node-health');
    if (btnHealth) btnHealth.addEventListener('click', () => this.runHealthCheckForNode(node));

    const btnToggle = document.getElementById('btn-toggle-node');
    if (btnToggle && !isCloudComponent) {
      btnToggle.addEventListener('click', async () => {
        if (node.type === 'pubber') {
          const devId = node.inputs.device_id || node.label;
          if (node.status === 'UP') {
            await this.stopPubberForDevice(devId);
          } else {
            await this.startPubberForDevice(node);
          }
          await this.runHealthCheckForNode(node);
          return;
        }
        const action = node.status === 'UP' ? 'stop' : 'start';
        node.status = 'INITIALIZING';
        this.renderGraph();
        this.renderInspector();
        try {
          const endpoint = action === 'start' ? '/api/testbed/start_component' : '/api/testbed/stop_component';
          await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ component: node.type, site_model: this.siteModel, project_spec: this.projectSpec })
          });
        } catch (e) {
          console.error("Component toggle error:", e);
        }
        await this.runHealthCheckForNode(node);
      });
    }

    const btnDelete = document.getElementById('btn-delete-node');
    if (btnDelete) btnDelete.addEventListener('click', () => this.deleteNode(node.id));
  }

  // --- TRUE SEQUENCER TEST EXECUTION & INTUITIVE STOP CONTROL ---
  async runSelectedDeviceTests(node) {
    if (!node || node.selectedTests.size === 0) return;
    const deviceId = node.inputs.device_id || 'AHU-1';

    node.isTestingRunning = true;
    node.currentRunTests = new Set(node.selectedTests);
    node.activeRunResults = {};
    if (!node.testResults) node.testResults = {};
    for (const testId of node.currentRunTests) {
      node.activeRunResults[testId] = { status: 'RUNNING', message: 'Executing...' };
      node.testResults[testId] = { status: 'RUNNING', message: 'Executing...' };
    }
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
    this.updateExecutionControlsState();

    if (this.suiteProgressFill) this.suiteProgressFill.style.width = '0%';
    if (this.metricPassed) this.metricPassed.textContent = '0';
    if (this.metricFailed) this.metricFailed.textContent = '0';
    if (this.metricSkipped) this.metricSkipped.textContent = '0';
    if (this.metricTime) this.metricTime.textContent = '00:00';

    if (this.testTimerInterval) {
      clearInterval(this.testTimerInterval);
    }
    this.testStartTime = Date.now();
    this.testTimerInterval = setInterval(() => {
      if (this.metricTime && this.testStartTime) {
        const elapsedSec = Math.floor((Date.now() - this.testStartTime) / 1000);
        const mm = String(Math.floor(elapsedSec / 60)).padStart(2, '0');
        const ss = String(elapsedSec % 60).padStart(2, '0');
        this.metricTime.textContent = `${mm}:${ss}`;
      }
    }, 1000);

    if (this.logViewer) {
      this.logViewer.append(`\n▶️ Starting Sequencer test suite against device [${deviceId}] via ${this.projectSpec}...`, 'info');
      this.switchViewMode('logs');
    }

    try {
      const res = await fetch('/api/run_sequencer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          site_model: this.siteModel,
          project_spec: this.projectSpec,
          device_id: deviceId,
          tests: Array.from(node.selectedTests),
          log_level: 'INFO',
          min_stage: 'PREVIEW'
        })
      });

      if (!res.ok) throw new Error(`Server returned HTTP ${res.status}`);
      const data = await res.json();
      node.lastSessionId = data.session_id;

      if (this.logViewer) {
        this.logViewer.append(`✅ Sequencer process spawned successfully (PID: ${data.pid}, Session: ${data.session_id}). Streaming logs...`, 'success');
      }

      if (navigator.serviceWorker && navigator.serviceWorker.controller) {
        navigator.serviceWorker.controller.postMessage({
          type: 'START_MONITORING',
          sessionId: data.session_id,
          deviceId: deviceId,
          siteModel: this.siteModel
        });
      }

      // Connect real-time log polling
      this.logOffsets.set(data.session_id, 0);
      const interval = setInterval(() => this.pollSequencerStatus(node, data.session_id), 600);
      this.activePolls.set(data.session_id, interval);
    } catch (e) {
      console.error("Sequencer run failed:", e);
      if (this.logViewer) {
        this.logViewer.append(`❌ ERROR starting sequencer process: ${e.message}`, 'error');
      }
      if (this.testTimerInterval) {
        clearInterval(this.testTimerInterval);
        this.testTimerInterval = null;
      }
      node.isTestingRunning = false;
      this.renderGraph();
      this.renderInspector();
      this.updateExecutionControlsState();
    }
  }

  parseSequencerLogLineForProgress(node, line) {
    if (!node) return false;
    if (!node.testResults) node.testResults = {};
    if (!node.activeRunResults) node.activeRunResults = {};
    const trimmed = line.trim();

    // Match standard RESULT output: RESULT PASS/FAIL/SKIP/ERROR <bucket> <test_name> ...
    // e.g. "RESULT PASS beta extra_config BETA 1/1 Sequence completed successfully"
    // or "RESULT START beta extra_config BETA 0/1 ..."
    const resultMatch = trimmed.match(/^RESULT\s+(START|PASS|FAIL|SKIP|ERROR)\s+(?:\S+\s+)?([a-zA-Z0-9_-]+)(?:\s+\S+\s+\S+)?(?:\s+(.*))?/i);
    if (resultMatch) {
      const status = resultMatch[1].toUpperCase();
      const testId = resultMatch[2];
      const msg = resultMatch[3] || '';
      if (status === 'PASS') {
        const entry = { status: 'PASS', message: msg || 'Passed' };
        node.testResults[testId] = entry;
        node.activeRunResults[testId] = entry;
        return true;
      } else if (status === 'FAIL' || status === 'ERROR') {
        const entry = { status: 'FAIL', message: msg || 'Failed' };
        node.testResults[testId] = entry;
        node.activeRunResults[testId] = entry;
        return true;
      } else if (status === 'SKIP') {
        const entry = { status: 'SKIP', message: msg || 'Skipped' };
        node.testResults[testId] = entry;
        node.activeRunResults[testId] = entry;
        return true;
      } else if (status === 'START') {
        const entry = { status: 'RUNNING', message: 'Executing...' };
        node.testResults[testId] = entry;
        node.activeRunResults[testId] = entry;
        return true;
      }
    }

    // Match Start / Begin test logs:
    const startMatch = trimmed.match(/(?:Begin\s+test|Starting\s+test|Start\s+test)\s+([a-zA-Z0-9_-]+)/i);
    if (startMatch) {
      const testId = startMatch[1];
      if (!node.testResults[testId] || node.testResults[testId].status === 'RUNNING') {
        const entry = { status: 'RUNNING', message: 'Executing...' };
        node.testResults[testId] = entry;
        node.activeRunResults[testId] = entry;
        return true;
      }
    }

    // Match PASSED / FAILED / SKIPPED prefix:
    const altMatch = trimmed.match(/^(?:\[.*?\]\s*)?(PASSED|FAILED|SKIPPED|PASS|FAIL|SKIP):\s*([a-zA-Z0-9_-]+)(?:\s*-\s*(.*))?/i);
    if (altMatch) {
      const rawStatus = altMatch[1].toUpperCase();
      const testId = altMatch[2];
      const msg = altMatch[3] || '';
      const status = (rawStatus === 'PASSED' || rawStatus === 'PASS') ? 'PASS' : ((rawStatus === 'SKIPPED' || rawStatus === 'SKIP') ? 'SKIP' : 'FAIL');
      const entry = { status: status, message: msg || (status === 'PASS' ? 'Passed' : (status === 'SKIP' ? 'Skipped' : 'Failed')) };
      node.testResults[testId] = entry;
      node.activeRunResults[testId] = entry;
      return true;
    }

    return false;
  }

  updateActiveExecutionProgress(node) {
    if (!node) return;
    const runTests = node.currentRunTests && node.currentRunTests.size > 0
      ? Array.from(node.currentRunTests)
      : (node.selectedTests && node.selectedTests.size > 0 ? Array.from(node.selectedTests) : Object.keys(node.activeRunResults || {}));

    const totalSelected = runTests.length;
    const results = runTests.map(tId => node.activeRunResults?.[tId] || node.testResults?.[tId]).filter(Boolean);

    const passed = results.filter(r => r.status === 'PASS' || r.status === 'PASSED').length;
    const failed = results.filter(r => r.status === 'FAIL' || r.status === 'FAILED').length;
    const skipped = results.filter(r => r.status === 'SKIP' || r.status === 'SKIPPED').length;
    const finished = passed + failed + skipped;

    if (this.metricPassed) this.metricPassed.textContent = passed;
    if (this.metricFailed) this.metricFailed.textContent = failed;
    if (this.metricSkipped) this.metricSkipped.textContent = skipped;

    const pct = totalSelected > 0 ? Math.min(100, Math.round((finished / totalSelected) * 100)) : 0;
    if (this.suiteProgressFill) {
      this.suiteProgressFill.style.width = `${pct}%`;
    }

    if (this.isInspectorOpen && this.selectedNodeId === node.id) {
      this.renderInspector();
    }
  }

  async pollSequencerStatus(node, sessionId) {
    try {
      const offset = this.logOffsets.get(sessionId) || 0;
      const res = await fetch(`/api/sequencer_status?session_id=${sessionId}&offset=${offset}`);
      if (!res.ok) return;
      const data = await res.json();

      if (data.log && data.log.trim() !== '' && this.logViewer) {
        const lines = data.log.split('\n');
        let hadProgress = false;
        lines.forEach(line => {
          if (line.trim()) {
            if (this.parseSequencerLogLineForProgress(node, line)) {
              hadProgress = true;
            }
            const type = line.includes('ERROR') || line.includes('FAIL') ? 'error' : (line.includes('PASS') || line.includes('SUCCESS') ? 'success' : 'info');
            this.logViewer.append(line, type);
          }
        });
        if (hadProgress) {
          this.updateActiveExecutionProgress(node);
        }
      }
      this.logOffsets.set(sessionId, data.offset || offset);

      if (!data.running) {
        if (this.testTimerInterval) {
          clearInterval(this.testTimerInterval);
          this.testTimerInterval = null;
        }
        const interval = this.activePolls.get(sessionId);
        if (interval) {
          clearInterval(interval);
          this.activePolls.delete(sessionId);
        }
        if (this.logViewer) {
          this.logViewer.append(`\n🏁 Test sequence execution finished with exit code: ${data.exit_code}. Reading results from disk...`, data.exit_code === 0 ? 'success' : 'warn');
        }
        node.isTestingRunning = false;
        await this.fetchDeviceResultsFromDisk(node);
        this.updateActiveExecutionProgress(node);
        if (this.suiteProgressFill) {
          this.suiteProgressFill.style.width = '100%';
        }
        this.renderGraph();
        this.renderInspector();
        if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
        this.updateExecutionControlsState();

        // Check for test failures strictly within tests executed in THIS run
        const executedTestIds = node.currentRunTests && node.currentRunTests.size > 0
          ? Array.from(node.currentRunTests)
          : (node.selectedTests ? Array.from(node.selectedTests) : []);

        if (executedTestIds.length > 0) {
          const runResults = executedTestIds.map(tId => ({
            testId: tId,
            result: node.activeRunResults?.[tId] || node.testResults?.[tId]
          })).filter(entry => entry.result);

          const currentFailures = runResults.filter(e => e.result.status === 'FAIL' || e.result.status === 'FAILED');
          const currentPasses = runResults.filter(e => e.result.status === 'PASS' || e.result.status === 'PASSED');

          if (currentFailures.length > 0) {
            const failingTestIds = currentFailures.map(f => f.testId);
            const targetTestSpec = failingTestIds.join(', ');
            NotificationManager.notify({
              title: "⚠️ Test Failures Detected",
              body: `Device [${node.inputs.device_id || node.label}]: ${currentFailures.length} failed (${targetTestSpec}), ${currentPasses.length} passed. Mantis AI is diagnosing the failure(s)...`,
              type: "warning",
              duration: 8000
            });
            this.triggerMantisForTest(node, targetTestSpec, true /* autoRun */);
          } else if (currentPasses.length > 0) {
            NotificationManager.notify({
              title: "✅ Test Suite Passed",
              body: `Device [${node.inputs.device_id || node.label}]: All ${currentPasses.length} executed tests passed successfully!`,
              type: "success",
              duration: 6000
            });
          }

          this.checkAndDispatchEmailResultAlert(node, currentPasses.length, currentFailures.length);
        }
      }
    } catch (e) {
      console.error("Poll status error:", e);
    }
  }

  async stopDeviceTests(node) {
    if (this.testTimerInterval) {
      clearInterval(this.testTimerInterval);
      this.testTimerInterval = null;
    }
    if (!node || !node.lastSessionId) {
      node.isTestingRunning = false;
      this.renderGraph();
      this.renderInspector();
      this.updateExecutionControlsState();
      return;
    }

    if (this.logViewer) {
      this.logViewer.append(`\nAbort signal dispatched by user. Stopping session ${node.lastSessionId}...`, 'warn');
    }

    try {
      await fetch('/api/stop_sequencer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_id: node.lastSessionId })
      });
    } catch (e) {
      console.error("Error stopping sequencer:", e);
    }

    if (navigator.serviceWorker && navigator.serviceWorker.controller) {
      navigator.serviceWorker.controller.postMessage({ type: 'STOP_MONITORING', sessionId: node.lastSessionId });
    }

    const interval = this.activePolls.get(node.lastSessionId);
    if (interval) {
      clearInterval(interval);
      this.activePolls.delete(node.lastSessionId);
    }
    node.isTestingRunning = false;
    await this.fetchDeviceResultsFromDisk(node);
    this.updateActiveExecutionProgress(node);
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
    this.updateExecutionControlsState();
  }

  async runAllMatrixTests() {
    for (const dev of this.deviceNodes) {
      await this.runSelectedDeviceTests(dev);
    }
  }

  stopAllRunningTests() {
    this.deviceNodes.forEach(dev => {
      if (dev.isTestingRunning) {
        this.stopDeviceTests(dev);
      }
    });
  }

  updateExecutionControlsState() {
    const anyRunning = this.deviceNodes.some(d => d.isTestingRunning);
    if (this.btnMatrixStopAll) this.btnMatrixStopAll.style.display = anyRunning ? 'inline-flex' : 'none';
    if (this.btnLogsStop) this.btnLogsStop.style.display = anyRunning ? 'inline-flex' : 'none';
    if (this.suiteStatusBadge) {
      this.suiteStatusBadge.textContent = anyRunning ? 'Running...' : 'Idle';
      this.suiteStatusBadge.className = anyRunning ? 'badge badge-init' : 'badge badge-neutral';
    }
  }

  async fetchDeviceResultsFromDisk(node) {
    if (!node || !node.currentRunTests || node.currentRunTests.size === 0) return;
    const devId = node.inputs.device_id || node.label;
    try {
      const res = await fetch(`/api/device_results?site_model=${encodeURIComponent(this.siteModel)}&device=${encodeURIComponent(devId)}`);
      if (res.ok) {
        const data = await res.json();
        if (data.results && Object.keys(data.results).length > 0) {
          if (!node.testResults) node.testResults = {};
          if (!node.activeRunResults) node.activeRunResults = {};
          for (const tId of node.currentRunTests) {
            if (data.results[tId]) {
              node.testResults[tId] = data.results[tId];
              node.activeRunResults[tId] = data.results[tId];
            }
          }
        }
      }
    } catch (e) {
      console.error("Error fetching device results:", e);
    }
  }

  // --- DIFFERENTIAL LOG ANALYSIS (DIFF TAB) ---
  populateDiffBaselines() {
    if (!this.diffBaselineSelect) return;
    this.diffBaselineSelect.innerHTML = '<option value="">-- Select Reference Baseline --</option><option value="out_orig">Previous Successful Run (out_orig)</option><option value="out_prev">Latest Archived Baseline (out_prev)</option>';
  }

  async runLogDiff() {
    if (!this.diffViewerBody) return;
    const activeDev = stateStore.get('activeDevice') || (this.deviceNodes[0] ? this.deviceNodes[0].inputs.device_id : 'AHU-1');
    const targetTest = 'system.base.telemetry';

    this.diffViewerBody.innerHTML = '<div class="diff-placeholder"><span class="spinner-sm"></span> Comparing execution log against baseline...</div>';

    try {
      const res = await fetch('/api/log_diff', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          site_model: this.siteModel,
          device_id: activeDev,
          test_id: targetTest,
          current_session_id: null,
          baseline_session_id: this.diffBaselineSelect ? this.diffBaselineSelect.value : null
        })
      });

      if (!res.ok) throw new Error(`HTTP error ${res.status}`);
      const data = await res.json();
      const diffLines = data.diff_lines || [];

      if (diffLines.length === 0) {
        this.diffViewerBody.innerHTML = '<div class="diff-placeholder">No log discrepancies detected between active test run and baseline execution.</div>';
        return;
      }

      this.diffViewerBody.innerHTML = '';
      diffLines.forEach(item => {
        const div = document.createElement('div');
        div.className = `diff-line ${item.type}`;
        const prefix = item.type === 'removed' ? '- ' : (item.type === 'added' ? '+ ' : '  ');
        div.textContent = prefix + item.line;
        this.diffViewerBody.appendChild(div);
      });
    } catch (e) {
      this.diffViewerBody.innerHTML = `<div class="diff-placeholder" style="color:var(--color-error)">Failed to calculate log diff: ${e.message}</div>`;
    }
  }

  triggerMantisForTest(node, testId, autoRun = false) {
    const deviceId = node.inputs?.device_id || node.label || 'AHU-1';
    const data = {
      deviceId: deviceId,
      device_id: deviceId,
      testId: testId,
      test_id: testId,
      siteModel: this.siteModel,
      site_model: this.siteModel,
      projectSpec: this.projectSpec,
      project_spec: this.projectSpec,
      sessionId: node.lastSessionId,
      session_id: node.lastSessionId,
      autoRun: autoRun
    };
    stateStore.emit('open_mantis_triage', data);
    window.postMessage({ type: 'open_mantis_triage', ...data }, '*');
  }

  // --- MULTI-DEVICE COMPLIANCE MATRIX OVERLAY RENDERING ---
  renderComplianceMatrix() {
    if (!this.matrixTableBody) return;

    const devices = this.deviceNodes;
    if (this.matrixKpiTotal) this.matrixKpiTotal.textContent = devices.length;

    let totalPassed = 0;
    let totalFailed = 0;
    let tableHtml = '';

    if (devices.length === 0) {
      this.matrixTableBody.innerHTML = `
        <tr>
          <td colspan="6" style="text-align: center; padding: 36px; color: var(--text-muted);">
            No configured devices found on the canvas or site model. Switch to Topology Canvas view to drag devices.
          </td>
        </tr>
      `;
      if (this.matrixKpiScore) this.matrixKpiScore.textContent = '0%';
      if (this.matrixKpiPassed) this.matrixKpiPassed.textContent = '0';
      if (this.matrixKpiFailed) this.matrixKpiFailed.textContent = '0';
      return;
    }

    devices.forEach(dev => {
      const devId = dev.inputs.device_id || dev.label;
      const subInfo = dev.type === 'pubber' ? `Emulator (Serial: ${dev.inputs.serial_no || 'N/A'})` : `Physical Target (${dev.inputs.protocol || 'IP'})`;

      let overallPill = `<span class="status-pill idle">⚪ Not Tested</span>`;
      let sysPill = `<span class="status-pill idle">⚪ 0/3</span>`;
      let ptPill = `<span class="status-pill idle">⚪ 0/3</span>`;
      let gwPill = `<span class="status-pill idle">⚪ 0/2</span>`;
      let firstFailId = null;

      if (dev.isTestingRunning) {
        overallPill = `<span class="status-pill running"><span class="spinner-sm"></span> Testing...</span>`;
      } else if (dev.testResults && Object.keys(dev.testResults).length > 0) {
        const results = Object.entries(dev.testResults);
        const devPass = results.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;
        const devFail = results.filter(([_, r]) => r.status === 'FAIL' || r.status === 'FAILED').length;

        totalPassed += devPass;
        totalFailed += devFail;

        const failingEntry = results.find(([_, r]) => r.status === 'FAIL' || r.status === 'FAILED');
        if (failingEntry) firstFailId = failingEntry[0];

        if (devFail > 0) {
          overallPill = `<span class="status-pill fail">⚠️ ${devFail} Failed, ${devPass} Pass</span>`;
        } else if (devPass > 0) {
          overallPill = `<span class="status-pill pass">✅ 100% (${devPass} Pass)</span>`;
        }

        const sysTests = results.filter(([t]) => t.startsWith('system_') || ['broken_config', 'extra_config', 'device_config_acked', 'config_logging', 'valid_serial_no', 'state_make_model', 'state_software'].includes(t));
        const sysPass = sysTests.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;
        if (sysTests.length > 0) sysPill = (sysPass === sysTests.length ? `<span class="status-pill pass">✅ ${sysPass}/${sysTests.length}</span>` : `<span class="status-pill fail">❌ ${sysTests.length - sysPass} Fail</span>`);

        const ptTests = results.filter(([t]) => t.startsWith('pointset_'));
        const ptPass = ptTests.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;
        if (ptTests.length > 0) ptPill = (ptPass === ptTests.length ? `<span class="status-pill pass">✅ ${ptPass}/${ptTests.length}</span>` : `<span class="status-pill fail">❌ ${ptTests.length - ptPass} Fail</span>`);

        const gwTests = results.filter(([t]) => t.startsWith('gateway_') || t.startsWith('bad_'));
        const gwPass = gwTests.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;
        if (gwTests.length > 0) gwPill = (gwPass === gwTests.length ? `<span class="status-pill pass">✅ ${gwPass}/${gwTests.length}</span>` : `<span class="status-pill fail">❌ ${gwTests.length - gwPass} Fail</span>`);
      }

      tableHtml += `
        <tr>
          <td>
            <div class="device-cell" style="cursor: pointer;" data-node-id="${dev.id}" title="Jump to Live Log Stream">
              <span class="material-symbols-outlined" style="font-size: 20px; color: var(--color-primary);">${dev.icon}</span>
              <div>
                <div>${devId}</div>
                <div class="device-cell-sub">${subInfo}</div>
              </div>
            </div>
          </td>
          <td>${overallPill}</td>
          <td>${sysPill}</td>
          <td>${ptPill}</td>
          <td>${gwPill}</td>
          <td>
            <div style="display: flex; gap: 8px; align-items: center;">
              ${!dev.isTestingRunning ? `
              <button class="btn btn-outlined btn-sm btn-matrix-run" data-node-id="${dev.id}" title="Run Sequencer Suite">
                <span class="material-symbols-outlined" style="font-size: 16px;">play_arrow</span>
                <span>Run</span>
              </button>
              ` : `
              <button class="btn btn-danger-outlined btn-sm btn-matrix-stop" data-node-id="${dev.id}" title="Stop Running Tests">
                <span class="material-symbols-outlined" style="font-size: 16px;">stop</span>
                <span>Abort</span>
              </button>
              `}
              <button class="btn btn-outlined btn-sm btn-matrix-logs" data-node-id="${dev.id}" title="View Execution Logs">
                <span class="material-symbols-outlined" style="font-size: 16px;">terminal</span>
                <span>Logs</span>
              </button>
              ${firstFailId ? `
              <button class="btn btn-outlined btn-sm btn-matrix-diagnose" data-node-id="${dev.id}" data-test-id="${firstFailId}" title="Diagnose Failure with Mantis AI" style="color: #6d28d9; border-color: #d8b4fe; background: #fdf4ff;">
                <span class="material-symbols-outlined" style="font-size: 16px; color: #7c3aed;">auto_awesome</span>
                <span>Diagnose AI</span>
              </button>
              ` : ''}
            </div>
          </td>
        </tr>
      `;
    });

    this.matrixTableBody.innerHTML = tableHtml;

    if (this.matrixKpiPassed) this.matrixKpiPassed.textContent = totalPassed;
    if (this.matrixKpiFailed) this.matrixKpiFailed.textContent = totalFailed;
    if (this.metricPassed) this.metricPassed.textContent = totalPassed;
    if (this.metricFailed) this.metricFailed.textContent = totalFailed;
    if (this.suiteProgressFill && (totalPassed + totalFailed > 0)) {
      this.suiteProgressFill.style.width = '100%';
    }

    const totalExec = totalPassed + totalFailed;
    const scoreVal = totalExec > 0 ? `${Math.round((totalPassed / totalExec) * 100)}%` : '0%';
    if (this.matrixKpiScore) this.matrixKpiScore.textContent = scoreVal;

    this.matrixTableBody.querySelectorAll('.btn-matrix-run').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const targetNode = this.deviceNodes.find(n => n.id === btn.getAttribute('data-node-id'));
        if (targetNode) this.runSelectedDeviceTests(targetNode);
      });
    });

    this.matrixTableBody.querySelectorAll('.btn-matrix-stop').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const targetNode = this.deviceNodes.find(n => n.id === btn.getAttribute('data-node-id'));
        if (targetNode) this.stopDeviceTests(targetNode);
      });
    });

    this.matrixTableBody.querySelectorAll('.btn-matrix-logs, .device-cell').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const targetNode = this.deviceNodes.find(n => n.id === btn.getAttribute('data-node-id'));
        if (targetNode) {
          stateStore.set('activeDevice', targetNode.inputs.device_id);
          this.switchViewMode('logs');
        }
      });
    });

    this.matrixTableBody.querySelectorAll('.btn-matrix-diagnose').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const nid = btn.getAttribute('data-node-id');
        const tid = btn.getAttribute('data-test-id');
        const targetNode = this.deviceNodes.find(n => n.id === nid);
        if (targetNode && tid) this.triggerMantisForTest(targetNode, tid);
      });
    });
  }

  // --- HEALTH CHECK MACHINERY ---
  async runAllHealthChecks() {
    for (const node of this.nodes) {
      await this.runHealthCheckForNode(node);
    }
  }

  async runHealthCheckForNode(node) {
    if (node.type === 'actual_device') return;

    const prevStatus = node.status;
    let newStatus = prevStatus;

    try {
      if (node.type === 'pubber' || node.type === 'udmis' || node.type === 'mqtt_broker' || node.type === 'etcd' || node.type === 'influx' || node.type === 'postgresql' || node.type === 'zanzara_ingress' || node.type === 'zanzara_fabric' || node.type === 'cloud_udmis') {
        const res = await fetch(`/api/testbed/status?site_model=${encodeURIComponent(this.siteModel)}&project_spec=${encodeURIComponent(this.projectSpec)}`);
        if (res.ok) {
          const data = await res.json();
          const components = data.components || {};
          if (node.type === 'pubber') {
            const devId = node.inputs.device_id || node.label;
            const isPubberRunning = Array.isArray(data.active_pubbers) ? data.active_pubbers.includes(devId) : false;
            newStatus = isPubberRunning ? 'UP' : 'DOWN';
          }
          else if (node.type === 'mqtt_broker') newStatus = components.mqtt_broker && components.mqtt_broker.status === 'UP' ? 'UP' : 'DOWN';
          else if (node.type === 'udmis') newStatus = components.udmis && components.udmis.status === 'UP' ? 'UP' : 'DOWN';
          else if (node.type === 'zanzara_ingress') {
            const comp = components.zanzara_ingress;
            newStatus = comp?.status === 'UP' ? 'UP' : (comp?.status === 'DOWN' ? 'DOWN' : null);
            node.healthDetails = comp?.status !== 'UNAVAILABLE' ? (comp?.details || '') : '';
          }
          else if (node.type === 'zanzara_fabric') {
            const comp = components.zanzara_fabric;
            newStatus = comp?.status === 'UP' ? 'UP' : (comp?.status === 'DOWN' ? 'DOWN' : null);
            node.healthDetails = comp?.status !== 'UNAVAILABLE' ? (comp?.details || '') : '';
          }
          else if (node.type === 'cloud_udmis') {
            const comp = components.cloud_udmis;
            newStatus = comp?.status === 'UP' ? 'UP' : (comp?.status === 'DOWN' ? 'DOWN' : null);
            node.healthDetails = comp?.status !== 'UNAVAILABLE' ? (comp?.details || '') : '';
          }
          else if (node.type === 'etcd') {
            const comp = components.etcd;
            if (this.setupMode === 'CLOUD' || (this.projectSpec && !this.projectSpec.includes('localhost'))) {
              newStatus = comp?.status === 'UP' ? 'UP' : (comp?.status === 'DOWN' ? 'DOWN' : null);
              node.healthDetails = comp?.status !== 'UNAVAILABLE' ? (comp?.details || '') : '';
            } else {
              newStatus = comp && comp.status === 'UP' ? 'UP' : 'DOWN';
            }
          }
          else if (node.type === 'influx') newStatus = components.influx && components.influx.status === 'UP' ? 'UP' : 'DOWN';
          else if (node.type === 'postgresql') newStatus = components.postgresql && components.postgresql.status === 'UP' ? 'UP' : 'DOWN';
          else newStatus = 'UP';
        } else {
          newStatus = 'DOWN';
        }
      } else if (node.type === 'ancillary') {
        newStatus = 'UP';
      }
    } catch (e) {
      newStatus = 'DOWN';
    }

    if (newStatus !== prevStatus) {
      node.status = newStatus;
      if (this.activeViewMode === 'canvas') this.renderGraph();
      if (node.id === this.selectedNodeId || this.selectedNodeIds.has(node.id)) this.renderInspector();
    }
  }

  // --- GIT RESULTS ARCHIVING & BRANCH SAFETY ---
  async openGitSaveModal() {
    const modal = document.getElementById('git-save-modal');
    const branchText = document.getElementById('git-active-branch');
    const badge = document.getElementById('git-branch-badge');
    const alertBox = document.getElementById('git-protected-alert');
    const branchInput = document.getElementById('git-branch-input');
    const commitInput = document.getElementById('git-commit-msg-input');

    if (!modal) return;
    modal.classList.add('active');
    modal.style.display = 'flex';

    const activeDev = stateStore.get('activeDevice') || 'AHU-1';
    const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    if (branchInput) branchInput.value = `test-results-${activeDev}-${dateStr}`;
    if (commitInput) commitInput.value = `test: save device compliance results for ${activeDev}`;

    try {
      const res = await fetch(`/api/git/status?site_model=${encodeURIComponent(this.siteModel)}`);
      if (res.ok) {
        const data = await res.json();
        if (branchText) branchText.textContent = data.branch || 'main';
        if (badge) {
          badge.textContent = data.is_protected ? 'Protected Branch' : 'Feature Branch';
          badge.className = data.is_protected ? 'badge badge-warning' : 'badge badge-success';
        }
        if (alertBox) {
          alertBox.style.display = data.is_protected ? 'block' : 'none';
        }
      }
    } catch (e) {
      console.warn("Error fetching git status:", e);
      if (branchText) branchText.textContent = 'unknown';
    }
  }

  closeGitSaveModal() {
    const modal = document.getElementById('git-save-modal');
    if (modal) {
      modal.classList.remove('active');
      modal.style.display = 'none';
    }
  }

  async executeGitSave() {
    const radioVal = document.querySelector('input[name="git_branch_mode"]:checked')?.value;
    const isNewBranch = radioVal === 'new_branch';
    const branchName = document.getElementById('git-branch-input')?.value.trim();
    const commitMsg = document.getElementById('git-commit-msg-input')?.value.trim() || "test: save device compliance results";
    const doPush = document.getElementById('git-push-checkbox')?.checked ?? true;

    if (isNewBranch && !branchName) {
      alert("Please enter a valid branch name for results.");
      return;
    }

    this.closeGitSaveModal();
    NotificationManager.showToast({
      title: "⏳ Saving Test Results to Git...",
      message: `Staging output and committing on ${isNewBranch ? `branch '${branchName}'` : 'active branch'}...`,
      type: "info"
    });

    try {
      const res = await fetch('/api/git/commit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          site_model: this.siteModel,
          commit_message: commitMsg,
          create_branch: isNewBranch,
          branch_name: branchName,
          push: doPush,
          force_main: !isNewBranch
        })
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Git save operation failed");

      NotificationManager.notify({
        title: "✅ Test Results Saved to Git",
        body: data.message || `Commit created successfully (${data.commit_hash}). Push status: ${data.push_status}`,
        type: "success",
        duration: 8000
      });
    } catch (e) {
      NotificationManager.notify({
        title: "❌ Git Save Failed",
        body: e.message,
        type: "error",
        duration: 10000
      });
    }
  }

  // --- EMAIL & NOTIFICATION ALERT DELIVERY ---
  openEmailSettingsModal() {
    const modal = document.getElementById('email-settings-modal');
    if (!modal) return;
    modal.classList.add('active');
    modal.style.display = 'flex';

    const recip = document.getElementById('email-recipient-input');
    const chkComp = document.getElementById('email-trigger-completion');
    const chkFail = document.getElementById('email-trigger-failure-only');
    const chkRca = document.getElementById('email-trigger-rca');
    const smtp = document.getElementById('email-smtp-input');

    if (recip) recip.value = localStorage.getItem('udmi_email_recipient') || '';
    if (smtp) smtp.value = localStorage.getItem('udmi_email_smtp') || '';
    if (chkComp) chkComp.checked = localStorage.getItem('udmi_email_trig_comp') !== 'false';
    if (chkFail) chkFail.checked = localStorage.getItem('udmi_email_trig_fail') === 'true';
    if (chkRca) chkRca.checked = localStorage.getItem('udmi_email_trig_rca') !== 'false';
  }

  closeEmailSettingsModal() {
    const modal = document.getElementById('email-settings-modal');
    if (modal) {
      modal.classList.remove('active');
      modal.style.display = 'none';
    }
  }

  saveEmailSettings() {
    const recip = document.getElementById('email-recipient-input')?.value.trim();
    const smtp = document.getElementById('email-smtp-input')?.value.trim();
    const chkComp = document.getElementById('email-trigger-completion')?.checked;
    const chkFail = document.getElementById('email-trigger-failure-only')?.checked;
    const chkRca = document.getElementById('email-trigger-rca')?.checked;

    localStorage.setItem('udmi_email_recipient', recip || '');
    localStorage.setItem('udmi_email_smtp', smtp || '');
    localStorage.setItem('udmi_email_trig_comp', chkComp ? 'true' : 'false');
    localStorage.setItem('udmi_email_trig_fail', chkFail ? 'true' : 'false');
    localStorage.setItem('udmi_email_trig_rca', chkRca ? 'true' : 'false');

    this.closeEmailSettingsModal();
    NotificationManager.showToast({
      title: "✅ Notification Settings Saved",
      message: recip ? `Email alerts enabled for ${recip}.` : "Automatic email notifications disabled.",
      type: "success"
    });
  }

  async sendTestEmail() {
    const recip = document.getElementById('email-recipient-input')?.value.trim() || localStorage.getItem('udmi_email_recipient');
    const smtp = document.getElementById('email-smtp-input')?.value.trim();

    if (!recip) {
      alert("Please enter a recipient email address first.");
      return;
    }

    NotificationManager.showToast({ title: "📧 Sending Test Alert...", message: `Dispatching to ${recip}...`, type: "info" });
    try {
      const res = await fetch('/api/notifications/send_email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipient: recip,
          subject: "[UDMI Workbench] Test Alert Verification",
          body: "This is an automated verification email from UDMI Workbench confirming alert delivery.",
          smtp_server: smtp
        })
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Failed to dispatch test email");

      NotificationManager.notify({
        title: "📧 Test Alert Dispatched",
        body: `Delivered via ${data.delivery_method}. ${data.delivery_method.includes('OUTBOX') ? `Saved to ${data.outbox_file}` : ''}`,
        type: "success",
        duration: 8000
      });
    } catch (e) {
      NotificationManager.notify({ title: "❌ Email Delivery Failed", body: e.message, type: "error" });
    }
  }

  async checkAndDispatchEmailResultAlert(deviceNode, passCount, failCount) {
    const recip = localStorage.getItem('udmi_email_recipient');
    if (!recip) return;

    const trigComp = localStorage.getItem('udmi_email_trig_comp') !== 'false';
    const trigFail = localStorage.getItem('udmi_email_trig_fail') === 'true';

    if (!trigComp && !trigFail) return;
    if (trigFail && failCount === 0) return;

    const devId = deviceNode.inputs.device_id || deviceNode.label;
    const statusText = failCount > 0 ? `⚠️ FAILED (${failCount} Failed, ${passCount} Passed)` : `✅ PASSED (All ${passCount} Passed)`;
    const subject = `[UDMI Workbench] Compliance Suite Finished: Device ${devId} ${statusText}`;
    const bodyText = `UDMI Compliance Testing has completed for device target [${devId}] in setup mode [${this.setupMode} :18833].\n\nResult Summary:\n- Status: ${statusText}\n- Site Model: ${this.siteModel}\n- Transport Target: ${this.projectSpec}\n\n${failCount > 0 ? 'Mantis AI root cause analysis is active and evaluating failure signatures.' : 'All tested behaviors conform to expected UDMI specifications.'}`;

    try {
      await fetch('/api/notifications/send_email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipient: recip,
          subject: subject,
          body: bodyText,
          smtp_server: localStorage.getItem('udmi_email_smtp') || ''
        })
      });
      console.log(`[Email Alert] Dispatched test completion email for ${devId} to ${recip}`);
    } catch (e) {
      console.warn("[Email Alert] Failed to send automated email alert:", e);
    }
  }

  // --- NODE CONFIGURATION POPUP MODAL ---

  openNodeModal(target) {
    if (!this.nodeModal || !this.nodeConfigFormBody) return;
    this.currentEditingNodeTarget = target;
    this.nodeModal.style.display = 'flex';
    this.nodeModal.classList.add('active');
    this.nodeConfigFormBody.innerHTML = '';

    const isNew = target.isNew;
    const nodeType = isNew ? target.type : target.type;
    const spec = this.getNodeSpec(nodeType);
    const inputs = isNew ? (spec.inputs || {}) : (target.inputs || {});

    if (this.nodeModalTitle) this.nodeModalTitle.textContent = isNew ? `Configure New ${spec.label}` : `Edit Inputs: ${target.label}`;
    if (this.nodeModalSub) this.nodeModalSub.textContent = isNew ? `Enter required parameters before spawning component on canvas.` : `Modify required parameters for this node.`;

    Object.keys(inputs).forEach(key => {
      const val = inputs[key];
      const labelText = key.replace(/_/g, ' ');
      const group = document.createElement('div');
      group.className = 'control-group';
      group.style.marginBottom = '10px';
      group.innerHTML = `
        <label for="modal-inp-${key}" class="control-label" style="font-weight: 700; display: block; margin-bottom: 4px; font-size: 12px;">${labelText}</label>
        <input type="text" id="modal-inp-${key}" class="form-input w-full" style="width: 100%; font-family: monospace;" data-key="${key}" value="${val}" />
      `;
      this.nodeConfigFormBody.appendChild(group);
    });
  }

  closeNodeModal() {
    if (this.nodeModal) {
      this.nodeModal.style.display = 'none';
      this.nodeModal.classList.remove('active');
    }
    this.currentEditingNodeTarget = null;
  }

  saveNodeFromModal() {
    if (!this.currentEditingNodeTarget || !this.nodeConfigFormBody) return;
    const target = this.currentEditingNodeTarget;
    const newInputs = {};
    this.nodeConfigFormBody.querySelectorAll('input[data-key]').forEach(inp => {
      newInputs[inp.getAttribute('data-key')] = inp.value.trim();
    });

    if (target.isNew) {
      const x = target.x !== undefined ? target.x : 60;
      const y = target.y !== undefined ? target.y : 160 + (this.deviceNodes.length * 90);
      const node = this.createNodeObject(target.type, x, y);
      node.inputs = { ...node.inputs, ...newInputs };
      if (newInputs.device_id) node.label = newInputs.device_id;
      this.deviceNodes.push(node);
      this.selectNode(node.id, false);
      NotificationManager.showToast({ title: "➕ Component Created", message: `Spawned ${node.label} on graph.`, type: "success" });
    } else {
      target.inputs = { ...target.inputs, ...newInputs };
      if (newInputs.device_id && (target.type === 'pubber' || target.type === 'actual_device')) {
        target.label = newInputs.device_id;
      }
      NotificationManager.showToast({ title: "✅ Inputs Updated", message: `Updated configuration for ${target.label}.`, type: "success" });
    }
    this.closeNodeModal();
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
  }

  addDeviceToCanvas(devId, mode = 'actual_device', x = null, y = null) {
    if (!devId) return null;
    let node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
    const isLocalUp = this.infraNodes.some(n => n.type === 'mqtt_broker' && n.status === 'UP');
    if (node) {
      if (mode && node.type !== mode && mode !== 'ancillary' && mode !== 'spotter') {
        const spec = this.getNodeSpec(mode, devId);
        node.type = spec.type;
        node.label = devId;
        node.icon = spec.icon;
        node.status = mode === 'actual_device' ? '' : (isLocalUp ? 'UP' : 'DOWN');
        node.inputs = { ...spec.inputs, device_id: devId };
        if (mode === 'pubber') {
          node.inputs.serial_no = this.generateDeviceSerialNo(devId);
        }
      }
      return node;
    }

    if (x === null || y === null) {
      const idx = this.deviceNodes.length;
      const col = Math.floor(idx / 5);
      const row = idx % 5;
      x = 60 + col * 200;
      y = 160 + row * 105;
    }

    node = this.createNodeObject(mode, x, y, devId);
    this.deviceNodes.push(node);
    this.deviceConfigs.set(devId, { enabled: true, mode: mode });
    this.selectNode(node.id, false);
    return node;
  }

  removeDeviceFromCanvas(devId) {
    if (!devId) return;
    const node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
    if (!node) return;

    this.deviceNodes = this.deviceNodes.filter(n => n.id !== node.id);
    this.selectedNodeIds.delete(node.id);
    if (this.selectedNodeId === node.id) {
      const remaining = Array.from(this.selectedNodeIds);
      this.selectedNodeId = remaining.length > 0 ? remaining[0] : (this.deviceNodes[0]?.id || null);
    }
    this.deviceConfigs.set(devId, { enabled: false, mode: node.type });
  }

  toggleDeviceOnCanvas(devId, isChecked) {
    if (isChecked) {
      const cfg = this.deviceConfigs.get(devId) || { mode: 'actual_device' };
      this.addDeviceToCanvas(devId, cfg.mode || 'actual_device');
    } else {
      this.removeDeviceFromCanvas(devId);
    }
    this.renderSiteDevicesList();
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
  }

  changeDeviceMode(devId, newMode) {
    if (newMode === 'ancillary' || newMode === 'spotter') return; // Disabled for now
    const config = this.deviceConfigs.get(devId) || { enabled: false, mode: 'actual_device' };
    config.mode = newMode;
    this.deviceConfigs.set(devId, config);

    const isLocalUp = this.infraNodes.some(n => n.type === 'mqtt_broker' && n.status === 'UP');
    const node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
    if (node) {
      const spec = this.getNodeSpec(newMode, devId);
      node.type = spec.type;
      node.label = devId;
      node.icon = spec.icon;
      node.status = newMode === 'actual_device' ? '' : (isLocalUp ? 'UP' : 'DOWN');
      node.inputs = { ...spec.inputs, device_id: devId };
      if (newMode === 'pubber') {
        node.inputs.serial_no = this.generateDeviceSerialNo(devId);
      }
    } else {
      // Auto add device to canvas in selected mode
      this.addDeviceToCanvas(devId, newMode);
    }

    this.renderSiteDevicesList();
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();

    if (newMode === 'pubber') {
      this.startPubberForDevice(devId);
    } else if (newMode === 'actual_device') {
      this.stopPubberForDevice(devId);
    }
  }

  async startPubberForDevice(nodeOrDevId) {
    const devId = typeof nodeOrDevId === 'string' ? nodeOrDevId : (nodeOrDevId.inputs?.device_id || nodeOrDevId.label);
    let node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
    if (!node) {
      node = this.addDeviceToCanvas(devId, 'pubber');
    }
    const serialNo = (node && node.inputs && node.inputs.serial_no) || this.generateDeviceSerialNo(devId);
    if (node) {
      node.inputs.serial_no = serialNo;
      node.status = 'INITIALIZING';
      this.renderGraph();
    }

    try {
      const res = await fetch('/api/testbed/start_pubber', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          device_id: devId,
          serial_no: serialNo,
          site_model: this.siteModel,
          project_spec: this.projectSpec || '//mqtt/localhost:18833'
        })
      });

      if (res.ok) {
        const data = await res.json();
        if (node) {
          node.status = 'UP';
          this.renderGraph();
          this.renderInspector();
        }
        if (data.session_id) {
          this.openSetupLogsModal(data.session_id, 'PUBBER', this.projectSpec, data.already_running || false, {
            deviceId: devId,
            serialNo: serialNo,
            cmd: data.cmd || `UDMI_NO_SUDO=true bin/pubber ${this.siteModel} ${this.projectSpec || '//mqtt/localhost:18833'} ${devId} ${serialNo}`
          });
        }
      } else {
        const err = await res.json().catch(() => ({}));
        NotificationManager.showToast({
          title: "Pubber Launch Failed",
          message: err.error || `Could not start pubber emulator for ${devId}.`,
          type: "error"
        });
        if (node) {
          node.status = 'DOWN';
          this.renderGraph();
        }
      }
    } catch (e) {
      console.error("Failed to start pubber:", e);
      if (node) {
        node.status = 'DOWN';
        this.renderGraph();
      }
    }
  }

  async stopPubberForDevice(devId) {
    const node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
    if (node) {
      node.status = 'DOWN';
      this.renderGraph();
      this.renderInspector();
    }
    try {
      await fetch('/api/testbed/stop_pubber', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_id: devId })
      });
      NotificationManager.showToast({
        title: "Pubber Stopped",
        message: `Stopped pubber emulator for ${devId}.`,
        type: "info"
      });
    } catch (e) {
      console.error("Failed to stop pubber:", e);
    }
  }

  selectDeviceFromList(devId) {
    let node = this.deviceNodes.find(n => n.inputs.device_id === devId || n.id === `device_${devId}`);
    if (!node) {
      const cfg = this.deviceConfigs.get(devId) || { mode: 'actual_device' };
      node = this.addDeviceToCanvas(devId, cfg.mode || 'actual_device');
    }
    if (node) {
      this.selectNode(node.id, false);
    }
    this.renderSiteDevicesList();
  }

  selectAllDevices() {
    const query = (this.deviceSearchQuery || '').toLowerCase();
    const targets = this.discoveredDevices.filter(devId => devId.toLowerCase().includes(query));
    targets.forEach(devId => {
      const cfg = this.deviceConfigs.get(devId) || { mode: 'actual_device' };
      this.addDeviceToCanvas(devId, cfg.mode || 'actual_device');
    });
    this.renderSiteDevicesList();
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
  }

  clearAllDevices() {
    const query = (this.deviceSearchQuery || '').toLowerCase();
    const targets = this.discoveredDevices.filter(devId => devId.toLowerCase().includes(query));
    targets.forEach(devId => {
      this.removeDeviceFromCanvas(devId);
    });
    this.renderSiteDevicesList();
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
  }

  setBulkDeviceMode(newMode) {
    if (newMode === 'ancillary' || newMode === 'spotter') return; // Disabled for now
    const isLocalUp = this.infraNodes.some(n => n.type === 'mqtt_broker' && n.status === 'UP');
    this.deviceNodes.forEach(node => {
      const devId = node.inputs.device_id;
      const spec = this.getNodeSpec(newMode, devId);
      node.type = spec.type;
      node.icon = spec.icon;
      node.status = newMode === 'actual_device' ? '' : (isLocalUp ? 'UP' : 'DOWN');
      node.inputs = { ...spec.inputs, device_id: devId };
      if (newMode === 'pubber') {
        node.inputs.serial_no = this.generateDeviceSerialNo(devId);
      }
      if (devId) {
        this.deviceConfigs.set(devId, { enabled: true, mode: newMode });
      }
    });
    this.renderSiteDevicesList();
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
  }

  openSetupLogsModal(sessionId = null, mode = 'LOCAL', projectSpec = '', isReused = false, extra = {}) {
    const modal = document.getElementById('setup-logs-modal');
    if (!modal) return;

    const targetSessionId = sessionId || this.latestSetupSessionId;
    if (targetSessionId && mode === 'LOCAL') {
      this.latestSetupSessionId = targetSessionId;
    }

    const titleEl = document.getElementById('setup-logs-modal-title');
    const iconEl = document.getElementById('setup-logs-modal-icon');
    const cmdEl = document.getElementById('setup-logs-cmd');
    const badgeEl = document.getElementById('setup-logs-status-badge');
    const contentEl = document.getElementById('setup-logs-content');
    const btnClose = document.getElementById('btn-close-setup-logs-modal');
    const btnCopy = document.getElementById('btn-copy-setup-logs');
    const btnClear = document.getElementById('btn-clear-setup-logs');

    const portMatch = (projectSpec || this.projectSpec || '').match(/:(\d+)/);
    const mqttPort = portMatch ? portMatch[1] : '18833';

    modal.classList.remove('is-ready');
    if (mode === 'PUBBER') {
      if (titleEl) titleEl.textContent = `Pubber Emulator (${extra.deviceId || 'Device'})`;
      if (iconEl) {
        iconEl.textContent = 'robot_2';
        iconEl.style.color = '#0b57d0';
      }
      if (cmdEl) cmdEl.textContent = extra.cmd || `UDMI_NO_SUDO=true bin/pubber ${this.siteModel} ${projectSpec || this.projectSpec} ${extra.deviceId || 'AHU-1'} ${extra.serialNo || '10491'}`;
    } else {
      if (titleEl) titleEl.textContent = 'Local Setup Status';
      if (iconEl) {
        iconEl.textContent = 'terminal';
        iconEl.style.color = '#0b57d0';
      }
      if (cmdEl) cmdEl.textContent = `bin/start_local ${this.siteModel} ${projectSpec || this.projectSpec}`;
    }

    let autoCloseTimer = null;
    const cleanup = () => {
      if (autoCloseTimer) {
        clearTimeout(autoCloseTimer);
        autoCloseTimer = null;
      }
      if (this.setupLogsInterval) {
        clearInterval(this.setupLogsInterval);
        this.setupLogsInterval = null;
      }
      modal.classList.remove('active');
      modal.classList.remove('is-ready');
      this.runAllHealthChecks();
    };

    const setReadyState = (showToast = true) => {
      modal.classList.add('is-ready');
      if (mode === 'PUBBER') {
        if (titleEl) titleEl.textContent = `Pubber READY (${extra.deviceId || 'Device'})`;
      } else {
        if (titleEl) titleEl.textContent = 'Local Setup READY';
      }
      if (iconEl) {
        iconEl.textContent = 'check_circle';
        iconEl.style.color = '#137333';
      }
      if (badgeEl) {
        badgeEl.className = 'badge badge-success';
        badgeEl.style.background = '#ceead6';
        badgeEl.style.color = '#137333';
        badgeEl.innerHTML = `READY`;
      }
      this.runAllHealthChecks();

      if (!autoCloseTimer) {
        autoCloseTimer = setTimeout(() => {
          cleanup();
          if (showToast) {
            NotificationManager.showToast({
              title: mode === 'PUBBER' ? "Pubber Emulator Ready" : "Local Setup Ready",
              message: mode === 'PUBBER'
                ? `Pubber emulator running in background for ${extra.deviceId || 'Device'} (Serial: ${extra.serialNo || 'N/A'}).`
                : "Local testbed environment is ready and operational.",
              type: "success"
            });
          }
        }, 1200);
      }
    };

    if (isReused) {
      setReadyState(false);
      if (contentEl) {
        if (mode === 'PUBBER') {
          contentEl.textContent = `⚡ Active Pubber Emulator Found\n` +
            `==================================================\n` +
            `Device ID: ${extra.deviceId || 'AHU-1'}\n` +
            `Serial No: ${extra.serialNo || 'N/A'}\n` +
            `Target Spec: ${projectSpec || this.projectSpec}\n` +
            `Status: RUNNING\n\n` +
            `Pubber emulator process is actively running in background.\n`;
        } else {
          contentEl.textContent = `Active ${mode} Setup Found\n` +
            `==================================================\n` +
            `Status: READY\n` +
            `Target Spec: ${projectSpec || this.projectSpec}\n\n` +
            `Active Services:\n` +
            `• Mosquitto MQTT Broker: UP (Port ${mqttPort})\n` +
            `• etcd Key-Value Store: UP (Port ${parseInt(mqttPort) + 1})\n` +
            `• InfluxDB Time-Series: UP (Port ${parseInt(mqttPort) + 2})\n` +
            `• PostgreSQL Database: UP (Port ${parseInt(mqttPort) + 3})\n` +
            `• UDMIS Service: UP (Pod Ready)\n\n` +
            `Local pipeline is operational and ready.\n`;
        }
      }
    } else {
      if (badgeEl) {
        badgeEl.className = 'badge badge-info';
        badgeEl.style.background = '#e8f0fe';
        badgeEl.style.color = '#0b57d0';
        badgeEl.innerHTML = `<span class="spinner-sm"></span> Initializing...`;
      }
      if (!contentEl.textContent || sessionId) {
        contentEl.textContent = mode === 'PUBBER'
          ? `Launching Pubber emulator for ${extra.deviceId || 'device'} (Serial: ${extra.serialNo || 'N/A'})...\n`
          : `Launching ${mode} setup pipeline...\n`;
      }
    }

    modal.classList.add('active');

    // Close any previous setup polling interval
    if (this.setupLogsInterval) {
      clearInterval(this.setupLogsInterval);
      this.setupLogsInterval = null;
    }

    let offset = 0;
    let isFinished = isReused;

    if (btnClose) btnClose.onclick = cleanup;
    modal.onclick = (e) => {
      if (e.target === modal) cleanup();
    };

    if (btnCopy) {
      btnCopy.onclick = () => {
        if (contentEl && navigator.clipboard) {
          navigator.clipboard.writeText(contentEl.textContent).then(() => {
            NotificationManager.showToast({ title: "Copied", message: "Logs copied to clipboard", type: "info" });
          });
        }
      };
    }

    if (btnClear) {
      btnClear.onclick = () => {
        if (contentEl) contentEl.textContent = '';
      };
    }

    if (isReused) return;

    const pollLogs = async () => {
      if (isFinished) return;
      try {
        const queryUrl = targetSessionId
          ? `/api/testbed_proc_status?session_id=${encodeURIComponent(targetSessionId)}&offset=${offset}`
          : `/api/testbed_proc_status?offset=${offset}`;
        const res = await fetch(queryUrl);
        if (!res.ok) return;
        const data = await res.json();

        if (data.session_id && !this.latestSetupSessionId && mode === 'LOCAL') {
          this.latestSetupSessionId = data.session_id;
        }

        if (data.log && data.log.trim() !== '') {
          if (contentEl) {
            contentEl.textContent += data.log;
            contentEl.scrollTop = contentEl.scrollHeight;
          }
        }
        offset = data.offset || offset;

        if (data.ready) {
          setReadyState(true);
        }

        if (!data.running && mode !== 'PUBBER') {
          isFinished = true;
          if (this.setupLogsInterval) {
            clearInterval(this.setupLogsInterval);
            this.setupLogsInterval = null;
          }
          if (data.exit_code === 0 || data.ready) {
            setReadyState(true);
            if (contentEl && (contentEl.textContent.trim() === `Launching ${mode} setup pipeline...` || !contentEl.textContent.trim())) {
              contentEl.textContent = `⚡ Active ${mode} Setup Ready\n` +
                `==================================================\n` +
                `Status: READY\n` +
                `Site Model: ${this.siteModel}\n` +
                `Target Spec: ${projectSpec || this.projectSpec}\n\n` +
                `Active Services:\n` +
                `• Mosquitto MQTT Broker: UP (Port ${mqttPort})\n` +
                `• etcd Key-Value Store: UP (Port ${parseInt(mqttPort) + 1})\n` +
                `• InfluxDB Time-Series: UP (Port ${parseInt(mqttPort) + 2})\n` +
                `• PostgreSQL Database: UP (Port ${parseInt(mqttPort) + 3})\n` +
                `• UDMIS Service: UP (Pod Ready)\n\n` +
                `Local pipeline is operational and ready.\n`;
            }
          } else {
            if (badgeEl) {
              badgeEl.className = 'badge badge-danger';
              badgeEl.style.background = '#fce8e6';
              badgeEl.style.color = '#c5221f';
              badgeEl.innerHTML = `FAILED (Exit: ${data.exit_code})`;
            }
          }
          this.runAllHealthChecks();
        } else if (!data.running && mode === 'PUBBER') {
          if (data.exit_code !== null && data.exit_code !== 0 && !data.ready) {
            isFinished = true;
            if (this.setupLogsInterval) {
              clearInterval(this.setupLogsInterval);
              this.setupLogsInterval = null;
            }
            if (badgeEl) {
              badgeEl.className = 'badge badge-danger';
              badgeEl.style.background = '#fce8e6';
              badgeEl.style.color = '#c5221f';
              badgeEl.innerHTML = `FAILED (Exit: ${data.exit_code})`;
            }
            const devNode = this.deviceNodes.find(n => n.inputs.device_id === extra.deviceId || n.id === `device_${extra.deviceId}`);
            if (devNode) {
              devNode.status = 'DOWN';
              this.renderGraph();
            }
          }
        }
      } catch (e) {
        console.warn("Failed to poll setup logs:", e);
      }
    };

    // Poll immediately and then every 500ms
    pollLogs();
    this.setupLogsInterval = setInterval(pollLogs, 500);
  }
}
