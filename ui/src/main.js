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


// --- MICRO-FRONTEND ORCHESTRATOR SHELL ---
class ShellOrchestrator {
  constructor() {
    this.activeTab = 'testbed'; // Default starting tab
    this.serverAllowedFeatures = ['testbed', 'sequencer', 'mantis'];
    
    // Folder browser state
    this.browserPath = '~';
    this.selectedBrowserFolder = null;
    this.debounceTimeout = null;

    // Perform synchronous elements binding
    this.initElements();
    
    // Kick off async security handshake and orchestration
    this.initOrchestration();
  }

  async initOrchestration() {
    // 1. Fetch server's active security policy (Purely backend construct)
    try {
      const res = await fetch('/api/features');
      if (res.ok) {
        this.serverAllowedFeatures = await res.json();
      }
    } catch (e) {
      console.error('Error fetching server allowed features:', e);
    }

    // Rely entirely on server-configured allowed features
    this.activeFeatures = Array.isArray(this.serverAllowedFeatures) ? this.serverAllowedFeatures : ['testbed', 'sequencer', 'mantis'];

    // 2. Initialize layout and event loops
    this.applyFeatureFlagsLayout();
    this.initEvents();
    this.loadCachedSiteModelPath();

    // 3. Dismiss application loader smoothly once fonts/DOM are ready
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
    const iframes = document.querySelectorAll('.app-iframe');
    const mainContent = document.querySelector('.app-main');

    // 1. Hide tabs not included in the active features list
    tabButtons.forEach(btn => {
      const feat = btn.getAttribute('data-feature');
      if (!this.activeFeatures.includes(feat)) {
        btn.style.display = 'none';
      }
    });

    iframes.forEach(iframe => {
      const feat = iframe.getAttribute('data-feature');
      if (!this.activeFeatures.includes(feat)) {
        iframe.remove(); // Prune completely from DOM
      }
    });

    // 2. Adjust Layout depending on number of active features
    if (this.activeFeatures.length <= 1) {
      // Standalone mode
      if (navTabsContainer) navTabsContainer.style.display = 'none';
      if (mainContent) mainContent.style.padding = '0'; // Clean fullscreen edge-to-edge
      
      const singleFeature = this.activeFeatures[0] || 'testbed';
      this.activeTab = singleFeature;
      
      const activeIframe = document.getElementById(`iframe-${singleFeature}`);
      if (activeIframe) activeIframe.classList.add('active');
    } else {
      // Multi-feature Suite mode
      if (navTabsContainer) navTabsContainer.style.display = 'flex';
      
      const cachedTab = localStorage.getItem('udmi_last_active_tab');
      if (cachedTab && this.activeFeatures.includes(cachedTab)) {
        this.activeTab = cachedTab;
      } else {
        this.activeTab = this.activeFeatures[0];
      }

      this.switchTab(this.activeTab);
    }
  }

  initElements() {
    // Shared Global Controls
    this.siteInput = document.getElementById('site-input');
    this.btnBrowseSite = document.getElementById('btn-browse-site');
    this.siteStatusBadge = document.getElementById('site-status-badge');
    this.onboardingBanner = document.getElementById('site-onboarding-banner');
    
    // Mantis AI Drawer Controls (Right Edge Pull Tab)
    this.btnToggleMantis = document.getElementById('mantis-pull-tab') || document.getElementById('btn-toggle-mantis');
    this.mantisDrawer = document.getElementById('mantis-drawer');
    this.btnCloseMantisDrawer = document.getElementById('btn-close-mantis-drawer');

    // Folder Browser Modal
    this.browserModal = document.getElementById('folder-browser-modal');
    this.btnCloseBrowser = document.getElementById('btn-close-browser');
    this.btnBrowserUp = document.getElementById('btn-browser-up');
    this.browserCurrentPath = document.getElementById('browser-current-path');
    this.browserList = document.querySelector('.browser-list');
    this.btnBrowserCancel = document.getElementById('btn-browser-cancel');
    this.btnBrowserSelect = document.getElementById('btn-browser-select');
  }

