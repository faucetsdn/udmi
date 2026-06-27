import { LogViewer } from '../shared/components/log-viewer.js';

// --- HELPERS: DYNAMIC ENDPOINT FETCH ---
async function fetchDirectoryList(targetPath) {
  const url = `/api/list?path=${encodeURIComponent(targetPath)}`;
  const response = await fetch(url);
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || `Server error: ${response.status}`);
  }
  return data;
}

function combinePaths(base, sub) {
  if (!base || base === '.') return sub;
  if (base.endsWith('/')) return base + sub;
  return base + '/' + sub;
}

class SequencerController {
  constructor() {
    // Parent State (Synced via postMessage)
    this.siteModel = '';
    
    // Local State
    this.device = '';
    this.testCases = [];
    this.isRunning = false;
    this.runTimer = null;
    this.secondsElapsed = 0;
    this.logOffset = 0;
    this.pollInterval = null;

    this.initElements();
    this.initComponents();
    this.initEvents();

    // Load local cached items and specs
    this.loadVersion();
    this.loadCachedProject();
    this.loadTestSequences();
  }

  initElements() {
    // Buttons
    this.btnRun = document.getElementById('btn-run');
    this.btnStop = document.getElementById('btn-stop');
    this.btnSelectAll = document.getElementById('btn-select-all');
    this.btnDeselectAll = document.getElementById('btn-deselect-all');
    this.btnSettings = document.getElementById('btn-settings');
    
    // Inputs
    this.deviceSelect = document.getElementById('device-select');
    this.projectInput = document.getElementById('project-input');
    this.modelInput = document.getElementById('model-input');
    this.testSearch = document.getElementById('test-search');
    
    // Advanced Settings Popover Controls
    this.settingsPopover = document.getElementById('advanced-settings-popover');
    this.selectLogLevel = document.getElementById('select-log-level');
    this.selectTestStage = document.getElementById('select-test-stage');
    this.inputSerialNo = document.getElementById('input-serial-no');

    // Layout Containers
    this.testCasesList = document.querySelector('.test-cases-list');
    
    // Metrics Card
    this.suiteStatusBadge = document.getElementById('suite-status');
    this.progressBar = document.querySelector('.progress-bar-fill');
    this.metricPassed = document.getElementById('metric-passed');
    this.metricSkipped = document.getElementById('metric-skipped');
    this.metricFailed = document.getElementById('metric-failed');
    this.metricTime = document.getElementById('metric-time');

    // Modal Elements
    this.fileViewerModal = document.getElementById('file-viewer-modal');
    this.modalTestName = document.getElementById('modal-test-name');
    this.modalTabMd = document.getElementById('modal-tab-md');
    this.modalTabLog = document.getElementById('modal-tab-log');
    this.modalCloseBtn = document.getElementById('modal-close-btn');
    this.modalFileContent = document.getElementById('modal-file-content');

    this.activeModalTestCase = null;
    this.activeModalFileType = 'sequence.md';
  }

