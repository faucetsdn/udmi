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

    def test_model_discovery_handler(self):
        handler = DiscoveryHandler()
        self.assertTrue(handler.can_handle("discovery", "events", {}))
        self.assertFalse(handler.can_handle("discovery", "state", {}))

        start_env = {"deviceRegistryId": "reg-1"}
        start_payload = {
            "family": "iot",
            "source": "model",
            "generation": "2026-08-21T00:00:00Z",
            "event_no": 0,
            "addrs": ["AHU-1", "DEL-1"],
            "active_count": 1,
        }
        start_res = handler.process_message(start_env, start_payload, self.mock_pg, self.mock_influx)
        self.assertEqual(start_res["count"], 0)
        self.assertEqual(start_res["addrs"], ["AHU-1", "DEL-1"])
        self.mock_pg.insert_row.assert_not_called()

        envelope = {"deviceId": "AHU-1", "deviceRegistryId": "reg-1"}
        payload = {
            "family": "iot",
            "generation": "2026-08-21T00:00:00Z",
            "event_no": 1,
            "addr": "AHU-1",
            "model": {
                "version": "1.5.7",
                "timestamp": "2026-08-21T00:00:00Z",
                "system": {
                    "hardware": {"make": "Acme", "model": "AHU-Pro"},
                    "serial_no": "SN-AHU-1",
                    "location": {"site": "reg-1", "section": "Floor-2", "position": {"x": 1, "y": 2}},
                },
                "localnet": {
                    "families": {
                        "bacnet": {"addr": "41122"},
                        "ipv4": {"addr": "10.0.0.10"},
                    }
                },
            },
        }

        res = handler.process_message(envelope, payload, self.mock_pg, self.mock_influx)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_discovery")
        self.assertEqual(self.mock_pg.insert_row.call_count, 2)
        disc_table, disc_row = self.mock_pg.insert_row.call_args_list[0][0]
        meta_table, meta_row = self.mock_pg.insert_row.call_args_list[1][0]
        self.assertEqual(disc_table, "udmi_discovery")
        self.assertEqual(disc_row["scan_family"], "iot")
        self.assertEqual(disc_row["bacnet_addr"], "41122")
        self.assertEqual(disc_row["ipv4_addr"], "10.0.0.10")
        self.assertEqual(disc_row["hardware_make"], "Acme")
        self.assertEqual(meta_table, "udmi_metadata")
        self.assertEqual(meta_row["device_id"], "AHU-1")
        self.assertEqual(meta_row["metadata"]["system"]["serial_no"], "SN-AHU-1")
        self.mock_pg.execute_sql.assert_called_once_with(
            "DELETE FROM udmi_metadata WHERE device_registry_id = %s AND device_id IN (%s)",
            ("reg-1", "DEL-1"),
        )

    def test_provider_discovered_models_deletion_reconciliation(self):
        from mcp.butler.provider import ButlerProvider

        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        mock_cur.fetchall.side_effect = [
            [
                ("AHU-1", {"system": {"serial_no": "SN-OLD"}}),
                ("DEL-1", {"system": {"serial_no": "SN-DEL"}}),
                ("VAV-1", {"system": {"serial_no": "SN-VAV"}}),
            ],
            [
                (
                    None,
                    "events",
                    "discovery",
                    {
                        "family": "iot",
                        "generation": "2026-08-21T01:00:00Z",
                        "event_no": 0,
                        "addrs": ["AHU-1", "DEL-1"],
                        "active_count": 1,
                    },
                ),
                (
                    "AHU-1",
                    "events",
                    "discovery",
                    {
                        "family": "iot",
                        "generation": "2026-08-21T01:00:00Z",
                        "event_no": 1,
                        "addr": "AHU-1",
                        "model": {"system": {"serial_no": "SN-NEW"}},
                    },
                ),
                (
                    None,
                    "events",
                    "discovery",
                    {
                        "family": "iot",
                        "generation": "2026-08-21T02:00:00Z",
                        "event_no": 0,
                        "active_count": 2,
                    },
                ),
            ],
        ]
        self.mock_pg.get_connection.return_value = mock_conn
        provider = ButlerProvider(pg_manager=self.mock_pg, influx_manager=self.mock_influx)
        models = provider.get_discovered_models("reg-1")
        self.assertEqual(set(models.keys()), {"AHU-1", "VAV-1"})
        self.assertEqual(models["AHU-1"]["system"]["serial_no"], "SN-NEW")

    def test_registry_model_discovery_topics(self):
        from butler.src.connection import ButlerConnection
        conn = ButlerConnection("//mqtt/localhost:18833", registry_id="ZZ-TRI-FECTA")
        self.assertEqual(
            conn.get_discovery_topics(None),
            ["/uufi/r/ZZ-TRI-FECTA/c/config/discovery"],
        )
        self.assertEqual(
            conn.get_discovery_topics("GAT-123"),
            ["/uufi/r/ZZ-TRI-FECTA/d/GAT-123/c/config/discovery"],
        )

    def test_mapping_with_discovered_models(self):
        from unittest.mock import patch
        from butler.src.mapping import run_mapping

        mock_butler = MagicMock()
        mock_butler.get_discovered_models.return_value = {
            "AHU-1": {
                "version": "1.5.2",
                "timestamp": "2026-08-21T00:00:00Z",
                "system": {"serial_no": "SN-AHU-1"},
                "localnet": {
                    "families": {
                        "bacnet": {"addr": "41122"},
                    }
                },
            }
        }
        mock_butler.get_discovered_devices.return_value = [
            {
                "gateway_id": "GAT-123",
                "generation": "2026-08-21T00:00:00Z",
                "bacnet": "41122",
                "ipv4": "192.168.1.50",
                "vendor": None,
                "timestamp": "2026-08-21T00:00:01Z",
            }
        ]

        with patch("butler.src.mapping.ButlerConnection") as mock_conn_cls:
            mock_conn = MagicMock()
            mock_conn.actual_registry = "ZZ-TRI-FECTA"
            mock_conn.project = "localhost"
            mock_conn.get_propose_topics.side_effect = (
                lambda dev_id, sf="system": [f"/uufi/r/ZZ-TRI-FECTA/d/{dev_id}/c/propose/{sf}"]
            )
            mock_conn_cls.return_value = mock_conn

            run_mapping("//mqtt/localhost:18833", "ZZ-TRI-FECTA", site_model=None, butler_client=mock_butler)
            mock_conn.publish_messages.assert_called_once()
            published_pairs = mock_conn.publish_messages.call_args[0][0]
            topics = [t for t, _ in published_pairs]
            self.assertIn("/uufi/r/ZZ-TRI-FECTA/d/AHU-1/c/propose/localnet", topics)


if __name__ == "__main__":
    unittest.main()
