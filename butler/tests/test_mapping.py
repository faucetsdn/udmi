"""Unit tests for Butler mapping with optional localnet.families."""

import json
import os
import sys
import tempfile
import unittest
from unittest.mock import MagicMock

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
sys.path.insert(0, os.path.join(REPO_ROOT, "common", "src", "main", "python"))
sys.path.insert(0, os.path.join(REPO_ROOT, "gencode", "python"))
sys.path.insert(0, REPO_ROOT)

from butler.src.mapping import _get_family_addr, run_mapping


class MappingTests(unittest.TestCase):
    """Tests Butler mapping against optional or null localnet.families models."""

    def test_get_family_addr_handles_missing_and_none_fields(self):
        self.assertIsNone(_get_family_addr(None, "bacnet"))
        self.assertIsNone(_get_family_addr({}, "bacnet"))
        self.assertIsNone(_get_family_addr({"localnet": None}, "bacnet"))
        self.assertIsNone(
            _get_family_addr(
                {"localnet": {"parent": {"target": "GAT-1"}, "families": None}},
                "bacnet",
            )
        )
        self.assertIsNone(
            _get_family_addr({"localnet": {"families": {"bacnet": None}}}, "bacnet")
        )
        self.assertEqual(
            _get_family_addr(
                {"localnet": {"families": {"bacnet": {"addr": "1001"}}}}, "bacnet"
            ),
            "1001",
        )

    def test_run_mapping_preserves_parent_only_and_null_families_devices(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            sns_dir = os.path.join(tmpdir, "devices", "SNS-8")
            trh_dir = os.path.join(tmpdir, "devices", "TRH-1")
            os.makedirs(sns_dir)
            os.makedirs(trh_dir)

            with open(os.path.join(sns_dir, "metadata.json"), "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "version": "1.5.7",
                        "timestamp": "2026-01-01T00:00:00Z",
                        "localnet": {
                            "parent": {"target": "GAT-123", "family": "hardwired"}
                        },
                    },
                    f,
                )

            with open(os.path.join(trh_dir, "metadata.json"), "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "version": "1.5.7",
                        "timestamp": "2026-01-01T00:00:00Z",
                        "localnet": {
                            "parent": {"target": "GAT-123", "family": "hardwired"},
                            "families": None,
                        },
                    },
                    f,
                )

            mock_butler = MagicMock()
            mock_butler.get_discovered_devices.return_value = [
                {
                    "bacnet": "4200",
                    "ipv4": None,
                    "vendor": None,
                    "gateway_id": "GAT-123",
                    "generation": "2026-01-02T00:00:00Z",
                }
            ]

            run_mapping(None, "ZZ-TRI-FECTA", site_model=tmpdir, butler_client=mock_butler)

            with open(os.path.join(sns_dir, "metadata.json"), "r", encoding="utf-8") as f:
                sns_out = json.load(f)
            self.assertEqual(sns_out["localnet"]["parent"]["target"], "GAT-123")
            self.assertNotIn("families", sns_out["localnet"])

            with open(os.path.join(tmpdir, "devices", "UNK-1", "metadata.json"), "r", encoding="utf-8") as f:
                unk_out = json.load(f)
            self.assertEqual(unk_out["localnet"]["families"]["bacnet"]["addr"], "4200")


if __name__ == "__main__":
    unittest.main()
