# ⚡ State Management & Inter-Module Events Specification

This document specifies the global application state model, cross-module event messaging contracts, late-bound synchronization rules, and local client persistence.

---

## 1. Global Application State Model

The Workbench UI relies on a small set of primary application state properties:

| State Key | Scope | Type | Description |
| :--- | :--- | :--- | :--- |
| `siteModel` | Global | `string` | The active site model directory path (e.g., `sites/udmi_site_model`). |
| `activeTab` | Shell | `string` | Identifies which tool micro-frontend is currently active (e.g. `sequencer`, `mantis`). |
| `activeFeatures` | Shell | `Array<string>` | List of tool features allowed by backend policy and enabled by user flags. |
| `selectedDevice` | Local Tool | `string` | Device ID selected in Sequencer or Mantis view. |
| `executionState` | Local Tool | `enum` | State of backend process (`IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `COMPLETED`, `FAILED`). |

---

## 2. Cross-Module Messaging Protocol (State Sync)

When tools are rendered inside separate context containers (such as micro-frontends or separate view models), state changes in the Host Shell MUST be broadcast downstream to active tools.

### 2.1 Message Envelope Schema
All inter-module messages follow a standard payload structure:

```json
{
  "type": "udmi_state_change",
  "siteModel": "sites/udmi_site_model",
  "timestamp": 1784710000000
}
```

### 2.2 Broadcast Lifecycle
1. **User Action / Change**: User updates the `Site Model Path` input or selects a directory in the Directory Browser Modal.
2. **Host Shell Validation**: Host Shell updates its local state and validates the path.
3. **Downstream Broadcast**: Host Shell posts the `udmi_state_change` message to all tool containers.
4. **Tool Subscription & Scan Trigger**: Each tool container listens for the message and automatically triggers a local scan (e.g., refreshing device lists or test result listings for the new site model).

---

## 3. Late-Bound Synchronization Rule

Because micro-frontend views or modular panels may load asynchronously or mount after the user has selected a site model path:

- **Immediate On-Load Broadcast**: The Host Shell MUST listen for the readiness event of every tool view container (e.g., iframe `load` or component `mount`).
- **Zero-Latency Push**: The exact millisecond a tool view becomes ready, the Host Shell MUST push the current global `siteModel` state to that view immediately, preventing blank initial states.

---

## 4. Local Client Persistence

To maintain developer productivity across browser reloads:

| Storage Key | Purpose |
| :--- | :--- |
| `udmi_site_model_path` | Caches the last entered or selected site model path. Automatically restored upon initialization. |
| `udmi_active_features` | Caches active URL feature flags. |

---

## 5. Event & Stream Cleanup Rules

- **Unmount Cleanup**: When switching away from a tool view or stopping execution, all active HTTP connections, SSE event source listeners, and polling timers associated with that view MUST be explicitly closed/unsubscribed.
- **Server Stop Signal**: Triggering a "Stop" button in the UI MUST send an explicit backend stop request (e.g., `/api/stop_sequencer`) before closing local stream listeners.
