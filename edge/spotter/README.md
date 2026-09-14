# UDMI Spotter Edge Reference Node

The **UDMI Spotter Agent** is the reference edge node for Operational Technology (OT) building automation networks within the UDMI ecosystem. Spotter consolidates field network discovery, automated point mapping, remote ephemeral packet captures (PCAP), and host observability telemetry into a single unified edge process.

---

## Technical Architecture

Spotter runs as a single unified process using the UDMI Python Client Library (`clientlib`), managing three core managers under a single device identity:

1. **`LocalnetManager` & Pluggable Family Providers**:
   - **`BacnetFamilyProvider`**: Performs active BACnet Who-Is / I-Am discovery, object enumeration, and UDP port extraction.
   - **`EtherFamilyProvider`**: Performs Layer-2 Ethernet / ARP, nmap XML host parsing, and ICMP ping sweeps.
   - **`PassiveFamilyProvider`**: Listens for passive broadcast network traffic and extracts discovered device metadata.
2. **`SpotterDiscoveryManager`**:
   - Manages scheduled and on-demand discovery sweeps.
   - Streams live remote packet capture traces (`events/streams`) buffered in volatile memory with circuit-breaker protection (zero-disk streaming).
3. **`SpotterSystemManager`**:
   - Collects host metrics (CPU load, memory, OS distribution) and emits periodic telemetry (`events/system`).
   - Evaluates memory usage against safety thresholds (`check_safety_circuit_breaker`), throttling active discovery and packet captures to protect edge devices from kernel OOM termination.

```mermaid
graph TD
    subgraph "Spotter Edge Process"
        AGENT["Spotter Core Agent (agent.py)"]
        SYS["SpotterSystemManager (Host Telemetry & Health)"]
        DISC["SpotterDiscoveryManager (PCAP & Scheduling)"]
        LOC["LocalnetManager (Pluggable Providers)"]
        
        BAC["BacnetFamilyProvider (Port Capture)"]
        ETH["EtherFamilyProvider (ARP / Ping)"]
        PAS["PassiveFamilyProvider (Passive Sniffing)"]
        
        AGENT --> SYS
        AGENT --> DISC
        AGENT --> LOC
        
        LOC --> BAC
        LOC --> ETH
        LOC --> PAS
    end

    subgraph "Messaging & Field Network"
        MB["MQTT Broker / UDMIS"]
        DEV["Field OT Devices / BACnet Controllers"]
    end

    SYS -->|"state.system / events/system"| MB
    DISC -->|"events/discovery & events/streams"| MB
    LOC -->|"Scans & Probes"| DEV
```

---

## Edge Configuration & Compatibility

Spotter natively accepts both canonical UDMI configurations and production configurations supplied to legacy edge discovery nodes:

### 1. Static Configuration Overlays (`configs_dir`)
When `configs_dir` is specified in the primary configuration (e.g. `/opt/discovery_node/configs/extra.d`), Spotter automatically scans and recursively merges all JSON overlay files alphabetically upon startup.

### 2. Protocol Discovery Controls
- **Family Enablement Toggles**: `udmi.discovery.bacnet`, `udmi.discovery.ether`, and `udmi.discovery.ipv4` selectively enable or disable discovery providers via boolean (`true`/`false`) or string (`"false"`, `"0"`, `"off"`) flags.
- **Ethernet Concurrency**: `ether.ping_concurrency` configures the maximum parallel ICMP ping worker threads (default: `4`).
- **Passive Sniffing & Subnet Exclusions**: `ip.subnet_filter` (CIDR format, e.g. `192.168.1.10/24`) and `ip.interface` configure the network capture interface and BPF exclusion filters for broadcast, gateway, and host addresses.

### 3. Transparent Legacy Depth Normalization
To maintain backward compatibility with legacy edge schedules without polluting the abstract UDMI schema, Spotter normalizes legacy strings at the edge ingestion boundary:
- `"ping"` $\rightarrow$ `"entries"` (Host presence / IP addresses)
- `"ports"` $\rightarrow$ `"details"` (Open TCP/UDP ports and attributes)
- `"services"` $\rightarrow$ `"parts"` (Detailed service and point definitions)

---

## Ephemeral PCAP & Streaming MQTT Pipeline

Spotter processes diagnostic packet capture triggers sent declaratively over the UDMI discovery configuration channel (`config.discovery.families` with `depth: "trace"`):

1. **Capture Worker ([pcap.py](src/pcap.py))**: Spawns `tcpdump` with configurable interface filters, enforcing strict execution bounds (maximum duration and byte quotas).
2. **Streaming MQTT Egress Transport**:
   - **Zero-Disk Streaming**: Packets are buffered dynamically in volatile memory (RAM) and sequentially published as reliable base64 chunks (`StreamsEvents`) over the universal streaming MQTT event topic (`events/streams`).
   - **Zero Secret Distribution**: Leverages the existing mTLS hardware key/certificate connection directly, avoiding external network credentials or outbound HTTP rules at the edge.
3. **Ad-hoc PCAP Reassembly ([bin/reassemble_pcap](bin/reassemble_pcap))**:
   Reassembles chunked `events/streams` messages (from JSON, JSONL, or `mosquitto_sub`) back into a valid `.pcap` binary capture file:
   ```bash
   # Reassemble stream events file into a pcap file:
   ./edge/spotter/bin/reassemble_pcap stream_events.json capture.pcap

   # Or stream directly from mosquitto subscriber:
   mosquitto_sub -h $BROKER -t '/r/+/d/+/events/streams' | ./edge/spotter/bin/reassemble_pcap - live.pcap
   ```

---

## Directory Structure

