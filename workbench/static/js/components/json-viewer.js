/**
 * Layer 1 — Collapsible JSON tree.
 *
 * Ported from v1's viewer. Two changes: the v1 version injected a <style> block
 * into every instance (duplicating CSS per mount), and it rendered a literal
 * '\n' into a closing-bracket text node which showed up as a stray character.
 * Styling now lives in theme.css and the bracket layout is handled by CSS.
 */

const COLLAPSE_THRESHOLD = 50;

export class JSONViewer {
  constructor(container) {
    this.container = container;
    this.container.classList.add('json-viewer');
  }

  /** Replaces the tree with a rendering of `data`. */
  render(data) {
    this.container.textContent = '';
    this.container.appendChild(this._value(null, data, true, 0));
  }

  _value(key, value, isLast, depth) {
    const line = document.createElement('div');
    line.className = 'json-line';

    if (key !== null) {
      const keyEl = document.createElement('span');
      keyEl.className = 'json-key';
      keyEl.textContent = `"${key}": `;
      line.appendChild(keyEl);
    }

    if (value !== null && typeof value === 'object') {
      line.appendChild(this._branch(value, isLast, depth));
    } else {
      line.appendChild(this._leaf(value, isLast));
    }
    return line;
  }

  _leaf(value, isLast) {
    const suffix = isLast ? '' : ',';
    const el = document.createElement('span');

    if (value === null) {
      el.className = 'json-null';
      el.textContent = `null${suffix}`;
    } else if (typeof value === 'string') {
      el.className = 'json-string';
      el.textContent = `"${value}"${suffix}`;
    } else if (typeof value === 'number') {
      el.className = 'json-number';
      el.textContent = `${value}${suffix}`;
    } else if (typeof value === 'boolean') {
      el.className = 'json-boolean';
      el.textContent = `${value}${suffix}`;
    } else {
      el.className = 'json-null';
      el.textContent = `undefined${suffix}`;
    }
    return el;
  }

  _branch(value, isLast, depth) {
    const isArray = Array.isArray(value);
    const keys = Object.keys(value);
    const open = isArray ? '[' : '{';
    const close = isArray ? ']' : '}';

    const node = document.createElement('span');
    // Large nodes start collapsed so a big metadata blob is navigable, not a wall.
    const startCollapsed = depth > 0 && keys.length > COLLAPSE_THRESHOLD;
    node.className = `json-node ${startCollapsed ? 'json-collapsed' : 'json-expanded'}`;

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'json-toggle';
    toggle.setAttribute('aria-expanded', String(!startCollapsed));
    toggle.textContent = open;

    const summary = document.createElement('span');
    summary.className = 'json-summary';
    summary.textContent = `${keys.length} ${keys.length === 1 ? 'entry' : 'entries'}`;

    const children = document.createElement('div');
    children.className = 'json-children';
    keys.forEach((childKey, index) => {
      children.appendChild(
        this._value(isArray ? null : childKey, value[childKey], index === keys.length - 1, depth + 1)
      );
    });

    const closer = document.createElement('span');
    closer.className = 'json-bracket';
    closer.textContent = `${close}${isLast ? '' : ','}`;

    toggle.addEventListener('click', () => {
      const collapsed = node.classList.toggle('json-collapsed');
      node.classList.toggle('json-expanded', !collapsed);
      toggle.setAttribute('aria-expanded', String(!collapsed));
    });

    node.append(toggle, summary, children, closer);
    return node;
  }
}
