/**
 * Layer 2 — Mantis drawer.
 *
 * Mantis used to be a routed tab, so asking about a failing run meant
 * navigating away from it. The drawer overlays whichever view is mounted and
 * keeps one long-lived MantisPanel alive across navigation, so a diagnosis
 * that takes minutes survives a trip to the Devices screen and back.
 *
 * The pull tab is the only affordance that is visible on every view; the
 * drawer itself is off-screen until opened.
 */

import { MantisPanel } from './mantis-panel.js';
import { store } from '../core/store.js';
import { attachResizer, clampSize } from './resizer.js';

/**
 * Narrower than this the hypothesis matrix loses its verdict column and the
 * sequence diagrams have to be scrolled in both axes to be read at all, which
 * is the complaint the widening is answering.
 */
const DRAWER_MIN_WIDTH_PX = 420;

/** The drawer leaves a sliver of the view underneath, so it never reads as a page. */
const VIEWPORT_MAX_FRACTION = 0.96;

export class MantisDrawer {
  constructor(mountEl) {
    if (!mountEl) {
      throw new Error(
        'MantisDrawer requires a mount element: [data-role="mantis-mount"] is missing from index.html.'
      );
    }
    this.mountEl = mountEl;
    this.rootEl = null;
    this.shellEl = null;
    this.tabEl = null;
    this.maximizeEl = null;
    this.maximizeIconEl = null;
    this.panel = null;
    this.isOpen = false;
    this.isMaximized = false;
    this.detachResizer = null;
    this.listeners = new AbortController();
  }

  /** Builds the drawer and binds its shortcuts. Safe to call more than once. */
  mount() {
    if (this.rootEl) return;

    this.mountEl.innerHTML = `
      <aside class="mantis-drawer" data-role="drawer" aria-label="Mantis triage assistant">
        <button type="button" class="mantis-pull-tab" data-role="pull-tab"
                aria-controls="mantis-drawer-shell" aria-expanded="false"
                title="Mantis triage (Ctrl+K / Cmd+K)">
          <span class="material-symbols-outlined mantis-pull-tab-icon" aria-hidden="true">neurology</span>
          <span class="mantis-pull-tab-label">MANTIS</span>
        </button>
        <div class="resize-handle resize-handle-x" data-role="resizer"></div>
        <div class="mantis-drawer-shell" id="mantis-drawer-shell" data-role="shell" aria-hidden="true" inert>
          <div class="mantis-drawer-header">
            <span class="material-symbols-outlined" aria-hidden="true">neurology</span>
            <h2 class="mantis-drawer-title">Mantis</h2>
            <span class="mantis-drawer-hint">Ctrl+K</span>
            <button type="button" class="btn-icon" data-act="maximize" aria-pressed="false"
                    aria-label="Maximize Mantis drawer" title="Maximize Mantis drawer">
              <span class="material-symbols-outlined" data-role="maximize-icon" aria-hidden="true">fullscreen</span>
            </button>
            <button type="button" class="btn-icon" data-act="close" aria-label="Close Mantis drawer">
              <span class="material-symbols-outlined" aria-hidden="true">close</span>
            </button>
          </div>
          <div class="mantis-drawer-body" data-role="panel-mount"></div>
        </div>
      </aside>
    `;

    this.rootEl = this.mountEl.querySelector('[data-role="drawer"]');
    this.shellEl = this.mountEl.querySelector('[data-role="shell"]');
    this.tabEl = this.mountEl.querySelector('[data-role="pull-tab"]');
    this.maximizeEl = this.mountEl.querySelector('[data-act="maximize"]');
    this.maximizeIconEl = this.mountEl.querySelector('[data-role="maximize-icon"]');

    this.tabEl.addEventListener('click', () => this.toggle());
    this.maximizeEl.addEventListener('click', () => this.toggleMaximized());
    this.mountEl.querySelector('[data-act="close"]').addEventListener('click', () => this.close());

    window.addEventListener('keydown', (event) => this._onKeydown(event), {
      signal: this.listeners.signal,
    });

    this._mountResizer();

    this.panel = new MantisPanel(this.mountEl.querySelector('[data-role="panel-mount"]'));
    this.panel.init();
  }

  /**
   * Bounds are taken from the live viewport, never from storage, so a width
   * chosen on a wide monitor cannot reopen as a drawer that buries the app on a
   * narrow one.
   */
  _drawerBounds() {
    const min = DRAWER_MIN_WIDTH_PX;
    // attachResizer refuses a degenerate range. On a viewport narrower than the
    // readable minimum the handle stays mounted and every drag clamps back to
    // the minimum, which is the honest outcome: there is no room to give.
    const max = Math.max(min + 1, Math.round(window.innerWidth * VIEWPORT_MAX_FRACTION));
    return { min, max };
  }

  _mountResizer() {
    const { min, max } = this._drawerBounds();
    const width = clampSize(store.getState().mantisDrawerWidth, min, max);
    this._applyWidth(width);

    this.detachResizer = attachResizer(this.mountEl.querySelector('[data-role="resizer"]'), {
      axis: 'x',
      // The drawer is pinned to the right edge, so its left edge moving left is
      // what makes it wider.
      direction: -1,
      label: 'Resize Mantis drawer',
      min,
      max,
      value: width,
      onResize: (target) => this._applyWidth(clampSize(target, min, max)),
      onCommit: (applied) =>
        store.update('layout.mantisDrawerWidth', { mantisDrawerWidth: applied }),
    });
  }

