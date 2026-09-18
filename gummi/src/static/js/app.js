/**
 * GUMMI Frontend Client Application
 */

const API_BASE = "";

// Global App State
const state = {
  activeTab: "portfolio",
  devices: {
    data: [],
    total: 0,
    limit: 25,
    offset: 0,
    filters: {
      search: "",
      registry: "",
      prefix: "",
      make: "",
    },
  },
  selectedDevice: null,
  rollouts: [],
  explorer: {
    registries: [],
    devices: [],
    totalDevices: 0,
    activeRegistry: null,
    activeDevice: null,
    activeProperty: null,
    lastSetHash: null,
  },
};

// -----------------------------------------------------------------------------
// Initialization & Tab Navigation
// -----------------------------------------------------------------------------

document.addEventListener("DOMContentLoaded", () => {
  setupNavigation();
  setupEventHandlers();
  setupSSE();
  setupConsole();
  initHashRouting();
  initExplorerTheme();

  // Initial load
  loadCapabilities();
  loadBridgeheadStatus();
  loadPortfolio();
  loadDevices();
  loadRollouts();
  loadExplorerRegistries();
});

function setupNavigation() {
  const tabs = document.querySelectorAll(".nav-tab");
  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      const targetTab = tab.dataset.tab;
      switchTab(targetTab);
    });
  });
}

function switchTab(tabId, updateHash = true) {
  state.activeTab = tabId;
  document.body.classList.toggle("tab-explorer", tabId === "explorer");

  // Update nav buttons
  document.querySelectorAll(".nav-tab").forEach((t) => {
    t.classList.toggle("active", t.dataset.tab === tabId);
  });

  // Update panes
  document.querySelectorAll(".tab-pane").forEach((pane) => {
    pane.classList.remove("active");
  });
  const targetPane = document.getElementById(`pane-${tabId}`);
  if (targetPane) {
    targetPane.classList.add("active");
  }

  if (updateHash) {
    if (tabId === "explorer") {
      setExplorerHash(state.explorer.activeRegistry, state.explorer.activeDevice, state.explorer.activeProperty);
    } else {
      const h = `#${tabId}`;
      state.explorer.lastSetHash = h;
      window.location.hash = h;
    }
  }

  // Trigger pane-specific refreshes
  if (tabId === "portfolio") loadPortfolio();
  if (tabId === "devices") loadDevices();
  if (tabId === "admin") loadBridgeheadStatus();
  if (tabId === "rollout") loadRollouts();
  if (tabId === "explorer") loadExplorerRegistries();
}

// -----------------------------------------------------------------------------
// API Calls & Data Loaders
// -----------------------------------------------------------------------------

async function loadCapabilities() {
  try {
    const res = await fetch(`${API_BASE}/api/system/capabilities`);
    const data = await res.json();
    const uufiBadge = document.getElementById("uufi-badge");
    const uufiText = document.getElementById("uufi-status-text");
    if (data.uufi_status === "ACTIVE") {
      uufiBadge.className = "badge badge-success";
      uufiText.textContent = "UUFI: Connected";
    } else {
      uufiBadge.className = "badge badge-neutral";
      uufiText.textContent = "UUFI: Local/Offline";
    }

    const seedBtn = document.getElementById("btn-seed-mapping");
    if (seedBtn) {
      if (data.enable_mapping_seed || (data.features && data.features.includes("mapping_seed"))) {
        seedBtn.style.display = "inline-block";
      } else {
        seedBtn.style.display = "none";
      }
    }
  } catch (err) {
    console.warn("Failed to load capabilities:", err);
  }
}

async function loadBridgeheadStatus() {
  try {
    const res = await fetch(`${API_BASE}/api/bridgehead/status`);
    const data = await res.json();

    const sysBadge = document.getElementById("system-badge");
    const sysText = document.getElementById("system-status-text");
    if (data.overall_status === "HEALTHY") {
      sysBadge.className = "badge badge-success";
      sysText.textContent = "System: Healthy";
    } else {
      sysBadge.className = "badge badge-danger";
      sysText.textContent = `System: ${data.overall_status}`;
    }

    // Update component cards in Admin view
    if (data.components) {
      updateComponentCard("card-uufi", data.components.uufi_service || data.components.mqtt_broker);
      updateComponentCard("card-pg", data.components.postgres);
      updateComponentCard("card-influx", data.components.influxdb);
      updateComponentCard("card-etcd", data.components.etcd);
    }
  } catch (err) {
    console.warn("Failed to load bridgehead status:", err);
  }
}

function updateComponentCard(cardId, comp) {
  const card = document.getElementById(cardId);
  if (!card || !comp) return;
  const statusEl = card.querySelector(".comp-status");
  const latEl = card.querySelector(".comp-lat");
  const isUp = comp.status === "UP";
  statusEl.textContent = comp.status;
  statusEl.className = `comp-status ${isUp ? "status-up" : "status-down"}`;
  if (latEl && comp.latency_ms !== undefined) {
    latEl.textContent = `${comp.latency_ms}ms`;
  }
}

async function loadPortfolio() {
  try {
    const [summaryRes, alertsRes] = await Promise.all([
      fetch(`${API_BASE}/api/portfolio/summary`),
      fetch(`${API_BASE}/api/portfolio/alerts?limit=10`),
    ]);

    const summary = await summaryRes.json();
    const alerts = await alertsRes.json();

    // Update Counter Cards
    document.getElementById("metric-total-devices").textContent = summary.device_counts.total;
    document.getElementById("metric-online-devices").textContent = summary.device_counts.online;
    document.getElementById("metric-offline-devices").textContent = summary.device_counts.offline;
    document.getElementById("metric-error-devices").textContent = summary.device_counts.error;

    // Render Alerts Table
    const alertsTbody = document.getElementById("portfolio-alerts-table");
    alertsTbody.innerHTML = "";
    if (!alerts.alerts || alerts.alerts.length === 0) {
      alertsTbody.innerHTML = '<tr><td colspan="5" class="empty-state">No critical alerts detected</td></tr>';
    } else {
      alerts.alerts.forEach((alert) => {
        const row = document.createElement("tr");
        row.innerHTML = `
          <td>${formatTime(alert.timestamp)}</td>
          <td><strong>${escapeHtml(alert.device_id)}</strong></td>
          <td><span class="badge ${alert.level >= 800 ? "badge-danger" : "badge-neutral"}">${alert.level}</span></td>
          <td>${escapeHtml(alert.category)}</td>
          <td>${escapeHtml(alert.message)}</td>
        `;
        alertsTbody.appendChild(row);
      });
    }
  } catch (err) {
    console.warn("Failed to load portfolio:", err);
  }
}

async function loadDevices() {
  const { limit, offset, filters } = state.devices;
  const params = new URLSearchParams({
    limit: limit.toString(),
    offset: offset.toString(),
  });
  if (filters.search) params.append("search", filters.search);
  if (filters.registry) params.append("registry_id", filters.registry);
  if (filters.prefix) params.append("device_prefix", filters.prefix);
  if (filters.make) params.append("make", filters.make);

  try {
    const res = await fetch(`${API_BASE}/api/devices?${params.toString()}`);
    const data = await res.json();
    state.devices.data = data.devices || [];
    state.devices.total = data.total || 0;

    renderDevicesTable();
  } catch (err) {
    console.warn("Failed to load devices:", err);
  }
}

