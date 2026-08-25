"""Unit tests for Butler message handlers."""

import os
import sys
import unittest
from unittest.mock import MagicMock

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, REPO_ROOT)

from butler.src.handlers.alarms import AlarmsHandler
from butler.src.handlers.discovery import DiscoveryHandler
from butler.src.handlers.metadata import MetadataHandler
from butler.src.handlers.point_state import PointStateHandler
from butler.src.handlers.pointset_events import PointsetEventsHandler
from butler.src.handlers.raw_fallback import RawFallbackHandler
from butler.src.handlers.system_state import SystemStateHandler
from butler.src.handlers.validation import ValidationHandler


class HandlerTests(unittest.TestCase):

    def setUp(self):
        self.mock_pg = MagicMock()
        self.mock_influx = MagicMock()

    def test_pointset_events_handler(self):
        handler = PointsetEventsHandler()
        self.assertTrue(handler.can_handle("pointset", "events", {"points": {}}))
        self.assertFalse(handler.can_handle("system", "state", {}))

        self.mock_influx.write_pointset_payload.return_value = 2
        envelope = {"deviceId": "AHU-1", "deviceRegistryId": "reg-1", "projectId": "proj-1"}
        payload = {"points": {"filter_alarm": {"present_value": True}, "temp": {"present_value": 72.5}}}

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "influx")
        self.assertEqual(res["count"], 2)
        self.mock_influx.write_pointset_payload.assert_called_once_with(envelope, payload)

    def test_point_state_handler(self):
        handler = PointStateHandler()
        self.assertTrue(handler.can_handle("pointset", "state", {}))
        self.assertFalse(handler.can_handle("pointset", "events", {}))

        envelope = {"deviceId": "AHU-1", "deviceRegistryId": "reg-1", "messageId": "msg-123"}
        payload = {
            "timestamp": "2026-08-21T12:00:00Z",
            "points": {
                "temp": {
                    "value_state": "ok",
                    "units": "Celsius",
                    "status": {"level": 100, "message": "Normal"},
                }
            },
        }

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_point_state")
        self.assertEqual(res["count"], 1)
        self.mock_pg.insert_rows.assert_called_once()
        inserted_rows = self.mock_pg.insert_rows.call_args[0][1]
        self.assertEqual(len(inserted_rows), 1)
        self.assertEqual(inserted_rows[0]["point_name"], "temp")
        self.assertEqual(inserted_rows[0]["units"], "Celsius")
        self.assertEqual(inserted_rows[0]["level"], 100)

    def test_system_state_handler(self):
        handler = SystemStateHandler()
        self.assertTrue(handler.can_handle("system", "state", {}))
        self.assertFalse(handler.can_handle("system", "events", {}))

        envelope = {"deviceId": "AHU-1", "deviceRegistryId": "reg-1"}
        payload = {
            "serial_no": "SN-9988",
            "hardware": {"make": "Bosch", "model": "Ctrl-X", "rev": "v2", "sku": "SKU-1"},
            "software": {"firmware": "1.2.3", "kernel": "5.4.0"},
        }

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_system_state")
        self.mock_pg.insert_row.assert_called_once()
        inserted_row = self.mock_pg.insert_row.call_args[0][1]
        self.assertEqual(inserted_row["serial_no"], "SN-9988")
        self.assertEqual(inserted_row["make"], "Bosch")
        self.assertEqual(len(inserted_row["software"]), 2)

    def test_discovery_handler(self):
        handler = DiscoveryHandler()
        self.assertTrue(handler.can_handle("discovery", "events", {}))

        envelope = {"deviceId": "GAT-1", "deviceRegistryId": "reg-1"}
        payload = {
            "family": "ether",
            "generation": "2026-08-21T00:00:00Z",
            "addr": "00:11:22:33:44:55",
            "system": {
                "hardware": {"make": "Cisco", "model": "Catalyst"},
                "serial_no": "CS-1234",
            },
            "status": {"level": 200, "message": "Discovery complete"},
            "refs": {
                "80": {"adjunct": {"state": "open", "service": "http", "product": "Apache"}},
            },
        }

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_discovery")
        self.mock_pg.insert_row.assert_called_once()
        row = self.mock_pg.insert_row.call_args[0][1]
        self.assertEqual(row["ether_addr"], "00:11:22:33:44:55")
        self.assertEqual(len(row["ports"]), 1)
        self.assertEqual(row["ports"][0]["port"], "80")

    def test_validation_handler(self):
        handler = ValidationHandler()
        self.assertTrue(handler.can_handle("validation", "events", {}))

        envelope = {"deviceId": "DEV-1", "deviceRegistryId": "reg-1"}
        payload = {
            "sub_type": "state",
            "sub_folder": "system",
            "status": {"level": 500, "category": "validation.schema", "message": "Schema error"},
            "errors": [{"message": "Missing required field", "level": 500, "category": "schema"}],
        }

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_validation")
        self.mock_pg.insert_row.assert_called_once()
        row = self.mock_pg.insert_row.call_args[0][1]
        self.assertEqual(row["level"], 500)
        self.assertEqual(len(row["errors"]), 1)

    def test_alarms_handler(self):
        handler = AlarmsHandler()
        self.assertTrue(handler.can_handle("alarms", "events", {}))

        envelope = {"deviceId": "DEV-1", "deviceRegistryId": "reg-1"}
        payload = {
            "alarm_type": "HIGH_TEMP",
            "alarm_priority": "CRITICAL",
            "fault": True,
            "in_alarm": "true",
            "message_text": "Temperature exceeded limit",
        }

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_alarms")
        self.mock_pg.insert_row.assert_called_once()
        row = self.mock_pg.insert_row.call_args[0][1]
        self.assertEqual(row["alarm_type"], "HIGH_TEMP")
        self.assertEqual(row["fault"], True)
        self.assertEqual(row["in_alarm"], True)

    def test_raw_fallback_handler(self):
        handler = RawFallbackHandler()
        self.assertTrue(handler.can_handle("unknown", "unknown", {}))

        envelope = {
            "projectId": "proj-1",
            "deviceRegistryId": "reg-1",
            "deviceId": "DEV-1",
            "subFolder": "custom",
            "subType": "events",
            "publishTime": "2026-08-21T12:00:00Z",
        }
        payload = {"arbitrary": "data", "value": 123}

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_messages")
        self.mock_pg.insert_row.assert_called_once()
        row = self.mock_pg.insert_row.call_args[0][1]
        self.assertEqual(row["project_id"], "proj-1")
        self.assertEqual(row["payload"], payload)


if __name__ == "__main__":
    unittest.main()