| Path | Description |
| :--- | :--- |
| **[bin/spotter](../../bin/spotter)** | Unified CLI orchestrator for starting (local/container) and stopping instances |
| **[bin/run_spotter_tests](bin/run_spotter_tests)** | Unified verification test runner for unit and integration suites |
| **[bin/reassemble_pcap](bin/reassemble_pcap)** | Ad-hoc CLI utility to reassemble `events/streams` chunks into `.pcap` files |
| **[bin/compare_field_parity](bin/compare_field_parity)** | Automated CLI tool to verify functional parity between legacy node and Spotter |
| **[src/agent.py](src/agent.py)** | Main Spotter agent entry point, manager wiring, and command dispatcher |
| **[src/providers/](src/providers/)** | Modular protocol discovery providers (BACnet, Ether, Passive) |
| **[src/host_telemetry.py](src/host_telemetry.py)** | Host OS and performance telemetry probes |
| **[src/pcap.py](src/pcap.py)** | Safe `tcpdump` wrapper yielding binary streams with duration and size caps |
| **[container/Dockerfile](container/Dockerfile)** | Multi-stage Docker image build specification |
| **[spotter_config.json](spotter_config.json)** | Sample configuration file for endpoint and BACnet parameters |
| **[GEMINI.md](GEMINI.md)** | Detailed testing standards, execution matrices, and triage guidelines |

---

## Quick Start & Usage

Use the unified orchestrator script [bin/spotter](../../bin/spotter) located in the project root:

### 1. Launch Spotter

* **Local Development (against UDMI Local Orchestrator)**:
  ```bash
  # 1. Start local UDMI infrastructure (Barbican & Butler services)
  bin/udmi start sites/udmi_site_model //mqtt/localhost:46432

  # 2. Launch Spotter targeting the local broker
  ./bin/spotter sites/udmi_site_model //mqtt/localhost:46432 AHU-1
  ```
* **Container Mode (using Configuration File)**:
  ```bash
  ./bin/spotter edge/spotter/spotter_config.json 1234 --mode container
  ```

### 2. Connection Resilience & Startup Probing

Spotter incorporates built-in broker readiness probing and connection retry loops:
* Probes target MQTT broker reachability via TCP socket probing for up to 30 seconds (`mqtt.connect_timeout_sec`).
* Automatically performs retry backoff upon transient disconnects during broker TLS initialization (`mqtt.connect_retries` and `mqtt.connect_retry_delay_sec`).

### 3. Stop Running Instances

```bash
# Stop all background Spotter processes
./bin/spotter stop

# Stop a specific instance by serial number
./bin/spotter stop 1234
```

---

## Verification & Testing

Spotter includes a comprehensive suite of unit and integration tests located in [tests/](tests) and [bin/](bin). Use the unified test orchestrator [run_spotter_tests](bin/run_spotter_tests) for consistent execution across environments:

```bash
# Run logical unit tests (fast, no external dependencies)
./edge/spotter/bin/run_spotter_tests unit

# Run container lifecycle, PCAP streaming, circuit breakers, and differential discovery parity
./edge/spotter/bin/run_spotter_tests integration

# Run the complete test suite (unit and integration)
./edge/spotter/bin/run_spotter_tests all
```

### Key Integration Tests

- **`test_container`**: Validates container lifecycle isolation, volume mounting of runtime configs, clean signal termination, and exit code propagation inside Docker.
- **`test_pcap`**: Validates remote-triggered PCAP packet capture diagnostics over MQTT streaming and verifies binary reassembly via `reassemble_pcap`.
- **`test_resource_contention`**: Validates process CPU, memory consumption, `/proc/PID/fd` file descriptor stability under load, and memory safety circuit breaker tripping.
- **`test_fault_injection`**: Validates network fault tolerance, streaming MQTT backoff recovery, and socket reconnect logic under simulated broker resets.
- **`test_parity`**: Runs co-existence integration testing against a simulated BACnet device on an isolated bridge network, confirming functional parity with legacy discovery nodes.
- **`compare_field_parity`**: Automated CLI utility to run sequential discovery parity scans or diff event logs on live field testbeds without manual comparison.

### Automated Field Parity Verification ([bin/compare_field_parity](bin/compare_field_parity))

To prove byte-for-byte discovery parity on an actual field testbed machine without manual diffing:

```bash
# Automated live scan comparison (back-to-back sequential execution):
sudo ./edge/spotter/bin/compare_field_parity --config /etc/udmi_discovery/config.json --duration 20

# Or offline diff of previously collected discovery event files:
./edge/spotter/bin/compare_field_parity --diff legacy_events.json spotter_events.json -v
```

---

## Future Roadmap

The following capabilities are tracked as future milestones:

1. **UDMIS Native PCAP Ingestion Pipeline**: Native service-side ingestion and reassembly in UDMIS to automatically collect and persist `events/streams` PCAP chunks directly into Cloud Storage (GCS) or BigQuery blob storage.
2. **Ephemeral Secret Delivery**: Architectural mechanism for delivering transient, authenticated operational credentials (e.g., vendor device credentials, BACnet network encryption keys, or REST API bearer tokens) strictly in volatile RAM without disk persistence. A dedicated architectural discussion will finalize the delivery transport (such as non-persisted command channels e.g. `commands/secret` vs encrypted `config.blobset`), memory scrubbing lifecycles, and cryptographic envelope validation before implementation.
3. **Key Rotation & Lifecycle**: End-to-end device private/public key rotation with automated cloud IoT registry coordination, backup verification, and zero-downtime reconnection.
4. **Expanded Host Observability Metrics**: Network adapter error/drop counters, hardware temperature, storage/inode thresholds, and edge-to-cloud roundtrip latency.
