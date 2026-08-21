# GUMMI Backend Server Technical Specification (`SERVER.md`)

## Overview

This specification details the technical architecture, data models, protocol interactions, background lifecycle, and HTTP REST / SSE API contracts for the **GUMMI (Glorious Unified Methodical Management Interface)** backend server.

GUMMI operates as the primary management plane interface over the UDMI ecosystem, bridging the web-based user interface ([gummi/GUMMI.md](file:///home/peringknife/udmi/gummi/GUMMI.md)) with UDMI messaging infrastructure ([docs/specs/uufi.md](file:///home/peringknife/udmi/docs/specs/uufi.md)), PostgreSQL snapshot projections, and InfluxDB time-series telemetry.

---

## 1. System Architecture & Component Roles

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Web Browser (Vanilla JS)                         │
└───────────────────▲─────────────────────────────────┬───────────────────┘
                    │                                 │
     SSE Events /   │                                 │ REST API Queries
     Live Stream    │                                 │ & Declarations
                    │                                 ▼
┌───────────────────┴─────────────────────────────────────────────────────┐
│                       GUMMI Flask Web Server                            │
│  ┌─────────────────────────────────┐   ┌─────────────────────────────┐  │
│  │     REST API Endpoints          │   │      SSE Broadcaster        │  │
│  │ (Auth / Validation / Dispatch)  │   │  (Alerts / State / Rollout) │  │
│  └────────────────┬────────────────┘   └──────────────▲──────────────┘  │
│                   │                                   │                 │
│                   ▼                                   │                 │
│  ┌────────────────────────────────────────────────────┴──────────────┐  │
│  │                 Embedded UUFI Ingestion Engine                    │  │
│  │  - Layer 1 Handshake Daemon                                       │  │
│  │  - MQTT / PubSub Subscriber & Dispatcher                          │  │
│  │  - Snapshot Projections & Convergence Calculator                  │  │
│  │  - Rollout Dispatch Worker                                        │  │
│  └────────────────┬───────────────────────────────────▲──────────────┘  │
└───────────────────┼───────────────────────────────────┼─────────────────┘
                    │ Read/Write                        │ Publish/Subscribe
                    ▼                                   ▼
┌───────────────────────────────────┐   ┌─────────────────────────────────┐
│         Storage Layer             │   │       UDMI Bus (UUFI)           │
│  - PostgreSQL (Snapshot/Audit)    │   │  - Mosquitto Broker / PubSub    │
│  - InfluxDB (Point Telemetry)     │   │  - UDMIS / Reflector / Devices  │
└───────────────────────────────────┘   └─────────────────────────────────┘
```

### 1.1 Process & Concurrency Model
The GUMMI backend server operates as an integrated service process containing two primary execution tiers:
1. **Web Serving Tier (Flask / WSGI)**: Handles incoming HTTP requests, performs request authentication/identification, validates schemas against authoritative definitions in `schema/`, queries PostgreSQL and InfluxDB for read requests, and enqueues commands to the UUFI dispatcher.
2. **UUFI Ingestion & Management Tier (Background Thread / Async Loop)**:
   - Maintains a single, persistent MQTT or PubSub connection with an active Layer 1 Service Handshake.
   - Subscribes to configured topic namespaces (`/uufi/r/+/d/+/c/#`).
   - Projects incoming state, model, and event payloads into relational snapshot tables in PostgreSQL.
   - Pushes live updates into an internal in-memory pub-sub queue for distribution across active SSE connections.
   - Advances active staged rollouts by tracking target vs. reported state convergence.

---

## 2. Data Store & Projection Architecture

To support instantaneous querying, faceted filtering (make, model, software version, status), and $O(1)$ server-side pagination across 100,000+ devices without scanning raw JSON logs, GUMMI maintains dedicated relational projection tables in PostgreSQL alongside raw message logs.

### 2.1 Entity Relationship Diagram

```
       ┌────────────────────────┐
       │      registries        │
       │────────────────────────│
       │ PK registry_id         │
       │    project_id          │
       │    site_id             │
       └───────────┬────────────┘
                   │ 1
                   │
                   │ *
       ┌───────────▼────────────┐       1 ┌─────────────────────────────┐
       │        devices         ├─────────┤    device_state_snapshots   │
       │────────────────────────│         │─────────────────────────────│
       │ PK id                  │         │ PK id                       │
       │ FK registry_id         │         │ FK device_id                │
       │    device_id           │         │    sub_folder               │
       │    make                │         │    payload (JSONB)          │
       │    model               │         │    last_updated             │
       │    software_version    │         └─────────────────────────────┘
       │    liveness_status     │
       │    last_seen           │       1 ┌─────────────────────────────┐
       │    status_summary      ├─────────┤    device_config_snapshots  │
       └───────────┬────────────┘         │─────────────────────────────│
                   │                      │ PK id                       │
                   │ 1                    │ FK device_id                │
                   │                      │    sub_folder               │
                   │ *                    │    desired_payload (JSONB)  │
       ┌───────────▼────────────┐         │    applied_payload (JSONB)  │
       │    events_status_log   │         │    version                  │
       │────────────────────────│         └─────────────────────────────┘
       │ PK id                  │
       │ FK device_id           │
       │    level               │
       │    category            │
       │    message             │
       │    timestamp           │
       └────────────────────────┘
```

### 2.2 PostgreSQL Table Definitions (SQLAlchemy / DDL)

#### `devices`
Represents the current flattened catalog and summary state of each discovered device.
```sql
CREATE TABLE devices (
    id SERIAL PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    registry_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    site_id VARCHAR(255),
    make VARCHAR(255) DEFAULT 'unknown',
    model VARCHAR(255) DEFAULT 'unknown',
    firmware_version VARCHAR(255),
    software_version VARCHAR(255),
    software_subsystems JSONB DEFAULT '{}'::jsonb,
    liveness_status VARCHAR(50) DEFAULT 'OFFLINE', -- 'ONLINE', 'OFFLINE', 'ERROR'
    last_seen TIMESTAMP WITH TIME ZONE,
    status_summary JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_registry UNIQUE (registry_id, device_id)
);

CREATE INDEX idx_devices_filter ON devices (registry_id, make, model, liveness_status);
CREATE INDEX idx_devices_last_seen ON devices (last_seen);
CREATE INDEX idx_devices_software ON devices USING GIN (software_subsystems);
```

#### `device_state_snapshots`
Stores the latest parsed UDMI state reports per subfolder for each device.
```sql
CREATE TABLE device_state_snapshots (
    id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    sub_folder VARCHAR(50) NOT NULL, -- 'system', 'pointset', 'blobset', 'gateway', 'discovery'
    payload JSONB NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_state_subfolder UNIQUE (device_id, sub_folder)
);
```

#### `device_config_snapshots`
Stores the declared desired configuration and acknowledged reported configuration per subfolder.
```sql
CREATE TABLE device_config_snapshots (
    id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    sub_folder VARCHAR(50) NOT NULL,
    desired_payload JSONB NOT NULL,
    applied_payload JSONB,
    version VARCHAR(50),
    last_modified_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_config_subfolder UNIQUE (device_id, sub_folder)
);
```

#### `events_status_log`
Chronological operational and error log entries captured from `events/status`.
```sql
CREATE TABLE events_status_log (
    id SERIAL PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    registry_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    level INTEGER NOT NULL, -- Level integer corresponding to UDMI schema/entry.json
    category VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    detail TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_status_device ON events_status_log (registry_id, device_id, timestamp DESC);
CREATE INDEX idx_status_level ON events_status_log (level, timestamp DESC);
```

#### `rollouts` & `rollout_device_status`
Manages declarative staged rollouts and records convergence state across targeted devices.
```sql
CREATE TABLE rollouts (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    target_filter JSONB NOT NULL, -- e.g. {"make": "Acme", "registry_id": "site-1"}
    target_subfolder VARCHAR(50) NOT NULL DEFAULT 'system',
    target_payload JSONB NOT NULL,
    status VARCHAR(50) DEFAULT 'DRAFT', -- 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED'
    batch_size INTEGER NOT NULL DEFAULT 10,
    batch_interval_sec INTEGER NOT NULL DEFAULT 60,
    total_devices INTEGER DEFAULT 0,
    converged_devices INTEGER DEFAULT 0,
    failed_devices INTEGER DEFAULT 0,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rollout_device_status (
    id SERIAL PRIMARY KEY,
    rollout_id INTEGER NOT NULL REFERENCES rollouts(id) ON DELETE CASCADE,
    registry_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    stage_number INTEGER NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING', -- 'PENDING', 'DISPATCHED', 'CONVERGED', 'FAILED'
    dispatched_at TIMESTAMP WITH TIME ZONE,
    converged_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    CONSTRAINT uq_rollout_device UNIQUE (rollout_id, registry_id, device_id)
);
```

#### `operator_audit_log`
Tracks actions performed by human operators.
```sql
CREATE TABLE operator_audit_log (
    id SERIAL PRIMARY KEY,
    operator_email VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL, -- e.g., 'CONFIG_UPDATE', 'ROLLOUT_CREATE', 'ROLLOUT_CANCEL'
    target_type VARCHAR(50) NOT NULL, -- 'DEVICE', 'ROLLOUT', 'SITE'
    target_id VARCHAR(255) NOT NULL,
    payload JSONB,
    ip_address VARCHAR(100),
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

---

## 3. UUFI Protocol & Lifecycle Engine

The GUMMI server acts as an application-level UUFI Client adhering to the constraints of [docs/specs/uufi.md](file:///home/peringknife/udmi/docs/specs/uufi.md).

### 3.1 Connection Specification
* **Connection String Format**: `scheme://[user@]host[:port][/path]`
  * Supported schemes: `mqtt://`, `mqtts://`, `pubsub://`.
* **Static Service Principal**: Outgoing message envelopes utilize the configured system principal:
  * Format: `gummi.service@{prefix}`
* **MQTT Client ID Format**: `[/{prefix}]/{client_id}_{nonce}` to ensure broker connection stability and prevent session hijacking.

### 3.2 Layer 1 Service Handshake Protocol
Upon establishing connection to the broker or on reconnect:
1. GUMMI generates a unique UUID `handshake_tx_id`.
2. GUMMI publishes a UDMI `state` handshake request to:
   `[/{prefix}]/uufi/c/state/udmi`
   * Envelope: `transactionId: handshake_tx_id`, `source: "gummi"`, `principal: "gummi.service@"`
   * Payload: `{"version": "1.5.2", "timestamp": "...", "setup": {"transaction_id": handshake_tx_id, "functions_ver": 2}}`
3. GUMMI subscribes to `[/{prefix}]/uufi/c/config/udmi`.
4. The system validates the incoming configuration reply envelope:
   * Discards any reply where `reply.transaction_id != handshake_tx_id`.
   * On match, marks the UUFI interface as `ACTIVE` and begins operational subscriptions.
5. If no valid reply is received within 5 seconds, retries publish up to the 60-second timeout threshold.

```
 [ GUMMI Server ]                                                 [ UDMIS / System ]
        │                                                                 │
        │── (1) Handshake State (/uufi/c/state/udmi) ────────────────────►│
        │       Envelope: transactionId = "TX-101", principal = "gummi@"  │
        │       Payload: { setup: { transaction_id: "TX-101" } }          │
        │                                                                 │
        │◄─ (2) Handshake Reply (/uufi/c/config/udmi) ────────────────────│
        │       Envelope: transactionId = "TX-101", source = "udmis"      │
        │       Payload: { reply: { transaction_id: "TX-101" } }          │
        ▼                                                                 ▼
   [ Activated ] ── Subscribes to Device Channels (/uufi/r/+/d/+/c/#)
```

### 3.3 Message Routing and Ingestion Handlers
The ingestion engine subscribes to `[/{prefix}]/uufi/r/+/d/+/c/#` and processes inbound topics:

| Inbound Topic Pattern | Message SubType / SubFolder | DB Action & Projection Strategy |
| :--- | :--- | :--- |
| `.../c/state/{subFolder}` | `state` / `{subFolder}` | 1. Upsert `devices.last_seen`.<br>2. Upsert `device_state_snapshots`.<br>3. If `subFolder == 'system'`, update `firmware_version`, `software_version`, and `software_subsystems`.<br>4. Trigger rollout convergence check.<br>5. Broadcast state diff over SSE. |
| `.../c/events/status` | `events` / `status` | 1. Insert into `events_status_log`.<br>2. If level $\ge$ WARNING (500), update `devices.liveness_status = 'ERROR'`.<br>3. Broadcast alert to SSE `/api/stream/events`. |
| `.../c/events/pointset` | `events` / `pointset` | 1. Update `devices.last_seen`.<br>2. InfluxDB capture processor handles raw telemetry metrics. |
| `.../c/events/{other}` | `events` / `{other}` | 1. Update `devices.last_seen` timestamp.<br>2. Upsert device entry if unseen. |
| `.../c/model/system` | `model` / `system` | 1. Update `devices.make`, `devices.model`, and baseline metadata. |

### 3.4 Liveness State Evaluation
Device liveness status is dynamically computed based on `last_seen` and recent error states:
- **`ONLINE`**: `last_seen` $\ge$ `(NOW() - interval)` (default threshold: 300 seconds) AND no active status entry with level $\ge$ 500 within the last 15 minutes.
- **`ERROR`**: Active status error entry with level $\ge$ 500 within the last 15 minutes.
- **`OFFLINE`**: `last_seen` < `(NOW() - interval)` or `last_seen IS NULL`.

---

## 4. Configuration Mutation & Rollout Engine

### 4.1 Configuration Update Workflow
When an operator saves a configuration change for a device:
1. **Schema Validation**: Server validates payload against authoritative schemas in `schema/` (e.g., [schema/config_system.json](file:///home/peringknife/udmi/schema/config_system.json), [schema/config_pointset.json](file:///home/peringknife/udmi/schema/config_pointset.json)) using Python `jsonschema`.
2. **Desired State Record**: Server updates `device_config_snapshots.desired_payload` and increments/stamps the version.
3. **Audit Entry**: Server logs the modification to `operator_audit_log`.
4. **UUFI Dispatch**: Server publishes the configuration message to:
   `[/{prefix}]/uufi/r/{registryId}/d/{deviceId}/c/config/{subFolder}`
   * Envelope includes: `projectId`, `deviceRegistryId`, `deviceId`, `subType: "config"`, `subFolder`, `transactionId: <uuid>`, `principal: "gummi.service@"`.

### 4.2 Managed Rollout Execution
1. **Creation**: Operator selects target devices (via filter query or explicit list) and target configuration.
2. **Staging**: Rollout worker groups devices into batches according to `batch_size`.
3. **Batch Dispatch**:
   - For batch $N$, worker iterates target devices, publishes config updates via UUFI, and marks `rollout_device_status.status = 'DISPATCHED'`.
4. **Convergence Monitoring**:
   - As incoming `state/{subFolder}` messages arrive for target devices, the ingestion engine compares reported state against target config (e.g. `state.system.software[subsystem] == config.system.software[subsystem]`).
   - On match, sets `rollout_device_status.status = 'CONVERGED'` and increments `rollouts.converged_devices`.
5. **Progression**:
   - Worker evaluates whether batch $N$ has achieved required convergence threshold before advancing to batch $N+1$ after `batch_interval_sec`.
   - If error rate exceeds safety threshold, sets `rollout.status = 'PAUSED'` and emits an SSE alert.

---

## 5. InfluxDB Telemetry Integration

GUMMI uses the official `influxdb-client-python` to query historical point data ingested by `CaptureProcessor`.

### 5.1 Measurement Layout
* **Measurement**: `point_value`
* **Tags**: `device_id`, `registry_id`, `project_id`, `point_name`
* **Fields**: `present_value_num` (float), `present_value_bool` (boolean), `present_value_str` (string)

### 5.2 Flux Query Patterns
The server issues parameterized Flux queries:
```flux
from(bucket: "home")
  |> range(start: -1h)
  |> filter(fn: (r) => r["_measurement"] == "point_value")
  |> filter(fn: (r) => r["device_id"] == "AHU-1" and r["registry_id"] == "site-reg")
  |> filter(fn: (r) => r["point_name"] == "supply_air_temperature_sensor")
  |> aggregateWindow(every: 1m, fn: mean, createEmpty: false)
  |> yield(name: "mean")
```

---

## 6. Authentication & Environment Adaptation

### 6.1 Authentication Modes
* **Identity-Aware Proxy (`IAP`)**:
  * Resolves user identity from incoming request headers:
    * `X-Goog-Authenticated-User-Email` (stripped of prefix `accounts.google.com:`)
    * `X-Goog-Authenticated-User-Id`
* **No Authentication (`NO_AUTH`)**:
  * Fallback to configured default identity: `operator@localhost`.
* **Audit Binding**: Regardless of auth mode, the resolved user identity is persisted into `operator_audit_log` on all state mutation endpoints.

### 6.2 Environment Capability API
The server dynamically probes local vs. remote components to declare available system capabilities:
* Local port/socket probes:
  * Mosquitto MQTT: TCP connection check to port 1883 / 8883 / configured port.
  * PostgreSQL: `SELECT 1;`
  * InfluxDB: `GET http://host:port/ping`
  * etcd: `GET http://host:port/health`
* When running in cloud mode where local component probing is disabled, the server reports environment mode as `CLOUD` and flags Bridgehead administration capabilities as `EXTERNAL_MANAGED`.

---

## 7. HTTP REST & SSE API Endpoint Contracts

### 7.1 System & Capability

#### `GET /api/system/capabilities`
Returns operational mode, authentication mode, and active feature sets.
* **Response `200 OK`**:
  ```json
  {
    "environment": "LOCAL_BRIDGEHEAD",
    "auth_mode": "IAP",
    "uufi_status": "ACTIVE",
    "features": [
      "portfolio",
      "devices",
      "config_management",
      "managed_rollout",
      "bridgehead_admin"
    ]
  }
  ```

#### `GET /api/bridgehead/status`
Returns real-time health checks for infrastructure components.
* **Response `200 OK`**:
  ```json
  {
    "overall_status": "HEALTHY",
    "timestamp": "2026-08-21T12:00:00Z",
    "components": {
      "mqtt_broker": {"status": "UP", "endpoint": "localhost:1883", "latency_ms": 2},
      "postgres": {"status": "UP", "endpoint": "localhost:5432", "latency_ms": 1},
      "influxdb": {"status": "UP", "endpoint": "localhost:8086", "latency_ms": 4},
      "etcd": {"status": "UP", "endpoint": "localhost:2379", "latency_ms": 3}
    }
  }
  ```

---

### 7.2 Portfolio Overview

#### `GET /api/portfolio/summary`
Aggregate status counts across the fleet.
* **Response `200 OK`**:
  ```json
  {
    "device_counts": {
      "total": 1250,
      "online": 1180,
      "offline": 55,
      "error": 15
    },
    "registries_count": 4,
    "active_rollouts_count": 1,
    "critical_alerts_24h": 6
  }
  ```

#### `GET /api/portfolio/alerts`
Retrieves prioritized fleet alerts.
* **Query Parameters**:
  * `limit` *(int, default 50)*
  * `min_level` *(int, default 500)*
* **Response `200 OK`**:
  ```json
  {
    "alerts": [
      {
        "id": 8492,
        "registry_id": "site-floor1",
        "device_id": "AHU-102",
        "level": 600,
        "category": "system.hardware",
        "message": "Temperature sensor communication failure",
        "timestamp": "2026-08-21T11:45:10Z"
      }
    ]
  }
  ```

---

### 7.3 Devices Explorer

#### `GET /api/devices`
Fetches a paginated, filtered grid of fleet devices.
* **Query Parameters**:
  * `limit` *(int, default 100)*: Result page size.
  * `offset` *(int, default 0)*: Result offset.
  * `registry_id` *(string, optional)*: Match exact registry.
  * `device_prefix` *(string, optional)*: Match device name prefix (e.g. `AHU-`).
  * `make` *(string, optional)*: Match hardware make.
  * `model` *(string, optional)*: Match hardware model.
  * `status` *(string, optional)*: Filter by `ONLINE`, `OFFLINE`, `ERROR`.
  * `search` *(string, optional)*: Full-text substring search across device IDs and make/model.
* **Response `200 OK`**:
  ```json
  {
    "total": 1250,
    "limit": 100,
    "offset": 0,
    "devices": [
      {
        "id": 142,
        "registry_id": "site-floor1",
        "device_id": "AHU-1",
        "make": "Acme Corp",
        "model": "HVAC-3000",
        "firmware_version": "2.4.1",
        "liveness_status": "ONLINE",
        "last_seen": "2026-08-21T11:58:32Z"
      }
    ]
  }
  ```

---

### 7.4 Device Properties & Telemetry

#### `GET /api/devices/{registry_id}/{device_id}`
Returns complete details and latest state snapshot for a single device.
* **Response `200 OK`**:
  ```json
  {
    "metadata": {
      "registry_id": "site-floor1",
      "device_id": "AHU-1",
      "make": "Acme Corp",
      "model": "HVAC-3000",
      "firmware_version": "2.4.1",
      "software_subsystems": {"system": "2.4.1", "hvac_app": "1.0.8"},
      "last_seen": "2026-08-21T11:58:32Z",
      "liveness_status": "ONLINE"
    },
    "state": {
      "system": {
        "last_config": "2026-08-20T10:00:00Z",
        "operational": true,
        "software": {"system": "2.4.1"}
      },
      "pointset": {
        "points": {
          "fan_speed": {"status": "applied", "value": 75}
        }
      }
    },
    "config": {
      "system": {
        "software": {"system": "2.4.1"}
      }
    }
  }
  ```

#### `GET /api/devices/{registry_id}/{device_id}/telemetry`
Queries InfluxDB for time-series point values.
* **Query Parameters**:
  * `points` *(string, required)*: Comma-separated point names (e.g. `supply_temp,return_temp`).
  * `start` *(string, default "-1h")*: Flux duration or RFC3339 start timestamp.
  * `stop` *(string, default "now()")*: Flux duration or RFC3339 end timestamp.
  * `window` *(string, default "1m")*: Aggregation window.
* **Response `200 OK`**:
  ```json
  {
    "device_id": "AHU-1",
    "registry_id": "site-floor1",
    "series": [
      {
        "point_name": "supply_temp",
        "values": [
          {"time": "2026-08-21T11:00:00Z", "value": 72.4},
          {"time": "2026-08-21T11:01:00Z", "value": 72.5}
        ]
      }
    ]
  }
  ```

---

### 7.5 Configuration Mutation

#### `POST /api/devices/{registry_id}/{device_id}/config`
Declares desired configuration for a device.
* **Request Payload**:
  ```json
  {
    "sub_folder": "system",
    "payload": {
      "system": {
        "software": {
          "system": "2.5.0"
        }
      }
    }
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "status": "DISPATCHED",
    "transaction_id": "9f32b842-2b63-4c9b-8bc3-3b2d1847c0e1",
    "message": "Configuration successfully published to UUFI bus."
  }
  ```
* **Response `400 Bad Request`** (Schema validation failure):
  ```json
  {
    "error": "SCHEMA_VALIDATION_ERROR",
    "details": "Payload failed schema validation: 'software' is required"
  }
  ```

---

### 7.6 Managed Rollouts

#### `GET /api/rollouts`
Returns a list of all staged rollout campaigns.
* **Response `200 OK`**:
  ```json
  [
    {
      "id": 12,
      "name": "Upgrade HVAC v2.5.0",
      "status": "RUNNING",
      "total_devices": 150,
      "converged_devices": 95,
      "failed_devices": 2,
      "batch_size": 25,
      "created_at": "2026-08-21T09:30:00Z"
    }
  ]
  ```

#### `POST /api/rollouts`
Defines and starts a new rollout campaign.
* **Request Payload**:
  ```json
  {
    "name": "Upgrade HVAC v2.5.0",
    "target_filter": {
      "make": "Acme Corp",
      "model": "HVAC-3000"
    },
    "target_subfolder": "system",
    "target_payload": {
      "system": {
        "software": {
          "system": "2.5.0"
        }
      }
    },
    "batch_size": 25,
    "batch_interval_sec": 120
  }
  ```
* **Response `201 Created`**:
  ```json
  {
    "rollout_id": 12,
    "matched_devices": 150,
    "status": "RUNNING"
  }
  ```

#### `POST /api/rollouts/{rollout_id}/pause`
Pauses rollout dispatch.

#### `POST /api/rollouts/{rollout_id}/cancel`
Cancels rollout execution.

---

### 7.7 Real-Time Event Streaming

#### `GET /api/stream/events`
Server-Sent Events (SSE) stream broadcasting live fleet events.
* **Headers**: `Content-Type: text/event-stream`, `Cache-Control: no-cache`
* **Event Types**:
  * `device_state`:
    ```json
    event: device_state
    data: {"registry_id": "site-1", "device_id": "AHU-1", "sub_folder": "system", "liveness": "ONLINE"}
    ```
  * `alert`:
    ```json
    event: alert
    data: {"registry_id": "site-1", "device_id": "AHU-1", "level": 600, "message": "Motor overtemp"}
    ```
  * `rollout_progress`:
    ```json
    event: rollout_progress
    data: {"rollout_id": 12, "converged": 96, "total": 150, "status": "RUNNING"}
    ```
