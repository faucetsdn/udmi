# 🏗️ UI Architecture & Layout Specification

This document details the high-level structural patterns, navigation architecture, feature flagging, and viewport constraints of the UDMI Workbench user interface.

---

## 1. Structural Architecture: Host Shell & Micro-Frontend Plugins

The Workbench UI utilizes a **Host Shell and Micro-Frontend Architecture**.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Global Host Shell                                │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Top Header: Logo, Title, Site Model Selector + Folder Browser Trigger │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│  ┌────────────┬──────────────────────────────────────────────────────────┐  │
│  │ Navigation │ Active Tool Viewport                                     │  │
│  │ Sidebar    │ ┌──────────────────────────────────────────────────────┐ │  │
│  │            │ │ Sandboxed Tool Context (e.g. Sequencer or Mantis)    │ │  │
│  │ [Sequencer]│ │ - Isolated CSS styling & local viewport DOM          │ │  │
│  │ [Mantis]   │ │ - Subscribed to global state broadcasts              │ │  │
│  │            │ └──────────────────────────────────────────────────────┘ │  │
│  └────────────┴──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.1 Host Shell Responsibility
The **Host Shell** acts as the parent container and orchestrator. Its responsibilities are strictly limited to:
- Rendering the persistent global header (Logo, App Title, global `Site Model Path` input, and directory browser modal trigger).
- Rendering the primary navigation control (Navigation Rail / Sidebar).
- Managing active feature visibility based on security policies and URL feature flags.
- Maintaining global application state and broadcasting state updates downstream to active tool views.
- Mounting and unmounting tool view containers.

### 1.2 Plugin Tool View Responsibility
Each **Plugin Tool View** (e.g., Sequencer, Mantis) is a self-contained functional component. Its responsibilities are:
- Rendering local view controls (filtering, test execution triggers, status dashboards).
- Managing internal view state (active device filter, active tab, expanded log views).
- Consuming state broadcasts from the Host Shell.
- Communicating with backend API endpoints for tool-specific operations.

---

## 2. Sandboxing & Isolation Requirements

To guarantee application stability across modular tools:
1. **Style Isolation**: Styles defined within a plugin view MUST NOT bleed into the Host Shell or sibling plugin views.
2. **Execution Fault Isolation**: Unhandled Javascript errors, unhandled promise rejections, or script timeouts inside a plugin view MUST be trapped locally and MUST NOT crash or freeze the Host Shell navigation or sibling views.
3. **Network & Stream Isolation**: Terminating or closing a tool view must automatically cancel or cleanup any active long-polling or Server-Sent Event (SSE) streams associated with that tool context.

---

## 3. Dynamic Feature Flagging & Security Policy

The UI MUST dynamically adapt its navigation and available tools based on two security layers:

### 3.1 Backend Security Policy
- Upon initialization, the Host Shell queries the backend security policy (listing allowed features for the current environment).
- If a feature is omitted from the backend security policy, it MUST be hard-disabled and hidden from the navigation sidebar regardless of client configuration.

### 3.2 URL / Local Storage Feature Overrides
- Users can filter active tools using URL query parameters (e.g., `?features=sequencer` or `?features=sequencer,mantis`).
- When feature query flags are parsed, valid features are persisted to client storage.
- **Single-Feature Layout Optimization**: If only one feature is enabled/allowed, the Host Shell navigation sidebar MUST automatically collapse/hide to maximize workspace area for that tool.

---

## 4. Viewport & Layout Spatial Model

The UI spatial model follows a **Viewport-Locked Layout**:

- **No Window-Level Page Scrolling**: The root window viewport is locked (`overflow: hidden`). The document body must never show a main browser scrollbar.
- **Header height**: Fixed vertical top bar providing persistent access to context controls.
- **Navigation Sidebar**: Fixed-width vertical rail or sidebar placed on the leading edge (left side).
- **Workspace Canvas**: Takes up 100% of the remaining width and height.
- **Local Panel Scrolling**: Independent scroll containers inside tool panels allow data tables, log terminals, and tree inspectors to scroll vertically and horizontally without affecting top-level layout alignment.
