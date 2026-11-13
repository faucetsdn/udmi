[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Spotter](#)

# Spotter Reference Edge Node

The **UDMI Spotter Agent** is the reference edge node for Operational Technology (OT) building automation networks within the UDMI ecosystem. Spotter consolidates field network discovery, automated point mapping, remote ephemeral packet captures (PCAP), and host observability telemetry into a single unified edge process.

---

## Technical Architecture

Spotter runs as a single unified process using the UDMI Python Client Library (`clientlib`), managing three core managers under a single device identity (such as `DSN-1`):

1. **`LocalnetManager` & Pluggable Family Providers**:
   - **`BacnetFamilyProvider`**: Performs active BACnet Who-Is / I-Am discovery, object enumeration, and UDP port extraction.
   - **`EtherFamilyProvider`**: Performs Layer-2 Ethernet / ARP, nmap XML host parsing, and ICMP ping sweeps.
   - **`PassiveFamilyProvider`**: Listens for passive broadcast network traffic, decodes BACnet/IP `BVLL` (`Forwarded-NPDU` real controller IP/port behind `BBMD` routers) and `NPDU` (`SNET`/`SADR` routed `MS/TP` frames and `I-Am-Router-To-Network` `DNET` lists), and extracts discovered device metadata.
2. **`SpotterDiscoveryManager`**:
   - Manages scheduled and on-demand discovery sweeps.
   - Streams live remote packet capture traces (`upload/discovery`) buffered in volatile memory with circuit-breaker protection (zero-disk streaming).
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
        MB["MQTT Broker / UDMIS / Butler"]
        DEV["Field OT Devices / BACnet Controllers"]
    end

    SYS -->|"state.system / events/system"| MB
    DISC -->|"events/discovery & upload/discovery"| MB
    LOC -->|"Scans & Probes"| DEV
```

---

## Ephemeral PCAP & Streaming MQTT Pipeline

Spotter processes diagnostic packet capture triggers sent declaratively over the UDMI discovery configuration channel (`config.discovery.families.<family>` with `depth: "trace"`):

1. **Capture Worker & Active BACnet Trace Rounds ([pcap.py](../../edge/spotter/src/pcap.py))**:
   - Spawns `tcpdump` with configurable interface filters (defaulting to `"udp port 47808"` for `bacnet`), enforcing strict execution bounds (maximum duration and byte quotas).
   - For `families.bacnet` with `depth: "trace"`, skips `BAC0.lite()` socket binding so UDP port `47808` remains unbound and executes paced raw-socket (`SOCK_RAW, IPPROTO_UDP`) `Who-Is` and `Who-Is-Router-To-Network` rounds during the capture window.
2. **Streaming MQTT Egress Transport (`upload/discovery`)**:
   - **Zero-Disk Streaming**: Packets are buffered dynamically in volatile memory (RAM), `gzip`-compressed per chunk, and sequentially published as `DiscoveryUpload` chunks over the `upload/discovery` MQTT topic (`event_no = 0` start marker, `1..N` `gzip`-compressed base64 chunks, and `-(N+1)` terminal marker with uncompressed `sha256` and `scan_manifest`).
   - **Zero Secret Distribution**: Leverages the existing mTLS hardware key/certificate connection directly, avoiding external network credentials or outbound HTTP rules at the edge.
3. **Cloud & Local PCAP Reassembly**:
   - **Butler Service (`PcapUploadHandler`)**: Subscribes to `upload/discovery` (or `uufi_upload`), decompresses and reassembles chunks in order, validates the terminal `sha256` digest, and writes the `.pcap` and `scan_manifest.json` to GCS (`gs://<bucket>/<site>/<gen>.pcap`) or local disk without persisting raw chunks to PostgreSQL.
   - **Ad-hoc CLI Reassembly ([bin/reassemble_pcap](../../edge/spotter/bin/reassemble_pcap))**:
     Reassembles chunked `upload/discovery` messages (from JSON, JSONL, or `mosquitto_sub`) back into a valid `.pcap` binary capture file and `scan_manifest.json`:
   ```bash
   # Reassemble upload/discovery chunks file into a pcap file:
   ./edge/spotter/bin/reassemble_pcap discovery_upload.json capture.pcap

   # Or stream directly from mosquitto subscriber:
   mosquitto_sub -h $BROKER -t '/r/+/d/+/upload/discovery' | ./edge/spotter/bin/reassemble_pcap - live.pcap
   ```

---

## Quick Start & Running Spotter

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

### 4. Logging & Diagnostics

When running in local or container mode, logs are automatically routed to `out/spotter/agent.log`.

---

## Verification & Testing Standards

Spotter includes a comprehensive suite of unit and integration tests located in [edge/spotter/tests/](../../edge/spotter/tests) and [edge/spotter/bin/](../../edge/spotter/bin). Use the unified test orchestrator [run_spotter_tests](../../edge/spotter/bin/run_spotter_tests) for consistent execution across environments:

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
- **`test_parity`**: Runs co-existence integration testing against a simulated BACnet device on an isolated bridge network, confirming functional discovery parity with legacy discovery nodes.
- **`compare_field_parity`**: Automated CLI utility to run sequential discovery parity scans or diff event logs on live field testbeds without manual comparison.
