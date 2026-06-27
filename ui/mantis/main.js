import { JSONViewer } from '../shared/components/json-viewer.js';
import { LogViewer } from '../shared/components/log-viewer.js';

// --- HELPER: DYNAMIC ENDPOINT FETCH ---
async function fetchDirectoryList(targetPath) {
  const url = `/api/list?path=${encodeURIComponent(targetPath)}`;
  const response = await fetch(url);
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `Server error: ${response.status}`);
  }
  return data;
}

async function readFileContent(filePath) {
  const url = `/api/read_file?path=${encodeURIComponent(filePath)}`;
  const response = await fetch(url);
  const data = await response.text();
  if (!response.ok) {
    const errorJson = JSON.parse(data);
    throw new Error(errorJson.error || `Server error: ${response.status}`);
  }
  return data;
}

function combinePaths(base, sub) {
  if (!base || base === '.') return sub;
  if (base.endsWith('/')) return base + sub;
  return base + '/' + sub;
}

function getParentPath(path) {
  if (!path || path === '.' || path === '/' || path === '' || path === '~') return '~';
  const parts = path.split('/').filter(p => p);
  if (parts.length <= 1) {
    return path.startsWith('~') ? '~' : (path.startsWith('/') ? '/' : '.');
  }
  parts.pop();
  if (parts[0] === '~') {
    return parts.join('/');
  }
  return (path.startsWith('/') ? '/' : '') + parts.join('/');
}


// --- MANTIS DECOUPLED CONTROLLER ---
class MantisController {
  constructor() {
    // Parent state (Synced via postMessage)
    this.siteModel = '';
    this.device = '';
    this.projectSpec = ''; // Piped dynamically from Sequencer

    // Local state
    this.activeTab = 'trace'; // 'trace' or 'diagnostics'
    this.activeTraceNodes = [];
    this.currentSelectedNodePayload = null;
    
    // Triage Subprocess State
    this.isTriageRunning = false;
    this.isTriageLoading = false;
    this.triageLogOffset = 0;
    this.triagePollInterval = null;
    this.isPollingTriage = false;
    this.hasHandledCompletion = false;

    this.initElements();
    this.initComponents();
    this.initEvents();
  }

  initElements() {
    // Local Selection Controls
    this.deviceSelect = document.getElementById('device-select');
    this.scenarioSelect = document.getElementById('scenario-select');
    
    // Tab Headers & Pages
    this.tabBtnTrace = document.getElementById('tab-btn-trace');
    this.tabBtnDiagnostics = document.getElementById('tab-btn-diagnostics');
    this.tabPageTrace = document.getElementById('tab-page-trace');
    this.tabPageDiagnostics = document.getElementById('tab-page-diagnostics');
    
    // Trace Tab Elements
    this.mantisTreeContainer = document.querySelector('.mantis-tree-container');
    this.mqttTopicLabel = document.getElementById('mqtt-topic');
    this.btnCopyPayload = document.getElementById('btn-copy-payload');
    
    // Diagnostics Tab Elements
    this.playbookSelect = document.getElementById('playbook-select');
    this.btnRunTriage = document.getElementById('btn-run-triage');
    this.btnStopTriage = document.getElementById('btn-stop-triage');
    this.triageStatusBadge = document.getElementById('triage-status-badge');
    this.triageTerminalContainer = document.getElementById('triage-terminal-container');
    this.rcaReportBody = document.getElementById('rca-report-body');
    this.btnCopyReport = document.getElementById('btn-copy-report');
    
    // Layout Container & Resizer
    this.diagnosticsLayout = document.querySelector('.diagnostics-layout');
    this.diagnosticsSidebar = document.querySelector('.diagnostics-sidebar');
    this.diagnosticsResizer = document.getElementById('diagnostics-resizer');

    // AI Credentials & Baseline Run Controls
    this.providerSelect = document.getElementById('mantis-provider-select');
    this.apiKeyInput = document.getElementById('mantis-api-key');
    this.gcpProjectInput = document.getElementById('mantis-gcp-project');
    this.gcpLocationInput = document.getElementById('mantis-gcp-location');
    this.groupGeminiKey = document.getElementById('group-gemini-key');
    this.groupVertexConfig = document.getElementById('group-vertex-config');
    this.successRunInput = document.getElementById('mantis-success-run');
    this.fetchUdmisLogsCheckbox = document.getElementById('mantis-fetch-udmis-logs');
    this.cloudProjectInput = document.getElementById('mantis-cloud-project');
    this.groupCloudLoggingConfig = document.getElementById('group-cloud-logging-config');

    // Mantis Folder Browser Modal Elements
    this.btnBrowseSuccessRun = document.getElementById('btn-browse-success-run');
    this.mantisBrowserModal = document.getElementById('mantis-folder-browser-modal');
    this.btnCloseMantisBrowser = document.getElementById('btn-close-mantis-browser');
    this.btnMantisBrowserUp = document.getElementById('btn-mantis-browser-up');
    this.mantisBrowserCurrentPath = document.getElementById('mantis-browser-current-path');
    this.mantisBrowserList = document.getElementById('mantis-browser-list');
    this.btnMantisBrowserCancel = document.getElementById('btn-mantis-browser-cancel');
    this.btnMantisBrowserSelect = document.getElementById('btn-mantis-browser-select');
    this.mantisBrowserPath = '~';
    this.selectedMantisBrowserFolder = null;

    // Compliance Test Verdict Elements
    this.testStatusBadge = document.getElementById('mantis-test-status-badge');
    this.testTargetBadge = document.getElementById('mantis-test-target-badge');
    this.testTimeBadge = document.getElementById('mantis-test-time-badge');
    this.deviceResults = {};
  }

  initComponents() {
    // Initialize JSONViewer (Trace tab)
    this.jsonViewer = new JSONViewer(document.getElementById('mqtt-json-viewer'));
    this.jsonViewer.render({ message: "Waiting for Site Model selection from parent shell..." });

    // Initialize LogViewer (Diagnostics tab)
    this.triageLogViewer = new LogViewer(this.triageTerminalContainer);

    // Load cached auth settings
    this.loadCachedAuthSettings();

    // Initialize interactive pane resizer
    this.initResizer();
  }

