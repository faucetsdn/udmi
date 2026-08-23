# Butler Database Schemas (PostgreSQL & InfluxDB)

This document specifies the database schemas, connection parameters, and access patterns for the **PostgreSQL** relational database and the **InfluxDB** time-series database used by Butler and UDMI services.

---

## 1. Connection Configurations & Defaults

### PostgreSQL (Relational Store)
| Parameter | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **Host** | `POSTGRES_HOST` | `127.0.0.1` | PostgreSQL database host |
| **Port** | `POSTGRES_PORT` | `5432` | PostgreSQL TCP port (assigned dynamically in isolated test instances) |
| **User** | `POSTGRES_USER` | `postgres` | Database username (`$USER` in unprivileged local setups) |
| **Password** | `POSTGRES_PASSWORD` | `""` (empty) | Database password |
| **Database** | `POSTGRES_DB` | `postgres` | Default database name |

### InfluxDB v2 (Time-Series Store)
| Parameter | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **URL** | `INFLUXDB_URL` | `http://127.0.0.1:8086` | InfluxDB HTTP endpoint |
| **Port** | `INFLUX_PORT` / `INFLUXDB_PORT` | `8086` | InfluxDB TCP port |
| **Organization** | `INFLUXDB_ORG` | `bridgehead` | InfluxDB organization name |
| **Bucket** | `INFLUXDB_BUCKET` | `home` | Target bucket for telemetry metrics |
| **Token** | `INFLUXDB_TOKEN` | `test-influx-token-12345` | InfluxDB API access token |

---

## 2. PostgreSQL Relational Schemas

PostgreSQL stores structured state records, discovery scan events, validation reports, alarms, site metadata, and raw fallback payloads.

### Table: `udmi_messages` (Raw Fallback & Ingestion Audit)
Stores unhandled or raw UDMI envelopes and payloads as JSONB for audit and fallback replay.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `SERIAL PRIMARY KEY` | Auto-incrementing row ID |
| `project_id` | `VARCHAR(255)` | GCP/UDMI project identifier |
| `registry_id` | `VARCHAR(255)` | IoT device registry identifier |
| `device_id` | `VARCHAR(255)` | Unique device identifier |
| `sub_folder` | `VARCHAR(50)` | UDMI subFolder (e.g. `pointset`, `system`, `discovery`) |
| `sub_type` | `VARCHAR(50)` | UDMI subType (e.g. `events`, `state`, `config`, `model`) |
| `publish_time` | `TIMESTAMP` | Message publish timestamp from envelope |
| `payload` | `JSONB` | Complete raw JSON payload |
| `created_at` | `TIMESTAMP` | Local ingestion timestamp (`DEFAULT CURRENT_TIMESTAMP`) |

---

### Table: `udmi_point_state` (Pointset State)
Tracks current operational status and value states of telemetry points per device.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `SERIAL PRIMARY KEY` | Auto-incrementing row ID |
| `timestamp` | `TIMESTAMP` | State message generation timestamp |
| `device_id` | `VARCHAR(255)` | Target device ID |
| `device_registry_id` | `VARCHAR(255)` | Device registry ID |
| `message_id` | `VARCHAR(255)` | Optional transaction or message ID |
| `point_name` | `VARCHAR(255)` | Point name (e.g. `filter_alarm_status`, `supply_air_temperature_sensor`) |
| `value_state` | `VARCHAR(50)` | State categorization (e.g. `applied`, `updating`, `invalid`, `failure`) |
| `units` | `VARCHAR(50)` | Engineering units (e.g. `Degrees-Celsius`, `Pascals`) |
| `status_timestamp` | `TIMESTAMP` | Point status entry timestamp |
| `level` | `INTEGER` | Status level (e.g. `100`=DEBUG, `300`=INFO, `500`=WARNING, `800`=ERROR) |
| `category` | `VARCHAR(100)` | Status error/event category |
| `message` | `TEXT` | Human-readable status description |
| `detail` | `TEXT` | Stack trace or detailed diagnostic error message |
| `created_at` | `TIMESTAMP` | Local ingestion timestamp (`DEFAULT CURRENT_TIMESTAMP`) |

---

### Table: `udmi_system_state` (System Block State)
Captures device hardware make/model, serial numbers, firmware/software versions, and gateway relationships.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `SERIAL PRIMARY KEY` | Auto-incrementing row ID |
| `timestamp` | `TIMESTAMP` | State message timestamp |
| `publish_timestamp` | `TIMESTAMP` | Message bus publication timestamp |
| `device_registry_id` | `VARCHAR(255)` | Registry ID |
| `device_id` | `VARCHAR(255)` | Target device ID |
| `device_num_id` | `VARCHAR(255)` | Numerical device ID assigned by cloud provider |
| `gateway_id` | `VARCHAR(255)` | Proxy gateway device ID (if bridged/proxied) |
| `make` | `VARCHAR(255)` | Hardware manufacturer |
| `model` | `VARCHAR(255)` | Hardware model name/number |
| `serial_no` | `VARCHAR(255)` | Manufacturer physical serial number |
| `rev` | `VARCHAR(255)` | Hardware board revision |
| `sku` | `VARCHAR(255)` | Hardware SKU |
| `software` | `JSONB` | Array of software modules: `[{"id": "<name>", "version": "<ver>"}]` |
| `created_at` | `TIMESTAMP` | Local ingestion timestamp (`DEFAULT CURRENT_TIMESTAMP`) |

