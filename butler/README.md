# Butler (UDMI Mapping & Data Ingestion Service)

**Butler** is a unified Python service and CLI tool in the UDMI support ecosystem. It provides two major functions:
1. **Device Discovery & Mapping**: Dispatches discovery scans across BACnet, IPv4, and Vendor networks, correlates discovered device addresses with site models, and publishes model diff updates.
2. **Database Ingestion Service**: Subscribes to UDMI telemetry and state streams (over MQTT / PubSub or stdin JSON streams) and persists structured records into PostgreSQL and InfluxDB.

---

## Architecture Overview

```mermaid
graph TD
    subgraph UDMI Ingestion Pipeline
        SRC["UDMI Stream (MQTT Broker / Stdin)"] --> SVC["ButlerService (service.py)"]
        SVC --> DISP["MessageDispatcher (dispatcher.py)"]
        DISP --> H_RAW["RawFallbackHandler"]
        DISP --> H_PCAP["PcapUploadHandler (upload/discovery)"]
        DISP --> H_PT["PointsetEventsHandler"]
        DISP --> H_ST["PointStateHandler / SystemStateHandler"]
        DISP --> H_OTH["Discovery / Validation / Alarms / Metadata"]
        
        H_RAW --> PG[("PostgreSQL (udmi_messages)")]
        H_PCAP --> GCS[("GCS / Local PCAP Archive + udmi_pcap_captures")]
        H_PT --> INF[("InfluxDB (point_value)")]
        H_ST --> PG_SPEC[("PostgreSQL (udmi_* state tables)")]
        H_OTH --> PG_SPEC
    end

    subgraph Discovery & Mapping Pipeline
        DISC_CMD["butler discover"] --> DISC["Discovery (discovery.py)"]
        DISC --> MQTT_PUB["MQTT Topic (/uufi/r/.../c/config/discovery)"]
        MAP_CMD["butler map"] --> MAP["Mapping (mapping.py)"]
        PG --> MAP
        MAP --> SITE_MOD["Site Model (metadata.json / extras)"]
        MAP --> MODEL_PUB["MQTT Topic (/uufi/r/.../c/model/system)"]
    end
```

---

## Directory Structure

```
butler/
├── bin/
│   └── butler              # CLI executable wrapper
├── src/
│   ├── connection.py       # MQTT connection & token generation
│   ├── discovery.py        # Discovery config generator & publisher
│   ├── mapping.py          # Discovery event parser & model diff merger
│   ├── service.py          # Butler ingestion service
│   ├── dispatcher.py       # Message router & handler registry
│   └── handlers/           # Specialized database payload handlers
│       ├── base.py
│       ├── pcap_upload.py
│       ├── pointset_events.py
│       ├── point_state.py
│       ├── system_state.py
│       ├── discovery.py
│       ├── validation.py
│       ├── alarms.py
│       ├── metadata.py
│       └── raw_fallback.py
├── tests/                  # Unit and integration test suites
│   ├── test_dispatcher.py
│   └── test_handlers.py
├── SCHEMAS.md              # Database schemas (PostgreSQL & InfluxDB)
└── README.md
```

---

## CLI Usage

### 1. Ingestion Service (Daemon & Stdin)

Run as standalone ingestion daemon connecting to MQTT:
```bash
bin/butler sites/udmi_site_model //mqtt/localhost:46432
```

Start/stop as background daemon via `bin/start_butler`:
```bash
bin/start_butler sites/udmi_site_model //mqtt/localhost:46432
bin/start_butler stop
```

Stream messages from stdin:
```bash
cat messages.jsonl | bin/butler --stdin
bin/pull_files tests/traces/simple | bin/butler --stdin --always-save-raw
```

### 2. Discovery & Mapping

Trigger device discovery for a gateway:
```bash
bin/mapper sites/udmi_site_model //mqtt/localhost:46432 GAT-1 discover bacnet
```

Execute mapping reconciliation against database discovery events:
```bash
bin/mapper sites/udmi_site_model //mqtt/localhost:46432 GAT-1 map
```

### 3. PCAP Upload Reassembly & MCP Trace Tools

Butler's `PcapUploadHandler` (`butler/src/handlers/pcap_upload.py`) subscribes to `upload/discovery` (`DiscoveryUpload`), reassembles gzip-compressed chunks `1..N` in order, verifies the mandatory `sha256` digest on the terminal `-(N+1)` EOF marker, and archives the `.pcap` and `scan_manifest.json` without writing raw chunk blobs to `udmi_messages`.

- **Environment Variables**:
  - `BUTLER_PCAP_DIR`: Local directory for reassembled `.pcap` and `scan_manifest.json` files (default: `out/pcap`).
  - `BUTLER_PCAP_GCS_BUCKET`: Optional Google Cloud Storage bucket name (`gs://<bucket>/<site>/<generation>.pcap`).
  - `BUTLER_CONN_SPEC`: Default UDMI connection specification (e.g. `//mqtt/localhost:46432`) used by the Butler MCP server when triggering remote traces.
- **MCP Tools (`mcp/butler/`)**:
  - `trigger_bacnet_trace`: Publishes a `depth: "trace"` `config/discovery` trigger for `families.bacnet`, records a `PENDING` capture entry in `udmi_pcap_captures`, and returns the target `.pcap` and `scan_manifest.json` URIs.
  - `get_pcap_capture`: Queries `udmi_pcap_captures` for chunk reassembly progress (`PENDING`, `RECEIVING`, `COMPLETED`, `FAILED`), verified `sha256`, `scan_manifest`, and resolved URIs.

---

## Testing

Run unit and integration tests:
```bash
bin/test_butler
```
