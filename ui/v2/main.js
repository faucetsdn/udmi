// ==========================================================================
// UDMI WORKBENCH - MAIN SPA SHELL & ORCHESTRATOR
// ==========================================================================
import { stateStore } from './shared/state-store.js?v=3.0';
import { TestbedGraphController } from './testbed/main.js?v=3.0';
import { MantisController } from './mantis/main.js?v=3.0';
import { NotificationManager } from './shared/components/notification-toast.js?v=3.0';

// --- HELPER: DYNAMIC ENDPOINT FETCH ---
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

function getParentPath(path) {
  if (!path || path === '.' || path === '/' || path === '' || path === '~') return '~';
  const parts = path.split('/').filter(p => p);
  if (parts.length <= 1) {
    return path.startsWith('~') ? '~' : (path.startsWith('/') ? '/' : '.');
  }
  parts.pop();
  if (parts[0] === '~') {
    return parts.join('/');
  }
  return (path.startsWith('/') ? '/' : '') + parts.join('/');
}

// --- SINGLE-PAGE APPLICATION ORCHESTRATOR ---
class ShellOrchestrator {
  constructor() {
    this.activeTab = 'testbed'; // Single master workspace screen
    this.serverAllowedFeatures = ['testbed', 'mantis'];
    
    // Folder browser state
    this.browserPath = '~';
    this.selectedBrowserFolder = null;
    this.debounceTimeout = null;

    // Synchronous element binding
    this.initElements();
    
    // Kick off orchestration
    this.initOrchestration();
  }

  async initOrchestration() {
    this.activeFeatures = ['testbed', 'mantis'];

    // Initialize consolidated master SPA controllers
    this.testbedController = new TestbedGraphController();
    this.mantisController = new MantisController();

    // Initialize layout and event loops
    this.applyFeatureFlagsLayout();
    this.initEvents();
    this.loadCachedSiteModelPath();

    // Request desktop notification permissions and register persistent service worker
    NotificationManager.requestPermission();
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('service-worker.js', { scope: './' })
        .then(reg => console.log('UDMI Service Worker active:', reg.scope))
        .catch(err => console.warn('SW registration failed:', err));
    }