---

### Table: `udmi_discovery` (Device Discovery Events)
Stores discovered device addresses, BACnet identifiers, IPv4/Ethernet MACs, and port scan enumerations.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `SERIAL PRIMARY KEY` | Auto-incrementing row ID |
| `timestamp` | `TIMESTAMP` | Discovery event timestamp |
| `generation` | `TIMESTAMP` | Discovery scan generation timestamp |
| `device_registry_id` | `VARCHAR(255)` | Registry ID |
| `device_id` | `VARCHAR(255)` | Reporting scanner / gateway device ID |
| `message_id` | `VARCHAR(255)` | Message correlation ID |
| `scan_family` | `VARCHAR(50)` | Address family (e.g. `bacnet`, `ipv4`, `ether`, `vendor`) |
| `ether_addr` | `VARCHAR(255)` | Discovered MAC address (e.g. `00:1a:2b:3c:4d:5e`) |
| `ipv4_addr` | `VARCHAR(255)` | Discovered IP address (e.g. `192.168.1.50`) |
| `bacnet_addr` | `VARCHAR(255)` | Discovered BACnet MSTP/IP ID (e.g. `12345`) |
| `hostname` | `VARCHAR(255)` | Hostname string |
| `fqdn` | `VARCHAR(255)` | Fully qualified domain name |
| `hardware_make` | `VARCHAR(255)` | Discovered device make |
| `hardware_model` | `VARCHAR(255)` | Discovered device model |
| `firmware_version` | `VARCHAR(255)` | Discovered firmware version |
| `serial_no` | `VARCHAR(255)` | Discovered serial number |
| `status_level` | `INTEGER` | Status log level |
| `status_category` | `VARCHAR(100)` | Status category |
| `status_message` | `TEXT` | Status message description |
| `ports` | `JSONB` | Array of open network ports and service banners |
| `created_at` | `TIMESTAMP` | Local ingestion timestamp (`DEFAULT CURRENT_TIMESTAMP`) |

---

### Table: `udmi_validation` (Validation Logs)
Records validation engine errors, schema conformance failures, and sequence verification entries.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `SERIAL PRIMARY KEY` | Auto-incrementing row ID |
| `timestamp` | `TIMESTAMP` | Message timestamp |
| `device_registry_id` | `VARCHAR(255)` | Registry ID |
| `device_id` | `VARCHAR(255)` | Device ID |
| `message_type` | `VARCHAR(100)` | Validated category tag (e.g. `events_pointset`, `state_system`) |
| `message` | `TEXT` | Summary validation error or status message |
| `detail` | `TEXT` | Detailed failure description or stack trace |
| `category` | `VARCHAR(100)` | Schema validation category (e.g. `validation.schema.error`) |
| `level` | `INTEGER` | Severity level (`500`=WARNING, `800`=ERROR) |
| `errors` | `JSONB` | Structured array of validation errors |
| `created_at` | `TIMESTAMP` | Local ingestion timestamp (`DEFAULT CURRENT_TIMESTAMP`) |

---

### Table: `udmi_alarms` (Device Alarms & Faults)
Stores point alarm states, equipment fault conditions, and operational overrides.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `SERIAL PRIMARY KEY` | Auto-incrementing row ID |
| `timestamp` | `TIMESTAMP` | Alarm event timestamp |
| `device_registry_id` | `VARCHAR(255)` | Registry ID |
| `device_id` | `VARCHAR(255)` | Device ID |
| `alarm_category` | `VARCHAR(100)` | Category classification |
| `alarm_priority` | `VARCHAR(50)` | Alarm priority (e.g. `critical`, `high`, `medium`, `low`) |
| `alarm_type` | `VARCHAR(100)` | Type identifier |
| `controller` | `VARCHAR(255)` | Target field controller identifier |
| `equipment` | `VARCHAR(255)` | Associated equipment tag (e.g. `AHU-1`) |
| `fault` | `BOOLEAN` | True if device/equipment is faulted |
| `from_state` | `VARCHAR(50)` | Prior alarm state |
| `to_state` | `VARCHAR(50)` | New alarm state |
| `generation_time` | `TIMESTAMP` | Event generation timestamp |
| `in_alarm` | `BOOLEAN` | True if alarm condition is currently active |
| `location_path` | `VARCHAR(255)` | Hierarchical location path |
| `message_text` | `TEXT` | Alarm message string |
| `out_of_service` | `BOOLEAN` | True if device or point is out of service |
| `overridden` | `BOOLEAN` | True if point has manual operator override |
| `created_at` | `TIMESTAMP` | Local ingestion timestamp (`DEFAULT CURRENT_TIMESTAMP`) |

---

