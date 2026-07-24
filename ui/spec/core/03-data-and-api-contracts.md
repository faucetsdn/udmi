# 📡 Backend Data & API Contracts Specification

This document specifies the HTTP REST endpoints, Server-Sent Event (SSE) streams, query parameters, request payloads, and response data formats required by the UDMI Workbench UI backend service.

---

## 1. System & Feature Discovery Endpoints

### 1.1 Security Policy & Allowed Features
- **Endpoint**: `GET /api/features`
- **Description**: Returns the list of tools permitted by the backend server in the current execution environment.
- **Response**: `200 OK`
  ```json
  ["sequencer", "mantis"]
  ```

---

## 2. Workspace & File System Endpoints

### 2.1 Directory Listing (Folder Browser)
- **Endpoint**: `GET /api/list`
- **Query Parameters**:
  - `path` *(string, optional)*: Absolute path, home-relative (`~`), or system root (`/` / `/mnt/c` on WSL) to list. Defaults to repo root or user home directory.
- **Cross-Platform / WSL Resolution Rules**:
  - Paths starting with `/mnt/` (WSL Windows drive mounts) or system root `/` MUST be accessible for directory listing and selection to support Windows Subsystem for Linux users whose site models reside outside `/home`.

---

## 3. Site Model & Telemetry Status Endpoints

### 3.1 Device Discovery
- **Endpoint**: `GET /api/devices`
- **Query Parameters**:
  - `site_model` *(string, required)*: Path to target site model directory.
- **Response**: `200 OK`
  ```json
  {
    "site_model": "sites/udmi_site_model",
    "devices": ["AHU-1", "VAV-201", "Lighting-Ctrl"]
  }
  ```

### 3.2 Device Test Results Matrix
- **Endpoint**: `GET /api/device_results`
- **Query Parameters**:
  - `site_model` *(string, required)*: Site model path.
  - `device` *(string, optional)*: Target device ID.
- **Response**: `200 OK`
  ```json
  {
    "device_id": "AHU-1",
    "tests": {
      "pointset.telemetry": {
        "status": "PASSED",
        "timestamp": "2026-07-22T08:30:00Z",
        "stage": "ALPHA"
      },
      "system.config": {
        "status": "FAILED",
        "timestamp": "2026-07-22T08:31:15Z",
        "stage": "BETA"
      }
    }
  }
  ```

---

## 4. Sequencer Test Execution Endpoints

### 4.1 Launch Sequencer Run
- **Endpoint**: `POST /api/run_sequencer` (or `GET`)
- **Parameters / Payload**:
  - `site_model` *(string, required)*
  - `device_id` *(string, optional)*
  - `project_spec` *(string, optional)*: Formatted target environment string matching `[//provider/]project[/namespace][+user]` (e.g. `//gref/bos-platform-dev/faucetsdn+heykhyati`, `//mqtt/localhost`).
  - `serial_no` *(string, optional)*
  - `log_level` *(string, optional)*: e.g., `INFO`, `DEBUG`
  - `min_stage` *(string, optional)*: e.g., `ALPHA`, `BETA`, `PREVIEW`
- **Response**: `200 OK`
  ```json
  {
    "session_id": "seq-98234-a8f",
    "status": "started"
  }
  ```

### 4.2 Terminate Sequencer Run
- **Endpoint**: `POST /api/stop_sequencer` (or `GET`)
- **Response**: `200 OK`
  ```json
  {
    "status": "stopped"
  }
  ```

### 4.3 Live Console SSE Log Stream
- **Endpoint**: `GET /api/stream`
- **Format**: `text/event-stream`
- **Event Payload**: Server-Sent Events delivering raw log lines continuously.
  ```http
  data: [2026-07-22 08:35:10] INFO Sequencer: Beginning test suite for AHU-1...
  data: [2026-07-22 08:35:12] PASS pointset.telemetry
  ```

---

## 5. Mantis AI Triage & Debugging Endpoints

### 5.1 Launch AI Triage Subprocess
- **Endpoint**: `POST /api/run_triage`
- **Parameters / Payload**:
  - `site_model` *(string, required)*
  - `device_id` *(string, required)*
  - `test_id` *(string, required)*
  - `project_spec` *(string, optional)*: Formatted target environment string matching `[//provider/]project[/namespace][+user]`.
  - `playbook` *(string, optional)*
  - `gemini_api_key` / `use_vertex` *(string, optional)*
- **Response**: `200 OK`
  ```json
  {
    "session_id": "mantis-4812-bc9",
    "status": "started"
  }
  ```

### 5.2 Terminate AI Triage Subprocess
- **Endpoint**: `POST /api/stop_triage`
- **Response**: `200 OK`
  ```json
  {
    "status": "stopped"
  }
  ```

### 5.3 Mantis Live Triage SSE Stream
- **Endpoint**: `GET /api/triage_stream`
- **Format**: `text/event-stream`
- **Event Payload**: Real-time log output from Mantis AI triage execution.

### 5.4 Fetch Generated AI Triage Report
- **Endpoint**: `GET /api/triage_report`
- **Query Parameters**:
  - `site_model` *(string, required)*
  - `device_id` *(string, required)*
  - `test_id` *(string, required)*
- **Response**: `200 OK`
  ```json
  {
    "device_id": "AHU-1",
    "test_id": "system.config",
    "report_markdown": "# Root Cause Analysis Report\n\n## Summary\nDevice failed system.config due to missing field `system.min_power`."
  }
  ```
