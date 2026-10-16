/**
 * Layer 1 — Searchable device combobox.
 *
 * Replaces a plain <select> for device choice. A site model can hold more
 * than 11,000 devices; a native select of that size is slow to
 * build and impossible to navigate. This follows the WAI-ARIA 1.2 combobox
 * pattern (editable input + listbox popup, list autocomplete):
 *
 *   - type to filter (case-insensitive substring on id and label);
 *   - ArrowDown / ArrowUp move the active option, opening the list if closed;
 *   - Enter selects the active option; Escape closes and restores the
 *     current selection; Tab / blur commit only an exact id match;
 *   - at most RENDER_LIMIT options are rendered, with a live status line
 *     saying how many matched, so the DOM stays small on any site size.
 *
 * Pure presentational component: options and selection come in via props and
 * the only output is `onSelect(value)`.
 *
 * Props: { id: string, label: string, onSelect: (value: string) => void }
 * Methods: setOptions(options: {value, label}[], emptyLabel: string)
 * Accessors: value (get/set, no event), disabled (set)
 */

export const RENDER_LIMIT = 200;

export class DeviceCombobox {
  constructor(container, { id, label, onSelect }) {
    this.id = id;
    this.onSelect = onSelect;
    this.options = [];
    this.filtered = [];
    this.activeIndex = -1;
    this._value = '';
    this.emptyLabel = '';

    const listId = `${id}-listbox`;
    this.root = document.createElement('div');
    this.root.className = 'combobox';
    this.root.innerHTML = `
      <input id="${id}" type="text" role="combobox" aria-autocomplete="list"
             aria-expanded="false" aria-controls="${listId}"
             autocomplete="off" spellcheck="false" />
      <ul id="${listId}" class="combobox-list" role="listbox" aria-label="${label}" hidden></ul>
      <p class="combobox-status" aria-live="polite"></p>
    `;
    container.appendChild(this.root);

    this.input = this.root.querySelector('input');
    this.list = this.root.querySelector('ul');
    this.status = this.root.querySelector('.combobox-status');

    this.input.addEventListener('focus', () => {
      this.input.select();
      this._open();
    });
    this.input.addEventListener('click', () => this._open());
    this.input.addEventListener('input', () => this._open({ resetActive: true }));
    this.input.addEventListener('keydown', (event) => this._onKeyDown(event));
    this.input.addEventListener('blur', () => this._commitTyped());
    // Keep focus in the input while an option is clicked, so blur does not
    // close the list before the click lands.
    this.list.addEventListener('mousedown', (event) => event.preventDefault());
    this.list.addEventListener('click', (event) => {
      const item = event.target.closest('[role="option"]');
      if (item) this._choose(item.dataset.value);
    });
  }

  /** Replaces the option set. A selection no longer present is cleared. */
  setOptions(options, emptyLabel) {
    this.options = options.map((option) => ({
      ...option,
      haystack: `${option.value} ${option.label}`.toLowerCase(),
    }));
    this.emptyLabel = emptyLabel;
    if (!this.options.some((option) => option.value === this._value)) this._value = '';
    this.input.value = this._value;
    this.input.placeholder = this.options.length
      ? `Type to search ${this.options.length} device${this.options.length === 1 ? '' : 's'}…`
      : emptyLabel;
    this._close();
  }

  get value() {
    return this._value;
  }

  /** Mirrors external state without emitting onSelect. */
  set value(next) {
    const value = next || '';
    this._value = this.options.some((option) => option.value === value) ? value : '';
    if (document.activeElement !== this.input) this.input.value = this._value;
  }

  set disabled(flag) {
    this.input.disabled = flag;
    if (flag) this._close();
  }

  // ----------------------------------------------------------- internals ---
  _query() {
    const text = this.input.value.trim();
    // Reopening on the current selection should show everything, not just it.
    return text === this._value ? '' : text.toLowerCase();
  }

  _open({ resetActive = false } = {}) {
    if (this.input.disabled) return;
    const query = this._query();
    this.filtered = query
      ? this.options.filter((option) => option.haystack.includes(query))
      : this.options;
    if (resetActive || this.activeIndex >= this.filtered.length) {
      this.activeIndex = this.filtered.length ? 0 : -1;
    }
    if (this.activeIndex < 0 && !resetActive) {
      const selected = this.filtered.findIndex((option) => option.value === this._value);
      this.activeIndex = selected >= 0 && selected < RENDER_LIMIT ? selected : this.filtered.length ? 0 : -1;
    }
    this._render(query);
    this.list.hidden = false;
    this.input.setAttribute('aria-expanded', 'true');
  }

  _close() {
    this.list.hidden = true;
    this.list.textContent = '';
    this.status.textContent = '';
    this.activeIndex = -1;
    this.input.setAttribute('aria-expanded', 'false');
    this.input.removeAttribute('aria-activedescendant');
  }

  _render(query) {
    this.list.textContent = '';
    const shown = this.filtered.slice(0, RENDER_LIMIT);
    shown.forEach((option, index) => {
      const item = document.createElement('li');
      item.id = `${this.id}-opt-${index}`;
      item.setAttribute('role', 'option');
      item.className = 'combobox-option';
      item.dataset.value = option.value;
      item.textContent = option.label;
      item.setAttribute('aria-selected', String(option.value === this._value));
      if (index === this.activeIndex) item.classList.add('is-active');
      this.list.appendChild(item);
    });
    this._syncActive();

    const total = this.filtered.length;
    if (!this.options.length) {
      this.status.textContent = this.emptyLabel;
    } else if (!total) {
      this.status.textContent = `No device matches "${query}".`;
    } else if (total > RENDER_LIMIT) {
      this.status.textContent =
        `Showing first ${RENDER_LIMIT} of ${total} matches — keep typing to narrow.`;
    } else {
      this.status.textContent = `${total} match${total === 1 ? '' : 'es'}.`;
    }
  }

  _syncActive() {
    for (const item of this.list.children) item.classList.remove('is-active');
    const active = this.activeIndex >= 0 ? this.list.children[this.activeIndex] : null;
    if (active) {
      active.classList.add('is-active');
      this.input.setAttribute('aria-activedescendant', active.id);
      active.scrollIntoView({ block: 'nearest' });
    } else {
      this.input.removeAttribute('aria-activedescendant');
    }
  }

  _move(delta) {
    const count = Math.min(this.filtered.length, RENDER_LIMIT);
    if (!count) return;
    this.activeIndex = Math.max(0, Math.min(count - 1, this.activeIndex + delta));
    this._syncActive();
  }

  _onKeyDown(event) {
    const open = !this.list.hidden;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!open) this._open();
      else this._move(1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) this._open();
      else this._move(-1);
    } else if (event.key === 'Enter') {
      if (open && this.activeIndex >= 0) {
        event.preventDefault();
        this._choose(this.filtered[this.activeIndex].value);
      }
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault();
        this.input.value = this._value;
        this._close();
      }
    }
  }

  /** On blur, only an exact id match becomes the selection; anything else reverts. */
  _commitTyped() {
    const typed = this.input.value.trim();
    this._close();
    if (typed !== this._value && this.options.some((option) => option.value === typed)) {
      this._choose(typed);
      return;
    }
    this.input.value = this._value;
  }

  _choose(value) {
    const changed = value !== this._value;
    this._value = value;
    this.input.value = value;
    this._close();
    if (changed) this.onSelect(value);
  }
}
