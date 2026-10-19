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

    def test_dispatch_upload_discovery_fallback(self):
        envelope = {"subFolder": "discovery", "subType": "upload", "deviceId": "DEV-1"}
        payload = {"family": "bacnet", "generation": "2026-04-06T12:00:00Z", "event_no": 0}

        res = self.dispatcher.dispatch(envelope, payload)
        self.assertEqual(res["target"], "postgres")
        self.assertEqual(res["table"], "udmi_messages")

    def test_dispatch_download_ignored(self):
        envelope = {"subFolder": "blobset", "subType": "download", "deviceId": "DEV-1"}
        payload = {"blobs": {"_iot_endpoint_config": {"hostname": "mqtt.example.com"}}}

        res = self.dispatcher.dispatch(envelope, payload)
        self.assertEqual(res["target"], "ignored")
        self.assertEqual(res["count"], 0)
        self.mock_pg.insert_raw_message.assert_not_called()

    def test_reassemble_upload(self):
        import base64
        import hashlib
        import json
        import tempfile
        from butler.src.reassemble import run_reassemble

        raw_bytes = b"\xa1\xb2\xc3\xd4PCAP-PAYLOAD"
        sha256_hex = hashlib.sha256(raw_bytes).hexdigest()
        lines = [
            json.dumps({"family": "ether", "generation": "2026-07-08T14:35:00Z", "event_no": 0}),
            json.dumps({
                "family": "ether",
                "generation": "2026-07-08T14:35:00Z",
                "event_no": 1,
                "data": base64.b64encode(raw_bytes).decode(),
            }),
            json.dumps({
                "family": "ether",
                "generation": "2026-07-08T14:35:00Z",
                "event_no": -2,
                "sha256": sha256_hex,
            }),
        ]
        with tempfile.TemporaryDirectory() as tmpdir:
            in_path = os.path.join(tmpdir, "stream.jsonl")
            out_path = os.path.join(tmpdir, "out.pcap")
            with open(in_path, "w", encoding="utf-8") as f:
                f.write("\n".join(lines))
            run_reassemble(in_path, out_path)
            with open(out_path, "rb") as f:
                self.assertEqual(f.read(), raw_bytes)


if __name__ == "__main__":
    unittest.main()
