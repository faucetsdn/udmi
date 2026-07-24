# 📋 UI Functional Requirements & Capabilities Specification

This document defines **WHAT** information, data entities, user controls, and functional capabilities the UDMI Workbench UI must display and expose to the user—regardless of how the interface is spatially laid out or visually styled.

> **Note**: While layout ([03-layout-and-viewport.md](./03-layout-and-viewport.md)) and design guidelines ([04-design-guidelines.md](./04-design-guidelines.md)) are disposable presentation specifications that can be thrown out during a redesign, **this document defines the core domain capabilities that remain constant across any UI rebuild**.

---

## 1. Global Workspace Context & Environment Capabilities

The UI MUST display and expose the following global workspace controls:

1. **Active Site Model Path Indicator**:
   - Displays the currently selected site model directory path (e.g., `sites/udmi_site_model`).
   - Exposes a text input and path editor.
2. **Workspace Status Indicator**:
   - Displays a clear visual badge indicating workspace status (`LOADED` when a valid site model directory is connected vs `NO SITE MODEL` when unconfigured).
3. **Interactive File System Explorer**:
   - Displays an interactive directory browser to navigate file paths.
   - Displays directory listings, folder names, parent folder navigation (`..`), and path confirmation triggers.
   - Supports navigating home directories (`~`), system root (`/`), and WSL drive mounts (`/mnt/c`, `/mnt/d`).
4. **First-Launch Setup Prompt**:
   - Exposes an attention-getting setup callout when no valid site model path is configured on initial application launch.

---

## 2. Target Environment & Project Specification Capabilities (`project_spec`)

The UI MUST provide structured controls to configure the target IoT execution environment string (`project_spec`) matching UDMI regex rules `[//provider/]project[/namespace][+user]`:

1. **Provider Selection**: Exposes options for selecting the transport provider (`gbos`, `gref`, `mqtt`, `pubsub`, `clearblade`).
2. **Project / Host Identifier**: Exposes a text input for GCP project ID (e.g., `bos-platform-dev`) or broker hostname (e.g., `localhost`).
3. **Namespace Segment (Optional)**: Exposes a text input for logical prefixing (`/namespace` -> `namespace~...`).
4. **User Segment (Optional)**: Exposes a text input for user isolation (`+user` -> subscription suffix).
   - **Validation Requirement**: User segment input MUST be automatically disabled with explanatory feedback when `gbos` or `mqtt` is selected, as user suffixes are only supported by `gref` and `pubsub`.
5. **Live Syntax Preview Display**: Dynamically builds and displays the formatted syntax string (e.g., `//gref/bos-platform-dev/faucetsdn+heykhyati`), clearly distinguishing namespace (`/`) from user (`+`) delimiters.

---

## 3. Device Discovery & Compliance Status Capabilities

The UI MUST display and expose the following device compliance information:

1. **Discovered Devices Listing**:
   - Displays a list of devices discovered within the active site model path (e.g., `AHU-1`, `VAV-201`), plus an "All Devices" view filter.
2. **Device Compliance Matrix**:
   - Displays per-device test result listings and statuses (`PASSED`, `FAILED`, `SKIPPED`, `RUNNING`, `NOT_RUN`).
   - Displays execution timestamps and test stage classifications (`ALPHA`, `BETA`, `PREVIEW`).
3. **Failure Action Triggers**:
   - Exposes a direct action trigger on failed test entries to launch AI Failure Triage for that specific failure.

---

## 4. Test Execution & Process Control Capabilities (Sequencer)

The UI MUST provide controls and status indicators for running compliance test suites:

1. **Execution Controls**: Primary action trigger to start (`Run Sequencer`) or terminate (`Stop Sequencer`) active test suites.
2. **Execution Parameters**: Controls to specify target serial number, log level (`INFO`, `DEBUG`), and minimum test stage (`ALPHA`, `BETA`, `PREVIEW`, `STABLE`).
3. **Process Lifecycle State**: Displays active execution state (`IDLE`, `STARTING`, `RUNNING`, `STOPPING`, `COMPLETED`, `FAILED`).

---

## 5. Console Log & Telemetry Visualization Capabilities

The UI MUST display and expose real-time console log streams:

1. **Live Stream Console**: Displays real-time, line-by-line log outputs from backend execution.
2. **Log Severity Highlighting**: Visually highlights log severity levels (`INFO`, `WARN`, `ERROR`, `PASS`, `FAIL`).
3. **Console Controls**: Exposes actions to clear console output, search/filter log text, and toggle auto-scroll locking during historical log inspection.

---

## 6. AI Failure Triage & Root Cause Investigation Capabilities (Mantis)

The UI MUST display and expose AI debugging and trace analysis capabilities:

1. **Scenario Selection**: Exposes selectors for target device ID, failed test case ID, and triage playbook rules.
2. **AI Triage Trigger**: Action button to launch automated AI Root Cause Analysis (`POST /api/run_triage`).
3. **Chronological Trace Timeline**: Displays a timestamped sequence of telemetry, state, and config events preceding a test failure.
4. **Telemetry Payload Inspector**: Displays hierarchical, collapsible JSON structures for selected trace events, complete with type highlighting and raw payload string copying.
5. **AI Root Cause Analysis Report**: Renders formatted Markdown reports explaining failure summaries, root cause details, remediation steps, and confidence scores.
