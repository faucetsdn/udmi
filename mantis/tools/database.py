"""Database inspection and read-only querying tool for Mantis."""

import json
import re
import urllib.parse
import urllib.request
from typing import Any, Dict

from mantis.session import SessionManager


FORBIDDEN_KEYWORDS = {
    "insert", "update", "delete", "drop", "alter", "create",
    "truncate", "grant", "revoke", "copy", "replace", "vacuum",
    "merge", "call", "set", "do", "execute", "into"
}


def query_database(
    session_mgr: SessionManager,
    test_id: str,
    database_type: str,
    query: str,
    max_rows: int = 100,
) -> Dict[str, Any]:
    """Execute a read-only query against InfluxDB or PostgreSQL of an active test session."""
    db_type = database_type.lower().strip()
    if db_type not in ("influx", "postgres"):
        raise ValueError(f"Unsupported database_type: '{database_type}'. Must be 'influx' or 'postgres'.")

    # Safety check: enforce read-only query by tokenizing SQL identifiers/keywords
    query_words = set(re.findall(r"\b[a-zA-Z0-9_]+\b", query.lower()))
    for kw in sorted(FORBIDDEN_KEYWORDS):
        if kw in query_words:
            raise ValueError(f"Mutating query rejected. Only read-only queries are permitted (found '{kw}').")

    info = session_mgr.get_session_info(test_id) or {}
    ports = info.get("ports", {})

    if db_type == "influx":
        influx_port = ports.get("influx")
        if not influx_port:
            influx_port = session_mgr.derive_port_block(test_id) + 2

        params = urllib.parse.urlencode({"db": "udmi", "q": query})
        url = f"http://127.0.0.1:{influx_port}/query?{params}"
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=3.0) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                results = data.get("results", [])
                truncated = False
                for res in results:
                    for series in res.get("series", []):
                        vals = series.get("values", [])
                        if len(vals) > max_rows:
                            series["values"] = vals[:max_rows]
                            truncated = True
                return {
                    "status": "SUCCESS",
                    "test_id": test_id,
                    "database_type": "influx",
                    "query": query,
                    "results": results,
                    "truncated": truncated,
                }
        except Exception as e:
            return {
                "status": "ERROR",
                "test_id": test_id,
                "database_type": "influx",
                "query": query,
                "error": str(e),
            }

    elif db_type == "postgres":
        pg_port = ports.get("postgres")
        if not pg_port:
            pg_port = session_mgr.derive_port_block(test_id) + 3

        try:
            import psycopg2
            conn = psycopg2.connect(
                host="127.0.0.1",
                port=pg_port,
                dbname="udmi",
                user="postgres",
                connect_timeout=3,
            )
            try:
                with conn.cursor() as cur:
                    cur.execute(query)
                    if cur.description:
                        columns = [desc[0] for desc in cur.description]
                        rows = cur.fetchmany(max_rows + 1)
                        truncated = len(rows) > max_rows
                        if truncated:
                            rows = rows[:max_rows]
                        rows_dict = [dict(zip(columns, row)) for row in rows]
                        return {
                            "status": "SUCCESS",
                            "test_id": test_id,
                            "database_type": "postgres",
                            "query": query,
                            "columns": columns,
                            "results": rows_dict,
                            "row_count": len(rows_dict),
                            "truncated": truncated,
                        }
                    return {
                        "status": "SUCCESS",
                        "test_id": test_id,
                        "database_type": "postgres",
                        "query": query,
                        "results": [],
                        "row_count": 0,
                        "truncated": False,
                    }
            finally:
                conn.close()
        except Exception as e:
            return {
                "status": "ERROR",
                "test_id": test_id,
                "database_type": "postgres",
                "query": query,
                "error": str(e),
            }
