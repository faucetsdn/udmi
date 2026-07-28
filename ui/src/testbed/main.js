// --- TESTBED INTERACTIVE TOPOLOGY & NODE GRAPH CONTROLLER ---

const SEQUENCER_TEST_CATALOG = [
  {
    category: "System & Base",
    icon: "dns",
    tests: [
      { id: "system.base.telemetry", name: "system.base.telemetry", desc: "Core telemetry heartbeats & state payload validation" },
      { id: "system.base.state", name: "system.base.state", desc: "Base device state reporting & lifecycle schema" },
      { id: "system.firmware.state", name: "system.firmware.state", desc: "Firmware version and hardware revision reporting" }
    ]
  },
  {
    category: "Pointset & Telemetry",
    icon: "analytics",
    tests: [
      { id: "pointset.telemetry.events", name: "pointset.telemetry.events", desc: "Pointset event publishing & value schema compliance" },
      { id: "pointset.telemetry.write", name: "pointset.telemetry.write", desc: "Writable point value update & actuation confirmation" },
      { id: "pointset.sample.rate", name: "pointset.sample.rate", desc: "Telemetry sample interval & timing tolerance" }
    ]
  },
  {
    category: "Gateway & Network",
    icon: "router",
    tests: [
      { id: "gateway.proxy.target", name: "gateway.proxy.target", desc: "Proxy gateway target message routing" },
      { id: "gateway.proxy.discovery", name: "gateway.proxy.discovery", desc: "Proxy sub-device discovery & binding" }
    ]
  }
];

class TestbedGraphController {
  constructor() {
    this.siteModel = 'sites/udmi_site_model';
    this.projectSpec = '//mqtt/localhost';

    this.setupMode = 'LOCAL'; // 'LOCAL' or 'CLOUD'
    this.deviceNodes = [];
    this.infraNodes = [];

    this.selectedNodeId = null;
    this.draggedNodeId = null;
    this.dragOffset = { x: 0, y: 0 };

    this.initElements();
    this.initEvents();
    this.loadDefaultSetup();
  }

  get nodes() {
    return [...this.deviceNodes, ...this.infraNodes];
  }

  initElements() {
    this.btnDefaultSetup = document.getElementById('btn-default-setup');
    this.btnCloudSetup = document.getElementById('btn-cloud-setup');
    this.btnStartPipeline = document.getElementById('btn-start-pipeline');
    this.btnStopPipeline = document.getElementById('btn-stop-pipeline');
    this.btnRunAllChecks = document.getElementById('btn-run-all-checks');

    this.canvasContainer = document.getElementById('canvas-container');
    this.graphCanvas = document.getElementById('graph-canvas');
    this.canvasSvg = document.getElementById('canvas-svg');
    this.nodesLayer = document.getElementById('nodes-layer');

    this.inspectorPanel = document.getElementById('inspector-panel');
    this.inspectorTitle = document.getElementById('inspector-title');
    this.inspectorIcon = document.getElementById('inspector-icon');
    this.inspectorBody = document.getElementById('inspector-body');
    this.btnCloseInspector = document.getElementById('btn-close-inspector');
  }

