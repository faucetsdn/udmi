"""Butler Data Provider for UDMI MCP Server.

Encapsulates relational (PostgreSQL) and time-series (InfluxDB) datastores to provide
pure domain-specific data access for device discovery, telemetry, and mapping flows
without exposing database credentials, query languages, or internal storage schemas.
"""

from datetime import datetime, timezone, date
import json
import os
import re
import sys
from typing import Any, Dict, List, Optional, Union


def _format_iso(val: Any) -> Optional[str]:
    """Formats a datetime or timestamp string into canonical ISO 8601 UTC format (YYYY-MM-DDTHH:MM:SSZ)."""
    if val is None:
        return None
    if isinstance(val, (datetime, date)):
        if isinstance(val, datetime):
            if val.tzinfo is not None:
                val = val.astimezone(timezone.utc)
            return val.strftime("%Y-%m-%dT%H:%M:%SZ")
        return val.strftime("%Y-%m-%d")
    s = str(val).strip()
    if not s or s in ("None", "null", "—"):
        return None
    try:
        dt = datetime.fromisoformat(s)
        if dt.tzinfo is not None:
            dt = dt.astimezone(timezone.utc)
        return dt.strftime("%Y-%m-%dT%H:%M:%SZ")
    except Exception:
        pass
    if " " in s and "T" not in s:
        s = s.replace(" ", "T")
    s = re.sub(r"\.\d+", "", s)
    s = re.sub(r"\+00:00$", "Z", s)
    if "T" in s and not s.endswith("Z") and not s.endswith("+00:00"):
        s += "Z"
    return s

