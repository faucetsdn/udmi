# 🖥️ Technical Specification: Backend Server (`ui/src/server.py`)

This document defines the technical architecture, HTTP REST endpoints, Server-Sent Event (SSE) log streaming protocols, process management, session lifecycle, and cross-platform path resolution rules for the UDMI Workbench backend server (`ui/src/server.py`).

---

## 1. Domain Responsibilities & Architecture

The backend server (`server.py`) acts as the lightweight local server and process manager for the Workbench UI:
- **Static Asset Serving**: Serves frontend HTML, JavaScript, CSS, and media assets rooted at the repository root.
- **REST API Subprocess Router**: Translates frontend HTTP requests into local tool process executions (Sequencer, Mantis AI Triage, Testbed validation checks).
- **Live Stream Engine**: Maintains real-time Server-Sent Event (SSE) streams for process stdout/stderr output.
- **Session & Output Manager**: Manages session directories under `out/sessions/<session_id>`, storing run logs, intermediate artifacts, and reports.
- **Security & Feature Policy Enforcer**: Exposes permitted backend capabilities based on system policy and URL feature overrides.
- **Cross-Platform & WSL Path Resolver**: Normalizes Linux, macOS, Windows, and WSL paths to guarantee cross-environment compatibility.

---

## 2. HTTP REST Endpoint Contracts

### 2.1 System & Feature Discovery

#### `GET /api/features`
- **Description**: Returns the list of tools enabled by backend policy for the current environment.
- **Response `200 OK`**:
  ```json
  ["sequencer", "mantis", "testbed"]
  ```

---

### 2.2 Workspace & File System Navigation

#### `GET /api/list`
- **Description**: Lists subdirectories and files for folder navigation.
- **Query Parameters**:
  - `path` *(string, optional)*: Absolute path, home-relative path (`~`), system root (`/`), or WSL mount point (`/mnt/c/`, `/mnt/d/`). Defaults to user home directory (`~`).
- **Response `200 OK`**:
  ```json
  {
    "current_path": "/usr/local/google/home/user/Projects/udmi/sites",
    "display_path": "~/Projects/udmi/sites",
    "parent_path": "/usr/local/google/home/user/Projects/udmi",
    "entries": [
      {"name": "udmi_site_model", "is_dir": true, "path": "/usr/local/google/home/user/Projects/udmi/sites/udmi_site_model"}
    ]
  }
  ```
- **WSL Path Resolution Requirement**: Must allow navigation to `/mnt/c` and `/mnt/d` without restricting paths strictly to `/home/user`.

---

### 2.3 Site Model & Device Compliance Status

#### `GET /api/devices`
- **Description**: Discovers devices configured in the specified site model directory.
- **Query Parameters**: `site_model` *(string, required)*
- **Response `200 OK`**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "devices": ["AHU-1", "VAV-201", "Lighting-Ctrl"]
  }
  ```

#### `GET /api/device_results`
- **Description**: Returns compliance test pass/fail matrix for devices in a site model.
- **Query Parameters**:
  - `site_model` *(string, required)*
  - `device` *(string, optional)*
- **Response `200 OK`**:
  ```json
  {
    "device_id": "AHU-1",
    "tests": {
      "pointset.telemetry": {
        "status": "PASSED",
        "timestamp": "2026-07-27T08:00:00Z",
        "stage": "ALPHA"
      },
      "system.config": {
        "status": "FAILED",
        "timestamp": "2026-07-27T08:01:15Z",
        "stage": "BETA"
      }
    }
  }
  ```

---

### 2.4 Testbed Architecture & Setup Validation

#### `GET /api/testbed/status`
- **Description**: Checks the health and connection status of testbed components (MQTT Broker, Validator, Sequencer, UDMIS).
- **Response `200 OK`**:
  ```json
  {
    "status": "HEALTHY",
    "components": {
      "mqtt_broker": {"status": "UP", "endpoint": "localhost:1883"},
      "validator": {"status": "UP", "version": "1.4.2"},
      "sequencer": {"status": "READY"},
      "udmis": {"status": "UP", "mode": "LOCAL"}
    }
  }
  ```

#### `GET /api/testbed/topology`
- **Description**: Returns the dynamic topology diagram node graph based on active testbed configuration.
- **Response `200 OK`**:
  ```json
  {
    "nodes": [
      {"id": "site_model", "label": "Site Model", "type": "config", "status": "ACTIVE"},
      {"id": "validator", "label": "UDMI Validator", "type": "service", "status": "UP"},
      {"id": "mqtt", "label": "MQTT Broker", "type": "broker", "status": "UP"},
      {"id": "sequencer", "label": "Sequencer Runner", "type": "runner", "status": "READY"},
      {"id": "udmis", "label": "UDMIS Runtime", "type": "backend", "status": "UP"}
    ],
    "edges": [
      {"source": "site_model", "target": "validator"},
      {"source": "validator", "target": "mqtt"},
      {"source": "mqtt", "target": "sequencer"},
      {"source": "sequencer", "target": "udmis"}
    ]
  }
  ```

---

### 2.5 Sequencer Test Execution & Differential Log Analysis

#### `POST /api/run_sequencer`
- **Description**: Launches a Sequencer test run process.
- **Payload**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "device_id": "AHU-1",
    "project_spec": "//gref/bos-platform-dev/faucetsdn+heykhyati",
    "serial_no": "12345678",
    "log_level": "INFO",
    "min_stage": "ALPHA"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "session_id": "seq-20260727-8912",
    "status": "started"
  }
  ```

