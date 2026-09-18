"""Unit tests for Butler persistent RolloutManager."""

from datetime import datetime, timezone
import json
import os
import sys
import unittest
from unittest.mock import MagicMock

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, REPO_ROOT)

from butler.src.rollout import RolloutManager


class RolloutManagerTests(unittest.TestCase):

    def setUp(self):
        self.mock_pg = MagicMock()
        self.mock_conn = MagicMock()
        self.mock_cur = MagicMock()
        self.mock_conn.cursor.return_value.__enter__.return_value = self.mock_cur
        self.mock_pg.get_connection.return_value = self.mock_conn
        self.manager = RolloutManager(postgres_manager=self.mock_pg)

    def test_init_table_called(self):
        self.mock_pg.execute_sql.assert_called_once()
        sql_arg = self.mock_pg.execute_sql.call_args[0][0]
        self.assertIn("CREATE TABLE IF NOT EXISTS udmi_rollouts", sql_arg)

    def test_create_rollout(self):
        now = datetime(2026, 9, 10, 10, 0, 0, tzinfo=timezone.utc)
        self.mock_cur.fetchone.return_value = (
            1,
            "Upgrade Fleet",
            {"make": "Acme"},
            "system",
            {"system": {"software": {"system": "2.0.0"}}},
            "RUNNING",
            5,
            60,
            10,
            0,
            0,
            now,
            now,
        )

        rollout = self.manager.create_rollout(
            name="Upgrade Fleet",
            target_filter={"make": "Acme"},
            target_payload={"system": {"software": {"system": "2.0.0"}}},
            batch_size=5,
            total_devices=10,
        )

        self.assertEqual(rollout["id"], 1)
        self.assertEqual(rollout["name"], "Upgrade Fleet")
        self.assertEqual(rollout["status"], "RUNNING")
        self.assertEqual(rollout["batch_size"], 5)
        self.assertEqual(rollout["total_devices"], 10)
        self.assertEqual(rollout["converged_devices"], 0)
        self.mock_conn.commit.assert_called_once()

    def test_list_rollouts(self):
        now = datetime(2026, 9, 10, 10, 0, 0, tzinfo=timezone.utc)
        self.mock_cur.fetchall.return_value = [
            (
                1,
                "Upgrade Fleet",
                {"make": "Acme"},
                "system",
                {"system": {"software": {"system": "2.0.0"}}},
                "RUNNING",
                5,
                60,
                10,
                0,
                0,
                now,
                now,
            )
        ]

        rollouts = self.manager.list_rollouts()
        self.assertEqual(len(rollouts), 1)
        self.assertEqual(rollouts[0]["id"], 1)
        self.assertEqual(rollouts[0]["name"], "Upgrade Fleet")

    def test_get_rollout(self):
        now = datetime(2026, 9, 10, 10, 0, 0, tzinfo=timezone.utc)
        self.mock_cur.fetchone.return_value = (
            1,
            "Upgrade Fleet",
            {"make": "Acme"},
            "system",
            {"system": {"software": {"system": "2.0.0"}}},
            "RUNNING",
            5,
            60,
            10,
            0,
            0,
            now,
            now,
        )

        rollout = self.manager.get_rollout(1)
        self.assertIsNotNone(rollout)
        self.assertEqual(rollout["id"], 1)

    def test_get_rollout_not_found(self):
        self.mock_cur.fetchone.return_value = None
        rollout = self.manager.get_rollout(999)
        self.assertIsNone(rollout)

    def test_update_rollout(self):
        now = datetime(2026, 9, 10, 10, 0, 0, tzinfo=timezone.utc)
        # 1. Existing row lookup
        self.mock_cur.fetchone.side_effect = [
            (1, 10, 0, "RUNNING"),  # existing row from SELECT FOR UPDATE
            (
                1,
                "Upgrade Fleet",
                {"make": "Acme"},
                "system",
                {"system": {"software": {"system": "2.0.0"}}},
                "PAUSED",
                5,
                60,
                10,
                0,
                0,
                now,
                now,
            ),
        ]

        updated = self.manager.update_rollout(1, status="PAUSED")
        self.assertIsNotNone(updated)
        self.assertEqual(updated["status"], "PAUSED")

    def test_update_rollout_invalid_status(self):
        with self.assertRaises(ValueError):
            self.manager.update_rollout(1, status="INVALID_STATUS")

    def test_update_rollout_auto_complete(self):
        now = datetime(2026, 9, 10, 10, 0, 0, tzinfo=timezone.utc)
        self.mock_cur.fetchone.side_effect = [
            (1, 10, 5, "RUNNING"),  # existing
            (
                1,
                "Upgrade Fleet",
                {"make": "Acme"},
                "system",
                {"system": {"software": {"system": "2.0.0"}}},
                "COMPLETED",
                5,
                60,
                10,
                10,
                0,
                now,
                now,
            ),
        ]

        updated = self.manager.update_rollout(1, converged_devices=10)
        self.assertIsNotNone(updated)
        self.assertEqual(updated["status"], "COMPLETED")
        self.assertEqual(updated["converged_devices"], 10)

    def test_get_active_rollouts_count(self):
        self.mock_cur.fetchone.return_value = (2,)
        count = self.manager.get_active_rollouts_count()
        self.assertEqual(count, 2)

    def test_evaluate_convergence(self):
        now = datetime(2026, 9, 10, 10, 0, 0, tzinfo=timezone.utc)
        self.mock_cur.fetchall.return_value = [(1, 10, 2)]
        self.mock_cur.fetchone.return_value = (
            1,
            "Upgrade Fleet",
            {"make": "Acme"},
            "system",
            {"system": {"software": {"system": "2.0.0"}}},
            "RUNNING",
            5,
            60,
            10,
            3,
            0,
            now,
            now,
        )

        res = self.manager.evaluate_convergence("REG-1", "DEV-1", "system", {})
        self.assertEqual(len(res), 1)
        self.assertEqual(res[0]["converged_devices"], 3)
        self.assertEqual(res[0]["status"], "RUNNING")

    def test_evaluate_convergence_filter_match_and_mismatch(self):
        now = datetime(2026, 9, 10, 10, 0, 0, tzinfo=timezone.utc)
        # One rollout matches DEV-1, another targets DEV-2
        self.mock_cur.fetchall.return_value = [
            (1, {"device_id": "DEV-1"}, 10, 2),
            (2, {"device_id": "DEV-2"}, 10, 1),
        ]
        self.mock_cur.fetchone.return_value = (
            1,
            "Upgrade DEV-1",
            {"device_id": "DEV-1"},
            "system",
            {"system": {}},
            "RUNNING",
            5,
            60,
            10,
            3,
            0,
            now,
            now,
        )

        res = self.manager.evaluate_convergence("REG-1", "DEV-1", "system", {})
        self.assertEqual(len(res), 1)
        self.assertEqual(res[0]["id"], 1)

        # Non-matching device should update nothing
        res_none = self.manager.evaluate_convergence("REG-1", "DEV-OTHER", "system", {})
        self.assertEqual(len(res_none), 0)

    def test_fail_fast_without_postgres(self):
        manager = RolloutManager(postgres_manager=None)
        manager.postgres_manager = None
        with self.assertRaises(RuntimeError):
            manager.create_rollout(
                name="Test",
                target_filter={},
                target_payload={},
            )
        with self.assertRaises(RuntimeError):
            manager.list_rollouts()
        with self.assertRaises(RuntimeError):
            manager.get_rollout(1)
        with self.assertRaises(RuntimeError):
            manager.update_rollout(1, status="PAUSED")


if __name__ == "__main__":
    unittest.main()