  initEvents() {
    // --- 1. POSTMESSAGE LISTENER (State Sync from Shell) ---
    window.addEventListener('message', (event) => {
      if (event.data) {
        if (event.data.type === 'udmi_state_change') {
          this.handleGlobalStateChange(event.data.siteModel);
        } else if (event.data.type === 'load_diagnose') {
          this.handleLoadDiagnose(event.data);
        }
      }
    });

    // Local Device / Scenario Dropdowns
    this.deviceSelect.addEventListener('change', (e) => {
      this.handleDeviceChange(e.target.value);
    });

    this.scenarioSelect.addEventListener('change', (e) => {
      this.handleScenarioChange(e.target.value);
    });

    // Copy to clipboard triggers
    this.btnCopyPayload.addEventListener('click', () => this.copyPayloadToClipboard());
    this.btnCopyReport.addEventListener('click', () => this.copyReportToClipboard());

    // Tab buttons triggers
    this.tabBtnTrace.addEventListener('click', () => this.switchLocalTab('trace'));
    this.tabBtnDiagnostics.addEventListener('click', () => this.switchLocalTab('diagnostics'));

    // Triage run controls
    this.btnRunTriage.addEventListener('click', () => this.startAITriage());
    this.btnStopTriage.addEventListener('click', () => this.stopAITriage());

    // AI Auth controls events
    this.providerSelect.addEventListener('change', () => this.toggleProviderControls());
    this.apiKeyInput.addEventListener('input', (e) => localStorage.setItem('udmi_mantis_api_key', e.target.value.trim()));
    this.gcpProjectInput.addEventListener('input', (e) => localStorage.setItem('udmi_mantis_gcp_project', e.target.value.trim()));
    this.gcpLocationInput.addEventListener('input', (e) => localStorage.setItem('udmi_mantis_gcp_location', e.target.value.trim()));
    if (this.fetchUdmisLogsCheckbox) {
      this.fetchUdmisLogsCheckbox.addEventListener('change', (e) => {
        localStorage.setItem('udmi_mantis_fetch_udmis', e.target.checked);
        this.toggleCloudLoggingControls();
      });
    }
    if (this.cloudProjectInput) {
      this.cloudProjectInput.addEventListener('input', (e) => localStorage.setItem('udmi_mantis_cloud_project', e.target.value.trim()));
    }

    // Prevent accidental tab closure during active AI triage
    window.addEventListener('beforeunload', (e) => {
      if (this.isTriageRunning) {
        e.preventDefault();
        e.returnValue = 'AI Triage is currently running. Are you sure you want to leave?';
        return e.returnValue;
      }
    });

    // --- 3. MANTIS FOLDER BROWSER LISTENERS ---
    if (this.btnBrowseSuccessRun) {
      this.btnBrowseSuccessRun.addEventListener('click', () => this.openMantisFolderBrowser());
    }
    if (this.btnCloseMantisBrowser) {
      this.btnCloseMantisBrowser.addEventListener('click', () => this.closeMantisFolderBrowser());
    }
    if (this.btnMantisBrowserCancel) {
      this.btnMantisBrowserCancel.addEventListener('click', () => this.closeMantisFolderBrowser());
    }
    if (this.btnMantisBrowserUp) {
      this.btnMantisBrowserUp.addEventListener('click', () => this.navigateMantisBrowserUp());
    }
    if (this.btnMantisBrowserSelect) {
      this.btnMantisBrowserSelect.addEventListener('click', () => this.selectMantisBrowserDirectory());
    }
    if (this.mantisBrowserCurrentPath) {
      this.mantisBrowserCurrentPath.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          this.loadMantisBrowserPath(e.target.value.trim());
        }
      });
    }
  }

  // --- RESIZER & TAB MACHINERY ---
  initResizer() {
    if (!this.diagnosticsResizer || !this.diagnosticsSidebar || !this.diagnosticsLayout) return;

    const savedWidth = localStorage.getItem('udmi_mantis_sidebar_width');
    if (savedWidth) {
      this.diagnosticsSidebar.style.flexBasis = (savedWidth.endsWith('%') || savedWidth.endsWith('px')) ? savedWidth : `${savedWidth}px`;
    } else {
      this.diagnosticsSidebar.style.flexBasis = '40%';
    }

    let isResizing = false;

    const onPointerDown = (e) => {
      isResizing = true;
      this.diagnosticsResizer.classList.add('resizing');
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      this.diagnosticsSidebar.style.transition = 'none';
    };

    const onPointerMove = (e) => {
      if (!isResizing) return;
      const layoutRect = this.diagnosticsLayout.getBoundingClientRect();
      let newWidth = e.clientX - layoutRect.left;
      
      const minWidth = 250;
      const maxWidth = layoutRect.width - 300;
      if (newWidth < minWidth) newWidth = minWidth;
      if (newWidth > maxWidth) newWidth = maxWidth;

      this.diagnosticsSidebar.style.flexBasis = `${newWidth}px`;
    };

    const onPointerUp = () => {
      if (isResizing) {
        isResizing = false;
        this.diagnosticsResizer.classList.remove('resizing');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        this.diagnosticsSidebar.style.transition = 'flex-basis 0.3s ease';
        const currentWidth = parseFloat(this.diagnosticsSidebar.style.flexBasis);
        if (currentWidth) {
          localStorage.setItem('udmi_mantis_sidebar_width', currentWidth);
        }
      }
    };

    this.diagnosticsResizer.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
  }

  loadCachedAuthSettings() {
    const provider = localStorage.getItem('udmi_mantis_provider') || 'gemini';
    this.providerSelect.value = provider;
    this.apiKeyInput.value = localStorage.getItem('udmi_mantis_api_key') || '';
    this.gcpProjectInput.value = localStorage.getItem('udmi_mantis_gcp_project') || '';
    this.gcpLocationInput.value = localStorage.getItem('udmi_mantis_gcp_location') || 'global';
    if (this.successRunInput) {
      this.successRunInput.value = localStorage.getItem('udmi_mantis_success_run') || '';
    }
    if (this.fetchUdmisLogsCheckbox) {
      this.fetchUdmisLogsCheckbox.checked = localStorage.getItem('udmi_mantis_fetch_udmis') === 'true';
    }
    if (this.cloudProjectInput) {
      this.cloudProjectInput.value = localStorage.getItem('udmi_mantis_cloud_project') || '';
    }
    this.toggleProviderControls();
  }

  toggleProviderControls() {
    const provider = this.providerSelect.value;
    localStorage.setItem('udmi_mantis_provider', provider);
    if (provider === 'vertex') {
      this.groupGeminiKey.style.display = 'none';
      this.groupVertexConfig.style.display = 'flex';
    } else {
      this.groupGeminiKey.style.display = 'flex';
      this.groupVertexConfig.style.display = 'none';
    }
    this.toggleCloudLoggingControls();
  }

  toggleCloudLoggingControls() {
    const isChecked = this.fetchUdmisLogsCheckbox ? this.fetchUdmisLogsCheckbox.checked : false;
    if (this.groupCloudLoggingConfig) {
      if (isChecked) {
        this.groupCloudLoggingConfig.style.display = 'flex';
      } else {
        this.groupCloudLoggingConfig.style.display = 'none';
      }
    }
  }

  // --- FOLDER BROWSER MODAL CONTROLLER FOR BASELINE RUN ---
  openMantisFolderBrowser() {
    const currentVal = this.successRunInput ? this.successRunInput.value.trim() : '';
    this.mantisBrowserPath = currentVal || '~';
    this.selectedMantisBrowserFolder = null;
    if (this.mantisBrowserModal) {
      this.mantisBrowserModal.classList.add('active');
    }
    this.loadMantisBrowserPath(this.mantisBrowserPath);
  }

  closeMantisFolderBrowser() {
    if (this.mantisBrowserModal) {
      this.mantisBrowserModal.classList.remove('active');
    }
  }

  async loadMantisBrowserPath(path) {
    this.selectedMantisBrowserFolder = null;
    if (this.mantisBrowserList) {
      this.mantisBrowserList.innerHTML = '<div style="padding:16px; text-align:center; color:var(--text-muted);">Reading directory...</div>';
    }
    
    try {
      const data = await fetchDirectoryList(path);
      this.mantisBrowserPath = data.path;
      if (this.mantisBrowserCurrentPath) {
        this.mantisBrowserCurrentPath.value = data.path;
      }
      this.renderMantisBrowserList(data.folders);
    } catch (err) {
      if (path !== '~') {
        console.warn(`Failed to load '${path}', falling back to '~':`, err);
        return this.loadMantisBrowserPath('~');
      }
      if (this.mantisBrowserList) {
        this.mantisBrowserList.innerHTML = `<div style="padding:16px; text-align:center; color:var(--color-error);">Error: ${err.message}</div>`;
      }
    }
  }

  renderMantisBrowserList(folders) {
    if (!this.mantisBrowserList) return;
    this.mantisBrowserList.innerHTML = '';
    
    if (folders.length === 0) {
      this.mantisBrowserList.innerHTML = '<div style="padding:16px; text-align:center; color:var(--text-muted);">No subdirectories found</div>';
      return;
    }

    folders.forEach(folder => {
      const itemEl = document.createElement('div');
      itemEl.className = 'browser-item';
      itemEl.innerHTML = `
        <span class="material-symbols-outlined">folder</span>
        <span>${folder}</span>
      `;
      
      itemEl.addEventListener('click', (e) => {
        e.stopPropagation();
        this.mantisBrowserList.querySelectorAll('.browser-item').forEach(el => el.classList.remove('selected'));
        itemEl.classList.add('selected');
        this.selectedMantisBrowserFolder = folder;
      });

      itemEl.addEventListener('dblclick', (e) => {
        e.stopPropagation();
        const nextPath = combinePaths(this.mantisBrowserPath, folder);
        this.loadMantisBrowserPath(nextPath);
      });

      this.mantisBrowserList.appendChild(itemEl);
    });
  }

  navigateMantisBrowserUp() {
    const parent = getParentPath(this.mantisBrowserPath);
    if (parent !== null && parent !== '') {
      this.loadMantisBrowserPath(parent);
    }
  }

  selectMantisBrowserDirectory() {
    let finalPath = this.mantisBrowserPath;
    if (this.selectedMantisBrowserFolder) {
      finalPath = combinePaths(this.mantisBrowserPath, this.selectedMantisBrowserFolder);
    }
    if (this.successRunInput) {
      this.successRunInput.value = finalPath;
      localStorage.setItem('udmi_mantis_success_run', finalPath);
    }
    this.closeMantisFolderBrowser();
  }

  switchLocalTab(tabId) {
    this.activeTab = tabId;
    
    // Toggle button active states (primary vs outlined)
    this.tabBtnTrace.classList.toggle('btn-primary', tabId === 'trace');
    this.tabBtnTrace.classList.toggle('btn-outlined', tabId !== 'trace');
    
    this.tabBtnDiagnostics.classList.toggle('btn-primary', tabId === 'diagnostics');
    this.tabBtnDiagnostics.classList.toggle('btn-outlined', tabId !== 'diagnostics');
    
    // Toggle page visibility
    this.tabPageTrace.classList.toggle('active', tabId === 'trace');
    this.tabPageDiagnostics.classList.toggle('active', tabId === 'diagnostics');
  }

  // --- STATE SYNC & SCENARIO DISCOVERY ---
  async handleGlobalStateChange(siteModel) {
    this.siteModel = siteModel;
    
    this.deviceSelect.innerHTML = '<option value="">-- Select Device --</option>';
    this.deviceSelect.disabled = true;
    this.device = '';
    
    this.scenarioSelect.innerHTML = '<option value="">-- Waiting for device --</option>';
    this.scenarioSelect.disabled = true;
    
    this.resetMantisWorkspace();

    if (!siteModel) {
      this.jsonViewer.render({ message: "Waiting for Site Model selection from parent shell..." });
      return;
    }

    this.jsonViewer.render({ message: "Select a device to view trace history." });
    this.deviceSelect.innerHTML = '<option value="">Scanning devices...</option>';

    const devicesPath = combinePaths(siteModel, 'devices');
    try {
      const data = await fetchDirectoryList(devicesPath);
      const devices = data.folders;
      this.deviceSelect.innerHTML = '<option value="">-- Select Device --</option>';

      if (devices.length > 0) {
        devices.forEach(dev => {
          const opt = document.createElement('option');
          opt.value = dev;
          opt.textContent = dev;
          this.deviceSelect.appendChild(opt);
        });
        
        this.deviceSelect.disabled = false;
        
        // Restore last selected device
        const cachedDev = localStorage.getItem('udmi_last_selected_device_mantis');
        if (cachedDev && devices.includes(cachedDev)) {
          this.deviceSelect.value = cachedDev;
          this.handleDeviceChange(cachedDev);
        }
      } else {
        this.deviceSelect.innerHTML = '<option value="">-- No devices found under path --</option>';
      }
    } catch (err) {
      this.deviceSelect.innerHTML = '<option value="">-- Invalid site path or no devices --</option>';
      console.error('Error scanning devices inside mantis:', err);
    }
  }

  async handleDeviceChange(device) {
    this.device = device;
    
    this.scenarioSelect.innerHTML = '<option value="">-- Select Scenario --</option>';
    this.scenarioSelect.disabled = true;
    this.resetMantisWorkspace();

    if (!device) {
      localStorage.removeItem('udmi_last_selected_device_mantis');
      this.jsonViewer.render({ message: "Select a device to view trace history." });
      return;
    }

    localStorage.setItem('udmi_last_selected_device_mantis', device);
    this.jsonViewer.render({ message: "Select a debug scenario from the toolbar." });
    this.scenarioSelect.innerHTML = '<option value="">Scanning scenarios...</option>';

    // Fetch device compliance test results for status badges
    try {
      const res = await fetch(`/api/device_results?site_model=${encodeURIComponent(this.siteModel)}&device=${encodeURIComponent(device)}`);
      if (res.ok) {
        const resData = await res.json();
        this.deviceResults = resData.results || {};
      }
    } catch (err) {
      console.error('Error fetching device results in mantis:', err);
    }

    const testsPath = combinePaths(this.siteModel, `out/devices/${device}/tests`);
    try {
      const data = await fetchDirectoryList(testsPath);
      const scenarios = data.folders;
      this.scenarioSelect.innerHTML = '<option value="">-- Select Scenario --</option>';

      if (scenarios.length > 0) {
        scenarios.forEach(sc => {
          const opt = document.createElement('option');
          opt.value = sc;
          const statusInfo = this.deviceResults[sc];
          const st = statusInfo ? (statusInfo.status || '').toLowerCase() : '';
          let symbol = '';
          if (st === 'pass') symbol = ' ✔';
          else if (st === 'fail' || st === 'error') symbol = ' ✘';
          else if (st === 'skip') symbol = ' ⊘';
          opt.textContent = this.formatScenarioTitle(sc) + symbol;
          this.scenarioSelect.appendChild(opt);
        });
        this.scenarioSelect.disabled = false;
      } else {
        this.scenarioSelect.innerHTML = '<option value="">-- No test runs found --</option>';
      }
    } catch (err) {
      this.scenarioSelect.innerHTML = '<option value="">-- No test runs found --</option>';
      console.error('Error scanning device scenarios inside mantis:', err);
    }
  }

  // --- CHROMATOGRAPHIC SEQUENCE TRACE LOADERS ---
  async handleScenarioChange(scenarioId) {
    this.resetMantisWorkspace();
    this.updateScenarioVerdict(scenarioId);
    if (!scenarioId) return;

    const statusInfo = this.deviceResults && this.deviceResults[scenarioId];
    if (statusInfo && (statusInfo.project_spec || statusInfo.target_project)) {
      const pSpec = statusInfo.project_spec || statusInfo.target_project;
      this.projectSpec = pSpec;
      if (this.cloudProjectInput && pSpec && pSpec !== 'localhost') {
        this.cloudProjectInput.value = pSpec;
      }
    }

    this.mantisTreeContainer.innerHTML = '<div style="text-align:center; padding-top:40px; color:var(--text-muted)">Loading transition sequence...</div>';

    const scenarioPath = combinePaths(this.siteModel, `out/devices/${this.device}/tests/${scenarioId}`);

    try {
      const dirData = await fetchDirectoryList(scenarioPath);
      const files = dirData.files;
      const attrFiles = files.filter(f => f.endsWith('.attr'));

      if (attrFiles.length === 0) {
        this.mantisTreeContainer.innerHTML = '<div style="text-align:center; padding-top:40px; color:var(--color-error)">No message logs found for this scenario.</div>';
        return;
      }

      // Load all .attr metadata blocks in parallel
      const loadPromises = attrFiles.map(async (attrFile) => {
        const fullAttrPath = combinePaths(scenarioPath, attrFile);
        try {
          const attrContent = await readFileContent(fullAttrPath);
          const meta = JSON.parse(attrContent);
          const jsonFile = attrFile.replace('.attr', '.json');
          const fullJsonPath = combinePaths(scenarioPath, jsonFile);

          return {
            id: attrFile.replace('.attr', ''),
            title: `${meta.subType}.${meta.subFolder}`,
            time: meta.publishTime,
            timestamp: new Date(meta.publishTime),
            subFolder: meta.subFolder,
            subType: meta.subType,
            transactionId: meta.transactionId,
            jsonPath: fullJsonPath,
            meta: meta
          };
        } catch (e) {
          console.error(`Error reading metadata file: ${attrFile}`, e);
          return null;
        }
      });

      const loadedNodes = (await Promise.all(loadPromises)).filter(node => node !== null);

      if (loadedNodes.length === 0) {
        this.mantisTreeContainer.innerHTML = '<div style="text-align:center; padding-top:40px; color:var(--color-error)">Failed to parse message metadata.</div>';
        return;
      }

      // Sort messages chronologically by timestamp
      loadedNodes.sort((a, b) => a.timestamp - b.timestamp);
      this.activeTraceNodes = loadedNodes;

      // Render nodes
      this.renderTraceTimeline();
    } catch (err) {
      this.mantisTreeContainer.innerHTML = `<div style="text-align:center; padding-top:40px; color:var(--color-error)">Error loading scenario: ${err.message}</div>`;
      console.error(err);
    }
  }

  renderTraceTimeline() {
    this.mantisTreeContainer.innerHTML = '';

    this.activeTraceNodes.forEach((node, idx) => {
      const nodeEl = document.createElement('div');
      nodeEl.className = 'state-node-item';
      nodeEl.id = node.id;
      
      const statusType = node.subType === 'config' ? 'pending' : 'success';
      const timeStr = node.time ? new Date(node.time).toLocaleTimeString() : '';

      nodeEl.innerHTML = `
        <div class="node-indicator ${statusType}">
          <span class="material-symbols-outlined">${this.getMantisIconName(node.subType)}</span>
        </div>
        <div class="node-content">
          <div class="node-header">
            <span class="node-title">${node.title}</span>
            <span class="node-timestamp">${timeStr}</span>
          </div>
          <span class="node-desc">Transaction ID: ${node.transactionId || 'N/A'}</span>
        </div>
      `;

      nodeEl.addEventListener('click', () => this.selectTraceNode(nodeEl, node));
      this.mantisTreeContainer.appendChild(nodeEl);
    });
  }

  async selectTraceNode(element, node) {
    this.mantisTreeContainer.querySelectorAll('.state-node-item').forEach(el => {
      el.classList.remove('active');
    });
    element.classList.add('active');

    this.mqttTopicLabel.textContent = `devices/${this.device}/${node.subFolder}`;

    this.jsonViewer.render({ message: "Reading JSON payload..." });
    this.btnCopyPayload.disabled = true;

    try {
      const jsonContent = await readFileContent(node.jsonPath);
      const payload = JSON.parse(jsonContent);
      
      this.jsonViewer.render(payload);
      this.currentSelectedNodePayload = JSON.stringify(payload, null, 2);
      this.btnCopyPayload.disabled = false;
    } catch (err) {
      this.jsonViewer.render({ error: `Failed to load payload: ${err.message}` });
      console.error(err);
    }
  }

  // --- CROSS-IFRAME AUTO-TRIAGE REDIRECTION (PostMessage) ---
  async handleLoadDiagnose(data) {
    if (this.isTriageRunning || this.isTriageLoading) {
      console.log('Mantis Screen: Triage run or load already in progress. Ignoring duplicate trigger.');
      return;
    }
    this.isTriageLoading = true;

    const { testId, deviceId, siteModel, projectSpec } = data;
    console.log(`Mantis Screen: Received load_diagnose payload for test ${testId} on device ${deviceId}`);
    
    this.siteModel = siteModel;
    this.projectSpec = projectSpec || ''; // Pipe target project specification
    
    // Set device select and trigger downstream loading
    this.deviceSelect.value = deviceId;
    await this.handleDeviceChange(deviceId);
    
    // Set scenario select and load sequence flow
    this.scenarioSelect.value = testId;
    await this.handleScenarioChange(testId);
    
    // Switch to diagnostics tab (waiting for user to click "Run AI Triage")
    this.switchLocalTab('diagnostics');
    this.isTriageLoading = false;
  }

  // --- MANTIS AI TRIAGE EXECUTION LOOP ---
  async startAITriage() {
    if (this.isTriageRunning) return;
    this.isTriageLoading = false; // Transition from loading to running state

    const deviceId = this.device;
    const testId = this.scenarioSelect.value;

    if (!deviceId || !testId) {
      alert('Please select a Device and Scenario before running diagnostics.');
      return;
    }

    // Request Desktop Notification permission if default
    if (typeof Notification !== 'undefined' && Notification.permission === 'default') {
      Notification.requestPermission();
    }

    this.isTriageRunning = true;
    this.triageLogOffset = 0;

    // Trigger panel sliding: expand console to 60% wide
    this.diagnosticsLayout.classList.remove('complete');
    this.diagnosticsLayout.classList.add('running');

    // Adjust button controls
    this.btnRunTriage.disabled = true;
    this.btnStopTriage.style.display = 'inline-flex';
    this.deviceSelect.disabled = true;
    this.scenarioSelect.disabled = true;
    this.playbookSelect.disabled = true;
    
    // Update badge state
    this.triageStatusBadge.textContent = 'Running';
    this.triageStatusBadge.className = 'badge badge-running';

    // Clear views
    this.triageLogViewer.clear();
    this.triageLogViewer.append(`Starting Mantis AI Triage Agent------\nDevice: ${deviceId}\nTest ID: ${testId}\nPlaybook: ${this.playbookSelect.value.toUpperCase()}\n------------------------------------------------\n`, 'info');
    
    this.rcaReportBody.innerHTML = `
      <div class="rca-placeholder-message" style="display: flex; flex-direction: column; align-items: center; text-align: center; gap: 14px;">
        <div class="loader" style="margin-bottom: 12px;"></div>
        <span style="font-weight: 500; color: var(--text-primary); font-size: 15px; max-width: 500px; line-height: 1.5;">
          Mantis AI is digesting compliance logs, scanning codebase references, and compiling the Root Cause Analysis...
        </span>
        <div style="font-size: 13px; color: var(--text-secondary); max-width: 460px; line-height: 1.5; background-color: var(--bg-surface-container); padding: 10px 16px; border-radius: var(--radius-md); border: 1px solid var(--border-color); display: flex; align-items: center; gap: 8px;">
          <span class="material-symbols-outlined" style="color: var(--color-primary); font-size: 20px;">notifications_active</span>
          <span>You will receive a notification when the RCA report is ready. Please do not close this tab.</span>
        </div>
      </div>
    `;
    this.btnCopyReport.disabled = true;

    // Trigger API
    const playbook = this.playbookSelect.value;
    const projectSpec = this.projectSpec || '//mqtt/localhost';
    const provider = this.providerSelect.value;
    const apiKey = this.apiKeyInput.value.trim();
    const gcpProject = this.gcpProjectInput.value.trim();
    const gcpLocation = this.gcpLocationInput.value.trim() || 'global';
    const successRun = this.successRunInput ? this.successRunInput.value.trim() : '';
    const fetchUdmis = this.fetchUdmisLogsCheckbox ? this.fetchUdmisLogsCheckbox.checked : false;
    const cloudProject = (this.cloudProjectInput ? this.cloudProjectInput.value.trim() : '') || gcpProject;

    if (this.successRunInput) {
      localStorage.setItem('udmi_mantis_success_run', successRun);
    }

    let runUrl = `/api/run_triage?device_id=${encodeURIComponent(deviceId)}&test_id=${encodeURIComponent(testId)}&playbook=${playbook}&site_model=${encodeURIComponent(this.siteModel)}&project_spec=${encodeURIComponent(projectSpec)}`;
    if (successRun) {
      runUrl += `&success_run=${encodeURIComponent(successRun)}`;
    }
    if (fetchUdmis) {
      runUrl += `&fetch_udmis=true`;
      if (cloudProject) {
        runUrl += `&cloud_project=${encodeURIComponent(cloudProject)}`;
      }
    }
    if (provider === 'vertex') {
      runUrl += `&use_vertex=true&gcp_project=${encodeURIComponent(gcpProject)}&gcp_location=${encodeURIComponent(gcpLocation)}`;
    } else if (apiKey) {
      runUrl += `&gemini_api_key=${encodeURIComponent(apiKey)}`;
    }
    
    try {
      const res = await fetch(runUrl);
      const startData = await res.json();
      if (!res.ok) {
        throw new Error(startData.error || `HTTP ${res.status}`);
      }

      if (startData.session_id) {
        this.currentSessionId = startData.session_id;
      }

      if (startData.status === "Skipped") {
        this.triageStatusBadge.textContent = 'Passed';
        this.triageStatusBadge.className = 'badge badge-success';
        this.triageLogViewer.append(`\n✨ Triage Bypassed: ${startData.message}\n`, 'info');
        
        this.rcaReportBody.innerHTML = `
          <div class="rca-placeholder-message">
            <span class="material-symbols-outlined" style="font-size:54px; color:var(--color-tertiary); margin-bottom:12px;">check_circle</span>
            <span style="font-weight: 600; font-size: 16px; color: var(--text-primary);">Test Case Passed Successfully!</span>
            <span style="font-size: 13.5px; color: var(--text-secondary); margin-top: 8px; max-width: 440px; text-align: center; line-height: 1.5;">
              Mantis AI Triage is skipped because compliance test case <strong>'${testId}'</strong> on device <strong>'${deviceId}'</strong> is in a healthy, passing state. No root-cause analysis is needed.
            </span>
          </div>
        `;
        
        this.stopAITriage(false);
        return;
      }

      this.hasHandledCompletion = false;
      this.isPollingTriage = false;

      // Start Polling
      this.triagePollInterval = setInterval(() => this.pollTriageStatus(), 500);
    } catch (err) {
      this.triageLogViewer.append(`\n❌ Error starting triage: ${err.message}\n`, 'error');
      this.stopAITriage(true); // Terminate with error
    }
  }

  async stopAITriage(hasError = false) {
    // Clear poll loop
    if (this.triagePollInterval) {
      clearInterval(this.triagePollInterval);
      this.triagePollInterval = null;
    }

    if (this.isTriageRunning && !hasError) {
      // Direct request to stop subprocess
      try {
        await fetch('/api/stop_triage');
      } catch {}
      this.triageLogViewer.append('\n🛑 Triage process terminated by user.\n', 'error');
    }

    this.isTriageRunning = false;
    this.isTriageLoading = false;

    // Reset panel sliding to default
    this.diagnosticsLayout.classList.remove('running', 'complete');

    // Restore buttons and controls
    this.btnRunTriage.disabled = false;
    this.btnStopTriage.style.display = 'none';
    this.deviceSelect.disabled = false;
    this.scenarioSelect.disabled = false;
    this.playbookSelect.disabled = false;

    if (hasError) {
      this.triageStatusBadge.textContent = 'Error';
      this.triageStatusBadge.className = 'badge badge-error';
      this.rcaReportBody.innerHTML = `
        <div class="rca-placeholder-message" style="color:var(--color-error)">
          <span class="material-symbols-outlined" style="font-size:48px;">error</span>
          <span>Diagnostics aborted due to execution failure. Review the console logs on the left.</span>
        </div>
      `;
    } else if (this.triageStatusBadge.textContent === 'Running') {
      this.triageStatusBadge.textContent = 'Aborted';
      this.triageStatusBadge.className = 'badge badge-error';
      this.rcaReportBody.innerHTML = `
        <div class="rca-placeholder-message">
          <span class="material-symbols-outlined" style="font-size:48px;">cancel</span>
          <span>AI Triage run was manually aborted.</span>
        </div>
      `;
    }
  }

  async pollTriageStatus() {
    if (this.isPollingTriage) return;
    this.isPollingTriage = true;
    const url = `/api/triage_status?offset=${this.triageLogOffset}`;
    try {
      const response = await fetch(url);
      const data = await response.json();
      
      if (!response.ok) {
        throw new Error(data.error || `HTTP ${response.status}`);
      }

      // Stream stdout/stderr character-by-character
      if (data.log) {
        this.triageLogViewer.append(data.log);
      }
      this.triageLogOffset = data.offset;

      // Handle process exit
      if (data.running === false && data.exit_code !== null) {
        if (this.hasHandledCompletion) return;
        this.hasHandledCompletion = true;

        clearInterval(this.triagePollInterval);
        this.triagePollInterval = null;
        this.isTriageRunning = false;

        this.btnRunTriage.disabled = false;
        this.btnStopTriage.style.display = 'none';
        this.deviceSelect.disabled = false;
        this.scenarioSelect.disabled = false;
        this.playbookSelect.disabled = false;

        if (data.exit_code === 0) {
          this.triageStatusBadge.textContent = 'Complete';
          this.triageStatusBadge.className = 'badge badge-success';
          this.triageLogViewer.append('\n✨ Triage complete! Root Cause Analysis generated successfully.\n', 'info');
          
          // Trigger panel sliding: shrink console to 280px sidebar, expand RCA report!
          this.diagnosticsLayout.classList.remove('running');
          this.diagnosticsLayout.classList.add('complete');

          // Show Desktop Notification
          if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
            new Notification("Mantis AI Triage Complete", {
              body: `Root Cause Analysis for '${this.scenarioSelect.value}' on device '${this.device}' is ready!`,
              tag: 'mantis-triage'
            });
          }

          // Load the rich Markdown report!
          await this.loadTriageReport();
        } else {
          this.triageStatusBadge.textContent = `Failed (${data.exit_code})`;
          this.triageStatusBadge.className = 'badge badge-error';
          this.triageLogViewer.append(`\n❌ AI Diagnostics failed with exit code ${data.exit_code}.\n`, 'error');
          
          // Reset panel sliding to default on failure
          this.diagnosticsLayout.classList.remove('running', 'complete');

          // Show Failure Desktop Notification
          if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
            new Notification("Mantis AI Triage Failed", {
              body: `Diagnostics for '${this.scenarioSelect.value}' on device '${this.device}' failed (Exit Code: ${data.exit_code}).`,
              tag: 'mantis-triage'
            });
          }

          this.rcaReportBody.innerHTML = `
            <div class="rca-placeholder-message" style="color:var(--color-error)">
              <span class="material-symbols-outlined" style="font-size:48px;">error</span>
              <span>Diagnostics completed with errors (Exit Code: ${data.exit_code}). Review console logs on the left.</span>
            </div>
          `;
        }
      }
    } catch (err) {
      this.triageLogViewer.append(`\n⚠️ Poll connection error: ${err.message}\n`, 'error');
      this.stopAITriage(true);
    } finally {
      this.isPollingTriage = false;
    }
  }

  async loadTriageReport(sessionId) {
    const projectSpec = this.projectSpec || '//mqtt/localhost';
    const sid = sessionId || this.currentSessionId;
    let url = `/api/triage_report?site_model=${encodeURIComponent(this.siteModel)}&project_spec=${encodeURIComponent(projectSpec)}&device_id=${encodeURIComponent(this.device)}&test_id=${encodeURIComponent(this.scenarioSelect.value)}`;
    if (sid) {
      url += `&session_id=${encodeURIComponent(sid)}`;
    }
    
    try {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`Report not found (Status ${response.status})`);
      }
      const markdown = await response.text();
      
      // Render report beautifully
      this.rcaReportBody.innerHTML = this.parseMarkdownToHTML(markdown);
      this.btnCopyReport.disabled = false;
      this.btnCopyReport.setAttribute('data-raw-markdown', markdown);
    } catch (err) {
      this.rcaReportBody.innerHTML = `
        <div class="rca-placeholder-message" style="color:var(--color-error)">
          <span class="material-symbols-outlined" style="font-size:48px;">error</span>
          <span>Failed to load diagnostic report: ${err.message}</span>
        </div>
      `;
    }
  }

  // --- PREMIUM HYBRID BLOCK-BASED MARKDOWN RENDERER ---
  parseMarkdownToHTML(md) {
    if (!md) return '';
    
    let text = md.trim();
    // Strip top-level fenced code block wrapper if LLM returned entire report enclosed in ```markdown ... ```
    if (text.startsWith('```')) {
      text = text.replace(/^```[a-zA-Z]*\r?\n/, '').replace(/\r?\n```$/, '').trim();
    }

    // 1. Escape HTML tags to protect the dashboard
    let escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    
    // 2. Pre-process block elements: Fenced code blocks
    // Temporarily replace fenced code blocks with placeholders to protect them from inline parsing
    const codeBlocks = [];
    escaped = escaped.replace(/```([\s\S]*?)```/g, (match, code) => {
      let cleanedCode = code.trim();
      const firstLineEnd = cleanedCode.indexOf('\n');
      if (firstLineEnd !== -1) {
        const firstLine = cleanedCode.substring(0, firstLineEnd).trim();
        if (/^[a-zA-Z0-9_-]+$/.test(firstLine)) {
          cleanedCode = cleanedCode.substring(firstLineEnd + 1).trim();
        }
      }
      const placeholder = `__CODE_BLOCK_PLACEHOLDER_${codeBlocks.length}__`;
      codeBlocks.push(`<pre class="markdown-code-block"><code>${cleanedCode}</code></pre>`);
      return placeholder;
    });

    // 3. Split into lines to parse line-level structures (lists, blockquotes, headings, tables)
    const lines = escaped.split('\n');
    const processedLines = [];
    
    let inList = false;
    let listType = null; // 'ul' or 'ol'
    let inTable = false;
    
    const closeListIfNeeded = () => {
      if (inList) {
        processedLines.push(`</${listType}>`);
        inList = false;
        listType = null;
      }
    };

    for (let i = 0; i < lines.length; i++) {
      let line = lines[i];
      
      // Match Table Rows: starts and ends with |
      const isTableRow = line.trim().startsWith('|') && line.trim().endsWith('|');
      if (isTableRow) {
        if (!inTable) {
          // Peek at next line to see if it is a markdown separator (e.g. | :--- | :--- |)
          const nextLine = lines[i + 1] || '';
          const isSeparator = nextLine.trim().startsWith('|') && nextLine.includes('---');
          if (isSeparator) {
            closeListIfNeeded();
            inTable = true;
            processedLines.push('<table class="markdown-table"><thead>');
            
            // Extract columns, filtering out the first and last empty elements
            const cols = line.split('|').map(c => c.trim()).filter((c, idx, arr) => idx > 0 && idx < arr.length - 1);
            processedLines.push('<tr>' + cols.map(c => `<th>${c}</th>`).join('') + '</tr>');
            processedLines.push('</thead><tbody>');
            
            i++; // Skip the separator line
            continue;
          }
        } else {
          // Render table body row
          const cols = line.split('|').map(c => c.trim()).filter((c, idx, arr) => idx > 0 && idx < arr.length - 1);
          processedLines.push('<tr>' + cols.map(c => `<td>${c}</td>`).join('') + '</tr>');
          continue;
        }
      } else {
        if (inTable) {
          processedLines.push('</tbody></table>');
          inTable = false;
        }
      }

      // Match Headings: # Header, ## Header, etc.
      const headingMatch = line.match(/^(#{1,6})\s+(.*?)$/);
      if (headingMatch) {
        closeListIfNeeded();
        const level = headingMatch[1].length;
        processedLines.push(`<h${level}>${headingMatch[2]}</h${level}>`);
        continue;
      }
      
      // Match GitHub Alerts: &gt; [!NOTE] text
      const alertMatch = line.match(/^&gt;\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*(.*?)$/i);
      if (alertMatch) {
        closeListIfNeeded();
        const type = alertMatch[1].toLowerCase();
        const content = alertMatch[2];
        const icon = this.getAlertIcon(type);
        processedLines.push(`<div class="markdown-alert alert-${type}"><span class="material-symbols-outlined alert-icon">${icon}</span><div class="alert-content">${content}</div></div>`);
        continue;
      }
      
      // Match Standard Blockquotes: &gt; text
      const quoteMatch = line.match(/^&gt;\s*(.*?)$/);
      if (quoteMatch) {
        closeListIfNeeded();
        processedLines.push(`<blockquote>${quoteMatch[1]}</blockquote>`);
        continue;
      }
      
      // Match Unordered Lists: - item or * item
      const ulMatch = line.match(/^\s*[-*]\s+(.*?)$/);
      if (ulMatch) {
        if (!inList || listType !== 'ul') {
          closeListIfNeeded();
          processedLines.push('<ul>');
          inList = true;
          listType = 'ul';
        }
        processedLines.push(`<li>${ulMatch[1]}</li>`);
        continue;
      }
      
      // Match Ordered Lists: 1. item
      const olMatch = line.match(/^\s*(\d+)\.\s+(.*?)$/);
      if (olMatch) {
        if (!inList || listType !== 'ol') {
          closeListIfNeeded();
          processedLines.push('<ol>');
          inList = true;
          listType = 'ol';
        }
        processedLines.push(`<li>${olMatch[2]}</li>`);
        continue;
      }
      
      // Empty line
      if (line.trim() === '') {
        closeListIfNeeded();
        processedLines.push(''); // keeps empty line for paragraph splitting
        continue;
      }
      
      // Regular text line (inside list or paragraph)
      processedLines.push(line);
    }
    
    // Close any open lists or tables at end of string
    closeListIfNeeded();
    if (inTable) {
      processedLines.push('</tbody></table>');
      inTable = false;
    }

    // Join processed lines back
    let processedText = processedLines.join('\n');

    // 4. Parse inline elements (Bold, Inline Code)
    // Bold: **text**
    processedText = processedText.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    // Inline code: `code`
    processedText = processedText.replace(/`(.*?)`/g, '<code class="markdown-inline-code">$1</code>');

    // 5. Build Paragraphs: split by double newlines, wrap non-HTML blocks in <p>
    const blocks = processedText.split(/\n\n+/);
    const finalBlocks = blocks.map(block => {
      const trimmed = block.trim();
      if (!trimmed) return '';
      
      // If it starts with an HTML block tag, do not wrap in <p>
      if (trimmed.startsWith('<h') || 
          trimmed.startsWith('<ul') || 
          trimmed.startsWith('<ol') || 
          trimmed.startsWith('<blockquote') || 
          trimmed.startsWith('<table') || 
          trimmed.startsWith('<div class="markdown-alert') ||
          trimmed.startsWith('__CODE_BLOCK_PLACEHOLDER_')) {
        return trimmed;
      }
      
      // Wrap plain text blocks in <p>
      // Replace single newlines within the paragraph with a space for standard Markdown flow
      const cleanParagraph = trimmed.replace(/\n/g, ' ');
      return `<p>${cleanParagraph}</p>`;
    });
    
    let finalHtml = finalBlocks.filter(b => b).join('\n');

    // 6. Restore Code Blocks
    codeBlocks.forEach((codeBlock, idx) => {
      finalHtml = finalHtml.replace(`__CODE_BLOCK_PLACEHOLDER_${idx}__`, codeBlock);
    });

    return finalHtml;
  }

  getAlertIcon(type) {
    switch (type) {
      case 'note': return 'info';
      case 'tip': return 'lightbulb';
      case 'important': return 'priority_high';
      case 'warning': return 'warning';
      case 'caution': return 'report';
      default: return 'info';
    }
  }

  // --- HELPERS ---
  resetMantisWorkspace() {
    if (!this.mantisTreeContainer) return;
    this.mantisTreeContainer.innerHTML = '<div style="text-align:center; padding-top:40px; color:var(--text-muted)">Select a debug scenario from the toolbar.</div>';
    this.mqttTopicLabel.textContent = 'Select a state node to inspect MQTT payload';
    this.jsonViewer.render({ message: "No node selected." });
    this.btnCopyPayload.disabled = true;
    this.currentSelectedNodePayload = null;
    this.activeTraceNodes = [];
    
    // Reset diagnostics tab pages if not running
    if (!this.isTriageRunning) {
      // Reset panel sliding to default
      if (this.diagnosticsLayout) {
        this.diagnosticsLayout.classList.remove('running', 'complete');
      }
      this.rcaReportBody.innerHTML = `
        <div class="rca-placeholder-message">
          Select a device and scenario, then click "Run AI Triage" to generate diagnostics.
        </div>
      `;
      this.btnCopyReport.disabled = true;
      this.btnCopyReport.removeAttribute('data-raw-markdown');
      this.triageLogViewer.clear();
      this.triageStatusBadge.textContent = 'Idle';
      this.triageStatusBadge.className = 'badge badge-idle';
    }

    if (this.testStatusBadge) this.testStatusBadge.style.display = 'none';
    if (this.groupTestVerdict) this.groupTestVerdict.style.display = 'none';
  }

  updateScenarioVerdict(scenarioId) {
    if (!scenarioId || !this.deviceResults || !this.deviceResults[scenarioId]) {
      if (this.testStatusBadge) this.testStatusBadge.style.display = 'none';
      if (this.testTargetBadge) this.testTargetBadge.style.display = 'none';
      if (this.testTimeBadge) this.testTimeBadge.style.display = 'none';
      this.btnRunTriage.disabled = !scenarioId;
      this.btnRunTriage.title = '';
      return;
    }

    const info = this.deviceResults[scenarioId];
    const status = info.status || 'idle';
    const ts = info.timestamp || '';
    const targetProject = info.project_spec || info.target_project || '';

    if (this.testStatusBadge) this.testStatusBadge.style.display = 'inline-flex';
    if (this.testTargetBadge) {
      if (targetProject) {
        this.testTargetBadge.style.display = 'inline-flex';
        this.testTargetBadge.innerHTML = `<span class="material-symbols-outlined" style="font-size:14px;">lan</span><span>${targetProject}</span>`;
      } else {
        this.testTargetBadge.style.display = 'none';
      }
    }
    if (this.testTimeBadge) {
      if (ts) {
        this.testTimeBadge.style.display = 'inline-flex';
        this.testTimeBadge.innerHTML = `<span class="material-symbols-outlined" style="font-size:14px;">schedule</span><span>${ts}</span>`;
      } else {
        this.testTimeBadge.style.display = 'none';
      }
    }

    if (status === 'pass') {
      if (this.testStatusBadge) {
        this.testStatusBadge.className = 'badge scenario-status-badge badge-success';
        this.testStatusBadge.innerHTML = '<span class="material-symbols-outlined" style="font-size:14px;">check_circle</span><span>Passed</span>';
      }
      this.btnRunTriage.disabled = true;
      this.btnRunTriage.title = 'AI Triage is disabled because compliance test case passed successfully.';
    } else if (status === 'skip') {
      if (this.testStatusBadge) {
        this.testStatusBadge.className = 'badge scenario-status-badge badge-warning';
        this.testStatusBadge.innerHTML = '<span class="material-symbols-outlined" style="font-size:14px;">remove_circle</span><span>Skipped</span>';
      }
      this.btnRunTriage.disabled = true;
      this.btnRunTriage.title = 'AI Triage is disabled for skipped test cases.';
    } else if (status === 'fail') {
      if (this.testStatusBadge) {
        this.testStatusBadge.className = 'badge scenario-status-badge badge-error';
        this.testStatusBadge.innerHTML = '<span class="material-symbols-outlined" style="font-size:14px;">cancel</span><span>Failed</span>';
      }
      this.btnRunTriage.disabled = false;
      this.btnRunTriage.title = 'Run AI Triage';
    } else {
      if (this.testStatusBadge) {
        this.testStatusBadge.className = 'badge scenario-status-badge badge-idle';
        this.testStatusBadge.innerHTML = '<span class="material-symbols-outlined" style="font-size:14px;">help_outline</span><span>Idle</span>';
      }
      this.btnRunTriage.disabled = false;
      this.btnRunTriage.title = 'Run AI Triage';
    }
  }

  getMantisIconName(subType) {
    switch (subType) {
      case 'config': return 'settings';
      case 'state': return 'swap_horiz';
      case 'events': return 'notification_important';
      default: return 'help';
    }
  }

  formatScenarioTitle(folder) {
    return folder
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  copyPayloadToClipboard() {
    if (!this.currentSelectedNodePayload) return;
    navigator.clipboard.writeText(this.currentSelectedNodePayload)
      .then(() => {
        const oldIcon = this.btnCopyPayload.innerHTML;
        this.btnCopyPayload.innerHTML = '<span class="material-symbols-outlined" style="color:var(--color-tertiary)">check</span>';
        setTimeout(() => {
          this.btnCopyPayload.innerHTML = oldIcon;
        }, 1500);
      });
  }

  copyReportToClipboard() {
    const markdown = this.btnCopyReport.getAttribute('data-raw-markdown');
    if (!markdown) return;
    navigator.clipboard.writeText(markdown)
      .then(() => {
        const oldIcon = this.btnCopyReport.innerHTML;
        this.btnCopyReport.innerHTML = '<span class="material-symbols-outlined" style="color:var(--color-tertiary)">check</span>';
        setTimeout(() => {
          this.btnCopyReport.innerHTML = oldIcon;
        }, 1500);
      });
  }
}

// Initialize on load
window.addEventListener('DOMContentLoaded', () => {
  new MantisController();
});
