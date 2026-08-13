# 🏗️ Technical Architecture: Sequencer Plugin (`sequencer`)

This document specifies the technical architecture, backend integrations, Project Spec Builder logic, log streaming protocols, and differential log analysis subsystem for the **Sequencer Test Execution** plugin tool.

---

## 1. Domain Purpose & Integration Scope

The **Sequencer** plugin tool enables users to run automated UDMI compliance test suites against site model devices, monitor real-time execution logs, inspect pass/fail results across test stages, and perform **differential log analysis** comparing current test runs against baseline successful runs.

---

## 2. Project Spec Builder Logic (`project_spec`)

The plugin includes a structured Project Spec Builder component that formats target environment strings according to UDMI regex rules `[//provider/]project[/namespace][+user]`:

1. **Provider Selection**: Options: `gbos`, `gref`, `mqtt`, `pubsub`, `clearblade`.
2. **Project / Host Segment (Required)**: GCP Project ID (e.g. `bos-platform-dev`) or broker hostname (`localhost`).
3. **Namespace Segment (Optional)**: Formatted with leading `/` (e.g., `/faucetsdn`).
4. **User Segment (Optional)**: User isolation suffix (e.g., `+heykhyati`).
   - **Validation & Disabled Rule**: User segment input MUST be automatically disabled when `gbos` or `mqtt` provider is selected, as user suffixes are only supported by `gref` and `pubsub`.
5. **Preview Generator**: Generates formatted string e.g. `//gref/bos-platform-dev/faucetsdn+heykhyati`.

---

## 3. Backend Integration APIs & Data Contracts

### 3.1 Device & Result Matrix APIs
- **Discovered Devices**: `GET /api/devices?site_model=...`
- **Result Matrix**: `GET /api/device_results?site_model=...&device=...`

### 3.2 Process Control & Streaming APIs
- **Run Sequencer**: `POST /api/run_sequencer` with payload containing `site_model`, `device_id`, `project_spec`, `serial_no`, `log_level`, `min_stage`.
- **Stop Sequencer**: `POST /api/stop_sequencer`.
- **Live Terminal SSE Stream**: `GET /api/stream` (subscribes to process stdout/stderr lines).

### 3.3 Differential Log Analysis Subsystem (`POST /api/log_diff`)
- **Purpose**: Enables differential analysis between a current failing test run and a past successful baseline run.
- **Workflow**:
  1. User selects a failed test entry in the Device Results Matrix and clicks "Compare with Past Run".
  2. Plugin sends `POST /api/log_diff` payload containing `site_model`, `device_id`, `test_id`, `current_session_id`, and optional `baseline_session_id`.
  3. Server locates the latest successful baseline log file for that test case under `out/sessions/` and computes a line-by-line diff.
  4. Server returns structured diff payload (`added`, `removed`, `unchanged` log lines, timestamp deltas).
  5. Plugin renders the differential log viewer highlighting missing expected lines and extra error output.

---

## 4. State Synchronization & Event Rules

1. **Host Shell Synchronization**: Subscribes to `udmi_state_change` postMessage to update active `siteModel` path.
2. **Local Storage Persistence**: Saves selected `project_spec` parameters and device filters to client storage (`localStorage`).
3. **Cleanup Invariant**: Closing the Sequencer tab or unmounting the iframe container explicitly terminates the SSE stream listener (`EventSource.close()`).
