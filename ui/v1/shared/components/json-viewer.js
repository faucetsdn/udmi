/**
 * JSONViewer Component
 * An interactive, collapsible JSON tree rendering component.
 * Features customizable M3 syntax highlighting and node toggling.
 */
export class JSONViewer {
  /**
   * @param {HTMLElement} container - The element where the JSON viewer will render
   */
  constructor(container) {
    this.container = container;
    this.initDOM();
  }

  initDOM() {
    this.container.classList.add('json-viewer-wrapper');
    this.container.innerHTML = `
      <style>
        .json-viewer-wrapper {
          font-family: var(--font-mono);
          font-size: 13px;
          line-height: 1.6;
          color: var(--text-primary);
          overflow: auto;
          height: 100%;
          padding: 12px;
          background-color: var(--bg-surface);
        }
        .json-node {
          margin-left: 20px;
          position: relative;
        }
        .json-expanded::before {
          content: '▼';
          position: absolute;
          left: -15px;
          top: 2px;
          font-size: 10px;
          color: var(--text-muted);
          cursor: pointer;
          user-select: none;
        }
        .json-collapsed::before {
          content: '▶';
          position: absolute;
          left: -15px;
          top: 2px;
          font-size: 10px;
          color: var(--text-muted);
          cursor: pointer;
          user-select: none;
        }
        .json-key {
          color: var(--text-secondary);
          font-weight: 500;
          margin-right: 4px;
        }
        .json-string {
          color: var(--color-tertiary);
          word-break: break-all;
        }
        .json-number {
          color: var(--color-primary);
        }
        .json-boolean {
          color: var(--color-warning);
          font-weight: 500;
        }
        .json-null {
          color: var(--text-muted);
          font-style: italic;
        }
        .json-bracket {
          color: var(--text-primary);
          font-weight: bold;
        }
        .json-collapsed > .json-collapsed-content {
          display: none;
        }
        .json-toggle {
          cursor: pointer;
          user-select: none;
        }
        .json-toggle:hover {
          background-color: var(--bg-surface-container);
        }
      </style>
      <div class="json-content"></div>
    `;
    this.contentEl = this.container.querySelector('.json-content');
  }

  /**
   * Render JSON object
   * @param {Object|Array} data - The JSON data to render
   */
  render(data) {
    this.contentEl.innerHTML = '';
    this.contentEl.appendChild(this.parseValue(null, data, true));
  }

  parseValue(key, value, isLast = true) {
    const wrapper = document.createElement('div');
    wrapper.className = 'json-line';

    // 1. Render Key (if present)
    if (key !== null) {
      const keySpan = document.createElement('span');
      keySpan.className = 'json-key';
      keySpan.textContent = `"${key}": `;
      wrapper.appendChild(keySpan);
    }

    // 2. Render Value based on type
    if (value === null) {
      const nullSpan = document.createElement('span');
      nullSpan.className = 'json-null';
      nullSpan.textContent = 'null' + (isLast ? '' : ',');
      wrapper.appendChild(nullSpan);
    } else if (typeof value === 'object') {
      const isArray = Array.isArray(value);
      const openBracket = isArray ? '[' : '{';
      const closeBracket = isArray ? ']' : '}';
      const keys = Object.keys(value);

      const nodeEl = document.createElement('span');
      nodeEl.className = 'json-node json-expanded';
      
      const openEl = document.createElement('span');
      openEl.className = 'json-bracket json-toggle';
      openEl.textContent = openBracket;
      wrapper.appendChild(openEl);

      const contentEl = document.createElement('div');
      contentEl.className = 'json-collapsed-content';

      // Render children
      keys.forEach((k, idx) => {
        const lastChild = idx === keys.length - 1;
        contentEl.appendChild(this.parseValue(isArray ? null : k, value[k], lastChild));
      });

      nodeEl.appendChild(contentEl);
      
      const closeEl = document.createElement('span');
      closeEl.className = 'json-bracket';
      closeEl.textContent = (keys.length > 0 ? '\n' : '') + closeBracket + (isLast ? '' : ',');
      nodeEl.appendChild(closeEl);

      wrapper.appendChild(nodeEl);

      // Expand/Collapse click handler
      openEl.addEventListener('click', (e) => {
        e.stopPropagation();
        nodeEl.classList.toggle('json-expanded');
        nodeEl.classList.toggle('json-collapsed');
      });

    } else if (typeof value === 'string') {
      const stringSpan = document.createElement('span');
      stringSpan.className = 'json-string';
      stringSpan.textContent = `"${value}"` + (isLast ? '' : ',');
      wrapper.appendChild(stringSpan);
    } else if (typeof value === 'number') {
      const numberSpan = document.createElement('span');
      numberSpan.className = 'json-number';
      numberSpan.textContent = value.toString() + (isLast ? '' : ',');
      wrapper.appendChild(numberSpan);
    } else if (typeof value === 'boolean') {
      const boolSpan = document.createElement('span');
      boolSpan.className = 'json-boolean';
      boolSpan.textContent = value.toString() + (isLast ? '' : ',');
      wrapper.appendChild(boolSpan);
    }

    return wrapper;
  }
}