function renderDevicesTable() {
  const tbody = document.getElementById("devices-table-body");
  tbody.innerHTML = "";

  if (state.devices.data.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" class="empty-state">No devices found in database</td></tr>';
  } else {
    state.devices.data.forEach((dev) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td><strong>${escapeHtml(dev.device_id)}</strong></td>
        <td>${escapeHtml(dev.registry_id)}</td>
        <td>${escapeHtml(dev.make)} / ${escapeHtml(dev.model)}</td>
        <td>${escapeHtml(dev.software_version || "—")}</td>
        <td><span class="badge badge-success">${dev.liveness_status}</span></td>
        <td><code>${formatIso(dev.last_seen)}</code></td>
        <td>
          <button class="btn btn-secondary btn-sm" onclick="selectDevice('${escapeHtml(dev.registry_id)}', '${escapeHtml(dev.device_id)}')">
            Inspect
          </button>
          <button class="btn btn-secondary btn-sm" title="Inspect raw ETCD key-value properties" onclick="navigateToExplorer('${escapeHtml(dev.registry_id)}', '${escapeHtml(dev.device_id)}')">
            🗄️ ETCD
          </button>
        </td>
      `;
      tbody.appendChild(row);
    });
  }

  // Update pagination info
  const start = state.devices.total > 0 ? state.devices.offset + 1 : 0;
  const end = Math.min(state.devices.offset + state.devices.limit, state.devices.total);
  document.getElementById("pagination-info").textContent = `Showing ${start}–${end} of ${state.devices.total} devices`;

  document.getElementById("btn-prev-page").disabled = state.devices.offset <= 0;
  document.getElementById("btn-next-page").disabled = end >= state.devices.total;
}

window.selectDevice = async function (registryId, deviceId) {
  state.selectedDevice = { registryId, deviceId };

  // Set titles
  document.getElementById("detail-device-title").textContent = deviceId;
  document.getElementById("detail-registry-badge").textContent = `Registry: ${registryId}`;

  // Pre-fill Configuration view
  document.getElementById("config-reg-input").value = registryId;
  document.getElementById("config-dev-input").value = deviceId;

  // Load details
  try {
    const res = await fetch(`${API_BASE}/api/devices/${encodeURIComponent(registryId)}/${encodeURIComponent(deviceId)}`);
    const data = await res.json();

    // Render metadata
    document.getElementById("detail-make").textContent = data.metadata.make || "—";
    document.getElementById("detail-model").textContent = data.metadata.model || "—";
    document.getElementById("detail-serial").textContent = data.metadata.serial_no || "—";
    document.getElementById("detail-location").textContent = `${data.metadata.room || "Room ?"} / ${data.metadata.floor || "Floor ?"}`;
    document.getElementById("detail-software").textContent = JSON.stringify(data.metadata.software || {});
    document.getElementById("detail-lastseen").textContent = formatIso(data.metadata?.last_seen || data.state?.system?.last_seen);

    // Render points table
    const pointsTbody = document.getElementById("detail-points-table");
    pointsTbody.innerHTML = "";
    const points = data.state?.pointset?.points || {};
    const pointKeys = Object.keys(points);
    if (pointKeys.length === 0) {
      pointsTbody.innerHTML = '<tr><td colspan="5" class="empty-state">No telemetry points reported</td></tr>';
    } else {
      pointKeys.forEach((ptName) => {
        const pt = points[ptName];
        const row = document.createElement("tr");
        row.innerHTML = `
          <td><code>${escapeHtml(ptName)}</code></td>
          <td><span class="badge badge-neutral">${escapeHtml(pt.value_state || "unknown")}</span></td>
          <td>${escapeHtml(pt.units || "—")}</td>
          <td>${pt.level !== undefined ? pt.level : "—"}</td>
          <td>${escapeHtml(pt.message || "OK")}</td>
        `;
        pointsTbody.appendChild(row);
      });
    }

    // Render state vs config diff
    document.getElementById("detail-reported-state").textContent = JSON.stringify(data.state || {}, null, 2);
    document.getElementById("detail-desired-config").textContent = JSON.stringify(data.config || {}, null, 2);

    // Load message lifecycle
    loadDeviceMessages(registryId, deviceId);

    switchTab("device-detail");
  } catch (err) {
    console.error("Failed to load device details:", err);
  }
};

async function loadDeviceMessages(registryId, deviceId) {
  const container = document.getElementById("detail-messages-timeline");
  container.innerHTML = '<div class="empty-state">Loading lifecycle messages...</div>';

  try {
    const res = await fetch(`${API_BASE}/api/devices/${encodeURIComponent(registryId)}/${encodeURIComponent(deviceId)}/messages`);
    const data = await res.json();
    const msgs = data.messages || [];

    if (msgs.length === 0) {
      container.innerHTML = '<div class="empty-state">No lifecycle messages (model, discovery, propose) recorded for this device.</div>';
      return;
    }

    container.innerHTML = "";
    msgs.forEach((m, idx) => {
      const card = document.createElement("div");
      card.style.borderLeft = `4px solid ${m.sub_type === "propose" ? "#1a73e8" : m.sub_type === "events" ? "#f2994a" : "#34a853"}`;
      card.style.background = "#f8f9fa";
      card.style.padding = "0.75rem 1rem";
      card.style.marginBottom = "0.75rem";
      card.style.borderRadius = "0 4px 4px 0";

      const badgeClass = m.sub_type === "propose" ? "badge-primary" : m.sub_type === "events" ? "badge-warning" : "badge-success";
      const updateFromTag = m.updateFrom ? `<span style="font-size:0.75rem; color:#5f6368; margin-left:0.5rem;">[updateFrom: <code>${escapeHtml(m.updateFrom)}</code>]</span>` : "";

      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.25rem;">
          <div>
            <strong>Step ${idx + 1}: ${m.sub_type.toUpperCase()} / ${m.sub_folder}</strong>
            <span class="badge ${badgeClass}" style="margin-left:0.5rem;">${m.sub_type}</span>
            ${updateFromTag}
          </div>
          <span style="font-size:0.8rem; color:#5f6368;">${formatTime(m.timestamp)} | Source: <code>${escapeHtml(m.source || "system")}</code></span>
        </div>
        <pre class="code-box" style="margin-top:0.25rem; font-size:0.8rem; max-height:160px; overflow-y:auto;">${escapeHtml(JSON.stringify(m.payload, null, 2))}</pre>
      `;
      container.appendChild(card);
    });
  } catch (err) {
    container.innerHTML = `<div class="empty-state">Error loading messages: ${escapeHtml(err.message)}</div>`;
  }
}

window.triggerMappingScenario = async function () {
  const regId = state.selectedDevice?.registryId || "ZZ-TRI-FECTA";
  const btn = document.getElementById("btn-seed-mapping");
  if (btn) btn.disabled = true;

  try {
    const res = await fetch(`${API_BASE}/api/mapping/run`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ registry_id: regId }),
    });
    const result = await res.json();
    alert(`Mapping scenario populated successfully (${result.records_inserted} messages inserted).`);
    if (state.selectedDevice) {
      loadDeviceMessages(state.selectedDevice.registryId, state.selectedDevice.deviceId);
    }
  } catch (err) {
    alert(`Failed to trigger mapping: ${err.message}`);
  } finally {
    if (btn) btn.disabled = false;
  }
};

