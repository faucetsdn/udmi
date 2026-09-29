/**
 * Layer 2 — Mantis triage panel.
 *
 * Streams the real tripartite reasoning loop: phase transitions, tool calls
 * with their actual arguments, tool results, the competing-hypothesis matrix,
 * and response tokens. Nothing is synthesised client-side — if the backend
 * emits an error the error is what the user sees.
 *
 * This was a routed view. It now renders into whatever container it is handed
 * (the Mantis drawer), so it owns no navigation of its own.
 */

import { streamEvents } from '../core/api.js';
import { desktopNotify } from '../core/desktop-notify.js';
import { store } from '../core/store.js';
import { JSONViewer } from './json-viewer.js';
import {
  DIAGRAM_ACTIONS_HTML,
  copyText,
  flashSuccess,
  icon,
  makeCopyButton,
  reasoningText,
  transcriptText,
  wireDiagramActions,
} from './mantis-export.js';
import { escapeHtml, formatInlineMarkdown } from './mantis-markdown.js';

const SESSION_ID = 'workbench-assistant';

/**
 * The verdict vocabulary is fixed by HYPOTHESIS_VERDICTS in mantis/agent.py and
 * relayed unchanged by _translate in workbench/server/mantis_adapter.py. An
 * earlier map keyed on CONFIRMED/SUPPORTED — words the agent never emits — so
 * the two verdicts that decide the outcome, PRIMARY and CONTRIBUTING, both fell
 * through to the neutral tone and read as "no finding".
 */
const VERDICT_TONE = {
  PRIMARY: 'primary',
  CONTRIBUTING: 'contributing',
  REFUTED: 'refuted',
  UNRESOLVED: 'unresolved',
};

export class MantisPanel {
  constructor(container) {
    if (!container) {
      throw new Error('MantisPanel requires a container element to render into.');
    }
    this.container = container;
    this.stream = null;

    // One AbortController detaches every document-level listener in destroy().
    this.listeners = new AbortController();

    // Per-turn state, reset by _beginTurn().
    this.activityEl = null;
    this.activityBodyEl = null;
    this.activityMetaEl = null;
    this.thoughtEl = null;
    this.matrixEl = null;
    this.toolCallCount = 0;
    this.currentPhase = null;
    this.assistantBubble = null;
    this.assistantRawText = '';
    this.turnStartedAt = null;
    this.elapsedTimer = null;

    // Recorded chat entries in DOM order; the source for "Copy transcript".
    // Kinds: user, reasoning, matrix, answer, note, error.
    this.transcript = [];
    this.reasoningItems = null;
    this.thoughtItem = null;
    this.answerEntry = null;
    this.matrixEntry = null;
    // Raw markdown behind each assistant bubble, kept before rendering.
    this.rawAnswers = new WeakMap();

    this.render();
  }

