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
};

// -----------------------------------------------------------------------------
// Initialization & Tab Navigation
// -----------------------------------------------------------------------------

document.addEventListener("DOMContentLoaded", () => {
  setupNavigation();
  setupEventHandlers();
  setupSSE();

  // Initial load
  loadCapabilities();
  loadBridgeheadStatus();
  loadPortfolio();
  loadDevices();
  loadRollouts();
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

function switchTab(tabId) {
  state.activeTab = tabId;

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

  // Trigger pane-specific refreshes
  if (tabId === "portfolio") loadPortfolio();
  if (tabId === "devices") loadDevices();
  if (tabId === "admin") loadBridgeheadStatus();
  if (tabId === "rollout") loadRollouts();
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
      updateComponentCard("card-mqtt", data.components.mqtt_broker);
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
        <td>${formatTime(dev.last_seen)}</td>
        <td>
          <button class="btn btn-secondary btn-sm" onclick="selectDevice('${escapeHtml(dev.registry_id)}', '${escapeHtml(dev.device_id)}')">
            Inspect
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
    document.getElementById("detail-lastseen").textContent = formatTime(data.metadata.last_seen);

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