from udmi.common.db.postgres import PostgresManager
from udmi.common.db.influx import InfluxManager
from udmi.common.project_spec import parse_project_spec
from butler.src.rollout import RolloutManager
class ButlerProvider:
    """Encapsulates Butler datastore access for mapping and telemetry reconciliation."""

    def __init__(
        self,
        pg_manager: Optional[Any] = None,
        influx_manager: Optional[Any] = None,
        project_spec: Optional[str] = None,
        pg_port: Optional[Union[str, int]] = None,
        influx_port: Optional[Union[str, int]] = None,
    ):
        """Initializes ButlerProvider with relational and timeseries managers."""
        if pg_manager is not None:
            self.pg_manager = pg_manager
        elif PostgresManager is not None:
            resolved_port = pg_port
            if not resolved_port and project_spec and parse_project_spec:
                spec_info = parse_project_spec(project_spec)
                p = spec_info.get("port")
                if p and str(p) != "8883":
                    resolved_port = str(int(p) + 3)
            if not resolved_port:
                resolved_port = os.environ.get("POSTGRES_PORT", "5432")
            self.pg_manager = PostgresManager(port=resolved_port)
        else:
            self.pg_manager = None

        if influx_manager is not None:
            self.influx_manager = influx_manager
        elif InfluxManager is not None:
            resolved_inf_port = influx_port
            if not resolved_inf_port and project_spec and parse_project_spec:
                spec_info = parse_project_spec(project_spec)
                p = spec_info.get("port")
                if p and str(p) != "8883":
                    resolved_inf_port = str(int(p) + 2)
            if not resolved_inf_port:
                resolved_inf_port = os.environ.get("INFLUX_PORT", os.environ.get("INFLUXDB_PORT", "8086"))
            host = os.environ.get("INFLUXDB_HOST", "127.0.0.1")
            url = f"http://{host}:{resolved_inf_port}"
            self.influx_manager = InfluxManager(url=url)
        else:
            self.influx_manager = None

        # Persistent rollout state management in Butler proper (PostgreSQL)
        self.rollout_manager = RolloutManager(postgres_manager=self.pg_manager)

    def health(self) -> Dict[str, Any]:
        """Probes relational and timeseries datastores to report abstract health status."""
        relational_ok = False
        timeseries_ok = False

        if self.pg_manager:
            try:
                conn = self.pg_manager.get_connection()
                with conn.cursor() as cur:
                    cur.execute("SELECT 1;")
                    cur.fetchone()
                conn.close()
                relational_ok = True
            except Exception:
                relational_ok = False

        if self.influx_manager:
            try:
                client = self.influx_manager.get_client()
                ready = client.ready()
                timeseries_ok = bool(ready and getattr(ready, "status", None) == "ready")
            except Exception:
                timeseries_ok = False

        if relational_ok and timeseries_ok:
            overall = "UP"
        elif relational_ok or timeseries_ok:
            overall = "DEGRADED"
        else:
            overall = "DOWN"

        return {
            "status": overall,
            "service": "butler",
            "connected": relational_ok or timeseries_ok,
            "datastores": {
                "relational": relational_ok,
                "timeseries": timeseries_ok,
            },
        }

    def get_discovery_events(self, registry_id: str) -> List[Dict[str, Any]]:
        """Queries discovery events for a device registry ordered chronologically.

        Extracts discovery payloads and reporting gateway IDs directly from the
        lifecycle audit store or specialized discovery tables.

        Args:
            registry_id: Target device registry identifier.

        Returns:
            List of discovery event objects.
        """
        if not self.pg_manager:
            return []

        try:
            conn = self.pg_manager.get_connection()
            with conn.cursor() as cur:
                # 1. Query udmi_messages for discovery events
                cur.execute(
                    """
                    SELECT id, device_id, payload, publish_time
                    FROM udmi_messages
                    WHERE registry_id = %s AND sub_folder = 'discovery' AND sub_type = 'events'
                    ORDER BY id ASC;
                    """,
                    (registry_id,),
                )
                rows = cur.fetchall()

                if rows:
                    conn.close()
                    results = []
                    for row in rows:
                        msg_id, dev_id, payload, pub_time = row
                        if isinstance(payload, str):
                            try:
                                payload = json.loads(payload)
                            except Exception:
                                pass
                        results.append({
                            "id": msg_id,
                            "gateway_id": dev_id,
                            "payload": payload,
                            "timestamp": _format_iso(pub_time),
                        })
                    return results

                # 2. Fallback to specialized udmi_discovery table if udmi_messages has no records
                cur.execute(
                    """
                    SELECT id, device_id, scan_family, ether_addr, ipv4_addr, bacnet_addr,
                           hardware_make, hardware_model, firmware_version, generation, timestamp, ports
                    FROM udmi_discovery
                    WHERE device_registry_id = %s
                    ORDER BY id ASC;
                    """,
                    (registry_id,),
                )
                disc_rows = cur.fetchall()
            conn.close()

            results = []
            for d in disc_rows:
                disc_id, dev_id, fam, ether, ipv4, bacnet, make, model, fw, gen, ts, ports = d
                families_dict: Dict[str, Any] = {}
                if bacnet:
                    families_dict["bacnet"] = {"addr": bacnet}
                if ipv4:
                    families_dict["ipv4"] = {"addr": ipv4}
                if fam and fam not in ("bacnet", "ipv4") and (ether or bacnet or ipv4):
                    families_dict[fam] = {"addr": ether or bacnet or ipv4}

                payload = {
                    "version": "1.5.7",
                    "timestamp": _format_iso(ts),
                    "generation": _format_iso(gen),
                    "family": fam,
                    "addr": bacnet or ipv4 or ether,
                    "families": families_dict,
                }
                if make or model or fw:
                    payload["system"] = {
                        "hardware": {"make": make, "model": model},
                        "ancillary": {"firmware": fw},
                    }
                if ports:
                    payload["refs"] = {p.get("port", f"p_{i}"): {"adjunct": p} for i, p in enumerate(ports) if isinstance(p, dict)}

                results.append({
                    "id": disc_id,
                    "gateway_id": dev_id,
                    "payload": payload,
                    "timestamp": payload["timestamp"],
                })
            return results

        except Exception as e:
            print(f"ButlerProvider error fetching discovery events: {e}", file=sys.stderr)
            return []

    def get_discovered_devices(self, registry_id: str) -> List[Dict[str, Any]]:
        """Parses discovery events for a registry into normalized discovered device records.

        Extracts network addresses across families (BACnet, IPv4, vendor) and associated
        gateway IDs ready for mapping reconciliation.

        Args:
            registry_id: Target device registry identifier.

        Returns:
            List of normalized discovered device summaries.
        """
        events = self.get_discovery_events(registry_id)
        devices: List[Dict[str, Any]] = []

        for ev in events:
            payload = ev.get("payload", {})
            gateway_id = ev.get("gateway_id")

            if isinstance(payload, dict) and "payload" in payload and isinstance(payload.get("payload"), dict):
                payload = payload["payload"]

            bacnet_addr = None
            ipv4_addr = None
            vendor_addr = None

            if payload.get("family") == "bacnet":
                bacnet_addr = payload.get("addr")
            elif payload.get("family") == "vendor":
                vendor_addr = payload.get("addr")
            elif payload.get("family") == "ipv4":
                ipv4_addr = payload.get("addr")

            families = payload.get("families", {})
            if isinstance(families, dict):
                if "bacnet" in families:
                    bacnet_addr = bacnet_addr or families["bacnet"].get("addr")
                if "ipv4" in families:
                    ipv4_addr = families["ipv4"].get("addr")
                if "vendor" in families:
                    vendor_addr = vendor_addr or families["vendor"].get("addr")

            if bacnet_addr or ipv4_addr or vendor_addr:
                devices.append({
                    "gateway_id": gateway_id,
                    "generation": payload.get("generation"),
                    "bacnet": str(bacnet_addr) if bacnet_addr else None,
                    "ipv4": str(ipv4_addr) if ipv4_addr else None,
                    "vendor": str(vendor_addr) if vendor_addr else None,
                    "timestamp": ev.get("timestamp"),
                })

        return devices

    def get_device_messages(
        self,
        registry_id: str,
        device_id: str,
    ) -> List[Dict[str, Any]]:
        """Queries the lifecycle message progression (model, discovery, proposal) for a device.

        Args:
            registry_id: Target registry identifier.
            device_id: Target device identifier.

        Returns:
            Chronologically ordered list of lifecycle messages.
        """
        if not self.pg_manager:
            return []

        try:
            conn = self.pg_manager.get_connection()
            with conn.cursor() as cur:
                # Query messages for this device directly or mentioning this device in payload
                cur.execute(
                    """
                    SELECT id, publish_time, registry_id, device_id, sub_type, sub_folder, payload
                    FROM udmi_messages
                    WHERE registry_id = %s
                      AND (device_id = %s OR payload::text LIKE %s)
                    ORDER BY id ASC;
                    """,
                    (registry_id, device_id, f'%"{device_id}"%'),
                )
                rows = cur.fetchall()
            conn.close()

            messages = []
            for r in rows:
                p_load = r[6]
                if isinstance(p_load, str):
                    try:
                        p_load = json.loads(p_load)
                    except Exception:
                        pass

                update_from = p_load.get("updateFrom") if isinstance(p_load, dict) else None
                source = p_load.get("source", "system") if isinstance(p_load, dict) else "system"
                tx_id = p_load.get("transactionId") if isinstance(p_load, dict) else None

                pub_time = r[1]
                ts_str = _format_iso(pub_time)

                messages.append({
                    "id": r[0],
                    "timestamp": ts_str,
                    "registry_id": r[2],
                    "device_id": r[3],
                    "sub_type": r[4],
                    "sub_folder": r[5],
                    "payload": p_load,
                    "updateFrom": update_from,
                    "source": source,
                    "transaction_id": tx_id,
                })
            return messages

        except Exception as e:
            print(f"ButlerProvider error fetching device messages: {e}", file=sys.stderr)
            return []

    def record_message(
        self,
        registry_id: str,
        device_id: str,
        sub_type: str,
        sub_folder: str,
        payload: Dict[str, Any],
        project_id: Optional[str] = None,
        timestamp: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Persists a message (base model, discovery event, or proposal) into the Butler lifecycle store.

        Args:
            registry_id: Device registry identifier.
            device_id: Device identifier.
            sub_type: Message subType (e.g. 'model', 'events', 'propose').
            sub_folder: Message subFolder (e.g. 'system', 'discovery', 'localnet', 'pointset').
            payload: Structured JSON payload.
            project_id: Optional GCP/UDMI project identifier.
            timestamp: Optional message timestamp.

        Returns:
            Dict indicating status and operation summary.
        """
        if not self.pg_manager:
            raise RuntimeError("Butler relational datastore is unavailable.")

        now_str = timestamp or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        row = {
            "project_id": project_id or "default",
            "registry_id": registry_id,
            "device_id": device_id,
            "sub_folder": sub_folder,
            "sub_type": sub_type,
            "publish_time": now_str,
            "payload": payload,
        }

        self.pg_manager.insert_row("udmi_messages", row)

        return {
            "status": "SUCCESS",
            "registry_id": registry_id,
            "device_id": device_id,
            "sub_folder": sub_folder,
            "sub_type": sub_type,
        }

    def get_device_telemetry(
        self,
        registry_id: str,
        device_id: str,
        point_names: Optional[List[str]] = None,
        start: str = "-1h",
        stop: str = "now()",
    ) -> Dict[str, Any]:
        """Queries time-series telemetry point values for a device from the timeseries datastore.

        Args:
            registry_id: Target registry identifier.
            device_id: Target device identifier.
            point_names: Optional list of point names to filter by.
            start: Query time window start (e.g. '-1h').
            stop: Query time window stop (e.g. 'now()').

        Returns:
            Dict containing time-series data grouped by point name.
        """
        if not self.influx_manager:
            return {
                "registry_id": registry_id,
                "device_id": device_id,
                "series": [],
            }

        try:
            client = self.influx_manager.get_client()
            query_api = client.query_api()

            filter_clauses = [
                'r["_measurement"] == "point_value"',
                f'r["device_id"] == "{device_id}"',
            ]
            if point_names:
                pt_filters = " or ".join([f'r["point_name"] == "{p.strip()}"' for p in point_names if p.strip()])
                if pt_filters:
                    filter_clauses.append(f"({pt_filters})")

            filter_expr = " and ".join(filter_clauses)
            flux_query = f"""
                from(bucket: "{self.influx_manager.bucket}")
                  |> range(start: {start}, stop: {stop})
                  |> filter(fn: (r) => {filter_expr})
                  |> yield(name: "points")
            """

            tables = query_api.query(flux_query)
            series_by_point: Dict[str, List[Dict[str, Any]]] = {}

            for table in tables:
                for record in table.records:
                    pt_name = record.values.get("point_name")
                    val = record.get_value()
                    ts = record.get_time()
                    if pt_name not in series_by_point:
                        series_by_point[pt_name] = []
                    series_by_point[pt_name].append({
                        "time": _format_iso(ts),
                        "value": val,
                        "field": record.get_field(),
                    })

            series_list = [
                {"point_name": k, "values": v}
                for k, v in series_by_point.items()
            ]

            return {
                "registry_id": registry_id,
                "device_id": device_id,
                "series": series_list,
            }

        except Exception as e:
            print(f"ButlerProvider error fetching telemetry: {e}", file=sys.stderr)
            return {
                "registry_id": registry_id,
                "device_id": device_id,
                "series": [],
            }

    def write_telemetry(
        self,
        registry_id: str,
        device_id: str,
        points: Dict[str, Any],
        timestamp: Optional[str] = None,
        project_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Writes point telemetry values for a device into the timeseries datastore.

        Args:
            registry_id: Device registry identifier.
            device_id: Device identifier.
            points: Dictionary mapping point names to numeric, boolean, or string values.
            timestamp: Optional publish timestamp.
            project_id: Optional project identifier.

        Returns:
            Dict containing operation summary.
        """
        if not self.influx_manager:
            raise RuntimeError("Butler timeseries datastore is unavailable.")

        now_str = timestamp or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        envelope = {
            "deviceId": device_id,
            "deviceRegistryId": registry_id,
            "projectId": project_id or "default",
            "publishTime": now_str,
        }
        points_payload: Dict[str, Any] = {}
        for pt_name, pt_val in points.items():
            if isinstance(pt_val, dict):
                points_payload[pt_name] = pt_val
            else:
                points_payload[pt_name] = {"present_value": pt_val}

        count = self.influx_manager.write_pointset_payload(envelope, {"points": points_payload})
        return {
            "status": "SUCCESS",
            "registry_id": registry_id,
            "device_id": device_id,
            "points_written": count,
        }

    def clear_registry_mapping_data(self, registry_id: str) -> Dict[str, Any]:
        """Deletes discovery events and proposals for a registry to reset mapping state.

        Args:
            registry_id: Target registry identifier.

        Returns:
            Dict indicating status and count of deleted records.
        """
        if not self.pg_manager:
            raise RuntimeError("Butler relational datastore is unavailable.")

        conn = self.pg_manager.get_connection()
        deleted_count = 0
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    DELETE FROM udmi_messages
                    WHERE registry_id = %s
                      AND (sub_folder = 'discovery' OR sub_type IN ('propose', 'model'));
                    """,
                    (registry_id,),
                )
                deleted_count += cur.rowcount
                cur.execute(
                    "DELETE FROM udmi_discovery WHERE device_registry_id = %s;",
                    (registry_id,),
                )
                deleted_count += cur.rowcount

            conn.commit()
        finally:
            conn.close()

        return {
            "status": "SUCCESS",
            "registry_id": registry_id,
            "deleted_records": deleted_count,
        }

    def get_portfolio_summary(self) -> Dict[str, Any]:
        """Returns aggregate device counts, online/offline breakdown, and recent alerts."""
        if not self.pg_manager:
            raise ConnectionError("Butler relational datastore is unavailable.")

        conn = self.pg_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute("""
                    SELECT 
                        COUNT(DISTINCT (device_registry_id, device_id)) as total_devices,
                        COUNT(DISTINCT device_registry_id) as total_registries
                    FROM udmi_system_state;
                """)
                row = cur.fetchone()
                total_devices = row[0] if row else 0
                total_registries = row[1] if row else 0

                cur.execute("""
                    SELECT COUNT(*) 
                    FROM udmi_validation 
                    WHERE level >= 500 AND timestamp >= NOW() - INTERVAL '24 hours';
                """)
                crit_row = cur.fetchone()
                critical_alerts_24h = crit_row[0] if crit_row else 0

                cur.execute("""
                    SELECT COUNT(DISTINCT (device_registry_id, device_id))
                    FROM udmi_validation
                    WHERE level >= 500 AND timestamp >= NOW() - INTERVAL '15 minutes';
                """)
                err_row = cur.fetchone()
                error_devices = err_row[0] if err_row else 0

            online_devices = max(0, total_devices - error_devices)
            offline_devices = 0
            active_rollouts_count = self.rollout_manager.get_active_rollouts_count()

            return {
                "device_counts": {
                    "total": total_devices,
                    "online": online_devices,
                    "offline": offline_devices,
                    "error": error_devices,
                },
                "registries_count": total_registries,
                "active_rollouts_count": active_rollouts_count,
                "critical_alerts_24h": critical_alerts_24h,
            }
        finally:
            conn.close()

    def get_alerts(self, limit: int = 50, min_level: int = 500) -> List[Dict[str, Any]]:
        """Queries recent validation and alarm events."""
        if not self.pg_manager:
            raise ConnectionError("Butler relational datastore is unavailable.")

        conn = self.pg_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute("""
                    SELECT id, device_registry_id, device_id, level, category, message, detail, timestamp
                    FROM udmi_validation
                    WHERE level >= %s
                    ORDER BY timestamp DESC
                    LIMIT %s;
                """, (min_level, limit))
                rows = cur.fetchall()

            alerts = []
            for r in rows:
                alerts.append({
                    "id": r[0],
                    "registry_id": r[1] or "default",
                    "device_id": r[2] or "unknown",
                    "level": r[3],
                    "category": r[4] or "validation",
                    "message": r[5] or "Validation Notice",
                    "detail": r[6],
                    "timestamp": _format_iso(r[7]),
                })
            return alerts
        finally:
            conn.close()

    def get_devices(
        self,
        limit: int = 100,
        offset: int = 0,
        registry_id: Optional[str] = None,
        device_prefix: Optional[str] = None,
        make: Optional[str] = None,
        model: Optional[str] = None,
        status: Optional[str] = None,
        search: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Fetches a paginated, filtered list of devices from udmi_system_state."""
        if status and status.upper() != "ONLINE":
            return {
                "total": 0,
                "limit": limit,
                "offset": offset,
                "devices": [],
            }

        if not self.pg_manager:
            raise ConnectionError("Butler relational datastore is unavailable.")

        conn = self.pg_manager.get_connection()
        try:
            conditions = ["1=1"]
            params: List[Any] = []

            if registry_id:
                conditions.append("s.device_registry_id = %s")
                params.append(registry_id)
            if device_prefix:
                conditions.append("s.device_id LIKE %s")
                params.append(f"{device_prefix}%")
            if make:
                conditions.append("s.make ILIKE %s")
                params.append(f"%{make}%")
            if model:
                conditions.append("s.model ILIKE %s")
                params.append(f"%{model}%")
            if search:
                conditions.append("(s.device_id ILIKE %s OR s.make ILIKE %s OR s.model ILIKE %s)")
                params.extend([f"%{search}%", f"%{search}%", f"%{search}%"])

            where_clause = " AND ".join(conditions)

            with conn.cursor() as cur:
                count_query = f"""
                    SELECT COUNT(DISTINCT (s.device_registry_id, s.device_id))
                    FROM udmi_system_state s
                    WHERE {where_clause};
                """
                cur.execute(count_query, params)
                total = cur.fetchone()[0]

                if total == 0:
                    return {
                        "total": 0,
                        "limit": limit,
                        "offset": offset,
                        "devices": [],
                    }

                data_query = f"""
                    SELECT DISTINCT ON (s.device_registry_id, s.device_id)
                        s.id,
                        s.device_registry_id,
                        s.device_id,
                        s.make,
                        s.model,
                        s.serial_no,
                        s.software,
                        s.timestamp
                    FROM udmi_system_state s
                    WHERE {where_clause}
                    ORDER BY s.device_registry_id, s.device_id, s.timestamp DESC
                    LIMIT %s OFFSET %s;
                """
                cur.execute(data_query, params + [limit, offset])
                rows = cur.fetchall()

            devices = []
            for r in rows:
                software_raw = r[6]
                software_ver = None
                if isinstance(software_raw, list) and software_raw:
                    software_ver = software_raw[0].get("version") if isinstance(software_raw[0], dict) else None
                elif isinstance(software_raw, dict):
                    software_ver = software_raw.get("system")

                devices.append({
                    "id": r[0],
                    "registry_id": r[1] or "default",
                    "device_id": r[2],
                    "make": r[3] or "Unknown",
                    "model": r[4] or "Unknown",
                    "serial_no": r[5],
                    "software_version": software_ver,
                    "liveness_status": "ONLINE",
                    "last_seen": _format_iso(r[7]),
                })

            return {
                "total": total,
                "limit": limit,
                "offset": offset,
                "devices": devices,
            }
        finally:
            conn.close()

    def get_device_detail(self, registry_id: str, device_id: str) -> Optional[Dict[str, Any]]:
        """Returns metadata, system state, point states, and recent validation errors for a device."""
        if not self.pg_manager:
            raise ConnectionError("Butler relational datastore is unavailable.")

        conn = self.pg_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute("""
                    SELECT make, model, serial_no, rev, sku, software, timestamp
                    FROM udmi_system_state
                    WHERE device_registry_id = %s AND device_id = %s
                    ORDER BY timestamp DESC
                    LIMIT 1;
                """, (registry_id, device_id))
                sys_row = cur.fetchone()

                if not sys_row:
                    return None
                cur.execute("""
                    SELECT system_location_room, system_location_floor, metadata
                    FROM udmi_metadata
                    WHERE device_registry_id = %s AND device_id = %s
                    ORDER BY timestamp DESC
                    LIMIT 1;
                """, (registry_id, device_id))
                meta_row = cur.fetchone()
                cur.execute("""
                    SELECT DISTINCT ON (point_name)
                        point_name, value_state, units, level, message, status_timestamp, timestamp
                    FROM udmi_point_state
                    WHERE device_registry_id = %s AND device_id = %s
                    ORDER BY point_name, timestamp DESC;
                """, (registry_id, device_id))
                point_rows = cur.fetchall()
                cur.execute("""
                    SELECT level, category, message, detail, timestamp
                    FROM udmi_validation
                    WHERE device_registry_id = %s AND device_id = %s
                    ORDER BY timestamp DESC
                    LIMIT 10;
                """, (registry_id, device_id))
                val_rows = cur.fetchall()

            meta_dict = meta_row[2] if (meta_row and len(meta_row) > 2 and isinstance(meta_row[2], dict)) else {}
            meta_dict.setdefault("make", sys_row[0] or "Unknown")
            meta_dict.setdefault("model", sys_row[1] or "Unknown")
            meta_dict.setdefault("serial_no", sys_row[2])
            meta_dict.setdefault("room", meta_row[0] if meta_row else None)
            meta_dict.setdefault("floor", meta_row[1] if meta_row else None)
            meta_dict["last_seen"] = _format_iso(meta_dict.get("last_seen") or (sys_row[6] if sys_row else None))

            points_map = {}
            for pr in point_rows:
                points_map[pr[0]] = {
                    "value_state": pr[1],
                    "units": pr[2],
                    "level": pr[3],
                    "message": pr[4],
                    "status_timestamp": _format_iso(pr[5]),
                }

            events = [
                {
                    "level": vr[0],
                    "category": vr[1],
                    "message": vr[2],
                    "detail": vr[3],
                    "timestamp": _format_iso(vr[4]),
                }
                for vr in val_rows
            ]

            software_dict = {}
            if sys_row and sys_row[5]:
                if isinstance(sys_row[5], list):
                    for item in sys_row[5]:
                        if isinstance(item, dict) and "id" in item:
                            software_dict[item["id"]] = item.get("version")
                elif isinstance(sys_row[5], dict):
                    software_dict = sys_row[5]

            return {
                "registry_id": registry_id,
                "device_id": device_id,
                "metadata": meta_dict,
                "state": {
                    "system": {
                        "make": sys_row[0],
                        "model": sys_row[1],
                        "serial_no": sys_row[2],
                        "software": software_dict,
                        "last_seen": _format_iso(sys_row[6]) if sys_row else None,
                    },
                    "pointset": {
                        "points": points_map,
                    },
                },
                "events": events,
                "config": {
                    "system": {
                        "software": software_dict,
                    }
                },
            }
        finally:
            conn.close()

    def create_rollout(
        self,
        name: str,
        target_filter: Dict[str, Any],
        target_payload: Dict[str, Any],
        target_subfolder: str = "system",
        batch_size: int = 10,
        batch_interval_sec: int = 60,
        total_devices: int = 0,
    ) -> Dict[str, Any]:
        """Creates and launches a new declarative staged rollout campaign."""
        return self.rollout_manager.create_rollout(
            name=name,
            target_filter=target_filter,
            target_payload=target_payload,
            target_subfolder=target_subfolder,
            batch_size=batch_size,
            batch_interval_sec=batch_interval_sec,
            total_devices=total_devices,
        )

    def list_rollouts(self) -> List[Dict[str, Any]]:
        """Returns all active and completed rollout campaigns."""
        return self.rollout_manager.list_rollouts()

    def get_rollout(self, rollout_id: int) -> Optional[Dict[str, Any]]:
        """Returns details for a single rollout campaign."""
        return self.rollout_manager.get_rollout(rollout_id=rollout_id)

    def update_rollout(
        self,
        rollout_id: int,
        status: Optional[str] = None,
        converged_devices: Optional[int] = None,
        failed_devices: Optional[int] = None,
    ) -> Optional[Dict[str, Any]]:
        """Updates status (e.g. PAUSED, CANCELLED) or progress of a rollout campaign."""
        return self.rollout_manager.update_rollout(
            rollout_id=rollout_id,
            status=status,
            converged_devices=converged_devices,
            failed_devices=failed_devices,
        )