  initEvents() {
    // --- 1. TAB SWITCHING (Multi-feature mode) ---
    if (this.activeFeatures.length > 1) {
      const tabButtons = document.querySelectorAll('.m3-tab, .nav-tab, .sidebar-tab');
      tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
          const tabId = btn.getAttribute('data-tab');
          this.switchTab(tabId);
        });
      });

      window.addEventListener('resize', () => {
        this.updateTabIndicator();
      });
    }

    // --- 2. MANTIS AI DRAWER TOGGLE LISTENERS ---
    if (this.btnToggleMantis && this.mantisDrawer) {
      this.btnToggleMantis.addEventListener('click', () => {
        this.mantisDrawer.classList.toggle('open');
      });
    }

    if (this.btnCloseMantisDrawer && this.mantisDrawer) {
      this.btnCloseMantisDrawer.addEventListener('click', () => {
        this.mantisDrawer.classList.remove('open');
      });
    }

    // --- 2b. CROSS-FRONTEND MANTIS TRIAGE DISPATCHER ---
    window.addEventListener('message', (event) => {
      if (event.data && event.data.type === 'open_mantis_triage') {
        if (this.mantisDrawer) {
          this.mantisDrawer.classList.add('open');
        }
        const mantisIframe = document.getElementById('iframe-mantis');
        if (mantisIframe && mantisIframe.contentWindow) {
          mantisIframe.contentWindow.postMessage({
            type: 'trigger_triage',
            deviceId: event.data.deviceId,
            testId: event.data.testId,
            siteModel: event.data.siteModel,
            projectSpec: event.data.projectSpec,
            sessionId: event.data.sessionId
          }, '*');
        }
      }
    });

    // --- 3. GLOBAL CONTROLS LISTENERS ---
    this.btnBrowseSite.addEventListener('click', () => this.openFolderBrowser());

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

    // --- 3. IFRAME LOAD LISTENERS (Ensures late-bound state synchronization) ---
    const iframes = document.querySelectorAll('.app-iframe');
    iframes.forEach(iframe => {
      iframe.addEventListener('load', () => {
        this.syncStateToIframe(iframe);
      });
    });

    // --- 4. FOLDER BROWSER MODAL EVENTS ---
    this.btnCloseBrowser.addEventListener('click', () => this.closeFolderBrowser());
    this.btnBrowserCancel.addEventListener('click', () => this.closeFolderBrowser());
    this.btnBrowserSelect.addEventListener('click', () => this.selectBrowserDirectory());
    this.btnBrowserUp.addEventListener('click', () => this.navigateBrowserUp());
    this.browserCurrentPath.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        this.loadBrowserPath(e.target.value.trim());
      }
    });

    // --- 5. CROSS-IFRAME TRIAGE REDIRECTION (Sequencer -> Mantis) ---
    window.addEventListener('message', (event) => {
      if (event.data && event.data.type === 'trigger_diagnose') {
        this.handleTriggerDiagnose(event.data);
      }
    });
  }

  // --- STATE SYNCING VIA POSTMESSAGE ---
  syncStateToIframes() {
    const iframes = document.querySelectorAll('.app-iframe');
    iframes.forEach(iframe => this.syncStateToIframe(iframe));
  }

  syncStateToIframe(iframe) {
    const sitePath = this.siteInput.value.trim();
    
    if (iframe.contentWindow) {
      iframe.contentWindow.postMessage({
        type: 'udmi_state_change',
        siteModel: sitePath
      }, '*');
    }
  }

  // --- TAB SWITCHING MACHINERY ---
  updateTabIndicator(activeBtn = null) {
    const indicator = document.querySelector('.m3-tab-indicator');
    const tabBar = document.getElementById('app-nav-tabs');
    if (!indicator || !tabBar) return;

    if (!activeBtn) {
      activeBtn = document.querySelector('.m3-tab.active, .nav-tab.active');
    }

    if (activeBtn) {
      const barRect = tabBar.getBoundingClientRect();
      const tabRect = activeBtn.getBoundingClientRect();
      const left = tabRect.left - barRect.left;
      const width = tabRect.width;
      indicator.style.left = `${left}px`;
      indicator.style.width = `${width}px`;
    }
  }

  switchTab(tabId) {
    this.activeTab = tabId;
    localStorage.setItem('udmi_last_active_tab', tabId);

    // Update Nav Buttons
    const tabButtons = document.querySelectorAll('.m3-tab, .nav-tab, .sidebar-tab');
    let activeBtn = null;
    tabButtons.forEach(btn => {
      const active = btn.getAttribute('data-tab') === tabId;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-selected', active ? 'true' : 'false');
      if (active) activeBtn = btn;
    });

    // Animate active tab underline indicator
    this.updateTabIndicator(activeBtn);

    // Update Iframes Visibility
    const iframes = document.querySelectorAll('.app-iframe');
    iframes.forEach(iframe => {
      const active = iframe.id === `iframe-${tabId}`;
      iframe.classList.toggle('active', active);
    });

    // Update Brand Subtitle dynamically
    const brandSubtitle = document.getElementById('app-subtitle');
    if (brandSubtitle) {
      brandSubtitle.textContent = `/ ${tabId.charAt(0).toUpperCase() + tabId.slice(1)}`;
    }
  }

  handleTriggerDiagnose(data) {
    const { testId, deviceId, siteModel } = data;
    console.log(`Parent Shell: Intercepted trigger_diagnose for test ${testId} on device ${deviceId}`);
    
    // 1. Open the Mantis AI side drawer without interrupting active screen workspace
    if (this.mantisDrawer) {
      this.mantisDrawer.classList.add('open');
    }
    
    // 2. Locate the Mantis iframe inside the drawer and pipe the event
    const iframeMantis = document.getElementById('iframe-mantis');
    if (iframeMantis) {
      const sendPayload = () => {
        iframeMantis.contentWindow.postMessage({
          type: 'load_diagnose',
          testId,
          deviceId,
          siteModel
        }, '*');
      };
      
      // Defensively check if the iframe is already loaded. If not, wait for it!
      if (iframeMantis.contentDocument && iframeMantis.contentDocument.readyState === 'complete') {
        sendPayload();
      } else {
        iframeMantis.addEventListener('load', sendPayload, { once: true });
      }
    }
  }

  // --- CACHE & DYNAMIC DIRECTORY SCANNERS ---
  async loadCachedSiteModelPath() {
    const cached = localStorage.getItem('udmi_site_model_path');
    let isValid = false;

    if (cached) {
      this.siteInput.value = cached;
      isValid = await this.handleSiteModelPathChange(cached);
    } else {
      this.siteInput.value = 'sites/udmi_site_model';
      isValid = await this.handleSiteModelPathChange('sites/udmi_site_model');
    }

    // Pop up folder browser modal on startup ONLY if no valid site model path is set
    if (!isValid) {
      setTimeout(() => {
        this.openFolderBrowser();
      }, 350);
    }
  }

  async handleSiteModelPathChange(sitePath) {
    if (!sitePath) {
      this.updateSiteModelStatus('unselected');
      this.syncStateToIframes();
      return false;
    }

    // Instantly set state to processing (amber) while validating backend endpoint
    this.updateSiteModelStatus('processing');

    let isValid = false;
    try {
      const res = await fetch(`/api/devices?site_model=${encodeURIComponent(sitePath)}`);
      if (res.ok) {
        this.updateSiteModelStatus('valid');
        isValid = true;
      } else {
        this.updateSiteModelStatus('invalid');
      }
    } catch (e) {
      this.updateSiteModelStatus('invalid');
    }

    if (isValid) {
      localStorage.setItem('udmi_site_model_path', sitePath);
    }
    this.syncStateToIframes();
    return isValid;
  }

  updateSiteModelStatus(status) { // 'valid' | 'invalid' | 'processing' | 'unselected'
    if (!this.siteInput) return;

    this.siteInput.classList.remove(
      'site-input-state-valid',
      'site-input-state-invalid',
      'site-input-state-processing',
      'site-input-state-unselected',
      'site-input-state-empty'
    );

    if (status === 'valid') {
      this.siteInput.classList.add('site-input-state-valid');
      if (this.onboardingBanner) this.onboardingBanner.style.display = 'none';
    } else if (status === 'invalid') {
      this.siteInput.classList.add('site-input-state-invalid');
      if (this.onboardingBanner) this.onboardingBanner.style.display = 'flex';
    } else { // 'processing' or 'unselected'
      this.siteInput.classList.add(status === 'processing' ? 'site-input-state-processing' : 'site-input-state-unselected');
      if (this.onboardingBanner) this.onboardingBanner.style.display = 'flex';
    }
  }

  // --- FOLDER BROWSER MODAL CONTROLLER ---
  openFolderBrowser() {
    const currentVal = this.siteInput.value.trim();
    this.browserPath = currentVal || '~';
    this.selectedBrowserFolder = null;
    this.browserModal.classList.add('active');
    this.loadBrowserPath(this.browserPath);
  }

  closeFolderBrowser() {
    this.browserModal.classList.remove('active');
  }

  async loadBrowserPath(path) {
    this.selectedBrowserFolder = null;
    this.browserList.innerHTML = '<div style="padding:16px; text-align:center; color:var(--text-muted);">Reading directory...</div>';
    
    try {
      const data = await fetchDirectoryList(path);
      this.browserPath = data.path;
      this.browserCurrentPath.value = data.path;
      this.renderBrowserList(data.folders);
    } catch (err) {
      this.browserList.innerHTML = `<div style="padding:16px; text-align:center; color:var(--color-error);">Error: ${err.message}</div>`;
    }
  }

  renderBrowserList(folders) {
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
    this.siteInput.value = finalPath;
    this.closeFolderBrowser();
    this.handleSiteModelPathChange(finalPath);
  }
}

// Initialize workspace on load
window.addEventListener('DOMContentLoaded', () => {
  new ShellOrchestrator();
});
