# 🖥️ Technical Specification: Backend Server (`ui/v2/server.py`)

This document defines the technical architecture, HTTP REST endpoint contracts, JSON request/response formats, Server-Sent Event (SSE) log streaming protocols, process management, session lifecycle, and local CLI tool translations for the UDMI Workbench backend server (`ui/v2/server.py`).

---

## 1. Core Server Responsibilities

The UDMI Workbench backend server (`ui/v2/server.py`) is a lightweight Python HTTP server that bridges the web interface with local UDMI command-line utilities and subprocesses. Its core responsibilities are:

1. **Frontend Asset Serving**: Serves static HTML, JavaScript, CSS, and media assets for the Host Shell and micro-frontend plugins.
2. **Subprocess Execution Routing**: Translates frontend HTTP REST requests into validated local tool invocations (`bin/udmi start`, `bin/sequencer`, `util/mantis/bin/triage`, `bin/test_validator`).
3. **Real-Time SSE Log Streaming**: Streams process `stdout`/`stderr` and log outputs continuously to the frontend via Server-Sent Events (SSE).
4. **Session Management & Isolation**: Allocates unique session IDs, isolates logs/reports under `out/sessions/<session_id>/`, manages process groups (`os.setsid`), and prunes historical session folders.
5. **Feature & Security Policy Enforcement**: Evaluates system policies (`--features=...`) to expose allowed tools and restrict unpermitted capabilities.
6. **Cross-Platform Disk & Path Normalization**: Normalizes POSIX vs. Windows backslashes, expands tilde home paths (`~`), abbreviates paths for display (`to_home_relative()`), and enables access to WSL drive mounts (`/mnt/c/`, `/mnt/d/`) while preventing unauthorized path traversal.

---

## 2. HTTP REST Endpoint Contracts & Local Tool Translations

### 2.1 System & Feature Discovery

#### `GET /api/features`
- **Description**: Returns the list of active tools permitted by server policy and CLI configurations.
- **Request Parameters**: None
- **Response Format `200 OK`**:
  ```json
  [
    "testbed",
    "sequencer",
    "mantis"
  ]
  ```
- **Local Translation**: Returns `list(ALLOWED_FEATURES)` populated from command-line flag `--features=...` or system defaults.

---

## 2.2 Workspace & File System Navigation

#### `GET /api/list`
- **Description**: Browses subdirectories and files for selecting site model folders.
- **Query Parameters**:
  - `path` *(string, optional)*: Target directory path (absolute, `~`, `/`, `/mnt/c`, `/mnt/d`). Defaults to user home directory (`~`).
- **Response Format `200 OK`**:
  ```json
  {
    "path": "~/Projects/udmi/sites",
    "absolute_path": "/usr/local/google/home/user/Projects/udmi/sites",
    "parent_path": "~/Projects/udmi",
    "entries": [
      {
        "name": "udmi_site_model",
        "is_dir": true,
        "path": "~/Projects/udmi/sites/udmi_site_model"
      }
    ]
  }
  ```
- **Response Format `403 Forbidden`** (Path traversal violation):
  ```json
  {
    "error": "Access denied: Path outside allowed workspace boundary."
  }
  ```
- **Local Path Translation**:
  - Tilde expansion: `os.path.abspath(os.path.expanduser(path))`
  - POSIX normalization: `path.replace('\\', '/')`
  - Display abbreviation: `to_home_relative(abs_path)` converts `/home/user/...` to `~/...`
  - WSL Mount Support: Preserves paths starting with `/mnt/c/` or `/mnt/d/`.

#### `GET /api/read_file`
- **Description**: Reads content of a text file (e.g. metadata, configuration, or log file).
- **Query Parameters**:
  - `path` *(string, required)*: Path to target file.
- **Response Format `200 OK`**:
  ```json
  {
    "path": "~/Projects/udmi/sites/udmi_site_model/cloud_iot_config.json",
    "content": "{\n  \"project_id\": \"bos-platform-dev\",\n  \"registry_id\": \"udmi_target_registry\"\n}"
  }
  ```

---

### 2.3 Testbed Architecture & Setup Validation

#### `GET /api/testbed/status`
- **Description**: Returns real-time health and connectivity status of local testbed infrastructure components.
- **Request Parameters**: None
- **Response Format `200 OK`**:
  ```json
  {
    "overall_status": "HEALTHY",
    "timestamp": 1784710000000,
    "components": {
      "mqtt_broker": {
        "status": "UP",
        "endpoint": "localhost:1883",
        "latency_ms": 4
      },
      "validator": {
        "status": "UP",
        "version": "1.4.2"
      },
      "sequencer": {
        "status": "READY"
      },
      "udmis": {
        "status": "UP",
        "mode": "LOCAL"
      }
    }
  }
  ```
- **Local Tool Translation**:
  - Executes TCP socket ping to `localhost:1883` (Mosquitto MQTT broker).
  - Verifies running process list for `udmis` and `java -cp ... SequenceRunner`.
  - Runs quick schema validation check: `bin/test_validator --check`.

