/**
 * Layer 1 — Streaming log terminal.
 *
 * Ported from the v1 viewer, which was the strongest part of that UI: a bounded
 * ring buffer with matching DOM trimming, HTML escaping, and smart auto-scroll
 * that releases when the user scrolls up and re-engages at the bottom.
 */

const DEFAULT_MAX_LINES = 2000;
const BOTTOM_THRESHOLD_PX = 32;

function classify(line) {
  const text = line.toLowerCase();
  if (/\b(error|fatal|exception)\b/.test(text)) return 'error';
  if (/^\s*result\s+fail\b/.test(text) || /\bwarn(ing)?\b/.test(text)) return 'warn';
  if (/^\s*result\s+pass\b/.test(text)) return 'success';
  if (/^\s*result\s+skip\b/.test(text)) return 'muted';
  if (/\bdebug\b/.test(text)) return 'debug';
  return 'info';
}

export class LogViewer {
  constructor(container, { maxLines = DEFAULT_MAX_LINES, initialLines = [] } = {}) {
    this.container = container;
    this.maxLines = maxLines;
    this.lines = [];
    this.filter = '';
    this.autoScroll = true;
    this._programmaticScroll = false;
    this._render();
    if (initialLines && initialLines.length > 0) {
      this.setLines(initialLines);
    }
  }

  _render() {
    this.container.innerHTML = `
      <div class="log-toolbar">
        <span class="log-title">Console Output</span>
        <input type="search" class="log-filter" placeholder="Filter output..." aria-label="Filter console output" />
        <button type="button" class="btn btn-ghost" data-act="copy">Copy</button>
        <button type="button" class="btn btn-ghost" data-act="clear">Clear</button>
        <button type="button" class="btn btn-ghost" data-act="scroll" aria-pressed="true">Follow: On</button>
      </div>
      <div class="log-body" role="log" aria-live="polite" tabindex="0"></div>
    `;
    this.body = this.container.querySelector('.log-body');
    this.filterInput = this.container.querySelector('.log-filter');
    this.scrollBtn = this.container.querySelector('[data-act="scroll"]');

    this.filterInput.addEventListener('input', (event) => {
      this.filter = event.target.value.toLowerCase();
      this._rebuild();
    });
    this.container.querySelector('[data-act="copy"]').addEventListener('click', () => this._copy());
    this.container.querySelector('[data-act="clear"]').addEventListener('click', () => this.clear());
    this.scrollBtn.addEventListener('click', () => this._setAutoScroll(!this.autoScroll));

    this.body.addEventListener('scroll', () => {
      if (this._programmaticScroll) return;
      const distance = this.body.scrollHeight - this.body.scrollTop - this.body.clientHeight;
      this._setAutoScroll(distance <= BOTTOM_THRESHOLD_PX);
    });
  }

  /** Appends a block of raw console text, splitting and classifying each line. */
  appendChunk(text) {
    const incoming = text.split('\n').filter((line) => line.length > 0);
    if (incoming.length === 0) return;

    const fragment = document.createDocumentFragment();
    for (const raw of incoming) {
      const entry = { text: raw, level: classify(raw) };
      this.lines.push(entry);
      if (this._matchesFilter(entry)) fragment.appendChild(this._lineElement(entry));
    }

    this.body.appendChild(fragment);
    this._trim();
    this._scrollToBottomIfFollowing();
  }

  /** Appends a viewer-generated notice (command echo, errors, lifecycle). */
  appendNotice(text, level = 'notice') {
    const entry = { text, level };
    this.lines.push(entry);
    if (this._matchesFilter(entry)) this.body.appendChild(this._lineElement(entry));
    this._trim();
    this._scrollToBottomIfFollowing();
  }

  clear() {
    this.lines = [];
    this.body.textContent = '';
  }

  getLines() {
    return this.lines;
  }

  setLines(lines) {
    this.lines = [...lines];
    this._rebuild();
    this._scrollToBottomIfFollowing();
  }

  _matchesFilter(entry) {
    return !this.filter || entry.text.toLowerCase().includes(this.filter);
  }

  _lineElement(entry) {
    const div = document.createElement('div');
    div.className = `log-line log-${entry.level}`;
    div.textContent = entry.text;
    return div;
  }

  _rebuild() {
    const fragment = document.createDocumentFragment();
    for (const entry of this.lines) {
      if (this._matchesFilter(entry)) fragment.appendChild(this._lineElement(entry));
    }
    this.body.textContent = '';
    this.body.appendChild(fragment);
    this._scrollToBottomIfFollowing();
  }

  _trim() {
    const excess = this.lines.length - this.maxLines;
    if (excess <= 0) return;
    this.lines.splice(0, excess);
    for (let i = 0; i < excess && this.body.firstChild; i += 1) {
      this.body.removeChild(this.body.firstChild);
    }
  }

  _setAutoScroll(enabled) {
    if (this.autoScroll === enabled) return;
    this.autoScroll = enabled;
    this.scrollBtn.textContent = enabled ? 'Follow: On' : 'Follow: Off';
    this.scrollBtn.setAttribute('aria-pressed', String(enabled));
    if (enabled) this._scrollToBottomIfFollowing();
  }

  _scrollToBottomIfFollowing() {
    if (!this.autoScroll) return;
    this._programmaticScroll = true;
    this.body.scrollTop = this.body.scrollHeight;
    requestAnimationFrame(() => {
      this._programmaticScroll = false;
    });
  }

  async _copy() {
    const visible = this.lines.filter((entry) => this._matchesFilter(entry));
    const payload = visible.map((entry) => entry.text).join('\n');
    const button = this.container.querySelector('[data-act="copy"]');
    try {
      await navigator.clipboard.writeText(payload);
      button.textContent = `Copied ${visible.length}`;
    } catch {
      button.textContent = 'Copy blocked';
    }
    setTimeout(() => {
      button.textContent = 'Copy';
    }, 1500);
  }
}