async function loadRollouts() {
  try {
    const res = await fetch(`${API_BASE}/api/rollouts`);
    const rollouts = await res.json();
    state.rollouts = rollouts || [];

    const listEl = document.getElementById("rollouts-list");
    listEl.innerHTML = "";
    if (state.rollouts.length === 0) {
      listEl.innerHTML = '<div class="empty-state">No active rollout campaigns. Click "New Rollout Campaign" to create one.</div>';
      return;
    }

    state.rollouts.forEach((r) => {
      const card = document.createElement("div");
      card.className = "panel";
      card.style.marginBottom = "1rem";
      const pct = r.total_devices > 0 ? Math.round((r.converged_devices / r.total_devices) * 100) : 0;
      card.innerHTML = `
        <div class="panel-header" style="display:flex; justify-content:space-between; align-items:center;">
          <h4>${escapeHtml(r.name)} <span class="badge ${r.status === "COMPLETED" ? "badge-success" : "badge-neutral"}">${r.status}</span></h4>
          <div>
            <button class="btn btn-secondary btn-sm" onclick="pauseRollout(${r.id})">Pause</button>
            <button class="btn btn-secondary btn-sm" onclick="cancelRollout(${r.id})">Cancel</button>
          </div>
        </div>
        <div class="panel-body">
          <div style="margin-bottom:0.5rem; font-size:0.85rem;">Convergence: ${r.converged_devices} / ${r.total_devices} devices (${pct}%)</div>
          <div style="background:#e8eaed; border-radius:4px; height:8px; width:100%; overflow:hidden;">
            <div style="background:var(--primary-color); height:100%; width:${pct}%;"></div>
          </div>
        </div>
      `;
      listEl.appendChild(card);
    });
  } catch (err) {
    console.warn("Failed to load rollouts:", err);
  }
}

window.pauseRollout = async function (id) {
  await fetch(`${API_BASE}/api/rollouts/${id}/pause`, { method: "POST" });
  loadRollouts();
};

window.cancelRollout = async function (id) {
  await fetch(`${API_BASE}/api/rollouts/${id}/cancel`, { method: "POST" });
  loadRollouts();
};

// -----------------------------------------------------------------------------
// Event Handlers & Submissions
// -----------------------------------------------------------------------------

