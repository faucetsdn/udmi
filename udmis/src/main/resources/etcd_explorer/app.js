// ETCD Visual Explorer Single Page Application Logic

let cachedRegistries = [];
let cachedDevices = [];
let activeRegistry = null;
let activeDevice = null;
let activeProperty = null;
let isUpdatingHash = false;

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initEventListeners();
  loadRegistries().then(() => {
    parseHash();
  });
});

function initTheme() {
  const savedTheme = localStorage.getItem('etcd_explorer_theme') || 'dark-theme';
  document.body.className = savedTheme;
  const themeBtn = document.getElementById('theme-toggle');
  if (themeBtn) {
    themeBtn.addEventListener('click', () => {
      if (document.body.classList.contains('dark-theme')) {
        document.body.className = 'light-theme';
        localStorage.setItem('etcd_explorer_theme', 'light-theme');
      } else {
        document.body.className = 'dark-theme';
        localStorage.setItem('etcd_explorer_theme', 'dark-theme');
      }
    });
  }
}

function initEventListeners() {
  window.addEventListener('hashchange', () => {
    if (!isUpdatingHash) {
      parseHash();
    }
  });

  const searchRegInput = document.getElementById('search-registries');
  if (searchRegInput) {
    let debounceTimer;
    searchRegInput.addEventListener('input', (e) => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        const query = e.target.value.toLowerCase().trim();
        const filtered = cachedRegistries.filter(r => r.toLowerCase().includes(query));
        renderRegistriesList(filtered, activeRegistry);
      }, 150);
    });
  }

  const searchDevInput = document.getElementById('search-devices');
  if (searchDevInput) {
    let debounceTimer;
    searchDevInput.addEventListener('input', (e) => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        const query = e.target.value.toLowerCase().trim();
        const filtered = cachedDevices.filter(d => d.toLowerCase().includes(query));
        renderDevicesList(filtered, activeDevice);
      }, 150);
    });
  }

  document.getElementById('refresh-registries').addEventListener('click', () => {
    loadRegistries().then(() => {
      const query = document.getElementById('search-registries').value.toLowerCase().trim();
      const filtered = cachedRegistries.filter(r => r.toLowerCase().includes(query));
      renderRegistriesList(filtered, activeRegistry);
    });
  });

  document.getElementById('refresh-devices').addEventListener('click', () => {
    if (activeRegistry) {
      loadDevices(activeRegistry).then(() => {
        const query = document.getElementById('search-devices').value.toLowerCase().trim();
        const filtered = cachedDevices.filter(d => d.toLowerCase().includes(query));
        renderDevicesList(filtered, activeDevice);
      });
    }
  });

  document.getElementById('refresh-properties').addEventListener('click', () => {
    if (activeRegistry && activeDevice) {
      loadProperties(activeRegistry, activeDevice);
    }
  });
}

function setHash(reg, dev, prop) {
  isUpdatingHash = true;
  let hash = '';
  if (reg) {
    hash += '/' + encodeURIComponent(reg);
    if (dev) {
      hash += '/' + encodeURIComponent(dev);
      if (prop) {
        hash += '/' + encodeURIComponent(prop);
      }
    }
  }
  window.location.hash = hash;
  setTimeout(() => { isUpdatingHash = false; }, 50);
}

