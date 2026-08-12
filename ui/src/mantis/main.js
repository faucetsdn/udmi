import { stateStore } from '../shared/state-store.js';
import { NotificationManager } from '../shared/components/notification-toast.js';

/**
 * Mantis AI Autonomous Diagnostics Chat Controller
 * Manages streaming multi-turn conversational triage, tool call visualization,
 * context synchronization, and slash-command workflows.
 */
export class MantisController {
  constructor() {
    this.siteModel = '';
    this.device = '';
    this.testId = '';
    this.sessionId = 'mantis-' + Math.random().toString(36).substring(2, 10);
    this.isStreaming = false;
    this.abortController = null;
    this.messages = [];

    // Settings (persisted in localStorage, default to Vertex ADC for Corp)
    this.provider = localStorage.getItem('mantis_provider') || 'vertex';
    this.apiKey = localStorage.getItem('mantis_api_key') || '';
    this.gcpProject = localStorage.getItem('mantis_gcp_project') || 'bos-platform-dev';
    this.gcpLocation = localStorage.getItem('mantis_gcp_location') || 'global';
    this.baselineRun = localStorage.getItem('mantis_baseline_run') || '';
    this.graphvizCache = new Map();

    this.initElements();

    this.initEvents();
    this.initFromStorage();
  }

  initElements() {
    this.statusBadge = document.getElementById('mantis-test-status-badge');

    this.chatStream = document.getElementById('mantis-chat-stream');
    this.emptyState = document.getElementById('mantis-empty-state');
    this.messagesContainer = document.getElementById('mantis-messages-container');

    this.chatInput = document.getElementById('mantis-chat-input');
    this.btnSend = document.getElementById('btn-mantis-send');
    this.btnStop = document.getElementById('btn-mantis-stop');

    this.btnSettings = document.getElementById('btn-mantis-settings');
    this.settingsModal = document.getElementById('mantis-settings-modal');
    this.btnCloseSettings = document.getElementById('btn-close-mantis-settings');
    this.btnSaveSettings = document.getElementById('btn-save-mantis-settings');

    this.btnFactCheck = document.getElementById('btn-mantis-factcheck') || document.getElementById('btn-mantis-critique');
    this.btnClear = document.getElementById('btn-mantis-clear');
    this.btnCopy = document.getElementById('btn-mantis-copy');
    this.btnExport = document.getElementById('btn-mantis-export');
    this.clearingOverlay = document.getElementById('mantis-clearing-overlay');
    this.chatLayout = document.querySelector('.mantis-chat-layout');



    // Settings Form Elements
    this.providerSelect = document.getElementById('setting-provider-select');
    this.inputApiKey = document.getElementById('setting-api-key');
    this.groupApiKey = document.getElementById('setting-group-api-key');
    this.inputGcpProject = document.getElementById('setting-gcp-project');
    this.inputGcpLocation = document.getElementById('setting-gcp-location');
    this.groupVertex = document.getElementById('setting-group-vertex');
    this.inputBaselineRun = document.getElementById('setting-baseline-run');
  }

  setWelcomeLayout(isWelcome) {
    if (this.chatLayout) {
      if (isWelcome) {
        this.chatLayout.classList.add('welcome-layout');
      } else {
        this.chatLayout.classList.remove('welcome-layout');
      }
    }
  }


