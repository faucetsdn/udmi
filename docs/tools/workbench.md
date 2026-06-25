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

## The Mantis Debugger

The [**Mantis**](./mantis.md) tool is a passive trace explorer and MQTT transaction debugger. It allows you to inspect the historical state transitions and configuration handshakes of a device.

### Key Features & How to Use:
1.  **Device Selector**: Select the device you want to inspect from the local **Device** dropdown.
2.  **Scenario Discovery**: Select an executed test run scenario (e.g., `blob_update_success`, `config_logging`) from the local **Debug Scenario** dropdown. Mantis automatically scans that device's test output history.
3.  **Chronological Timeline**: View a vertical, color-coded sequence timeline of all MQTT messages exchanged during that scenario, sorted precisely in chronological order.
    *   **Settings Icons (Orange)**: Represent configuration handshakes.
    *   **Swap Icons (Green)**: Represent state reports.
    *   **Notification Icons (Red)**: Represent system error alerts.
4.  **MQTT Payload Inspector**: Click any node on the timeline to fetch and display its raw JSON payload in the collapsible tree viewer. Use the **Copy Button** to copy the formatted JSON to your clipboard with one click.

