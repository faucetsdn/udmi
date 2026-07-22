# 🧪 Tool Specification: Sequencer Micro-Frontend

This document defines the functional requirements and visual controls for the **Sequencer** compliance test execution tool.

---

## 1. Tool Purpose

The **Sequencer** tool provides an interactive interface to execute UDMI compliance test suites against target site model devices, inspect test pass/fail results, and monitor live streaming console logs.

---

## 2. Layout & Key Functional Panels

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Local Toolbar: Device Dropdown | Serial No | Min Stage | [Run Sequencer]    │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ Left Panel: Device & Test Matrix     │ Right Panel: Live Console Terminal   │
│ ┌──────────────────────────────────┐ │ ┌──────────────────────────────────┐ │
│ │ Device: AHU-1                    │ │ │ [08:35:10] INFO Sequencer...     │ │
│ │  - pointset.telemetry [PASSED]   │ │ │ [08:35:12] PASS pointset...      │ │
│ │  - system.config      [FAILED]   │ │ │                                  │ │
│ └──────────────────────────────────┘ │ └──────────────────────────────────┘ │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

### 2.1 Local Tool Toolbar
- **Device Selector**: Dropdown listing all devices discovered in the active site model path (plus an "All Devices" option).
- **Serial Number Input**: Optional text input to override target device serial number.
- **Minimum Test Stage Selector**: Filter options (`ALPHA`, `BETA`, `PREVIEW`, `STABLE`).
- **Primary Action Button**: "Run Sequencer" / "Stop Sequencer".

### 2.2 Device & Test Matrix Panel (Left)
- Summarizes test suites grouped by device ID.
- Test Status Badges: Visual indicator pill for each test (`PASSED`, `FAILED`, `SKIPPED`, `RUNNING`, `NOT_RUN`).
- Quick Actions: Clicking a failed test row triggers an option to launch Mantis AI Triage directly for that test failure.

### 2.3 Terminal Log Console Panel (Right)
- Real-time log streaming output subscribing to `/api/stream`.
- Monospace font surface with ANSI color support.
- Console controls: "Clear Console", "Search Log Lines", "Auto-scroll Lock Toggle".