  initEvents() {
    // Send Message Trigger
    if (this.btnSend) {
      this.btnSend.addEventListener('click', () => this.handleSendMessage());
    }

    if (this.btnStop) {
      this.btnStop.addEventListener('click', () => this.handleStopStreaming());
    }

    if (this.chatInput) {
      this.chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          this.handleSendMessage();
        }
      });

      // Auto-resize input
      this.chatInput.addEventListener('input', () => {
        this.chatInput.style.height = 'auto';
        this.chatInput.style.height = Math.min(this.chatInput.scrollHeight, 160) + 'px';
      });
    }

    // Quick Prompt Chips
    document.querySelectorAll('.mantis-prompt-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        const prompt = chip.getAttribute('data-prompt');
        if (prompt && this.chatInput) {
          this.chatInput.value = prompt;
          this.handleSendMessage();
        }
      });
    });

    // Slash command hint chips
    document.querySelectorAll('.mantis-hint-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        const cmd = chip.getAttribute('data-cmd');
        if (cmd && this.chatInput) {
          this.chatInput.value = cmd + ' ';
          this.chatInput.focus();
        }
      });

      chip.addEventListener('dblclick', (e) => {
        e.preventDefault();
        const cmd = chip.getAttribute('data-cmd');
        if (cmd && (cmd === '/fact-check' || cmd === '/current-context' || cmd === '/context')) {
          if (this.chatInput) {
            this.chatInput.value = cmd;
          }
          this.handleSendMessage();
        }
      });
    });


    // Header Quick Actions
    if (this.btnFactCheck) {
      this.btnFactCheck.addEventListener('click', () => {
        this.chatInput.value = '/fact-check Review the diagnostic conclusions and test isolation assumptions.';
        this.handleSendMessage();
      });
    }


    if (this.btnClear) {
      this.btnClear.addEventListener('click', () => this.handleClearSession());
    }

    if (this.btnCopy) {
      this.btnCopy.addEventListener('click', () => this.handleCopyTranscript());
    }

    if (this.btnExport) {
      this.btnExport.addEventListener('click', () => this.handleExportTranscript());
    }


    // Settings Modal
    if (this.btnSettings && this.settingsModal) {
      this.btnSettings.addEventListener('click', () => this.openSettingsModal());
    }
    if (this.btnCloseSettings && this.settingsModal) {
      this.btnCloseSettings.addEventListener('click', () => this.closeSettingsModal());
    }
    if (this.btnSaveSettings) {
      this.btnSaveSettings.addEventListener('click', () => this.saveSettings());
    }
    if (this.providerSelect) {
      this.providerSelect.addEventListener('change', () => this.toggleProviderFields());
    }

    // Target Selection Sync
    if (this.deviceSelect) {
      this.deviceSelect.addEventListener('change', (e) => {
        this.device = e.target.value;
        this.populateScenarios();
      });
    }

    if (this.scenarioSelect) {
      this.scenarioSelect.addEventListener('change', (e) => {
        this.testId = e.target.value;
      });
    }

    // State store integration
    stateStore.on('site_model_changed', (sitePath) => {
      this.siteModel = sitePath;
      this.populateDevices();
    });

    stateStore.on('device_selected', (dev) => {
      this.device = dev;
      if (this.deviceSelect) this.deviceSelect.value = dev;
      this.populateScenarios();
    });
  }

  initFromStorage() {
    if (this.providerSelect) this.providerSelect.value = this.provider;
    if (this.inputApiKey) this.inputApiKey.value = this.apiKey;
    if (this.inputGcpProject) this.inputGcpProject.value = this.gcpProject;
    if (this.inputGcpLocation) this.inputGcpLocation.value = this.gcpLocation;
    if (this.inputBaselineRun) this.inputBaselineRun.value = this.baselineRun;
    this.toggleProviderFields();
  }

  toggleProviderFields() {
    const isVertex = (this.providerSelect?.value === 'vertex');
    if (this.groupApiKey) this.groupApiKey.style.display = isVertex ? 'none' : 'flex';
    if (this.groupVertex) this.groupVertex.style.display = isVertex ? 'flex' : 'none';
  }

  openSettingsModal() {
    if (this.settingsModal) this.settingsModal.classList.add('open');
  }

  closeSettingsModal() {
    if (this.settingsModal) this.settingsModal.classList.remove('open');
  }

  saveSettings() {
    this.provider = this.providerSelect?.value || 'gemini';
    this.apiKey = this.inputApiKey?.value || '';
    this.gcpProject = this.inputGcpProject?.value || '';
    this.gcpLocation = this.inputGcpLocation?.value || 'global';
    this.baselineRun = this.inputBaselineRun?.value || '';

    localStorage.setItem('mantis_provider', this.provider);
    localStorage.setItem('mantis_api_key', this.apiKey);
    localStorage.setItem('mantis_gcp_project', this.gcpProject);
    localStorage.setItem('mantis_gcp_location', this.gcpLocation);
    localStorage.setItem('mantis_baseline_run', this.baselineRun);

    this.closeSettingsModal();
    NotificationManager.show('Settings saved successfully', 'success');
  }

  async populateDevices() {
    if (!this.siteModel || !this.deviceSelect) return;
    try {
      const res = await fetch(`/api/devices?site_path=${encodeURIComponent(this.siteModel)}`);
      const data = await res.json();
      if (data.devices) {
        this.deviceSelect.innerHTML = '<option value="">-- Select Device --</option>';
        data.devices.forEach(d => {
          const opt = document.createElement('option');
          opt.value = d;
          opt.textContent = d;
          this.deviceSelect.appendChild(opt);
        });
        this.deviceSelect.disabled = false;
        if (this.device) this.deviceSelect.value = this.device;
      }
    } catch (e) {
      console.warn('Failed to populate devices:', e);
    }
  }

  async populateScenarios() {
    if (!this.siteModel || !this.device || !this.scenarioSelect) return;
    try {
      const res = await fetch(`/api/device_results?site_path=${encodeURIComponent(this.siteModel)}&device_id=${encodeURIComponent(this.device)}`);
      const data = await res.json();
      this.scenarioSelect.innerHTML = '<option value="">-- Select Scenario --</option>';
      if (data.results) {
        Object.keys(data.results).forEach(t => {
          const opt = document.createElement('option');
          opt.value = t;
          const status = data.results[t].status || '';
          opt.textContent = `${t} (${status})`;
          this.scenarioSelect.appendChild(opt);
        });
        this.scenarioSelect.disabled = false;
        if (this.testId) this.scenarioSelect.value = this.testId;
      }
    } catch (e) {
      console.warn('Failed to populate scenarios:', e);
    }
  }

  triggerTriage(data) {
    if (!data) return;
    const siteModel = data.site_model || data.siteModel || stateStore.get('siteModel') || localStorage.getItem('udmi_site_model') || '';
    const deviceId = data.device_id || data.deviceId || stateStore.get('activeDevice') || 'AHU-1';
    const testId = data.test_id || data.testId || '';
    const projectSpec = data.project_spec || data.projectSpec || stateStore.get('projectSpec') || '//mqtt/localhost:18833';

    if (siteModel) this.siteModel = siteModel;
    if (deviceId) this.device = deviceId;
    if (testId) this.testId = testId;
    if (projectSpec) this.projectSpec = projectSpec;

    if (this.deviceSelect) this.deviceSelect.value = this.device;
    if (this.scenarioSelect) this.scenarioSelect.value = this.testId;

    const query = `/diagnose ${this.testId || ''}`.trim();
    if (this.chatInput) this.chatInput.value = query;
    this.handleSendMessage();
  }

  loadDiagnose(data) {
    this.triggerTriage(data);
  }

  async handleSendMessage() {
    const rawText = this.chatInput ? this.chatInput.value.trim() : '';
    if (!rawText || this.isStreaming) return;

    this.chatInput.value = '';
    this.chatInput.style.height = 'auto';

    if (rawText === '/clear') {
      await this.handleClearSession();
      return;
    }

    if (rawText === '/copy') {
      await this.handleCopyTranscript();
      return;
    }

    if (rawText === '/export') {
      this.handleExportTranscript();
      return;
    }


    if (rawText === '/current-context' || rawText === '/context') {
      this.setWelcomeLayout(false);
      if (this.emptyState) this.emptyState.style.display = 'none';
      this.appendUserMessage(rawText);
      const aiBubble = this.createAIMessageBubble();

      if (aiBubble.loader) {
        aiBubble.loader.remove();
        aiBubble.loader = null;
      }
      aiBubble.textContainer.innerHTML = `
        <div style="font-family: var(--font-mono, monospace); font-size: 12px; background: rgba(0,0,0,0.03); padding: 12px; border-radius: 8px; line-height: 1.6;">
          <strong>Active Session Context:</strong><br>
          • <strong>Site Model:</strong> ${this.escapeHtml(this.siteModel || 'Auto-detected / Not set')}<br>
          • <strong>Target Device:</strong> ${this.escapeHtml(this.device || 'Auto-detected / Not set')}<br>
          • <strong>Target Test:</strong> ${this.escapeHtml(this.testId || 'Auto-detected / Not set')}<br>
          • <strong>Baseline Run:</strong> ${this.escapeHtml(this.baselineRun || 'None configured')}<br>
          • <strong>Auth Provider:</strong> ${this.escapeHtml(this.provider)} (${this.escapeHtml(this.gcpProject)} / ${this.escapeHtml(this.gcpLocation)})<br>
          • <strong>Session ID:</strong> <code>${this.escapeHtml(this.sessionId)}</code>
        </div>
      `;
      this.scrollToBottom();
      return;
    }

    let messageToSend = rawText;
    if (rawText.startsWith('/diagnose')) {
      const arg = rawText.replace(/^\/diagnose\s*/, '').trim();
      if (arg) {
        const tests = arg.split(/[\s,]+/).filter(t => t && t.toLowerCase() !== 'and');
        if (tests.length > 1) {
          this.testId = tests[0];
          messageToSend = `Diagnose why the following tests failed for device '${this.device || 'target device'}' in site model '${this.siteModel || 'the active site'}': ${tests.map(t => `'${t}'`).join(', ')}. Retrieve test execution logs for each test, analyze state/config synchronization, and determine the root cause for each failure.`;
        } else if (tests.length === 1) {
          this.testId = tests[0];
          messageToSend = `Diagnose why test '${this.testId}' failed for device '${this.device || 'target device'}' in site model '${this.siteModel || 'the active site'}'. Retrieve test execution logs, validate schema state, and determine the root cause.`;
        }
      } else {
        messageToSend = `Diagnose the active test failure for device '${this.device || 'target device'}' in site model '${this.siteModel || 'the active site'}'. Retrieve test execution logs, validate schema state, and determine the root cause.`;
      }
    } else if (rawText.startsWith('/diff')) {

      const arg = rawText.replace(/^\/diff\s*/, '').trim();
      const baseline = arg || this.baselineRun || 'the reference successful baseline run';
      messageToSend = `Perform a differential analysis comparing this test failure against reference baseline ${baseline}`;
    } else if (rawText.startsWith('/fact-check') || rawText.startsWith('/critique') || rawText.startsWith('/review')) {
      const arg = rawText.replace(/^\/(?:fact-check|critique|review)\s*/, '').trim();
      messageToSend = `/fact-check ${arg}`.trim();
    }


    // Switch from centered welcome layout to bottom composer layout
    this.setWelcomeLayout(false);
    if (this.emptyState) this.emptyState.style.display = 'none';

    // Extract last non-empty AI diagnostic response for resilient fact-checking across restarts
    let lastAiText = '';
    const existingAiBubbles = Array.from(document.querySelectorAll('.mantis-msg:not(.user) .mantis-msg-bubble'))
      .map(b => b.innerText.trim())
      .filter(t => t.length > 0);
    if (existingAiBubbles.length > 0) {
      lastAiText = existingAiBubbles[existingAiBubbles.length - 1];
    }

    // Append User Message
    this.appendUserMessage(rawText);

    // Create AI Message Container
    const aiBubble = this.createAIMessageBubble();

    this.setStreamingState(true);
    this.abortController = new AbortController();

    try {
      const payload = {
        session_id: this.sessionId,
        message: messageToSend,
        previous_report: lastAiText,
        site_model: this.siteModel,
        device_id: this.device,
        test_id: this.testId,
        project_spec: this.projectSpec,
        provider: this.provider,
        api_key: this.apiKey,
        gcp_project: this.gcpProject,
        gcp_location: this.gcpLocation,
        baseline_run: this.baselineRun
      };


      const response = await fetch('/api/mantis/chat/stream', {

        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: this.abortController.signal
      });

      if (!response.ok) {
        throw new Error(`Server returned status ${response.status}`);
      }

      await this.consumeSSEStream(response.body, aiBubble);
    } catch (e) {
      if (e.name !== 'AbortError') {
        aiBubble.textContainer.innerHTML += `<div class="text-error" style="color: #ef4444; margin-top: 8px;"><strong>Error:</strong> ${this.escapeHtml(e.message)}</div>`;
      } else {
        if (aiBubble.loader) {
          aiBubble.loader.remove();
          aiBubble.loader = null;
        }
        const currentText = aiBubble.textContainer.innerText.trim();
        if (!currentText) {
          aiBubble.textContainer.innerHTML = '<div style="color: #6b7280; font-style: italic; font-size: 13px; padding: 4px 0;">Generation stopped by user.</div>';
        } else if (!aiBubble.textContainer.querySelector('.mantis-stopped-tag')) {
          aiBubble.textContainer.innerHTML += '<div class="mantis-stopped-tag" style="color: #6b7280; font-style: italic; font-size: 12px; margin-top: 8px;">[Generation stopped by user]</div>';
        }
      }
    } finally {
      if (aiBubble.loader) {
        aiBubble.loader.remove();
        aiBubble.loader = null;
      }
      this.setStreamingState(false);
      this.scrollToBottom();
    }
  }


  async consumeSSEStream(readableStream, aiBubble) {
    const reader = readableStream.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let accumulatedMarkdown = '';
    let isStreamDone = false;

    try {
      while (!isStreamDone) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop(); // keep incomplete line in buffer

        let currentEvent = 'message';
        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.substring(6).trim();
          } else if (trimmed.startsWith('data:')) {
            const rawData = trimmed.substring(5).trim();
            if (!rawData) continue;

            try {
              const data = JSON.parse(rawData);
              this.handleStreamEvent(currentEvent, data, aiBubble, (chunk) => {
                accumulatedMarkdown += chunk;
                aiBubble.textContainer.innerHTML = this.renderMarkdown(accumulatedMarkdown);
                this.renderGraphvizDiagrams(aiBubble.textContainer);
                this.scrollToBottom();
              });

              if (currentEvent === 'done' || data.type === 'done' || currentEvent === 'error' || data.type === 'error') {
                this.renderGraphvizDiagrams(aiBubble.textContainer);
                isStreamDone = true;
                break;
              }
            } catch (jsonErr) {
              console.warn('Failed to parse SSE JSON:', jsonErr, rawData);
            }
          }
        }
      }
    } finally {
      this.renderGraphvizDiagrams(aiBubble.textContainer);
      try {
        await reader.cancel();
      } catch (_) {}
    }
  }


  handleStreamEvent(eventType, data, aiBubble, onToken) {
    const type = data.type || eventType;

    if (type === 'token') {
      if (aiBubble.loader) {
        aiBubble.loader.remove();
        aiBubble.loader = null;
      }
      onToken(data.text || '');
    } else if (type === 'done') {
      if (aiBubble.loader) {
        aiBubble.loader.remove();
        aiBubble.loader = null;
      }
      if (data.full_text && !aiBubble.textContainer.innerHTML) {
        onToken(data.full_text);
      }
      this.renderGraphvizDiagrams(aiBubble.textContainer);

    } else if (type === 'error') {
      if (aiBubble.loader) {
        aiBubble.loader.remove();
        aiBubble.loader = null;
      }
      aiBubble.textContainer.innerHTML += `<div style="color: #ef4444; margin-top: 8px;"><strong>Error:</strong> ${this.escapeHtml(data.error || 'Unknown error')}</div>`;
    } else if (type === 'context_update') {
      if (data.site_model) this.siteModel = data.site_model;
      if (data.device_id) this.device = data.device_id;
      if (data.test_id) this.testId = data.test_id;
      if (data.device_id && this.deviceSelect) this.deviceSelect.value = data.device_id;
      if (data.test_id && this.scenarioSelect) this.scenarioSelect.value = data.test_id;
    } else if (type === 'thought') {
      const thoughtDiv = document.createElement('div');
      thoughtDiv.className = 'mantis-thought-block';
      thoughtDiv.innerHTML = `
        <span class="material-symbols-outlined" style="font-size: 16px; color: var(--mantis-primary); margin-top: 1px; flex-shrink: 0;">psychology</span>
        <div><em>${this.escapeHtml(data.text || '')}</em></div>
      `;
      aiBubble.toolsContainer.appendChild(thoughtDiv);
      this.scrollToBottom();
    } else if (type === 'tool_start') {
      const pill = document.createElement('div');
      pill.className = 'mantis-tool-pill';
      pill.id = `tool-${data.name}-${Date.now()}`;
      pill.innerHTML = `
        <div class="mantis-tool-header">
          <div class="mantis-tool-title">
            <span class="material-symbols-outlined mantis-tool-status-icon running">sync</span>
            <span>Running <strong>${this.escapeHtml(data.name)}</strong>...</span>
          </div>
          <span class="material-symbols-outlined" style="font-size:16px;">expand_more</span>
        </div>
        <div class="mantis-tool-body" style="display: none;">Arguments: ${this.escapeHtml(JSON.stringify(data.args || {}, null, 2))}</div>
      `;
      pill.querySelector('.mantis-tool-header').addEventListener('click', () => {
        const body = pill.querySelector('.mantis-tool-body');
        body.style.display = (body.style.display === 'none') ? 'block' : 'none';
      });
      aiBubble.toolsContainer.appendChild(pill);
      this.scrollToBottom();
    } else if (type === 'tool_end') {
      const runningIcons = aiBubble.toolsContainer.querySelectorAll('.mantis-tool-status-icon.running');
      if (runningIcons.length > 0) {
        const lastIcon = runningIcons[runningIcons.length - 1];
        lastIcon.classList.remove('running');
        lastIcon.textContent = 'check_circle';
        const headerTitle = lastIcon.parentElement.querySelector('span:last-child');
        if (headerTitle) {
          headerTitle.innerHTML = `Executed <strong>${this.escapeHtml(data.name)}</strong> (${data.characters || 0} chars)`;
        }
        const pill = lastIcon.closest('.mantis-tool-pill');
        if (pill) {
          const body = pill.querySelector('.mantis-tool-body');
          if (body) {
            body.textContent = data.summary || data.result || 'Execution complete';
          }
        }
      }
    }
  }


  handleStopStreaming() {
    if (this.isStreaming) {
      if (this.abortController) {
        this.abortController.abort();
      }

      // Signal backend to abort LLM tools & execution loop
      fetch('/api/mantis/chat/stop', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_id: this.sessionId })
      }).catch(() => {});

      // Immediately purge any active thinking spinners from the DOM
      const activeLoaders = document.querySelectorAll('.mantis-thinking-loader');
      activeLoaders.forEach(loader => loader.remove());

      // If the latest AI response bubble is empty or mid-stream, display a stopped notice
      const aiBubbles = document.querySelectorAll('.mantis-msg:not(.user) .mantis-msg-bubble');
      if (aiBubbles.length > 0) {
        const lastBubble = aiBubbles[aiBubbles.length - 1];
        const textContainer = lastBubble.querySelector('.mantis-msg-text');
        if (textContainer) {
          const currentText = textContainer.innerText.trim();
          if (!currentText) {
            textContainer.innerHTML = '<div style="color: #6b7280; font-style: italic; font-size: 13px; padding: 4px 0;">Generation stopped by user.</div>';
          } else if (!textContainer.querySelector('.mantis-stopped-tag')) {
            textContainer.innerHTML += '<div class="mantis-stopped-tag" style="color: #6b7280; font-style: italic; font-size: 12px; margin-top: 8px;">[Generation stopped by user]</div>';
          }
        }
      }

      this.setStreamingState(false);
      NotificationManager.show('Mantis generation stopped', 'neutral');
    }
  }


  async handleClearSession() {
    if (this.clearingOverlay) {
      this.clearingOverlay.style.display = 'flex';
      // Trigger CSS transition
      this.clearingOverlay.offsetHeight;
      this.clearingOverlay.classList.add('active');
    }
    if (this.chatStream) this.chatStream.classList.add('mantis-clearing');
    if (this.btnClear) {
      this.btnClear.disabled = true;
      this.btnClear.classList.add('clearing');
    }
    if (this.chatInput) this.chatInput.disabled = true;

    try {
      const clearPromise = fetch('/api/mantis/chat/clear', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_id: this.sessionId })
      });
      // Maintain transition for 450ms for clear visual confirmation
      const delayPromise = new Promise(res => setTimeout(res, 450));
      await Promise.all([clearPromise, delayPromise]);

      this.sessionId = 'mantis-' + Math.random().toString(36).substring(2, 10);
      if (this.messagesContainer) this.messagesContainer.innerHTML = '';
      if (this.emptyState) this.emptyState.style.display = 'flex';
      this.setWelcomeLayout(true);
      NotificationManager.show('Fresh diagnostic session started', 'success');

    } catch (e) {
      console.warn('Failed to clear session:', e);
    } finally {
      if (this.clearingOverlay) {
        this.clearingOverlay.classList.remove('active');
        setTimeout(() => {
          this.clearingOverlay.style.display = 'none';
        }, 200);
      }
      if (this.chatStream) this.chatStream.classList.remove('mantis-clearing');
      if (this.btnClear) {
        this.btnClear.disabled = false;
        this.btnClear.classList.remove('clearing');
      }
      if (this.chatInput) {
        this.chatInput.disabled = false;
        this.chatInput.focus();
      }
    }
  }



  buildMarkdownTranscript() {
    const textNodes = [];
    document.querySelectorAll('.mantis-msg').forEach(msg => {
      const isUser = msg.classList.contains('user');
      const bubble = msg.querySelector('.mantis-msg-bubble');
      if (bubble) {
        textNodes.push(`### ${isUser ? '👤 User' : '🦗 Mantis'}\n\n${bubble.innerText}\n`);
      }
    });

    if (textNodes.length === 0) {
      return null;
    }

    const siteStr = this.siteModel || 'Auto-detected / Not set';
    const devStr = this.device || 'Auto-detected / Not set';
    const testStr = this.testId || 'Auto-detected / Not set';

    return `# Mantis AI Diagnostic Transcript\n- Date: ${new Date().toISOString()}\n- Site: ${siteStr}\n- Device: ${devStr}\n- Test: ${testStr}\n\n---\n\n` + textNodes.join('\n---\n\n');
  }

  async handleCopyTranscript() {
    const transcript = this.buildMarkdownTranscript();
    if (!transcript) {
      NotificationManager.show('No conversation history to copy', 'neutral');
      return;
    }

    try {
      await navigator.clipboard.writeText(transcript);
      NotificationManager.show('Transcript copied to clipboard as Markdown', 'success');
    } catch (err) {
      const textarea = document.createElement('textarea');
      textarea.value = transcript;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      NotificationManager.show('Transcript copied to clipboard as Markdown', 'success');
    }
  }

  handleExportTranscript() {
    const transcript = this.buildMarkdownTranscript();
    if (!transcript) {
      NotificationManager.show('No conversation history to export', 'neutral');
      return;
    }

    const blob = new Blob([transcript], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `mantis_transcript_${new Date().toISOString().replace(/[:.]/g, '-')}.md`;
    a.click();
    URL.revokeObjectURL(url);
    NotificationManager.show('Transcript exported to Markdown', 'success');
  }



  appendUserMessage(text) {
    if (!this.messagesContainer) return;
    const msgDiv = document.createElement('div');
    msgDiv.className = 'mantis-msg user';
    msgDiv.innerHTML = `
      <div class="mantis-msg-content">
        <div class="mantis-msg-bubble">${this.escapeHtml(text)}</div>
      </div>
      <div class="mantis-msg-avatar">
        <span class="material-symbols-outlined" style="font-size: 18px;">person</span>
      </div>
    `;
    this.messagesContainer.appendChild(msgDiv);
    this.scrollToBottom();
  }

  createAIMessageBubble() {
    if (!this.messagesContainer) return null;
    const msgDiv = document.createElement('div');
    msgDiv.className = 'mantis-msg ai';
    msgDiv.innerHTML = `
      <div class="mantis-msg-avatar">
        <span class="material-symbols-outlined" style="font-size: 18px;">auto_awesome</span>
      </div>
      <div class="mantis-msg-content" style="flex: 1;">
        <div class="mantis-msg-tools"></div>
        <div class="mantis-msg-bubble">
          <div class="mantis-thinking-loader">
            <div class="mantis-fade-dots"></div>
          </div>
          <div class="mantis-msg-text"></div>
        </div>

        <div class="mantis-msg-actions">
          <button class="btn-msg-action btn-copy-msg" title="Copy response">
            <span class="material-symbols-outlined">content_copy</span>
            <span>Copy</span>
          </button>
        </div>
      </div>
    `;
    this.messagesContainer.appendChild(msgDiv);

    const textContainer = msgDiv.querySelector('.mantis-msg-text');
    const toolsContainer = msgDiv.querySelector('.mantis-msg-tools');
    const bubbleContainer = msgDiv.querySelector('.mantis-msg-bubble');
    const loader = bubbleContainer.querySelector('.mantis-thinking-loader');
    const btnCopy = msgDiv.querySelector('.btn-copy-msg');


    if (btnCopy) {
      btnCopy.addEventListener('click', () => {
        navigator.clipboard.writeText(textContainer.innerText);
        NotificationManager.show('Copied to clipboard', 'success');
      });
    }

    this.scrollToBottom();
    return { element: msgDiv, textContainer, toolsContainer, loader };
  }

  setStreamingState(streaming) {
    this.isStreaming = streaming;
    if (this.btnSend) this.btnSend.style.display = streaming ? 'none' : 'flex';
    if (this.btnStop) this.btnStop.style.display = streaming ? 'flex' : 'none';
  }

  scrollToBottom() {
    if (this.chatStream) {
      this.chatStream.scrollTop = this.chatStream.scrollHeight;
    }
  }

  escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  renderMarkdown(markdown) {
    if (!markdown) return '';
    
    // 1. Extract and stash fenced code blocks (including Graphviz DOT diagrams)
    const codeBlocks = [];
    let text = markdown.replace(/```([a-zA-Z0-9_\-+]*)\n([\s\S]*?)```/g, (match, lang, code) => {
      const idx = codeBlocks.length;
      const cleanLang = (lang || '').trim().toLowerCase();
      
      if (cleanLang === 'dot' || cleanLang === 'graphviz') {
        const diagramId = 'mantis-gv-' + Math.random().toString(36).substring(2, 9);
        const encodedDot = encodeURIComponent(code.trim());
        codeBlocks.push(`
          <div class="mantis-graphviz-card" id="${diagramId}" data-dot="${encodedDot}">
            <div class="mantis-gv-header">
              <div class="mantis-gv-title">
                <span class="material-symbols-outlined">account_tree</span>
                <span>System Topology / Architecture Diagram</span>
              </div>
              <div class="mantis-gv-actions">
                <button type="button" class="btn-gv-toggle" title="Toggle Diagram / Source DOT">
                  <span class="material-symbols-outlined">code</span>
                  <span class="btn-text">View DOT</span>
                </button>
                <button type="button" class="btn-gv-copy" title="Copy DOT source">
                  <span class="material-symbols-outlined">content_copy</span>
                </button>
                <button type="button" class="btn-gv-download" title="Download SVG">
                  <span class="material-symbols-outlined">download</span>
                </button>
              </div>
            </div>
            <div class="mantis-gv-content">
              <div class="mantis-gv-preview">
                <div class="mantis-gv-loading">
                  <span class="spinner-dots"></span>
                  <span>Rendering Graphviz Diagram...</span>
                </div>
              </div>
              <pre class="mantis-gv-source" style="display: none;"><code class="language-dot">${this.escapeHtml(code.trim())}</code></pre>
            </div>
          </div>
        `);
      } else {
        codeBlocks.push(`<pre><code class="language-${lang}">${this.escapeHtml(code.trim())}</code></pre>`);
      }
      return `__CODE_BLOCK_${idx}__`;
    });

    // 2. Inline code
    text = text.replace(/`([^`]+)`/g, (match, code) => `<code>${this.escapeHtml(code)}</code>`);

    // 3. Headers
    text = text.replace(/^#### (.*$)/gim, '<h4>$1</h4>');
    text = text.replace(/^### (.*$)/gim, '<h3>$1</h3>');
    text = text.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    text = text.replace(/^# (.*$)/gim, '<h1>$1</h1>');

    // 4. Bold & Italics
    text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // 5. Blockquotes
    text = text.replace(/^\> (.*$)/gim, '<blockquote>$1</blockquote>');

    // 6. Ordered / Numbered Lists (e.g. 1. Item, 2. Item)
    text = text.replace(/^(\s*\d+\.\s+.*(?:\n\s*\d+\.\s+.*)*)/gm, (match) => {
      const items = match.split('\n').map(line => {
        const itemText = line.replace(/^\s*\d+\.\s+/, '').trim();
        return itemText ? `<li>${itemText}</li>` : '';
      }).filter(Boolean).join('');
      return `<ol>${items}</ol>`;
    });

    // 7. Unordered Bullet Lists (e.g. * Item, - Item)
    text = text.replace(/^(\s*[\-\*]\s+.*(?:\n\s*[\-\*]\s+.*)*)/gm, (match) => {
      const items = match.split('\n').map(line => {
        const itemText = line.replace(/^\s*[\-\*]\s+/, '').trim();
        return itemText ? `<li>${itemText}</li>` : '';
      }).filter(Boolean).join('');
      return `<ul>${items}</ul>`;
    });

    // 8. Restore Code Blocks
    text = text.replace(/__CODE_BLOCK_(\d+)__/g, (match, idx) => codeBlocks[parseInt(idx, 10)] || '');

    // 9. Paragraph & Line breaks outside HTML blocks
    text = text.replace(/\n\n+/g, '<br/><br/>');
    text = text.replace(/([^\>])\n/g, '$1<br/>');

    return text;
  }

  async renderGraphvizDiagrams(container) {
    if (!container) return;
    const cards = container.querySelectorAll('.mantis-graphviz-card:not([data-rendered="true"])');
    for (const card of cards) {
      const encodedDot = card.getAttribute('data-dot');
      if (!encodedDot) continue;

      const dotSource = decodeURIComponent(encodedDot);
      const preview = card.querySelector('.mantis-gv-preview');
      const source = card.querySelector('.mantis-gv-source');
      const btnToggle = card.querySelector('.btn-gv-toggle');
      const btnCopy = card.querySelector('.btn-gv-copy');
      const btnDownload = card.querySelector('.btn-gv-download');

      card.setAttribute('data-rendered', 'true');

      // Bind toolbar actions
      if (btnToggle) {
        btnToggle.addEventListener('click', () => {
          const isSourceVisible = source.style.display !== 'none';
          if (isSourceVisible) {
            source.style.display = 'none';
            preview.style.display = 'flex';
            btnToggle.innerHTML = '<span class="material-symbols-outlined">code</span><span class="btn-text">View DOT</span>';
          } else {
            preview.style.display = 'none';
            source.style.display = 'block';
            btnToggle.innerHTML = '<span class="material-symbols-outlined">schema</span><span class="btn-text">View Diagram</span>';
          }
        });
      }

      if (btnCopy) {
        btnCopy.addEventListener('click', () => {
          navigator.clipboard.writeText(dotSource);
          NotificationManager.show('DOT graph definition copied to clipboard', 'success');
        });
      }

      // Check cache first
      if (this.graphvizCache.has(dotSource)) {
        preview.innerHTML = this.graphvizCache.get(dotSource);
        this._bindDownloadBtn(btnDownload, dotSource);
        continue;
      }

      // Asynchronously fetch rendered SVG from backend
      try {
        const res = await fetch('/api/graphviz/render', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ dot: dotSource })
        });
        const data = await res.json();
        if (data.status === 'success' && data.svg) {
          this.graphvizCache.set(dotSource, data.svg);
          preview.innerHTML = data.svg;
          this._bindDownloadBtn(btnDownload, dotSource);
        } else {
          preview.innerHTML = `<div class="mantis-gv-error">Graphviz Error: ${this.escapeHtml(data.error || 'Syntax error')}</div>`;
        }
      } catch (err) {
        preview.innerHTML = `<div class="mantis-gv-error">Failed to render diagram: ${this.escapeHtml(err.message)}</div>`;
      }
    }
  }

  _bindDownloadBtn(btnDownload, dotSource) {
    if (!btnDownload) return;
    btnDownload.onclick = () => {
      const svgContent = this.graphvizCache.get(dotSource);
      if (!svgContent) return;
      const blob = new Blob([svgContent], { type: 'image/svg+xml;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `udmi-topology-${Date.now()}.svg`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      NotificationManager.show('SVG diagram downloaded', 'success');
    };
  }

}


