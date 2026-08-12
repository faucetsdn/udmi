# 🏗️ Technical Architecture: Host Shell (`shell`)

This document specifies the technical architecture, micro-frontend container isolation, state management, feature flag routing, and cross-module messaging protocols for the UDMI Workbench **Host Shell**.

---

## 1. Host Shell Core Responsibilities

The **Host Shell** serves as the primary top-level container and orchestrator for the UDMI Workbench frontend (`ui/v2/index.html`, `main.js`, `style.css`):

1. **Workspace Context Management**: Manages the active site model path state and coordinates directory browser navigation.
2. **Security Policy & Feature Flag Enforcement**: Queries backend security policy (`GET /api/features`), parses URL query parameters (`?features=...`), and dynamically mounts allowed plugin micro-frontends.
3. **Global State Store**: Maintains canonical application state and synchronizes updates across micro-frontends.
4. **Inter-Module Event Broker**: Routes cross-module state updates via HTML5 `postMessage` messaging.
5. **Micro-Frontend Lifecycle & Sandboxing**: Mounts, unmounts, and isolates tool plugin view containers.

---

## 2. Micro-Frontend Sandboxing & Isolation

To ensure system stability, security, and independent component lifecycle:

1. **Context Sandboxing (Iframe Isolation)**:
   - Tool plugin views operate inside dedicated `<iframe>` elements (`sandbox="allow-scripts allow-same-origin"`).
   - Prevents unhandled JavaScript exceptions in a plugin view from crashing or freezing the Host Shell or neighbor plugins.
2. **Style Isolation**:
   - CSS rules defined inside a plugin tool view MUST NOT bleed into the Host Shell or adjacent plugins.
3. **Resource & Connection Teardown**:
   - Switching away from a tool plugin view or unmounting its container triggers an explicit teardown signal to cancel active SSE log streams, long-polling timers, and pending API requests.

---

## 3. Global Application State Store

The Host Shell maintains a central state store (`ui/v2/main.js`) defined by the following keys:

| State Key | Type | Scope | Description |
| :--- | :--- | :--- | :--- |
| `siteModel` | `string` | Global | Active site model directory path (e.g., `sites/udmi_site_model`). |
| `activeTab` | `string` | Shell | Identifies currently mounted tool plugin (`testbed`, `sequencer`, `ai-assistant`). |
| `activeFeatures` | `Array<string>` | Shell | List of active tools permitted by security policy and feature flags. |
| `selectedDevice` | `string` | Shared | Device ID selected in Sequencer or AI Assistant view (e.g. `AHU-1`). |
| `executionState` | `enum` | Shared | Process status (`IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `COMPLETED`, `FAILED`). |

---

## 4. Cross-Module PostMessage Protocol (`udmi_state_change`)

### 4.1 Message Envelope Schema

All cross-module state synchronization messages sent from Host Shell to plugin tool views follow a standardized JSON envelope:

```json
{
  "type": "udmi_state_change",
  "siteModel": "sites/udmi_site_model",
  "selectedDevice": "AHU-1",
  "timestamp": 1784710000000
}
```

### 4.2 Downstream Broadcast Lifecycle

1. **User Action**: User selects or edits the `Site Model Path` input or picks a folder in the Directory Browser Modal.
2. **State Update**: Host Shell updates its central `siteModel` state property and saves it to local client storage (`localStorage.setItem('udmi_site_model_path', path)`).
3. **Broadcast**: Host Shell iterates through all active plugin view iframe containers and dispatches the `udmi_state_change` postMessage event (`iframe.contentWindow.postMessage(payload, '*')`).
4. **Plugin Reaction**: Child tool plugin view receives the event via `window.addEventListener('message', ...)` and automatically triggers local data refetching (e.g., updating device matrices or topology graphs).

### 4.3 Late-Bound Zero-Latency Synchronization

- When a tool plugin iframe container is dynamically mounted or reloaded, the Host Shell attaches a `load` listener to the iframe.
- The millisecond the iframe fires its `load` event, the Host Shell immediately pushes the current global state payload (`udmi_state_change`), eliminating blank uninitialized states upon tab switching.

---

## 5. Dynamic Feature Flagging & Layout Engine

1. **Feature Parsing Sequence**:
   - Step 1: Query backend `/api/features` -> returns system-permitted features (e.g. `["testbed", "sequencer", "mantis"]`).
   - Step 2: Parse URL query parameter `?features=...` -> intersects allowed backend features with requested user flags.
2. **Single-Feature Layout Auto-Collapse**:
   - If feature parsing resolves to exactly **one** active tool (e.g., `?features=sequencer`), the Host Shell automatically sets the Navigation Sidebar Rail width to `0px` and hides navigation toggles.
   - This maximizes screen workspace width for single-purpose execution contexts.
