# GUMMI Backend Server Technical Architecture (`SERVER.md`)

## Overview

This document defines the backend server architecture for **GUMMI (Glorious Unified Methodical Management Interface)**.

GUMMI operates as the management-plane server bridging the browser user interface ([`gummi/GUMMI.md`](file:///home/peringknife/udmi/gummi/GUMMI.md)) with the UDMI infrastructure, coordinating data access through the **Butler** ingestion subsystem and messaging through the **UUFI** channel.

---

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Web Browser (Vanilla JS)                         │
└───────────────────▲─────────────────────────────────┬───────────────────┘
                    │                                 │
     SSE Events /   │                                 │ REST API Requests
     Live Stream    │                                 │ & State Mutations
                    │                                 ▼
┌───────────────────┴─────────────────────────────────────────────────────┐
│                      GUMMI Web & Presentation Server                    │
│  ┌─────────────────────────────────┐   ┌─────────────────────────────┐  │
│  │       REST API Services         │   │       SSE Broadcaster       │  │
│  │ (Auth / Validation / Dispatch)  │   │  (Alerts / State / Rollout) │  │
│  └────────────────┬────────────────┘   └──────────────▲──────────────┘  │
│                   │                                   │                 │
│                   ▼                                   │                 │
│  ┌────────────────────────────────────────────────────┴──────────────┐  │
│  │                 MCP Client Abstraction Layer                      │  │
│  │  - ButlerClient (relational & timeseries datastores)               │  │
│  │  - BarbicanClient (device catalog & etcd properties)              │  │
│  │  - UUFIClient (messaging fabric & live event streaming)           │  │
│  └────────┬───────────────────────┬───────────────────▲──────────────┘  │
└───────────┼───────────────────────┼───────────────────┼─────────────────┘
            │ JSON-RPC              │ JSON-RPC          │ JSON-RPC / SSE
            ▼                       ▼                   ▼
┌───────────────────────┐ ┌───────────────────┐ ┌─────────────────────────┐
│      Butler MCP       │ │   Barbican MCP    │ │        UUFI MCP         │
│ (:8088 /rpc)          │ │ (:8085 /rpc)      │ │ (:8087 /rpc)            │
│ Postgres / InfluxDB   │ │ Device Catalog    │ │ MQTT Broker Fabric      │
└───────────────────────┘ └───────────────────┘ └─────────────────────────┘
```

### 1.1 Concurrency & Execution Model
The backend process consists of two primary operational tiers:
1. **Web Serving Tier**: Serves static assets, processes REST API queries, validates schema payloads, and delegates 100% of data reads and configuration mutations to backend MCP servers via standard JSON-RPC 2.0 clients.
2. **UUFI Management Tier (Background Worker)**:
   - Polls inbound device events from the UUFI MCP server.
   - Evaluates rollout convergence across target devices.
   - Pushes real-time updates to active Server-Sent Events (SSE) subscribers.

---

## 2. Subsystem Integrations

### 2.1 Relational & Timeseries Data (Butler MCP)
GUMMI delegates all telemetry queries, device state aggregation, inventory lookups, validation alerts, and message lifecycle storage to the **Butler MCP Server** (`:8088`). GUMMI never connects directly to PostgreSQL or InfluxDB; all data operations flow through `ButlerClient`.

> Specification Reference: See [**`mcp/butler/`**](file:///home/peringknife/udmi/mcp/butler/) and [**`butler/README.md`**](file:///home/peringknife/udmi/butler/README.md).

### 2.2 Device Catalog & Properties (Barbican MCP)
GUMMI interacts with device registries and etcd device hierarchy data via the **Barbican MCP Server** (`:8085`), utilizing `BarbicanClient` without opening raw socket connections or direct etcd database sessions.

> Specification Reference: See [**`mcp/barbican/`**](file:///home/peringknife/udmi/mcp/barbican/).

### 2.3 Messaging Bus & Control Plane (UUFI MCP)
GUMMI delegates configuration dispatch, live state queries, and event streaming to the **UUFI MCP Server** (`:8087`) via `UUFIClient`, removing any direct dependency on paho-mqtt or broker socket management.

> Specification Reference: See [**`mcp/uufi/`**](file:///home/peringknife/udmi/mcp/uufi/) and [**`docs/specs/uufi.md`**](file:///home/peringknife/udmi/docs/specs/uufi.md).

---

## 3. Core Functional Modules

### 3.1 Fleet Inventory & Search Engine
* Server-side filtering (by registry, device prefix, hardware make/model, software versions, and status).
* Server-side pagination ($O(1)$ limit/offset queries) for large-scale device datasets.
* Dynamic device liveness calculation based on activity and error severities.

### 3.2 Telemetry & Time-Series Visualization
* Serves downsampled time-series data for UI charting and device historical analysis.

### 3.3 Declarative Configuration & Staged Rollouts
* Validates user-submitted payloads against authoritative schemas in `schema/`.
* Dispatches configuration mutations across the messaging bus.
* Groups target devices into batches, monitoring reported state to verify per-device convergence before advancing rollout stages.

### 3.4 Bridgehead & Infrastructure Health Administration
* Dynamically detects environment capabilities (local bridgehead vs. cloud deployment).
* In local environments, exposes health checks for local infrastructure components.

### 3.5 Real-Time Event Streaming (SSE)
* Provides a Server-Sent Events endpoint (`/api/stream/events`) to broadcast live device updates, alerts, and rollout progress to the browser.

---

## 4. API Surface Overview

| Endpoint Group | Base Path | Description |
| :--- | :--- | :--- |
| **System & Status** | `/api/system/*`, `/api/bridgehead/*` | Environment capabilities, authentication identity, and component health. |
| **Portfolio** | `/api/portfolio/*` | Fleet-wide aggregate metrics, status counters, and prioritized alert feeds. |
| **Devices** | `/api/devices/*` | Paginated device queries, single-device metadata, state snapshots, and telemetry history. |
| **Configuration** | `/api/devices/{registry}/{device}/config` | Schema validation and target configuration dispatch. |
| **Rollouts** | `/api/rollouts/*` | Creating, inspecting, pausing, and cancelling staged configuration rollout campaigns. |
| **Streaming** | `/api/stream/events` | Server-Sent Events stream for push updates. |

---

## 5. Testing & Verification Strategy

Testing verifies end-to-end system behavior across backend logic, database queries, message bus interactions, and the browser UI.

```
                  ┌─────────────────────────────────┐
                  │    E2E UI Tests (Playwright)    │
                  │  - Browser navigation & routing │
                  │  - DOM rendering & data grids   │
                  │  - Live SSE stream updates      │
                  └────────────────┬────────────────┘
                                   │
                  ┌────────────────▼────────────────┐
                  │    Integration Tests (Python)   │
                  │  - REST API endpoint contracts  │
                  │  - Butler database queries      │
                  │  - UUFI handshake & dispatch    │
                  └────────────────┬────────────────┘
                                   │
                  ┌────────────────▼────────────────┐
                  │    Unit Tests (Pytest / Mocha)  │
                  │  - Schema validation logic      │
                  │  - Rollout batching algorithms  │
                  │  - In-memory data structures    │
                  └─────────────────────────────────┘
```

### 5.1 End-to-End Browser Testing with Playwright
* **Framework**: Headless browser automation via **Playwright** (`python -m playwright` or `@playwright/test`).
* **Test Scope**:
  * Navigation and state preservation across tabbed views (Portfolio, Devices, Device Detail, Configuration, Rollout).
  * Data density and responsive table rendering with server-side pagination controls.
  * Form inputs, JSON editor validation, and user interaction feedback.
  * Real-time DOM updates triggered by Server-Sent Events.

### 5.2 Infrastructure Orchestration via MCP Server
Hermetic backend environments for both integration and Playwright tests are managed using the **UDMI Test Infrastructure MCP Server** (`bin/test_infra_mcp` / `bin/test_setup`) as defined in [**`docs/specs/uufi.md`**](file:///home/peringknife/udmi/docs/specs/uufi.md#99-agentic-test-infrastructure-management-mcp-server--cli).

* **Lifecycle Management**:
  * `ensure_test_setup`: Dynamically provisions an isolated local stack (broker, databases, UDMIS, Butler) on allocated ports, waiting for full readiness.
  * `terminate_test_setup`: Destroys the test session and cleans up temporary state.
* **Failure Diagnostics**:
  * `get_test_logs`: Captures runtime logs using semantic window tags (e.g., `main`, `dut`) for rapid post-mortem analysis during automated CI and agentic test execution.
