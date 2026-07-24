# 🏗️ UI Architecture Specification

This document details the architectural invariants, micro-frontend plugin model, sandboxing, security policies, and cross-platform resolution requirements of the UDMI Workbench user interface.

---

## 1. System Invariants & Micro-Frontend Architecture

The Workbench UI utilizes a **Host Shell and Micro-Frontend Architecture**.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Global Host Shell                                │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Global Context Orchestration (State Broadcasting & Security Guard)    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────┐   ┌──────────────────────────┐                │
│  │ Plugin Tool View 1       │   │ Plugin Tool View 2       │                │
│  │ (e.g. Sequencer)         │   │ (e.g. Mantis)            │                │
│  │ - Sandboxed Context      │   │ - Sandboxed Context      │                │
│  └──────────────────────────┘   └──────────────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.1 Host Shell Responsibility
The **Host Shell** acts as the parent container and system orchestrator. Its responsibilities are strictly limited to:
- Managing global security policies and URL feature flag enforcement.
- Maintaining global application state and broadcasting state updates downstream to active tool views.
- Mounting and unmounting tool view containers.
- Routing backend API calls and maintaining session lifecycle.

### 1.2 Plugin Tool View Responsibility
Each **Plugin Tool View** (e.g., Sequencer, Mantis) is a self-contained, framework-agnostic functional component. Its responsibilities are:
- Managing internal view state (active device filter, active tab, expanded log views).
- Consuming state broadcasts from the Host Shell via cross-module events.
- Communicating with backend API endpoints for tool-specific operations.

---

## 2. Sandboxing & Fault Isolation Requirements

To guarantee system stability across modular tools:
1. **Style Isolation**: Styles defined within a plugin view MUST NOT bleed into the Host Shell or sibling plugin views.
2. **Execution Fault Isolation**: Unhandled Javascript errors, unhandled promise rejections, or script timeouts inside a plugin view MUST be trapped locally and MUST NOT crash or freeze the Host Shell or sibling views.
3. **Network & Stream Isolation**: Terminating or closing a tool view MUST automatically cancel or cleanup any active long-polling or Server-Sent Event (SSE) streams associated with that tool context.

---

## 3. Dynamic Feature Flagging & Security Policy

The UI MUST dynamically adapt available tools based on two security layers:

### 3.1 Backend Security Policy
- Upon initialization, the Host Shell queries the backend security policy (listing allowed features for the current environment).
- If a feature is omitted from the backend security policy, it MUST be hard-disabled and inaccessible regardless of client configuration.

### 3.2 URL / Local Storage Feature Overrides
- Users can filter active tools using URL query parameters (e.g., `?features=sequencer` or `?features=sequencer,mantis`).
- When feature query flags are parsed, valid features are persisted to client storage.

---

## 4. Cross-Platform & WSL Path Resolution Requirements

To ensure full compatibility across Linux, macOS, Windows, and Windows Subsystem for Linux (WSL):

1. **Non-Home Path Access (WSL Support)**:
   - Users running on WSL environments frequently store site models on Windows drives mounted under `/mnt/c/`, `/mnt/d/`, or custom system paths outside the Linux home directory (`~`).
   - Path resolution logic in the Host Shell and directory browsing API MUST NOT restrict path selection strictly to the user home directory (`~`).
   - File browsing APIs MUST support navigating to system root (`/`) and mount points (`/mnt/c/`, `/mnt/d/`).

2. **Path Normalization**:
   - Both forward slashes (`/`) and backward slashes (`\`) MUST be normalized to standard POSIX forward slashes before sending to backend APIs or saving to local storage.
   - Home-relative paths (`~/...`) MUST be expanded to absolute paths on the backend while displaying a user-friendly abbreviated path (`~/...`) in the UI input.
