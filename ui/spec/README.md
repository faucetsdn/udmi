# 📐 UDMI Workbench UI Specification

Welcome to the canonical specification suite for the **UDMI Workbench UI**.

This directory separates **Permanent Core Specifications** (domain requirements, invariants, API contracts) from **Disposable Implementation Presets** (visual styling and layout grids).

---

## 📂 Specification Directory Index

```
ui/spec/
├── core/                                # PERMANENT CORE SPECIFICATIONS (Kept during UI rebuilds)
│   ├── 01-architecture.md               # System invariants, micro-frontend plugin model, sandboxing, WSL path rules
│   ├── 02-functional-requirements.md    # WHAT to display & control (domain capabilities, controls, device matrix)
│   ├── 03-data-and-api-contracts.md     # REST API endpoints & SSE log stream contracts
│   ├── 04-state-and-events.md           # Global state keys, postMessage schemas, late-bound sync
│   ├── user-flows.md                    # End-to-end user journeys
│   └── tools/                           # Tool core requirements (shell.md, sequencer.md, mantis.md)
└── implementation/                      # DISPOSABLE IMPLEMENTATION PRESETS (UI v1 Presentation)
    ├── layout-and-viewport.md           # Spatial layout regions, viewport locking, panel scroll models
    └── design-guidelines.md             # Visual design tokens, color roles, typography, component CSS specs
```

---

## 🏛️ 1. Permanent Core Specifications ([`ui/spec/core/`](./core/README.md))

These documents define **WHAT** the UI does, its system invariants, backend API contracts, and user interactions. **These specs remain constant across any UI rebuild or framework migration.**

| Core Document | Description |
| :--- | :--- |
| **[core/01-architecture.md](./core/01-architecture.md)** | System invariants, micro-frontend plugin model, frame sandboxing, security policy guards, and WSL path resolution. |
| **[core/02-functional-requirements.md](./core/02-functional-requirements.md)** | **WHAT to display & control**: Workspace context, Project Spec Builder, device matrix, log streaming, and AI triage views. |
| **[core/03-data-and-api-contracts.md](./core/03-data-and-api-contracts.md)** | REST API endpoints, query parameter schemas, Server-Sent Event (SSE) log stream formats, and backend subprocess integration. |
| **[core/04-state-and-events.md](./core/04-state-and-events.md)** | Global application state, cross-module event messaging schemas, late-bound synchronization, and local storage persistence. |
| **[core/user-flows.md](./core/user-flows.md)** | End-to-end user journeys, site model selection workflows, execution loops, AI triage investigation, and error scenarios. |
| **[core/tools/](./core/tools/)** | Functional requirements for individual tools ([shell.md](./core/tools/shell.md), [sequencer.md](./core/tools/sequencer.md), [mantis.md](./core/tools/mantis.md)). |

---

## 🎨 2. Disposable Implementation Presets ([`ui/spec/impl/`](./impl/README.md))

These documents define **HOW** the current UI presentation layer is laid out and styled. **If you throw away the current UI and build a new UI from scratch, you can discard or overwrite this directory completely.**

| Implementation Preset | Description |
| :--- | :--- |
| **[impl/layout-and-viewport.md](./impl/layout-and-viewport.md)** | Spatial layout regions, viewport locking, panel scroll models, and responsiveness. |
| **[impl/design-guidelines.md](./impl/design-guidelines.md)** | Visual design tokens, semantic color roles, typography scale, and component styling specs. |

---

## 🛠️ Ground-Rule Principles for Implementers

1. **Strict Sandboxing**: Each tool micro-frontend MUST operate in isolation. A failure or script exception in one tool view MUST NEVER crash the parent orchestrator or neighbor tools.
2. **Late-Bound State Synchronization**: When switching active tool views or loading a new tool, the global state (such as the active `siteModel` path) MUST be pushed immediately upon view readiness.
3. **No Direct Backend Subprocess Invocation**: The UI frontend never executes local processes directly; all execution and log streaming MUST route through backend API contracts ([`core/03-data-and-api-contracts.md`](./core/03-data-and-api-contracts.md)).