  /**
   * Written on the document element, the same way the Local Test Setup drawer
   * publishes its width: the property is the single source of the drawer's
   * size, and the maximised rule in drawers.css overrides the width without
   * disturbing it, so restoring needs no saved copy of the old geometry.
   */
  _applyWidth(width) {
    document.documentElement.style.setProperty(
      '--mantis-drawer-width',
      `${Math.round(width)}px`
    );
    return width;
  }

  open() {
    if (this.isOpen) return;
    // Local Test Setup shares this edge; the shell decides who gets it.
    window.workbenchApp?.claimRightEdge?.(this);
    this.isOpen = true;
    this.rootEl.classList.add('is-open');
    this.shellEl.removeAttribute('inert');
    this.shellEl.setAttribute('aria-hidden', 'false');
    this.tabEl.setAttribute('aria-expanded', 'true');
    this.panel.focusComposer();
  }

  close() {
    if (!this.isOpen) return;
    this.isOpen = false;
    // Focus has to leave before the shell goes inert, otherwise the document
    // is left with no focused element and keyboard navigation restarts at the
    // top of the page.
    if (this.shellEl.contains(document.activeElement)) {
      this.tabEl.focus();
    }
    // Closing always surrenders the maximised state with the drawer. A drawer
    // parked off-screen while still maximised would spring back covering the
    // whole app the next time anything opened it, and it is what lets the
    // right-edge hand-off in app.js stay a plain close().
    this.setMaximized(false);
    this.rootEl.classList.remove('is-open');
    this.shellEl.setAttribute('inert', '');
    this.shellEl.setAttribute('aria-hidden', 'true');
    this.tabEl.setAttribute('aria-expanded', 'false');
  }

  toggle() {
    if (this.isOpen) {
      this.close();
    } else {
      this.open();
    }
  }

  /**
   * Maximised is a full-window overlay over the application, not a window of
   * its own: the drawer keeps its top at the app bar so the operator can still
   * see where they are and navigate away. Only the geometry changes, so the
   * width chosen by dragging is still there to return to.
   */
  setMaximized(maximized) {
    if (this.isMaximized === maximized) return;
    this.isMaximized = maximized;
    this.rootEl.classList.toggle('is-maximized', maximized);
    this.maximizeIconEl.textContent = maximized ? 'fullscreen_exit' : 'fullscreen';
    this.maximizeEl.setAttribute('aria-pressed', maximized ? 'true' : 'false');
    const label = maximized ? 'Restore Mantis drawer' : 'Maximize Mantis drawer';
    this.maximizeEl.setAttribute('aria-label', label);
    this.maximizeEl.title = label;
  }

  toggleMaximized() {
    this.setMaximized(!this.isMaximized);
  }

  /**
   * Opens the drawer and starts a triage turn for one failed test. This is the
   * only programmatic entry point; callers do not open the drawer and then
   * poke the panel separately.
   */
  openTriage({ siteModel, deviceId, testId }) {
    this.open();
    this.panel.runTriage({ siteModel, deviceId, testId });
  }

  /**
   * Opens the drawer and asks an informational question about any sequencer
   * test, failed or not. Unlike triage it needs no device or run: it is how a
   * device maker learns what a test (including an ALPHA one) expects.
   */
  explainTest(testName) {
    if (!testName) {
      throw new Error('explainTest requires a sequencer test name.');
    }
    this.open();
    // The panel's send() ignores input while a turn is in flight; say so
    // rather than dropping the question without a trace.
    if (this.panel.sendBtn.disabled) {
      this.panel.appendError(
        `Not asked about '${testName}': Mantis is still answering the previous question. ` +
        'Wait for it to finish or stop it, then press "Explain this test" again.'
      );
      return;
    }
    this.panel.inputEl.value =
      `Explain how the sequencer test '${testName}' works and what a device must do to pass it, ` +
      'in terms of UDMI config, state and events.';
    this.panel.send({ testId: testName });
  }

  _onKeydown(event) {
    if ((event.metaKey || event.ctrlKey) && (event.key === 'k' || event.key === 'K')) {
      const active = document.activeElement;
      const typing =
        active &&
        (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.isContentEditable);
      // Ctrl+K inside someone else's field belongs to that field, not to us.
      if (typing && !this.rootEl.contains(active)) return;
      event.preventDefault();
      this.toggle();
      return;
    }

    // Escape closes the drawer only while focus is inside it, so it never
    // steals the key from a dialog or a field on the view underneath.
    //
    // While maximised it unwinds one layer instead: the first Escape restores
    // the drawer and only the second closes it. The overlay is the most
    // disruptive thing on screen, so it is what the key undoes first, and a
    // diagnosis that has been running for minutes is never dismissed by a
    // keypress aimed at the overlay.
    if (event.key === 'Escape' && this.isOpen && this.rootEl.contains(event.target)) {
      if (this.isMaximized) {
        this.setMaximized(false);
        return;
      }
      this.close();
    }
  }
}