function setupEventHandlers() {
  // Refresh Buttons
  document.getElementById("btn-refresh-portfolio").addEventListener("click", loadPortfolio);
  document.getElementById("btn-refresh-devices").addEventListener("click", loadDevices);
  document.getElementById("btn-probe-admin").addEventListener("click", loadBridgeheadStatus);

  // ETCD Explorer Controls
  const btnRefreshAll = document.getElementById("btn-refresh-explorer-all");
  if (btnRefreshAll) btnRefreshAll.addEventListener("click", loadExplorerRegistries);

  const btnRefreshRegs = document.getElementById("btn-refresh-explorer-registries");
  if (btnRefreshRegs) btnRefreshRegs.addEventListener("click", loadExplorerRegistries);

  const btnRefreshDevs = document.getElementById("btn-refresh-explorer-devices");
  if (btnRefreshDevs) {
    btnRefreshDevs.addEventListener("click", () => {
      if (state.explorer.activeRegistry) {
        loadExplorerDevices(state.explorer.activeRegistry);
      }
    });
  }

  const btnRefreshProps = document.getElementById("btn-refresh-explorer-properties");
  if (btnRefreshProps) {
    btnRefreshProps.addEventListener("click", () => {
      if (state.explorer.activeRegistry && state.explorer.activeDevice) {
        loadExplorerProperties(state.explorer.activeRegistry, state.explorer.activeDevice);
      }
    });
  }

  const btnViewRawEtcd = document.getElementById("btn-view-raw-etcd");
  if (btnViewRawEtcd) {
    btnViewRawEtcd.addEventListener("click", () => {
      if (state.selectedDevice) {
        navigateToExplorer(state.selectedDevice.registryId, state.selectedDevice.deviceId);
      } else {
        switchTab("explorer");
      }
    });
  }

  // ETCD Explorer Search Filters
  const searchRegInput = document.getElementById("search-explorer-registries");
  if (searchRegInput) {
    let debounceTimer;
    searchRegInput.addEventListener("input", (e) => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        const query = e.target.value.toLowerCase().trim();
        const filtered = state.explorer.registries.filter((r) => r.toLowerCase().includes(query));
        renderExplorerRegistries(filtered, state.explorer.activeRegistry);
        updateExplorerCounts();
      }, 150);
    });
  }

  const searchDevInput = document.getElementById("search-explorer-devices");
  if (searchDevInput) {
    let debounceTimer;
    searchDevInput.addEventListener("input", (e) => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        const query = e.target.value.toLowerCase().trim();
        const filtered = state.explorer.devices.filter((d) => d.toLowerCase().includes(query));
        renderExplorerDevices(filtered, state.explorer.activeDevice);
        updateExplorerCounts();
      }, 150);
    });
  }

  // Filters
  document.getElementById("btn-apply-filters").addEventListener("click", () => {
    state.devices.filters.search = document.getElementById("filter-search").value.trim();
    state.devices.filters.registry = document.getElementById("filter-registry").value.trim();
    state.devices.filters.prefix = document.getElementById("filter-prefix").value.trim();
    state.devices.filters.make = document.getElementById("filter-make").value.trim();
    state.devices.offset = 0;
    loadDevices();
  });

  document.getElementById("btn-clear-filters").addEventListener("click", () => {
    document.getElementById("filter-search").value = "";
    document.getElementById("filter-registry").value = "";
    document.getElementById("filter-prefix").value = "";
    document.getElementById("filter-make").value = "";
    state.devices.filters = { search: "", registry: "", prefix: "", make: "" };
    state.devices.offset = 0;
    loadDevices();
  });

  // Pagination
  document.getElementById("btn-prev-page").addEventListener("click", () => {
    if (state.devices.offset > 0) {
      state.devices.offset = Math.max(0, state.devices.offset - state.devices.limit);
      loadDevices();
    }
  });

  document.getElementById("btn-next-page").addEventListener("click", () => {
    if (state.devices.offset + state.devices.limit < state.devices.total) {
      state.devices.offset += state.devices.limit;
      loadDevices();
    }
  });

  // Configuration Publish
  document.getElementById("btn-publish-config").addEventListener("click", async () => {
    const regId = document.getElementById("config-reg-input").value.trim();
    const devId = document.getElementById("config-dev-input").value.trim();
    const subFolder = document.getElementById("config-subfolder-select").value;
    const editorText = document.getElementById("config-payload-editor").value;
    const statusMsg = document.getElementById("config-status-msg");

    if (!regId || !devId) {
      statusMsg.style.color = "var(--danger)";
      statusMsg.textContent = "Please specify target registry and device ID.";
      return;
    }

    let payload;
    try {
      payload = JSON.parse(editorText);
    } catch (e) {
      statusMsg.style.color = "var(--danger)";
      statusMsg.textContent = "Invalid JSON payload format.";
      return;
    }

    try {
      statusMsg.style.color = "var(--primary-color)";
      statusMsg.textContent = "Publishing to UUFI...";
      const res = await fetch(`${API_BASE}/api/devices/${encodeURIComponent(regId)}/${encodeURIComponent(devId)}/config`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sub_folder: subFolder, payload }),
      });
      const data = await res.json();
      statusMsg.style.color = "var(--success)";
      statusMsg.textContent = `✓ ${data.message} (tx: ${data.transaction_id.substring(0, 8)})`;
    } catch (err) {
      statusMsg.style.color = "var(--danger)";
      statusMsg.textContent = "Failed to dispatch configuration.";
    }
  });

  // New Rollout Campaign Button
  document.getElementById("btn-new-rollout").addEventListener("click", async () => {
    const name = prompt("Enter Rollout Campaign Name:", "HVAC Firmware Upgrade v2.5");
    if (!name) return;
    await fetch(`${API_BASE}/api/rollouts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name,
        target_filter: { make: "Acme" },
        target_subfolder: "system",
        target_payload: { system: { software: { system: "2.5.0" } } },
        batch_size: 5,
        batch_interval_sec: 30,
      }),
    });
    loadRollouts();
  });
}

// -----------------------------------------------------------------------------
// Real-Time Server-Sent Events (SSE)
// -----------------------------------------------------------------------------

function setupSSE() {
  if (!window.EventSource) return;
  const evtSource = new EventSource(`${API_BASE}/api/stream/events`);

  evtSource.addEventListener("device_state", (e) => {
    // If on devices table, refresh
    if (state.activeTab === "devices") loadDevices();
    if (state.activeTab === "portfolio") loadPortfolio();
  });

  evtSource.addEventListener("alert", (e) => {
    if (state.activeTab === "portfolio") loadPortfolio();
  });

  evtSource.addEventListener("rollout_progress", (e) => {
    if (state.activeTab === "rollout") loadRollouts();
  });
}

// -----------------------------------------------------------------------------
// Utilities
// -----------------------------------------------------------------------------

function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function formatTime(isoStr) {
  if (!isoStr) return "—";
  try {
    const d = new Date(isoStr);
    return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  } catch (e) {
    return isoStr;
  }
}

function formatIso(isoStr) {
  if (!isoStr || isoStr === "None") return "—";
  let s = String(isoStr).trim();
  if (s.includes(" ") && !s.includes("T")) {
    s = s.replace(" ", "T");
  }
  s = s.replace(/\.\d+/, "").replace(/\+00:00$/, "Z");
  if (!s.endsWith("Z") && !s.includes("+") && !s.slice(10).includes("-")) {
    s += "Z";
  }
  return s;
}

// -----------------------------------------------------------------------------
// Jetski Task Console (xterm.js & backend tmux gummi~agent)
// -----------------------------------------------------------------------------

let term = null;
let fitAddon = null;
let isConsoleOpen = false;
let consoleOffset = 0;
let consolePollTimer = null;
let terminalResizeObserver = null;
let hasWrittenTerminalNotice = false;
const CONSOLE_PROJECT = "gummi";

function updateJetskiButtonState(buttonState, tooltip) {
  const btnJetski = document.getElementById("btn-jetski");
  if (!btnJetski) return;

  let state = "blue";
  const s = String(buttonState || "").toLowerCase();
  if (s === "red" || s === "error") {
    state = "red";
  } else if (s === "yellow" || s === "active" || s === "busy" || s === "working") {
    state = "yellow";
  } else if (s === "green" || s === "idle" || s === "running_idle" || s === "ready") {
    state = "green";
  } else if (s === "blue" || s === "not_running" || s === "stopped") {
    state = "blue";
  }

  btnJetski.classList.remove(
    "btn-jetski-blue", "btn-jetski-red", "btn-jetski-yellow", "btn-jetski-green",
    "state-blue", "state-red", "state-yellow", "state-green",
    "state-not-running", "state-error", "state-active", "state-idle"
  );

  btnJetski.classList.add(`btn-jetski-${state}`);
  btnJetski.classList.add(`state-${state}`);
  btnJetski.setAttribute("data-color", state);

  if (state === "blue") {
    btnJetski.classList.add("state-not-running");
    btnJetski.title = tooltip || "Jetski Agent: Not running, no error";
  } else if (state === "red") {
    btnJetski.classList.add("state-error");
    btnJetski.title = tooltip || "Jetski Agent: Not running, error";
  } else if (state === "yellow") {
    btnJetski.classList.add("state-active");
    btnJetski.title = tooltip || "Jetski Agent: Actively doing something";
  } else if (state === "green") {
    btnJetski.classList.add("state-idle");
    btnJetski.title = tooltip || "Jetski Agent: Running, idle";
  }
}

async function checkJetskiStatus() {
  try {
    const res = await fetch(`${API_BASE}/api/project/status?project=${encodeURIComponent(CONSOLE_PROJECT)}`);
    if (res.ok) {
      const data = await res.json();
      if (data.diagnostics) {
        const diag = Object.assign({}, data.diagnostics, {
          running: data.running,
          button_state: data.button_state || data.diagnostics.button_state,
        });
        updateDiagnosticsUI(diag, data.running);
      } else if (data.button_state) {
        updateJetskiButtonState(data.button_state);
      } else if (data.running) {
        updateJetskiButtonState("green");
      } else {
        updateJetskiButtonState("blue");
      }
    } else {
      let errMsg = "Status check failed";
      try {
        const errData = await res.json();
        errMsg = errData.message || errData.error || errMsg;
      } catch (_) {}
      updateJetskiButtonState("red", `Jetski Agent: ${errMsg}`);
    }
  } catch (e) {
    updateJetskiButtonState("red", "Jetski Agent: Disconnected");
  }
}

function setupConsole() {
  const btnJetski = document.getElementById("btn-jetski");
  const btnClose = document.getElementById("btn-close-console");
  const btnExpand = document.getElementById("btn-expand-console");
  const btnClear = document.getElementById("btn-clear-console");
  const btnKill = document.getElementById("btn-kill-console");
  const btnAlertRestart = document.getElementById("btn-alert-restart");

  if (btnJetski) {
    btnJetski.addEventListener("click", () => toggleConsole());
  }
  if (btnClose) {
    btnClose.addEventListener("click", () => setConsoleVisible(false));
  }
  if (btnExpand) {
    btnExpand.addEventListener("click", () => toggleConsoleExpand());
  }
  if (btnClear) {
    btnClear.addEventListener("click", () => clearConsole());
  }
  if (btnKill) {
    btnKill.addEventListener("click", () => killAndRestartConsole());
  }
  if (btnAlertRestart) {
    btnAlertRestart.addEventListener("click", () => killAndRestartConsole());
  }

  // Handle window resize & orientation change for xterm
  const handleViewportResize = () => {
    if (isConsoleOpen && fitAddon && term) {
      try {
        fitAddon.fit();
        syncTermSize();
      } catch (e) {}
    }
  };
  window.addEventListener("resize", handleViewportResize);
  window.addEventListener("orientationchange", () => {
    setTimeout(handleViewportResize, 100);
  });

  // Initial status check and periodic polling
  checkJetskiStatus();
  setInterval(checkJetskiStatus, 2000);
}

function updateDiagnosticsUI(diag, isRunning) {
  if (!diag) return;
  const statusText = document.getElementById("console-status-text");
  const alertBanner = document.getElementById("console-alert-banner");
  const alertMsg = document.getElementById("console-alert-msg");
  const sessionBadge = document.getElementById("console-session-badge");

  // Determine running state accurately
  let running = false;
  if (isRunning !== undefined) {
    running = !!isRunning;
  } else if (diag.running !== undefined) {
    running = !!diag.running;
  } else if (diag.state === "active" || diag.state === "busy" || diag.state === "idle" || diag.state === "auth_required") {
    running = (diag.state !== "not_running" && diag.state !== "error");
  }

  let buttonState = diag.button_state;
  if (!buttonState) {
    if (!running) {
      if (diag.severity === "error" || diag.state === "error" || (diag.exit_code !== undefined && diag.exit_code !== 0)) {
        buttonState = "red";
      } else {
        buttonState = "blue";
      }
    } else {
      // Session is running!
      if (diag.active || diag.state === "active" || diag.state === "busy" || diag.state === "working") {
        buttonState = "yellow";
      } else {
        buttonState = "green";
      }
    }
  }
  updateJetskiButtonState(buttonState, diag.status_text);

  if (sessionBadge) {
    if (!running) {
      sessionBadge.className = "badge badge-neutral";
    } else if (buttonState === "yellow") {
      sessionBadge.className = "badge badge-warning";
    } else {
      sessionBadge.className = "badge badge-success";
    }
  }

  if (statusText) {
    statusText.textContent = diag.status_text || (buttonState === "yellow" ? "Actively Working" : (buttonState === "green" ? "Idle" : "Not Running"));
    statusText.className = "console-status-text";
    if (diag.severity === "warning" || diag.state === "auth_required") {
      statusText.classList.add("status-warning");
    } else if (diag.severity === "error" || diag.state === "error" || diag.state === "key_error" || buttonState === "red") {
      statusText.classList.add("status-error");
    } else if (buttonState === "yellow" || diag.state === "active") {
      statusText.classList.add("status-warning");
    } else if (diag.severity === "success" || buttonState === "green" || diag.state === "idle") {
      statusText.classList.add("status-active");
    } else {
      statusText.classList.add("status-info");
    }
  }

  if (alertBanner && alertMsg) {
    if (diag.alert) {
      alertBanner.style.display = "flex";
      alertBanner.className = "console-alert-banner " + (diag.severity === "error" ? "alert-error" : "");
      alertMsg.innerHTML = escapeHtml(diag.alert).replace(
        /&quot;glogin&quot;|&#39;glogin&#39;|'glogin'/g,
        "<code>glogin</code>"
      );
    } else {
      alertBanner.style.display = "none";
    }
  }
}

function initTerminal() {
  if (term) return;

  const container = document.getElementById("terminal-container");
  if (!container) return;

  if (typeof Terminal === "undefined") {
    console.warn("xterm Terminal is not defined; verify vendor assets are loaded.");
    return;
  }

  term = new Terminal({
    convertEol: false,
    scrollback: 10000,
    theme: {
      background: "#ffffff",
      foreground: "#202124",
      cursor: "#1a73e8",
      cursorAccent: "#ffffff",
      selectionBackground: "rgba(26, 115, 232, 0.25)",
      black: "#202124",
      red: "#c5221f",
      green: "#137333",
      yellow: "#b06000",
      blue: "#1a73e8",
      magenta: "#a142f4",
      cyan: "#007b83",
      white: "#dadce0",
      brightBlack: "#5f6368",
      brightRed: "#d93025",
      brightGreen: "#1e8e3e",
      brightYellow: "#f9ab00",
      brightBlue: "#4285f4",
      brightMagenta: "#af5cf7",
      brightCyan: "#12b5cb",
      brightWhite: "#ffffff",
    },
    fontFamily: '"Roboto Mono", monospace',
    fontSize: 13,
  });

  if (typeof FitAddon !== "undefined" && FitAddon.FitAddon) {
    fitAddon = new FitAddon.FitAddon();
    term.loadAddon(fitAddon);
  }

  term.open(container);

  term.attachCustomKeyEventHandler((e) => {
    if (e.type === "keydown") {
      e.stopPropagation();
    }
    return true;
  });

  term.onSelectionChange(() => {
    const sel = term.getSelection();
    if (sel && navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(sel).catch(() => {});
    }
  });

  const encoder = new TextEncoder();
  term.onData((data) => {
    const bytes = encoder.encode(data);
    const hexKeys = [];
    for (let i = 0; i < bytes.length; i++) {
      hexKeys.push(bytes[i].toString(16).padStart(2, "0"));
    }
    fetch(`${API_BASE}/api/project/term-input`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ projectName: CONSOLE_PROJECT, hexKeys: hexKeys }),
    }).catch((err) => console.error("Error sending input to term:", err));
  });

  container.addEventListener("click", () => {
    if (term) {
      term.focus();
      term.scrollToBottom();
    }
  });

  if (!terminalResizeObserver && typeof ResizeObserver !== "undefined") {
    let lastW = 0;
    let lastH = 0;
    terminalResizeObserver = new ResizeObserver(() => {
      const pane = document.getElementById("console-pane");
      if (pane && pane.style.display === "flex") {
        const w = container.clientWidth;
        const h = container.clientHeight;
        if (Math.abs(w - lastW) > 2 || Math.abs(h - lastH) > 2) {
          lastW = w;
          lastH = h;
          if (fitAddon) {
            try {
              fitAddon.fit();
              syncTermSize();
              if (term) term.scrollToBottom();
            } catch (e) {}
          }
        }
      }
    });
    terminalResizeObserver.observe(container);
  }

  window.term = term;
}

async function syncTermSize() {
  if (!term) return;
  const cols = term.cols || 120;
  const rows = term.rows || 30;
  try {
    await fetch(`${API_BASE}/api/project/term-resize`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ projectName: CONSOLE_PROJECT, cols: cols, rows: rows }),
    });
  } catch (e) {}
}

async function toggleConsole() {
  setConsoleVisible(!isConsoleOpen);
}

async function setConsoleVisible(visible) {
  isConsoleOpen = visible;
  const pane = document.getElementById("console-pane");
  if (!pane) return;

  if (visible) {
    pane.style.display = "flex";
    initTerminal();

    const statusText = document.getElementById("console-status-text");
    if (statusText) {
      statusText.textContent = "Connecting to gummi~agent...";
      statusText.className = "console-status-text status-info";
    }

    let startOk = true;
    try {
      const res = await fetch(`${API_BASE}/api/project/jetski`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ projectName: CONSOLE_PROJECT }),
      });
      if (!res.ok) {
        startOk = false;
        let errMsg = "Error starting session";
        try {
          const errData = await res.json();
          errMsg = errData.message || errData.error || errMsg;
        } catch (_) {}

        if (statusText) {
          statusText.textContent = "Error starting session";
          statusText.className = "console-status-text status-error";
        }
        updateJetskiButtonState("red", `Jetski Agent: ${errMsg}`);
        const alertBanner = document.getElementById("console-alert-banner");
        const alertMsg = document.getElementById("console-alert-msg");
        if (alertBanner && alertMsg) {
          alertBanner.style.display = "flex";
          alertBanner.className = "console-alert-banner alert-error";
          alertMsg.textContent = errMsg;
        }
        if (term) {
          term.write(`\r\n\x1b[1;31m[Jetski Session Error]\x1b[0m ${errMsg}\r\n`);
        }
      }
    } catch (e) {
      startOk = false;
      const errMsg = e.message || "Disconnected";
      if (statusText) {
        statusText.textContent = "Disconnected";
        statusText.className = "console-status-text status-error";
      }
      updateJetskiButtonState("red", `Jetski Agent: ${errMsg}`);
      const alertBanner = document.getElementById("console-alert-banner");
      const alertMsg = document.getElementById("console-alert-msg");
      if (alertBanner && alertMsg) {
        alertBanner.style.display = "flex";
        alertBanner.className = "console-alert-banner alert-error";
        alertMsg.textContent = errMsg;
      }
      if (term) {
        term.write(`\r\n\x1b[1;31m[Jetski Session Error]\x1b[0m ${errMsg}\r\n`);
      }
    }

    try {
      const stRes = await fetch(`${API_BASE}/api/project/status?project=${encodeURIComponent(CONSOLE_PROJECT)}`);
      if (stRes.ok) {
        const stData = await stRes.json();
        if (stData.diagnostics) {
          updateDiagnosticsUI(stData.diagnostics);
        }
      } else if (!startOk) {
        updateJetskiButtonState("red", "Jetski Agent: Error starting session");
      }
    } catch (e) {}

    setTimeout(() => {
      if (fitAddon) {
        try {
          fitAddon.fit();
          syncTermSize();
          if (term) term.scrollToBottom();
        } catch (e) {}
      }
      if (term) term.focus();
    }, 50);

    setTimeout(() => {
      if (fitAddon) {
        try {
          fitAddon.fit();
          syncTermSize();
          if (term) term.scrollToBottom();
        } catch (e) {}
      }
    }, 250);

    pollConsoleLog();
  } else {
    pane.style.display = "none";
    if (consolePollTimer) {
      clearTimeout(consolePollTimer);
      consolePollTimer = null;
    }
  }
}

function toggleConsoleExpand() {
  const pane = document.getElementById("console-pane");
  const btn = document.getElementById("btn-expand-console");
  if (!pane) return;
  pane.classList.toggle("expanded");
  if (btn) {
    btn.textContent = pane.classList.contains("expanded") ? "Restore" : "Expand";
  }
  setTimeout(() => {
    if (fitAddon) {
      try {
        fitAddon.fit();
        syncTermSize();
        if (term) term.scrollToBottom();
      } catch (e) {}
    }
    if (term) term.focus();
  }, 100);
  setTimeout(() => {
    if (fitAddon) {
      try {
        fitAddon.fit();
        syncTermSize();
        if (term) term.scrollToBottom();
      } catch (e) {}
    }
  }, 250);
}

function clearConsole() {
  if (term) term.reset();
  consoleOffset = 0;
  hasWrittenTerminalNotice = false;
}

async function killAndRestartConsole() {
  const statusText = document.getElementById("console-status-text");
  if (statusText) {
    statusText.textContent = "Killing session...";
    statusText.className = "console-status-text status-warning";
  }
  try {
    await fetch(`${API_BASE}/api/project/term-kill`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ projectName: CONSOLE_PROJECT }),
    });
  } catch (e) {}
  clearConsole();
  if (statusText) {
    statusText.textContent = "Restarting...";
    statusText.className = "console-status-text status-info";
  }
  try {
    const res = await fetch(`${API_BASE}/api/project/jetski`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ projectName: CONSOLE_PROJECT }),
    });
    if (!res.ok) {
      let errMsg = "Error starting session";
      try {
        const errData = await res.json();
        errMsg = errData.message || errData.error || errMsg;
      } catch (_) {}
      if (statusText) {
        statusText.textContent = "Error starting session";
        statusText.className = "console-status-text status-error";
      }
      updateJetskiButtonState("red", `Jetski Agent: ${errMsg}`);
      const alertBanner = document.getElementById("console-alert-banner");
      const alertMsg = document.getElementById("console-alert-msg");
      if (alertBanner && alertMsg) {
        alertBanner.style.display = "flex";
        alertBanner.className = "console-alert-banner alert-error";
        alertMsg.textContent = errMsg;
      }
      if (term) {
        term.write(`\r\n\x1b[1;31m[Jetski Session Error]\x1b[0m ${errMsg}\r\n`);
      }
    }
  } catch (e) {
    if (statusText) {
      statusText.textContent = "Disconnected";
      statusText.className = "console-status-text status-error";
    }
    updateJetskiButtonState("red", `Jetski Agent: ${e.message || "Disconnected"}`);
  }
  pollConsoleLog();
}

async function pollConsoleLog() {
  if (!isConsoleOpen) return;
  if (consolePollTimer) {
    clearTimeout(consolePollTimer);
    consolePollTimer = null;
  }

  try {
    const res = await fetch(
      `${API_BASE}/api/project/term-log?project=${encodeURIComponent(CONSOLE_PROJECT)}&offset=${consoleOffset}`
    );
    if (res.ok) {
      const data = await res.json();
      if (data.cleared) {
        if (term) term.reset();
        consoleOffset = 0;
        hasWrittenTerminalNotice = false;
      }
      if (data.diagnostics) {
        const diag = Object.assign({}, data.diagnostics, {
          running: data.running,
          button_state: data.button_state || data.diagnostics.button_state,
        });
        updateDiagnosticsUI(diag, data.running);
        if (
          data.diagnostics.alert &&
          !hasWrittenTerminalNotice &&
          term &&
          (!data.data || data.data.length === 0)
        ) {
          term.write(
            `\r\n\x1b[1;33m[GUMMI Task Console]\x1b[0m ${data.diagnostics.status_text}\r\n` +
            `\x1b[33m${data.diagnostics.alert}\x1b[0m\r\n\r\n`
          );
          hasWrittenTerminalNotice = true;
        }
      }
      if (data.data && term) {
        const rawString = window.atob(data.data);
        const bytes = new Uint8Array(rawString.length);
        for (let i = 0; i < rawString.length; i++) {
          bytes[i] = rawString.charCodeAt(i);
        }
        term.write(bytes, () => {
          term.scrollToBottom();
        });
      }
      if (data.offset !== undefined) {
        consoleOffset = data.offset;
      }
    }
  } catch (e) {
    console.error("Error polling console log:", e);
  }

  if (isConsoleOpen) {
    consolePollTimer = setTimeout(pollConsoleLog, 150);
  }
}

window.toggleConsole = toggleConsole;
window.setConsoleVisible = setConsoleVisible;
window.pollConsoleLog = pollConsoleLog;
window.updateDiagnosticsUI = updateDiagnosticsUI;
window.updateJetskiButtonState = updateJetskiButtonState;
window.checkJetskiStatus = checkJetskiStatus;

// -----------------------------------------------------------------------------
// ETCD Visual Explorer Logic (etcd_explorer parity)
function initExplorerTheme() {
  const saved = localStorage.getItem("etcd_explorer_theme") || "dark-theme";
  const pane = document.getElementById("pane-explorer");
  if (pane) {
    pane.classList.remove("dark-theme", "light-theme");
    pane.classList.add(saved);
  }
  const btn = document.getElementById("theme-toggle-explorer");
  if (btn) {
    btn.addEventListener("click", () => {
      if (pane.classList.contains("dark-theme")) {
        pane.classList.remove("dark-theme");
        pane.classList.add("light-theme");
        localStorage.setItem("etcd_explorer_theme", "light-theme");
      } else {
        pane.classList.remove("light-theme");
        pane.classList.add("dark-theme");
        localStorage.setItem("etcd_explorer_theme", "dark-theme");
      }
    });
  }
}

function naturalCompare(a, b) {
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: "base" });
}

function showToast(message) {
  const toast = document.getElementById("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.classList.remove("hidden");
  setTimeout(() => {
    toast.classList.add("hidden");
  }, 3500);
}

function setExplorerHash(reg, dev, prop) {
  let hash = "#explorer";
  if (reg) {
    hash += "/" + encodeURIComponent(reg);
    if (dev) {
      hash += "/" + encodeURIComponent(dev);
      if (prop) {
        hash += "/" + encodeURIComponent(prop);
      }
    }
  }
  state.explorer.lastSetHash = hash;
  window.location.hash = hash;
}

function initHashRouting() {
  window.addEventListener("hashchange", () => {
    if (window.location.hash === state.explorer.lastSetHash) {
      return;
    }
    parseHash();
  });

  if (window.location.hash) {
    parseHash();
  }
}

function parseHash() {
  const rawHash = window.location.hash.replace(/^#\/?/, "");
  if (!rawHash) return;

  const parts = rawHash.split("/").map(decodeURIComponent);
  const first = parts[0];

  // Check if hash points to another GUMMI tab
  const knownTabs = ["portfolio", "devices", "device-detail", "config", "rollout", "admin"];
  if (knownTabs.includes(first)) {
    switchTab(first, false);
    if (first === "device-detail" && parts[1] && parts[2]) {
      selectDevice(parts[1], parts[2]);
    }
    return;
  }

  // ETCD Explorer routing: either #explorer/... or legacy etcd_explorer pattern #/<reg>/<dev>/<prop>
  let targetReg = null;
  let targetDev = null;
  let targetProp = null;

  if (first === "explorer") {
    targetReg = parts[1] || null;
    targetDev = parts[2] || null;
    targetProp = parts[3] || null;
  } else {
    // Legacy etcd_explorer pattern: #/<reg>/<dev>/<prop>
    targetReg = parts[0] || null;
    targetDev = parts[1] || null;
    targetProp = parts[2] || null;
  }

  switchTab("explorer", false);

  if (targetReg) {
    const regPromise = (targetReg === state.explorer.activeRegistry && state.explorer.devices.length > 0)
      ? Promise.resolve()
      : selectExplorerRegistry(targetReg, false);

    regPromise.then(() => {
      if (targetDev) {
        const devPromise = (targetDev === state.explorer.activeDevice)
          ? Promise.resolve()
          : selectExplorerDevice(targetDev, false);

        devPromise.then(() => {
          if (targetProp) {
            highlightExplorerProperty(targetProp);
          }
        });
      }
    });
  }
}

async function loadExplorerRegistries() {
  const container = document.getElementById("explorer-registries-list");
  if (!container) return;
  container.innerHTML = '<div class="loading-state">Loading registries...</div>';

  try {
    const res = await fetch(`${API_BASE}/api/registries`);
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    state.explorer.registries = (data.registries || []).sort(naturalCompare);
    state.explorer.totalDevices = data.totalDevicesCount !== undefined ? data.totalDevicesCount : 0;

    updateExplorerCounts();
    renderExplorerRegistries(state.explorer.registries, state.explorer.activeRegistry);

    // If activeRegistry is no longer present, clear
    if (state.explorer.activeRegistry && !state.explorer.registries.includes(state.explorer.activeRegistry)) {
      clearExplorerDeviceSelection();
    }
  } catch (err) {
    container.innerHTML = `<div class="empty-state">❌ Error: ${err.message}</div>`;
    showToast("Failed to load registries from etcd");
  }
}

function renderExplorerRegistries(items, activeId) {
  const container = document.getElementById("explorer-registries-list");
  if (!container) return;
  container.innerHTML = "";

  if (items.length === 0) {
    container.innerHTML = '<div class="empty-state">No registries found</div>';
    return;
  }

  const fragment = document.createDocumentFragment();
  items.forEach((reg) => {
    const div = document.createElement("div");
    div.className = `list-item explorer-item ${reg === activeId ? "active" : ""}`;
    div.textContent = reg;
    div.addEventListener("click", () => {
      selectExplorerRegistry(reg, true);
    });
    fragment.appendChild(div);
  });
  container.appendChild(fragment);
}

async function selectExplorerRegistry(reg, updateHash = true) {
  state.explorer.activeRegistry = reg;
  renderExplorerRegistries(state.explorer.registries, reg);

  const label = document.getElementById("explorer-active-registry-label");
  if (label) label.textContent = reg;

  const btnRefDev = document.getElementById("btn-refresh-explorer-devices");
  if (btnRefDev) btnRefDev.disabled = false;

  const searchDev = document.getElementById("search-explorer-devices");
  if (searchDev) {
    searchDev.disabled = false;
    searchDev.value = "";
  }

  clearExplorerDeviceSelection();

  if (updateHash) {
    setExplorerHash(reg, null, null);
  }

  await loadExplorerDevices(reg);
}

async function loadExplorerDevices(registryId) {
  const container = document.getElementById("explorer-devices-list");
  if (!container) return;
  container.innerHTML = '<div class="loading-state">Loading devices...</div>';

  try {
    const res = await fetch(`${API_BASE}/api/registries/${encodeURIComponent(registryId)}/devices`);
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    state.explorer.devices = (data.devices || []).sort(naturalCompare);

    updateExplorerCounts();
    renderExplorerDevices(state.explorer.devices, state.explorer.activeDevice);
  } catch (err) {
    container.innerHTML = `<div class="empty-state">❌ Error: ${err.message}</div>`;
    showToast(`Failed to load devices for registry '${registryId}'`);
  }
}

function renderExplorerDevices(items, activeId) {
  const container = document.getElementById("explorer-devices-list");
  if (!container) return;
  container.innerHTML = "";

  if (items.length === 0) {
    container.innerHTML = '<div class="empty-state">No devices found in this registry</div>';
    return;
  }

  const fragment = document.createDocumentFragment();
  items.forEach((dev) => {
    const div = document.createElement("div");
    div.className = `list-item explorer-item ${dev === activeId ? "active" : ""}`;
    div.textContent = dev;
    div.addEventListener("click", () => {
      selectExplorerDevice(dev, true);
    });
    fragment.appendChild(div);
  });
  container.appendChild(fragment);
}

function clearExplorerDeviceSelection() {
  state.explorer.activeDevice = null;
  state.explorer.activeProperty = null;
  state.explorer.devices = [];
  updateExplorerCounts();

  const devList = document.getElementById("explorer-devices-list");
  if (devList) devList.innerHTML = '<div class="empty-state">Select a registry to view devices</div>';

  const devLabel = document.getElementById("explorer-active-device-label");
  if (devLabel) devLabel.textContent = "No device selected";

  const btnRefProp = document.getElementById("btn-refresh-explorer-properties");
  if (btnRefProp) btnRefProp.disabled = true;

  const propContent = document.getElementById("explorer-properties-content");
  if (propContent) propContent.innerHTML = '<div class="empty-state">Select a device to view properties</div>';
}

async function selectExplorerDevice(dev, updateHash = true) {
  state.explorer.activeDevice = dev;
  renderExplorerDevices(state.explorer.devices, dev);

  const label = document.getElementById("explorer-active-device-label");
  if (label) label.textContent = `${state.explorer.activeRegistry} / ${dev}`;

  const btnRefProp = document.getElementById("btn-refresh-explorer-properties");
  if (btnRefProp) btnRefProp.disabled = false;

  if (updateHash) {
    setExplorerHash(state.explorer.activeRegistry, dev, null);
  }

  await loadExplorerProperties(state.explorer.activeRegistry, dev);
}

async function loadExplorerProperties(registryId, deviceId) {
  const container = document.getElementById("explorer-properties-content");
  if (!container) return;
  container.innerHTML = '<div class="loading-state">Loading properties...</div>';

  try {
    const res = await fetch(
      `${API_BASE}/api/registries/${encodeURIComponent(registryId)}/devices/${encodeURIComponent(deviceId)}/properties`
    );
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    renderExplorerProperties(data.properties || {});
  } catch (err) {
    container.innerHTML = `<div class="empty-state">❌ Error: ${err.message}</div>`;
    showToast(`Failed to load properties for device '${deviceId}'`);
  }
}

function renderExplorerProperties(properties) {
  const container = document.getElementById("explorer-properties-content");
  if (!container) return;
  container.innerHTML = "";

  const keys = Object.keys(properties);
  if (keys.length === 0) {
    container.innerHTML = '<div class="empty-state">No properties found for this device</div>';
    return;
  }

  const table = document.createElement("table");
  table.className = "properties-table";

  const colgroup = document.createElement("colgroup");
  const colKey = document.createElement("col");
  colKey.className = "col-property-key";
  const colVal = document.createElement("col");
  colVal.className = "col-property-val";
  colgroup.appendChild(colKey);
  colgroup.appendChild(colVal);
  table.appendChild(colgroup);

  const devProps = keys.filter((k) => !k.startsWith("/c/")).sort(naturalCompare);
  const colProps = keys.filter((k) => k.startsWith("/c/")).sort(naturalCompare);

  if (devProps.length > 0) {
    appendExplorerGroupHeader(table, "Device Properties");
    devProps.forEach((k) => appendExplorerPropertyRow(table, k, properties[k]));
  }

  if (colProps.length > 0) {
    appendExplorerGroupHeader(table, `Collections (${colProps.length})`);
    colProps.forEach((k) => appendExplorerPropertyRow(table, k, properties[k]));
  }

  container.appendChild(table);

  if (state.explorer.activeProperty) {
    highlightExplorerProperty(state.explorer.activeProperty);
  }
}

function appendExplorerGroupHeader(table, title) {
  const tr = document.createElement("tr");
  const td = document.createElement("td");
  td.colSpan = 2;
  td.className = "property-group-title";
  td.textContent = title;
  tr.appendChild(td);
  table.appendChild(tr);
}

function appendExplorerPropertyRow(table, key, value) {
  const tr = document.createElement("tr");
  tr.className = "property-row";
  tr.dataset.key = key;
  tr.addEventListener("click", (e) => {
    if (!e.target.classList.contains("copy-btn") && !e.target.classList.contains("device-link") && e.target.tagName !== "A") {
      state.explorer.activeProperty = key;
      setExplorerHash(state.explorer.activeRegistry, state.explorer.activeDevice, key);
      highlightExplorerProperty(key);
    }
  });

  const tdKey = document.createElement("td");
  tdKey.className = "property-key";

  let targetDeviceId = null;
  if (key.startsWith("/c/") && key.includes(":")) {
    const colonIdx = key.indexOf(":");
    const potentialDev = key.substring(colonIdx + 1);
    if (key.startsWith("/c/bound_devices:") || (state.explorer.devices && state.explorer.devices.includes(potentialDev))) {
      targetDeviceId = potentialDev;
    }
  }

  if (targetDeviceId && state.explorer.activeRegistry) {
    const colonIdx = key.indexOf(":");
    const prefixSpan = document.createElement("span");
    prefixSpan.textContent = key.substring(0, colonIdx + 1);

    const link = document.createElement("a");
    link.href = `#explorer/${encodeURIComponent(state.explorer.activeRegistry)}/${encodeURIComponent(targetDeviceId)}`;
    link.className = "device-link";
    link.title = `Navigate to device ${targetDeviceId}`;
    link.textContent = targetDeviceId;
    link.addEventListener("click", (e) => {
      e.stopPropagation();
      selectExplorerDevice(targetDeviceId, true);
    });

    tdKey.appendChild(prefixSpan);
    tdKey.appendChild(link);
  } else {
    tdKey.textContent = key;
  }

  const tdVal = document.createElement("td");
  tdVal.className = "property-val";

  let isJson = false;
  let formattedJson = value;
  if (value && (value.startsWith("{") || value.startsWith("["))) {
    try {
      const parsed = JSON.parse(value);
      formattedJson = JSON.stringify(parsed, null, 2);
      isJson = true;
    } catch (e) {
      isJson = false;
    }
  }

  if (isJson) {
    const box = document.createElement("div");
    box.className = "json-box";
    const pre = document.createElement("pre");
    const code = document.createElement("code");
    code.textContent = formattedJson;
    pre.appendChild(code);
    box.appendChild(pre);

    const copyBtn = document.createElement("button");
    copyBtn.className = "copy-btn";
    copyBtn.textContent = "📋 Copy";
    copyBtn.addEventListener("click", () => {
      navigator.clipboard.writeText(formattedJson).then(() => {
        copyBtn.textContent = "✓ Copied!";
        setTimeout(() => {
          copyBtn.textContent = "📋 Copy";
        }, 2000);
      });
    });
    box.appendChild(copyBtn);
    tdVal.appendChild(box);
  } else {
    tdVal.textContent = value;
  }

  tr.appendChild(tdKey);
  tr.appendChild(tdVal);
  table.appendChild(tr);
}

function highlightExplorerProperty(key) {
  state.explorer.activeProperty = key;
  document.querySelectorAll(".property-row").forEach((row) => {
    if (row.dataset.key === key) {
      row.classList.add("highlighted");
      row.scrollIntoView({ behavior: "smooth", block: "center" });
    } else {
      row.classList.remove("highlighted");
    }
  });
}

function updateExplorerCounts() {
  const regQuery = (document.getElementById("search-explorer-registries")?.value || "").toLowerCase().trim();
  const filteredRegs = regQuery
    ? state.explorer.registries.filter((r) => r.toLowerCase().includes(regQuery))
    : state.explorer.registries;

  const regCountEl = document.getElementById("explorer-registries-count");
  const topRegEl = document.getElementById("top-registries-count");
  if (regCountEl) {
    regCountEl.textContent = regQuery
      ? `${filteredRegs.length}/${state.explorer.registries.length}`
      : state.explorer.registries.length;
  }
  if (topRegEl) {
    topRegEl.textContent = `Registries: ${state.explorer.registries.length}`;
  }

  const devQuery = (document.getElementById("search-explorer-devices")?.value || "").toLowerCase().trim();
  const filteredDevs = devQuery
    ? state.explorer.devices.filter((d) => d.toLowerCase().includes(devQuery))
    : state.explorer.devices;

  const devCountEl = document.getElementById("explorer-devices-count");
  const topDevEl = document.getElementById("top-devices-count");
  if (devCountEl) {
    devCountEl.textContent = devQuery
      ? `${filteredDevs.length}/${state.explorer.devices.length}`
      : state.explorer.devices.length;
  }
  if (topDevEl) {
    topDevEl.textContent = `Devices: ${state.explorer.totalDevices}`;
  }
}

window.navigateToExplorer = function (registryId, deviceId, propertyKey = null) {
  switchTab("explorer");
  if (registryId) {
    selectExplorerRegistry(registryId, false).then(() => {
      if (deviceId) {
        selectExplorerDevice(deviceId, false).then(() => {
          if (propertyKey) {
            highlightExplorerProperty(propertyKey);
            setExplorerHash(registryId, deviceId, propertyKey);
          } else {
            setExplorerHash(registryId, deviceId, null);
          }
        });
      } else {
        setExplorerHash(registryId, null, null);
      }
    });
  }
};
