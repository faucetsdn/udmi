"""Unit tests for mantis.tools.database."""

import pytest
from mantis.session import SessionManager
from mantis.tools.database import query_database


def test_query_database_mutating_rejected():
    mgr = SessionManager()
    with pytest.raises(ValueError, match="Mutating query rejected"):
        query_database(mgr, "test_1", "postgres", "DROP TABLE devices;")

    with pytest.raises(ValueError, match="Mutating query rejected"):
        query_database(mgr, "test_1", "influx", "DELETE FROM pointset;")


def test_query_database_unsupported_type():
    mgr = SessionManager()
    with pytest.raises(ValueError, match="Unsupported database_type"):
        query_database(mgr, "test_1", "oracle", "SELECT * FROM devices;")


def test_query_database_read_only_identifiers_allowed(monkeypatch):
    mgr = SessionManager()
    # Mock urllib for influx to verify SELECT last_update is not rejected by guard
    from unittest.mock import MagicMock
    import io
    mock_resp = MagicMock()
    mock_resp.read.return_value = b'{"results": []}'
    mock_resp.__enter__.return_value = mock_resp
    monkeypatch.setattr("urllib.request.urlopen", MagicMock(return_value=mock_resp))

    res = query_database(mgr, "test_1", "influx", "SELECT last_update, updated_at FROM devices;")
    assert res["status"] == "SUCCESS"


def test_query_database_extended_mutating_rejected():
    mgr = SessionManager()
    for q, kw in [
        ("MERGE target USING source ON (id) ...", "merge"),
        ("CALL execute_maintenance()", "call"),
        ("SET statement_timeout = 0;", "set"),
        ("SELECT * INTO backup_table FROM devices;", "into"),
    ]:
        with pytest.raises(ValueError, match=f"found '{kw}'"):
            query_database(mgr, "test_1", "postgres", q)


def test_query_database_influx_row_truncation(monkeypatch):
    mgr = SessionManager()
    import io
    from unittest.mock import MagicMock
    # Return 150 values in a series
    mock_data = {
        "results": [{
            "series": [{
                "name": "pointset",
                "columns": ["time", "value"],
                "values": [[f"2026-09-15T10:00:{i:02d}Z", i] for i in range(150)],
            }]
        }]
    }
    mock_resp = MagicMock()
    import json
    mock_resp.read.return_value = json.dumps(mock_data).encode("utf-8")
    mock_resp.__enter__.return_value = mock_resp
    monkeypatch.setattr("urllib.request.urlopen", MagicMock(return_value=mock_resp))

    res = query_database(mgr, "test_1", "influx", "SELECT * FROM pointset;", max_rows=50)
    assert res["status"] == "SUCCESS"
    assert res["truncated"] is True
    assert len(res["results"][0]["series"][0]["values"]) == 50