#### `POST /api/stop_sequencer`
- **Description**: Terminates active Sequencer execution.
- **Response `200 OK`**:
  ```json
  {"status": "stopped"}
  ```

#### `GET /api/stream`
- **Description**: Server-Sent Event (SSE) stream returning real-time console log lines from the active Sequencer process.
- **Format**: `text/event-stream`

#### `POST /api/log_diff`
- **Description**: Performs differential log analysis comparing the logs of a current failed test run against a past successful run for the target device.
- **Payload**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "device_id": "AHU-1",
    "test_id": "system.config",
    "current_session_id": "seq-20260727-8912",
    "baseline_session_id": "seq-20260720-1045"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "device_id": "AHU-1",
    "test_id": "system.config",
    "has_baseline": true,
    "diff": [
      {"type": "unchanged", "line": "INFO Starting test system.config"},
      {"type": "removed", "line": "INFO Received config ack from device"},
      {"type": "added", "line": "ERROR Config update timed out after 30000ms"}
    ]
  }
  ```

---

### 2.6 AI Assistant & Mantis Triage

#### `POST /api/run_triage`
- **Description**: Launches AI Root Cause Analysis process for a failed test run.
- **Payload**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "device_id": "AHU-1",
    "test_id": "system.config",
    "project_spec": "//gref/bos-platform-dev/faucetsdn+heykhyati"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "session_id": "mantis-20260727-4412",
    "status": "started"
  }
  ```

#### `POST /api/stop_triage`
- **Description**: Terminates active AI triage process.
- **Response `200 OK`**: `{"status": "stopped"}`

#### `GET /api/triage_stream`
- **Description**: SSE stream delivering real-time execution thoughts and trace logs from the AI Assistant.

#### `GET /api/triage_report`
- **Description**: Fetches the rendered AI triage Markdown report.
- **Response `200 OK`**:
  ```json
  {
    "device_id": "AHU-1",
    "test_id": "system.config",
    "report_markdown": "# Root Cause Analysis Report\n\n..."
  }
  ```

#### `POST /api/ai_query`
- **Description**: Handles free-form natural language queries sent to the AI Assistant.
- **Payload**: `{"query": "Why did AHU-1 fail telemetry validation?", "context": {"site_model": "sites/udmi_site_model"}}`
- **Response `200 OK`**: `{"answer_markdown": "..."}`

---

## 3. Subprocess Management & Session Lifecycle

1. **Thread Safety**: All process tracking dictionary operations use `threading.Lock()` (`active_processes_lock`).
2. **Session Output Isolation**: Execution outputs are logged under `out/sessions/<session_id>/`.
3. **Session Pruning**: `prune_old_sessions(max_sessions=10)` automatically prunes non-active, historical session directories to prevent disk accumulation.
4. **Signal Handling**: Subprocesses are spawned using process groups (`preexec_fn=os.setsid`) and terminated cleanly with `SIGTERM` / `SIGKILL` sequence.

---

## 4. Cross-Platform & WSL Path Resolution Rules

1. **POSIX Path Normalization**: All incoming path parameters are normalized to use forward slashes (`/`).
2. **Home Directory Abbreviation**: `to_home_relative(path)` abbreviates absolute paths starting with the user's home directory to `~/...` for clean UI display.
3. **WSL Mount Support**: Paths pointing to `/mnt/c/`, `/mnt/d/`, or custom system mount points are preserved and supported without home directory boundary restrictions.
