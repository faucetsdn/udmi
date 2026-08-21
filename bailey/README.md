# Bailey (UDMI Data Ingestion Service)

**Bailey** is a standalone Python service within the UDMI support ecosystem (alongside `butler` and `BAMBI`). Its primary responsibility is consuming messages from the `udmi_target` topic/feed and persisting structured data into InfluxDB and PostgreSQL instances.

---

## Overview & Architecture

Bailey connects to a message broker (MQTT or Google Cloud Pub/Sub) or reads JSON messages from standard input. Incoming messages are inspected and routed by the message dispatcher to specialized handlers based on the message `sub_folder` and `sub_type`.

```mermaid
flowchart TD
    subgraph Input Sources
        MQTT["MQTT Broker (udmi_target/#)"]
        PUBSUB["Cloud Pub/Sub (udmi_target topic)"]
        STDIN["Piped Stdin (JSON envelopes)"]
    end

    subgraph Bailey Core
        SVC["BaileyService (service.py)"]
        DISP["MessageDispatcher (dispatcher.py)"]
    end

    subgraph Handlers
        H_PT["PointsetEventsHandler"]
        H_PS["PointStateHandler"]
        H_SS["SystemStateHandler"]
        H_DISC["DiscoveryHandler"]
        H_VAL["ValidationHandler"]
        H_ALARM["AlarmsHandler"]
        H_META["MetadataHandler"]
        H_RAW["RawFallbackHandler"]
    end

    subgraph Storage
        INFLUX[("InfluxDB (point_value)")]
        PG[("PostgreSQL")]
    end

    Input Sources --> SVC
    SVC --> DISP
    DISP -->|events/pointset| H_PT
    DISP -->|state/pointset| H_PS
    DISP -->|state/system| H_SS
    DISP -->|events/discovery or state/discovery| H_DISC
    DISP -->|events/validation| H_VAL
    DISP -->|events/alarm| H_ALARM
    DISP -->|metadata| H_META
    DISP -->|fallback / unhandled| H_RAW

    H_PT --> INFLUX
    H_PS -->|udmi_point_state| PG
    H_SS -->|udmi_system_state| PG
    H_DISC -->|udmi_discovery| PG
    H_VAL -->|udmi_validation| PG
    H_ALARM -->|udmi_alarms| PG
    H_META -->|udmi_metadata| PG
    H_RAW -->|udmi_messages| PG
```

---

## Database Mappings

### InfluxDB
* **Measurement**: `point_value`
* **Tags**: `site_id`, `device_id`, `point_name`, `units`
* **Fields**:
  * `present_value_num` (float): Stored when present value is numeric.
  * `present_value_str` (string): Stored when present value is non-numeric/string.
  * `present_value_bool` (boolean): Stored when present value is boolean.
  * `value` (float): Numeric representation for metric visualization.
  * `status` (string): Point status string/JSON.

### PostgreSQL Tables
Tables are automatically initialized upon startup if they do not exist:

| Table | Description | Key Columns |
| :--- | :--- | :--- |
| `udmi_messages` | Raw message capture fallback | `timestamp`, `sub_folder`, `sub_type`, `device_id`, `payload` (JSONB) |
| `udmi_point_state` | Point-level configuration and state | `timestamp`, `device_id`, `point_name`, `units`, `state` (JSONB) |
| `udmi_system_state` | System/device status and metadata | `timestamp`, `device_id`, `make_model`, `firmware`, `status` (JSONB) |
| `udmi_discovery` | Discovered device scans and points | `timestamp`, `device_id`, `generation`, `discovery` (JSONB) |
| `udmi_validation` | Validator compliance results | `timestamp`, `device_id`, `sub_folder`, `status`, `summary` (JSONB) |
| `udmi_alarms` | System alarm events | `timestamp`, `device_id`, `alarm_id`, `severity`, `alarm_data` (JSONB) |
| `udmi_metadata` | Device metadata updates | `timestamp`, `device_id`, `metadata` (JSONB) |

