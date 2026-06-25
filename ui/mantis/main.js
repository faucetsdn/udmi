import { JSONViewer } from '../shared/components/json-viewer.js';

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

// --- MANTIS DECOUPLED CONTROLLER ---
class MantisController {
  constructor() {
    // Parent state (Synced via postMessage)
    this.siteModel = '';
    this.device = '';

    // Local state
    this.activeTraceNodes = [];
    this.currentSelectedNodePayload = null;

    this.initElements();
    this.initComponents();
    this.initEvents();
  }

  initElements() {
    // Local Controls
    this.deviceSelect = document.getElementById('device-select');
    this.scenarioSelect = document.getElementById('scenario-select');
    this.btnCopyPayload = document.getElementById('btn-copy-payload');
    
    // Layout Elements
    this.mantisTreeContainer = document.querySelector('.mantis-tree-container');
    this.mqttTopicLabel = document.getElementById('mqtt-topic');
  }

  initComponents() {
    // Initialize JSONViewer
    this.jsonViewer = new JSONViewer(document.getElementById('mqtt-json-viewer'));
    this.jsonViewer.render({ message: "Waiting for Site Model selection from parent shell..." });
  }

  initEvents() {
    // --- 1. POSTMESSAGE LISTENER (State Sync from Shell) ---
    window.addEventListener('message', (event) => {
      if (event.data && event.data.type === 'udmi_state_change') {
        this.handleGlobalStateChange(event.data.siteModel);
      }
    });

    // Local Device Select Trigger
    this.deviceSelect.addEventListener('change', (e) => {
      this.handleDeviceChange(e.target.value);
    });

    // Local dropdown trigger
    this.scenarioSelect.addEventListener('change', (e) => {
      this.handleScenarioChange(e.target.value);
    });

    // Copy to clipboard trigger
    this.btnCopyPayload.addEventListener('click', () => this.copyPayloadToClipboard());
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

    const testsPath = combinePaths(this.siteModel, `out/devices/${device}/tests`);
    try {
      const data = await fetchDirectoryList(testsPath);
      const scenarios = data.folders;
      this.scenarioSelect.innerHTML = '<option value="">-- Select Scenario --</option>';

      if (scenarios.length > 0) {
        scenarios.forEach(sc => {
          const opt = document.createElement('option');
          opt.value = sc;
          opt.textContent = this.formatScenarioTitle(sc);
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
    if (!scenarioId) return;

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

  // --- HELPERS ---
  resetMantisWorkspace() {
    if (!this.mantisTreeContainer) return;
    this.mantisTreeContainer.innerHTML = '<div style="text-align:center; padding-top:40px; color:var(--text-muted)">Select a debug scenario from the toolbar.</div>';
    this.mqttTopicLabel.textContent = 'Select a state node to inspect MQTT payload';
    this.jsonViewer.render({ message: "No node selected." });
    this.btnCopyPayload.disabled = true;
    this.currentSelectedNodePayload = null;
    this.activeTraceNodes = [];
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
}

// Initialize on load
window.addEventListener('DOMContentLoaded', () => {
  new MantisController();
});
