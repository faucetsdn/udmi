/**
 * Layer 2 — Application shell and router.
 *
 * v1 mounted each screen in an <iframe> and synchronised state with
 * postMessage('*') across origins. This shell mounts a single view at a time
 * into one document, so state is shared directly through the store and there
 * is no cross-frame message channel to secure.
 */

import { api } from './core/api.js';
import { desktopNotify } from './core/desktop-notify.js';
import { logger } from './core/logger.js';
import { LogsDrawer } from './components/logs-drawer.js';
import { MantisDrawer } from './components/mantis-drawer.js';
import { SettingsDialog } from './components/settings-dialog.js';
import { DevicesView } from './views/devices.js';
import { SequencerView } from './views/sequencer.js';

/**
 * Only the two working surfaces are routed tabs. Mantis and the log console
 * used to be tabs of their own, which meant leaving the run you were debugging
 * in order to ask about it; both are now drawers that overlay whichever view is
 * mounted.
 */
const ROUTES = [
  { path: '/sequencer', label: 'Sequencer', view: SequencerView },
  { path: '/devices', label: 'Devices', view: DevicesView },
];

const DEFAULT_ROUTE = '/sequencer';

/** Opening the log drawer swaps the path to this, so the view is linkable. */
const LOGS_PATH = '/logs';

class WorkbenchApp {
  constructor() {
    this.navEl = document.querySelector('[data-role="nav"]');
    this.outletEl = document.querySelector('[data-role="outlet"]');
    this.statusEl = document.querySelector('[data-role="server-status"]');
    this.logsToggleEl = document.querySelector('[data-role="logs-toggle"]');
    this.settingsToggleEl = document.querySelector('[data-role="settings-toggle"]');
    this.settings = new SettingsDialog(document.querySelector('[data-role="settings-mount"]'));
    this.views = new Map();
    this.current = null;
    this.currentPath = null;
    this.mantis = new MantisDrawer(document.querySelector('[data-role="mantis-mount"]'));
    this.logs = new LogsDrawer(document.querySelector('[data-role="logs-mount"]'), {
      onVisibilityChange: (open) => this._onLogsVisibility(open),
    });
    window.workbenchApp = this;
  }

  async start() {
    this.buildNav();
    this.mantis.mount();
    this.logs.mount();
    this.logsToggleEl.addEventListener('click', () => this.logs.toggle());
    this.settingsToggleEl.addEventListener('click', () => this.settings.open());
    this._watchDesktopNotifications();

    window.addEventListener('popstate', () => this._applyLocation(false));
    await this.checkServer();
    await this._applyLocation(false);
  }

  /**
   * Resolves the current URL. `/logs` is not a view: it mounts the view the
   * operator was last on (or the default) and raises the log drawer over it, so
   * that a link to the logs never discards the working surface underneath.
   */
  async _applyLocation(pushState) {
    const pathname = window.location.pathname;
    if (pathname === LOGS_PATH) {
      await this.mount(this.currentPath || DEFAULT_ROUTE, false);
      this.logs.open();
      return;
    }
    this.logs.close();
    await this.mount(pathname, pushState);
  }

  /**
   * Keeps a persistent app-bar notice while desktop notifications cannot be
   * shown, so a blocked permission is never silent. There is no re-prompt:
   * browsers only let the operator change a denial in site settings.
   */
  _watchDesktopNotifications() {
    const notice = document.querySelector('[data-role="desktop-notify-notice"]');
    const text = document.querySelector('[data-role="desktop-notify-text"]');
    desktopNotify.subscribe((status) => {
      if (status === 'denied') {
        text.textContent = 'Desktop notifications blocked';
        notice.title =
          'The browser is blocking notifications for this site. To allow them, click the ' +
          'site information icon at the left of the address bar, set Notifications to ' +
          'Allow, then reload Workbench.';
      } else if (status === 'unsupported') {
        text.textContent = 'Desktop notifications unavailable';
        notice.title =
          'This browser has no Notification API, so finished runs and Mantis answers ' +
          'cannot raise desktop notifications. Use Email me when done instead.';
      }
      notice.hidden = status !== 'denied' && status !== 'unsupported';
    });
  }

  /** Keeps the URL and the toggle's ARIA state in step with the drawer. */
  _onLogsVisibility(open) {
    this.logsToggleEl.setAttribute('aria-expanded', open ? 'true' : 'false');
    this.logsToggleEl.classList.toggle('is-active', open);
    const target = open ? LOGS_PATH : (this.currentPath || DEFAULT_ROUTE);
    if (window.location.pathname !== target) {
      window.history.pushState({}, '', `${target}${window.location.search}`);
    }
  }

