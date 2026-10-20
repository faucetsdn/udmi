# UDMI Workbench: Architecture & Interface Contracts

**Status**: Describes the implementation on this branch. Items that are part of the
vision but not built are listed only in [§10](#10-not-yet-implemented).  
**Package Target**: `workbench/` (Workbench UI and gateway), `mantis/` (agent and MCP tools)

---

## 1. Executive Summary & Vision

The Workbench vision converges three evolutions of UDMI device testing into one product:
1. **Mantis Workbench**: LLM-assisted diagnostic reasoning that helps device developers understand *why* tests fail and how to fix them.
2. **Test Cadre**: Reproducible local test infrastructure that lets lab operators run compliance suites without cloud dependencies.
3. **Unified Product**: Standardized reporting that lets ecosystem curators certify devices.

```
                  ┌────────────────────────────────────────────────────────┐
                  │                   WORKBENCH VISION                     │
                  └──────────────────────────┬─────────────────────────────┘
                                             │
             ┌───────────────────────────────┼──────────────────────────────┐
             ▼                               ▼                              ▼
   ┌───────────────────┐           ┌───────────────────┐          ┌───────────────────┐
   │ Mantis Workbench  │           │    Test Cadre     │          │  Unified Product  │
   │ - Assisted Diag   │           │ - Local Stack     │          │ - Compliance View │
   │ - Root Cause RCA  │           │ - Pubber / DUT    │          │ - Device Reports  │
   │ - Dev Remediation │           │ - Swappable Stack │          │ - Support Bundles │
   └─────────┬─────────┘           └─────────┬─────────┘          └─────────┬─────────┘
             │                               │                              │
             ▼                               ▼                              ▼
      Device Developer               Test Lab Operator              Ecosystem Curator
      (Deep-dive 1 DUT)              (Broad multi-DUT)              (Certify & catalog)
```

### Architectural Foundation: The MCP-First Design
The Workbench is one Python process, `workbench/server/gateway.py`, a stdlib
`ThreadingHTTPServer` started by `bin/workbench`. It serves the single-page frontend and three backend planes:
- **MCP tools** (`mantis/mcp_server.py`, `MCPServer`): Model Context Protocol (JSON-RPC 2.0) for deterministic operations. The Workbench exposes it over HTTP at `POST /rpc` and `POST /message`. `python -m mantis.mcp_server` also serves it standalone over stdio or HTTP.
- **Mantis agent** (`mantis/agent.py`), streamed to the browser as SSE by `workbench/server/mantis_adapter.py`.
- **Workbench REST and SSE routes** (`workbench/server/routes.py`) for site models, sequencer runs, the local testbed, compliance, notifications and diagnostics.

The frontend is plain ES modules with no framework and no build step (`workbench/static/`).

---

## 2. The 3 Primary User Personas & Workflows

| Persona | Core Mission | Key UI Needs | Primary Contracts Used |
| :--- | :--- | :--- | :--- |
| **Device Developer** | Develop and validate one IoT device before deployment. | Failure triage, schema inspection, state-sync timelines, remediation. | Contract 2 (Agent SSE), Contract 1 (MCP Tools), Contract 4 (Workspace State) |
| **Test Lab Operator** | Manage physical and virtual test rigs, run suites across many units. | Local stack setup (`//mqtt/localhost:<port>`), Pubber or a physical DUT, setup logs, live run logs. | Contract 1 (Testbed REST), Contract 3 (Run Log Streaming), Contract 4 (Workspace State) |
| **Ecosystem Curator** | Certify devices for installations. | Per-device compliance by feature bucket and stage, device reports, support bundles. | Contract 5 (Compliance & Reporting) |

---

## 3. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           Workbench UI (plain ES modules SPA)                           │
│  ┌───────────────────────────┐ ┌───────────────────────────┐ ┌────────────────────────┐ │
│  │ /sequencer                │ │ /devices                  │ │ Drawers & dialogs      │ │
│  │ - Run config & test list  │ │ - Compliance per device   │ │ - Mantis (Ctrl/Cmd+K)  │ │
│  │ - Live run log            │ │ - Bucket x stage matrix   │ │ - Logs (/logs)         │ │
│  │ - Local Test Setup drawer │ │ - Device reports          │ │ - Settings (gear)      │ │
│  └─────────────┬─────────────┘ └─────────────┬─────────────┘ └───────────┬────────────┘ │
│                └──────────────────────┐      │      ┌────────────────────┘              │
│                                       ▼      ▼      ▼                                   │
│                           ┌────────────────────────────────────────┐                    │
│                           │  WorkspaceStore (Contract 4, store.js) │                    │
│                           │  - siteModel, deviceId, activeTestId   │                    │
│                           │  - projectSpec, sessionId, running     │                    │
│                           └──────────────────┬─────────────────────┘                    │
└──────────────────────────────────────────────┼──────────────────────────────────────────┘
                                               │
     ┌─────────────────────────────────────────┼─────────────────────────────────────────┐
     │ 1. MCP Tool Calls (JSON-RPC)            │ 2. Agent SSE Stream    │ 3. Run Log SSE   │
     │ (POST /message or POST /rpc)            │ (POST /api/mantis/chat)│ (GET /api/       │
     │                                         │                        │  sequencer/stream│
     ▼                                         ▼                        ▼                  ▼
┌───────────────────────────────┐ ┌─────────────────────────┐ ┌──────────────────────────┐
│     MCP Server & Registry     │ │      Mantis Engine      │ │ Sequencer runner, testbed│
│   (mantis/mcp_server.py,      │ │    (mantis/agent.py)    │ │ & tmux sessions          │
│    mantis/tools/registry.py)  │ │ - Scoping               │ │ (workbench/server/       │
│ - 27 tools                    │ │ - Actor (ReAct)         │ │  runner.py, testbed.py;  │
│ - Site Model & Schema Read    │ │ - Critic                │ │  mcp/infra/              │
│ - DOT / Mermaid diagrams      │ │ - Arbitrator            │ │  session_manager.py)     │
└───────────────┬───────────────┘ └────────────┬────────────┘ └────────────┬─────────────┘
                └──────────────────────────────┴───────────────────────────┘
                                               │
                                               ▼
                              ┌──────────────────────────────────┐
                              │       Local Substrate / DUT      │
                              │  - Mosquitto, etcd, UDMIS        │
                              │  - DUT (Physical / Pubber)       │
                              └──────────────────────────────────┘
```

### 3.1. Routes, Drawers and Dialogs

The SPA has two routed views. Everything else is a drawer or dialog over the current view.

| URL / Surface | Primary Persona | Purpose & Capabilities |
| :--- | :--- | :--- |
| **`/sequencer`** (default) | Developer / Operator | Pick a site model, device and target spec, select tests, run `bin/sequencer`, watch the live log, and inspect artifacts (`RESULT.log`, `sequence.md`, `sequence.png`). Hosts the **Local Test Setup drawer** (§3.2). Failed test rows offer **Diagnose with Mantis**; every row offers **Explain this test**. |
| **`/devices`** | Curator / Operator | Compliance view: per-device verdict, feature bucket × stage score matrix, run targets, and reports (Contract 5). |
| **`/logs`** | Operator / Developer | Not a view. Loading it opens the **Logs drawer** (also opened by the header terminal icon) over `/sequencer`. It shows the client diagnostic ring buffer and `GET /api/diagnostics/logs?limit=`. |
| **Mantis drawer** | All | Opened with `Cmd/Ctrl+K`, the header button, or a test row action. It inherits the active site model, device and test. |
| **Settings dialog** | All | Header gear icon. Email notification consent and recent deliveries (§5.4.1). |

Deep links are query parameters: `?site_model=<name>&device=<id>`. Any other path falls back to `/sequencer`.

#### Mantis Entry Points
1. **Diagnose with Mantis**: on failed test rows in `/sequencer` and in the Artifact Viewer. It opens the Mantis drawer and sends a triage request with `{site_model, device_id, test_id}`.
2. **Explain this test**: on every test row. It opens the drawer and asks how the test works.
3. **Free-form questions** in the drawer, with the failed-test selector in the drawer toolbar.

---

### 3.2. Local Test Setup Drawer (Sequencer Screen Integration)

The **Local Test Setup drawer** docks to the right of `/sequencer`, so operators can manage the local stack without a terminal.

```
┌───────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Sequencer Workspace (/sequencer)                                       │ [Local Setup Drawer]     │
│ ┌────────────────────────────────────────────────────────────────────┐ │ ┌───────────────────────┐│
│ │ Run Configuration (Site Model, Device, Target Spec)                │ │ │ [Start] [Stop] [Restart││
│ ├────────────────────────────────────────────────────────────────────┤ │ ├───────────────────────┤│
│ │ [Min stage: PREVIEW ▼] [Bucket ▼] [Select all] [Clear]             │ │ │ Topology Graph        ││
│ │ [✓ Passed] [– Skipped] [✗ Failed] [Search]                         │ │ │  [DUT / Pubber]       ││
│ ├────────────────────────────────────────────────────────────────────┤ │ │        │ (MQTT:port)  ││
│ │ Test list                                                          │ │ │        ▼              ││
│ │  pointset_publish     PASSED   [View Artifacts] [Explain]          │ │ │  [Mosquitto Broker]   ││
│ │  system_last_start    FAILED   [Diagnose with Mantis] [Explain]    │ │ │        ▼              ││
│ ├────────────────────────────────────────────────────────────────────┤ │ │  [Local UDMIS Pod]    ││
│ │ Live run log (SSE, Contract 3)                                     │ │ │        ▼              ││
│ └────────────────────────────────────────────────────────────────────┘ │ │  [etcd State Store]   ││
│                                                                        │ └───────────────────────┘│
└───────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 3.2.1. Drawer Affordance & Behavior
- **Trigger**: The **Start Local Setup** button in the Sequencer toolbar, with a status dot whose class is `dot-<overall>` (`up`, `initializing`, `down`, `error`, `unknown`). Shortcut: `Cmd/Ctrl+Shift+L`.
- **Layout**: Slides in from the right without unmounting the Sequencer or interrupting a run. Its width is resizable and persisted (`localSetupDrawerWidth`).
- **Open state**: The drawer writes `udmi_testbed_drawer_open` to `localStorage` but does not read it back, so it always starts closed.

#### 3.2.2. Topology Graph
- **Device Under Test (DUT)**: the device selected in the Sequencer (`deviceId`).
  - **Mode switch**: **Physical Device** (default) or **Pubber** (local `bin/pubber`).
  - Physical mode shows `NOT MONITORED`. Pubber mode shows the pubber component status (`UP` / `DOWN`).
- **Local MQTT Broker (Mosquitto)** on the drawer spec's port. `UP` when a TCP connection to `localhost:<port>` succeeds, otherwise `DOWN`.
- **Local UDMIS**: `UP` when `var/pod_ready.txt` exists AND the UDMIS process is alive. `INITIALIZING` only while a start is in flight. Otherwise `DOWN`.
- **etcd** on spec port + 1: `UP` / `DOWN` by TCP probe.
- **Edges**: DUT → broker (events, state); broker → UDMIS; UDMIS → etcd; UDMIS → broker → DUT (config, commands).

#### 3.2.3. Status Model
- Component statuses from the server are `UP`, `INITIALIZING` (UDMIS only) and `DOWN`. The server never sets `ERROR` on a component.
- `overall` precedence: `INITIALIZING` (start in flight) > `ERROR` (`last_error` set) > `UP` (MQTT and UDMIS up) > `INITIALIZING` (anything partially up) > `DOWN`.
- The client shows `UNKNOWN` when the drawer has no valid spec.

**Probes**:
- **Mosquitto**: non-blocking TCP check of `localhost:<port>`.
- **UDMIS**: sentinel `var/pod_ready.txt` plus a live UDMIS process.
- **etcd**: non-blocking TCP check of spec port + 1.
- **Pubber**: the tracked subprocess, else an anchored command-line match of `bin/pubber`.

#### 3.2.4. Lifecycle Control Actions
- **Local Project Spec** (drawer-owned input, persisted in `localStorage` key `udmi_testbed_project_spec`):
  - Required, no default; Start/Stop/Restart are disabled while it is empty. Must be `//mqtt/localhost:<port>`.
  - Independent of the Sequencer's `projectSpec`; when the Sequencer targets a different `//mqtt/localhost:<port>`, the drawer shows a mismatch warning (it never edits the Sequencer's spec).
  - The topology tag and every port shown come from this spec and its status response.
- **Start Local Setup**:
  - Invokes `POST /api/testbed/start` with `{"site_model": "<active_site_model>", "project_spec": "<drawer spec>"}`.
  - Polls `GET /api/testbed/status?project_spec=<drawer spec>` until `overall === "UP"`; hard stop at 90 s with an explicit error, or on `ERROR` (quoting `last_error`).
  - Only then, in Pubber mode, launches Pubber.
- **Stop Local Setup**: `POST /api/testbed/stop` with `{"project_spec": "<drawer spec>"}`.
- **Restart (Clean Slate)**: `POST /api/testbed/restart`; same readiness wait as Start.
- **Device Mode Toggle**: Physical device is the default; Pubber is opt-in.
  - Pubber mode: `POST /api/testbed/pubber/start` / `stop` with the drawer spec.
  - Physical mode: the device badge reads `NOT MONITORED` (never a fabricated connection state), and a connection card shows the facts from `GET /api/testbed/connection`.
- **Setup Logs**: tabs **Setup**, **UDMIS**, **Mosquitto** and **Pubber**, each fetched with `tail=150`.

---

## 4. Contract 1: MCP Tools & Workbench REST (Deterministic Operations)

* **Protocol**: MCP JSON-RPC 2.0.
* **Workbench endpoints**: `POST /rpc` or `POST /message`. The Workbench has no `GET /sse`.
* **Standalone**: `python -m mantis.mcp_server` serves stdio, or HTTP with `serve`. Only the standalone HTTP server has `GET /sse`, which writes one `endpoint` event and returns.

### 4.1. Supported JSON-RPC Methods
- `initialize`: client handshake.
- `notifications/initialized`: acknowledged with no result (the Workbench replies `{}`).
- `ping`: keep-alive.
- `tools/list`: every registered tool with its JSON schema.
- `tools/call`: runs a tool and returns structured content.

`resources/list` and `resources/read` are not implemented and return `-32601`.

### 4.2. Tool Registry

All 27 tools are registered in `mantis/tools/registry.py` (`get_mcp_tools()`). Argument models live in `mantis/models.py`.

#### A. Environment & Test Setup Lifecycle
* **`ensure_test_setup`**:
  * **Args**: `{"test_id": "lab_run_1", "site_model": "sites/udmi_site_model", "clean": true, "timeout_seconds": 150, "exclude": ["influxdb"], "added": ["validator"], "dut_device_id": "AHU-1", "dut_serial_no": "1234"}`
  * `exclude` and `added` become `!svc` and `++svc` tokens on `bin/udmi start block`.
  * Ports are derived from `test_id` in the range 20000-55000.
  * **Output**: `{"status", "test_id", "session_name", "project_spec", "connection_url", "ports": {"mqtt", "etcd", "influx", "postgres"}, "tls", "credentials", "exclude", "added", "site_model", "run_dir", "windows"}`. `windows` is the real tmux window list: `main`, plus `dut` when `dut_device_id` is set.
* **`terminate_test_setup`**: `{"test_id": "lab_run_1", "clean_workspace": true}`
* **`list_test_setups`**: `{}`. Active sessions, connection URLs, port blocks and windows.
* **`list_test_windows`**: `{"test_id": "lab_run_1"}`
* **`start_session_process`**: `{"test_id", "window", "command"}`
* **`get_test_logs`**: `{"test_id", "window": "main", "lines": 100}`
* **`query_database`**: `{"test_id", "database_type": "influx" | "postgres", "query"}`
* **`publish_mqtt_message`**: `{"test_id", "topic", "payload", "target_spec"?, "site_model"?, "device_id"?}`

#### B. Sequencer Execution & Diagnosis
* **`run_sequencer_test`**:
  * **Args**: `{"test_name": "pointset_publish", "device_id": "AHU-1", "target_spec": "//mqtt/localhost:20000", "site_model": "sites/udmi_site_model", "session_id"?: "..."}`
  * **Output**: `{"status": "LAUNCHED", "session_id", "window", "command"}`
* **`inspect_sequencer_test`**: `{"test_name": "pointset_publish"}`. Returns the `@Feature` bucket, stage, timeout, javadoc, assertions and Java source.
* **`get_test_timeline`**: `{"test_id", "device_id", "run_dir"?}`
* **`compare_test_runs`**: `{"target_run", "baseline_run"?}`
* **`diagnose_test_failure`**: `{"test_id", "device_id", "site_model"?, "run_dir"?}`. Deterministic: it harvests the timeline and evidence and does not call a model.
* **`evaluate_test_stability`**: `{"test_id"?, "site_model"?, "base_dir"?}`
* **`verify_golden_baseline`**: `{"baseline_name": "validator", "test_output_path"?}`
* **`inspect_message_trace`**: `{"run_dir", "message_type"?}`
* **`detect_log_anomalies`**: `{"run_dir"}`
* **`get_udmis_runtime_logs`**: `{"pattern"?, "window_start"?, "window_end"?, "project_spec"?, "log_filter"?, "max_lines": 200}`

#### C. Site Model, Schemas & Source
* **`inspect_site_model`**: `{"site_model": "sites/udmi_site_model", "device_id": "AHU-1"}`. Device metadata, points, cloud IoT config, gateway bindings.
* **`patch_site_model`**: `{"site_model", "device_id", "patch_data": {...}, "dry_run": false}`. Returns a unified diff, the `.bak` path and the patch status.
* **`inspect_udmi_schema`**: `{"schema_name": "pointset", "resolve_refs": true, "sub_path"?: "..."}`
* **`read_udmi_file`**: `{"file_path", "start_line"?, "end_line"?}`
* **`search_codebase`**: `{"query", "path_prefix"?, "file_pattern"?, "max_results": 25, "max_per_file": 3, "is_regex"?}`
* **`locate_udmi_doc`**: `{"topic"}`

#### D. Visualizations
* **`generate_topology_diagram`**: `{"site_model", "focus_device"?, "format": "dot" | "mermaid" | "both"}`
* **`generate_sequence_diagram`**: `{"run_dir", "title"?, "format"?}`. `run_dir` is a test result directory.
* **`render_dot_to_svg`**: `{"dot_content": "digraph G { ... }"}`. Returns an SVG string.

### 4.3. Custom Site Model Roots
Site models may live outside the UDMI root. A directory is used only after the operator approves it in the consent modal. Approved roots are stored server-side in `~/.config/udmi/workbench.json` under `site_roots`, never in the browser.
* **`GET /api/site-roots`**: `{"site_roots": [{"path", "available", "site_model_count"?, "reason"?}], "config_path"}`. The site models themselves come from `GET /api/site-models`.
* **`POST /api/site-roots`** `{"path": "/path/to/sites"}`: returns `{"path", "site_model_count", "site_models"}`.
* **`DELETE /api/site-roots?path=/path/to/sites`**: returns `{"path", "removed": true}`.
* **Site model directory layouts**:
  - **Standard**: `<site_dir>/cloud_iot_config.json` (e.g. `sites/udmi_site_model`).
  - **Subdirectory**: `<site_dir>/udmi/cloud_iot_config.json` (e.g. `ZZ-TEST-SITE/udmi/cloud_iot_config.json`).
  - **Display name**: for the subdirectory layout, the UI and API show the parent directory (`ZZ-TEST-SITE`), never the inner name `udmi`.

### 4.4. Local Testbed REST Endpoints
The **Local Test Setup drawer** manages the local stack through these endpoints.
Every lifecycle and status call names the drawer's own project spec, which must be
`//mqtt/localhost:<port>` with an explicit unprivileged port (1024-65535, not 8883).
All probed ports are derived from that spec per call (MQTT = port, etcd = port+1).
A missing or non-local spec is rejected with HTTP 400.
Start runs in the background: a later `bin/udmi` failure shows only as `overall: "ERROR"`
with `last_error` quoting the tail of `out/testbed_setup.log`. Stop, and the stop phase of
restart, return HTTP 500 when the command fails or the ports stay open.
* **`POST /api/testbed/start`**:
  * **Request**: `{"site_model": "sites/udmi_site_model", "project_spec": "//mqtt/localhost:46432", "clean": false}`
  * **Behavior**: Runs `bin/udmi [clean <spec>;] start <site_model> <spec>` in the background. A non-zero exit sets `last_error`.
  * **Response** (200): `{"status": "INITIALIZING", "site_model", "project_spec", "mqtt_port", "message"}`
* **`POST /api/testbed/stop`**:
  * **Request**: `{"project_spec": "//mqtt/localhost:46432"}`
  * **Behavior**: Stops the tracked Pubber process group, runs `bin/udmi stop <spec>`, then verifies within 10 s that the spec's MQTT and etcd ports closed (the script always exits 0).
  * **Response**: `{"status": "STOPPED", "project_spec", "message", "pubber": {"status": "STOPPED" | "NOT_RUNNING", ...}}`
* **`POST /api/testbed/restart`**:
  * **Request**: `{"site_model": "sites/udmi_site_model", "project_spec": "//mqtt/localhost:46432"}`
  * **Behavior**: Validates the spec, then stop, `clean <spec>`, start.
  * **Response**: as `start`.
* **`GET /api/testbed/status?project_spec=//mqtt/localhost:46432`**:
  * **Behavior**: Non-blocking probes of the spec's ports. `is_starting` and `last_error` apply only when they belong to this spec.
  * **Response**:
    ```json
    {
      "overall": "UP | INITIALIZING | DOWN | ERROR",
      "project_spec": "//mqtt/localhost:46432",
      "started_project_spec": "//mqtt/localhost:46432",
      "site_model": "sites/udmi_site_model",
      "last_error": null,
      "components": {
        "mqtt_broker": {"name": "...", "status": "UP", "port": 46432, "probe": "tcp://localhost:46432"},
        "udmis": {"name": "...", "status": "UP", "sentinel": "var/pod_ready.txt", "sentinel_exists": true, "process_alive": true, "probe": "sentinel + process"},
        "etcd": {"name": "...", "status": "UP", "port": 46433, "probe": "tcp://localhost:46433"},
        "pubber": {"name": "...", "status": "DOWN", "pid": null, "device_id": null, "probe": "..."}
      }
    }
    ```
* **`GET /api/testbed/connection?project_spec=...&site_model=...&device_id=...`**:
  * **Behavior**: For physical-device mode: what an external device needs to reach the local broker, derived from `etc/mosquitto_udmi.conf`, `bin/setup_ca`/`bin/keygen`, `bin/mosquctl_site`/`bin/mosquctl_device`, `bin/pubber` and the site model. Values that cannot be determined are `"unknown"` and listed in `unknowns`.
  * **Response**: `{"broker": {"hosts": [...], "port": ...}, "tls": {...}, "identity": {"client_id": "/r/<registry>/d/<device>", ...}, "topics": {...}, "device_key": {...}, "docs": [...], "unknowns": [...]}`
* **`POST /api/testbed/pubber/start`**:
  * **Request**: `{"site_model": "sites/udmi_site_model", "project_spec": "//mqtt/localhost:46432", "device_id": "AHU-1", "serial_no": "1234"}` (`serial_no` defaults to `"1234"`)
  * **Behavior**: Runs `bin/pubber` in its own process group (session). The drawer only calls this after status reports `overall: "UP"`, and only in Pubber mode (Physical is the default).
  * **Response**: `{"status": "RUNNING", "device_id": "AHU-1", "serial_no": "1234", "pid": 1312500, "message"}`
* **`POST /api/testbed/pubber/stop`**:
  * **Request**: `{"project_spec": "//mqtt/localhost:46432", "device_id": "AHU-1"}`
  * **Behavior**: Signals only the tracked Pubber's process group (SIGTERM, then SIGKILL). While a Pubber is tracked, a `device_id` or spec that does not match it is rejected (400).
  * **Response**: `{"status": "STOPPED", "device_id", "pid", "message"}` or `{"status": "NOT_RUNNING", "message": "No Workbench-launched Pubber is tracked; nothing was stopped"}`
* **`GET /api/testbed/logs?component=setup|mosquitto|udmis|pubber&tail=100`**:
  * `tail` defaults to 100 and is clamped to 10-1000. `udmis` and `mosquitto` fall back to the setup log when `out/udmis.log` or `out/mosquitto.log` is missing.
  * **Response**: `{"component", "log_path", "logs"}`

### 4.5. Other Workbench REST Endpoints
| Method & Path | Purpose |
| :--- | :--- |
| `GET /api/health` | Gateway liveness. |
| `GET /api/site-models` | Discovered site models (UDMI root and approved roots). |
| `GET /api/devices?site_model=` / `GET /api/devices/summary?site_model=` / `GET /api/device?site_model=&device_id=` | Devices, with a summary form for the pickers. |
| `GET /api/sequences` | Sequencer test catalog. |
| `GET /api/results?site_model=&device_id=` | Previous results for the test list. |
| `GET /api/browse`, `GET /api/file`, `GET /api/repo-doc` | Artifact and doc viewers. |
| `GET /api/sequencer/options`, `GET /api/sequencer/sessions` | Run options and active runs. |
| `POST /api/sequencer/run` | Start a run (§6). |
| `POST /api/sequencer/stop` `{session_id}` | Stop a run. |
| `GET /api/results/commit/preview`, `POST /api/results/commit` `{site_model, message, branch, create_branch, push, remote}` | Commit results to the site model repo. |
| `POST /api/support-bundle` `{site_model}`, `GET /api/support-bundle/download?bundle_id=` | Build and download a gzip tarball support bundle. |
| `GET /api/diagnostics/logs?limit=` | Server diagnostic log for the Logs drawer. |

---

## 5. Contract 2: Streaming AI Agent Contract (Mantis Cognitive Loop)

* **Protocol**: Server-Sent Events from `POST /api/mantis/chat`.
* **Purpose**: Multi-step investigations with streamed reasoning phases, tool calls and a hypothesis audit.

### 5.1. Execution Modes

Mode selection happens in `mantis_adapter.py`.

#### Mode A: Targeted Failure Triage
* **Triggered when** the message starts with `diagnose`, `triage` or `run triage`, OR matches `why did|how did … fail` and also contains `test` or `sequence`. The request context must include `device_id` and `test_id`; otherwise the stream ends with an `error` event.
* **Sources**: **Diagnose with Mantis** on a failed test row or in the Artifact Viewer, or the failed-test selector in the Mantis drawer.
* **Behavior**: The triage prompt tells the agent to call `diagnose_test_failure` first, then investigate with the other tools. The agent generates its own competing hypotheses and the Critic/Arbitrator audit them.

#### Mode B: General Exploration & Specification
* **Triggered by** any other message, e.g. *"What is the state field configAcked and how does it relate to config synchronization?"* or **Explain this test**.
* **Behavior**: The agent acts as a domain expert: schemas (`inspect_udmi_schema`), docs (`locate_udmi_doc`), code (`search_codebase`, `read_udmi_file`), sequencer tests (`inspect_sequencer_test`). Informational questions skip hypothesis ranking.

### 5.2. Request

The adapter reads only `session_id` (default `"sess-default"`), `message`, `notify`, and `context.{site_model, device_id, test_id}`. The Workbench UI sends `session_id: "workbench-assistant"`. The model provider is chosen by the server environment, not per request (§9).

#### Example 1: Targeted Failure Triage Request
```http
POST /api/mantis/chat HTTP/1.1
Content-Type: application/json
Accept: text/event-stream

{
  "session_id": "workbench-assistant",
  "message": "Why did test pointset_publish fail for AHU-1?",
  "context": {
    "site_model": "sites/udmi_site_model",
    "device_id": "AHU-1",
    "test_id": "pointset_publish"
  },
  "notify": false
}
```

#### Example 2: General Exploration Request
```http
POST /api/mantis/chat HTTP/1.1
Content-Type: application/json
Accept: text/event-stream

{
  "session_id": "workbench-assistant",
  "message": "What is the state field configAcked and how does it relate to config synchronization?",
  "context": {
    "site_model": "sites/udmi_site_model"
  }
}
```

### 5.3. Canonical Event Wire Schema

| Event | Data | Notes |
| :--- | :--- | :--- |
| `phase` | `{"phase": "SCOPING" \| "ACTOR" \| "CRITIC" \| "ARBITRATOR"}` | Phase transitions. |
| `thought` | `{"text"}` | Interim prose from the agent. |
| `tool_call` | `{"call_id", "tool", "args", "timestamp"}` | |
| `tool_result` | `{"call_id", "tool", "summary", "output"}` | `summary` is the record's status, or `"completed"`. It is not prose. |
| `token` | `{"text"}` | Sent **once** with the whole final answer (also once for a slash command). Diagrams are ` ```mermaid ` blocks inside it, rendered by the client. |
| `hypothesis_matrix` | `{"hypotheses": [...], "final": bool, "audit_markdown"?}` | Interim matrices have rows with verdict `UNRESOLVED`, rationale `"Under investigation."`, evidence_tier `"NONE"`. The final matrix is sent after the `token` and carries `audit_markdown`; its rows may have null `rationale` or `evidence_tier`. |
| `done` | `{"session_id", "metrics": {"steps", "tool_calls", "tripartite_status", "agent_duration_sec", "total_duration_sec"}}` | A slash command's `done` has `metrics: {}`. |
| `error` | `{"message"}` | Validation failure, busy session, notify refusal, offline provider, missing triage context, operator stop, agent failure, empty answer, or the 600 s view timeout. Ends the stream. |

Hypothesis rows are `{"hypothesis", "verdict": "PRIMARY" | "CONTRIBUTING" | "REFUTED" | "UNRESOLVED", "rationale", "evidence_tier": "LOCAL_FILE" | "CLOUD" | "NONE"}`.

```
event: phase
data: {"phase": "ACTOR"}

event: tool_call
data: {"call_id": "call-1", "tool": "get_test_timeline", "args": {"test_id": "pointset_publish", "device_id": "AHU-1"}, "timestamp": "2026-09-15T15:00:00Z"}

event: tool_result
data: {"call_id": "call-1", "tool": "get_test_timeline", "summary": "completed", "output": {...}}

event: phase
data: {"phase": "CRITIC"}

event: phase
data: {"phase": "ARBITRATOR"}

event: token
data: {"text": "The test failed because ...\n\n```mermaid\nsequenceDiagram\n...\n```"}

event: hypothesis_matrix
data: {"final": true, "audit_markdown": "...", "hypotheses": [{"hypothesis": "State update arrived after the cutoff", "verdict": "PRIMARY", "rationale": "...", "evidence_tier": "LOCAL_FILE"}]}

event: done
data: {"session_id": "workbench-assistant", "metrics": {"steps": 9, "tool_calls": 7, "tripartite_status": "SUCCESS", "agent_duration_sec": 41.2, "total_duration_sec": 41.9}}
```

### 5.4. Session Control Endpoints
* **`POST /api/mantis/chat/stop`**: Stops the session's running agent: `{"session_id": "string"}` (default `"workbench-assistant"`). Returns `{"session_id", "status": "STOPPING" | "NOT_RUNNING", "message"}`. The agent observes the stop at its next step boundary (before a ReAct step, a tool call, forced synthesis, the Critic, or the Arbitrator); a model call or tool already in progress finishes first. The open stream ends with an `error` event naming the boundary (the Workbench UI aborts its own stream after stopping, so it does not show that event), and `[Run stopped by operator: ...]` is recorded in the session history in place of an answer. A new message on the same session is refused with an `error` event until the stopped worker has exited. Closing the SSE connection stops the agent the same way, except for a run sent with `notify: true` (§5.4.1).
* **`POST /api/mantis/chat/clear`**: Stops any running agent, then resets conversation turns while preserving environment state: `{"session_id": "string"}`. Returns `{"session_id", "status": "CLEARED"}`.

### 5.4.1. Email Notifications ("Email me when done")
* **Consent** is given once in the Settings dialog (header gear icon) and stored under `notifications.email` in `~/.config/udmi/workbench.json` (`$XDG_CONFIG_HOME/udmi/workbench.json`), beside `site_roots`. It is bound to the address of the operator's own Application Default Credentials, which must carry the `gmail.send` scope. If the credentials later belong to another account, delivery is refused until consent is given again. The recipient is always that same address.
* **`GET /api/notifications`**: `{"email": {"consented", "consented_address", "consented_at", "address", "scope_ok", "ready", "problem", "setup_command"}, "deliveries": [{"id", "kind": "mantis" | "sequencer" | "test", "subject", "status": "SENDING" | "SENT" | "FAILED", "error", "message_id", "address", "at"}]}`. Deliveries cover the last 20 attempts since the server started, newest first. A message that fails to compose is recorded as `FAILED` with `subject: null`.
* **`POST /api/notifications/consent`**: `{"consent": true | false}`. Giving consent returns 400 when the credentials cannot send. Withdrawing needs no credentials, but returns 400 if `workbench.json` cannot be read or rewritten.
* **`POST /api/notifications/test`**: Sends a test email synchronously. Returns the delivery record, or 400 with the reason.
* **Per-run opt-in**: `POST /api/mantis/chat` and `POST /api/sequencer/run` accept `"notify": true | false` (default `false`). Both toggles are off by default in the UI and reset after each send or run. A request is refused up front (an SSE `error` event for Mantis, HTTP 400 for the sequencer) when `notify` is not a boolean, and a `notify: true` request is refused when email is not ready, and for slash commands.
* **Behaviour of a notify run**: closing the tab does not cancel it. A Mantis run keeps working past the view's 600 s window and emails the answer, with each Mermaid diagram as an inline PNG (a diagram that cannot be rendered is shown as an error plus its source), the hypothesis audit, or the error that ended it. A sequencer run emails its verdict (computed the same way as the run badge), pass/fail/skip counts, every test that did not pass with its message, selected tests that never reported, and the last 40 log lines when the verdict is Error or Incomplete. The run's `results.md` is attached when it was written after the run started; otherwise the email says it is missing. An operator Stop sends nothing.

### 5.4.2. Desktop Notifications
* Browser-only (`static/js/core/desktop-notify.js`, Notification API); no server endpoint.
* Raised for **every** finished sequencer run (title `Sequencer run <verdict>`, body with the device and counts) and every finished Mantis turn (`Mantis has answered` or `Mantis stopped with an error`, body with the question), independent of the email toggle.
* Shown only when the tab is not in front (`document.hidden` or no focus), and only with permission `granted`. Clicking focuses the tab.
* Permission is requested once, from the Run, Send or Diagnose click. `denied`, or a browser without the Notification API, shows a persistent app-bar notice with how to re-enable; the operator is never re-prompted.

### 5.5. Slash Commands (In Chat Prompt)
Every message starting with `/` is handled as a slash command without running the agent.
- `/status`: `{session_id, site_model, device_id, test_id, turns, tools_available}`.
- `/logs [window]`: the last 80 lines of a tmux window (default `main`) in the tmux session named after the chat session id.
- `/clear`: clears conversation history while preserving environment state.
- `/help`: command syntax.

Any other slash command returns "Unknown command". The Mantis drawer has a **Copy transcript** button for exporting a conversation.

### 5.6. Layer 0 Canonical Pydantic Models (`mantis/models.py`)

```python
class ClaimStatus(str, Enum):
    CONFIRMED = "CONFIRMED"
    REFUTED = "REFUTED"
    UNVERIFIED_ASSUMPTION = "UNVERIFIED ASSUMPTION"
    NOT_ASSESSED = "NOT ASSESSED"

class MessageRole(str, Enum):
    USER = "user"
    ASSISTANT = "assistant"
    SYSTEM = "system"
    TOOL = "tool"

class ChatMessage(BaseModel):
    role: MessageRole
    content: str
    timestamp: str  # ISO-8601
    tool_name: Optional[str] = None
    tool_call_id: Optional[str] = None

class ExecutionMetrics(BaseModel):
    total_steps: int = 0
    total_duration_sec: float = 0.0
    api_calls_count: int = 0
    prompt_tokens: int = 0
    candidates_tokens: int = 0
    total_tokens: int = 0
    tool_calls: Dict[str, int] = Field(default_factory=dict)
    retry_count: int = 0
    tripartite_degraded: bool = False
    tripartite_status: str = "SUCCESS"

class SessionContext(BaseModel):
    active_site_model: str = "sites/udmi_site_model"
    active_session_id: Optional[str] = None
    active_device_id: Optional[str] = None
    active_test_id: Optional[str] = None
    history: List[ChatMessage] = Field(default_factory=list)
    metrics: Optional[ExecutionMetrics] = None

class VerificationClaim(BaseModel):
    claim: str
    status: ClaimStatus
    evidence: str

class HypothesisEvaluation(BaseModel):
    status: ClaimStatus
    evidence: str

class DiagnosticResult(BaseModel):
    status: str = "SUCCESS"
    test_id: str
    device_id: str
    site_model: str
    root_cause: str
    evidence: List[str] = Field(default_factory=list)
    fix: List[str] = Field(default_factory=list)
    verification_matrix: List[VerificationClaim] = Field(default_factory=list)
    competing_hypotheses: Dict[str, HypothesisEvaluation] = Field(default_factory=dict)
    timeline: Optional[Dict[str, Any]] = None
    report: str = ""
    error: Optional[str] = None
```

### 5.7. Native Python API

The Workbench calls the agent in-process from a worker thread (`mantis_adapter.py`):

```python
import threading
from mantis.agent import MantisAgent
from mantis.models import SessionContext

agent = MantisAgent()
context = SessionContext(active_site_model="sites/udmi_site_model")
cancel_event = threading.Event()  # set() to stop at the next step boundary

answer: str = agent.run(
    "How does the test scan_single_future work?",
    context,
    stream_callback=lambda text: print(text, end=""),  # interim prose
    event_callback=lambda record: None,                 # phases, tool calls, hypotheses, metrics
    cancel_event=cancel_event,
)
```

`agent.run_tripartite(prompt, context, stream_callback)` returns a dict `{"status", "prompt", "final_answer", "metrics"}`.

---

## 6. Contract 3: Sequencer Run & Log Streaming

* **Start**: `POST /api/sequencer/run` `{site_model, device_id, project_spec, tests: [...], min_stage: "PREVIEW", log_level: "INFO", serial_no, notify}` returns `{session_id, pid, command, command_line, started_at, log_path, site_model, notify, stages_admitted}`.
* **Stream**: `GET /api/sequencer/stream?session_id=<id>&offset=<bytes>` (SSE only). Reconnect with the last `offset` to resume.
* **Stop**: `POST /api/sequencer/stop` `{session_id}`.

### 6.1. Stream Wire Format
| Event | Data |
| :--- | :--- |
| `log` | `{"offset", "text"}` |
| `test_event` | `{"type": "started", "test"}` or `{"type": "result", "test", "variant", "result", "bucket", "stage", "score", "message"}` |
| `heartbeat` | `{"offset"}` |
| `complete` | `{"session_id", "exit_code", "stopped", "offset"}` |
| `error` | `{"message", "offset"}` (for example after the 1800 s idle timeout) |

```
GET /api/sequencer/stream?session_id=seq-1&offset=0 HTTP/1.1
Accept: text/event-stream

event: log
data: {"offset": 118, "text": "Starting sequence pointset_publish on AHU-1...\n"}

event: test_event
data: {"type": "result", "test": "scan_single_future", "variant": "scan_single_future+bacnet", "result": "pass", "bucket": "discovery.scan", "stage": "...", "score": "...", "message": "..."}

event: complete
data: {"session_id": "seq-1", "exit_code": 0, "stopped": false, "offset": 20480}
```

---

## 7. Contract 4: UI State & Workspace Contract (Client-Side State Store)

* **Implementation**: `WorkspaceStore` in `static/js/core/store.js`, a small class with `subscribe` / `update`.
* **Persistence**: `localStorage` key `udmi_workbench_state_v3`, for these keys only: `siteModel`, `deviceId`, `projectSpec`, `logLevel`, `minStage`, `serialNo`, `selectedTests`, `bucketFilter`, `logPanelHeight`, `localSetupDrawerWidth`, `mantisDrawerWidth`.
* **URL mirroring**: `siteModel` → `?site_model=`, `deviceId` → `?device=`.

### 7.1. State Shape
```typescript
export interface WorkspaceState {
  // Selections (persisted)
  siteModel: string;
  deviceId: string;
  projectSpec: string;               // Sequencer target, e.g. '//mqtt/localhost:18833' or '//gbos/...'
  logLevel: 'INFO' | 'DEBUG' | 'TRACE';
  minStage: 'PREVIEW' | 'ALPHA' | 'ALPHA_ONLY'; // default 'PREVIEW'
  serialNo: string;
  selectedTests: string[];
  bucketFilter: string;
  searchQuery: string;

  // Layout (persisted)
  logPanelHeight: number;
  localSetupDrawerWidth: number;
  mantisDrawerWidth: number;

  // Catalog & results
  siteModels: object[];
  devices: object[];
  sequences: object[];
  results: Record<string, object>;
  resultsDirExists: boolean;
  stagesAdmitted: string[];

  // Current run
  sessionId: string | null;
  running: boolean;
  startedAt: string | null;
  elapsedDuration: string;
  exitCode: number | null;
  statusLabel: string;
  testStatus: Record<string, object>;
  commandLine: string;
  activeTestId: string;
  consoleLogs: object[];

  // Local Test Setup drawer
  testbedDrawerOpen: boolean;        // declared, not updated
  testbedStatus: object;             // raw GET /api/testbed/status payload (snake_case), plus client spec_error / UNKNOWN
  pubberMode: boolean;
}
```

### 7.2. View Preservation
1. `/sequencer` and `/devices` are each created on first visit and kept mounted.
2. Switching routes toggles the `hidden` attribute on the panes; DOM nodes are not destroyed.
3. The run log stream, the Mantis stream (in its drawer), filters and scroll position survive route changes and drawer toggles.

---

## 8. Contract 5: Compliance & Reporting

### 8.1. Compliance Data (`GET /api/compliance?site_model=`)
```json
{
  "site_model": "sites/udmi_site_model",
  "devices": [
    {
      "device_id": "AHU-1",
      "has_results": true,
      "reason": null,
      "last_run": "...",
      "start_time": "...",
      "udmi_version": "...",
      "status_message": "...",
      "counts": {},
      "features": {},
      "stages": {},
      "verdict": "pass | fail | not_evaluated",
      "sequences": [],
      "unscored": [],
      "targets": [],
      "provenance": {},
      "reports": {}
    }
  ],
  "totals": {
    "devices": 1, "devices_with_results": 1,
    "pass": 0, "fail": 0, "skip": 0, "total": 0,
    "devices_passing": 1, "devices_failing": 0, "devices_not_evaluated": 0
  },
  "stages": [],
  "stages_for_pass": []
}
```
By design there is no aggregate score, compliance rate or flakiness index.

### 8.2. Reports & Bundles
- **`GET /api/device/report?site_model=&device_id=&kind=results_md|result_log|sequencer_json`**: one device report file.
- **Support bundle**: `POST /api/support-bundle` `{site_model}` builds a gzip tarball; `GET /api/support-bundle/download?bundle_id=` downloads it. The UI offers it as **Export support bundle** in the run summary and the Artifact Viewer.

---

## 9. Frontend UI Components

1. **Mantis drawer & conversation feed**:
   - Renders the final answer's Markdown (`token`), including Mermaid diagrams with a zoom/pan modal.
   - Collapsible **Agent Reasoning** accordion with phases, tool calls and thoughts.
   - **Tool call badges**: tool name, arguments (JSON viewer) and `tool → summary`.
   - **Stop**, **Clear**, **Copy transcript**, the failed-test selector, and **Email me when done**.
2. **Hypothesis Audit Matrix**:
   - Status chips: `PRIMARY`, `CONTRIBUTING`, `REFUTED`, `UNRESOLVED`.
   - Evidence badges: `LOCAL_FILE`, `CLOUD`, `NONE`.
3. **Local Test Setup drawer** (§3.2): Start Setup, Stop, Restart, the Pubber/Physical switch, the topology graph and log tabs.
4. **Sequencer filter toolbar**: Min stage (`PREVIEW`, `ALPHA`, `ALPHA_ONLY`), feature bucket, **Select all**, **Clear**, the selection buttons **✓ Passed** / **– Skipped** / **✗ Failed** (select tests by previous result), search, and **Email me when done**.
5. **Site Roots consent modal**: register, list and revoke external site model directories (§4.3).
6. **Artifact Viewer modal**: `RESULT.log`, `sequence.md`, `sequence.png` and other artifacts, with **Diagnose with Mantis** and **Export support bundle**.
7. **Settings dialog**: email notification consent, a test email, and recent deliveries only. The model provider is chosen when the gateway starts: the one-time `bin/mantis setup --vertex=<project>[/<region>]` (saved under `mantis` in `~/.config/udmi/workbench.json`; the gateway refuses to start if the environment contradicts it), otherwise `MANTIS_OFFLINE`, then `GEMINI_API_KEY` / `GOOGLE_API_KEY` (AI Studio), then Vertex AI via ADC.
8. **Logs drawer**: client diagnostic ring buffer and the server diagnostic log.

---

## 10. Not Yet Implemented

These are part of the Workbench vision and are not built:
- A certified device catalog (`/catalog`) and curator approval flow.
- A certification package (spec, report, signed token, traces, diagrams) and a flakiness index.
- Ancillary Test Node (ATN) writeback control.
- A live tmux terminal in the UI.
- MCP resources (`resources/list`, `resources/read`).