  initComponents() {
    // Initialize LogViewer
    this.logViewer = new LogViewer(document.getElementById('sequencer-logs'));
    this.logViewer.append('Waiting for Site Model selection from parent shell...', 'debug');
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
      this.device = e.target.value;
      if (this.device) {
        localStorage.setItem('udmi_last_selected_device', this.device);
        this.loadDevicePreviousResults();
      } else {
        localStorage.removeItem('udmi_last_selected_device');
        this.clearPreviousResults();
      }
      this.validateInputs();
    });

    // Run / Stop Triggers
    this.btnRun.addEventListener('click', () => this.startTestSuite());
    this.btnStop.addEventListener('click', () => this.stopTestSuite());

    // Settings Gear Trigger
    this.btnSettings.addEventListener('click', (e) => {
      e.stopPropagation();
      this.settingsPopover.classList.toggle('active');
    });

    document.addEventListener('click', (e) => {
      if (this.settingsPopover && this.btnSettings && 
          !this.settingsPopover.contains(e.target) && 
          !this.btnSettings.contains(e.target)) {
        this.settingsPopover.classList.remove('active');
      }
    });

    // Local inputs caching
    this.projectInput.addEventListener('input', (e) => {
      const val = e.target.value.trim();
      if (val) localStorage.setItem('udmi_target_project', val);
      this.validateInputs();
    });

    // Checklist operations
    this.testSearch.addEventListener('input', (e) => this.filterTestCases(e.target.value));
    this.btnSelectAll.addEventListener('click', () => this.toggleAllTestCases(true));
    this.btnDeselectAll.addEventListener('click', () => this.toggleAllTestCases(false));

    // Modal events
    this.modalCloseBtn.addEventListener('click', () => {
      this.fileViewerModal.style.display = 'none';
    });
    this.fileViewerModal.addEventListener('click', (e) => {
      if (e.target === this.fileViewerModal) {
        this.fileViewerModal.style.display = 'none';
      }
    });
    this.modalTabMd.addEventListener('click', () => {
      this.activeModalFileType = 'sequence.md';
      this.modalTabMd.classList.add('active');
      this.modalTabLog.classList.remove('active');
      this.loadModalFileContent();
    });
    this.modalTabLog.addEventListener('click', () => {
      this.activeModalFileType = 'sequence.log';
      this.modalTabLog.classList.add('active');
      this.modalTabMd.classList.remove('active');
      this.loadModalFileContent();
    });
  }

  // --- STATE SYNC & INPUT VALIDATION ---
  async handleGlobalStateChange(siteModel) {
    this.siteModel = siteModel;
    
    this.deviceSelect.innerHTML = '<option value="">-- Select DUT --</option>';
    this.deviceSelect.disabled = true;
    this.device = '';
    this.clearPreviousResults();

    if (!siteModel) {
      this.deviceSelect.innerHTML = '<option value="">-- Enter site path --</option>';
      this.logViewer.append(`\nGlobal state cleared. Waiting for Site Model...`, 'debug');
      this.validateInputs();
      return;
    }

    this.logViewer.append(`\nGlobal Site Model synced: "${siteModel}". Scanning for devices...`, 'info');
    this.deviceSelect.innerHTML = '<option value="">Scanning devices...</option>';

    const devicesPath = combinePaths(siteModel, 'devices');
    try {
      const data = await fetchDirectoryList(devicesPath);
      const devices = data.folders;
      this.deviceSelect.innerHTML = '<option value="">-- Select DUT --</option>';

      if (devices.length > 0) {
        devices.forEach(dev => {
          const opt = document.createElement('option');
          opt.value = dev;
          opt.textContent = dev;
          this.deviceSelect.appendChild(opt);
        });
        
        this.deviceSelect.disabled = false;
        
        // Restore last selected device if it matches one of the scanned folders
        const cachedDev = localStorage.getItem('udmi_last_selected_device');
        if (cachedDev && devices.includes(cachedDev)) {
          this.deviceSelect.value = cachedDev;
          this.device = cachedDev;
          this.logViewer.append(`Restored last active DUT: "${this.device}"`, 'success');
          this.loadDevicePreviousResults();
        }
      } else {
        this.deviceSelect.innerHTML = '<option value="">-- No devices found under path --</option>';
      }
    } catch (err) {
      this.deviceSelect.innerHTML = '<option value="">-- Invalid path or no devices --</option>';
      console.error('Error scanning devices inside sequencer:', err);
    }

    this.validateInputs();
  }

  validateInputs() {
    const hasSite = this.siteModel !== '';
    const hasDevice = this.device !== '';
    const hasProject = this.projectInput.value.trim() !== '';
    const hasChecked = this.testCases.some(tc => tc.checked !== false);

    this.btnRun.disabled = !(hasSite && hasDevice && hasProject && hasChecked && !this.isRunning);
  }

  // --- LOCAL CACHE LOADERS ---
  async loadVersion() {
    try {
      const res = await fetch('../version.txt');
      if (res.ok) {
        const version = (await res.text()).trim();
        this.modelInput.value = version;
      } else {
        this.modelInput.value = 'HEAD';
      }
    } catch (err) {
      console.error('Failed to load version.txt, falling back to HEAD:', err);
      this.modelInput.value = 'HEAD';
    }
  }

  loadCachedProject() {
    const cached = localStorage.getItem('udmi_target_project');
    this.projectInput.value = cached || '//mqtt/localhost';
  }

  async loadTestSequences() {
    try {
      const res = await fetch('/docs/specs/sequences/generated.md');
      if (!res.ok) throw new Error(`Failed to load generated.md: ${res.statusText}`);
      const text = await res.text();
      
      const lines = text.split('\n');
      const parsedCases = [];
      const headingRegex = /^##\s+(\w+)\s+\(([^)]+)\)/i;
      
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        const match = line.match(headingRegex);
        
        if (match) {
          const id = match[1].trim();
          
          let desc = '';
          for (let j = i + 1; j < lines.length; j++) {
            const nextLine = lines[j].trim();
            if (nextLine.startsWith('##')) break;
            if (nextLine !== '') {
              const isListItem = /^(?:\d+\.|\*|\-|\+)\s/.test(nextLine);
              const isTestVerdict = /^test\s+(?:passed|failed|skipped)\b/i.test(nextLine);
              if (isListItem || isTestVerdict) break;
              
              desc = nextLine;
              break;
            }
          }
          
          const title = id
            .split('_')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
            
          parsedCases.push({
            id: id,
            title: title,
            desc: desc,
            status: 'idle',
            checked: true
          });
        }
      }
      
      if (parsedCases.length > 0) {
        this.testCases = parsedCases;
        this.renderTestCases();
        if (this.device) {
          this.loadDevicePreviousResults();
        }
      } else {
        throw new Error('No test cases parsed');
      }
    } catch (err) {
      this.logViewer.append(`Error loading test cases: ${err.message}`, 'error');
      console.error(err);
    }
  }

  clearPreviousResults() {
    this.testCases.forEach(tc => {
      tc.status = 'idle';
      tc.lastTimestamp = null;
    });
    this.renderTestCases();

    if (!this.isRunning) {
      this.metricPassed.textContent = 0;
      this.metricFailed.textContent = 0;
      this.metricSkipped.textContent = 0;
      this.suiteStatusBadge.textContent = 'Idle';
      this.suiteStatusBadge.className = 'badge badge-neutral';
    }
  }

  async loadDevicePreviousResults(preserveBadge = false, updateMetrics = true) {
    if (!this.siteModel || !this.device) {
      this.clearPreviousResults();
      return;
    }

    try {
      const url = `/api/device_results?site_model=${encodeURIComponent(this.siteModel)}&device=${encodeURIComponent(this.device)}`;
      const res = await fetch(url);
      if (!res.ok) {
        if (!preserveBadge) this.clearPreviousResults();
        return;
      }
      const data = await res.json();

      if (data.results && Object.keys(data.results).length > 0) {
        let passCount = 0;
        let failCount = 0;
        let skipCount = 0;

        this.testCases.forEach(tc => {
          if (data.results[tc.id]) {
            const resObj = data.results[tc.id];
            tc.status = resObj.status;
            tc.lastTimestamp = resObj.timestamp;
            tc.projectSpec = resObj.project_spec || resObj.target_project;

            if (resObj.status === 'pass') passCount++;
            else if (resObj.status === 'fail' || resObj.status === 'error') failCount++;
            else if (resObj.status === 'skip') skipCount++;
          } else {
            tc.status = 'idle';
            tc.lastTimestamp = null;
            tc.projectSpec = null;
          }
        });

        this.renderTestCases();

        if (!this.isRunning && updateMetrics) {
          this.metricPassed.textContent = passCount;
          this.metricFailed.textContent = failCount;
          this.metricSkipped.textContent = skipCount;
          if (!preserveBadge) {
            this.suiteStatusBadge.textContent = 'Historical Runs';
            this.suiteStatusBadge.className = 'badge badge-info';
          }
        }
      } else if (!preserveBadge) {
        this.clearPreviousResults();
      }
    } catch (err) {
      console.error('Error fetching device previous results:', err);
      if (!preserveBadge) this.clearPreviousResults();
    }
  }

  async openFileViewerModal(tc) {
    this.activeModalTestCase = tc;
    this.activeModalFileType = 'sequence.md';

    this.modalTestName.textContent = `${tc.title} (${tc.id})`;
    this.modalTabMd.classList.add('active');
    this.modalTabLog.classList.remove('active');
    this.fileViewerModal.style.display = 'flex';

    this.loadModalFileContent();
  }

  async loadModalFileContent() {
    if (!this.activeModalTestCase || !this.siteModel || !this.device) return;

    const filePath = combinePaths(this.siteModel, `out/devices/${this.device}/tests/${this.activeModalTestCase.id}/${this.activeModalFileType}`);
    this.modalFileContent.textContent = `Loading ${this.activeModalFileType}...`;

    try {
      const res = await fetch(`/api/read_file?path=${encodeURIComponent(filePath)}`);
      if (!res.ok) {
        this.modalFileContent.textContent = `File not found or empty: ${this.activeModalFileType}`;
        return;
      }
      const text = await res.text();
      this.modalFileContent.textContent = text || `(File is empty: ${this.activeModalFileType})`;
    } catch (err) {
      this.modalFileContent.textContent = `Error loading file: ${err.message}`;
    }
  }

  // --- CHECKLIST VIEW MANAGEMENT ---
  renderTestCases() {
    this.btnSelectAll.disabled = this.isRunning;
    this.btnDeselectAll.disabled = this.isRunning;

    this.testCasesList.innerHTML = '';
    this.testCases.forEach(tc => {
      const itemEl = document.createElement('div');
      itemEl.className = `test-case-item ${tc.id === this.selectedTestCaseId ? 'active' : ''}`;
      
      const disabledAttr = this.isRunning ? 'disabled' : '';
      const isClickable = tc.status !== 'idle' && tc.status !== 'queued' && tc.status !== 'running';
      const clickableClass = isClickable ? 'clickable' : '';
      const titleAttr = isClickable ? 'View test sequence logs & markdown' : '';
      
      itemEl.innerHTML = `
        <div class="test-case-meta">
          <input type="checkbox" class="test-case-checkbox" ${tc.checked !== false ? 'checked' : ''} ${disabledAttr} aria-label="Select ${tc.title}" />
          <div class="test-case-info">
            <div class="test-case-title">${tc.title}</div>
            <div class="test-case-desc">${tc.desc}</div>
            ${(tc.projectSpec || tc.lastTimestamp) ? `
              <div class="test-case-details" style="display: flex; flex-direction: column; gap: 2px; margin-top: 4px; font-size: 11px;">
                ${tc.projectSpec ? `
                  <div style="display: flex; align-items: center; gap: 4px; color: var(--color-primary, #89b4fa);">
                    <span class="material-symbols-outlined" style="font-size: 13px; opacity: 0.85;">lan</span>
                    <span style="font-family: var(--font-mono, monospace); font-weight: 500;">${tc.projectSpec}</span>
                  </div>
                ` : ''}
                ${tc.lastTimestamp ? `
                  <div style="display: flex; align-items: center; gap: 4px; color: var(--text-muted);">
                    <span class="material-symbols-outlined" style="font-size: 13px; opacity: 0.7;">schedule</span>
                    <span style="font-family: var(--font-mono, monospace);">${tc.lastTimestamp}</span>
                  </div>
                ` : ''}
              </div>
            ` : ''}
          </div>
        </div>
        <div class="test-case-status" style="display: flex; align-items: center;">
          ${tc.status === 'fail' ? `
            <button class="btn-diagnose" title="Diagnose failure with Mantis AI" aria-label="Diagnose with Mantis">
              <span class="material-symbols-outlined">psychology</span>
            </button>
          ` : ''}
          <span class="material-symbols-outlined status-icon ${tc.status} ${clickableClass}" title="${titleAttr}">
            ${this.getSequencerStatusIconName(tc.status)}
          </span>
        </div>
      `;

      const checkbox = itemEl.querySelector('.test-case-checkbox');
      checkbox.addEventListener('change', (e) => {
        tc.checked = e.target.checked;
        this.validateInputs();
      });

      const diagnoseBtn = itemEl.querySelector('.btn-diagnose');
      if (diagnoseBtn) {
        diagnoseBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          window.parent.postMessage({
            type: 'trigger_diagnose',
            testId: tc.id,
            deviceId: this.deviceSelect.value,
            siteModel: this.siteModel,
            projectSpec: this.projectInput.value
          }, '*');
        });
      }

      const statusIcon = itemEl.querySelector('.status-icon');
      if (statusIcon && isClickable) {
        statusIcon.addEventListener('click', (e) => {
          e.stopPropagation();
          this.openFileViewerModal(tc);
        });
      }

      this.testCasesList.appendChild(itemEl);
    });
  }

  toggleAllTestCases(checked) {
    if (this.isRunning) return;
    this.testCases.forEach(tc => tc.checked = checked);
    this.renderTestCases();
    this.validateInputs();
  }

  getSequencerStatusIconName(status) {
    switch (status) {
      case 'idle': return 'remove';
      case 'queued': return 'pending';
      case 'running': return 'progress_activity';
      case 'pass': return 'check_circle';
      case 'skip': return 'remove_circle';
      case 'fail': return 'cancel';
      default: return 'remove';
    }
  }

  filterTestCases(query) {
    const q = query.toLowerCase();
    const items = this.testCasesList.querySelectorAll('.test-case-item');
    this.testCases.forEach((tc, idx) => {
      const match = tc.title.toLowerCase().includes(q) || tc.desc.toLowerCase().includes(q);
      items[idx].style.display = match ? 'flex' : 'none';
    });
  }

  // --- PROCESS RUNNER MACHINERY ---
  async startTestSuite() {
    if (this.isRunning) return;

    const activeTests = this.testCases.filter(tc => tc.checked !== false);
    if (activeTests.length === 0) {
      this.logViewer.append('ERROR: No test cases checked.', 'error');
      return;
    }

    this.isRunning = true;
    this.logOffset = 0;

    this.secondsElapsed = 0;
    this.metricTime.textContent = '00:00';
    this.progressBar.style.width = '0%';
    this.suiteStatusBadge.textContent = 'Running';
    this.suiteStatusBadge.className = 'badge badge-info';
    this.logViewer.clear();
    
    // Disable local controls
    this.btnRun.disabled = true;
    this.btnStop.disabled = false;
    this.projectInput.disabled = true;
    this.btnSettings.disabled = true;
    this.settingsPopover.classList.remove('active');

    this.testCases.forEach(tc => {
      if (tc.checked !== false) {
        tc.status = 'queued';
      }
    });
    this.renderTestCases();

    this.runTimer = setInterval(() => {
      this.secondsElapsed++;
      const mins = Math.floor(this.secondsElapsed / 60).toString().padStart(2, '0');
      const secs = (this.secondsElapsed % 60).toString().padStart(2, '0');
      this.metricTime.textContent = `${mins}:${secs}`;
    }, 1000);

    this.metricPassed.textContent = '0';
    this.metricSkipped.textContent = '0';
    this.metricFailed.textContent = '0';

    const queryParams = new URLSearchParams({
      site_model: this.siteModel,
      project_spec: this.projectInput.value.trim(),
      device_id: this.device,
      tests: activeTests.map(tc => tc.id).join(','),
      log_level: this.selectLogLevel.value,
      min_stage: this.selectTestStage.value,
      serial_no: this.inputSerialNo.value.trim()
    });

    try {
      this.logViewer.append(`Spawning system process: bin/sequencer ${this.getFormattedArgsString(activeTests.length)}`, 'info');
      
      const res = await fetch(`/api/run_sequencer?${queryParams.toString()}`);
      const data = await res.json();
      
      if (!res.ok) throw new Error(data.error || 'Failed to start sequencer process');

      this.logViewer.append(`Process spawned successfully (PID: ${data.pid}). Connecting to log stream...`, 'success');
      this.pollInterval = setInterval(() => this.pollSequencerStatus(activeTests.length), 500);
    } catch (err) {
      this.logViewer.append(`ERROR starting sequencer: ${err.message}`, 'error');
      this.finishTestSuite(0, activeTests.length, 0, false);
    }
  }

  getFormattedArgsString(testCount) {
    let args = "";
    if (this.selectLogLevel.value === "DEBUG") args += "-v ";
    if (this.selectLogLevel.value === "TRACE") args += "-vv ";
    if (this.selectTestStage.value === "ALPHA") args += "-a ";
    if (this.selectTestStage.value === "ALPHA_ONLY") args += "-x ";
    if (this.inputSerialNo.value.trim()) args += `-s ${this.inputSerialNo.value.trim()} `;
    return `${args}[SITE_MODEL] [PROJECT] [DEVICE] (${testCount} tests selected)`;
  }

  async pollSequencerStatus(totalTests) {
    try {
      const res = await fetch(`/api/sequencer_status?offset=${this.logOffset}`);
      const data = await res.json();
      
      if (!res.ok) throw new Error(data.error || 'Log polling error');

      if (data.log && data.log.trim() !== '') {
        const lines = data.log.split('\n');
        lines.forEach(line => {
          if (line.trim()) {
            this.parseLogLineForStatus(line);
            this.logViewer.append(line, this.getLogLineClassType(line));
          }
        });
      }
      
      this.logOffset = data.offset;

      if (!data.running) {
        if (this.pollInterval) {
          clearInterval(this.pollInterval);
          this.pollInterval = null;
        } else {
          return;
        }
        const code = data.exit_code;
        this.logViewer.append(`\nProcess exited with status code: ${code}`, code === 0 ? 'success' : 'warn');
        
        let passed = 0;
        let failed = 0;
        let skipped = 0;
        this.testCases.forEach(tc => {
          if (tc.checked !== false) {
            if (tc.status === 'pass') passed++;
            else if (tc.status === 'skip') skipped++;
            else {
              if (tc.status === 'idle' || tc.status === 'queued' || tc.status === 'running') tc.status = 'fail';
              failed++;
            }
          }
        });
        
        this.renderTestCases();
        this.updateMetricsFromCheckedStates();
        this.finishTestSuite(passed, failed, skipped, code === 0);
      }
    } catch (err) {
      console.error('Error polling sequencer status:', err);
      this.logViewer.append(`[Log connection lost: ${err.message}]`, 'error');
    }
  }

  parseLogLineForStatus(line) {
    const startRegex = /Start(?:ing)?\s+test\s+(\w+)/i;
    let match = line.match(startRegex);
    if (match) {
      const testId = match[1];
      const tc = this.testCases.find(t => t.id === testId);
      if (tc) {
        tc.status = 'running';
        this.renderTestCases();
      }
      return;
    }

    const resultRegex = /RESULT\s+(\w+)\s+(\S+)\s+(\S+)/i;
    match = line.match(resultRegex);
    if (match) {
      const resultStatus = match[1].toLowerCase();
      const testId = match[3];
      const tc = this.testCases.find(t => t.id === testId);
      if (tc) {
        if (resultStatus === 'pass') tc.status = 'pass';
        else if (resultStatus === 'skip') tc.status = 'skip';
        else tc.status = 'fail';
        this.renderTestCases();
        this.updateMetricsFromCheckedStates();
      }
    }
  }

  updateMetricsFromCheckedStates() {
    let passed = 0;
    let failed = 0;
    let skipped = 0;
    this.testCases.forEach(tc => {
      if (tc.checked !== false) {
        if (tc.status === 'pass') passed++;
        else if (tc.status === 'skip') skipped++;
        else if (tc.status === 'fail') failed++;
      }
    });
    this.metricPassed.textContent = passed;
    this.metricSkipped.textContent = skipped;
    this.metricFailed.textContent = failed;

    const activeTests = this.testCases.filter(tc => tc.checked !== false);
    const completedCount = passed + failed + skipped;
    const percent = Math.round((completedCount / activeTests.length) * 100);
    this.progressBar.style.width = `${percent}%`;
  }

  getLogLineClassType(line) {
    const l = line.toLowerCase();
    if (l.includes('[error]') || l.includes('error:') || l.includes('fatal:')) return 'error';
    if (l.includes('[warn]') || l.includes('warning:') || l.includes('fail')) return 'warn';
    if (l.includes('pass') || l.includes('success')) return 'success';
    if (l.includes('[debug]')) return 'debug';
    return 'info';
  }

  async stopTestSuite() {
    if (!this.isRunning) return;
    this.logViewer.append('\nAborting compliance run... Sending termination signal...', 'warn');
    try {
      const res = await fetch('/api/stop_sequencer');
      const data = await res.json();
      this.logViewer.append(`Process stopped signal response: ${data.status}`, 'success');
    } catch (err) {
      this.logViewer.append(`Failed to stop cleanly: ${err.message}`, 'error');
    }
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
    this.finishTestSuiteVisuals();
    this.testCases.forEach(tc => {
      if (tc.status === 'running' || tc.status === 'queued') tc.status = 'idle';
    });
    this.renderTestCases();
    this.updateMetricsFromCheckedStates();
    await this.loadDevicePreviousResults(true, false);
    this.suiteStatusBadge.textContent = 'Aborted';
    this.suiteStatusBadge.className = 'badge badge-warning';
  }

  async finishTestSuite(passed, failed, skipped, cleanExit) {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
    this.finishTestSuiteVisuals();
    if (failed > 0 || !cleanExit) {
      this.suiteStatusBadge.textContent = 'Failed';
      this.suiteStatusBadge.className = 'badge badge-error';
      this.logViewer.append(`\nCOMPLIANCE RUN COMPLETE. Passed: ${passed}, Skipped: ${skipped}, Failed: ${failed}. DUT has compliance issues.`, 'error');
    } else {
      this.suiteStatusBadge.textContent = 'Compliant';
      this.suiteStatusBadge.className = 'badge badge-success';
      this.logViewer.append(`\nCOMPLIANCE RUN COMPLETE. Passed: ${passed}, Skipped: ${skipped}, Failed: ${failed}. DUT is compliant!`, 'success');
    }
    await this.loadDevicePreviousResults(true, false);
  }

  finishTestSuiteVisuals() {
    clearInterval(this.runTimer);
    this.isRunning = false;
    this.btnRun.disabled = false;
    this.btnStop.disabled = true;
    this.projectInput.disabled = false;
    this.btnSettings.disabled = false;
    this.renderTestCases();
  }
}

// Initialize on load
window.addEventListener('DOMContentLoaded', () => {
  new SequencerController();
});