  /**
   * The right screen edge hosts one drawer at a time.
   *
   * Mantis and Local Test Setup are both anchored right. Stacking them left the
   * Mantis pull tab floating on top of the setup panel's content, and together
   * they cover most of the workspace anyway. Whichever drawer is being opened
   * claims the edge and the other closes. The shell arbitrates rather than the
   * drawers knowing about each other, so neither has to import the other.
   *
   * A maximised Mantis needs nothing extra here. MantisDrawer.close() drops the
   * maximised state along with the drawer, so the loser of the arbitration
   * leaves the screen outright instead of lingering as a full-window overlay,
   * and its focus-return already runs before the shell goes inert. That is why
   * the setup drawer keeps its z-index 100 under Mantis' 200: the two are never
   * on screen together, so there is no stacking to correct. Raising the setup
   * drawer over a maximised Mantis would be the wrong fix -- it would put a
   * second live surface on top of one that still holds focusable content, which
   * is exactly the trap this contract exists to prevent.
   */
  claimRightEdge(claimant) {
    if (claimant !== this.mantis) {
      this.mantis.close();
    }
    const setup = this.current?.localSetupDrawer;
    if (setup && claimant !== setup) {
      setup.close();
    }
  }

  navigate(path, params = null) {
    return this.mount(path, true, params);
  }

  buildNav() {
    this.navEl.textContent = '';
    for (const route of ROUTES) {
      const link = document.createElement('a');
      link.href = route.path;
      link.className = 'nav-link';
      link.dataset.path = route.path;
      link.textContent = route.label;
      link.addEventListener('click', (event) => {
        event.preventDefault();
        this.mount(route.path, true);
      });
      this.navEl.appendChild(link);
    }
  }

  /** Surfaces the real server identity; a dead gateway is stated, not hidden. */
  async checkServer() {
    try {
      const health = await api.health();
      this.statusEl.textContent = `${health.service} · ${health.mcp_tools} MCP tools`;
      this.statusEl.className = 'server-status is-ok';
      this.statusEl.title = health.udmi_root;
    } catch (cause) {
      this.statusEl.textContent = 'Gateway unreachable';
      this.statusEl.className = 'server-status is-error';
      this.statusEl.title = cause.message;
    }
  }

  async mount(pathname, pushState, params = null) {
    const route = ROUTES.find((candidate) => candidate.path === pathname) ||
      ROUTES.find((candidate) => candidate.path === DEFAULT_ROUTE);

    if (pushState || window.location.pathname !== route.path) {
      const search = window.location.search;
      window.history[pushState ? 'pushState' : 'replaceState']({}, '', `${route.path}${search}`);
    }

    logger.info('WorkbenchApp', 'route.mount', { details: { path: route.path } });

    for (const link of this.navEl.querySelectorAll('.nav-link')) {
      const active = link.dataset.path === route.path;
      link.classList.toggle('is-active', active);
      link.setAttribute('aria-current', active ? 'page' : 'false');
    }

    // Hide all view panes
    for (const entry of this.views.values()) {
      entry.pane.hidden = true;
    }

    this.currentPath = route.path;

    let entry = this.views.get(route.path);
    if (!entry) {
      const pane = document.createElement('div');
      pane.className = 'view-pane';
      pane.dataset.view = route.path;
      this.outletEl.appendChild(pane);

      const view = new route.view(pane);
      entry = { view, pane };
      this.views.set(route.path, entry);
      this.current = view;

      try {
        await view.init();
      } catch (cause) {
        logger.error('WorkbenchApp', 'route.init_failed', {
          details: { path: route.path },
          error: { code: 'INIT', message: cause.message },
        });
        const note = document.createElement('p');
        note.className = 'empty-note is-error';
        note.textContent = `Failed to initialise ${route.label}: ${cause.message}`;
        pane.appendChild(note);
      }
    } else {
      this.current = entry.view;
    }

    entry.pane.hidden = false;
    try {
      await entry.view.onActivate?.(params);
    } catch (cause) {
      logger.warn('WorkbenchApp', 'route.activate_error', {
        details: { path: route.path, error: cause.message },
      });
    }
  }
}

// Surface otherwise-invisible failures in the diagnostics ring buffer.
window.addEventListener('error', (event) => {
  logger.error('Window', 'uncaught.error', {
    error: { code: 'UNCAUGHT', message: event.message },
    details: { source: `${event.filename}:${event.lineno}` },
  });
});
window.addEventListener('unhandledrejection', (event) => {
  logger.error('Window', 'unhandled.rejection', {
    error: { code: 'UNHANDLED_REJECTION', message: String(event.reason?.message || event.reason) },
  });
});

new WorkbenchApp().start();
