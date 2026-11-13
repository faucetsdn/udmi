"""Unit tests for Butler MessageDispatcher."""

import os
import sys
import unittest
from unittest.mock import MagicMock

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, REPO_ROOT)

from butler.src.dispatcher import MessageDispatcher


class DispatcherTests(unittest.TestCase):

    def setUp(self):
        self.mock_pg = MagicMock()
        self.mock_influx = MagicMock()
        self.dispatcher = MessageDispatcher(
            postgres_manager=self.mock_pg,
            influx_manager=self.mock_influx,
        )

    def test_dispatch_pointset_events(self):
        self.mock_influx.write_pointset_payload.return_value = 1
        envelope = {"subFolder": "pointset", "subType": "events", "deviceId": "DEV-1"}
        payload = {"points": {"p1": {"present_value": 10}}}

        res = self.dispatcher.dispatch(envelope, payload)
        self.assertEqual(res["target"], "influx")
        self.assertEqual(res["count"], 1)

    def test_dispatch_point_state(self):
        envelope = {"subFolder": "pointset", "subType": "state", "deviceId": "DEV-1"}
        payload = {"points": {"p1": {"value_state": "ok"}}}

        res = self.dispatcher.dispatch(envelope, payload)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_point_state")

    def test_dispatch_system_state(self):
        envelope = {"subFolder": "system", "subType": "state", "deviceId": "DEV-1"}
        payload = {"hardware": {"make": "Acme"}}

        res = self.dispatcher.dispatch(envelope, payload)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_system_state")

    def test_dispatch_fallback(self):
        envelope = {"subFolder": "unknown_folder", "subType": "unknown_type", "deviceId": "DEV-1"}
        payload = {"msg": "hello"}

        res = self.dispatcher.dispatch(envelope, payload)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_messages")

    def test_dispatch_pcap_upload_skips_raw_fallback(self):
        import base64
        import gzip
        import hashlib
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            self.dispatcher.handlers[0].archive_dir = tmpdir
            raw_pcap = b"\xa1\xb2\xc3\xd4" + b"\x00" * 20
            b64_chunk = base64.b64encode(gzip.compress(raw_pcap)).decode("ascii")
            digest = hashlib.sha256(raw_pcap).hexdigest()

            env = {
                "deviceRegistryId": "ZZ-TRI-FECTA",
                "deviceId": "DSN-1",
                "subType": "upload",
                "subFolder": "discovery",
            }
            self.dispatcher.dispatch(
                env,
                {"generation": "2026-11-10T12:00:00Z", "family": "bacnet", "event_no": 0},
            )
            self.dispatcher.dispatch(
                env,
                {
                    "generation": "2026-11-10T12:00:00Z",
                    "family": "bacnet",
                    "event_no": 1,
                    "data": b64_chunk,
                },
            )
            res = self.dispatcher.dispatch(
                env,
                {
                    "generation": "2026-11-10T12:00:00Z",
                    "family": "bacnet",
                    "event_no": -2,
                    "sha256": digest,
                    "scan_manifest": {
                        "gateway_ip": "192.168.1.50",
                        "interface": "eth0",
                        "filter": "udp port 47808",
                        "scan_round_timestamps": ["2026-11-10T12:00:01Z"],
                    },
                },
            )
            self.assertEqual(res["status"], "COMPLETED")
            self.assertEqual(res["sha256"], digest)
            self.assertTrue(os.path.exists(res["local_pcap_path"]))
            # Verify raw_fallback never wrote chunk blobs into udmi_messages
            for call in self.mock_pg.insert_row.call_args_list:
                self.assertNotEqual(call[0][0], "udmi_messages")


if __name__ == "__main__":
    unittest.main()
