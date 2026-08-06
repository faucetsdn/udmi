// ==========================================================================
// TESTBED INTERACTIVE TOPOLOGY, COMPLIANCE MATRIX & LIVE LOG ANALYZER
// ==========================================================================
import { stateStore } from '../shared/state-store.js';
import { LogViewer } from '../shared/components/log-viewer.js';
import { NotificationManager } from '../shared/components/notification-toast.js';

const SEQUENCER_TEST_CATALOG = [
  {
    category: "System & Base",
    icon: "dns",
    tests: [
      { id: "system.base.telemetry", name: "system.base.telemetry", desc: "Core telemetry heartbeats & state payload validation" },
      { id: "system.base.state", name: "system.base.state", desc: "Base device state reporting & lifecycle schema" },
      { id: "system.firmware.state", name: "system.firmware.state", desc: "Firmware version and hardware revision reporting" }
    ]
  },
  {
    category: "Pointset & Telemetry",
    icon: "analytics",
    tests: [
      { id: "pointset.telemetry.events", name: "pointset.telemetry.events", desc: "Pointset event publishing & value schema compliance" },
      { id: "pointset.telemetry.write", name: "pointset.telemetry.write", desc: "Writable point value update & actuation confirmation" },
      { id: "pointset.sample.rate", name: "pointset.sample.rate", desc: "Telemetry sample interval & timing tolerance" }
    ]
  },
  {
    category: "Gateway & Network",
    icon: "router",
    tests: [
      { id: "gateway.proxy.target", name: "gateway.proxy.target", desc: "Proxy gateway target message routing" },
      { id: "gateway.proxy.discovery", name: "gateway.proxy.discovery", desc: "Proxy sub-device discovery & binding" }
    ]
  }
];

export class TestbedGraphController {
  constructor() {
    this.siteModel = stateStore.get('siteModel') || 'sites/udmi_site_model';
    // Explicit unprivileged port 18833 automatically triggers isolated mode in shell_common.sh without sudo
    this.projectSpec = stateStore.get('projectSpec') || '//mqtt/localhost:18833';

    this.setupMode = 'LOCAL'; // 'LOCAL' or 'CLOUD'
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

    this.initElements();
    this.initComponents();
    this.initEvents();
    this.initStoreListeners();
    this.loadDefaultSetup();
    setTimeout(() => this.updateTabIndicator(), 50);
    setTimeout(() => this.checkAndRecoverBackgroundJobs(), 1000);
  }

  get nodes() {
    return [...this.deviceNodes, ...this.infraNodes];
  }

  initElements() {
    this.btnDefaultSetup = document.getElementById('btn-default-setup');
    this.btnCloudSetup = document.getElementById('btn-cloud-setup');
    this.btnStartPipeline = document.getElementById('btn-start-pipeline');
    this.btnStopPipeline = document.getElementById('btn-stop-pipeline');

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

    // Setup & Node Configuration Modals and Actual Devices Dropdown
    this.setupModal = document.getElementById('setup-config-modal');
    this.btnCloseSetup = document.getElementById('btn-close-setup-modal');
    this.btnCancelSetup = document.getElementById('btn-cancel-setup-modal');
    this.btnApplySetup = document.getElementById('btn-apply-setup');
    this.setupTargetMode = document.getElementById('setup-target-mode');
    this.setupSiteModelInput = document.getElementById('setup-sitemodel-input');
    this.setupLocalPort = document.getElementById('setup-local-port');
    this.setupCloudProject = document.getElementById('setup-cloud-project');
    this.setupLocalFields = document.getElementById('setup-local-fields');
    this.setupCloudFields = document.getElementById('setup-cloud-fields');
    this.setupModalTitle = document.getElementById('setup-modal-title');

    this.nodeModal = document.getElementById('node-config-modal');
    this.btnCloseNode = document.getElementById('btn-close-node-modal');
    this.btnCancelNode = document.getElementById('btn-cancel-node-modal');
    this.btnSaveNode = document.getElementById('btn-save-node-config');
    this.nodeConfigFormBody = document.getElementById('node-config-form-body');
    this.nodeModalTitle = document.getElementById('node-modal-title');
    this.nodeModalSub = document.getElementById('node-modal-sub');
    this.selectActualDevices = document.getElementById('select-actual-devices');
  }

  initComponents() {
    const logsContainer = document.getElementById('sequencer-logs');
    if (logsContainer) {
      this.logViewer = new LogViewer(logsContainer);
      this.logViewer.append('UDMI Workbench Live Log Analyzer ready. Select devices and start tests to view streaming output...', 'info');
    }
  }

