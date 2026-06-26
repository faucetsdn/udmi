[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [workbench](#)

# UDMI Workbench: Visual Workspace Guide

**UDMI Workbench** is a unified web application for UDMI device development, compliance testing, and automated AI diagnostics. It replaces complex CLI commands with an interactive dashboard.

---

## Quick Start: Launching Workbench

The **`bin/workbench`** launcher manages the Python backend API server and opens Workbench in your browser:

```bash
# Launch full suite
bin/workbench

# Launch standalone tool (Sequencer or Mantis full-screen)
bin/workbench sequencer
bin/workbench mantis
```

---

## Screen Input Reference

### 1. Global Inputs
*   **Site Model Path**: Path to the active site model directory (e.g., `sites/udmi_site_model`). Enter manually or click the folder icon to launch the interactive directory browser.

---

### 2. The Sequencer Screen
Used to run and monitor end-to-end UDMI device compliance tests.

#### Main Header Inputs:
*   **Device Under Test (DUT)**: Dropdown selecting the target device from the site model (`devices/` folder).
*   **Target Project**: Text field specifying the MQTT broker or cloud project endpoint (default: `//mqtt/localhost`).
*   **Sequencer Version**: Displays the active Sequencer version (`HEAD`).

#### Advanced Settings (Gear Icon Popover):
*   **Log Level**: Verbosity selection (`INFO` default, `DEBUG` `-v`, `TRACE` `-vv`).
*   **Min Test Stage**: Test stage filter (`PREVIEW` default, `ALPHA` `-a`, `ALPHA_ONLY` `-x`).
*   **Serial Number**: Optional text override for device serial number (`-s`).

#### Test Suite Checklist & Historical Runs:
*   **Search Box**: Filter test cases by name.
*   **Checkboxes**: Select specific test cases to execute (includes **Select All** / **Deselect All** toggles).
*   **Historical Status Display**: Automatically loads and displays previous execution results (`Passed` ✔, `Failed` ✘, `Skipped` ⊘) for each test case from disk artifacts (`out/devices/`).
*   **Artifact Inspection**: Click any test item to open the artifact viewer modal and inspect historical `sequence.md` summaries and `sequence.log` output.
*   **Direct AI Diagnosis**: Click the inline **Diagnose with Mantis AI** brain icon on any failed test to switch directly to the Mantis screen for immediate triage of past run results.

---

### 3. The Mantis Screen
Used to inspect MQTT telemetry transactions and run AI-powered triage root cause analysis (RCA).

#### Main Header Inputs:
*   **Device**: Dropdown selecting the target device.
*   **Debug Scenario**: Dropdown selecting test execution runs. Automatically scans historical test artifacts from disk, displaying historical run verdicts (`Passed` ✔, `Failed` ✘, `Skipped` ⊘), target project badges, and execution timestamps.
*   **Historical Run Triage**: Historical test runs can be selected and triaged immediately using AI Diagnostics without triggering a fresh test execution.

#### Timeline Trace Tab:
*   **Interactive Node Selection**: Click any node on the sequence flow graph to view raw `config`, `state`, or `events` MQTT payloads in the JSON inspector with one-click clipboard copying.

#### AI Diagnostics Tab Inputs:
*   **Diagnostic Playbook**: Select triage depth:
    *   `Device Compliance Auditor (Standard OEM)`: Hardware/protocol compliance validation.
    *   `Codebase Debugger (Advanced SWE)`: Source code debugging.
*   **Reference Successful Run** *(Optional)*: Text path or folder browser selection pointing to a baseline passing test run (e.g., `out_prev`). Enables differential diagnosis against a known working state.
*   **AI Auth Provider**: Choose authentication mode:
    *   `Gemini API Key`: Enter your Gemini API key in the password field.
    *   `Google Vertex AI (ADC)`: Enter **GCP Project ID** and **GCP Location** (default: `global`).
*   **Fetch UDMIS Logs**: Checkbox toggling automatic fetching of cloud-hosted UDMIS container logs when debugging remote GKE/cloud targets.
    *   **Cloud Logging GCP Project ID**: Text field for the target cloud project ID (auto-populated from historical test metadata).
*   **Run AI Triage Button**: Launches the backend diagnostic agent on the selected scenario. Disabled for tests with a `Passed` status to preserve API quota.