#### `GET /api/testbed/topology`
- **Description**: Generates dynamic architecture topology node graph based on active `project_spec` and configuration.
- **Query Parameters**:
  - `site_model` *(string, required)*: Path to site model directory.
  - `project_spec` *(string, optional)*: Transport environment string (e.g. `//mqtt/localhost`, `//pubsub/bos-platform-dev`).
- **Response Format `200 OK`**:
  ```json
  {
    "topology_type": "LOCAL_MQTT",
    "nodes": [
      {"id": "site_model", "label": "Site Model Store", "kind": "config", "status": "VALID"},
      {"id": "validator", "label": "Schema Validator", "kind": "service", "status": "UP"},
      {"id": "mqtt_broker", "label": "Local MQTT Broker", "kind": "broker", "status": "UP"},
      {"id": "sequencer", "label": "Sequencer Engine", "kind": "runner", "status": "READY"},
      {"id": "udmis", "label": "UDMIS Reflective Core", "kind": "backend", "status": "UP"}
    ],
    "edges": [
      {"source": "site_model", "target": "validator", "label": "Schema Contract"},
      {"source": "validator", "target": "mqtt_broker", "label": "Telemetry Ingestion"},
      {"source": "mqtt_broker", "target": "sequencer", "label": "Sequence Events"},
      {"source": "sequencer", "target": "udmis", "label": "Reflective Sync"}
    ]
  }
  ```

#### `POST /api/testbed/start`
- **Description**: Brings up local testing environment infrastructure components.
- **Request Payload**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "project_spec": "//mqtt/localhost"
  }
  ```
- **Response Format `200 OK`**:
  ```json
  {
    "session_id": "testbed-20260727-1042",
    "status": "starting",
    "message": "Local testbed environment initialization launched."
  }
  ```
- **Local Tool Translation**:
  - Spawns background process group:
    ```bash
    bin/udmi start sites/udmi_site_model //mqtt/localhost
    ```
  - Redirects logs to `out/sessions/<session_id>/testbed_start.log`.

---

### 2.4 Device Compliance Status & Discovery

#### `GET /api/devices`
- **Description**: Discovers devices configured within the active site model directory.
- **Query Parameters**:
  - `site_model` *(string, required)*
- **Response Format `200 OK`**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "devices": [
      "AHU-1",
      "VAV-201",
      "Lighting-Ctrl"
    ]
  }
  ```
- **Local Translation**: Scans directory `sites/<site_model>/devices/*` and lists subdirectories containing `metadata.json`.

#### `GET /api/device_results`
- **Description**: Fetches per-device test pass/fail results and stage classifications.
- **Query Parameters**:
  - `site_model` *(string, required)*
  - `device` *(string, optional)*
- **Response Format `200 OK`**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "device": "AHU-1",
    "results": {
      "pointset.telemetry": {
        "status": "PASSED",
        "stage": "ALPHA",
        "timestamp": "2026-07-27T08:30:00Z"
      },
      "system.config": {
        "status": "FAILED",
        "stage": "BETA",
        "timestamp": "2026-07-27T08:31:15Z"
      }
    }
  }
  ```
- **Local Translation**: Reads `sites/<site_model>/out/devices/<device>/results.json` or `out/sequencer.json`.

---

### 2.5 Sequencer Test Execution & Differential Log Analysis

#### `POST /api/run_sequencer`
- **Description**: Launches automated Sequencer test suite run against target device.
- **Request Payload**:
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
- **Response Format `200 OK`**:
  ```json
  {
    "session_id": "seq-20260727-8912",
    "status": "started"
  }
  ```
- **Local Tool Translation**:
  - Creates session directory `out/sessions/seq-20260727-8912/`.
  - Spawns process group (`preexec_fn=os.setsid`):
    ```bash
    bin/sequencer -a -s 12345678 sites/udmi_site_model //gref/bos-platform-dev/faucetsdn+heykhyati AHU-1
    ```
  - Redirects process `stdout` and `stderr` to `out/sessions/seq-20260727-8912/sequencer.log`.

#### `POST /api/stop_sequencer`
- **Description**: Terminates active Sequencer execution.
- **Request Payload**:
  ```json
  {
    "session_id": "seq-20260727-8912"
  }
  ```
- **Response Format `200 OK`**:
  ```json
  {
    "status": "stopped",
    "session_id": "seq-20260727-8912"
  }
  ```
- **Local Tool Translation**: Sends `signal.SIGTERM` (followed by `signal.SIGKILL` if needed) to the process group PID (`os.killpg`).

#### `POST /api/log_diff`
- **Description**: Performs differential log analysis comparing current test run logs against baseline successful run logs.
- **Request Payload**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "device_id": "AHU-1",
    "test_id": "system.config",
    "current_session_id": "seq-20260727-8912",
    "baseline_session_id": "seq-20260720-1045"
  }
  ```
