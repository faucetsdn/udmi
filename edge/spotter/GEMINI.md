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
- **Target**: Functional pipeline, lifecycle boundaries, container isolation, and co-existence parity.
- **Location**: [bin/](bin)
- **Executables**:
  - [test_supervisor](bin/test_supervisor): Validates the subprocess supervisor's PID tracking, signal propagation (`SIGTERM` / `SIGINT`), and graceful termination handlers.
  - [test_container](bin/test_container): Validates container lifecycle isolation, volume mounting of on-prem configuration files, and supervisor integration inside Docker.
  - [test_parity](bin/test_parity): Runs co-existence integration testing against a simulated BACnet device on a custom docker network, confirming 100% functional telemetry payload parity.

To get detailed explanations of what each integration test script validates, run them with the `--help` flag:
```bash
./edge/spotter/bin/test_supervisor --help
./edge/spotter/bin/test_container --help
./edge/spotter/bin/test_parity --help
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
