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
    this.activeTab = 'sequencer'; // Default starting tab
    this.serverAllowedFeatures = ['sequencer', 'mantis'];
    
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
    // 1. Fetch server's active security policy
    try {
      const res = await fetch('/api/features');
      if (res.ok) {
        this.serverAllowedFeatures = await res.json();
      }
    } catch (e) {
      console.error('Error fetching server allowed features:', e);
    }

    // 2. Parse feature flags from URL / Cache
    this.activeFeatures = this.parseFeatureFlags();
    
    // 3. Strict Enforce: Intercept and filter out any disabled features
    this.activeFeatures = this.activeFeatures.filter(f => this.serverAllowedFeatures.includes(f));

    // 4. Initialize layout and event loops
    this.applyFeatureFlagsLayout();
    this.initEvents();
    this.loadCachedSiteModelPath();

    // 5. Dismiss application loader smoothly once fonts/DOM are ready
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
      }, 450);
    }
  }

  parseFeatureFlags() {
    const params = new URLSearchParams(window.location.search);
    const featuresParam = params.get('features');
    
    if (featuresParam) {
      const parsed = featuresParam.split(',').map(f => f.trim().toLowerCase());
      localStorage.setItem('udmi_active_features', JSON.stringify(parsed));
      return parsed;
    }
    
    const cached = localStorage.getItem('udmi_active_features');
    if (cached) {
      try {
        return JSON.parse(cached);
      } catch (e) {
        console.error('Error parsing cached active features:', e);
      }
    }
    
    return ['sequencer', 'mantis']; // Default: all features active
  }

  applyFeatureFlagsLayout() {
    const sidebar = document.querySelector('.app-sidebar');
    const tabButtons = document.querySelectorAll('.sidebar-tab');
    const iframes = document.querySelectorAll('.app-iframe');
    const brandSubtitle = document.getElementById('app-subtitle');
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
      sidebar.style.display = 'none';
      mainContent.style.padding = '0'; // Clean fullscreen edge-to-edge
      
      const singleFeature = this.activeFeatures[0] || 'sequencer';
      this.activeTab = singleFeature;
      
      const activeIframe = document.getElementById(`iframe-${singleFeature}`);
      if (activeIframe) activeIframe.classList.add('active');

      brandSubtitle.textContent = `/ ${singleFeature.charAt(0).toUpperCase() + singleFeature.slice(1)}`;
    } else {
      // Multi-feature Suite mode
      sidebar.style.display = 'flex';
      
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
      const tabButtons = document.querySelectorAll('.sidebar-tab');
      tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
          const tabId = btn.getAttribute('data-tab');
          this.switchTab(tabId);
        });
      });
    }

    // --- 2. GLOBAL CONTROLS LISTENERS ---
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
  switchTab(tabId) {
    this.activeTab = tabId;
    localStorage.setItem('udmi_last_active_tab', tabId);

    // Update Sidebar Rail Buttons
    const tabButtons = document.querySelectorAll('.sidebar-tab');
    tabButtons.forEach(btn => {
      const active = btn.getAttribute('data-tab') === tabId;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-selected', active ? 'true' : 'false');
    });

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
    
    // 1. Switch to the Mantis tab
    this.switchTab('mantis');
    
    // 2. Locate the Mantis iframe and pipe the event
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
    if (cached) {
      this.siteInput.value = cached;
      await this.handleSiteModelPathChange(cached);
    }
  }

  async handleSiteModelPathChange(sitePath) {
    if (!sitePath) {
      this.syncStateToIframes();
      return;
    }

    localStorage.setItem('udmi_site_model_path', sitePath);
    this.syncStateToIframes();
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
