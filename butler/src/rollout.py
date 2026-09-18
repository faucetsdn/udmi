"""Butler staged configuration rollout management with persistent PostgreSQL backing.

Encapsulates declarative staged rollout campaign state and convergence tracking
within the Butler subsystem, storing all campaign records in the PostgreSQL
`udmi_rollouts` table. This ensures Butler can be terminated and restarted
at any time without loss of rollout state.
"""

import json
import sys
from typing import Any, Dict, List, Optional

try:
    from udmi.common.db.postgres import PostgresManager
except (ImportError, ModuleNotFoundError):
    try:
        from common.db.postgres import PostgresManager
    except (ImportError, ModuleNotFoundError):
        PostgresManager = None


ROLLOUTS_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS udmi_rollouts (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    target_filter JSONB DEFAULT '{}'::jsonb,
    target_subfolder VARCHAR(50) DEFAULT 'system',
    target_payload JSONB NOT NULL,
    status VARCHAR(50) DEFAULT 'RUNNING',
    batch_size INTEGER DEFAULT 10,
    batch_interval_sec INTEGER DEFAULT 60,
    total_devices INTEGER DEFAULT 0,
    converged_devices INTEGER DEFAULT 0,
    failed_devices INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
"""


_SENTINEL = object()


class RolloutManager:
    """Manages persistent declarative rollout campaigns backed by PostgreSQL."""

    def __init__(self, postgres_manager: Optional[PostgresManager] = _SENTINEL):
        if postgres_manager is _SENTINEL:
            try:
                self.postgres_manager = PostgresManager()
            except Exception:
                self.postgres_manager = None
        else:
            self.postgres_manager = postgres_manager

        if self.postgres_manager:
            self.init_table()

    def init_table(self) -> None:
        """Ensures the udmi_rollouts table exists in PostgreSQL."""
        if not self.postgres_manager:
            return
        try:
            self.postgres_manager.execute_sql(ROLLOUTS_TABLE_SQL)
        except Exception as e:
            print(f"Warning: Could not initialize udmi_rollouts table: {e}", file=sys.stderr)

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
        """Creates and persists a new declarative staged rollout campaign in PostgreSQL."""
        if not self.postgres_manager:
            raise RuntimeError("Butler PostgreSQL datastore is required for rollout persistence.")

        conn = self.postgres_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO udmi_rollouts (
                        name, target_filter, target_subfolder, target_payload,
                        status, batch_size, batch_interval_sec, total_devices,
                        converged_devices, failed_devices, created_at, updated_at
                    ) VALUES (
                        %s, %s, %s, %s,
                        'RUNNING', %s, %s, %s,
                        0, 0, NOW(), NOW()
                    )
                    RETURNING id, name, target_filter, target_subfolder, target_payload,
                              status, batch_size, batch_interval_sec, total_devices,
                              converged_devices, failed_devices, created_at, updated_at;
                    """,
                    (
                        name,
                        json.dumps(target_filter),
                        target_subfolder,
                        json.dumps(target_payload),
                        batch_size,
                        batch_interval_sec,
                        total_devices,
                    ),
                )
                row = cur.fetchone()
                conn.commit()
                return self._row_to_dict(row)
        finally:
            conn.close()

    def list_rollouts(self) -> List[Dict[str, Any]]:
        """Lists all rollout campaigns from PostgreSQL ordered by ID."""
        if not self.postgres_manager:
            raise RuntimeError("Butler PostgreSQL datastore is required for rollout persistence.")

        conn = self.postgres_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id, name, target_filter, target_subfolder, target_payload,
                           status, batch_size, batch_interval_sec, total_devices,
                           converged_devices, failed_devices, created_at, updated_at
                    FROM udmi_rollouts
                    ORDER BY id ASC;
                    """
                )
                rows = cur.fetchall()
                return [self._row_to_dict(row) for row in rows]
        finally:
            conn.close()

    def get_rollout(self, rollout_id: int) -> Optional[Dict[str, Any]]:
        """Fetches a single rollout campaign by ID from PostgreSQL."""
        if not self.postgres_manager:
            raise RuntimeError("Butler PostgreSQL datastore is required for rollout persistence.")

        conn = self.postgres_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id, name, target_filter, target_subfolder, target_payload,
                           status, batch_size, batch_interval_sec, total_devices,
                           converged_devices, failed_devices, created_at, updated_at
                    FROM udmi_rollouts
                    WHERE id = %s;
                    """,
                    (rollout_id,),
                )
                row = cur.fetchone()
                return self._row_to_dict(row) if row else None
        finally:
            conn.close()

    def update_rollout(
        self,
        rollout_id: int,
        status: Optional[str] = None,
        converged_devices: Optional[int] = None,
        failed_devices: Optional[int] = None,
    ) -> Optional[Dict[str, Any]]:
        """Updates rollout status or progress counters in PostgreSQL."""
        if not self.postgres_manager:
            raise RuntimeError("Butler PostgreSQL datastore is required for rollout persistence.")

        VALID_STATUSES = {"RUNNING", "PAUSED", "CANCELLED", "COMPLETED"}
        normalized_status = None
        if status:
            normalized_status = status.upper()
            if normalized_status not in VALID_STATUSES:
                raise ValueError(
                    f"Invalid rollout status '{status}'. Must be one of: {', '.join(sorted(VALID_STATUSES))}"
                )

        conn = self.postgres_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id, total_devices, converged_devices, status
                    FROM udmi_rollouts
                    WHERE id = %s
                    FOR UPDATE;
                    """,
                    (rollout_id,),
                )
                existing = cur.fetchone()
                if not existing:
                    return None

                total_devices = existing[1]
                current_conv = existing[2]
                new_conv = converged_devices if converged_devices is not None else current_conv

                final_status = normalized_status
                if final_status is None and new_conv >= total_devices:
                    final_status = "COMPLETED"

                update_parts = ["updated_at = NOW()"]
                params: List[Any] = []

                if final_status is not None:
                    update_parts.append("status = %s")
                    params.append(final_status)
                if converged_devices is not None:
                    update_parts.append("converged_devices = %s")
                    params.append(converged_devices)
                if failed_devices is not None:
                    update_parts.append("failed_devices = %s")
                    params.append(failed_devices)

                params.append(rollout_id)
                query = f"""
                    UPDATE udmi_rollouts
                    SET {', '.join(update_parts)}
                    WHERE id = %s
                    RETURNING id, name, target_filter, target_subfolder, target_payload,
                              status, batch_size, batch_interval_sec, total_devices,
                              converged_devices, failed_devices, created_at, updated_at;
                """
                cur.execute(query, tuple(params))
                row = cur.fetchone()
                conn.commit()
                return self._row_to_dict(row) if row else None
        finally:
            conn.close()

    def get_active_rollouts_count(self) -> int:
        """Returns the number of currently running rollouts in PostgreSQL."""
        if not self.postgres_manager:
            return 0
        conn = self.postgres_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT COUNT(*) FROM udmi_rollouts WHERE status = 'RUNNING';")
                row = cur.fetchone()
                return row[0] if row else 0
        finally:
            conn.close()

    def evaluate_convergence(
        self,
        registry_id: Optional[str],
        device_id: Optional[str],
        subfolder: Optional[str],
        payload: Optional[Dict[str, Any]] = None,
    ) -> List[Dict[str, Any]]:
        """Updates convergence for running rollouts when device state messages arrive."""
        if not self.postgres_manager or not subfolder:
            return []

        updated = []
        conn = self.postgres_manager.get_connection()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id, target_filter, total_devices, converged_devices
                    FROM udmi_rollouts
                    WHERE status = 'RUNNING' AND target_subfolder = %s
                    FOR UPDATE;
                    """,
                    (subfolder,),
                )
                running = cur.fetchall()
                for row_data in running:
                    if len(row_data) == 4:
                        r_id, raw_filter, tot, conv = row_data
                    else:
                        r_id, tot, conv = row_data
                        raw_filter = {}

                    target_filter = (
                        raw_filter
                        if isinstance(raw_filter, dict)
                        else (json.loads(raw_filter) if raw_filter else {})
                    )
                    if target_filter:
                        if "registry_id" in target_filter and registry_id and target_filter["registry_id"] != registry_id:
                            continue
                        if "device_id" in target_filter and device_id and target_filter["device_id"] != device_id:
                            continue
                        if "device_ids" in target_filter and device_id and device_id not in target_filter["device_ids"]:
                            continue

                    new_conv = min(tot, conv + 1) if tot > 0 else conv + 1
                    new_st = "COMPLETED" if (tot > 0 and new_conv >= tot) else "RUNNING"
                    cur.execute(
                        """
                        UPDATE udmi_rollouts
                        SET converged_devices = %s, status = %s, updated_at = NOW()
                        WHERE id = %s
                        RETURNING id, name, target_filter, target_subfolder, target_payload,
                                  status, batch_size, batch_interval_sec, total_devices,
                                  converged_devices, failed_devices, created_at, updated_at;
                        """,
                        (new_conv, new_st, r_id),
                    )
                    row = cur.fetchone()
                    if row:
                        updated.append(self._row_to_dict(row))
                conn.commit()
            return updated
        finally:
            conn.close()

    @staticmethod
    def _row_to_dict(row: tuple) -> Dict[str, Any]:
        """Converts a database row tuple into a standard rollout dictionary."""
        (
            r_id,
            name,
            target_filter,
            target_subfolder,
            target_payload,
            status,
            batch_size,
            batch_interval_sec,
            total_devices,
            converged_devices,
            failed_devices,
            created_at,
            updated_at,
        ) = row

        if isinstance(target_filter, str):
            try:
                target_filter = json.loads(target_filter)
            except Exception:
                pass
        if isinstance(target_payload, str):
            try:
                target_payload = json.loads(target_payload)
            except Exception:
                pass

        return {
            "id": r_id,
            "name": name,
            "target_filter": target_filter or {},
            "target_subfolder": target_subfolder,
            "target_payload": target_payload or {},
            "status": status,
            "batch_size": batch_size,
            "batch_interval_sec": batch_interval_sec,
            "total_devices": total_devices,
            "converged_devices": converged_devices,
            "failed_devices": failed_devices,
            "created_at": created_at.isoformat() if hasattr(created_at, "isoformat") else str(created_at),
            "updated_at": updated_at.isoformat() if hasattr(updated_at, "isoformat") else str(updated_at),
        }
