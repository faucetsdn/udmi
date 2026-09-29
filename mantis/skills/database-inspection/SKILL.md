---
name: database-inspection
description: Reference for inspecting captured telemetry and event tables in InfluxDB and PostgreSQL test databases.
---

# Database Inspection & Observability

## 1. Overview
Active UDMI local test setups maintain isolated time-series (InfluxDB) and relational (PostgreSQL) databases for capturing events, pointset telemetry, and device state histories.

## 2. Ports & Access
In an active test session, ports are deterministically derived from the session port block:
* **InfluxDB**: Port `MQTT_PORT + 2` (HTTP REST query interface).
* **PostgreSQL**: Port `MQTT_PORT + 3` (Postgres protocol, dbname `udmi`, user `postgres`).

## 3. Read-Only Safety Policy
To ensure environment immutability and prevent data corruption during diagnostic triages:
* All database queries must be strictly **read-only** (`SELECT ...`).
* Mutating statements (`INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, `CREATE`, `TRUNCATE`, etc.) are unconditionally rejected.

## 4. Tool Usage
Use the `query_database` tool:
* `test_id`: Active session ID.
* `database_type`: `'influx'` or `'postgres'`.
* `query`: Read-only SQL query string.