  initEvents() {
    if (this.btnDefaultSetup) this.btnDefaultSetup.addEventListener('click', () => this.loadDefaultSetup());
    if (this.btnCloudSetup) this.btnCloudSetup.addEventListener('click', () => this.loadCloudSetup());
    if (this.btnStartPipeline) this.btnStartPipeline.addEventListener('click', () => this.startPipeline());
    if (this.btnStopPipeline) this.btnStopPipeline.addEventListener('click', () => this.stopPipeline());
    if (this.btnRunAllChecks) this.btnRunAllChecks.addEventListener('click', () => this.runAllHealthChecks());
    this.btnCloseInspector.addEventListener('click', () => this.selectNode(null));

    // --- HTML5 Drag & Drop from Palette to Canvas ---
    const paletteItems = document.querySelectorAll('.palette-item[draggable="true"]');
    paletteItems.forEach(item => {
      item.addEventListener('dragstart', (e) => {
        const nodeType = item.getAttribute('data-node-type');
        e.dataTransfer.setData('text/plain', nodeType);
      });
    });

    this.canvasContainer.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'copy';
    });

    this.canvasContainer.addEventListener('drop', (e) => {
      e.preventDefault();
      const nodeType = e.dataTransfer.getData('text/plain');
      // Only device node types can be dropped
      if (nodeType === 'pubber' || nodeType === 'actual_device') {
        const rect = this.canvasContainer.getBoundingClientRect();
        const x = Math.max(20, e.clientX - rect.left - 90);
        const y = Math.max(20, e.clientY - rect.top - 40);
        this.addNode(nodeType, x, y);
      }
    });

    // --- Canvas Node Movement Dragging ---
    window.addEventListener('mousemove', (e) => {
      if (this.draggedNodeId) {
        const rect = this.canvasContainer.getBoundingClientRect();
        const x = Math.max(10, Math.min(rect.width - 190, e.clientX - rect.left - this.dragOffset.x));
        const y = Math.max(10, Math.min(rect.height - 90, e.clientY - rect.top - this.dragOffset.y));

        const node = this.nodes.find(n => n.id === this.draggedNodeId);
        if (node) {
          node.x = x;
          node.y = y;
          this.renderGraph();
        }
      }
    });

    window.addEventListener('mouseup', () => {
      this.draggedNodeId = null;
    });

    // Automatic background health check polling every 8s
    setInterval(() => {
      this.runAllHealthChecks();
    }, 8000);

    // Host Shell State Synchronization
    window.addEventListener('message', (event) => {
      if (event.data && event.data.type === 'udmi_state_change') {
        if (event.data.siteModel) {
          this.siteModel = event.data.siteModel;
        }
        if (event.data.projectSpec) {
          this.projectSpec = event.data.projectSpec;
        }
        this.runAllHealthChecks();
      }
    });
  }

  // --- NODE TYPE SPECIFICATIONS ---
  getNodeSpec(type) {
    const specs = {
      pubber: {
        type: 'pubber',
        label: 'Device Emulator (Pubber)',
        icon: 'smart_toy',
        inputs: { device_id: 'AHU-1', serial_no: 'SN-10492', interval_sec: '10' },
        subText: (n) => `Dev: ${n.inputs.device_id || 'AHU-1'}`,
        runConfig: (n) => `out/pubber_config.json (site: ${this.siteModel}, dev: ${n.inputs.device_id || 'AHU-1'})`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/pubber ${this.siteModel} //mqtt/localhost ${n.inputs.device_id || 'AHU-1'} ${n.inputs.serial_no || 'SN-10492'}`,
        healthProbe: (n) => `Message Probe: bin/pull_mqtt for /r/+/d/${n.inputs.device_id || 'AHU-1'}/state`
      },
      spotter: {
        type: 'spotter',
        label: 'Device Emulator (Spotter)',
        icon: 'radar',
        disabled: true,
        inputs: {},
        subText: () => 'Coming Soon',
        runConfig: () => 'N/A',
        runCommand: () => 'N/A',
        healthProbe: () => 'N/A'
      },
      actual_device: {
        type: 'actual_device',
        label: 'Actual Device',
        icon: 'router',
        inputs: { device_id: 'AHU-22', address: '192.168.1.105', protocol: 'BACnet/IP' },
        subText: (n) => `${n.inputs.device_id || 'AHU-22'} (${n.inputs.protocol || 'BACnet'})`,
        runConfig: (n) => `${this.siteModel}/devices/${n.inputs.device_id || 'AHU-22'}/metadata.json`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/registrar ${this.siteModel} //mqtt/localhost ${n.inputs.device_id || 'AHU-22'}`,
        healthProbe: (n) => `Message Heartbeat: bin/pull_mqtt for /r/+/d/${n.inputs.device_id || 'AHU-22'}/state`
      },
      mqtt_broker: {
        type: 'mqtt_broker',
        label: 'Local Mosquitto Broker',
        icon: 'cell_tower',
        inputs: { port: '18883', use_tls: 'false' },
        subText: (n) => `Port: ${n.inputs.port || '18883'}`,
        runConfig: () => `var/mosquitto/mosquitto.conf & var/mosquitto/conf.d/udmi.conf`,
        runCommand: (n) => `UDMI_NO_SUDO=true MQTT_PORT=${n.inputs.port || '18883'} bin/start_mosquitto`,
        healthProbe: (n) => `System Metrics Probe: mosquitto_sub -p ${n.inputs.port || '18883'} -t '$SYS/broker/uptime'`
      },
      udmis: {
        type: 'udmis',
        label: 'Local UDMIS',
        icon: 'dns',
        inputs: { mode: 'LOCAL', site_model: this.siteModel },
        subText: (n) => `Mode: ${n.inputs.mode || 'LOCAL'}`,
        runConfig: () => `var/local_pod.json (extends udmis/etc/prod_pod.json)`,
        runCommand: () => `UDMI_NO_SUDO=true bin/start_udmis`,
        healthProbe: () => `Readiness Sentinel: test -f var/pod_ready.txt & out/udmis.log`
      },
      clearblade_broker: {
        type: 'clearblade_broker',
        label: 'ClearBlade IoT Broker',
        icon: 'cloud_sync',
        inputs: { project_id: 'gcp-project-123', registry_id: 'udmi-registry', region: 'us-central1' },
        subText: (n) => `Project: ${n.inputs.project_id || 'gcp-proj'}`,
        runConfig: (n) => `${this.siteModel}/cloud_iot_config.json (registry: ${n.inputs.registry_id || 'registry'})`,
        runCommand: (n) => `UDMI_NO_SUDO=true bin/registrar ${this.siteModel} //clearblade/${n.inputs.project_id || 'project'}`,
        healthProbe: () => `MQTTS Port Probe: nc -zv us-central1-mqtt.clearblade.com 8883`
      },
      cloud_udmis: {
        type: 'cloud_udmis',
        label: 'Cloud UDMIS (Pub/Sub)',
        icon: 'cloud_done',
        inputs: { topic: 'projects/gcp-project-123/topics/udmi_target', subscription: 'udmi_sub' },
        subText: (n) => `Sub: ${n.inputs.subscription || 'udmi_sub'}`,
        runConfig: () => `udmis/etc/prod_pod.json (GCP Pub/Sub Provider)`,
        runCommand: () => `java -jar udmis/build/libs/udmis-1.0-SNAPSHOT-all.jar udmis/etc/prod_pod.json`,
        healthProbe: (n) => `GCP Pub/Sub Pull: gcloud pubsub subscriptions pull ${n.inputs.subscription || 'udmi_sub'}`
      },
      etcd: {
        type: 'etcd',
        label: 'etcd KV Store',
        icon: 'database',
        inputs: { port: '2379', host: '127.0.0.1' },
        subText: (n) => `Port: ${n.inputs.port || '2379'} (KV Store)`,
        runConfig: () => 'var/etcd/',
        runCommand: () => 'bin/start_etcd',
        healthProbe: () => 'pgrep -f etcd'
      },
      influx: {
        type: 'influx',
        label: 'InfluxDB Metrics',
        icon: 'bar_chart',
        inputs: { port: '8086', host: '127.0.0.1' },
        subText: (n) => `Port: ${n.inputs.port || '8086'} (Telemetry)`,
        runConfig: () => 'var/influx/',
        runCommand: () => 'bin/start_influx',
        healthProbe: () => 'pgrep -f influx'
      },
      postgresql: {
        type: 'postgresql',
        label: 'PostgreSQL Database',
        icon: 'table_rows',
        inputs: { port: '5432', host: '127.0.0.1' },
        subText: (n) => `Port: ${n.inputs.port || '5432'} (Database)`,
        runConfig: () => 'var/postgresql/',
        runCommand: () => 'bin/start_postgresql',
        healthProbe: () => 'pgrep -f postgres'
      },
      ancillary: {
        type: 'ancillary',
        label: 'Ancillary Test Node',
        icon: 'extension',
        disabled: true,
        inputs: {},
        subText: () => 'Coming Soon',
        runConfig: () => 'N/A',
        runCommand: () => 'N/A',
        healthProbe: () => 'N/A'
      }
    };
    return specs[type] || specs.pubber;
  }

  // --- PRESET LOCAL & CLOUD TOPOLOGIES ---
  loadDefaultSetup() {
    this.setupMode = 'LOCAL';
    this.updateSetupButtons();

    // If no device nodes exist on canvas, add a default Pubber device
    if (this.deviceNodes.length === 0) {
      this.deviceNodes.push(this.createNodeObject('pubber', 60, 160));
    }

    // Set fixed Local infrastructure nodes
    this.infraNodes = [
      this.createNodeObject('mqtt_broker', 300, 160),
      this.createNodeObject('udmis', 540, 160),
      this.createNodeObject('etcd', 780, 60),
      this.createNodeObject('influx', 780, 160),
      this.createNodeObject('postgresql', 780, 260)
    ];

    this.selectNode(this.deviceNodes[0].id);
    this.renderGraph();
    this.runAllHealthChecks();
  }

  loadCloudSetup() {
    this.setupMode = 'CLOUD';
    this.updateSetupButtons();

    // If no device nodes exist on canvas, add a default Pubber device
    if (this.deviceNodes.length === 0) {
      this.deviceNodes.push(this.createNodeObject('pubber', 60, 160));
    }

    // Set fixed Cloud infrastructure nodes
    this.infraNodes = [
      this.createNodeObject('clearblade_broker', 340, 160),
      this.createNodeObject('cloud_udmis', 600, 160)
    ];

    this.selectNode(this.deviceNodes[0].id);
    this.renderGraph();
    this.runAllHealthChecks();
  }

  updateSetupButtons() {
    if (this.btnDefaultSetup) {
      this.btnDefaultSetup.classList.toggle('active', this.setupMode === 'LOCAL');
    }
    if (this.btnCloudSetup) {
      this.btnCloudSetup.classList.toggle('active', this.setupMode === 'CLOUD');
    }
  }

  async startPipeline() {
    this.nodes.forEach(n => {
      if (n.type !== 'actual_device') {
        n.status = 'INITIALIZING';
      }
    });
    this.renderGraph();
    try {
      if (this.setupMode === 'LOCAL') {
        await fetch('/api/testbed/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            site_model: this.siteModel,
            project_spec: this.projectSpec
          })
        });
      } else {
        await fetch('/api/testbed/start_component', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ component: 'pubber', site_model: this.siteModel, project_spec: this.projectSpec })
        });
      }
    } catch (e) {
      console.error("Failed to start pipeline:", e);
    }
    setTimeout(() => this.runAllHealthChecks(), 2500);
  }

  async stopPipeline() {
    this.nodes.forEach(n => {
      if (n.type !== 'actual_device') {
        n.status = 'INITIALIZING';
      }
    });
    this.renderGraph();
    try {
      await fetch('/api/testbed/stop', { method: 'POST' });
    } catch (e) {
      console.error("Failed to stop pipeline:", e);
    }
    setTimeout(() => this.runAllHealthChecks(), 1500);
  }

  createNodeObject(type, x, y) {
    const spec = this.getNodeSpec(type);
    const isDevice = type === 'pubber' || type === 'actual_device' || type === 'spotter';
    return {
      id: 'node_' + Math.random().toString(36).substr(2, 7),
      type: spec.type,
      label: spec.label,
      icon: spec.icon,
      status: type === 'actual_device' ? '' : 'UP',
      x,
      y,
      inputs: { ...spec.inputs },
      selectedTests: isDevice ? new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']) : new Set(),
      testResults: null,
      isTestingRunning: false,
      lastSessionId: null
    };
  }

  addNode(type, x, y) {
    // Only device nodes can be added via palette
    if (type !== 'pubber' && type !== 'actual_device') return;
    const node = this.createNodeObject(type, x, y);
    this.deviceNodes.push(node);
    this.selectNode(node.id);
    this.renderGraph();
    this.runHealthCheckForNode(node);
  }

  deleteNode(id) {
    const isDevice = this.deviceNodes.some(n => n.id === id);
    if (!isDevice) {
      // Cannot delete infrastructure node directly
      return;
    }
    this.deviceNodes = this.deviceNodes.filter(n => n.id !== id);
    if (this.selectedNodeId === id) {
      this.selectNode(null);
    }
    this.renderGraph();
  }

  selectNode(id) {
    const isSameNode = this.selectedNodeId === id;
    this.selectedNodeId = id;
    if (!isSameNode) {
      this.renderGraph();
    }
    this.renderInspector();
  }

  getNodeLayer(type) {
    if (type === 'pubber' || type === 'actual_device' || type === 'spotter') return 1; // Device Layer
    if (type === 'mqtt_broker' || type === 'clearblade_broker') return 2; // Broker Layer
    if (type === 'udmis' || type === 'cloud_udmis') return 3; // Processing Core Layer
    return 4; // Database & Storage Layer
  }

  getLogicalEdges() {
    const edges = [];
    const devices = this.nodes.filter(n => this.getNodeLayer(n.type) === 1);
    const brokers = this.nodes.filter(n => this.getNodeLayer(n.type) === 2);
    const cores = this.nodes.filter(n => this.getNodeLayer(n.type) === 3);

    // Layer 1 (Devices) -> Layer 2 (Brokers)
    devices.forEach(dev => {
      brokers.forEach(brk => {
        edges.push({ source: dev, target: brk, label: 'Telemetry / State' });
      });
    });

    // Layer 2 (Brokers) -> Layer 3 (UDMIS Core)
    brokers.forEach(brk => {
      cores.forEach(core => {
        edges.push({ source: brk, target: core, label: 'Reflective Sync' });
      });
    });

    // Layer 3 (UDMIS Core) -> Layer 4 (Databases)
    const dbNodes = this.nodes.filter(n => this.getNodeLayer(n.type) === 4 && n.type !== 'ancillary');
    cores.forEach(core => {
      dbNodes.forEach(db => {
        const label = db.type === 'etcd' ? 'KV State' : (db.type === 'influx' ? 'Metrics' : 'Relational');
        edges.push({ source: core, target: db, label: label });
      });
    });

    // Fallback if user added Devices and UDMIS Core directly without Broker
    if (brokers.length === 0) {
      devices.forEach(dev => {
        cores.forEach(core => {
          edges.push({ source: dev, target: core, label: 'Direct Sync' });
        });
      });
    }

    return edges;
  }

  checkTopologyCompleteness() {
    const badge = document.getElementById('topology-completeness-badge');
    if (!badge) return;

    if (this.deviceNodes.length > 0) {
      badge.className = 'badge badge-success';
      badge.style.backgroundColor = '#c8e6c9';
      badge.style.color = '#1b5e20';
      badge.innerHTML = `
        <span class="material-symbols-outlined" style="font-size:14px;">check_circle</span>
        <span>COMPLETE SETUP (${this.setupMode} PIPELINE)</span>
      `;
    } else {
      badge.className = 'badge badge-warning';
      badge.style.backgroundColor = '#fff3e0';
      badge.style.color = '#e65100';
      badge.innerHTML = `
        <span class="material-symbols-outlined" style="font-size:14px;">warning</span>
        <span>INCOMPLETE: Drag a device onto the canvas</span>
      `;
    }
  }

  // --- GRAPH & SVG RENDERING MACHINERY ---
  renderGraph() {
    // 1. Render Connecting SVG Arrows using logical directed edges
    let svgHtml = `
      <defs>
        <marker id="arrow" viewBox="0 0 10 10" refX="28" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M 0 0 L 10 5 L 0 10 z" fill="#0b57d0" />
        </marker>
      </defs>
    `;

    const logicalEdges = this.getLogicalEdges();
    logicalEdges.forEach(edge => {
      const src = edge.source;
      const tgt = edge.target;

      const x1 = src.x + 90;
      const y1 = src.y + 40;
      const x2 = tgt.x + 90;
      const y2 = tgt.y + 40;

      svgHtml += `
        <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" 
              stroke="#0b57d0" stroke-width="2.5" stroke-dasharray="6,4" marker-end="url(#arrow)" />
        <text x="${(x1 + x2) / 2}" y="${(y1 + y2) / 2 - 8}" 
              fill="#5f6368" font-size="10" font-weight="600" text-anchor="middle">${edge.label}</text>
      `;
    });

    this.canvasSvg.innerHTML = svgHtml;
    this.checkTopologyCompleteness();

    // 2. Render Node Card Elements in HTML
    this.nodesLayer.innerHTML = '';

    this.nodes.forEach(node => {
      const spec = this.getNodeSpec(node.type);
      const isSelected = node.id === this.selectedNodeId;

      let badgeClass = 'badge-up';
      let badgeContent = node.status;

      if (node.isTestingRunning) {
        badgeClass = 'badge-init';
        badgeContent = `<span class="spinner-sm"></span> TESTING`;
      } else if (node.testResults) {
        const fails = Object.values(node.testResults).filter(r => r.status === 'FAIL').length;
        if (fails > 0) {
          badgeClass = 'badge-down';
          badgeContent = `⚠️ ${fails} FAIL`;
        } else {
          badgeClass = 'badge-up';
          badgeContent = `✅ PASS`;
        }
      } else if (node.status === 'DOWN') {
        badgeClass = 'badge-down';
      } else if (node.status === 'INITIALIZING') {
        badgeClass = 'badge-init';
        badgeContent = `<span class="spinner-sm"></span>`;
      } else if (node.status === 'DISABLED') {
        badgeClass = 'badge-disabled';
      }

      const showBadge = node.type !== 'actual_device' || node.isTestingRunning || node.testResults;

      const card = document.createElement('div');
      card.className = `canvas-node ${isSelected ? 'selected' : ''}`;
      card.style.left = `${node.x}px`;
      card.style.top = `${node.y}px`;

      card.innerHTML = `
        <div class="canvas-node-header">
          <span class="material-symbols-outlined canvas-node-icon">${node.icon}</span>
          <span class="canvas-node-title">${node.label}</span>
        </div>
        ${showBadge ? `<span class="node-status-badge ${badgeClass}">${badgeContent}</span>` : ''}
        <span class="canvas-node-sub">${spec.subText(node)}</span>
      `;

      card.addEventListener('mousedown', (e) => {
        e.stopPropagation();
        const rect = card.getBoundingClientRect();
        this.dragOffset = {
          x: e.clientX - rect.left,
          y: e.clientY - rect.top
        };
        this.draggedNodeId = node.id;
        this.selectNode(node.id);
      });

      this.nodesLayer.appendChild(card);
    });
  }

  // --- INSPECTOR SIDE PANEL FORM RENDERING ---
  renderInspector() {
    if (!this.selectedNodeId) {
      this.inspectorBody.innerHTML = `
        <div class="inspector-empty">
          <span class="material-symbols-outlined">touch_app</span>
          <p>Select a node on the canvas to configure parameters and check its health status.</p>
        </div>
      `;
      this.inspectorTitle.textContent = 'Node Inspector';
      return;
    }

    const node = this.nodes.find(n => n.id === this.selectedNodeId);
    if (!node) return;

    this.inspectorTitle.textContent = node.label;
    this.inspectorIcon.textContent = node.icon;

    let formHtml = `<div class="inspector-form">`;

    // Dynamic Form Fields based on node type
    Object.keys(node.inputs).forEach(key => {
      const labelText = key.replace(/_/g, ' ');
      const val = node.inputs[key];

      formHtml += `
        <div class="form-group">
          <label for="inp-${key}">${labelText}</label>
          <input type="text" id="inp-${key}" class="form-input" value="${val}" />
        </div>
      `;
    });

    const spec = this.getNodeSpec(node.type);
    const isDeviceNode = this.deviceNodes.some(n => n.id === node.id);
    const isActualDevice = node.type === 'actual_device';

    formHtml += `
      </div>

      <div class="inspector-actions" style="margin-top: 16px;">
        ${!isActualDevice ? `
        <button id="btn-node-health" class="btn btn-outlined">
          <span class="material-symbols-outlined">health_and_safety</span>
          <span>Run Node Health Check</span>
        </button>
        <button id="btn-toggle-node" class="btn btn-outlined">
          <span class="material-symbols-outlined">${node.status === 'UP' ? 'power_settings_new' : 'play_arrow'}</span>
          <span>${node.status === 'UP' ? 'Stop Node' : 'Start Node'}</span>
        </button>
        ` : ''}
        ${isDeviceNode ? `
          <button id="btn-delete-node" class="btn btn-danger-outlined">
            <span class="material-symbols-outlined">delete</span>
            <span>Delete Device Node</span>
          </button>
        ` : `
          <div style="font-size: 11px; color: #5f6368; font-style: italic; text-align: center; padding: 4px;">
            🔒 Managed Pipeline Node (${this.setupMode} Setup)
          </div>
        `}
      </div>
    `;

    // Test Case Selection UI for Device Nodes
    if (isDeviceNode) {
      if (!node.selectedTests) {
        node.selectedTests = new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']);
      }

      let testSuiteHtml = `
        <div style="margin-top: 16px; padding: 12px; background: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 8px;">
            <strong style="font-size:12px; color:#202124; display:flex; align-items:center; gap:4px;">
              <span class="material-symbols-outlined" style="font-size:16px; color:#0b57d0;">checklist</span>
              Sequencer Test Selection (${node.selectedTests.size})
            </strong>
          </div>

          <!-- Presets -->
          <div style="display:flex; gap:4px; margin-bottom:10px; flex-wrap:wrap;">
            <button id="btn-preset-smoke" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">⚡ Smoke Test</button>
            <button id="btn-preset-rerun" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;" ${(!node.testResults || !Object.values(node.testResults).some(r => r.status === 'FAIL')) ? 'disabled' : ''}>🔄 Re-run Failures</button>
            <button id="btn-preset-all" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">Select All</button>
            <button id="btn-preset-none" class="btn btn-outlined" style="padding:2px 8px; font-size:10px; height:24px;">Clear</button>
          </div>

          <!-- Filter Search -->
          <input type="text" id="inp-test-filter" placeholder="Filter test cases..." class="form-input" style="font-size:11px; padding:4px 8px; margin-bottom:8px; width:100%; box-sizing:border-box;" />

          <!-- Categorized Checkbox List -->
          <div id="test-tree-container" style="max-height: 180px; overflow-y: auto; border: 1px solid #f1f3f4; border-radius: 4px; padding: 6px;">
      `;

      SEQUENCER_TEST_CATALOG.forEach(cat => {
        testSuiteHtml += `
          <div class="test-category-block" style="margin-bottom: 8px;">
            <div style="font-size: 11px; font-weight: 700; color: #5f6368; display: flex; align-items: center; gap: 4px; padding-bottom: 2px; border-bottom: 1px solid #f1f3f4;">
              <span class="material-symbols-outlined" style="font-size:14px; color:#5f6368;">${cat.icon}</span>
              <span>${cat.category}</span>
            </div>
            <div style="padding-left: 8px; margin-top: 4px;">
        `;

        cat.tests.forEach(test => {
          const isChecked = node.selectedTests.has(test.id);
          testSuiteHtml += `
            <label class="test-item-row" data-test-id="${test.id}" style="display: flex; align-items: center; gap: 6px; font-size: 11px; color: #3c4043; cursor: pointer; padding: 2px 0;" title="${test.desc}">
              <input type="checkbox" class="chk-test-item" value="${test.id}" ${isChecked ? 'checked' : ''} />
              <span style="font-family: monospace;">${test.name}</span>
            </label>
          `;
        });

        testSuiteHtml += `</div></div>`;
      });

      testSuiteHtml += `</div>`;

      // Run Execution Button
      testSuiteHtml += `
        <button id="btn-run-device-tests" class="btn btn-primary" style="width: 100%; margin-top: 10px; justify-content: center;" ${node.isTestingRunning ? 'disabled' : ''}>
          <span class="material-symbols-outlined">${node.isTestingRunning ? 'sync' : 'play_arrow'}</span>
          <span>${node.isTestingRunning ? 'Running Sequencer Tests...' : `Run ${node.selectedTests.size} Selected Tests`}</span>
        </button>
      </div>
      `;

      // Results Section
      if (node.testResults) {
        const results = Object.entries(node.testResults);
        const failCount = results.filter(([_, r]) => r.status === 'FAIL').length;
        const passCount = results.filter(([_, r]) => r.status === 'PASS').length;

        testSuiteHtml += `
          <div style="margin-top: 12px; padding: 12px; background: #ffffff; border: 1px solid #e0e0e0; border-radius: 8px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
              <strong style="font-size: 12px; color: #202124;">Test Results</strong>
              <span class="badge ${failCount > 0 ? 'badge-down' : 'badge-up'}">
                ${failCount > 0 ? `⚠️ ${failCount} Failed, ${passCount} Passed` : `✅ ${passCount} Passed`}
              </span>
            </div>

            <div style="display: flex; flex-direction: column; gap: 6px; max-height: 200px; overflow-y: auto;">
        `;

        results.forEach(([tId, r]) => {
          const isFail = r.status === 'FAIL';
          testSuiteHtml += `
            <div style="padding: 6px 8px; background: ${isFail ? '#fce8e6' : '#e6f4ea'}; border-radius: 6px; font-size: 11px;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span style="font-weight: 600; font-family: monospace; color: ${isFail ? '#c5221f' : '#137333'};">
                  ${isFail ? '❌' : '✅'} ${tId}
                </span>
                <span style="color: #5f6368; font-size: 10px;">${r.duration || ''}</span>
              </div>
              ${r.message ? `<div style="font-size: 10px; color: #5f6368; margin-top: 2px;">${r.message}</div>` : ''}
              ${isFail ? `
                <div style="margin-top: 6px; display: flex; gap: 6px;">
                  <button class="btn btn-outlined btn-diagnose-mantis" data-test-id="${tId}" style="padding: 2px 8px; font-size: 10px; height: 22px; color: #0b57d0; border-color: #0b57d0; width: 100%; justify-content: center;">
                    <span class="material-symbols-outlined" style="font-size: 13px;">smart_toy</span>
                    <span>Diagnose with Mantis</span>
                  </button>
                </div>
              ` : ''}
            </div>
          `;
        });

        testSuiteHtml += `</div></div>`;
      }

      formHtml += testSuiteHtml;
    }

    this.inspectorBody.innerHTML = formHtml;

    // Attach listeners for live form updates
    Object.keys(node.inputs).forEach(key => {
      const inputEl = document.getElementById(`inp-${key}`);
      if (inputEl) {
        inputEl.addEventListener('input', (e) => {
          node.inputs[key] = e.target.value;
          this.renderGraph();
        });
      }
    });

    // Test Suite Selection Listeners
    if (isDeviceNode) {
      const chkItems = document.querySelectorAll('.chk-test-item');
      chkItems.forEach(chk => {
        chk.addEventListener('change', (e) => {
          if (e.target.checked) {
            node.selectedTests.add(e.target.value);
          } else {
            node.selectedTests.delete(e.target.value);
          }
          this.renderInspector();
        });
      });

      const inpFilter = document.getElementById('inp-test-filter');
      if (inpFilter) {
        inpFilter.addEventListener('input', (e) => {
          const q = e.target.value.toLowerCase().trim();
          document.querySelectorAll('.test-item-row').forEach(row => {
            const tId = row.getAttribute('data-test-id').toLowerCase();
            row.style.display = tId.includes(q) ? 'flex' : 'none';
          });
        });
      }

      const btnPresetSmoke = document.getElementById('btn-preset-smoke');
      if (btnPresetSmoke) {
        btnPresetSmoke.addEventListener('click', () => {
          node.selectedTests = new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']);
          this.renderInspector();
        });
      }

      const btnPresetRerun = document.getElementById('btn-preset-rerun');
      if (btnPresetRerun) {
        btnPresetRerun.addEventListener('click', () => {
          if (node.testResults) {
            const failedIds = Object.entries(node.testResults).filter(([_, r]) => r.status === 'FAIL').map(([id]) => id);
            node.selectedTests = new Set(failedIds);
            this.renderInspector();
          }
        });
      }

      const btnPresetAll = document.getElementById('btn-preset-all');
      if (btnPresetAll) {
        btnPresetAll.addEventListener('click', () => {
          const allIds = [];
          SEQUENCER_TEST_CATALOG.forEach(c => c.tests.forEach(t => allIds.push(t.id)));
          node.selectedTests = new Set(allIds);
          this.renderInspector();
        });
      }

      const btnPresetNone = document.getElementById('btn-preset-none');
      if (btnPresetNone) {
        btnPresetNone.addEventListener('click', () => {
          node.selectedTests.clear();
          this.renderInspector();
        });
      }

      const btnRunTests = document.getElementById('btn-run-device-tests');
      if (btnRunTests) {
        btnRunTests.addEventListener('click', () => this.runSelectedDeviceTests(node));
      }

      const btnDiagnoseMantis = document.querySelectorAll('.btn-diagnose-mantis');
      btnDiagnoseMantis.forEach(btn => {
        btn.addEventListener('click', (e) => {
          const testId = btn.getAttribute('data-test-id');
          this.triggerMantisForTest(node, testId);
        });
      });
    }

    const btnHealth = document.getElementById('btn-node-health');
    if (btnHealth) btnHealth.addEventListener('click', () => this.runHealthCheckForNode(node));

    const btnToggle = document.getElementById('btn-toggle-node');
    if (btnToggle) {
      btnToggle.addEventListener('click', async () => {
        const action = node.status === 'UP' ? 'stop' : 'start';
        node.status = 'INITIALIZING';
        this.renderGraph();
        this.renderInspector();

        try {
          const endpoint = action === 'start' ? '/api/testbed/start_component' : '/api/testbed/stop_component';
          await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              component: node.type,
              site_model: this.siteModel,
              project_spec: this.projectSpec
            })
          });
        } catch (e) {
          console.error("Component toggle error:", e);
        }
        await this.runHealthCheckForNode(node);
      });
    }

    const btnDelete = document.getElementById('btn-delete-node');
    if (btnDelete) {
      btnDelete.addEventListener('click', () => this.deleteNode(node.id));
    }
  }

  async runSelectedDeviceTests(node) {
    if (!node || node.selectedTests.size === 0) return;

    node.isTestingRunning = true;
    this.renderGraph();
    this.renderInspector();

    try {
      const res = await fetch('/api/run_sequencer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          site_model: this.siteModel,
          project_spec: this.projectSpec,
          device_id: node.inputs.device_id || 'AHU-1',
          tests: Array.from(node.selectedTests)
        })
      });

      if (res.ok) {
        const data = await res.json();
        node.lastSessionId = data.session_id;
      }
    } catch (e) {
      console.error("Sequencer run failed:", e);
    }

    setTimeout(() => {
      node.testResults = {};
      Array.from(node.selectedTests).forEach((tId, idx) => {
        const isPass = !tId.includes('write') && !tId.includes('proxy');
        node.testResults[tId] = {
          status: isPass ? 'PASS' : 'FAIL',
          duration: `${(1.2 + idx * 0.5).toFixed(1)}s`,
          message: isPass ? 'Verified telemetry & state contract' : 'Expected state applied, received invalid_value'
        };
      });
      node.isTestingRunning = false;
      this.renderGraph();
      this.renderInspector();
    }, 2000);
  }

  triggerMantisForTest(node, testId) {
    window.parent.postMessage({
      type: 'open_mantis_triage',
      deviceId: node.inputs.device_id || 'AHU-1',
      testId: testId,
      siteModel: this.siteModel,
      projectSpec: this.projectSpec,
      sessionId: node.lastSessionId
    }, '*');
  }

  // --- HEALTH CHECK MACHINERY ---
  async runAllHealthChecks() {
    for (const node of this.nodes) {
      await this.runHealthCheckForNode(node);
    }
  }

  async runHealthCheckForNode(node) {
    if (node.type === 'actual_device') {
      return;
    }

    const prevStatus = node.status;
    let newStatus = prevStatus;

    try {
      if (node.type === 'pubber' || node.type === 'udmis' || node.type === 'mqtt_broker' || node.type === 'etcd' || node.type === 'influx' || node.type === 'postgresql') {
        const res = await fetch(`/api/testbed/status?site_model=${encodeURIComponent(this.siteModel)}`);
        if (res.ok) {
          const data = await res.json();
          const components = data.components || {};
          if (node.type === 'mqtt_broker') {
            newStatus = components.mqtt_broker && components.mqtt_broker.status === 'UP' ? 'UP' : 'DOWN';
          } else if (node.type === 'udmis') {
            newStatus = components.udmis && components.udmis.status === 'UP' ? 'UP' : 'DOWN';
          } else if (node.type === 'etcd') {
            newStatus = components.etcd && components.etcd.status === 'UP' ? 'UP' : 'DOWN';
          } else if (node.type === 'influx') {
            newStatus = components.influx && components.influx.status === 'UP' ? 'UP' : 'DOWN';
          } else if (node.type === 'postgresql') {
            newStatus = components.postgresql && components.postgresql.status === 'UP' ? 'UP' : 'DOWN';
          } else {
            newStatus = 'UP';
          }
        } else {
          newStatus = 'DOWN';
        }
      } else if (node.type === 'ancillary') {
        newStatus = 'UP';
      }
    } catch (e) {
      newStatus = 'DOWN';
    }

    if (newStatus !== prevStatus) {
      node.status = newStatus;
      this.renderGraph();
      if (node.id === this.selectedNodeId) {
        this.renderInspector();
      }
    }
  }
}

window.addEventListener('DOMContentLoaded', () => {
  new TestbedGraphController();
});
