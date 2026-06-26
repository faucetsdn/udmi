/**
 * LogViewer Component
 * A high-performance, vanilla JavaScript log-streaming component.
 * Supports auto-scroll, log-level highlighting, and filtering.
 */
export class LogViewer {
  /**
   * @param {HTMLElement} container - The element where the log viewer will render
   * @param {Object} options - Configuration options
   */
  constructor(container, options = {}) {
    this.container = container;
    this.options = {
      maxLines: options.maxLines || 1000,
      autoScroll: options.autoScroll !== undefined ? options.autoScroll : true,
      ...options
    };

    this.logs = [];
    this.filterText = '';
    this.initDOM();
  }

  initDOM() {
    this.container.classList.add('log-viewer-wrapper');
    this.container.innerHTML = `
      <style>
        .log-viewer-wrapper {
          display: flex;
          flex-direction: column;
          height: 100%;
          background-color: #1e1e1e;
          border-radius: var(--radius-sm);
          font-family: var(--font-mono);
          font-size: 13px;
          color: #f1f1f1;
        }
        .log-viewer-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 8px 12px;
          background-color: #2d2d2d;
          border-bottom: 1px solid #3c3c3c;
        }
        .log-viewer-controls {
          display: flex;
          gap: 8px;
        }
        .log-viewer-search {
          background: #1e1e1e;
          border: 1px solid #3c3c3c;
          border-radius: 4px;
          color: #f1f1f1;
          padding: 2px 8px;
          font-family: var(--font-mono);
          font-size: 12px;
          width: 180px;
        }
        .log-viewer-btn {
          background: transparent;
          border: 1px solid #3c3c3c;
          color: #ccc;
          padding: 2px 8px;
          font-size: 12px;
          cursor: pointer;
          border-radius: 4px;
          font-family: var(--font-mono);
        }
        .log-viewer-btn:hover {
          background: #3c3c3c;
          color: #fff;
        }
        .log-viewer-body {
          flex: 1;
          overflow-y: auto;
          padding: 12px;
        }
        .log-line {
          white-space: pre-wrap;
          word-break: break-all;
          line-height: 1.6;
          margin-bottom: 4px;
          border-left: 3px solid transparent;
          padding-left: 8px;
        }
        .log-line.info { border-color: var(--color-primary); color: #8ab4f8; }
        .log-line.warn { border-color: var(--color-warning); color: #ffb703; }
        .log-line.error { border-color: var(--color-error); color: #ff6b6b; background: rgba(179, 38, 30, 0.1); }
        .log-line.debug { border-color: #5f6368; color: #9aa0a6; }
        .log-line.success { border-color: var(--color-tertiary); color: #6dd58c; }
        .log-timestamp {
          color: #80868b;
          margin-right: 8px;
          user-select: text;
        }
      </style>
      <div class="log-viewer-header">
        <span style="font-weight: 500; color: #aaa;">STREAMING LOGS</span>
        <div class="log-viewer-controls">
          <input type="text" class="log-viewer-search" placeholder="Filter logs..." aria-label="Filter logs" />
          <button class="log-viewer-btn btn-copy">Copy Logs</button>
          <button class="log-viewer-btn btn-clear">Clear</button>
          <button class="log-viewer-btn btn-scroll">Lock Scroll</button>
        </div>
      </div>
      <div class="log-viewer-body"></div>
    `;

    this.body = this.container.querySelector('.log-viewer-body');
    this.searchInput = this.container.querySelector('.log-viewer-search');
    this.copyBtn = this.container.querySelector('.btn-copy');
    this.clearBtn = this.container.querySelector('.btn-clear');
    this.scrollBtn = this.container.querySelector('.btn-scroll');

    // Event listeners
    this.searchInput.addEventListener('input', (e) => this.setFilter(e.target.value));
    this.copyBtn.addEventListener('click', () => this.copyLogsToClipboard());
    this.clearBtn.addEventListener('click', () => this.clear());
    this.scrollBtn.addEventListener('click', () => this.toggleScrollLock());

    this.body.addEventListener('scroll', () => {
      // If user scrolls up, unlock scroll, otherwise lock it if scrolled to bottom
      const threshold = 15;
      const isAtBottom = this.body.scrollHeight - this.body.clientHeight - this.body.scrollTop < threshold;
      if (!isAtBottom && this.options.autoScroll) {
        this.setAutoScroll(false);
      } else if (isAtBottom && !this.options.autoScroll) {
        this.setAutoScroll(true);
      }
    });
  }

  /**
   * Append a log line
   * @param {string} text - The log message
   * @param {string} level - 'info', 'warn', 'error', 'debug', 'success'
   */
  append(text, level = 'info') {
    const timestamp = new Date().toISOString().split('T')[1].slice(0, 8); // hh:mm:ss
    const logEntry = { timestamp, text, level };
    
    this.logs.push(logEntry);
    if (this.logs.length > this.options.maxLines) {
      this.logs.shift();
    }

    if (this.matchesFilter(logEntry)) {
      this.renderLine(logEntry);
    }
  }

  renderLine(log) {
    const lineEl = document.createElement('div');
    lineEl.className = `log-line ${log.level}`;
    lineEl.innerHTML = `<span class="log-timestamp">[${log.timestamp}]</span>${this.escapeHTML(log.text)}`;
    this.body.appendChild(lineEl);

    // Enforce max DOM elements
    if (this.body.children.length > this.options.maxLines) {
      this.body.removeChild(this.body.firstChild);
    }

    if (this.options.autoScroll) {
      this.scrollToBottom();
    }
  }

  scrollToBottom() {
    this.body.scrollTop = this.body.scrollHeight;
  }

  setAutoScroll(value) {
    this.options.autoScroll = value;
    if (value) {
      this.scrollBtn.textContent = 'Lock Scroll';
      this.scrollBtn.style.borderColor = 'var(--color-primary)';
      this.scrollBtn.style.color = 'var(--color-primary)';
    } else {
      this.scrollBtn.textContent = 'Unlock Scroll';
      this.scrollBtn.style.borderColor = '#3c3c3c';
      this.scrollBtn.style.color = '#ccc';
    }
  }

  toggleScrollLock() {
    this.setAutoScroll(!this.options.autoScroll);
    if (this.options.autoScroll) {
      this.scrollToBottom();
    }
  }

  setFilter(text) {
    this.filterText = text.toLowerCase();
    this.rebuildLogs();
  }

  matchesFilter(log) {
    if (!this.filterText) return true;
    return log.text.toLowerCase().includes(this.filterText) || log.level.toLowerCase().includes(this.filterText);
  }

  rebuildLogs() {
    this.body.innerHTML = '';
    this.logs.forEach(log => {
      if (this.matchesFilter(log)) {
        this.renderLine(log);
      }
    });
  }

  clear() {
    this.logs = [];
    this.body.innerHTML = '';
  }

  copyLogsToClipboard() {
    if (this.logs.length === 0) return;
    const formattedText = this.logs
      .filter(log => this.matchesFilter(log))
      .map(log => `[${log.timestamp}] ${log.text}`)
      .join('\n');
    
    navigator.clipboard.writeText(formattedText).then(() => {
      const oldText = this.copyBtn.textContent;
      this.copyBtn.textContent = 'Copied!';
      this.copyBtn.style.borderColor = 'var(--color-tertiary)';
      this.copyBtn.style.color = 'var(--color-tertiary)';
      setTimeout(() => {
        this.copyBtn.textContent = oldText;
        this.copyBtn.style.borderColor = '#3c3c3c';
        this.copyBtn.style.color = '#ccc';
      }, 1500);
    });
  }

  escapeHTML(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
}