  render() {
    this.container.innerHTML = `
      <div class="mantis-panel">
        <div class="mantis-panel-toolbar">
          <span class="chat-context" data-role="context"></span>
          <div class="mantis-panel-actions">
            <span class="mantis-elapsed" data-role="elapsed" hidden aria-live="off"></span>
            <select data-role="test-select" class="btn btn-ghost" title="Select test to diagnose" hidden></select>
            <button type="button" class="btn btn-ghost btn-with-icon" data-act="copy-transcript"
                    title="Copy the entire chat as markdown">
              <span class="material-symbols-outlined" aria-hidden="true">content_copy</span> Copy transcript
            </button>
            <button type="button" class="btn btn-ghost" data-act="clear" title="Clear chat history">Clear</button>
          </div>
        </div>
        <div class="chat-log" data-role="log" role="log" aria-live="polite"></div>
        <form class="chat-composer" data-role="composer">
          <label class="visually-hidden" for="mantis-composer-input">Message</label>
          <textarea id="mantis-composer-input" data-role="input" rows="2"
                    placeholder="Ask Mantis to diagnose a failure, or type /help"></textarea>
          <button type="submit" class="btn btn-primary" data-role="send">Send</button>
          <button type="button" class="btn btn-danger btn-with-icon mantis-stop" data-act="stop" hidden
                  title="Stop the running Mantis analysis">
            <span class="material-symbols-outlined" aria-hidden="true">stop_circle</span> Stop
          </button>
          <label class="notify-toggle" title="Email me the answer when it is ready. Closing this tab will not stop the run. Enable email in Settings first.">
            <input type="checkbox" data-role="notify" /> Email me when done
          </label>
        </form>
      </div>
    `;

    this.logEl = this.container.querySelector('[data-role="log"]');
    this.contextEl = this.container.querySelector('[data-role="context"]');
    this.testSelect = this.container.querySelector('[data-role="test-select"]');
    this.clearBtn = this.container.querySelector('[data-act="clear"]');
    this.stopBtn = this.container.querySelector('[data-act="stop"]');
    this.elapsedEl = this.container.querySelector('[data-role="elapsed"]');
    this.inputEl = this.container.querySelector('[data-role="input"]');
    this.sendBtn = this.container.querySelector('[data-role="send"]');
    this.notifyInput = this.container.querySelector('[data-role="notify"]');

    this._buildDiagramModal();

    this.clearBtn.addEventListener('click', () => {
      this.clearChat();
    });

    const transcriptBtn = this.container.querySelector('[data-act="copy-transcript"]');
    transcriptBtn.addEventListener('click', async () => {
      try {
        await copyText(transcriptText(this.transcript, this.contextEl.textContent));
        flashSuccess(transcriptBtn);
      } catch (cause) {
        this.appendError(`Copy transcript failed: ${cause.message}`);
      }
    });

    this.stopBtn.addEventListener('click', () => {
      this.stop();
    });

    this.testSelect.addEventListener('change', () => {
      const selected = this.testSelect.value;
      if (!selected) return;
      const { siteModel, deviceId } = store.getState();
      this.runTriage({ siteModel, deviceId, testId: selected });
    });

    this.container.querySelector('[data-role="composer"]').addEventListener('submit', (event) => {
      event.preventDefault();
      this.send();
    });
    this.inputEl.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        this.send();
      }
    });

    this.unsubscribe = store.subscribe(() => this.paintContext());
  }

  /**
   * The diagram viewer is mounted on <body>, not inside the panel. The drawer
   * that hosts this panel is a transformed element, which would make a
   * `position: fixed` overlay resolve against the drawer instead of the
   * viewport and clip the diagram to the drawer's column.
   */
  _buildDiagramModal() {
    const modal = document.createElement('div');
    modal.className = 'modal-backdrop mantis-diagram-backdrop';
    modal.hidden = true;
    modal.innerHTML = `
      <div class="modal modal-diagram-fullscreen" role="dialog" aria-modal="true" aria-labelledby="diagram-modal-title">
        <header class="modal-header">
          <div>
            <h2 class="modal-title" id="diagram-modal-title">Diagram</h2>
            <p class="modal-subtitle">Interactive full-screen sequence viewer with zoom &amp; pan</p>
          </div>
          <div class="modal-header-actions" style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
            <div class="diagram-export-actions">${DIAGRAM_ACTIONS_HTML}</div>
            <div class="diagram-zoom-controls">
              <button type="button" class="btn btn-ghost btn-sm" data-zoom="out" title="Zoom Out">−</button>
              <span class="diagram-zoom-level" data-role="zoom-level">100%</span>
              <button type="button" class="btn btn-ghost btn-sm" data-zoom="in" title="Zoom In">+</button>
              <button type="button" class="btn btn-ghost btn-sm" data-zoom="reset" title="Reset Zoom">Fit / 100%</button>
            </div>
            <button type="button" class="btn btn-ghost" data-act="close-diagram" aria-label="Close diagram viewer">✕ Close</button>
          </div>
        </header>
        <div class="diagram-modal-body" data-role="diagram-modal-body" tabindex="0">
          <div class="diagram-modal-canvas" data-role="diagram-canvas"></div>
        </div>
      </div>
    `;
    document.body.appendChild(modal);

    this.diagramModal = modal;
    this.diagramModalBody = modal.querySelector('[data-role="diagram-modal-body"]');
    this.diagramCanvas = modal.querySelector('[data-role="diagram-canvas"]');
    this.zoomLevelEl = modal.querySelector('[data-role="zoom-level"]');
    this.diagramZoom = 1.0;

    const signal = this.listeners.signal;

    wireDiagramActions(
      modal.querySelector('.diagram-export-actions'),
      () => this.diagramCanvas.querySelector('svg'),
      (message) => this.appendError(`Diagram export failed: ${message}`),
    );

    modal.querySelector('[data-act="close-diagram"]')
      .addEventListener('click', () => this.closeDiagramModal());
    modal.addEventListener('click', (e) => {
      if (e.target === modal) this.closeDiagramModal();
    });

    modal.querySelector('[data-zoom="in"]')
      .addEventListener('click', () => this.setZoom(this.diagramZoom * 1.25));
    modal.querySelector('[data-zoom="out"]')
      .addEventListener('click', () => this.setZoom(this.diagramZoom / 1.25));
    modal.querySelector('[data-zoom="reset"]')
      .addEventListener('click', () => this.setZoom(1.0));

    // Pan & wheel zoom handlers
    let isPanning = false;
    let startX = 0, startY = 0;
    let scrollLeft = 0, scrollTop = 0;

    this.diagramModalBody.addEventListener('mousedown', (e) => {
      if (e.target.closest('button')) return;
      isPanning = true;
      this.diagramModalBody.style.cursor = 'grabbing';
      startX = e.pageX - this.diagramModalBody.offsetLeft;
      startY = e.pageY - this.diagramModalBody.offsetTop;
      scrollLeft = this.diagramModalBody.scrollLeft;
      scrollTop = this.diagramModalBody.scrollTop;
    });

    window.addEventListener('mouseup', () => {
      if (isPanning) {
        isPanning = false;
        this.diagramModalBody.style.cursor = 'grab';
      }
    }, { signal });

    this.diagramModalBody.addEventListener('mousemove', (e) => {
      if (!isPanning) return;
      e.preventDefault();
      const x = e.pageX - this.diagramModalBody.offsetLeft;
      const y = e.pageY - this.diagramModalBody.offsetTop;
      this.diagramModalBody.scrollLeft = scrollLeft - (x - startX);
      this.diagramModalBody.scrollTop = scrollTop - (y - startY);
    });

    this.diagramModalBody.addEventListener('wheel', (e) => {
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        const factor = e.deltaY < 0 ? 1.15 : 0.85;
        this.setZoom(this.diagramZoom * factor);
      }
    }, { passive: false, signal });

    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !this.diagramModal.hidden) {
        // Stop here so the host drawer's own Escape handler does not also close
        // the drawer behind the diagram the operator was reading.
        e.stopPropagation();
        this.closeDiagramModal();
      }
    }, { signal, capture: true });
  }

  destroy() {
    this.unsubscribe?.();
    this.stream?.abort();
    this._stopElapsedTimer();
    this.listeners.abort();
    this.diagramModal?.remove();
  }

  init() {
    this.paintContext();
    if (this.logEl.childElementCount === 0) {
      this.appendNote(
        'Select a site model and device on the Sequencer screen to give triage real context.'
      );
    }
  }

  /** Puts the caret in the composer when the host drawer opens. */
  focusComposer() {
    this.inputEl.focus();
  }

  paintContext() {
    const { siteModel, deviceId, activeTestId, results, testStatus } = store.getState();
    let label = 'No workspace context selected';
    if (siteModel) {
      label = siteModel;
      if (deviceId) label += ` · ${deviceId}`;
      if (activeTestId) label += ` · Test: ${activeTestId}`;
    }
    this.contextEl.textContent = label;
    this.contextEl.title = label;

    // Collect all tests that failed
    const failed = new Set();
    for (const [name, rec] of Object.entries(results || {})) {
      if (rec?.status === 'fail') failed.add(name);
    }
    for (const [name, stat] of Object.entries(testStatus || {})) {
      if (stat === 'fail') failed.add(name);
    }

    if (failed.size > 0) {
      this.testSelect.hidden = false;
      this.testSelect.innerHTML = '<option value="">Diagnose failed test…</option>';
      for (const t of [...failed].sort()) {
        const opt = document.createElement('option');
        opt.value = t;
        opt.textContent = t;
        if (t === activeTestId) opt.selected = true;
        this.testSelect.appendChild(opt);
      }
    } else {
      this.testSelect.hidden = true;
    }
  }

  /**
   * The single entry point for a context-bound triage turn. Triage without a
   * site model, device, and test is not a weaker diagnosis, it is a different
   * question; the run is refused rather than padded with a stand-in device.
   */
  runTriage({ siteModel, deviceId, testId }) {
    const missing = [];
    if (!siteModel) missing.push('site model');
    if (!deviceId) missing.push('device id');
    if (!testId) missing.push('test id');
    if (missing.length > 0) {
      this.appendError(
        `Triage not started: ${missing.join(', ')} missing from the workspace ` +
        'context. Select them on the Sequencer screen and trigger triage again.'
      );
      return;
    }

    store.update('triage.select', { activeTestId: testId });
    this.inputEl.value =
      `Diagnose test failure for '${testId}' on device '${deviceId}' in site model '${siteModel}'`;
    this.send({ siteModel, deviceId, testId });
  }

  send(overrides = {}) {
    const message = this.inputEl.value.trim();
    if (!message || this.sendBtn.disabled) return;
    // Still inside the Send/Diagnose click, the only moment a browser shows the prompt.
    desktopNotify.requestFromGesture();
    this.turnFailed = false;

    const state = store.getState();
    const siteModel = overrides.siteModel ?? state.siteModel;
    const deviceId = overrides.deviceId ?? state.deviceId;
    const testId = overrides.testId ?? state.activeTestId ?? null;

    // Per message and off by default: the toggle applies to this send only.
    const notify = this.notifyInput.checked;
    this.notifyInput.checked = false;

    this.appendBubble('user', message);
    this.inputEl.value = '';
    this._beginTurn();
    this.setBusy(true);
    if (notify) {
      this.appendNote(
        'You will get an email when Mantis finishes. Closing this tab will not stop the run; ' +
        'Stop still cancels it and sends nothing.'
      );
    }

    this.stream = streamEvents('/api/mantis/chat', {
      method: 'POST',
      module: 'MantisPanel',
      body: {
        session_id: SESSION_ID,
        message,
        notify,
        context: {
          site_model: siteModel,
          device_id: deviceId,
          test_id: testId,
        },
      },
      onEvent: (name, data) => this.handleEvent(name, data),
    });

    this.stream.done
      .catch((cause) => {
        if (cause.name !== 'AbortError') {
          this.turnFailed = true;
          this.appendError(cause.message);
        }
      })
      .finally(() => {
        this.finalizeAssistantBubble();
        this.setBusy(false);
        desktopNotify.notify({
          title: this.turnFailed ? 'Mantis stopped with an error' : 'Mantis has answered',
          body: message.length > 120 ? `${message.slice(0, 117)}...` : message,
          tag: 'workbench-mantis',
        });
      });
  }

  /**
   * Stops the in-flight run on the server, then closes the stream. Aborting only
   * the fetch would leave the agent calling the model and running tools. If the
   * server refuses the stop, the stream stays open so the run remains visible.
   */
  async stop() {
    if (!this.stream || this.stopBtn.disabled) return;
    this.stopBtn.disabled = true;
    let result;
    try {
      const response = await fetch('/api/mantis/chat/stop', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_id: SESSION_ID }),
      });
      if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
      }
      result = await response.json();
    } catch (cause) {
      this.stopBtn.disabled = false;
      this.appendError(
        `Mantis was NOT stopped: POST /api/mantis/chat/stop failed with ${cause.message}. ` +
        'The analysis is still running.'
      );
      return;
    }
    const elapsed = this._elapsedLabel();
    this.stream?.abort();
    this.appendNote(`Stopped by operator after ${elapsed}. ${result.message}`);
  }

  handleEvent(name, data) {
    if (name === 'phase') {
      this.appendPhase(data.phase);
    } else if (name === 'tool_call') {
      this.appendToolCall(data);
    } else if (name === 'tool_result') {
      this.appendToolResult(data);
    } else if (name === 'thought') {
      this.appendThought(data.text);
    } else if (name === 'hypothesis_matrix') {
      this.appendMatrix(data);
    } else if (name === 'token') {
      this.appendToken(data.text);
    } else if (name === 'error') {
      this.turnFailed = true;
      this.appendError(data.message);
    } else if (name === 'done') {
      this.finalizeAssistantBubble();
      this.setBusy(false);
    }
  }

  // --------------------------------------------------------- turn state ---
  /** Resets the per-turn accumulators so nothing leaks between questions. */
  _beginTurn() {
    this.activityEl = null;
    this.activityBodyEl = null;
    this.activityMetaEl = null;
    this.thoughtEl = null;
    this.matrixEl = null;
    this.toolCallCount = 0;
    this.currentPhase = null;
    this.assistantBubble = null;
    this.assistantRawText = '';
    this.reasoningItems = null;
    this.thoughtItem = null;
    this.answerEntry = null;
    this.matrixEntry = null;
    this.turnStartedAt = Date.now();
  }

  /**
   * Phases, tool calls, and streamed prose share one disclosure in arrival
   * order. Keeping them together is what makes a 3-4 minute run legible: the
   * operator watches one region move instead of a transcript scrolling past.
   */
  _activityBody() {
    if (this.activityBodyEl) return this.activityBodyEl;

    const details = document.createElement('details');
    details.className = 'mantis-activity';
    details.open = true;

    const summary = document.createElement('summary');
    summary.className = 'mantis-activity-summary';

    const chevron = icon('expand_more');
    chevron.classList.add('mantis-activity-chevron');

    const label = document.createElement('span');
    label.className = 'mantis-activity-label';
    label.textContent = 'Agent Reasoning';

    const meta = document.createElement('span');
    meta.className = 'mantis-activity-meta';
    meta.textContent = 'starting…';

    const entry = { kind: 'reasoning', items: [] };
    this.transcript.push(entry);
    this.reasoningItems = entry.items;

    const copyBtn = makeCopyButton({
      label: 'Copy agent reasoning',
      getText: () => reasoningText(entry.items),
      onError: (message) => this.appendError(`Copy reasoning failed: ${message}`),
    });

    // Visual only: the native <details> already exposes expanded state.
    const hint = document.createElement('span');
    hint.className = 'mantis-activity-hint';
    hint.setAttribute('aria-hidden', 'true');
    hint.textContent = 'Hide';
    details.addEventListener('toggle', () => {
      hint.textContent = details.open ? 'Hide' : 'Show';
    });

    summary.append(chevron, label, meta, copyBtn, hint);
    details.appendChild(summary);

    const body = document.createElement('div');
    body.className = 'mantis-activity-body';
    details.appendChild(body);

    this.logEl.appendChild(details);
    this.activityEl = details;
    this.activityBodyEl = body;
    this.activityMetaEl = meta;
    this.scroll();
    return body;
  }

  _paintActivityMeta() {
    if (!this.activityMetaEl) return;
    const parts = [];
    if (this.currentPhase) parts.push(this.currentPhase);
    parts.push(`${this.toolCallCount} tool call${this.toolCallCount === 1 ? '' : 's'}`);
    this.activityMetaEl.textContent = parts.join(' · ');
  }

  /** Keeps the newest activity in view without dragging the outer log along. */
  _scrollActivity() {
    if (this.activityBodyEl) {
      this.activityBodyEl.scrollTop = this.activityBodyEl.scrollHeight;
    }
  }

  // ------------------------------------------------------ elapsed timer ---
  _elapsedLabel() {
    if (!this.turnStartedAt) return '0:00';
    const seconds = Math.floor((Date.now() - this.turnStartedAt) / 1000);
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  }

  _startElapsedTimer() {
    this._stopElapsedTimer();
    this.elapsedEl.hidden = false;
    this.elapsedEl.textContent = this._elapsedLabel();
    this.elapsedTimer = window.setInterval(() => {
      this.elapsedEl.textContent = this._elapsedLabel();
    }, 1000);
  }

  _stopElapsedTimer() {
    if (this.elapsedTimer !== null) {
      window.clearInterval(this.elapsedTimer);
      this.elapsedTimer = null;
    }
  }

  setBusy(busy) {
    // While a run is active, Stop takes Send's place in the composer. Send stays
    // disabled as well: other components read it to detect a busy panel.
    this.sendBtn.disabled = busy;
    this.sendBtn.hidden = busy;
    this.stopBtn.hidden = !busy;
    this.stopBtn.disabled = false;

    if (busy) {
      this._startElapsedTimer();
      return;
    }

    this._stopElapsedTimer();
    // The final duration stays on screen: a 210s run is a fact about the run.
    this.elapsedEl.textContent = this._elapsedLabel();
    this.stream = null;
    if (this.activityEl) {
      this.activityEl.open = false;
      this._paintActivityMeta();
    }
  }

  // ---------------------------------------------------------- rendering ---
  /**
   * Bubble text lives in a `.chat-bubble-content` child so streaming and the
   * markdown render can replace it without removing the copy button.
   */
  appendBubble(role, text) {
    const bubble = document.createElement('div');
    bubble.className = `chat-bubble chat-${role}`;
    const content = document.createElement('div');
    content.className = 'chat-bubble-content';
    content.textContent = text;

    let getText;
    if (role === 'user') {
      this.transcript.push({ kind: 'user', text });
      getText = () => text;
    } else if (role === 'assistant') {
      getText = () => this.rawAnswers.get(bubble) ?? '';
    } else {
      throw new Error(`appendBubble: unsupported role '${role}'.`);
    }
    const copyBtn = makeCopyButton({
      label: 'Copy message',
      getText,
      onError: (message) => this.appendError(`Copy message failed: ${message}`),
      className: 'chat-bubble-copy',
    });

    bubble.append(content, copyBtn);
    this.logEl.appendChild(bubble);
    this.scroll();
    return bubble;
  }

  /**
   * Prose from the agent's reasoning channel. It is deliberately kept out of
   * the answer bubble: streaming the raw ReAct narration and the critic's audit
   * into the reply is what made the final response unreadable.
   */
  appendThought(text) {
    if (!text) return;
    const body = this._activityBody();
    if (!this.thoughtEl) {
      this.thoughtEl = document.createElement('pre');
      this.thoughtEl.className = 'mantis-thought';
      body.appendChild(this.thoughtEl);
      this.thoughtItem = { type: 'thought', text: '' };
      this.reasoningItems.push(this.thoughtItem);
    }
    this.thoughtEl.textContent += text;
    this.thoughtItem.text += text;
    this._scrollActivity();
  }

  appendToken(text) {
    if (!this.assistantBubble) {
      this.assistantBubble = this.appendBubble('assistant', '');
      this.assistantRawText = '';
      this.answerEntry = { kind: 'answer', raw: '' };
      this.transcript.push(this.answerEntry);
      // The answer has started; the reasoning trail steps back out of the way.
      if (this.activityEl) this.activityEl.open = false;
    }
    this.assistantRawText += text;
    this.answerEntry.raw = this.assistantRawText;
    this.rawAnswers.set(this.assistantBubble, this.assistantRawText);
    this.assistantBubble.querySelector('.chat-bubble-content').textContent = this.assistantRawText;
    this.scroll();
  }

  async finalizeAssistantBubble() {
    if (!this.assistantBubble || !this.assistantRawText) return;
    const bubble = this.assistantBubble;
    const raw = this.assistantRawText;
    this.assistantBubble = null;
    this.assistantRawText = '';
    await this.renderMarkdownAndDiagrams(bubble.querySelector('.chat-bubble-content'), raw);
    this.scroll();
  }

  escapeHtml(str) {
    return escapeHtml(str);
  }

  /** Repo doc citations (docs/*.md, schema/*.json) become links; see mantis-markdown.js. */
  formatInlineMarkdown(str) {
    return formatInlineMarkdown(str);
  }

  async renderMarkdownAndDiagrams(container, markdown) {
    if (!markdown) return;

    // 1. Stash code blocks and mermaid diagrams
    const codeBlocks = [];
    const text = markdown.replace(/```([a-zA-Z0-9_\-+]*)[ \t]*\n?([\s\S]*?)```/g, (match, lang, code) => {
      const cleanLang = (lang || '').trim().toLowerCase();
      const idx = codeBlocks.length;
      if (cleanLang === 'mermaid') {
        const diagId = 'mermaid-' + Math.random().toString(36).substring(2, 9);
        codeBlocks.push({
          type: 'mermaid',
          id: diagId,
          code: code.trim(),
          html: `
            <div class="mermaid-diagram-card" id="${diagId}">
              <div class="mermaid-header">
                <div class="mermaid-header-left">
                  <span class="material-symbols-outlined mermaid-icon">schema</span>
                  <span class="mermaid-title">Diagram</span>
                </div>
                <div class="mermaid-header-actions">
                  ${DIAGRAM_ACTIONS_HTML}
                  <button type="button" class="btn btn-ghost btn-sm btn-diagram-fullscreen" data-diag-id="${diagId}" title="View full-screen with zoom & pan">
                    <span class="material-symbols-outlined" style="font-size: 15px; vertical-align: middle;">fullscreen</span> Fullscreen
                  </button>
                </div>
              </div>
              <div class="mermaid-body">
                <div class="mermaid-loading">
                  <span class="spinner-inline"></span>
                  <span>Rendering sequence diagram...</span>
                </div>
              </div>
            </div>
          `,
        });
      } else {
        codeBlocks.push({
          type: 'code',
          html: `<pre class="chat-code-block"><code class="language-${this.escapeHtml(cleanLang)}">${this.escapeHtml(code.trim())}</code></pre>`,
        });
      }
      return `\n__CODE_BLOCK_${idx}__\n`;
    });

    // 2. Parse lines into structured HTML
    const lines = text.split(/\r?\n/);
    const htmlLines = [];
    let inList = false;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      const codeMatch = line.trim().match(/^__CODE_BLOCK_(\d+)__$/);
      if (codeMatch) {
        if (inList) {
          htmlLines.push('</ul>');
          inList = false;
        }
        htmlLines.push(line.trim());
        continue;
      }

      const listMatch = line.match(/^\s*[-*]\s+(.*)$/);
      if (listMatch) {
        if (!inList) {
          htmlLines.push('<ul class="chat-markdown-list">');
          inList = true;
        }
        htmlLines.push(`<li>${this.formatInlineMarkdown(listMatch[1])}</li>`);
        continue;
      } else if (inList) {
        htmlLines.push('</ul>');
        inList = false;
      }

      if (/^###\s+(.*)$/.test(line)) {
        htmlLines.push(`<h3>${this.formatInlineMarkdown(line.replace(/^###\s+/, ''))}</h3>`);
      } else if (/^##\s+(.*)$/.test(line)) {
        htmlLines.push(`<h2>${this.formatInlineMarkdown(line.replace(/^##\s+/, ''))}</h2>`);
      } else if (/^#\s+(.*)$/.test(line)) {
        htmlLines.push(`<h1>${this.formatInlineMarkdown(line.replace(/^#\s+/, ''))}</h1>`);
      } else if (line.trim() !== '') {
        htmlLines.push(`<p>${this.formatInlineMarkdown(line)}</p>`);
      }
    }

    if (inList) {
      htmlLines.push('</ul>');
    }

    let fullHtml = htmlLines.join('\n');
    fullHtml = fullHtml.replace(/__CODE_BLOCK_(\d+)__/g, (match, idx) => {
      const block = codeBlocks[Number(idx)];
      return block ? block.html : '';
    });

    container.innerHTML = fullHtml;

    // 3. Render Mermaid diagrams
    const mermaidBlocks = codeBlocks.filter((b) => b.type === 'mermaid');
    if (mermaidBlocks.length > 0) {
      if (!window.mermaid) {
        await new Promise((resolve) => {
          const script = document.createElement('script');
          script.src = '/js/vendor/mermaid.min.js';
          script.onload = () => resolve();
          script.onerror = () => resolve();
          document.head.appendChild(script);
        });
      }

      if (window.mermaid) {
        try {
          window.mermaid.initialize({
            startOnLoad: false,
            theme: 'default',
            securityLevel: 'loose',
            // SVG <text> labels instead of <foreignObject> HTML: foreignObject
            // taints the canvas and breaks PNG copy/download.
            htmlLabels: false,
            flowchart: { htmlLabels: false },
            fontFamily: 'Google Sans, Roboto, system-ui, sans-serif',
            themeVariables: {
              darkMode: false,
              background: '#ffffff',
              primaryColor: '#f0f4f9',
              primaryTextColor: '#1f1f1f',
              primaryBorderColor: '#0b57d0',
              lineColor: '#0b57d0',
              secondaryColor: '#e9eef6',
              tertiaryColor: '#ffffff',
              noteBkgColor: '#fce8e6',
              noteTextColor: '#b3261e',
              noteBorderColor: '#b3261e',
              actorBkg: '#f0f4f9',
              actorTextColor: '#0b57d0',
              actorBorder: '#0b57d0',
              actorLineColor: '#5f6368',
              signalColor: '#1f1f1f',
              signalTextColor: '#1f1f1f',
              labelBoxBkgColor: '#f0f4f9',
              labelBoxBorderColor: '#0b57d0',
              labelTextColor: '#1f1f1f',
            },
          });
        } catch (_) {}

        for (const block of mermaidBlocks) {
          const cardEl = container.querySelector(`#${block.id}`);
          const bodyEl = cardEl?.querySelector('.mermaid-body');
          if (!bodyEl) continue;
          try {
            const svgId = block.id + '-svg';
            const res = await window.mermaid.render(svgId, block.code);
            bodyEl.innerHTML = `<div class="mermaid-svg-wrapper">${res.svg}</div>`;
            wireDiagramActions(
              cardEl.querySelector('.mermaid-header-actions'),
              () => bodyEl.querySelector('svg'),
              (message) => this.appendError(`Diagram export failed: ${message}`),
            );
            const fsBtn = cardEl?.querySelector('.btn-diagram-fullscreen');
            if (fsBtn) {
              fsBtn.addEventListener('click', () => {
                const svg = bodyEl.querySelector('svg');
                if (svg) {
                  this.openDiagramModal(svg.outerHTML);
                }
              });
            }
          } catch (err) {
            console.warn('Failed to render Mermaid diagram:', err);
            bodyEl.innerHTML = `<pre class="mermaid-fallback"><code class="language-mermaid">${this.escapeHtml(block.code)}</code></pre>`;
            // No SVG exists to export; the source above is the diagram.
            for (const btn of cardEl.querySelectorAll('[data-diagram-act]')) btn.hidden = true;
          }
        }
      }
    }
  }

  /**
   * Clears the server-side history and the transcript together. A failed clear
   * leaves both intact: wiping the visible transcript while the agent still
   * holds the history would misreport what the next turn is conditioned on.
   */
  async clearChat() {
    this.stream?.abort();
    try {
      const response = await fetch('/api/mantis/chat/clear', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_id: SESSION_ID }),
      });
      if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
      }
    } catch (cause) {
      this.appendError(
        `Chat history was NOT cleared: POST /api/mantis/chat/clear failed with ${cause.message}. ` +
        'The agent still holds the previous turns.'
      );
      return;
    }
    this.logEl.innerHTML = '';
    this.transcript = [];
    this._beginTurn();
    this.elapsedEl.hidden = true;
    this.appendNote('Conversation history cleared. Workspace context preserved.');
  }

  openDiagramModal(svgHtml) {
    if (!this.diagramModal) return;
    this.diagramCanvas.innerHTML = svgHtml;
    this.diagramModal.hidden = false;
    this.diagramModalBody.focus();

    // Auto-fit diagram to screen size comfortably
    requestAnimationFrame(() => {
      const svg = this.diagramCanvas.querySelector('svg');
      if (svg && this.diagramModalBody) {
        const bbox = svg.viewBox?.baseVal;
        const w = bbox?.width || svg.clientWidth || svg.getBoundingClientRect().width;
        const h = bbox?.height || svg.clientHeight || svg.getBoundingClientRect().height;
        const bodyW = this.diagramModalBody.clientWidth - 80;
        const bodyH = this.diagramModalBody.clientHeight - 80;
        if (w > 0 && h > 0 && bodyW > 0 && bodyH > 0) {
          const scaleX = bodyW / w;
          const scaleY = bodyH / h;
          const fitScale = Math.min(scaleX, scaleY);
          this.diagramZoom = Math.min(Math.max(Math.round(fitScale * 10) / 10, 1.0), 3.0);
        } else {
          this.diagramZoom = 1.5;
        }
      } else {
        this.diagramZoom = 1.5;
      }
      this.updateDiagramZoom();
    });
  }

  closeDiagramModal() {
    if (!this.diagramModal) return;
    this.diagramModal.hidden = true;
    this.diagramCanvas.innerHTML = '';
  }

  updateDiagramZoom() {
    if (!this.diagramCanvas) return;
    this.diagramCanvas.style.transform = `scale(${this.diagramZoom})`;
    if (this.zoomLevelEl) {
      this.zoomLevelEl.textContent = `${Math.round(this.diagramZoom * 100)}%`;
    }
  }

  setZoom(val) {
    this.diagramZoom = Math.min(Math.max(val, 0.3), 3.5);
    this.updateDiagramZoom();
  }

  appendPhase(phase) {
    const body = this._activityBody();
    const el = document.createElement('div');
    el.className = 'chat-phase';
    el.textContent = phase;
    body.appendChild(el);

    this.reasoningItems.push({ type: 'phase', text: phase });
    this.currentPhase = phase;
    // A new heading starts a new prose block, so the trail stays chronological.
    this.thoughtEl = null;
    this.thoughtItem = null;
    this._paintActivityMeta();
    this._scrollActivity();
  }

  appendToolCall(data) {
    const body = this._activityBody();
    const el = document.createElement('details');
    el.className = 'chat-tool';
    const summary = document.createElement('summary');
    summary.textContent = `${data.tool}()`;
    el.appendChild(summary);

    const mount = document.createElement('div');
    el.appendChild(mount);
    new JSONViewer(mount).render(data.args ?? {});

    body.appendChild(el);
    this.reasoningItems.push({ type: 'tool_call', tool: data.tool, args: data.args ?? {} });
    this.toolCallCount += 1;
    this.thoughtEl = null;
    this.thoughtItem = null;
    this._paintActivityMeta();
    this._scrollActivity();
  }

  appendToolResult(data) {
    const body = this._activityBody();
    const el = document.createElement('details');
    el.className = 'chat-tool chat-tool-result';
    const summary = document.createElement('summary');
    summary.textContent = `${data.tool} → ${data.summary}`;
    el.appendChild(summary);

    const mount = document.createElement('div');
    el.appendChild(mount);
    new JSONViewer(mount).render(data.output ?? {});

    body.appendChild(el);
    this.reasoningItems.push({
      type: 'tool_result', tool: data.tool, summary: data.summary, output: data.output ?? {},
    });
    this.thoughtEl = null;
    this.thoughtItem = null;
    this._scrollActivity();
  }

  /**
   * The matrix arrives twice: a provisional one at scoping time with every
   * verdict UNRESOLVED, then the audited one. The second replaces the first —
   * two tables in the transcript read as two sets of conclusions. The audited
   * one is placed below the answer and collapsed: the answer is the device-facing
   * report, and the audit is its supporting record, not its headline. It carries
   * the answer's trailing "Hypothesis Resolution Audit" section, which the
   * adapter split off the answer, rendered inside the same disclosure.
   */
  appendMatrix(data) {
    const isFinal = data.final === true;
    const details = document.createElement('details');
    details.className = `mantis-matrix ${isFinal ? 'is-final' : 'is-provisional'}`;
    details.open = false;

    const summary = document.createElement('summary');
    summary.className = 'mantis-matrix-summary';
    const chevron = icon('expand_more');
    chevron.classList.add('mantis-matrix-chevron');
    const label = document.createElement('span');
    label.className = 'mantis-matrix-label';
    label.textContent = isFinal
      ? 'Hypothesis Resolution Audit'
      : 'Hypotheses under investigation — no verdicts reached yet';
    const meta = document.createElement('span');
    meta.className = 'mantis-matrix-meta';
    meta.textContent = this._matrixMeta(data.hypotheses || [], isFinal);
    summary.append(chevron, label, meta);
    details.appendChild(summary);

    const table = document.createElement('table');
    table.className = 'hypothesis-matrix';
    table.innerHTML =
      '<thead><tr><th>Hypothesis</th><th>Verdict</th><th>Rationale</th><th>Evidence</th></tr></thead>';

    const body = document.createElement('tbody');
    for (const item of data.hypotheses || []) {
      body.appendChild(this._matrixRow(item));
    }
    table.appendChild(body);
    details.appendChild(table);

    if (isFinal && data.audit_markdown) {
      const audit = document.createElement('div');
      audit.className = 'mantis-matrix-audit chat-bubble-content';
      details.appendChild(audit);
      this.renderMarkdownAndDiagrams(audit, data.audit_markdown);
    }

    if (this.matrixEl) {
      this.matrixEl.remove();
      this.matrixEntry.data = data;
      // Re-queue the entry so the transcript order matches the DOM order.
      this.transcript.splice(this.transcript.indexOf(this.matrixEntry), 1);
      this.transcript.push(this.matrixEntry);
    } else {
      this.matrixEntry = { kind: 'matrix', data };
      this.transcript.push(this.matrixEntry);
    }
    this.logEl.appendChild(details);
    this.matrixEl = details;
    this.scroll();
  }

  _matrixMeta(hypotheses, isFinal) {
    const count = `${hypotheses.length} hypothes${hypotheses.length === 1 ? 'is' : 'es'}`;
    if (!isFinal) return count;
    const primary = hypotheses.filter((h) => h.verdict === 'PRIMARY').length;
    return `${count} · ${primary} PRIMARY`;
  }

  _matrixRow(item) {
    const row = document.createElement('tr');
    const verdict = item.verdict;
    const tone = VERDICT_TONE[verdict];
    if (tone === 'refuted') row.className = 'row-refuted';

    const statement = document.createElement('td');
    if (item.hypothesis) {
      statement.textContent = item.hypothesis;
    } else {
      statement.className = 'mantis-missing';
      statement.textContent = 'Hypothesis text missing from the backend payload.';
    }

    const verdictCell = document.createElement('td');
    const badge = document.createElement('span');
    if (tone) {
      badge.className = `verdict-badge verdict-${tone}`;
      badge.textContent = verdict;
    } else {
      // Anything outside HYPOTHESIS_VERDICTS is a contract break. Show the raw
      // value rather than folding it into a neutral badge that hides the break.
      badge.className = 'verdict-badge verdict-unknown';
      badge.textContent = verdict ? String(verdict) : 'NO VERDICT';
      badge.title = `Not one of ${Object.keys(VERDICT_TONE).join(', ')}.`;
    }
    verdictCell.appendChild(badge);

    const rationale = document.createElement('td');
    if (item.rationale) {
      // formatInlineMarkdown escapes everything; doc citations become links.
      rationale.innerHTML = formatInlineMarkdown(item.rationale);
    } else {
      rationale.textContent = '—';
    }

    const evidence = document.createElement('td');
    evidence.className = 'mantis-evidence-tier';
    evidence.textContent = item.evidence_tier || '—';

    row.append(statement, verdictCell, rationale, evidence);
    return row;
  }

  /**
   * Errors are reproduced exactly as the backend worded them. They routinely
   * name a missing credential or an unreachable service, which is the only
   * thing that tells the operator what to go and fix.
   */
  appendError(message) {
    const box = document.createElement('div');
    box.className = 'mantis-error';

    const head = document.createElement('div');
    head.className = 'mantis-error-head';
    const icon = document.createElement('span');
    icon.className = 'material-symbols-outlined';
    icon.setAttribute('aria-hidden', 'true');
    icon.textContent = 'error';
    const title = document.createElement('span');
    title.textContent = 'Mantis stream error';
    head.append(icon, title);

    const body = document.createElement('pre');
    body.className = 'mantis-error-body';
    body.textContent = message
      ? String(message)
      : 'The backend emitted an error event with no message field.';

    box.append(head, body);
    this.logEl.appendChild(box);
    this.transcript.push({ kind: 'error', text: body.textContent });
    this.scroll();
  }

  appendNote(text) {
    const el = document.createElement('p');
    el.className = 'empty-note';
    el.textContent = text;
    this.logEl.appendChild(el);
    this.transcript.push({ kind: 'note', text });
    this.scroll();
  }

  scroll() {
    this.logEl.scrollTop = this.logEl.scrollHeight;
  }
}
