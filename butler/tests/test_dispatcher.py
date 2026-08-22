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


if __name__ == "__main__":
    unittest.main()
