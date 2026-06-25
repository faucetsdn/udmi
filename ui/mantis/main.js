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
    this.triageLogOffset = 0;
    this.triagePollInterval = null;

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
    
    // Layout Container (for sliding panels animation)
    this.diagnosticsLayout = document.querySelector('.diagnostics-layout');
  }

  initComponents() {
    // Initialize JSONViewer (Trace tab)
    this.jsonViewer = new JSONViewer(document.getElementById('mqtt-json-viewer'));
    this.jsonViewer.render({ message: "Waiting for Site Model selection from parent shell..." });

    // Initialize LogViewer (Diagnostics tab)
    this.triageLogViewer = new LogViewer(this.triageTerminalContainer);
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
  }

  // --- TAB SWITCHING MACHINERY ---
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
        this.btnRunTriage.disabled = false; // Enable AI triage trigger
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

  // --- CROSS-IFRAME AUTO-TRIAGE REDIRECTION (PostMessage) ---
  async handleLoadDiagnose(data) {
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
    
    // Switch to diagnostics tab and auto-boot triage!
    this.switchLocalTab('diagnostics');
    this.startAITriage();
  }

  // --- MANTIS AI TRIAGE EXECUTION LOOP ---
  async startAITriage() {
    if (this.isTriageRunning) return;

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
    this.triageLogViewer.append(`🤖 Starting Mantis AI Triage Agent...\nDevice: ${deviceId}\nTest ID: ${testId}\nPlaybook: ${this.playbookSelect.value.toUpperCase()}\n------------------------------------------------\n`, 'info');
    
    this.rcaReportBody.innerHTML = `
      <div class="rca-placeholder-message">
        <div class="loader" style="margin-bottom: 24px;"></div>
        <span style="font-weight: 500; color: var(--text-secondary); max-width: 480px;">Mantis AI is digesting compliance logs, scanning codebase references, and compiling the Root Cause Analysis...</span>
      </div>
    `;
    this.btnCopyReport.disabled = true;

    // Trigger API
    const playbook = this.playbookSelect.value;
    const projectSpec = this.projectSpec || '//mqtt/localhost';
    const runUrl = `/api/run_triage?device_id=${encodeURIComponent(deviceId)}&test_id=${encodeURIComponent(testId)}&playbook=${playbook}&site_model=${encodeURIComponent(this.siteModel)}&project_spec=${encodeURIComponent(projectSpec)}`;
    
    try {
      const res = await fetch(runUrl);
      const startData = await res.json();
      if (!res.ok) {
        throw new Error(startData.error || `HTTP ${res.status}`);
      }

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
      if (data.running === false) {
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
    }
  }

  async loadTriageReport() {
    const projectSpec = this.projectSpec || '//mqtt/localhost';
    const url = `/api/triage_report?site_model=${encodeURIComponent(this.siteModel)}&project_spec=${encodeURIComponent(projectSpec)}&device_id=${encodeURIComponent(this.device)}&test_id=${encodeURIComponent(this.scenarioSelect.value)}`;
    
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
    
    // 1. Escape HTML tags to protect the dashboard
    let escaped = md.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    
    // 2. Pre-process block elements: Fenced code blocks
    // Temporarily replace fenced code blocks with placeholders to protect them from inline parsing
    const codeBlocks = [];
    escaped = escaped.replace(/```([\s\S]*?)```/g, (match, code) => {
      const placeholder = `__CODE_BLOCK_PLACEHOLDER_${codeBlocks.length}__`;
      codeBlocks.push(`<pre class="markdown-code-block"><code>${code.trim()}</code></pre>`);
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