function parseHash() {
  const hash = window.location.hash.replace(/^#\/?/, '');
  if (!hash) {
    clearDeviceSelection();
    return;
  }

  const parts = hash.split('/').map(decodeURIComponent);
  const targetReg = parts[0] || null;
  const targetDev = parts[1] || null;
  const targetProp = parts[2] || null;

  if (targetReg && targetReg !== activeRegistry) {
    if (!cachedRegistries.includes(targetReg)) {
      showToast(`Registry '${targetReg}' not found in ETCD`);
      return;
    }
    selectRegistry(targetReg, false).then(() => {
      if (targetDev) {
        if (!cachedDevices.includes(targetDev)) {
          showToast(`Device '${targetDev}' not found in registry '${targetReg}'`);
          return;
        }
        selectDevice(targetDev, false).then(() => {
          if (targetProp) {
            highlightPropertyRow(targetProp);
          }
        });
      }
    });
  } else if (targetDev && targetDev !== activeDevice) {
    if (!cachedDevices.includes(targetDev)) {
      showToast(`Device '${targetDev}' not found in registry '${activeRegistry}'`);
      return;
    }
    selectDevice(targetDev, false).then(() => {
      if (targetProp) {
        highlightPropertyRow(targetProp);
      }
    });
  } else if (targetProp && targetProp !== activeProperty) {
    highlightPropertyRow(targetProp);
  }
}

async function loadRegistries() {
  const container = document.getElementById('registries-list');
  container.innerHTML = '<div class="loading-state">Loading registries...</div>';
  try {
    const res = await fetch('/api/registries');
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    cachedRegistries = data.registries || [];
    document.getElementById('registries-count').textContent = cachedRegistries.length;
    renderRegistriesList(cachedRegistries, activeRegistry);
  } catch (err) {
    container.innerHTML = `<div class="empty-state">❌ Error: ${err.message}</div>`;
    showToast('Failed to load registries from server');
  }
}

function renderRegistriesList(items, activeId) {
  const container = document.getElementById('registries-list');
  container.innerHTML = '';
  if (items.length === 0) {
    container.innerHTML = '<div class="empty-state">No registries found</div>';
    return;
  }

  const fragment = document.createDocumentFragment();
  items.forEach(reg => {
    const div = document.createElement('div');
    div.className = `list-item ${reg === activeId ? 'active' : ''}`;
    div.textContent = reg;
    div.addEventListener('click', () => {
      selectRegistry(reg, true);
    });
    fragment.appendChild(div);
  });
  container.appendChild(fragment);
}

async function selectRegistry(reg, updateUrl) {
  activeRegistry = reg;
  renderRegistriesList(cachedRegistries, activeRegistry);

  const label = document.getElementById('active-registry-label');
  label.textContent = reg;
  document.getElementById('refresh-devices').disabled = false;
  document.getElementById('search-devices').disabled = false;
  document.getElementById('search-devices').value = '';

  clearDeviceSelection();
  if (updateUrl) {
    setHash(reg, null, null);
  }

  await loadDevices(reg);
}

async function loadDevices(registryId) {
  const container = document.getElementById('devices-list');
  container.innerHTML = '<div class="loading-state">Loading devices...</div>';
  try {
    const res = await fetch(`/api/registries/${encodeURIComponent(registryId)}/devices`);
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    cachedDevices = data.devices || [];
    document.getElementById('devices-count').textContent = cachedDevices.length;
    renderDevicesList(cachedDevices, activeDevice);
  } catch (err) {
    container.innerHTML = `<div class="empty-state">❌ Error: ${err.message}</div>`;
    showToast('Failed to load devices from server');
  }
}

function renderDevicesList(items, activeId) {
  const container = document.getElementById('devices-list');
  container.innerHTML = '';
  if (items.length === 0) {
    container.innerHTML = '<div class="empty-state">No devices found</div>';
    return;
  }

  const fragment = document.createDocumentFragment();
  items.forEach(dev => {
    const div = document.createElement('div');
    div.className = `list-item ${dev === activeId ? 'active' : ''}`;
    div.textContent = dev;
    div.addEventListener('click', () => {
      selectDevice(dev, true);
    });
    fragment.appendChild(div);
  });
  container.appendChild(fragment);
}

function clearDeviceSelection() {
  activeDevice = null;
  activeProperty = null;
  cachedDevices = [];
  document.getElementById('devices-count').textContent = '0';
  document.getElementById('devices-list').innerHTML = '<div class="empty-state">Select a registry to view devices</div>';
  document.getElementById('active-device-label').textContent = 'No device selected';
  document.getElementById('refresh-properties').disabled = true;
  document.getElementById('properties-content').innerHTML = '<div class="empty-state">Select a device to view properties</div>';
}

async function selectDevice(dev, updateUrl) {
  activeDevice = dev;
  renderDevicesList(cachedDevices, activeDevice);

  const label = document.getElementById('active-device-label');
  label.textContent = `${activeRegistry} / ${dev}`;
  document.getElementById('refresh-properties').disabled = false;

  if (updateUrl) {
    setHash(activeRegistry, dev, null);
  }

  await loadProperties(activeRegistry, dev);
}

async function loadProperties(registryId, deviceId) {
  const container = document.getElementById('properties-content');
  container.innerHTML = '<div class="loading-state">Loading properties...</div>';
  try {
    const res = await fetch(`/api/registries/${encodeURIComponent(registryId)}/devices/${encodeURIComponent(deviceId)}/properties`);
    if (!res.ok) throw new Error(await res.text());
    const data = await res.json();
    renderPropertiesTable(data.properties || {});
  } catch (err) {
    container.innerHTML = `<div class="empty-state">❌ Error: ${err.message}</div>`;
    showToast('Failed to load device properties from server');
  }
}

function renderPropertiesTable(properties) {
  const container = document.getElementById('properties-content');
  container.innerHTML = '';
  const keys = Object.keys(properties);
  if (keys.length === 0) {
    container.innerHTML = '<div class="empty-state">No properties found for this device</div>';
    return;
  }

  const table = document.createElement('table');
  table.className = 'properties-table';

  const devProps = keys.filter(k => !k.startsWith('/c/'));
  const colProps = keys.filter(k => k.startsWith('/c/'));

  if (devProps.length > 0) {
    appendGroupHeader(table, 'Device Properties');
    devProps.forEach(k => appendPropertyRow(table, k, properties[k]));
  }

  if (colProps.length > 0) {
    appendGroupHeader(table, 'Collections');
    colProps.forEach(k => appendPropertyRow(table, k, properties[k]));
  }

  container.appendChild(table);
}

function appendGroupHeader(table, title) {
  const tr = document.createElement('tr');
  const td = document.createElement('td');
  td.colSpan = 2;
  td.className = 'property-group-title';
  td.textContent = title;
  tr.appendChild(td);
  table.appendChild(tr);
}

function appendPropertyRow(table, key, value) {
  const tr = document.createElement('tr');
  tr.className = 'property-row';
  tr.dataset.key = key;
  tr.addEventListener('click', (e) => {
    if (!e.target.classList.contains('copy-btn')) {
      activeProperty = key;
      setHash(activeRegistry, activeDevice, key);
      highlightPropertyRow(key);
    }
  });

  const tdKey = document.createElement('td');
  tdKey.className = 'property-key';
  tdKey.textContent = key;

  const tdVal = document.createElement('td');
  tdVal.className = 'property-val';

  let isJson = false;
  let formattedJson = value;
  if (value && (value.startsWith('{') || value.startsWith('['))) {
    try {
      const parsed = JSON.parse(value);
      formattedJson = JSON.stringify(parsed, null, 2);
      isJson = true;
    } catch (e) {
      isJson = false;
    }
  }

  if (isJson) {
    const box = document.createElement('div');
    box.className = 'json-box';
    const pre = document.createElement('pre');
    const code = document.createElement('code');
    code.textContent = formattedJson;
    pre.appendChild(code);
    box.appendChild(pre);

    const copyBtn = document.createElement('button');
    copyBtn.className = 'btn copy-btn';
    copyBtn.textContent = '📋 Copy';
    copyBtn.addEventListener('click', () => {
      navigator.clipboard.writeText(formattedJson).then(() => {
        copyBtn.textContent = '✓ Copied!';
        setTimeout(() => { copyBtn.textContent = '📋 Copy'; }, 2000);
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

function highlightPropertyRow(key) {
  activeProperty = key;
  document.querySelectorAll('.property-row').forEach(row => {
    if (row.dataset.key === key) {
      row.classList.add('highlighted');
      row.scrollIntoView({ behavior: 'smooth', block: 'center' });
    } else {
      row.classList.remove('highlighted');
    }
  });
}

function showToast(message) {
  const toast = document.getElementById('toast');
  toast.textContent = message;
  toast.classList.remove('hidden');
  setTimeout(() => {
    toast.classList.add('hidden');
  }, 3500);
}