    // Dismiss application loader smoothly once fonts/DOM are ready
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(() => this.dismissLoader());
    } else {
      setTimeout(() => this.dismissLoader(), 300);
    }
  }

  dismissLoader() {
    const loader = document.getElementById('app-loader');
    if (loader && !loader.classList.contains('fade-out')) {
      loader.classList.add('fade-out');
      setTimeout(() => {
        loader.style.display = 'none';
        this.updateTabIndicator();
      }, 450);
    }
  }

  applyFeatureFlagsLayout() {
    const navTabsContainer = document.getElementById('app-nav-tabs');
    const tabButtons = document.querySelectorAll('.m3-tab, .nav-tab, .sidebar-tab');
    const views = document.querySelectorAll('.app-view');
    const mainContent = document.querySelector('.app-main');

    tabButtons.forEach(btn => {
      const feat = btn.getAttribute('data-feature');
      if (!this.activeFeatures.includes(feat)) {
        btn.style.display = 'none';
      }
    });

    views.forEach(view => {
      const feat = view.getAttribute('data-feature');
      if (!this.activeFeatures.includes(feat)) {
        view.remove();
      }
    });

    // In our consolidated single-screen model (testbed workspace + mantis drawer),
    // the tab bar collapses to give maximum fullscreen height to the testing suite!
    if (this.activeFeatures.filter(f => f !== 'mantis').length <= 1) {
      if (navTabsContainer) navTabsContainer.style.display = 'none';
      if (mainContent) mainContent.style.padding = '0';
      
      const singleFeature = 'testbed';
      this.activeTab = singleFeature;
      
      const activeView = document.getElementById(`view-${singleFeature}`);
      if (activeView) activeView.classList.add('active');
    }
  }

  initElements() {
    this.siteInput = document.getElementById('site-input');
    this.btnBrowseSite = document.getElementById('btn-browse-site');
    this.onboardingBanner = document.getElementById('site-onboarding-banner');
    
    this.btnToggleMantis = document.getElementById('mantis-pull-tab') || document.getElementById('btn-toggle-mantis');
    this.mantisDrawer = document.getElementById('mantis-drawer');
    this.btnMantisFullscreen = document.getElementById('btn-mantis-fullscreen');
    this.btnCloseMantisDrawer = document.getElementById('btn-close-mantis-drawer');

    this.browserModal = document.getElementById('folder-browser-modal');
    this.btnCloseBrowser = document.getElementById('btn-close-browser');
    this.btnBrowserUp = document.getElementById('btn-browser-up');
    this.browserCurrentPath = document.getElementById('browser-current-path');
    this.browserList = document.querySelector('.browser-list');
    this.btnBrowserCancel = document.getElementById('btn-browser-cancel');
    this.btnBrowserSelect = document.getElementById('btn-browser-select');
  }

  initEvents() {
    if (this.btnToggleMantis && this.mantisDrawer) {
      this.btnToggleMantis.addEventListener('click', () => {
        this.mantisDrawer.classList.toggle('open');
      });
    }

    if (this.btnMantisFullscreen && this.mantisDrawer) {
      this.btnMantisFullscreen.addEventListener('click', () => {
        const isFullscreen = this.mantisDrawer.classList.toggle('fullscreen');
        const icon = this.btnMantisFullscreen.querySelector('.material-symbols-outlined');
        if (icon) {
          icon.textContent = isFullscreen ? 'fullscreen_exit' : 'fullscreen';
        }
        this.btnMantisFullscreen.title = isFullscreen ? 'Exit full screen' : 'Full screen';
        this.btnMantisFullscreen.setAttribute('aria-label', isFullscreen ? 'Exit full screen' : 'Full screen');
      });
    }

    if (this.btnCloseMantisDrawer && this.mantisDrawer) {
      this.btnCloseMantisDrawer.addEventListener('click', () => {
        this.mantisDrawer.classList.remove('open');
        this.mantisDrawer.classList.remove('fullscreen');
        if (this.btnMantisFullscreen) {
          const icon = this.btnMantisFullscreen.querySelector('.material-symbols-outlined');
          if (icon) icon.textContent = 'fullscreen';
          this.btnMantisFullscreen.title = 'Full screen';
          this.btnMantisFullscreen.setAttribute('aria-label', 'Full screen');
        }
      });
    }

    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        if (this.browserModal && (this.browserModal.classList.contains('active') || this.browserModal.style.display === 'flex')) {
          this.closeFolderBrowser();
        }
        if (this.mantisDrawer && this.mantisDrawer.classList.contains('fullscreen')) {
          this.mantisDrawer.classList.remove('fullscreen');
          if (this.btnMantisFullscreen) {
            const icon = this.btnMantisFullscreen.querySelector('.material-symbols-outlined');
            if (icon) icon.textContent = 'fullscreen';
            this.btnMantisFullscreen.title = 'Full screen';
            this.btnMantisFullscreen.setAttribute('aria-label', 'Full screen');
          }
        }
      }
    });

    stateStore.on('open_mantis_triage', (data) => {
      if (this.mantisDrawer) {
        this.mantisDrawer.classList.add('open');
      }
      if (this.mantisController && typeof this.mantisController.triggerTriage === 'function') {
        this.mantisController.triggerTriage(data);
      }
    });

    stateStore.on('trigger_diagnose', (data) => {
      if (this.mantisDrawer) {
        this.mantisDrawer.classList.add('open');
      }
      if (this.mantisController && typeof this.mantisController.loadDiagnose === 'function') {
        this.mantisController.loadDiagnose(data);
      }
    });

    stateStore.on('open_folder_browser', () => {
      this.openFolderBrowser();
    });

    stateStore.on('change:siteModel', () => {
      this.updateBanner();
    });

    stateStore.on('change:activeDeviceNodesCount', (count) => {
      this.activeDeviceNodesCount = count;
      this.updateBanner();
    });

    stateStore.on('canvas_devices_changed', (data) => {
      this.activeDeviceNodesCount = data ? data.count : 0;
      this.updateBanner();
    });

    window.addEventListener('message', (event) => {
      if (event.data && event.data.type === 'open_mantis_triage') {
        stateStore.emit('open_mantis_triage', event.data);
      } else if (event.data && event.data.type === 'trigger_diagnose') {
        stateStore.emit('trigger_diagnose', event.data);
      } else if (event.data && event.data.type === 'open_folder_browser') {
        this.openFolderBrowser();
      } else if (event.data && event.data.type === 'udmi_state_change') {
        if (event.data.siteModel) {
          stateStore.set('siteModel', event.data.siteModel);
        }
        if (event.data.projectSpec) {
          stateStore.set('projectSpec', event.data.projectSpec);
        }
      }
    });

    if (this.btnBrowseSite) {
      this.btnBrowseSite.addEventListener('click', (e) => {
        e.preventDefault();
        this.openFolderBrowser();
      });
    }

    if (this.onboardingBanner) {
      this.onboardingBanner.addEventListener('click', () => {
        if (this.bannerState === 'no_site_model') {
          this.openFolderBrowser();
        } else if (this.bannerState === 'no_devices') {
          const deviceSearchInput = document.getElementById('input-device-search');
          if (deviceSearchInput) deviceSearchInput.focus();
        }
      });
    }

    if (this.siteInput) {
      this.siteInput.addEventListener('input', (e) => {
        clearTimeout(this.debounceTimeout);
        this.debounceTimeout = setTimeout(() => {
          this.handleSiteModelPathChange(e.target.value.trim());
        }, 400);
      });

      this.siteInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          clearTimeout(this.debounceTimeout);
          this.handleSiteModelPathChange(e.target.value.trim());
        }
      });
    }

    if (this.browserModal) {
      this.browserModal.addEventListener('click', (e) => {
        if (e.target === this.browserModal) {
          this.closeFolderBrowser();
        }
      });
    }

    if (this.btnCloseBrowser) this.btnCloseBrowser.addEventListener('click', () => this.closeFolderBrowser());
    if (this.btnBrowserCancel) this.btnBrowserCancel.addEventListener('click', () => this.closeFolderBrowser());
    if (this.btnBrowserSelect) this.btnBrowserSelect.addEventListener('click', () => this.selectBrowserDirectory());
    if (this.btnBrowserUp) this.btnBrowserUp.addEventListener('click', () => this.navigateBrowserUp());
    if (this.browserCurrentPath) {
      this.browserCurrentPath.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          this.loadBrowserPath(e.target.value.trim());
        }
      });
    }
  }

  updateTabIndicator() {
    // No-op for single screen model
  }

  async loadCachedSiteModelPath() {
    let cached = null;
    try {
      cached = localStorage.getItem('udmi_site_model_path');
    } catch (e) {}

    if (cached) {
      this.siteInput.value = cached;
      await this.handleSiteModelPathChange(cached);
    } else {
      this.siteInput.value = '';
      this.updateSiteModelStatus('unselected');
      stateStore.set('siteModel', '');
      stateStore.set('devices', []);
    }
  }

  async handleSiteModelPathChange(sitePath) {
    if (!sitePath) {
      stateStore.set('siteModel', '');
      stateStore.set('devices', []);
      this.updateSiteModelStatus('unselected');
      try {
        localStorage.removeItem('udmi_site_model_path');
      } catch (e) {}
      return false;
    }

    this.updateSiteModelStatus('processing');

    let isValid = false;
    try {
      const res = await fetch(`/api/devices?site_model=${encodeURIComponent(sitePath)}`);
      if (res.ok) {
        const data = await res.json();
        isValid = true;
        stateStore.set('siteModel', sitePath);
        stateStore.set('devices', data.devices || []);
        stateStore.set('deviceMetadata', data.device_metadata || {});
        this.updateSiteModelStatus('valid');
      } else {
        stateStore.set('siteModel', '');
        stateStore.set('devices', []);
        this.updateSiteModelStatus('invalid');
      }
    } catch (e) {
      stateStore.set('siteModel', '');
      stateStore.set('devices', []);
      this.updateSiteModelStatus('invalid');
    }

    if (isValid) {
      try {
        localStorage.setItem('udmi_site_model_path', sitePath);
      } catch (e) {}
    } else {
      try {
        localStorage.removeItem('udmi_site_model_path');
      } catch (e) {}
    }
    return isValid;
  }

  updateSiteModelStatus(status) {
    if (!this.siteInput) return;

    this.siteInput.classList.remove(
      'site-input-state-valid',
      'site-input-state-invalid',
      'site-input-state-processing',
      'site-input-state-unselected'
    );

    if (status === 'valid') {
      this.siteInput.classList.add('site-input-state-valid');
    } else if (status === 'invalid') {
      this.siteInput.classList.add('site-input-state-invalid');
    } else {
      this.siteInput.classList.add(status === 'processing' ? 'site-input-state-processing' : 'site-input-state-unselected');
    }

    this.updateBanner();
  }

  updateBanner() {
    if (!this.onboardingBanner) return;

    const iconEl = this.onboardingBanner.querySelector('.icon');
    const textEl = this.onboardingBanner.querySelector('.text');

    const siteModel = stateStore.get('siteModel') || (this.siteInput ? this.siteInput.value.trim() : '');
    const isSiteValid = this.siteInput && this.siteInput.classList.contains('site-input-state-valid');
    const deviceCount = this.activeDeviceNodesCount !== undefined
      ? this.activeDeviceNodesCount
      : (stateStore.get('activeDeviceNodesCount') !== undefined
        ? stateStore.get('activeDeviceNodesCount')
        : (this.testbedController && this.testbedController.deviceNodes ? this.testbedController.deviceNodes.length : 0));

    this.onboardingBanner.classList.remove('banner-info', 'banner-warning');

    if (!siteModel || !isSiteValid) {
      this.bannerState = 'no_site_model';
      this.onboardingBanner.classList.add('banner-info');
      if (iconEl) iconEl.textContent = 'info';
      if (textEl) textEl.textContent = 'Welcome to UDMI Workbench! Please select or enter a valid Site Model Path above to begin.';
      this.onboardingBanner.style.display = 'flex';
      this.onboardingBanner.style.cursor = 'pointer';
      this.onboardingBanner.title = 'Click to browse and select a Site Model directory';
    } else if (deviceCount === 0) {
      this.bannerState = 'no_devices';
      this.onboardingBanner.classList.add('banner-warning');
      if (iconEl) iconEl.textContent = 'devices';
      if (textEl) textEl.textContent = "Select one or more devices from the 'Site Devices' list on the left to include them in the test topology.";
      this.onboardingBanner.style.display = 'flex';
      this.onboardingBanner.style.cursor = 'pointer';
      this.onboardingBanner.title = 'Click to focus Site Devices list';
    } else {
      this.bannerState = 'ready';
      this.onboardingBanner.style.display = 'none';
      this.onboardingBanner.title = '';
    }
  }

  openFolderBrowser() {
    const currentVal = (this.siteInput ? this.siteInput.value.trim() : '') || stateStore.get('siteModel') || '';
    this.browserPath = currentVal || 'sites';
    this.selectedBrowserFolder = null;
    if (this.browserModal) {
      this.browserModal.classList.add('active');
    }
    this.loadBrowserPath(this.browserPath);
  }

  closeFolderBrowser() {
    if (this.browserModal) {
      this.browserModal.classList.remove('active');
    }
  }

  async loadBrowserPath(path) {
    this.selectedBrowserFolder = null;
    if (this.browserList) {
      this.browserList.innerHTML = '<div style="padding:16px; text-align:center; color:var(--text-muted);">Reading directory...</div>';
    }
    
    try {
      const data = await fetchDirectoryList(path);
      this.browserPath = data.path;
      if (this.browserCurrentPath) this.browserCurrentPath.value = data.path;
      this.renderBrowserList(data.folders);
    } catch (err) {
      if (this.browserList) {
        this.browserList.innerHTML = `<div style="padding:16px; text-align:center; color:var(--color-error);">Error: ${err.message}</div>`;
      }
    }
  }

  renderBrowserList(folders) {
    if (!this.browserList) return;
    this.browserList.innerHTML = '';
    
    if (folders.length === 0) {
      this.browserList.innerHTML = '<div style="padding:16px; text-align:center; color:var(--text-muted);">No subdirectories found</div>';
      return;
    }

    folders.forEach(folder => {
      const itemEl = document.createElement('div');
      itemEl.className = 'browser-item';
      itemEl.innerHTML = `
        <span class="material-symbols-outlined">folder</span>
        <span>${folder}</span>
      `;
      
      itemEl.addEventListener('click', (e) => {
        e.stopPropagation();
        this.browserList.querySelectorAll('.browser-item').forEach(el => el.classList.remove('selected'));
        itemEl.classList.add('selected');
        this.selectedBrowserFolder = folder;
      });

      itemEl.addEventListener('dblclick', (e) => {
        e.stopPropagation();
        const nextPath = combinePaths(this.browserPath, folder);
        this.loadBrowserPath(nextPath);
      });

      this.browserList.appendChild(itemEl);
    });
  }

  navigateBrowserUp() {
    const parent = getParentPath(this.browserPath);
    if (parent !== null) {
      this.loadBrowserPath(parent);
    }
  }

  selectBrowserDirectory() {
    let finalPath = this.browserPath;
    if (this.selectedBrowserFolder) {
      finalPath = combinePaths(this.browserPath, this.selectedBrowserFolder);
    }
    if (this.siteInput) this.siteInput.value = finalPath;
    this.closeFolderBrowser();
    this.handleSiteModelPathChange(finalPath);
  }
}

window.addEventListener('DOMContentLoaded', () => {
  new ShellOrchestrator();
});
