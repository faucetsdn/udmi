# Spotter Testing & Verification Standards

This document establishes the testing and verification standards for the **Spotter** project. It specifies all automated test scripts, their purposes, execution conditions, and manual triage fallback procedures to ensure system integrity.

---

## 1. Test Suite Overview

All verification tests are categorised into **Unit Tests** and **Integration Tests**.

### 1.1 Automated Unit Tests
- **Target**: Pure logical verification of internal modules (e.g., config mapping, credential parsing, password derivation) without dependencies on external running processes or Docker.
- **Location**: [tests/](tests)
- **Command**:
  ```bash
  python3 -m unittest discover -s edge/spotter/tests
  ```

### 1.2 Automated Integration Tests
- **Target**: Functional pipeline, lifecycle boundaries, container isolation, resource contention, and co-existence parity.
- **Location**: [bin/](bin) & [tests/](tests)
- **Executables**:
  - [test_supervisor](bin/test_supervisor): Validates the subprocess supervisor's PID tracking, signal propagation (`SIGTERM` / `SIGINT`), and graceful termination handlers.
  - [test_container](bin/test_container): Validates container lifecycle isolation, volume mounting of on-prem configuration files, and supervisor integration inside Docker.
  - [test_parity](bin/test_parity): Runs co-existence integration testing against a simulated BACnet device on a custom docker network, confirming 100% functional telemetry payload parity.
  - [test_resource_contention](bin/test_resource_contention): Validates dual-process CPU, memory cgroups, file descriptor limits, and telemetry heartbeat latency under concurrent heavy workloads.
  - [test_fault_injection](bin/test_fault_injection): Validates network fault tolerance, proxy disconnection fallbacks (HTTP -> MQTT base64 chunking), and socket reconnect logic.
  - [self_test.py](tests/self_test.py): In-container micro-self-test suite executed by supervisor post-OTA staging to verify imports, credentials, raw socket access, and loop sanity.

To get detailed explanations of what each integration test script validates, run them with the `--help` flag:
```bash
./edge/spotter/bin/test_supervisor --help
./edge/spotter/bin/test_container --help
./edge/spotter/bin/test_parity --help
./edge/spotter/bin/test_resource_contention --help
./edge/spotter/bin/test_fault_injection --help
```

### 1.3 Standalone & Container Startup Orchestration
The startup orchestrator [spotter](../../bin/spotter) handles environment setup, builds, and execution.
- Run in standard Docker Container Mode (default config: [spotter_config.json](spotter_config.json)):
  ```bash
  ./bin/spotter --mode container
  ```
- Run as Standalone Local Service (default config: [spotter_config.json](spotter_config.json)):
  ```bash
  ./bin/spotter --mode local
  ```
  *(To use a custom configuration, append `--config /path/to/custom_config.json`)*
- To gracefully stop all background Spotter instances (both Docker containers and local supervised processes):
  ```bash
  ./bin/stop_spotter
  ```

---

## 2. Test Execution Environments & Conditions

Tests must be executed under specific conditions to guarantee environment hygiene and avoid false positives/negatives:

### 2.1 Network & Port Requirements
- **BACnet Port 47808 Isolation**: BACnet discovery broadcasts to UDP port `47808`. If running tests locally, no other application (e.g., a bare-metal BACnet scanner or another running container) must be bound to port `47808` on the host.
- **Custom Docker Bridge Subnet**: Parity tests utilize an isolated docker bridge network named `spotter-parity-net` on subnet `192.168.12.0/24`. The host's gateway is defined as `192.168.12.254`. This subnet must not conflict with any existing network interfaces on the host.

### 2.2 Security & Certificates (mTLS)
- The containerized local tests use the pre-generated CA from `sites/udmi_site_model/reflector/ca.crt` to authenticate clients. The local Mosquitto broker MUST run with a TLS listener configured on port `18883` validating client certificates.

### 2.3 State Isolation & Sanitization
Before executing a new test run, ensure that all residual state from prior runs is purged:
```bash
# Stop any orphaned testing containers
docker stop parity-legacy-node parity-spotter-node parity-bacnet-device 2>/dev/null

# Remove the custom testing network
docker network rm spotter-parity-net 2>/dev/null
```

---

## 3. Reversion Testing Policy (Negative Verification)

To ensure any bug fix is the direct cause of the resolution and not an artifact of environment contamination:
1. Confirm the test fails under the original broken codebase (reproduce the internal failure signature).
2. Apply the fix and confirm the test passes successfully.
3. **Revert the change temporarily** and re-run the test.
4. **Hard Stop Constraint**: If the test passes after the fix is reverted, the environment is contaminated. Declare a sanitization failure, clean the state, and restart triage.

---

## 4. Log-Based Evidence Verification

When reviewing test results (automated stdout or manual container logs), verify these key transition signatures:

### 4.1 Supervisor Signal Propagation
Stdout must show successful process signaling and termination transitions:
```
Supervisor: Received shutdown signal, terminating child processes...
Supervisor: Sending SIGTERM to legacy node (PID: <pid>)
Supervisor: Sending SIGTERM to Spotter agent (PID: <pid>)
Supervisor: All child processes terminated. Exiting.
```

### 4.2 Crash Handlers
Stdout must show exit code mappings upon internal process failure:
- Legacy node crash: `Supervisor: Fatal crash detected on legacy node. Restarting container.` (Container exits with status `101`).
- Spotter agent crash: `Supervisor: Fatal crash detected on Spotter agent. Restarting container.` (Container exits with status `102`).