  initStoreListeners() {
    stateStore.on('change:siteModel', (val) => {
      this.siteModel = val;
      this.runAllHealthChecks();
    });

    stateStore.on('change:projectSpec', (val) => {
      this.projectSpec = val;
      this.runAllHealthChecks();
    });

    stateStore.on('change:devices', (devices) => {
      this.onDiscoveredDevices(devices);
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

  onDiscoveredDevices(devices) {
    if (!this.selectActualDevices) return;
    this.selectActualDevices.innerHTML = '';
    if (!devices || devices.length === 0) {
      this.selectActualDevices.innerHTML = '<option disabled>No device folders found in site model</option>';
      return;
    }
    devices.forEach(devId => {
      const opt = document.createElement('option');
      opt.value = devId;
      opt.textContent = devId;
      if (this.deviceNodes.some(n => n.type === 'actual_device' && (n.id === devId || n.inputs.device_id === devId))) {
        opt.selected = true;
      }
      this.selectActualDevices.appendChild(opt);
    });
  }

  async loadActualDevicesDropdown() {
    if (!this.selectActualDevices) return;
    try {
      const res = await fetch(`/api/devices?site_model=${encodeURIComponent(this.siteModel)}`);
      if (res.ok) {
        const data = await res.json();
        this.onDiscoveredDevices(data.devices || []);
      }
    } catch (e) {
      console.warn("Failed to load actual devices list:", e);
    }
  }

  initEvents() {
    if (this.btnDefaultSetup) this.btnDefaultSetup.addEventListener('click', () => this.openSetupModal('LOCAL'));
    if (this.btnCloudSetup) this.btnCloudSetup.addEventListener('click', () => this.openSetupModal('CLOUD'));
    if (this.btnStartPipeline) this.btnStartPipeline.addEventListener('click', () => this.startPipeline());
    if (this.btnStopPipeline) this.btnStopPipeline.addEventListener('click', () => this.stopPipeline());
    if (this.btnCloseInspector) this.btnCloseInspector.addEventListener('click', () => this.selectNode(null, false));

    // Setup & Node Config Modals & Actual Devices Dropdown
    if (this.btnCloseSetup) this.btnCloseSetup.addEventListener('click', () => this.closeSetupModal());
    if (this.btnCancelSetup) this.btnCancelSetup.addEventListener('click', () => this.closeSetupModal());
    if (this.btnApplySetup) this.btnApplySetup.addEventListener('click', () => this.applySetupFromModal());
    if (this.btnCloseNode) this.btnCloseNode.addEventListener('click', () => this.closeNodeModal());
    if (this.btnCancelNode) this.btnCancelNode.addEventListener('click', () => this.closeNodeModal());
    if (this.btnSaveNode) this.btnSaveNode.addEventListener('click', () => this.saveNodeFromModal());
    if (this.selectActualDevices) this.selectActualDevices.addEventListener('change', () => this.handleActualDevicesDropdownChange());

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

    // Drag & Drop & Palette Item Click Configuration
    const paletteItems = document.querySelectorAll('.palette-item');
    paletteItems.forEach(item => {
      if (!item.classList.contains('disabled')) {
        item.addEventListener('click', () => {
          const nodeType = item.getAttribute('data-node-type');
          this.openNodeModal({ type: nodeType, isNew: true });
        });
        if (item.getAttribute('draggable') === 'true') {
          item.addEventListener('dragstart', (e) => {
            const nodeType = item.getAttribute('data-node-type');
            e.dataTransfer.setData('text/plain', nodeType);
          });
        }
      }
    });

    if (this.canvasContainer) {
      this.canvasContainer.addEventListener('dragover', (e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'copy';
      });

      this.canvasContainer.addEventListener('drop', (e) => {
        e.preventDefault();
        const nodeType = e.dataTransfer.getData('text/plain');
        if (nodeType === 'pubber') {
          const rect = this.canvasContainer.getBoundingClientRect();
          const x = Math.max(20, e.clientX - rect.left - 90);
          const y = Math.max(20, e.clientY - rect.top - 40);
          this.openNodeModal({ type: nodeType, x, y, isNew: true });
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

    if (this.canvasWorkspace) this.canvasWorkspace.style.display = mode === 'canvas' ? 'flex' : 'none';
    if (this.matrixWorkspace) this.matrixWorkspace.style.display = mode === 'matrix' ? 'flex' : 'none';
    if (this.logsWorkspace) this.logsWorkspace.style.display = mode === 'logs' ? 'flex' : 'none';

    this.updateTabIndicator();

    if (mode === 'matrix') {
      this.renderComplianceMatrix();
    } else if (mode === 'canvas') {
      this.renderGraph();
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

  // --- NODE TYPE SPECIFICATIONS ---
  getNodeSpec(type) {
    const specs = {
      pubber: {
        type: 'pubber',
        label: 'Device Emulator (Pubber)',
        icon: 'smart_toy',
        inputs: { device_id: 'AHU-1', serial_no: 'SN-10492', interval_sec: '10' },
        subText: (n) => `Dev: ${n.inputs.device_id || 'AHU-1'}`,
        runConfig: (n) => `out/pubber_config.json (site: ${this.siteModel}, dev: ${n.inputs.device_id || 'AHU-1'})`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/pubber ${this.siteModel} //mqtt/localhost:18833 ${n.inputs.device_id || 'AHU-1'} ${n.inputs.serial_no || 'SN-10492'}`,
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
        icon: 'router',
        inputs: { device_id: 'AHU-22', address: '192.168.1.105', protocol: 'BACnet/IP' },
        subText: (n) => `${n.inputs.device_id || 'AHU-22'} (${n.inputs.protocol || 'BACnet'})`,
        runConfig: (n) => `${this.siteModel}/devices/${n.inputs.device_id || 'AHU-22'}/metadata.json`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/registrar ${this.siteModel} //mqtt/localhost:18833 ${n.inputs.device_id || 'AHU-22'}`,
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
      clearblade_broker: {
        type: 'clearblade_broker',
        label: 'ClearBlade IoT Broker',
        icon: 'cloud_sync',
        inputs: { project_id: 'bos-platform-dev', registry_id: 'udmi-registry', region: 'us-central1' },
        subText: (n) => `Project: ${n.inputs.project_id || 'gcp-proj'}`,
        runConfig: (n) => `${this.siteModel}/cloud_iot_config.json (registry: ${n.inputs.registry_id || 'registry'})`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/registrar ${this.siteModel} //clearblade/${n.inputs.project_id || 'project'}`,
        healthProbe: () => `MQTTS Port Probe: nc -zv us-central1-mqtt.clearblade.com 8883`
      },
      cloud_udmis: {
        type: 'cloud_udmis',
        label: 'Cloud UDMIS (Pub/Sub)',
        icon: 'cloud_done',
        inputs: { topic: 'projects/bos-platform-dev/topics/udmi_target', subscription: 'udmi_sub' },
        subText: (n) => `Sub: ${n.inputs.subscription || 'udmi_sub'}`,
        runConfig: () => `udmis/etc/prod_pod.json (GCP Pub/Sub Provider)`,
        runCommand: () => `java -jar udmis/build/libs/udmis-1.0-SNAPSHOT-all.jar udmis/etc/prod_pod.json`,
        healthProbe: (n) => `GCP Pub/Sub Pull: gcloud pubsub subscriptions pull ${n.inputs.subscription || 'udmi_sub'}`
      },
      etcd: {
        type: 'etcd',
        label: 'etcd KV Store',
        icon: 'database',
        inputs: { port: '2379', host: '127.0.0.1' },
        subText: (n) => `Port: ${n.inputs.port || '2379'} (KV Store)`,
        runConfig: () => 'var/etcd/',
        runCommand: () => 'bin/start_etcd',
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

  loadDefaultSetup() {
    this.setupMode = 'LOCAL';
    this.projectSpec = '//mqtt/localhost:18833'; // Unprivileged isolated mode
    stateStore.set('projectSpec', this.projectSpec);
    this.updateSetupButtons();

    if (this.deviceNodes.length === 0) {
      const pNode = this.createNodeObject('pubber', 60, 160);
      this.deviceNodes.push(pNode);
      this.fetchDeviceResultsFromDisk(pNode);
    }

    this.infraNodes = [
      this.createNodeObject('mqtt_broker', 300, 160),
      this.createNodeObject('udmis', 540, 160),
      this.createNodeObject('etcd', 780, 60),
      this.createNodeObject('influx', 780, 160),
      this.createNodeObject('postgresql', 780, 260)
    ];

    this.selectNode(this.deviceNodes[0].id, false);
    this.renderGraph();
    this.runAllHealthChecks();
    this.loadActualDevicesDropdown();
  }

  loadCloudSetup() {
    this.setupMode = 'CLOUD';
    this.projectSpec = '//pubsub/bos-platform-dev';
    stateStore.set('projectSpec', this.projectSpec);
    this.updateSetupButtons();

    if (this.deviceNodes.length === 0) {
      const pNode = this.createNodeObject('pubber', 60, 160);
      this.deviceNodes.push(pNode);
      this.fetchDeviceResultsFromDisk(pNode);
    }

    this.infraNodes = [
      this.createNodeObject('clearblade_broker', 340, 160),
      this.createNodeObject('cloud_udmis', 600, 160)
    ];

    this.selectNode(this.deviceNodes[0].id, false);
    this.renderGraph();
    this.runAllHealthChecks();
    this.loadActualDevicesDropdown();
  }

  updateSetupButtons() {
    if (this.btnDefaultSetup) this.btnDefaultSetup.classList.toggle('active', this.setupMode === 'LOCAL');
    if (this.btnCloudSetup) this.btnCloudSetup.classList.toggle('active', this.setupMode === 'CLOUD');
  }

  async startPipeline() {
    this.nodes.forEach(n => { if (n.type !== 'actual_device') n.status = 'INITIALIZING'; });
    this.renderGraph();
    try {
      if (this.setupMode === 'LOCAL') {
        await fetch('/api/testbed/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ site_model: this.siteModel, project_spec: this.projectSpec })
        });
      } else {
        await fetch('/api/testbed/start_component', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ component: 'pubber', site_model: this.siteModel, project_spec: this.projectSpec })
        });
      }
    } catch (e) {
      console.error("Failed to start pipeline:", e);
    }
    setTimeout(() => this.runAllHealthChecks(), 2500);
  }

  async stopPipeline() {
    this.nodes.forEach(n => { if (n.type !== 'actual_device') n.status = 'INITIALIZING'; });
    this.renderGraph();
    try {
      await fetch('/api/testbed/stop', { method: 'POST' });
    } catch (e) {
      console.error("Failed to stop pipeline:", e);
    }
    setTimeout(() => this.runAllHealthChecks(), 1500);
  }

  createNodeObject(type, x, y) {
    const spec = this.getNodeSpec(type);
    const isDevice = type === 'pubber' || type === 'actual_device' || type === 'spotter';
    return {
      id: 'node_' + Math.random().toString(36).substr(2, 7),
      type: spec.type,
      label: spec.label,
      icon: spec.icon,
      status: type === 'actual_device' ? '' : 'UP',
      x,
      y,
      inputs: { ...spec.inputs },
      selectedTests: isDevice ? new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']) : new Set(),
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
    this.fetchDeviceResultsFromDisk(node);
    this.renderGraph();
    this.runHealthCheckForNode(node);
  }

  deleteNode(id) {
    const isDevice = this.deviceNodes.some(n => n.id === id);
    if (!isDevice) return;
    
    this.deviceNodes = this.deviceNodes.filter(n => n.id !== id);
    this.selectedNodeIds.delete(id);
    if (this.selectedNodeId === id) {
      const remaining = Array.from(this.selectedNodeIds);
      this.selectedNodeId = remaining.length > 0 ? remaining[0] : null;
    }
    this.renderGraph();
    this.renderInspector();
  }

  selectNode(id, isMulti = false) {
    if (!id) {
      this.selectedNodeId = null;
      this.selectedNodeIds.clear();
      this.renderGraph();
      this.renderInspector();
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

    this.renderGraph();
    this.renderInspector();
  }

  getNodeLayer(type) {
    if (type === 'pubber' || type === 'actual_device' || type === 'spotter') return 1;
    if (type === 'mqtt_broker' || type === 'clearblade_broker') return 2;
    if (type === 'udmis' || type === 'cloud_udmis') return 3;
    return 4;
  }

  getLogicalEdges() {
    const edges = [];
    const devices = this.nodes.filter(n => this.getNodeLayer(n.type) === 1);
    const brokers = this.nodes.filter(n => this.getNodeLayer(n.type) === 2);
    const cores = this.nodes.filter(n => this.getNodeLayer(n.type) === 3);

    devices.forEach(dev => {
      brokers.forEach(brk => { edges.push({ source: dev, target: brk, label: 'Telemetry / State' }); });
    });

    brokers.forEach(brk => {
      cores.forEach(core => { edges.push({ source: brk, target: core, label: 'Reflective Sync' }); });
    });

    const dbNodes = this.nodes.filter(n => this.getNodeLayer(n.type) === 4 && n.type !== 'ancillary');
    cores.forEach(core => {
      dbNodes.forEach(db => {
        const label = db.type === 'etcd' ? 'KV State' : (db.type === 'influx' ? 'Metrics' : 'Relational');
        edges.push({ source: core, target: db, label: label });
      });
    });

    if (brokers.length === 0) {
      devices.forEach(dev => { cores.forEach(core => { edges.push({ source: dev, target: core, label: 'Direct Sync' }); }); });
    }

    return edges;
  }

  checkTopologyCompleteness() {
    const badge = document.getElementById('topology-completeness-badge');
    if (!badge) return;
    if (this.deviceNodes.length > 0) {
      badge.className = 'badge badge-success';
      badge.style.backgroundColor = '#c8e6c9';
      badge.style.color = '#1b5e20';
      badge.innerHTML = `
        <span class="material-symbols-outlined" style="font-size:14px;">check_circle</span>
        <span>COMPLETE SETUP (${this.setupMode} :18833)</span>
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
      const x1 = src.x + 90;
      const y1 = src.y + 40;
      const x2 = tgt.x + 90;
      const y2 = tgt.y + 40;

      svgHtml += `
        <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" 
              stroke="#0b57d0" stroke-width="2.5" stroke-dasharray="6,4" marker-end="url(#arrow)" />
        <text x="${(x1 + x2) / 2}" y="${(y1 + y2) / 2 - 8}" 
              fill="#5f6368" font-size="10" font-weight="600" text-anchor="middle">${edge.label}</text>
      `;
    });

    this.canvasSvg.innerHTML = svgHtml;
    this.checkTopologyCompleteness();

    this.nodesLayer.innerHTML = '';

    this.nodes.forEach(node => {
      const spec = this.getNodeSpec(node.type);
      const isSelected = node.id === this.selectedNodeId;
      const isMultiSelected = this.selectedNodeIds.size > 1 && this.selectedNodeIds.has(node.id);

      let badgeClass = 'badge-up';
      let badgeContent = node.status;

      if (node.isTestingRunning) {
        badgeClass = 'badge-init';
        badgeContent = `<span class="spinner-sm"></span> TESTING`;
      } else if (node.testResults) {
        const fails = Object.values(node.testResults).filter(r => r.status === 'FAIL' || r.status === 'FAILED').length;
        if (fails > 0) {
          badgeClass = 'badge-down';
          badgeContent = `⚠️ ${fails} FAIL`;
        } else {
          badgeClass = 'badge-up';
          badgeContent = `✅ PASS`;
        }
      } else if (node.status === 'DOWN') {
        badgeClass = 'badge-down';
      } else if (node.status === 'INITIALIZING') {
        badgeClass = 'badge-init';
        badgeContent = `<span class="spinner-sm"></span>`;
      } else if (node.status === 'DISABLED') {
        badgeClass = 'badge-disabled';
      }

      const showBadge = node.type !== 'actual_device' || node.isTestingRunning || node.testResults;

      const card = document.createElement('div');
      card.className = `canvas-node ${isSelected ? 'selected' : ''} ${isMultiSelected ? 'multi-selected' : ''}`;
      card.style.left = `${node.x}px`;
      card.style.top = `${node.y}px`;

      card.innerHTML = `
        <div class="canvas-node-header">
          <span class="material-symbols-outlined canvas-node-icon">${node.icon}</span>
          <span class="canvas-node-title">${node.label}</span>
        </div>
        ${showBadge ? `<span class="node-status-badge ${badgeClass}">${badgeContent}</span>` : ''}
        <span class="canvas-node-sub">${spec.subText(node)}</span>
      `;

      card.addEventListener('mousedown', (e) => {
        e.stopPropagation();
        const rect = card.getBoundingClientRect();
        this.dragOffset = { x: e.clientX - rect.left, y: e.clientY - rect.top };
        this.draggedNodeId = node.id;
        const isMulti = e.shiftKey || e.ctrlKey || e.metaKey;
        this.selectNode(node.id, isMulti);
      });

      card.addEventListener('dblclick', (e) => {
        e.stopPropagation();
        this.openNodeModal(node);
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
        <div class="inspector-form" style="padding: 12px 0;">
          <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 8px;">Selected Devices:</div>
          <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px;">
      `;

      selectedDevices.forEach(d => {
        batchHtml += `
          <div class="batch-chip">
            <span class="material-symbols-outlined" style="font-size: 14px;">${d.icon}</span>
            <span>${d.inputs.device_id || d.label}</span>
            <span class="btn-remove-chip" data-node-id="${d.id}" title="Remove from batch">&times;</span>
          </div>
        `;
      });

      batchHtml += `
          </div>
        </div>
        <div style="margin-top: 8px; padding: 12px; background: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;">
          <strong style="font-size:12px; color:#202124; display:flex; align-items:center; gap:4px; margin-bottom: 8px;">
            <span class="material-symbols-outlined" style="font-size:16px; color:#0b57d0;">checklist</span>
            Batch Sequencer Test Suite
          </strong>
          <div style="display:flex; gap:4px; margin-bottom:10px; flex-wrap:wrap;">
            <button id="btn-batch-smoke" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">⚡ Smoke Test</button>
            <button id="btn-batch-all" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">Select All</button>
            <button id="btn-batch-none" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">Clear</button>
          </div>
          ${!anyRunning ? `
          <button id="btn-run-batch-tests" class="btn btn-primary" style="width: 100%; margin-top: 10px; justify-content: center;">
            <span class="material-symbols-outlined">play_arrow</span>
            <span>Run Tests on ${selectedDevices.length} Selected Devices</span>
          </button>
          ` : `
          <button id="btn-stop-batch-tests" class="btn btn-danger-outlined" style="width: 100%; margin-top: 10px; justify-content: center; font-weight: 600;">
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
          selectedDevices.forEach(d => { d.selectedTests = new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']); });
        });
      }
      return;
    }

    // SINGLE NODE INSPECTOR
    const node = this.nodes.find(n => n.id === this.selectedNodeId);
    if (!node) return;

    if (this.inspectorTitle) this.inspectorTitle.textContent = node.label;
    if (this.inspectorIcon) this.inspectorIcon.textContent = node.icon;

    let formHtml = `
      <div class="inspector-form" style="padding: 12px 0;">
        <div style="background: #f8f9fa; padding: 10px 12px; border: 1px solid #e0e0e0; border-radius: 8px; margin-bottom: 12px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 8px;">
            <span style="font-size:11px; font-weight:700; color:#5f6368; text-transform:uppercase;">Node Inputs</span>
            <button id="btn-inspector-edit-inputs" class="btn btn-outlined" style="padding: 2px 8px; font-size: 11px; height: 24px;">
              <span class="material-symbols-outlined" style="font-size:14px;">tune</span>
              <span>Edit Inputs</span>
            </button>
          </div>
          <div style="font-size: 12px; color: #202124; line-height: 1.5;">
            ${Object.entries(node.inputs).map(([k, v]) => `<div><span style="color:#5f6368;">${k.replace(/_/g, ' ')}:</span> <strong style="font-family:monospace;">${v}</strong></div>`).join('')}
          </div>
        </div>
    `;

    const spec = this.getNodeSpec(node.type);
    const isDeviceNode = this.deviceNodes.some(n => n.id === node.id);
    const isActualDevice = node.type === 'actual_device';

    formHtml += `
      </div>
      <div class="inspector-actions" style="margin-top: 16px;">
        ${!isActualDevice ? `
        <button id="btn-node-health" class="btn btn-outlined">
          <span class="material-symbols-outlined">health_and_safety</span>
          <span>Run Node Health Check</span>
        </button>
        <button id="btn-toggle-node" class="btn btn-outlined">
          <span class="material-symbols-outlined">${node.status === 'UP' ? 'power_settings_new' : 'play_arrow'}</span>
          <span>${node.status === 'UP' ? 'Stop Node' : 'Start Node'}</span>
        </button>
        ` : ''}
        ${isDeviceNode ? `
          <button id="btn-delete-node" class="btn btn-danger-outlined">
            <span class="material-symbols-outlined">delete</span>
            <span>Delete Device Node</span>
          </button>
        ` : `
          <div style="font-size: 11px; color: #5f6368; font-style: italic; text-align: center; padding: 4px;">
            🔒 Managed Pipeline Node (${this.setupMode} Setup :18833)
          </div>
        `}
      </div>
    `;

    if (isDeviceNode) {
      if (!node.selectedTests) {
        node.selectedTests = new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']);
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
            <button id="btn-preset-smoke" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">⚡ Smoke Test</button>
            <button id="btn-preset-rerun" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;" ${(!node.testResults || !Object.values(node.testResults).some(r => r.status === 'FAIL' || r.status === 'FAILED')) ? 'disabled' : ''}>🔄 Re-run Failures</button>
            <button id="btn-preset-all" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">Select All</button>
            <button id="btn-preset-none" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">Clear</button>
          </div>

          <input type="text" id="inp-test-filter" placeholder="Filter test cases..." class="form-input" style="font-size:11px; padding:4px 8px; margin-bottom:8px; width:100%; box-sizing:border-box;" />

          <div id="test-tree-container" style="max-height: 180px; overflow-y: auto; border: 1px solid #f1f3f4; border-radius: 4px; padding: 6px;">
      `;

      SEQUENCER_TEST_CATALOG.forEach(cat => {
        testSuiteHtml += `
          <div class="test-category-block" style="margin-bottom: 8px;">
            <div style="font-size: 11px; font-weight: 700; color: #5f6368; display: flex; align-items: center; gap: 4px; padding-bottom: 2px; border-bottom: 1px solid #f1f3f4;">
              <span class="material-symbols-outlined" style="font-size:14px; color:#5f6368;">${cat.icon}</span>
              <span>${cat.category}</span>
            </div>
            <div style="padding-left: 8px; margin-top: 4px;">
        `;

        cat.tests.forEach(test => {
          const isChecked = node.selectedTests.has(test.id);
          testSuiteHtml += `
            <label class="test-item-row" data-test-id="${test.id}" style="display: flex; align-items: center; gap: 6px; font-size: 11px; color: #3c4043; cursor: pointer; padding: 2px 0;" title="${test.desc}">
              <input type="checkbox" class="chk-test-item" value="${test.id}" ${isChecked ? 'checked' : ''} />
              <span style="font-family: monospace;">${test.name}</span>
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
            const tId = row.getAttribute('data-test-id').toLowerCase();
            row.style.display = tId.includes(q) ? 'flex' : 'none';
          });
        });
      }

      const btnPresetSmoke = document.getElementById('btn-preset-smoke');
      if (btnPresetSmoke) {
        btnPresetSmoke.addEventListener('click', () => {
          node.selectedTests = new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']);
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
    if (btnToggle) {
      btnToggle.addEventListener('click', async () => {
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
    this.renderGraph();
    this.renderInspector();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
    this.updateExecutionControlsState();

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
      node.isTestingRunning = false;
      this.renderGraph();
      this.renderInspector();
      this.updateExecutionControlsState();
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
        lines.forEach(line => {
          if (line.trim()) {
            const type = line.includes('ERROR') || line.includes('FAIL') ? 'error' : (line.includes('PASS') || line.includes('SUCCESS') ? 'success' : 'info');
            this.logViewer.append(line, type);
          }
        });
      }
      this.logOffsets.set(sessionId, data.offset || offset);

      if (!data.running) {
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
        this.renderGraph();
        this.renderInspector();
        if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
        this.updateExecutionControlsState();

        // Check for test failures and fire notifications & automated AI triage (Requirement 6 & 8)
        if (node.testResults) {
          const results = Object.entries(node.testResults);
          const failCount = results.filter(([_, r]) => r.status === 'FAIL' || r.status === 'FAILED').length;
          const passCount = results.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;
          const failingEntry = results.find(([_, r]) => r.status === 'FAIL' || r.status === 'FAILED');

          if (failCount > 0 && failingEntry) {
            NotificationManager.notify({
              title: "⚠️ Test Failures Detected",
              body: `Device [${node.inputs.device_id || node.label}]: ${failCount} failed, ${passCount} passed. Mantis AI is automatically triaging the root cause...`,
              type: "warning",
              duration: 8000
            });
            this.triggerMantisForTest(node, failingEntry[0], true /* autoRun */);
          } else if (passCount > 0) {
            NotificationManager.notify({
              title: "✅ Test Suite Passed",
              body: `Device [${node.inputs.device_id || node.label}]: All ${passCount} compliance tests passed successfully!`,
              type: "success",
              duration: 6000
            });
          }

          this.checkAndDispatchEmailResultAlert(node, passCount, failCount);
        }
      }
    } catch (e) {
      console.error("Poll status error:", e);
    }
  }

  async stopDeviceTests(node) {
    if (!node || !node.lastSessionId) {
      node.isTestingRunning = false;
      this.renderGraph();
      this.renderInspector();
      this.updateExecutionControlsState();
      return;
    }

    if (this.logViewer) {
      this.logViewer.append(`\n🛑 Abort signal dispatched by user. Stopping session ${node.lastSessionId}...`, 'warn');
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
    const devId = node.inputs.device_id || node.label;
    try {
      const res = await fetch(`/api/device_results?site_model=${encodeURIComponent(this.siteModel)}&device=${encodeURIComponent(devId)}`);
      if (res.ok) {
        const data = await res.json();
        if (data.results && Object.keys(data.results).length > 0) {
          node.testResults = data.results;
        } else if (!node.testResults) {
          // Provide baseline default state if no tests have run on disk yet
          node.testResults = {};
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
    const data = {
      deviceId: node.inputs.device_id || 'AHU-1',
      testId: testId,
      siteModel: this.siteModel,
      projectSpec: this.projectSpec,
      sessionId: node.lastSessionId,
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

        const sysTests = results.filter(([t]) => t.startsWith('system.'));
        const sysPass = sysTests.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;
        if (sysTests.length > 0) sysPill = (sysPass === sysTests.length ? `<span class="status-pill pass">✅ ${sysPass}/${sysTests.length}</span>` : `<span class="status-pill fail">❌ ${sysTests.length - sysPass} Fail</span>`);

        const ptTests = results.filter(([t]) => t.startsWith('pointset.'));
        const ptPass = ptTests.filter(([_, r]) => r.status === 'PASS' || r.status === 'PASSED').length;
        if (ptTests.length > 0) ptPill = (ptPass === ptTests.length ? `<span class="status-pill pass">✅ ${ptPass}/${ptTests.length}</span>` : `<span class="status-pill fail">❌ ${ptTests.length - ptPass} Fail</span>`);

        const gwTests = results.filter(([t]) => t.startsWith('gateway.'));
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
      if (node.type === 'pubber' || node.type === 'udmis' || node.type === 'mqtt_broker' || node.type === 'etcd' || node.type === 'influx' || node.type === 'postgresql') {
        const res = await fetch(`/api/testbed/status?site_model=${encodeURIComponent(this.siteModel)}`);
        if (res.ok) {
          const data = await res.json();
          const components = data.components || {};
          if (node.type === 'mqtt_broker') newStatus = components.mqtt_broker && components.mqtt_broker.status === 'UP' ? 'UP' : 'DOWN';
          else if (node.type === 'udmis') newStatus = components.udmis && components.udmis.status === 'UP' ? 'UP' : 'DOWN';
          else if (node.type === 'etcd') newStatus = components.etcd && components.etcd.status === 'UP' ? 'UP' : 'DOWN';
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

  // --- SETUP AND NODE CONFIGURATION POPUP MODALS & DROPDOWN ---
  openSetupModal(mode) {
    if (!this.setupModal) return;
    this.setupModal.style.display = 'flex';
    this.setupModal.classList.add('active');
    if (this.setupTargetMode) this.setupTargetMode.value = mode;
    if (this.setupSiteModelInput) this.setupSiteModelInput.value = this.siteModel || 'sites/udmi_site_model';
    if (this.setupModalTitle) this.setupModalTitle.textContent = `Configure ${mode === 'LOCAL' ? 'Local' : 'Cloud'} Setup`;
    if (this.setupLocalFields) this.setupLocalFields.style.display = mode === 'LOCAL' ? 'flex' : 'none';
    if (this.setupCloudFields) this.setupCloudFields.style.display = mode === 'CLOUD' ? 'flex' : 'none';
    if (mode === 'LOCAL' && this.setupLocalPort) {
      const match = this.projectSpec.match(/:(\d+)/);
      this.setupLocalPort.value = match ? match[1] : '18833';
    } else if (mode === 'CLOUD' && this.setupCloudProject) {
      this.setupCloudProject.value = this.projectSpec.includes('//') ? this.projectSpec : '//pubsub/bos-platform-dev';
    }
  }

  closeSetupModal() {
    if (this.setupModal) {
      this.setupModal.style.display = 'none';
      this.setupModal.classList.remove('active');
    }
  }

  applySetupFromModal() {
    const mode = this.setupTargetMode ? this.setupTargetMode.value : 'LOCAL';
    const newSiteModel = this.setupSiteModelInput ? this.setupSiteModelInput.value.trim() : 'sites/udmi_site_model';
    if (newSiteModel) {
      this.siteModel = newSiteModel;
      stateStore.set('siteModel', newSiteModel);
    }

    if (mode === 'LOCAL') {
      const port = this.setupLocalPort ? (this.setupLocalPort.value.trim() || '18833') : '18833';
      this.projectSpec = `//mqtt/localhost:${port}`;
      stateStore.set('projectSpec', this.projectSpec);
      this.loadDefaultSetup();
    } else {
      const target = this.setupCloudProject ? (this.setupCloudProject.value.trim() || '//pubsub/bos-platform-dev') : '//pubsub/bos-platform-dev';
      this.projectSpec = target;
      stateStore.set('projectSpec', this.projectSpec);
      this.loadCloudSetup();
    }
    this.closeSetupModal();
    NotificationManager.showToast({ title: `🌐 ${mode} Setup Applied`, message: `Configured for ${this.projectSpec}`, type: 'success' });
  }

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
      this.fetchDeviceResultsFromDisk(node);
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

  handleActualDevicesDropdownChange() {
    if (!this.selectActualDevices) return;
    const selectedDevIds = Array.from(this.selectActualDevices.selectedOptions).map(opt => opt.value);

    // Add newly selected devices to graph
    selectedDevIds.forEach((devId, idx) => {
      if (!this.deviceNodes.some(n => n.type === 'actual_device' && (n.id === devId || n.inputs.device_id === devId))) {
        const x = 60;
        const y = 260 + (this.deviceNodes.length * 95);
        const node = this.createNodeObject('actual_device', x, y);
        node.id = devId;
        node.inputs.device_id = devId;
        node.label = devId;
        this.deviceNodes.push(node);
        this.fetchDeviceResultsFromDisk(node);
      }
    });

    // Remove unselected actual_devices from graph
    this.deviceNodes = this.deviceNodes.filter(n => {
      if (n.type === 'actual_device') {
        return selectedDevIds.includes(n.id) || selectedDevIds.includes(n.inputs.device_id);
      }
      return true;
    });

    this.renderGraph();
    if (this.activeViewMode === 'matrix') this.renderComplianceMatrix();
  }
}