### Table: `udmi_metadata` (Device Metadata & Site Model)
Stores device metadata blocks, physical coordinates, network interface bindings, and connection configurations.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `SERIAL PRIMARY KEY` | Auto-incrementing row ID |
| `timestamp` | `TIMESTAMP` | Metadata extraction or message timestamp |
| `device_id` | `VARCHAR(255)` | Unique device ID |
| `device_registry_id` | `VARCHAR(255)` | Registry ID |
| `system_hardware_make` | `VARCHAR(255)` | Specified hardware make |
| `system_hardware_model` | `VARCHAR(255)` | Specified hardware model |
| `system_hardware_sku` | `VARCHAR(255)` | Hardware SKU |
| `system_hardware_rev` | `VARCHAR(255)` | Hardware revision |
| `system_location_room` | `VARCHAR(255)` | Physical room assignment |
| `system_location_floor` | `VARCHAR(255)` | Physical floor assignment |
| `localnet_families_ipv4_addr` | `VARCHAR(255)` | Static or assigned IPv4 address |
| `localnet_families_ether_addr` | `VARCHAR(255)` | Ethernet MAC address |
| `cloud_connection_type` | `VARCHAR(50)` | Cloud connection mechanism (e.g. `MQTT`, `GATEWAY`) |
| `metadata` | `JSONB` | Full raw `metadata.json` document |
| `created_at` | `TIMESTAMP` | Local ingestion timestamp (`DEFAULT CURRENT_TIMESTAMP`) |

---

## 3. InfluxDB Time-Series Schema

InfluxDB v2 ingests numeric, boolean, and textual point telemetry from `events/pointset` messages.

### Measurement: `point_value`

* **Tags (Indexed)**:
  * `device_id`: Target device ID (e.g. `AHU-1`, `DDC-2`).
  * `registry_id`: Registry identifier (e.g. `ZZ-TRI-FECTA`).
  * `project_id`: Project identifier (e.g. `bos-platform-dev`).
  * `point_name`: Point telemetry key (e.g. `supply_air_temperature_sensor`, `filter_alarm_status`).

* **Fields (Values)**:
  * `present_value_num` (`float`): Ingested when `present_value` is numeric (`float` or `int`).
  * `present_value_bool` (`bool`): Ingested when `present_value` is boolean (`true`/`false`).
  * `present_value_str` (`string`): Ingested when `present_value` is string/enum.

* **Timestamp**:
  * Set to `publishTime` (converted to nanoseconds) from message envelope, or server ingestion time.

---

## 4. Example Queries

### PostgreSQL Queries (psql / SQL)

```sql
-- 1. Query latest point states for a device
SELECT point_name, value_state, units, level, message, timestamp
FROM udmi_point_state
WHERE device_id = 'AHU-1'
ORDER BY timestamp DESC
LIMIT 20;

-- 2. Query all discovered BACnet devices from latest discovery scan
SELECT device_id, bacnet_addr, hardware_make, hardware_model, generation
FROM udmi_discovery
WHERE scan_family = 'bacnet'
ORDER BY generation DESC;

-- 3. Query validation errors
SELECT device_id, message_type, category, message, errors
FROM udmi_validation
WHERE level >= 500
ORDER BY timestamp DESC;

-- 4. Inspect raw JSONB message payload
SELECT device_id, sub_folder, payload->'system'->>'serial_no' AS serial_no
FROM udmi_messages
WHERE sub_folder = 'system'
LIMIT 10;
```

### InfluxDB Flux Queries

```flux
// Query the last 1 hour of numeric telemetry for a specific point
from(bucket: "home")
  |> range(start: -1h)
  |> filter(fn: (r) => r["_measurement"] == "point_value")
  |> filter(fn: (r) => r["device_id"] == "AHU-1")
  |> filter(fn: (r) => r["point_name"] == "supply_air_temperature_sensor")
  |> filter(fn: (r) => r["_field"] == "present_value_num")
```

---

## 5. Python Access Snippet

```python
import os
from udmi.common.db.postgres import PostgresManager
from udmi.common.db.influx import InfluxManager

# 1. Connect to PostgreSQL
pg = PostgresManager(
    host=os.environ.get("POSTGRES_HOST", "127.0.0.1"),
    port=os.environ.get("POSTGRES_PORT", 5432),
    user=os.environ.get("POSTGRES_USER", "postgres"),
    database=os.environ.get("POSTGRES_DB", "postgres"),
)
conn = pg.get_connection()
with conn.cursor() as cur:
    cur.execute("SELECT device_id, make, model, serial_no FROM udmi_system_state;")
    devices = cur.fetchall()

# 2. Connect to InfluxDB
influx = InfluxManager(
    url=os.environ.get("INFLUXDB_URL", "http://127.0.0.1:8086"),
    token=os.environ.get("INFLUXDB_TOKEN", "test-influx-token-12345"),
    org=os.environ.get("INFLUXDB_ORG", "bridgehead"),
    bucket=os.environ.get("INFLUXDB_BUCKET", "home"),
)
query_api = influx.get_client().query_api()
tables = query_api.query('from(bucket:"home") |> range(start: -10m)')
```