### 4.3 MQTT Local Connection
Logs must show successful client mTLS handshakes:
```
mqtt:on_pre_connect client ID is /r/ZZ-TRI-FECTA/d/AHU-1
mqtt:on_connect on_connect Success
```

---

## 5. Development Plan Maintenance Policy

To maintain project tracking visibility and alignment on behavioral specifications during development:
- **Mandate**: Every agent developing the Spotter codebase must update the detailed implementation plan ([spotter_plan.md](spotter_plan.md)) as they proceed.
- **Completeness**: When a task or sub-phase is successfully implemented and verified, the agent must mark it as `[Completed]` in the plan.
- **Traceability**: The agent must document the precise files modified/created and the specific verification methods/results directly under the task in the plan.
- **Preservation**: The original behavioral specifications for each task must not be removed or truncated; implementation and testing details should be appended underneath them.

---

## 6. Production Release Cycle & Execution Matrix

To move towards a regular, methodological release cycle while protecting live OT infrastructure, tests are categorized by target execution safety:

### 6.1 Target Execution Matrix
| Test Executable / Profile | Synthetic Local Testbed | Production Edge Targets | Rationale |
| :--- | :---: | :---: | :--- |
| **`tests/test_agent.py`** | **Yes** | **No** | Unit test suite; requires local test runner environment. |
| **`bin/test_supervisor`** | **Yes** | **No** | Destructive process signaling and `sys.exit` crash handlers. |
| **`bin/test_container`** | **Yes** | **No** | Local container build and volume mount lifecycle checks. |
| **`bin/test_parity`** | **Yes** | **No** | Uses custom bridge network and mock local Mosquitto instances. |
| **`bin/test_fault_injection`** | **Yes** | **No** | Induces artificial proxy drops and latency (`tc/netem`). |
| **`bin/test_resource_contention`** | **Yes** | **Canary Only** | Full local stress run; production runs in non-destructive canary mode. |
| **`tests/self_test.py`** | **Yes** | **Yes** | Non-destructive <10s in-container post-OTA staging validation. |
| **`system.diagnostics.resource_audit`** | **Yes** | **Yes** | Non-destructive on-device cgroup & telemetry health probe. |

### 6.2 3-Tier Release Pipeline
1. **Tier 1 (Local Pre-Submit Gate)**: Stage 1/2 unit & schema tests plus Stage 3 local integration (`test_container`, `test_resource_contention`).
2. **Tier 2 (In-Container Staging Rollback)**: OTA wheel packages deployed to `/opt/spotter/staging/venv`. Supervisor executes `self_test.py`. Any non-zero exit code triggers instant rollback without promoting the active symlink.
3. **Tier 3 (Progressive Production Canary)**: Rollout progresses in stages (1% -> 10% -> 100%). Automated cloud monitoring inspects telemetry latency, cgroup metrics, and heartbeat rates, auto-triggering rollbacks upon anomaly detection.

---

## 7. Observability Standards & Metrics Telemetry Specification

### 7.1 Telemetry Delivery Channels
Spotter supports three distinct metric delivery mechanisms depending on deployment architecture:
1. **Prometheus / OpenTelemetry (Open-Source)**: HTTP `/metrics` scrape endpoint exposed on container internal port `9090` (or push via OpenTelemetry OTLP exporter).
2. **Google Murdock Daemon (`murdockd`) Integration**: Internal Unix domain socket interface (`/healthz` liveness & metrics stream) piping edge telemetry to Monarch and Google Cloud Monitoring.
3. **Native UDMI MQTT Channel (`events/metrics`)**: Periodic JSON metric events published over mTLS MQTT for firewall-restricted OT environments where inbound HTTP ports cannot be exposed.

### 7.2 Metric Catalog
| Metric Identifier | Metric Type | Labels | Description |
| :--- | :---: | :--- | :--- |
| `spotter_cpu_usage_ratio` | Gauge | `process="spotter\|legacy"` | CPU utilization ratio vs allocated quota. |
| `spotter_memory_bytes` | Gauge | `type="rss\|cgroup_limit"` | Memory consumption vs cgroup memory bounds. |
| `spotter_open_fds` | Gauge | — | Count of open file descriptors (`ulimit -n`). |
| `spotter_pcap_packets_total` | Counter | `status="captured\|dropped"` | Count of network packets captured in PCAP driver. |
| `spotter_pcap_bytes_transferred_total` | Counter | `transport="gcs\|mqtt"` | Diagnostic stream volume uploaded to cloud. |
| `spotter_pcap_upload_duration_seconds` | Histogram | — | Latency bucket distributions for GCS/MQTT uploads. |
| `spotter_ota_events_total` | Counter | `result="success\|rollback"` | Outcome counters for staged OTA packages. |
| `spotter_mqtt_connection_status` | Gauge | — | Connectivity indicator (`1`=connected, `0`=disconnected). |

### 7.3 Distributed Tracing & Logging Standards
- **W3C OpenTelemetry Trace Context**: Spotter injects `traceparent` context headers into MQTT events and GCS upload HTTP headers to correlate edge packet captures with cloud reassembly pipelines.
- **Single-Line Structured JSON Logs**: stdout/stderr logs are formatted as single-line JSON (`timestamp`, `severity`, `component`, `trace_id`, `message`) for parsing by Cloud Logging or Vector.
