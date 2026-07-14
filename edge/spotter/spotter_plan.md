# UDMI Spotter Node: Detailed Implementation & Technical Parity Plan

## Executive Summary

This document outlines the phase-by-phase implementation plan for the **UDMI Spotter Node** ([go/tdd:spotter](http://goto.google.com/tdd:spotter)). 

To maximize development velocity and deliver high-impact diagnostic features before the end of the year, this plan packages the **existing legacy discovery node** alongside the new **Spotter Core Agent** inside a dual-process container. This co-existence strategy allows the team to achieve drop-in replacement parity quickly, bypassing FIE coordination, while focusing engineering effort on advanced **Over-The-Air (OTA) Updates** and **Remote Ephemeral Packet Capture (PCAP)** features triggered declaratively via the UDMI Config channel.

---

## Phase Roadmap Overview

```mermaid
graph TD
    subgraph Phase 1: Containerization & Legacy Co-existence
        P1_1[Sub-phase 1.1: Dual-Process Container Architecture]
        P1_2[Sub-phase 1.2: Legacy Discovery Integration]
        P1_3[Sub-phase 1.3: Co-existence Parity Verification]
        P1_1 --> P1_2 --> P1_3
    end

    subgraph Phase 2: Advanced Remote Diagnostics & PCAP Engine
        P2_1[Sub-phase 2.1: Declarative PCAP Config Trigger]
        P2_2[Sub-phase 2.2: GCS Resumable Streaming Upload]
        P2_3[Sub-phase 2.3: Proxy & Offline (MQTT) Fallbacks]
        P1_3 --> P2_1 --> P2_2 --> P2_3
    end

    subgraph Phase 3: FIE-Independent OTA Engine & Test Cadre
        P3_1[Sub-phase 3.1: In-Container OTA Update Engine]
        P3_2[Sub-phase 3.2: Test Cadre - ATN & DUT Modes]
        P2_3 --> P3_1 --> P3_2
    end
```

---

## Phase 1: Containerization & Legacy Co-existence MVP

The goal of Phase 1 is to package the legacy discovery node into a containerized environment alongside the new Spotter Agent, ensuring functional parity with the existing bare-metal deployment without rewriting active discovery logic.

### Sub-phase 1.1: Dual-Process Container Architecture

#### Task 1.1.1: Container Entrypoint & Process Supervision [Completed]
* **Target Location**: `edge/spotter/container/`
* **Behavioral Specification**:
  * Define a container configuration (Dockerfile/Podman) that installs dependencies for both the legacy discovery node (e.g., `nmap`) and the Python Spotter Agent.
  * Implement a lightweight process supervisor (e.g., a shell entrypoint or `supervisord`) that launches and monitors:
    1.  The legacy `discovery_node` daemon.
    2.  The new `Spotter Core Agent` (Python).
  * Handle system signals (`SIGTERM`, `SIGINT`) to ensure graceful termination of both child processes. If either process experiences a fatal crash, log the failure and attempt recovery or restart the container.
* **Implementation & Verification**:
  * [Dockerfile](container/Dockerfile) - Multi-stage build with isolated virtual environments (`/venv_legacy` and `/venv_spotter`).
  * [supervisor.sh](container/supervisor.sh) - Lightweight process supervisor using `exec` in background jobs to properly track PIDs and handle signals gracefully.
  * [test_supervisor](bin/test_supervisor) - Bash-based verification of graceful shutdown under signals, and exit status code validation (101/102) when child processes crash.
  * [test_container](bin/test_container) - Bash-based verification of container execution, volume mounts, and child process lifecycle inside the built docker container.

#### Task 1.1.2: Shared Credentials & Connection Lifecycle [Completed]
* **Behavioral Specification**:
  * Configure the container to share on-prem credential paths (mTLS certificates/keys) so both the legacy discovery node and the Spotter Agent can authenticate with ClearBlade/Mosquitto.
  * Ensure the Spotter Agent initializes its own UDMI connection using clientlib abstractions (`CredentialManager`), establishing the management channel for new features (OTA, diagnostics).
* **Implementation & Verification**:
  * [agent.py](src/agent.py) - Spotter Core Agent connects using clientlib factory and maps shared configuration credentials (`key_file`, `cert_file`, `ca_file`). Supports `udmi_local` basic authentication by automatically deriving password from the sha256 hash of on-prem private keys.
  * [test_agent.py](tests/test_agent.py) - Checks correct construction of `EndpointConfiguration` for different authentication types and client ID configurations.

#### Task 1.1.3: Port & Resource Contention Audit [Completed]
* **Behavioral Specification**:
  * Identify and document all network ports used by both processes (e.g., legacy node binding to UDP 47808 for BACnet).
  * Configure the Spotter Agent to run without binding to any static inbound ports, avoiding conflicts with the legacy node.
  * Establish isolated file paths for logs and databases (e.g., legacy node writes to `/var/log/discovery_node/` while Spotter Agent writes to `/var/log/spotter/`) to prevent file lock contention.
  * Test behavior when ports are blocked or pre-bound by the host system to ensure graceful failure modes (no silent hangs).
* **Implementation & Verification**:
  * Isolated log directories `/var/log/discovery_node/` and `/var/log/spotter/` created.
  * Spotter Core Agent runs only as an outbound client connection without binding to static local inbound listening ports.
  * Verified supervisor avoids silent hangs and correctly terminates other processes if one crashes.

---

### Sub-phase 1.2: Legacy Discovery Integration

#### Task 1.2.1: Legacy Configuration Pass-Through [Completed]
* **Behavioral Specification**:
  * The legacy discovery node continues to receive its `discovery` configurations directly from the MQTT broker.
  * Spotter Agent ignores legacy discovery configuration blocks, avoiding conflicts.
  * Verify that passive scanning, active Nmap scans, and BACnet discovery triggered by the legacy process function correctly inside the containerized network namespace (host networking mode).
* **Implementation & Verification**:
  * [agent.py](src/agent.py) - Configured to register only the `SystemManager`, bypassing the default `DiscoveryManager` registration. This ensures the Spotter agent completely ignores `discovery` configurations, which are instead processed exclusively by the legacy node.


### Phase 1 Testing & Verification Summary

> [!IMPORTANT]
> Detailed testing requirements, network/mTLS execution conditions, and manual triage policies are documented in the project's testing standard: [GEMINI.md](GEMINI.md).

To ensure robustness, resource safety, and signal reliability, the following tests were executed and validated:

1. **Supervisor Process Lifecycle and Signal Propagation (`bin/test_supervisor`)**:
   - **Normal Lifecycle and Shutdown**: Verified that sending `SIGTERM` to the supervisor successfully propagates `SIGTERM` to both legacy node and spotter agent, causing them to shutdown gracefully. The supervisor waited for child terminations and exited cleanly with code `0`.
   - **Legacy Node Crash**: Simulated a crash of the legacy node (immediate exit 1). Observed that the supervisor logged the failure, immediately sent `SIGTERM` to the spotter agent, waited for the agent to exit, and exited with exit code `101`.
   - **Spotter Agent Crash**: Simulated a crash of the Spotter agent (immediate exit 2). Observed that the supervisor logged the failure, immediately sent `SIGTERM` to the legacy node, waited for the legacy node to exit, and exited with exit code `102`.
   - **Process Orphan Prevention**: Confirmed that using `exec` in background command invocations prevents intermediate shell fork wrappers. As a result, PIDs matched `$!` exactly, allowing signals to reach actual Python processes directly and preventing orphaned processes (reparented to PID 1).

2. **Agent Configuration Mapping & Security (`tests/test_agent.py`)**:
   - **Password Derivation**: Checked password calculations for `udmi_local` authentication. Derivations successfully read both `.pkcs8` private key versions and fallback `.pem` versions, producing the correct SHA256-based broker authentication secret.
   - **mTLS/Local Config**: Verified configuration mapping for basic authentication using a local Mosquitto setup. The derived `EndpointConfiguration` mapped the correct hostname, port, credentials files, and a client ID format (`/r/{registry_id}/d/{device_id}-spotter`) to prevent broker connection conflicts with the legacy node.
   - **JWT/GCP Config**: Verified configuration mapping for GCP JWT authentication, proving correct region, project, and GCP device paths formatting without basic credentials.
   - **Custom Client ID**: Verified custom `spotter_client_id` overrides were honored during config construction.

3. **Containerized Lifecycle & Integration Verification (`bin/test_container`)**:
   - **Environment Isolation Verification**: Ran the built Docker image (`udmi-spotter:latest`) in a container, mounting mock python scripts over the container's entry points (`/app/legacy/main.py` and `/app/spotter/agent.py`) and passing container-relative configurations.
   - **Graceful Signal Propagation in Container**: Verified that sending `SIGTERM` to the container correctly triggers the supervisor inside, propagates the shutdown signal to the containerized python processes, and exits cleanly (exit code `0`) within a timeout.
   - **Internal Crash Handlers**: Verified that when the simulated legacy script exits inside the container, the container halts and returns exit code `101`. Similarly, when the simulated spotter script crashes, the container halts and returns `102`, confirming the container lifecycle acts correctly in production environments.

4. **Differential Parity Integration Testing (`bin/test_parity`)**:
   - **Custom Isolated Bridge Network**: Set up a custom docker bridge network (`spotter-parity-net` on `192.168.12.0/24` with gateway `192.168.12.254`) to allow multiple containers to bind to BACnet port 47808 on separate IP addresses.
   - **Dual-Listener Local Broker**: Started a local `mosquitto` broker on the host with two listeners: a plain TCP listener on port 1883 for the test runner client, and a TLS listener on port 18883 with client certificate validation for the containers.
   - **Simulated BACnet Device Integration**: Deployed a simulated BACnet server container (`test-bacnet-device`) on the bridge network which responds to global BACnet discoveries.
   - **Sequential Execution & Event Capture**: Ran both the legacy container (`test-discovery_node:latest`) and the new dual-process container (`udmi-spotter:latest`) sequentially, triggered BACnet system scans via config updates, and intercepted published `DiscoveryEvent` messages on `/r/ZZ-TRI-FECTA/d/AHU-1/events/discovery`.
   - **100% Functional Payload Parity**: Confirmed that the output telemetry events (Scan Start, Discovered BACnet Device Attributes, Scan Stop) published by both containers matched exactly, verifying zero behavioral regressions.

---


### Sub-phase 1.3: Co-existence Parity Verification

#### Task 1.3.1: Differential Parity Testbed [Completed]
* **Target Location**: `edge/spotter/bin`
* **Behavioral Specification**:
  * Deploy a virtual test network with simulated BACnet devices.
  * Run the legacy bare-metal discovery node and the new containerized Spotter side-by-side.
  * Intercept and compare the published `DiscoveryEvent` telemetry payloads.
* **Implementation & Verification**:
  * [test_parity](bin/test_parity) - Spins up a custom bridge network, runs the simulated BACnet server container (`test-bacnet-device`), runs a dual-port Mosquitto broker (plain & TLS with client certs validation), and sequentially tests `test-discovery_node` vs `udmi-spotter` to assert event parity.
* **Verification Criteria**:
  * Assert 100% field parity for discovered device attributes. The containerized deployment must not introduce regressions or schema deviations.

---

## Phase 2: Advanced Remote Diagnostics & PCAP Engine

Phase 2 focuses on delivering the high-impact diagnostic features: remote triggering via standard UDMI blobsets, RAM-safe streaming, and robust transport modes for restricted networks.

### Sub-phase 2.1: Declarative Diagnostic Job Trigger (via UDMI Blobset)

#### Task 2.1.1: UDMI Blobset Job Manager (`BlobsetManager`) [Completed]
* **Target Location**: `udmi.core.managers.blobset_manager` (Spotter Agent)
* **Behavioral Specification**:
  * Monitor configuration updates in the standard `blobset.blobs` block.
  * Listen for changes to a specific diagnostic target (e.g., `pcap_capture`).
  * Trigger execution when the `generation` timestamp increments and the `phase` is set to `final`.
  * Retrieve the diagnostic job profile from the provided `url`. Support both HTTPS URLs and inline Base64 data URIs.
  * Validate the cryptographic `sha256` checksum of the retrieved profile payload.
  * Report progress in `state.blobset.blobs.pcap_capture` using standard phases (`apply`, `final`) and log messages under the `blobset.blob` namespace.
* **Implementation & Verification**:
  * [agent.py](src/agent.py) - Registers a custom pcap_capture blob handler, validates checksums, and decodes inline Base64 diagnostic payloads.
  * [system_manager.py](../../clientlib/python/src/udmi/core/managers/system_manager.py) - Incorporates support for standard `blobset.blobs` configuration schemas, tracks generation versions, and manages asynchronous worker thread lifecycles.

#### Task 2.1.2: PCAP Driver Execution [Completed]
* **Target Location**: `udmi.core.diagnostics.pcap`
* **Behavioral Specification**:
  * Parse the downloaded JSON diagnostic profile, which contains:
    * `interface`: Target network interface.
    * `filter`: BPF filter string (e.g., `udp port 47808` to target BACnet).
    * `max_duration_sec`: Capture duration safety limit.
    * `max_bytes`: Byte limit to prevent storage/network exhaustion.
    * `upload_url`: Target destination GCS URL.
  * Spawn a thread to execute the packet capture driver (using `tcpdump` or socket sniffer).
  * **Resource Safety:** Run capture processes under low IO/CPU scheduling priorities (`nice` / `ionice`).
* **Implementation & Verification**:
  * [pcap.py](src/pcap.py) - Captures raw network traffic via a subprocess executing `tcpdump`. Terminates gracefully on duration or byte constraints.

---

### Sub-phase 2.2: GCS Resumable Streaming Upload

#### Task 2.2.1: Resumable Upload Client [Completed]
* **Target Location**: `udmi.core.blob.uploader`
* **Behavioral Specification**:
  * Implement the Google Cloud Storage (GCS) **Resumable Upload protocol** using HTTP PUT chunking.
  * Upon trigger, immediately POST to the `upload_url` provided in the diagnostic profile to initiate a resumable session, obtaining a long-lived Session URI.
  * Pipe the packet capture stdout directly to the uploader.
  * Stream the data in chunks (e.g., 5MB blocks) to the Session URI *while the capture is running*.
  * **Zero-Disk Rule:** Do not write raw packets to the local filesystem. Buffers must exist only in RAM and be flushed immediately to GCS.
  * Finalize the session when the capture completes, verifying the GCS response.
* **Implementation & Verification**:
  * [uploader.py](src/uploader.py) - Implements chunked streaming GCS resumable upload client using standard Python HTTP libraries and chunk generator piping.
  * [test_diagnostics](bin/test_diagnostics) - Verifies remote pcap capture triggers, local mock resumable upload server handling, and packet validation.

---

### Sub-phase 2.3: Proxy & Offline (MQTT) Fallbacks

#### Task 2.3.1: Proxy Support for HTTP Uploads [Completed]
* **Behavioral Specification**:
  * Ensure the HTTP uploader honors standard environment variables (`HTTPS_PROXY`, `HTTP_PROXY`) to route GCS upload traffic through local corporate/OT proxies.
* **Implementation & Verification**:
  * Honors standard proxy environment variables natively through the use of the Python `requests` library. No additional code overhead is required to route traffic through proxies or custom CA certificates.

#### Task 2.3.2: MQTT Chunked Telemetry Fallback [Completed]
* **Behavioral Specification**:
  * In environments where HTTP egress is completely blocked (no proxy), Spotter must fall back to MQTT transport.
  * Chunk the PCAP file into broker-compliant sizes (e.g., 128KB), base64-encode them, and publish them sequentially over a dedicated telemetry topic (`events/pcap`).
  * Include metadata: `session_id`, `chunk_index`, `total_chunks`, and the chunk payload.
  * *Note: Requires a cloud-side reassembly worker to reconstruct the files on GCS.*
* **Implementation & Verification**:
  * [agent.py](src/agent.py) - Implements caching generator wrapper to store raw packet chunks in RAM during live capture, catches GCS resumable HTTP transport errors (connection/timeout), and publishes base64-encoded `PcapChunkEvent` objects sequentially over MQTT to `events/pcap`.
  * [test_diagnostics](bin/test_diagnostics) - Verifies MQTT fallback chunking by triggering a diagnostic job without an `upload_url`, subscribing to `/r/ZZ-TRI-FECTA/d/AHU-1-spotter/events/pcap`, reassembling the chunks, and validating the resulting PCAP headers.

#### Sub-phase 2.4: Startup & Dependency Management [Completed]

#### Task 2.4.1: Standalone Dependency Declarations [Completed]
* **Behavioral Specification**:
  * Provide a dedicated `requirements.txt` listing all Spotter Python dependencies to support local developer environment builds without container rebuild requirements.
* **Implementation & Verification**:
  * [requirements.txt](requirements.txt) - Declares explicit python dependency versions (`requests>=2.32.5`).

#### Task 2.4.2: Unified Startup Orchestrator (`spotter`) [Completed]
* **Behavioral Specification**:
  * Provide a startup script (`spotter`) that automatically prepares the environment and launches Spotter either in containerized mode or standalone local mode.
  * In local mode, it must build distinct virtual environments for both the legacy discovery node and the Spotter agent, configure PYTHONPATH, and start both processes under the lightweight supervisor.
* **Implementation & Verification**:
  * [spotter](../../bin/spotter) - Integrated into the root `bin/` directory. Added support for positional arguments (mimicking `bin/pubber`) allowing running against any device in any site model. Dynamically generates configurations, resolves credentials/certificates, and updates the local broker dynamic security rules to bypass Client ID constraints (allowing both processes to run under the same device ID without connection clashes).
  * [stop_spotter](../../bin/stop_spotter) - Integrated into the root `bin/` directory. Safely cleans up containerized and local background supervised processes.

---

## Phase 3: FIE-Independent OTA Engine & Test Cadre

Phase 3 enables the development team to update Spotter logic autonomously and integrates Spotter into the UDMI verification pipelines.

### Sub-phase 3.1: In-Container OTA Update Engine

#### Task 3.1.1: Two-Stage Blob Pipeline & Supervisor Integration
* **Target Location**: `udmi.core.blob` (Spotter Agent) & Supervisor Entrypoint
* **Behavioral Specification**:
  * Implement the two-stage blob update pipeline (`apply` -> `final` state transitions) targeting the Spotter Agent itself.
  * Define the orchestration flow where the Spotter Agent downloads the package, notifies the Supervisor, and exits.
  * Implement Supervisor logic to handle process restarts, detect exit codes, manage virtual environment symlinks (`/opt/spotter/active/venv` -> `/opt/spotter/staging/venv`), and trigger rollbacks on failure.
  * Supported OTA Blob Types:
    1.  `ota_package` (High Impact): Update the Spotter Agent Python code using `.whl` files.
    2.  `discovery_rules`: Hot-reloading of discovery signatures without agent restart.

#### Task 3.1.2: Staged Sandbox & Self-Test Suite (The Self-Testing Agent)
* **Target Location**: `edge/spotter/tests/self_test.py` & Supervisor
* **Behavioral Specification**:
  * Create a lightweight standalone self-test suite (`self_test.py`) packed inside the Spotter distribution.
  * The test suite must run quickly (< 10 seconds) and check:
    *   `test_imports`: Verify all python modules and dependencies resolve.
    *   `test_permissions`: Verify the script can access raw sockets and shared credentials.
    *   `test_mock_loop`: Verify the core message loop starts without immediate crash.
  * Configure the Supervisor to execute this test suite against the *staged* virtual environment (`/opt/spotter/staging/venv/bin/python -m spotter.tests.self_test`) **before** swapping the active symlink.
  * Ensure the update is rejected and rolled back immediately if the test suite returns a non-zero exit code.

---

### Sub-phase 3.2: Test Cadre Integration

#### Task 3.2.1: ATN & DUT Modes
* **Behavioral Specification**:
  * Support Ancillary Test Node (ATN) mode to inject mock states and topologies for validator testing.
  * Support Device Under Test (DUT) mode to verify cloud routing and key rotation.
* **Testing Strategy**:
  * Run the full sequencer test suite (`bin/test_sequencer`) with Spotter acting as the target to audit compliance.

---

## Definition of Done (DoD) Criteria

1. **Dual-Process Parity**: Parity verified against bare-metal `discovery_node` using the `diff_validator` suite.
2. **Declarative Triggering**: PCAP capture trigger and parameter mapping verified via `system.diagnostics.pcap` config block.
3. **RAM-Safe PCAP Streaming**: Ephemeral packet capture verified to stream via GCS Resumable Upload without exceeding local RAM limits (OOM test).
4. **Proxy/Fallback Compliance**: Successful upload verified under simulated proxy-only and MQTT-only environments.
5. **OTA Verification**: Safe OTA update flow verified: successful sandbox self-testing promotes the package, while simulated syntax/dependency errors trigger immediate rollback before promotion.
6. **Standard Compliance**: Zero-code plan compliance and 3-stage validation gate completion (Unit, Schema, Local Integration) as per `GEMINI.md`.
