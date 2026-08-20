#!/usr/bin/env python3
"""Script to parse JSON stream from stdin and save messages to databases.

All pointset events messages should be saved to influx, while everything else
should be written to postgresql.
"""
import sys
import json
import os
from datetime import datetime
import psycopg2
from psycopg2.extras import Json
from influxdb_client import InfluxDBClient, Point
from influxdb_client.client.write_api import SYNCHRONOUS

# Database config from environment with defaults
POSTGRES_PORT = os.environ.get("POSTGRES_PORT", "5432")
POSTGRES_USER = os.environ.get("POSTGRES_USER", "postgres")
POSTGRES_DB = os.environ.get("POSTGRES_DB", "postgres")
POSTGRES_HOST = os.environ.get("POSTGRES_HOST", "127.0.0.1")

INFLUXDB_TOKEN = os.environ.get("INFLUXDB_TOKEN", "test-influx-token-12345")
INFLUXDB_ORG = os.environ.get("INFLUXDB_ORG", "bridgehead")
INFLUXDB_BUCKET = os.environ.get("INFLUXDB_BUCKET", "home")
INFLUX_PORT = os.environ.get("INFLUX_PORT", "8086")
INFLUXDB_URL = os.environ.get("INFLUXDB_URL", f"http://localhost:{INFLUX_PORT}")

def get_postgres_connection():
  """Returns a connection to the PostgreSQL database."""
  return psycopg2.connect(
      host=POSTGRES_HOST,
      port=POSTGRES_PORT,
      user=POSTGRES_USER,
      dbname=POSTGRES_DB
  )

def init_postgres():
  """Create the postgres table if not exists."""
  create_table_sql = """
  CREATE TABLE IF NOT EXISTS udmi_messages (
      id SERIAL PRIMARY KEY,
      project_id VARCHAR(255),
      registry_id VARCHAR(255),
      device_id VARCHAR(255),
      sub_folder VARCHAR(50),
      sub_type VARCHAR(50),
      publish_time TIMESTAMP,
      payload JSONB,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
  """
  conn = get_postgres_connection()
  try:
    with conn.cursor() as cur:
      cur.execute(create_table_sql)
    conn.commit()
  finally:
    conn.close()

def save_to_postgres(envelope, payload):
  """Insert a message into postgres using parameterized queries."""
  project_id = envelope.get("projectId")
  registry_id = envelope.get("deviceRegistryId")
  device_id = envelope.get("deviceId")
  sub_folder = envelope.get("subFolder")
  sub_type = envelope.get("subType")
  publish_time = envelope.get("publishTime")

  # Ensure payload is written as JSONB
  payload_json = Json(payload)

  sql = """
  INSERT INTO udmi_messages (
      project_id, registry_id, device_id, sub_folder, sub_type,
      publish_time, payload
  )
  VALUES (
      %s, %s, %s, %s, %s,
      %s, %s
  );
  """

  conn = get_postgres_connection()
  try:
    with conn.cursor() as cur:
      cur.execute(sql, (
          project_id, registry_id, device_id, sub_folder, sub_type,
          publish_time, payload_json
      ))
    conn.commit()
  finally:
    conn.close()

class InfluxWriteError(Exception):
  """Custom exception raised when influx write fails."""

def save_to_influx(envelope, payload, write_api):
  """Save pointset event metrics to influxdb."""
  project_id = envelope.get("projectId", "unknown")
  registry_id = envelope.get("deviceRegistryId", "unknown")
  device_id = envelope.get("deviceId", "unknown")
  publish_time = envelope.get("publishTime")

  # Parse timestamp to ns epoch
  # Default to current time if parsing fails
  ts_ns = None
  if publish_time:
    try:
      cleaned_time = publish_time.replace("Z", "+00:00")
      dt = datetime.fromisoformat(cleaned_time)
      ts_ns = int(dt.timestamp() * 1e9)
    except ValueError:
      pass

  if ts_ns is None:
    ts_ns = int(datetime.utcnow().timestamp() * 1e9)

  points_data = payload.get("points", {})

  points_to_write = []
  for point_name, point_def in points_data.items():
    present_value = point_def.get("present_value")
    if present_value is None:
      continue

    p = Point("point_value") \
        .tag("device_id", device_id) \
        .tag("registry_id", registry_id) \
        .tag("project_id", project_id) \
        .tag("point_name", point_name) \
        .time(ts_ns)

    if isinstance(present_value, bool):
      p = p.field("present_value_bool", present_value)
    elif isinstance(present_value, (int, float)):
      p = p.field("present_value_num", float(present_value))
    else:
      p = p.field("present_value_str", str(present_value))

    points_to_write.append(p)

  if not points_to_write:
    return

  try:
    write_api.write(
        bucket=INFLUXDB_BUCKET, org=INFLUXDB_ORG, record=points_to_write)
  except Exception as e:
    raise InfluxWriteError(f"influx client write failed: {e}") from e

def main():
  """Main entry point."""
  try:
    init_postgres()
  except psycopg2.Error as e:
    print(
        f"Warning: Failed to initialize PostgreSQL table: {e}",
        file=sys.stderr
    )

  influx_client = InfluxDBClient(
      url=INFLUXDB_URL, token=INFLUXDB_TOKEN, org=INFLUXDB_ORG)
  write_api = influx_client.write_api(write_options=SYNCHRONOUS)

  try:
    for line in sys.stdin:
      if not line.strip():
        continue
      try:
        msg = json.loads(line)
        envelope = msg.get("envelope", {})
        payload = msg.get("payload", {})

        sub_folder = envelope.get("subFolder")
        sub_type = envelope.get("subType")

        if sub_folder == "pointset" and sub_type == "events":
          save_to_influx(envelope, payload, write_api)
          target_db = "influx"
        else:
          save_to_postgres(envelope, payload)
          target_db = "postgres"

        registry_id = envelope.get("deviceRegistryId", "unknown")
        device_id = envelope.get("deviceId", "unknown")
        print(f"db:{target_db}/{registry_id}/{device_id}"
              f"/{sub_folder}/{sub_type}")
      except (json.JSONDecodeError, KeyError, psycopg2.Error,
              InfluxWriteError) as e:
        print(f"Error processing message: {e}", file=sys.stderr)
  finally:
    influx_client.close()

if __name__ == "__main__":
  main()
