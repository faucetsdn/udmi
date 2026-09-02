# UDMI Spotter Edge Reference Node

The **UDMI Spotter Agent** is the reference edge node for Operational Technology (OT) building automation networks within the UDMI ecosystem. Spotter consolidates field network discovery, automated point mapping, remote ephemeral packet captures (PCAP), and host observability telemetry into a single unified edge process.

---

## Technical Architecture

Spotter runs as a single unified process using the UDMI Python Client Library (`clientlib`), managing three core managers under a single device identity:

1. **`LocalnetManager` & Pluggable Family Providers**:
   - **`BacnetFamilyProvider`**: Performs active BACnet Who-Is / I-Am discovery, object enumeration, and UDP port extraction ([b/549909773](https://b.corp.google.com/issues/549909773)).
   - **`EtherFamilyProvider`**: Performs Layer-2 Ethernet / ARP and ping discovery.
   - **`PassiveFamilyProvider`**: Listens for passive broadcast network traffic and extracts discovered device metadata.
2. **`SpotterDiscoveryManager`**:
   - Manages scheduled and on-demand discovery sweeps.
   - Streams live remote packet capture traces (`events/stream`) safely buffered in RAM.
3. **`SystemManager`**:
   - Collects host metrics (CPU load, memory, OS distribution) without SSH.
   - Handles ephemeral secrets over non-persisted MQTT command channels (`commands/secret`).

```mermaid
graph TD
    subgraph "Spotter Edge Process"
        AGENT["Spotter Core Agent (agent.py)"]
        SYS["SystemManager (Host Telemetry & Ephemeral Secrets)"]
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

    SYS -->|"state.system / commands/secret"| MB
    DISC -->|"events/discovery & events/stream"| MB
    LOC -->|"Scans & Probes"| DEV
```

---

## Ephemeral PCAP & Streaming MQTT Pipeline

Spotter processes diagnostic packet capture triggers sent declaratively over the UDMI discovery configuration channel (`config.discovery.families` with `depth: "trace"`):

1. **Capture Worker ([pcap.py](src/pcap.py))**: Spawns `tcpdump` with configurable interface filters, enforcing strict execution bounds (maximum duration and byte quotas).
2. **Streaming MQTT Egress Transport**:
   - **Zero-Disk Streaming**: Packets are buffered dynamically in volatile memory (RAM) and sequentially published as reliable base64 chunks (`StreamEvents`) over the universal streaming MQTT event topic (`events/stream`).
   - **Zero Secret Distribution**: Leverages the existing mTLS hardware key/certificate connection directly, avoiding external network credentials or outbound HTTP rules at the edge.
3. **Ad-hoc PCAP Reassembly ([bin/reassemble_pcap](bin/reassemble_pcap))**:
   Reassembles chunked `events/stream` messages (from JSON, JSONL, or `mosquitto_sub`) back into a valid `.pcap` binary capture file:
   ```bash
   # Reassemble stream events file into a pcap file:
   ./edge/spotter/bin/reassemble_pcap stream_events.json capture.pcap

   # Or stream directly from mosquitto subscriber:
   mosquitto_sub -h $BROKER -t '/r/+/d/+/events/stream' | ./edge/spotter/bin/reassemble_pcap - live.pcap
   ```

---

## Directory Structure

| Path | Description |
| :--- | :--- |
| **[bin/spotter](../../bin/spotter)** | Unified CLI orchestrator for starting (local/container) and stopping instances |
| **[bin/run_spotter_tests](bin/run_spotter_tests)** | Unified verification test runner for unit and integration suites |
| **[bin/reassemble_pcap](bin/reassemble_pcap)** | Ad-hoc CLI utility to reassemble `events/stream` chunks into `.pcap` files |
| **[bin/compare_field_parity](bin/compare_field_parity)** | Automated CLI tool to verify 100% field parity between legacy node and Spotter |
| **[src/agent.py](src/agent.py)** | Main Spotter agent entry point, manager wiring, and command dispatcher |
| **[src/providers/](src/providers/)** | Modular protocol discovery providers (BACnet, Ether, Passive) |
| **[src/host_telemetry.py](src/host_telemetry.py)** | Zero-SSH host OS and performance telemetry probes |
| **[src/pcap.py](src/pcap.py)** | Safe `tcpdump` wrapper yielding binary streams with duration and size caps |
| **[container/Dockerfile](container/Dockerfile)** | Multi-stage Docker image build specification |
| **[spotter_config.json](spotter_config.json)** | Sample configuration file for endpoint and BACnet parameters |
| **[GEMINI.md](GEMINI.md)** | Detailed testing standards, execution matrices, and triage guidelines |

---

## Quick Start & Usage

Use the unified orchestrator script [bin/spotter](../../bin/spotter) located in the project root:

### 1. Launch Spotter

* **Local Development (from Site Model)**:
  ```bash
  ./bin/spotter sites/udmi_site_model //mqtt/localhost AHU-1
  ```
* **Container Mode (using Configuration File)**:
  ```bash
  ./bin/spotter edge/spotter/spotter_config.json 1234 --mode container
  ```

### 2. Stop Running Instances

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

1. **UDMIS Native PCAP Ingestion Pipeline**: Native service-side ingestion and reassembly in UDMIS to automatically collect and persist `events/stream` PCAP chunks directly into Cloud Storage (GCS) or BigQuery blob storage.
2. **Heterogeneous Secret Delivery**: Extensible mechanism for securely injecting multi-tenant operational credentials (e.g., vendor device credentials, BACnet network encryption keys) via encrypted/ephemeral payloads.
3. **Key Rotation & Lifecycle**: End-to-end device private/public key rotation with automated cloud IoT registry coordination, backup verification, and zero-downtime reconnection.
4. **Expanded Host Observability Metrics**: Network adapter error/drop counters, hardware temperature, storage/inode thresholds, and edge-to-cloud roundtrip latency.
