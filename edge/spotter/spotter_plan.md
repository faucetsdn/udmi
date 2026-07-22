# UDMI Spotter Node: Detailed Implementation & Technical Parity Plan

## Executive Summary

This document outlines the phase-by-phase implementation plan for the **UDMI Spotter Node** ([go/tdd:spotter](http://goto.google.com/tdd:spotter)). 

To maximize development velocity and deliver high-impact diagnostic features before the end of the year, this plan packages the **existing legacy discovery node** alongside the new **Spotter Core Agent** inside a dual-process container. This co-existence strategy allows the team to achieve drop-in replacement parity quickly, bypassing FIE coordination, while focusing engineering effort on advanced **Over-The-Air (OTA) Updates** and **Remote Ephemeral Packet Capture (PCAP)** features triggered declaratively via the UDMI Config channel.

---

## Technical Notes & Architectural Considerations

### 1. Direct Edge-to-GCS Streaming Authentication Hurdles
Directly streaming diagnostic data or PCAP packet captures from edge nodes to Google Cloud Storage (GCS) bucket endpoints introduces significant service account configuration and authentication hurdles:
* **Secret Distribution**: Requiring edge nodes to maintain GCP Service Account keys or OAuth credentials increases secret sprawl and security risk across OT networks.
* **Credential Rotation**: Key rotation and short-lived token management at the edge in zero-trust or restricted-egress environments introduces operational friction and risks authentication lockouts.

### 2. Primary Transport Strategy: Streaming MQTT Protocol
To eliminate cloud credential distribution to the edge, Spotter adopts the **Streaming MQTT Protocol** as its primary packet export mechanism:
* **Payload Chunking**: Massive message payloads are not required. Captures are divided into a continuous stream of smaller, broker-compliant frame events (e.g. 64KB - 128KB base64-encoded chunks).
* **Protocol Reusability**: The same streaming protocol pattern handles both outbound diagnostic data streams (`events/pcap`) and inbound binary transfers (e.g. delivering OTA blob updates).
* **Zero Additional Secrets**: Leverages the pre-existing edge-to-broker mTLS hardware key/certificate authentication channel without extra GCS credentials on edge devices.

### 3. Future Mosquitto Migration & Cloud Reassembly Pipeline
* **Long-Term Mosquitto Architecture**: Future iterations of the Mosquitto cloud bridge architecture will natively support higher throughput message streams and dedicated cloud ingestion bridges.
* **Cloud Storage Offload**: Dedicated cloud bridge workers consume streaming MQTT topics directly from the broker and write reassembled files to GCS buckets or object storage in the cloud, fully insulating edge nodes from cloud storage authentication mechanics.

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
        P2_2[Sub-phase 2.2: Ephemeral PCAP & Streaming MQTT Engine]
        P2_3[Sub-phase 2.3: Cloud Ingestion & Edge Secret Decoupling]
        P1_3 --> P2_1 --> P2_2 --> P2_3
    end

    subgraph Phase 3: FIE-Independent OTA Engine & Test Cadre
        P3_1[Sub-phase 3.1: In-Container OTA Update Engine]
        P3_2[Sub-phase 3.2: Test Cadre - ATN & DUT Modes]
        P3_3[Sub-phase 3.3: Resource Contention & Prod Release Suite]
        P3_4[Sub-phase 3.4: Edge & Cloud Observability Framework]
        P2_3 --> P3_1 --> P3_2 --> P3_3 --> P3_4
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

### Sub-phase 2.2: Ephemeral PCAP & Streaming MQTT Transport Engine

#### Task 2.2.1: Streaming MQTT Protocol Driver [Completed]
* **Target Location**: `udmi.core.blob.uploader` & `udmi.core.diagnostics.pcap`
* **Behavioral Specification**:
  * Adopt **Streaming MQTT Protocol** as the primary first-class transport mechanism for packet export, avoiding edge GCS credential distribution and secret rotation hurdles.
  * **Payload Framing**: Ephemeral packet captures are divided into lightweight broker-compliant frame events (e.g. 64KB - 128KB base64-encoded chunks) and published sequentially over `events/pcap`.
  * **Protocol Metadata**: Each frame contains `session_id`, `chunk_index`, `total_chunks`, and chunk payload data.
  * **RAM Buffering & Zero-Disk Constraint**: Do not write raw packets to local disk; stream chunks dynamically from RAM buffers to the MQTT client socket.
* **Implementation & Verification**:
  * [agent.py](src/agent.py) - Implements chunked streaming generator emitting base64-encoded `PcapChunkEvent` objects sequentially over MQTT to `events/pcap`.
  * [test_diagnostics](bin/test_diagnostics) - Verifies streaming MQTT packet export by subscribing to `/r/ZZ-TRI-FECTA/d/AHU-1-spotter/events/pcap`, reassembling chunks, and validating PCAP headers.

---

### Sub-phase 2.3: Cloud Ingestion & Edge Secret Decoupling

#### Task 2.3.1: Edge Credential Decoupling & Optional Direct HTTP Upload [Completed]
* **Behavioral Specification**:
  * **Edge Authentication Decoupling**: Edge nodes rely solely on standard mTLS broker certificates. No GCP Service Account keys or OAuth tokens are required or distributed to the edge.
  * **Future Mosquitto Migration Compatibility**: Dedicated cloud bridge workers consume streaming MQTT topics directly from Mosquitto and handle GCS reassembly and object storage writes in the cloud.
  * **Optional Signed URL Fallback**: Direct HTTP GCS uploads (`uploader.py`) remain supported as an optional mode when pre-signed HTTP PUT URLs are explicitly supplied in the diagnostic config, using standard `HTTPS_PROXY` settings.
* **Implementation & Verification**:
  * [uploader.py](src/uploader.py) - Maintains optional chunked HTTP uploader supporting pre-signed URLs.
  * [agent.py](src/agent.py) - Defaults to zero-secret Streaming MQTT Protocol for all standard diagnostic jobs.

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

### Sub-phase 3.3: Dual-Process Resource Contention & Production Verification Suite

#### Task 3.3.1: Resource Contention & Load Stress Testing (`bin/test_resource_contention`)
* **Target Location**: `edge/spotter/bin/test_resource_contention` & `edge/spotter/src/agent.py`
* **Behavioral Specification**:
  * Implement an automated resource contention and load test script (`bin/test_resource_contention`) runnable both locally in synthetic docker integration pipelines and remotely against deployed production target nodes via declarative diagnostic triggers.
  * **Workload Scenarios**:
    1. **Concurrent Heavy I/O & Network Capture**: Trigger active Nmap scans & BACnet sweeps on the legacy node while simultaneously running high-throughput packet captures (`pcap_capture`) on Spotter.
    2. **CPU & Thread Starvation Probe**: Induce CPU load spikes on the legacy process while asserting that Spotter MQTT telemetry heartbeats remain punctual without dropped state updates.
    3. **File Descriptor & RAM Cgroup Leak Sweep**: Monitor File Descriptor allocations (`ulimit -n`) and memory cgroup usage during continuous PCAP streaming and discovery scans to verify zero memory bloat or FD leaks.
  * **Dual Execution Modes**:
    * **Local Synthetic Run**: Executed during Stage 3 local integration testing (`./edge/spotter/bin/test_resource_contention --mode local`) using containerized stress workloads on bridge subnets.
    * **Deployed Production Canary Run**: Declaratively triggered via MQTT diagnostic job profile (`system.diagnostics.resource_audit`) on deployed instances, streaming resource health metrics to cloud telemetry without interrupting production operations.
  * **Safety Circuit Breakers**:
    * Spotter Agent actively monitors container cgroup limits; if combined RAM or CPU usage crosses safety thresholds (e.g. 85%), non-essential diagnostic captures automatically throttle back or terminate to prevent kernel OOM Kills.

#### Task 3.3.2: Network Resilience & Transport Fault Injection (`bin/test_fault_injection`)
* **Target Location**: `edge/spotter/bin/test_fault_injection`
* **Behavioral Specification**:
  * Validate system resiliency when edge networks experience instability, proxy outages, or packet drops.
  * **Fault Scenarios**:
    1. **GCS Upload Proxy Outage**: Interrupt HTTP proxy connections mid-stream during PCAP upload to verify seamless fallback to chunked MQTT telemetry (`events/pcap`).
    2. **Transient Broker Socket Disconnection**: Force periodic broker socket disconnects during OTA wheel downloads to verify HTTP resumable download retries and backoff.
    3. **High Latency / Loss Networks**: Inject packet loss and latency via `tc/netem` on bridge networks to ensure heartbeat threads do not block or cause supervisor timeouts.

#### Task 3.3.3: Production Target Probes & Micro-Audit Suite
* **Target Location**: `edge/spotter/tests/self_test.py` & `edge/spotter/src/agent.py`
* **Behavioral Specification**:
  * Establish safe micro-audit probes that can be executed directly on deployed production target instances without disrupting discovery operations or host stability:
    1. **In-Container Sandbox Self-Test (`self_test.py`)**: Runs post-OTA deployment in <10 seconds. Verifies module imports, raw socket access, and credential file sanity before promoting staged virtual environments (`/opt/spotter/staging/venv`).
    2. **Declarative On-Device Resource Audit (`system.diagnostics.resource_audit`)**: Triggered via MQTT config update. Performs a timed non-destructive resource sweep and streams cgroup memory profiles, CPU saturation, file descriptor allocation (`ulimit -n`), and telemetry latencies to cloud telemetry.
    3. **Transport Fallback Micro-Probe**: Triggers micro packet captures directed to non-existent GCS endpoints to verify fallback paths on live OT networks.
  * **Test Location Target Matrix**:
    | Test Category | Local Integration Pipeline | Deployed Production Target |
    | :--- | :---: | :---: |
    | Supervisor Signal & Exit Code Kills ([test_supervisor](bin/test_supervisor)) | **Yes** | **No** (Destructive) |
    | Container Volume Mount & Build Isolation ([test_container](bin/test_container)) | **Yes** | **No** (Infrastructure) |
    | Full Parity Dual Bridge Network ([test_parity](bin/test_parity)) | **Yes** | **No** (Network Contention) |
    | Full Load Resource Stress ([test_resource_contention](bin/test_resource_contention)) | **Yes** | **Controlled Canary Only** |
    | Network Fault & Proxy Interruption ([test_fault_injection](bin/test_fault_injection)) | **Yes** | **No** (Simulated Fault) |
    | In-Container Self-Test Probe ([self_test.py](tests/self_test.py)) | **Yes** | **Yes** (Non-destructive) |
    | On-Device Resource Audit Profile (`resource_audit`) | **Yes** | **Yes** (Non-destructive) |

#### Task 3.3.4: Methodological Test-for-Production Release Framework
* **Behavioral Specification**:
  * Establish a 3-tier progressive release pipeline to eliminate release anxiety:
    1. **Tier 1 (Pre-Submit Integration Gate)**: Execution of `code_tests`, `schema_tests`, and local container integration suites (`test_container`, `test_resource_contention`).
    2. **Tier 2 (Autonomous In-Container Staging & Rollback)**: OTA updates deployed to staging venv. Supervisor invokes `self_test.py`; non-zero exit code triggers instant automatic rollback before symlink promotion.
    3. **Tier 3 (Progressive Production Canary Rollout)**: Staged rollout (1% -> 10% -> 100% of production fleet) paired with cloud-side automated metric monitoring (heartbeats, memory cgroups, error spikes) triggering automated rollback on anomaly detection.

---

### Sub-phase 3.4: Edge & Cloud Observability Framework

#### Task 3.4.1: Multi-Provider Metrics Pipeline (Prometheus / OpenTelemetry / `murdockd` / UDMI)
* **Target Location**: `edge/spotter/src/metrics.py` & `edge/spotter/src/agent.py`
* **Behavioral Specification**:
  * Implement a unified metrics collection exporter supporting open-source standards, Google-internal infrastructure, and native OT messaging channels:
  * **Open-Source Prometheus / OpenTelemetry Exporter**:
    * Expose a lightweight HTTP `/metrics` endpoint on port `9090` (or OpenTelemetry OTLP daemon push client).
    * Standard Metric Instrument Catalog:
      * `spotter_cpu_usage_ratio` (Gauge): Current CPU saturation ratio of the Spotter container.
      * `spotter_memory_bytes` (Gauge with label `type="rss|cgroup_limit|vms"`): Memory allocation vs allocated cgroup limits.
      * `spotter_open_fds` (Gauge): Open File Descriptors count (`ulimit -n` usage).
      * `spotter_pcap_packets_total` (Counter with label `status="captured|dropped|filtered"`): Raw PCAP packet accounting.
      * `spotter_pcap_bytes_transferred_total` (Counter with label `transport="gcs|mqtt"`): Streaming upload throughput.
      * `spotter_pcap_upload_duration_seconds` (Histogram): Upload latency buckets.
      * `spotter_ota_events_total` (Counter with label `result="success|rollback|failure"`): Staged OTA package promotions/rollbacks.
      * `spotter_mqtt_connection_status` (Gauge): Broker connectivity health (`1`=connected, `0`=disconnected).
  * **Google Internal `murdockd` Daemon Integration**:
    * Instrument Spotter Agent to export daemon health telemetry, heartbeat states, and daemon liveness probes over `/healthz` Unix domain sockets for Google-internal `murdockd` (Murdock Daemon) integration, piping metrics directly to Monarch and Google Cloud Monitoring.
  * **UDMI Native Metric Telemetry Channel (`events/metrics`)**:
    * Format metric snapshots into UDMI-compliant `events/metrics` JSON payloads published periodically over the outbound mTLS MQTT connection. This enables firewall-restricted OT environments to deliver observability telemetry to cloud backends without exposing local HTTP scrape ports.

#### Task 3.4.2: Distributed Tracing Context & Structured Logging
* **Target Location**: `edge/spotter/src/logger.py` & `edge/spotter/src/uploader.py`
* **Behavioral Specification**:
  * **OpenTelemetry W3C Trace Propagation**: Inject W3C `traceparent` (Trace ID / Span ID) headers into PCAP metadata events, HTTP GCS resumable upload headers, and MQTT diagnostic headers. This links edge diagnostic capture events directly with cloud-side reassembly workers, bigquery logs, and cloud trace dashboards.
  * **Structured JSON Logging**: Format container stdout/stderr as single-line JSON logs (`timestamp`, `severity`, `component`, `trace_id`, `message`) for structured parsing by Cloud Logging, FluentBit, or Vector log collectors.

---

## Definition of Done (DoD) Criteria

1. **Dual-Process Parity**: Parity verified against bare-metal `discovery_node` using the `diff_validator` suite.
2. **Declarative Triggering**: PCAP capture trigger and parameter mapping verified via `system.diagnostics.pcap` config block.
3. **RAM-Safe Streaming MQTT Transport**: Ephemeral packet capture verified to stream sequentially over `events/pcap` using small frame chunks without local disk storage or edge GCS service account credentials.
4. **Edge Secret Decoupling & Cloud Reassembly**: Zero cloud service account secrets required on edge devices; verified cloud-side ingestion bridge compatibility with Mosquitto architecture.
5. **OTA Verification**: Safe OTA update flow verified: successful sandbox self-testing promotes the package, while simulated syntax/dependency errors trigger immediate rollback before promotion.
6. **Standard Compliance**: Zero-code plan compliance and 3-stage validation gate completion (Unit, Schema, Local Integration) as per `GEMINI.md`.
7. **Resource Contention Immunity**: Simultaneous legacy discovery sweeps and high-throughput diagnostic PCAP sessions verified to operate without OOM kills, FD exhaustion, or delayed MQTT telemetry heartbeats both in local synthetic testbeds (`bin/test_resource_contention`) and on deployed production target nodes.
8. **Network Fault Resiliency**: Automatic fallback to chunked MQTT telemetry and backoff retry logic verified under simulated proxy failures and socket interruptions (`bin/test_fault_injection`).
9. **Production Canary Verification**: Safe execution of non-destructive production micro-audit probes (`self_test.py` and `resource_audit`) confirmed on deployed instances.
10. **Observability & Metrics Verification**: End-to-end telemetry metric export (Prometheus `/metrics`, `murdockd` integration, and native UDMI `events/metrics`) verified alongside W3C trace context propagation across diagnostic PCAP streaming sessions.