---

## Local Development & Running Bailey

### 1. Prerequisites & Environment Setup
Ensure the Python virtual environment and dependencies are installed:
```bash
bin/setup_base
```

The shared Python libraries reside under [`common/src/main/python`](../common/src/main/python) and generated schemas under [`gencode/python`](../gencode/python).

### 2. Starting Local Services
To run Bailey against local instances of MQTT, InfluxDB, and PostgreSQL, start local services in isolated mode (e.g., using port `46432`):
```bash
bin/start_local sites/udmi_site_model //mqtt/localhost:46432
```
This automatically initializes:
* MQTT Broker on port `46432`
* InfluxDB on port `46434` (org: `bridgehead`, bucket: `home`, token: `test-influx-token-12345`)
* PostgreSQL on port `46435` (database: `udmi`, user: `$USER`)

### 3. Running Bailey CLI

Bailey can be run using `bin/bailey` (or `bailey/bin/bailey`):

#### Continuous Subscription Mode:
Subscribes to the message broker and processes messages in real time:
```bash
bin/bailey sites/udmi_site_model //mqtt/localhost:46432
```

#### Piped Stdin Ingestion:
Processes newline-delimited JSON envelopes or telemetry messages from standard input:
```bash
cat messages.jsonl | bin/bailey --stdin sites/udmi_site_model //mqtt/localhost:46432
```

#### Always Capture Raw Messages:
To persist all envelopes to `udmi_messages` in addition to specialized tables:
```bash
bin/bailey --always-save-raw sites/udmi_site_model //mqtt/localhost:46432
```

---

## Environment Variables & Configuration

Bailey respects standard database environment variables:

| Variable | Default | Description |
| :--- | :--- | :--- |
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_PORT` | `5432` (or calculated from project spec) | PostgreSQL port |
| `POSTGRES_DB` | `udmi` | PostgreSQL database name |
| `POSTGRES_USER` | Current user (`$USER`) | PostgreSQL username |
| `POSTGRES_PASSWORD` | empty | PostgreSQL password |
| `INFLUX_HOST` | `localhost` | InfluxDB host |
| `INFLUX_PORT` | `8086` (or calculated from project spec) | InfluxDB port |
| `INFLUX_ORG` | `bridgehead` | InfluxDB organization |
| `INFLUX_BUCKET` | `home` | InfluxDB bucket |
| `INFLUX_TOKEN` | `test-influx-token-12345` | InfluxDB API access token |

---

## Testing & Verification

### Running Bailey Unit Tests
```bash
pytest bailey/tests/
```

### Running Bailey End-to-End Test Suite
```bash
bin/test_bailey //mqtt/localhost:46432
```

### Inspecting Ingested Data
To verify database tables and record distribution:
```bash
bin/db_histogram //mqtt/localhost:46432
```

---

## Directory Structure

```
bailey/
├── bin/
│   └── bailey              # CLI executable wrapper
├── src/
│   ├── __init__.py
│   ├── service.py          # BaileyService runner (MQTT/PubSub & Stdin loops)
│   ├── dispatcher.py       # MessageDispatcher routing logic
│   └── handlers/
│       ├── __init__.py
│       ├── base.py         # BaseHandler abstract class
│       ├── pointset_events.py # InfluxDB telemetry point ingestion
│       ├── point_state.py  # udmi_point_state table handler
│       ├── system_state.py # udmi_system_state table handler
│       ├── discovery.py    # udmi_discovery table handler
│       ├── validation.py   # udmi_validation table handler
│       ├── alarms.py       # udmi_alarms table handler
│       ├── metadata.py     # udmi_metadata table handler
│       └── raw_fallback.py # udmi_messages raw capture handler
└── tests/
    ├── __init__.py
    ├── test_dispatcher.py  # Unit tests for dispatcher routing
    └── test_handlers.py    # Unit tests for individual DB handlers
```
