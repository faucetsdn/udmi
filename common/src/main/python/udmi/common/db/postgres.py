"""PostgreSQL database management and row insertion utilities."""

import json
import os
import sys
from typing import Any, Dict, List, Optional, Union
import psycopg2
from psycopg2.extras import Json


import math


def sanitize_for_json(obj: Any) -> Any:
    """Recursively replaces NaN and Infinity float values with None for JSON compliance."""
    if isinstance(obj, float):
        if math.isnan(obj) or math.isinf(obj):
            return None
        return obj
    if isinstance(obj, dict):
        return {k: sanitize_for_json(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [sanitize_for_json(v) for v in obj]
    if isinstance(obj, tuple):
        return tuple(sanitize_for_json(v) for v in obj)
    return obj


class PostgresManager:
    """Manages PostgreSQL connection and insertion operations."""

    def __init__(
        self,
        host: Optional[str] = None,
        port: Optional[Union[str, int]] = None,
        user: Optional[str] = None,
        password: Optional[str] = None,
        database: Optional[str] = None,
    ):
        self.host = host or os.environ.get("POSTGRES_HOST", "127.0.0.1")
        self.port = str(port or os.environ.get("POSTGRES_PORT", "5432"))
        self.user = user or os.environ.get("POSTGRES_USER", "postgres")
        self.password = password or os.environ.get("POSTGRES_PASSWORD", "")
        self.database = database or os.environ.get("POSTGRES_DB", "postgres")

    def get_connection(self):
        """Returns a psycopg2 connection."""
        conn_kwargs = {
            "host": self.host,
            "port": self.port,
            "user": self.user,
            "dbname": self.database,
        }
        if self.password:
            conn_kwargs["password"] = self.password
        return psycopg2.connect(**conn_kwargs)

    def execute_sql(self, sql: str, params: Optional[tuple] = None) -> None:
        """Executes a single SQL command with optional parameters."""
        conn = self.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, params)
            conn.commit()
        finally:
            conn.close()

    def init_table(self, create_table_sql: str) -> None:
        """Initializes a table if it does not exist."""
        try:
            self.execute_sql(create_table_sql)
        except psycopg2.Error as e:
            print(f"Warning: PostgreSQL table initialization error: {e}", file=sys.stderr)

    def init_default_tables(self) -> None:
        """Initializes default tables for UDMI messages."""
        raw_table_sql = """
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
        point_state_sql = """
        CREATE TABLE IF NOT EXISTS udmi_point_state (
            id SERIAL PRIMARY KEY,
            timestamp TIMESTAMP,
            device_id VARCHAR(255),
            device_registry_id VARCHAR(255),
            message_id VARCHAR(255),
            point_name VARCHAR(255),
            value_state VARCHAR(50),
            units VARCHAR(50),
            status_timestamp TIMESTAMP,
            level INTEGER,
            category VARCHAR(100),
            message TEXT,
            detail TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """
        system_state_sql = """
        CREATE TABLE IF NOT EXISTS udmi_system_state (
            id SERIAL PRIMARY KEY,
            timestamp TIMESTAMP,
            publish_timestamp TIMESTAMP,
            device_registry_id VARCHAR(255),
            device_id VARCHAR(255),
            device_num_id VARCHAR(255),
            gateway_id VARCHAR(255),
            make VARCHAR(255),
            model VARCHAR(255),
            serial_no VARCHAR(255),
            rev VARCHAR(255),
            sku VARCHAR(255),
            software JSONB,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """
        discovery_sql = """
        CREATE TABLE IF NOT EXISTS udmi_discovery (
            id SERIAL PRIMARY KEY,
            timestamp TIMESTAMP,
            generation TIMESTAMP,
            device_registry_id VARCHAR(255),
            device_id VARCHAR(255),
            message_id VARCHAR(255),
            scan_family VARCHAR(50),
            ether_addr VARCHAR(255),
            ipv4_addr VARCHAR(255),
            bacnet_addr VARCHAR(255),
            hostname VARCHAR(255),
            fqdn VARCHAR(255),
            hardware_make VARCHAR(255),
            hardware_model VARCHAR(255),
            firmware_version VARCHAR(255),
            serial_no VARCHAR(255),
            status_level INTEGER,
            status_category VARCHAR(100),
            status_message TEXT,
            ports JSONB,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """
        validation_sql = """
        CREATE TABLE IF NOT EXISTS udmi_validation (
            id SERIAL PRIMARY KEY,
            timestamp TIMESTAMP,
            device_registry_id VARCHAR(255),
            device_id VARCHAR(255),
            message_type VARCHAR(100),
            message TEXT,
            detail TEXT,
            category VARCHAR(100),
            level INTEGER,
            errors JSONB,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """
        alarms_sql = """
        CREATE TABLE IF NOT EXISTS udmi_alarms (
            id SERIAL PRIMARY KEY,
            timestamp TIMESTAMP,
            device_registry_id VARCHAR(255),
            device_id VARCHAR(255),
            alarm_category VARCHAR(100),
            alarm_priority VARCHAR(50),
            alarm_type VARCHAR(100),
            controller VARCHAR(255),
            equipment VARCHAR(255),
            fault BOOLEAN,
            from_state VARCHAR(50),
            to_state VARCHAR(50),
            generation_time TIMESTAMP,
            in_alarm BOOLEAN,
            location_path VARCHAR(255),
            message_text TEXT,
            out_of_service BOOLEAN,
            overridden BOOLEAN,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """
        metadata_sql = """
        CREATE TABLE IF NOT EXISTS udmi_metadata (
            id SERIAL PRIMARY KEY,
            timestamp TIMESTAMP,
            device_id VARCHAR(255),
            device_registry_id VARCHAR(255),
            system_hardware_make VARCHAR(255),
            system_hardware_model VARCHAR(255),
            system_hardware_sku VARCHAR(255),
            system_hardware_rev VARCHAR(255),
            system_location_room VARCHAR(255),
            system_location_floor VARCHAR(255),
            localnet_families_ipv4_addr VARCHAR(255),
            localnet_families_ether_addr VARCHAR(255),
            cloud_connection_type VARCHAR(50),
            metadata JSONB,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """

        for sql in [
            raw_table_sql,
            point_state_sql,
            system_state_sql,
            discovery_sql,
            validation_sql,
            alarms_sql,
            metadata_sql,
        ]:
            self.init_table(sql)

    def insert_row(self, table_name: str, row: Dict[str, Any]) -> None:
        """Inserts a single row dictionary into the target table."""
        self.insert_rows(table_name, [row])

    def insert_rows(self, table_name: str, rows: List[Dict[str, Any]]) -> None:
        """Inserts multiple row dictionaries into the target table with automatic JSONB serialization."""
        if not rows:
            return

        conn = self.get_connection()
        try:
            with conn.cursor() as cur:
                for row in rows:
                    columns = []
                    values = []
                    for col_name, val in row.items():
                        columns.append(col_name)
                        if isinstance(val, (dict, list)):
                            values.append(Json(sanitize_for_json(val)))
                        elif isinstance(val, float) and (math.isnan(val) or math.isinf(val)):
                            values.append(None)
                        else:
                            values.append(val)

                    col_list = ", ".join(columns)
                    placeholders = ", ".join(["%s"] * len(values))
                    sql = f"INSERT INTO {table_name} ({col_list}) VALUES ({placeholders})"
                    cur.execute(sql, tuple(values))
            conn.commit()
        finally:
            conn.close()
