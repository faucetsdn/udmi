# 🏗️ Technical Architecture: Testbed Validation Plugin (`testbed`)

This document specifies the technical architecture, component integration APIs, dynamic topology graph engine, and health validation mechanisms for the **Testbed Architecture & Setup Validation** plugin tool.

---

## 1. Domain Purpose & Integration Scope

The **Testbed Validation** plugin provides real-time verification of the UDMI testing environment before test suite execution. It ensures that all essential backend components (Site Model Store, UDMI Validator, MQTT Broker, Sequencer Test Runner, UDMIS Runtime) are up, reachable, and correctly connected.

---

## 2. Backend Service Integrations & API Contracts

### 2.1 Component Status Health Check (`GET /api/testbed/status`)
- **Description**: Polls or queries the backend to determine component readiness.
- **Request Parameters**: None (or optional `site_model` path query).
- **Data Payload Contract**:
  ```json
  {
    "overall_status": "HEALTHY",
    "timestamp": 1784710000000,
    "components": {
      "site_model": {"status": "VALID", "path": "sites/udmi_site_model", "device_count": 3},
      "validator": {"status": "UP", "version": "1.4.2", "schema_valid": true},
      "mqtt_broker": {"status": "UP", "endpoint": "localhost:1883", "latency_ms": 4},
      "sequencer": {"status": "READY", "version": "1.4.2"},
      "udmis": {"status": "UP", "mode": "LOCAL", "active_reflectors": 1}
    }
  }
  ```

### 2.2 Dynamic Topology Graph Engine (`GET /api/testbed/topology`)
- **Description**: Returns the dynamic topology structure representing the active testbed setup. The graph adapts dynamically depending on whether a local broker (`//mqtt/localhost`), cloud pubsub (`//pubsub/project`), or bridge provider (`//gbos/...`) is configured in the workspace `project_spec`.
- **Data Payload Contract**:
  ```json
  {
    "topology_type": "LOCAL_MQTT",
    "nodes": [
      {"id": "site_model", "name": "Site Model Directory", "kind": "config", "status": "VALID"},
      {"id": "validator", "name": "Schema Validator", "kind": "service", "status": "UP"},
      {"id": "mqtt_broker", "name": "Local MQTT Broker", "kind": "broker", "status": "UP"},
      {"id": "sequencer", "name": "Sequencer Engine", "kind": "runner", "status": "READY"},
      {"id": "udmis", "name": "UDMIS Reflective Core", "kind": "backend", "status": "UP"}
    ],
    "edges": [
      {"source": "site_model", "target": "validator", "label": "Validates Schema"},
      {"source": "validator", "target": "mqtt_broker", "label": "Publishes Telemetry"},
      {"source": "mqtt_broker", "target": "sequencer", "label": "Subscribes Events"},
      {"source": "sequencer", "target": "udmis", "label": "Reflective Sync"}
    ]
  }
  ```

---

## 3. Dynamic Diagram State Engine

1. **State Listener**: Listens for `udmi_state_change` postMessage events from the Host Shell containing updated `siteModel` or `project_spec` parameters.
2. **Re-Query Trigger**: Automatically re-fetches `/api/testbed/topology` and `/api/testbed/status` upon workspace change.
3. **Reactive Graph Update**: Updates the interactive node graph state model in memory without causing full DOM teardown, animating connection lines and node status badges.

---

## 4. Teardown & Resource Cleanup

- Polling interval for `/api/testbed/status` (default: 5000ms) MUST be paused or cancelled whenever the plugin view iframe is hidden or unmounted.