- **Response Format `200 OK`**:
  ```json
  {
    "device_id": "AHU-1",
    "test_id": "system.config",
    "current_session_id": "seq-20260727-8912",
    "baseline_session_id": "seq-20260720-1045",
    "has_baseline": true,
    "diff_lines": [
      {
        "type": "unchanged",
        "line": "INFO Sequencer: Starting test case system.config"
      },
      {
        "type": "removed",
        "line": "INFO Received system config ACK from device"
      },
      {
        "type": "added",
        "line": "ERROR Config update timed out after 30000ms"
      }
    ]
  }
  ```
- **Local Tool Translation**: Executes Python line-by-line diff comparison between `out/sessions/seq-20260727-8912/sequencer.log` and `out/sessions/seq-20260720-1045/sequencer.log`.

---

### 2.6 Mantis AI Assistant & Triage Execution

#### `POST /api/run_triage`
- **Description**: Launches AI Root Cause Analysis process for a failed test run.
- **Request Payload**:
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "device_id": "AHU-1",
    "test_id": "system.config",
    "project_spec": "//gref/bos-platform-dev/faucetsdn+heykhyati"
  }
  ```
- **Response Format `200 OK`**:
  ```json
  {
    "session_id": "mantis-20260727-4412",
    "status": "started"
  }
  ```
- **Local Tool Translation**:
  - Creates session directory `out/sessions/mantis-20260727-4412/`.
  - Spawns background process group (`preexec_fn=os.setsid`):
    ```bash
    util/mantis/bin/triage -i out/sessions/seq-20260727-8912 -d AHU-1 -t system.config --swe
    ```
  - Redirects logs to `out/sessions/mantis-20260727-4412/triage.log`.

#### `POST /api/stop_triage`
- **Description**: Terminates active AI triage process.
- **Response Format `200 OK`**:
  ```json
  {
    "status": "stopped",
    "session_id": "mantis-20260727-4412"
  }
  ```

#### `GET /api/triage_report`
- **Description**: Fetches generated Markdown triage report.
- **Query Parameters**:
  - `site_model` *(string, required)*
  - `device_id` *(string, required)*
  - `test_id` *(string, required)*
- **Response Format `200 OK`**:
  ```json
  {
    "device_id": "AHU-1",
    "test_id": "system.config",
    "report_markdown": "# Root Cause Analysis Report\n\n## Failure Summary\nDevice `AHU-1` failed `system.config` due to missing required property `system.min_power`.\n\n## Recommended Remediation\n1. Update device metadata under `sites/udmi_site_model/devices/AHU-1/metadata.json`."
  }
  ```
- **Local Translation**: Reads `out/sessions/<session_id>/diagnose/report_<test_id>.md` or `out/mantis/<session_id>/diagnose/report.md`.

#### `POST /api/ai_query`
- **Description**: Handles free-form natural language queries sent to the AI Assistant.
- **Request Payload**:
  ```json
  {
    "query": "Why did AHU-1 fail system.config validation?",
    "context": {
      "site_model": "sites/udmi_site_model",
      "active_device": "AHU-1"
    }
  }
  ```
- **Response Format `200 OK`**:
  ```json
  {
    "query_id": "q-10492",
    "answer_markdown": "### Analysis Summary\nDevice `AHU-1` missing required property `system.min_power` in state payload."
  }
  ```

---

## 3. Real-Time Server-Sent Event (SSE) Log Stream Specification

### 3.1 Streaming Endpoints
- **Sequencer Console Log Stream**: `GET /api/stream`
- **AI Triage Console Stream**: `GET /api/triage_stream`

### 3.2 SSE HTTP Headers & Framing
```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
Access-Control-Allow-Origin: *
```

### 3.3 Event Payload Framing
Output lines from running process `stdout`/`stderr` pipes are formatted as standard Server-Sent Events:
```http
data: [2026-07-27 10:45:01] INFO Sequencer: Beginning test suite for AHU-1...

data: [2026-07-27 10:45:03] PASS pointset.telemetry

data: [2026-07-27 10:45:05] FAIL system.config - Config ACK timed out after 30000ms
```

### 3.4 Connection Teardown Rule
When the client closes the SSE stream connection or sends a `stop` API call, the backend server automatically detaches non-blocking line readers and closes stream handles cleanly.

---

## 4. Session & Process Lifecycle Invariants

1. **Thread-Safe Process Table**: All active subprocess handles are stored in `active_processes = {}` locked via `threading.Lock()`.
2. **Process Group Isolation**: Subprocesses are spawned using `subprocess.Popen(..., preexec_fn=os.setsid)` so child processes (such as `java`, `mosquitto`, `python3`) are killed cleanly when a stop signal is received.
3. **Session Pruning Invariant**: Calling `prune_old_sessions(10)` automatically scans `out/sessions/` and removes non-active, historical session directories when session count exceeds 10.
