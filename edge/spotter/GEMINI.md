# Spotter Testing & Verification Standards

This document establishes the testing and verification standards for the **Spotter** project. It specifies all automated test scripts, their purposes, execution conditions, and manual triage fallback procedures to ensure system integrity.

---

## 1. Test Suite Overview

All verification tests are categorised into **Unit Tests** and **Integration Tests**. For consistent execution across local developer environments and CI gating pipelines, use the unified test orchestrator [run_spotter_tests](bin/run_spotter_tests):
```bash
# Run unit tests
./edge/spotter/bin/run_spotter_tests unit

# Run integration suites
./edge/spotter/bin/run_spotter_tests integration

# Run all verification tests
./edge/spotter/bin/run_spotter_tests all
```

### 1.1 Automated Unit Tests
- **Target**: Pure logical verification of internal modules (e.g., config mapping, credential parsing, password derivation, PCAP drivers, discovery trace logic) without dependencies on external running processes or Docker.
- **Location**: [tests/](tests)
- **Command**:
  ```bash
  ./edge/spotter/bin/run_spotter_tests unit
  ```

### 1.2 Automated Integration Tests
- **Target**: Functional pipeline, lifecycle boundaries, container isolation, resource contention, and co-existence parity.
- **Location**: [bin/](bin) & [tests/](tests)
- **Executables**:
  - [test_container](bin/test_container): Validates container lifecycle isolation, volume mounting of configuration files, clean signal termination, and exit code propagation inside Docker.
  - [test_pcap](bin/test_pcap): Validates remote-triggered PCAP packet capture diagnostics over MQTT streaming and verify binary reassembly via [reassemble_pcap](bin/reassemble_pcap).
  - [test_parity](bin/test_parity): Runs co-existence integration testing against a simulated BACnet device on an isolated network, confirming functional parity with legacy discovery node.
  - [compare_field_parity](bin/compare_field_parity): Automated utility to run sequential discovery parity scans or diff event logs on live field testbeds.
  - [test_resource_contention](bin/test_resource_contention): Validates Spotter process CPU, memory consumption, file descriptor limits, and telemetry latency under concurrent heavy workloads.
  - [test_fault_injection](bin/test_fault_injection): Validates network fault tolerance, streaming MQTT backoff recovery, and socket reconnect logic.
  - [self_test.py](tests/self_test.py): In-container micro-self-test suite executed post-build to verify imports, credentials, raw socket access, and loop sanity.

To get detailed explanations of what each integration test script validates, run them with the `--help` flag:
```bash
./edge/spotter/bin/test_container --help
./edge/spotter/bin/test_pcap --help
./edge/spotter/bin/test_parity --help
./edge/spotter/bin/compare_field_parity --help
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
  ./bin/spotter stop
  ```

### 1.4 UDMI Infrastructure Orchestration & Connection Resilience
Spotter connects to local or cloud-hosted UDMI infrastructure as an edge client node.
- **Local Infrastructure Pairing**: When testing against local services, start the core stack using the canonical orchestrator [bin/udmi](../../bin/udmi):
  ```bash
  # Start Barbican and Butler services
  bin/udmi start sites/udmi_site_model //mqtt/localhost:46432

  # Launch Spotter targeting the local broker
  ./bin/spotter sites/udmi_site_model //mqtt/localhost:46432 AHU-1
  ```
- **Connection Resilience**: Spotter automatically probes broker reachability via TCP socket probing for up to 30 seconds before client attachment, and employs retry backoff during TLS handshake establishment to withstand broker cold starts.

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

### 4.1 Container Signal Propagation & Graceful Shutdown
Stdout must show clean signal trapping, manager shutdown, and disconnection:
```
2026-09-02 07:33:34,917|INFO|agent:handle_signal Signal 15 received. Shutting down Spotter...
2026-09-02 07:33:34,917|INFO|device:stop Stopping device...
2026-09-02 07:33:34,917|INFO|base_manager:stop Stopping manager: SystemManager
2026-09-02 07:33:35,917|INFO|base_manager:stop Stopping manager: LocalnetManager
2026-09-02 07:33:35,917|INFO|base_manager:stop Stopping manager: SpotterDiscoveryManager
2026-09-02 07:33:37,400|INFO|device:on_disconnect Client disconnected cleanly.
2026-09-02 07:33:37,401|INFO|device:stop Device stopped.
```

