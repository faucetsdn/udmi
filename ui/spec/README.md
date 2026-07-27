# UDMI Workbench UI Specification Suite

Welcome to the canonical specification suite for the **UDMI Workbench UI**.

The UDMI Workbench is a user interface layer built on top of existing UDMI tools to provide a friendly, accessible, and high-productivity user experience for IoT device testing and debugging.

---

## Architecture & Plugin Model

1. **Host Shell & Micro-Frontend Sandboxing**:
   - The UI is built around a **Host Shell Container** that manages security policies, global application state, site model selection, and plugin navigation.
   - Individual tools operate as **isolated micro-frontend plugins** that communicate with the Host Shell via cross-module events.

2. **Dynamic Feature Flagging**:
   - Different tools are enabled or disabled dynamically using feature flags.
   - If only a single plugin is active, the navigation rail automatically collapses to 0-width to maximize screen space for the active tool.

3. **Future Extensibility**:
   - While initial workflows focus on the testing infrastructure, the plugin model is designed to seamlessly integrate future capabilities such as:
     - Update Management
     - Site Model Management
     - Fleet Monitoring & Alert Management

---

## Testing User Workflows in Scope

### 1. Testbed Architecture & Setup Validation
- **Goal**: Bring up the testing architecture and verify all required components - DUT (either simulated or actual device), MQTT broker, UDMIS, Sequencer - are up, connected, and operating correctly before starting tests.
- **Key Capability**: Interactive, dynamic topology diagrams that visually represent the connected setup based on current configuration.

### 2. Sequencer Test Execution & Monitoring
- **Goal**: Run automated compliance test suites against target devices and inspect pass/fail statuses and generate test reports.
- **Key Capability**: Real-time console log streaming with severity highlights, device test matrix, and **differential log analysis** against past successful execution runs to pinpoint regressions.

### 3. AI Assistant Integration (Mantis)
- **Goal**: Debug failed sequencer tests, execute natural language queries, and diagnose complex system failures across the workbench.
- **Key Capability**: Responsive **expandable sidebar** that can expand into a **full-screen workspace mode**. Integrates chronological event timelines, telemetry payload JSON inspection, and AI root cause report generation. 

---

## Specification Directory Structure

The `ui/spec/` directory is strictly organized into **3 Types of Specification Files**:

```
ui/spec/
├── README.md                           # Master specification suite index & system overview (this document)
│
├── server/                             # [TYPE 1] SERVER TECHNICAL SPECIFICATION
│   └── TECHNICAL_SPEC.md               # Technical spec for server.py (REST endpoints, SSE streams, process management)
│
├── shell/                              # HOST SHELL SPECIFICATIONS
│   ├── ARCHITECTURE.md                 # [TYPE 2] Technical architecture & integrations for the Host Shell
│   └── DESIGN.md                       # [TYPE 3] Layout & visual design spec for the Host Shell
│
└── plugins/                            # MICRO-FRONTEND TOOL PLUGIN SPECIFICATIONS
    ├── testbed/                        # Testbed Validation Plugin
    │   ├── ARCHITECTURE.md             # [TYPE 2] Technical architecture & backend integrations
    │   └── DESIGN.md                   # [TYPE 3] Layout & design spec (dynamic topology diagrams, component status)
    ├── sequencer/                      # Sequencer Test Execution Plugin
    │   ├── ARCHITECTURE.md             # [TYPE 2] Technical architecture & backend integrations (log diff, process lifecycle)
    │   └── DESIGN.md                   # [TYPE 3] Layout & design spec (device matrix, live terminal, diff viewer)
    └── mantis/                         # Mantis Plugin
        ├── ARCHITECTURE.md             # [TYPE 2] Technical architecture & backend integrations (triage API, trace timeline)
        └── DESIGN.md                   # [TYPE 3] Layout & design spec (expandable sidebar, full-screen mode, payload inspector)
```

---

## The 3 Specification File Types

| Type | Document Name | Purpose & Contents |
| :--- | :--- | :--- |
| **Type 1** | `server/TECHNICAL_SPEC.md` | Technical specification for `server.py`: REST API endpoints, Server-Sent Event (SSE) streams, process lifecycle management, security policy enforcement, and cross-platform path normalization. |
| **Type 2** | `ARCHITECTURE.md` | Component Technical Architecture: Details internal state management, backend service integrations, cross-module messaging protocols, sandboxing, data contracts, and event streams for a specific component (Shell or Plugin). |
| **Type 3** | `DESIGN.md` | Component Layout & Visual Design: Details spatial layout regions, viewport locking, responsive breakpoints, semantic color roles, accessibility standards (WCAG 2.1 AA, keyboard nav, ARIA attributes), and reusable component specs. |

---

## Accessibility & Quality Invariants

- **WCAG 2.1 AA Compliance**: All text and interactive elements must satisfy strict color contrast ratios (minimum 4.5:1 for standard text).
- **Keyboard Navigability**: Every feature must be accessible via keyboard shortcuts and standard focus sequences (`Tab`, `Shift+Tab`, `Escape`, `Enter`).
- **ARIA Standards**: Proper landmark roles (`role="navigation"`, `role="banner"`, `role="main"`, `role="dialog"`), live regions (`aria-live="polite"` for log streams), and explicit state flags (`aria-expanded`, `aria-selected`).
- **Cross-Platform Support**: Full path resolution support for Windows Subsystem for Linux (WSL) drive mounts (`/mnt/c/`, `/mnt/d/`) and POSIX path normalization.
