# 🔄 User Flows & Interaction Workflows Specification

This document details the primary user journeys, step-by-step interaction loops, and error recovery scenarios for the UDMI Workbench UI.

---

## Journey 1: Initializing Workspace & Selecting Site Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Launch UI ──► 2. Load Cached Path ──► 3. Option: Browse Directory Modal │
│                                                      │                      │
│ 5. Trigger Scan ◄── 4. Confirm Path Selection ◄──────┘                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

1. **Launch Application**: User opens the Workbench URL.
2. **Restore Context**: Application loads cached `siteModel` path from client storage (or falls back to default `sites/udmi_site_model`).
3. **Directory Selection Options**:
   - *Option A*: User manually edits the `Site Model Path` input text field.
   - *Option B*: User clicks the "Browse" icon button next to the input, opening the Directory Browser Modal. User navigates directory levels, selects a site model folder, and confirms.
4. **Broadcast & Scan**: Host Shell updates global state and broadcasts `udmi_state_change`. Active tool views automatically scan the selected site model path to populate available devices and test results.

---

## Journey 2: Executing Sequencer Compliance Tests

1. **View Selection**: User opens the **Sequencer** tool tab.
2. **Device Filtering**: User selects a specific target device from the device dropdown (or selects "All Devices").
3. **Execution Trigger**: User clicks the "Run Sequencer" primary action button.
   - UI updates button state to "Running / Stopping".
   - UI sends execution command to backend (`POST /api/run_sequencer`).
4. **Live Stream Monitoring**:
   - UI connects to live log stream (`GET /api/stream`).
   - Terminal log panel streams real-time test execution output line-by-line with color-coded log levels.
   - Device test matrix updates test status indicators dynamically (`PASS`, `FAIL`, `SKIP`).
5. **Execution Completion or Cancellation**:
   - User can click "Stop Sequencer" anytime to send a termination request (`POST /api/stop_sequencer`).
   - Upon process exit, UI closes log stream and updates tool state back to "Idle".

---

## Journey 3: Investigating Failures with Mantis AI Triage

1. **Identify Failure**: In Sequencer or Mantis view, user spots a test failure (e.g., `system.config` failed).
2. **Navigate to Mantis Tool**: User switches to the **Mantis** tool view.
3. **Select Target Failure**: User chooses the target Device ID and failed Test ID from the scenario selectors.
4. **Launch AI Analysis**: User clicks "Run AI Triage".
   - UI triggers backend triage subprocess (`POST /api/run_triage`).
   - Live triage console displays real-time agent processing steps via SSE stream (`GET /api/triage_stream`).
5. **Inspect Results**:
   - **Chronological Trace Timeline**: User inspects timestamped events preceding the failure.
   - **Payload Inspector**: User clicks telemetry events to inspect collapsible JSON payloads.
   - **AI Root Cause Report**: Upon triage completion, UI renders the formatted Markdown AI analysis report explaining the failure and recommended fixes.

---

## 4. Error & Edge Case Scenarios

| Scenario | UI Behavior & Requirement |
| :--- | :--- |
| **Invalid Site Model Path** | Show inline warning badge near input; clear device list; disable execution buttons until valid path is provided. |
| **Empty Device List** | Display empty state illustration with helpful message: *"No devices discovered in target site model."* |
| **Backend Subprocess Crash** | Log stream displays fatal termination alert; execution button resets to "Run"; show notification toast with error details. |
| **Network Stream Interruption** | Log viewer displays *"Connection lost. Retrying..."*; attempts automatic reconnect for up to 30 seconds before failing gracefully. |
