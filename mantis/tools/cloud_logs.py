"""GCP Cloud Logging time-window queries and log extraction."""

import json
import os
from typing import Any, Dict, List, Optional


def query_cloud_logs(
    project_id: str,
    filter_query: str,
    start_time: Optional[str] = None,
    end_time: Optional[str] = None,
    limit: int = 100,
) -> Dict[str, Any]:
    """Query GCP Cloud Logging for UDMI device and broker logs."""
    try:
        from google.cloud import logging_v2  # type: ignore
        client = logging_v2.Client(project=project_id)
        
        full_filter = filter_query
        if start_time:
            full_filter = f'{full_filter} timestamp >= "{start_time}"'
        if end_time:
            full_filter = f'{full_filter} timestamp <= "{end_time}"'

        entries = client.list_entries(filter_=full_filter, max_results=limit)
        results = []
        for entry in entries:
            results.append({
                "timestamp": entry.timestamp.isoformat() if entry.timestamp else None,
                "severity": entry.severity,
                "payload": entry.payload,
                "insert_id": entry.insert_id,
            })

        return {
            "status": "SUCCESS",
            "project_id": project_id,
            "filter": full_filter,
            "count": len(results),
            "entries": results,
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "project_id": project_id,
            "filter": filter_query,
            "error": str(e),
            "entries": [],
        }
