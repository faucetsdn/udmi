[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [workbench](#)

# UDMI Workbench: Visual Workspace Guide

**UDMI Workbench** is a modern, unified web application designed to simplify UDMI device development, compliance testing, and device debugging. It replaces complex terminal-based workflows with a highly polished, responsive visual dashboard aligned with Material 3 styling.

---

## Quick Start: Launching Workbench

UDMI Workbench features a launcher script located at **`bin/workbench`** which manages the backend Python API server and triggers your default browser with appropriate feature flags.

Open your terminal and run any of the following commands:

### 1. Launch the Complete Workspace Suite
Opens the unified multi-tab workspace, showing the logo, global Site Model inputs, the Navigation Rail, and all the available tools:
```bash
bin/workbench
```

### 2. Launch with selective features (e.g. sequencer)
Runs the UI in standalone full-screen mode. The navigation sidebar is hidden, and the Sequencer app occupies the entire browser window:
```bash
bin/workbench sequencer
# or shorthand:
bin/workbench seq
```

---

## The Sequencer Dashboard

The [**Sequencer**](./sequencer.md) tool allows you to run, manage, and monitor end-to-end UDMI compliance tests on a Device Under Test (DUT).

### Key Features & How to Use:
1.  **Global Site Model**: Enter or browse your Site Model path (e.g., `sites/udmi_site_model`) in the top global toolbar.
2.  **Local DUT Selector**: Choose the target device to test from the local **Device Under Test** dropdown. The tool automatically scans the site model's `devices/` folder and caches your last active device.
3.  **Target Project**: Specify the target MQTT broker address (default: `//mqtt/localhost`).
4.  **Advanced Settings**: Click the **Settings Gear** icon in the toolbar to open the advanced execution popover. This allows you to customize the underlying test flags:
    *   **Log Level**: Adjusts the verbosity of the log output:
        *   `INFO` (Default): Standard execution progress logging.
        *   `DEBUG`: Enables verbose debug logging (equivalent to the command-line `-v` flag).
        *   `TRACE`: Enables highly detailed trace logging (equivalent to the command-line `-vv` flag).
    *   **Min Test Stage**: Filters which compliance tests are executed:
        *   `PREVIEW` (Default): Runs stable and preview-stage tests.
        *   `ALPHA`: Includes experimental alpha-stage tests in the run (equivalent to the command-line `-a` flag).
        *   `ALPHA_ONLY`: Restricts execution exclusively to alpha-stage tests (equivalent to the command-line `-x` flag).
    *   **Serial Number**: An optional text field to override the device's default serial number during the test run (equivalent to the command-line `-s [serial_no]` flag).
5.  **Live Log Streaming**: Once you click **Run Tests**, raw logs from the backend sequencer are streamed **instantly** (0ms latency) into the dark terminal window.
6.  **Progress Tracking**: View live metrics (Passed, Skipped, and Failed test counts) and a visual progress bar indicating the exact percentage of completed tests.

---

## The Mantis Debugger & AI Triage Suite

The [**Mantis**](./mantis.md) tool is an intelligent trace explorer, MQTT transaction debugger, and automated AI diagnostic triage suite. It provides passive log inspection alongside powered Root Cause Analysis (RCA) directly within Workbench.

### Key Features & How to Use:

#### 1. Passive Trace Explorer (Trace Tab)
*   **Device & Scenario Selector**: Choose a target device and test execution scenario (e.g., `endpoint_failure_and_restart`, `blob_update_success`) from the dropdown selectors. Mantis automatically scans historical test runs.
*   **Chronological Timeline**: Inspect a vertical, color-coded sequence timeline of all MQTT telemetry, state, and config messages exchanged during the run:
    *   **Settings Icons (Orange)**: Configuration handshakes (`config`).
    *   **Swap Icons (Green)**: Device state updates (`state`).
    *   **Notification Icons (Red)**: System errors and event alerts (`events`).
*   **MQTT Payload Inspector**: Click any node on the timeline to inspect its raw JSON payload in the collapsible tree viewer with one-click clipboard copying.

#### 2. Automated AI Triage & Root Cause Analysis (AI Diagnostics Tab)
*   **Compliance Test Verdict Badges**: View real-time test status indicators (`Passed`, `Skipped`, `Failed`). To save AI quota and eliminate unnecessary API usage, tests with a `Passed` status automatically disable the AI Triage trigger.
*   **AI Credentials & Provider Configuration**: Configure diagnostic settings using either a standard **Gemini API Key** or enterprise **GCP Vertex AI (ADC)** credentials with custom GCP project and location parameters. All credentials are securely cached locally in your browser.
*   **Diagnostic Playbooks**: Select between the **Device Compliance Auditor (Standard OEM)** playbook for hardware/integrator protocol auditing or the **Codebase Debugger (Advanced SWE)** playbook for Pubber/UDMIS Java source debugging.
*   **Reference Successful Run (Baseline Run Selector)**: Use the input field or click the folder icon to launch the **Folder Browser Modal** (rooted at `$HOME`). Selecting a past passing run directory adds clean baseline logs (`sequence.log` and `sequence.md`) to the manifest, enabling Gemini to perform differential diagnosis against a known working state.
*   **Live Subprocess Streaming & RCA Reports**: Click **Run AI Triage** to launch the backend diagnostic engine (`bin/triage`). Raw logs stream in real time into the embedded terminal. Upon completion, the final AI Root Cause Analysis markdown report renders in the right panel with one-click copy support.
*   **Interactive Workspace Controls**: Enjoy a drag-to-resize split pane layout and built-in tab closure safeguards that protect against accidental navigation while an AI diagnostic run is active.