### 4.2 Crash Propagation
Container exit codes must faithfully reflect internal agent terminations (e.g. exit code 42 propagates directly to Docker runtime, verified by `bin/test_container`).

### 4.3 MQTT Local Connection & mTLS Handshake
Logs must show successful client mTLS handshakes:
```
mqtt_messaging_client:connect Connecting to MQTT broker at 192.168.12.254:18883
mqtt_messaging_client:_on_connect Connected to MQTT broker. Subscribing...
message_dispatcher:_on_connect Dispatcher: Client connected.
device:on_ready Connection successful.
```

---

## 5. Engineering Standards & Parity Verification

To maintain technical integrity and ensure non-regression:
- **Functional Parity**: Spotter must maintain functional parity with legacy discovery nodes for existing protocol sweeps.
- **Differential Verification**: Run `bin/test_parity` against simulated device fixtures before changes are committed.
- **Negative Verification**: Confirm reproduction of any failure signatures before declaring a defect repaired.

---

## 6. Execution Matrix

| Test Executable / Profile | Synthetic Local Testbed | Production Edge Targets | Rationale |
| :--- | :---: | :---: | :--- |
| **`tests/test_agent.py`** | **Yes** | **No** | Unit test suite; runs within local Python venv. |
| **`bin/test_container`** | **Yes** | **No** | Local container build, volume mounts, and signal shutdown. |
| **`bin/test_pcap`** | **Yes** | **No** | PCAP streaming over MQTT with binary reassembly validation. |
| **`bin/test_parity`** | **Yes** | **No** | Differential comparison against simulated BACnet fixtures. |
| **`bin/compare_field_parity`** | **Yes** | **Yes** | Safe sequential discovery parity execution on live field devices. |
| **`bin/test_fault_injection`** | **Yes** | **No** | Induces transport disconnects and asserts backoff reconnects. |
| **`bin/test_resource_contention`** | **Yes** | **Canary Only** | Evaluates CPU, memory ratio, FD stability, and circuit breaker. |
| **`tests/self_test.py`** | **Yes** | **Yes** | Non-destructive <2s in-container verification of imports & permissions. |

---

## 7. Observability & Host Telemetry Standards

### 7.1 Telemetry Delivery
Spotter adheres strictly to the UDMI edge security model:
- **Outbound-Only mTLS Channel**: All telemetry (host metrics, discovery events, PCAP streaming) is published outbound over mTLS MQTT to the cloud broker.
- **Zero Inbound Ports**: To ensure compliance with firewall-restricted OT environments (BMS networks, industrial VLANs), Spotter runs with zero exposed inbound HTTP/scrape ports.

### 7.2 Native UDMI Telemetry Model
Host health metrics and system attributes are collected via host inspection (`/proc/meminfo`, `/proc/loadavg`, `/etc/os-release`) and published natively through `SystemManager`:
- **Dynamic Metrics (`events/system`)**: Periodically published by `SystemManager.publish_metrics()` as `SystemEvents` payloads:
  - `metrics.mem_total_mb`: Total host physical memory in megabytes.
  - `metrics.mem_free_mb`: Available/free host memory in megabytes.
  - `metrics.system_load`: System load average.
- **Static Host State (`state`)**: Published in `system_state`:
  - `system.software.os`: Host OS distribution name (e.g., Debian GNU/Linux 12).
  - `system.software.os_version`: OS release version identifier.
- **Safety Circuit Breaker**: Evaluates memory usage against safety thresholds (`check_safety_circuit_breaker`), throttling operations to protect edge devices from kernel OOM termination.
