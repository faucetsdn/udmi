"""Unit tests for BDNS-compliant device ID allocation in bin/dbo_merge."""

import importlib.machinery
import importlib.util
import json
from pathlib import Path
import re
import tempfile
import unittest
import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
BDNS_RE = re.compile(r"^[A-Z]{2,6}-[1-9][0-9]*\Z")


def _load_dbo_merge():
  script_path = REPO_ROOT / "bin" / "dbo_merge"
  loader = importlib.machinery.SourceFileLoader("dbo_merge", str(script_path))
  spec = importlib.util.spec_from_loader(loader.name, loader)
  module = importlib.util.module_from_spec(spec)
  loader.exec_module(module)
  return module


DBO_MERGE = _load_dbo_merge()


class DboMergeBdnsDeviceIdTest(unittest.TestCase):
  """Verify bin/dbo_merge assigns BDNS-compliant IDs to virtual devices."""

  def _write_device(self, site_dir: Path, device_id: str, metadata: dict):
    dev_dir = site_dir / "devices" / device_id
    dev_dir.mkdir(parents=True, exist_ok=True)
    (dev_dir / "metadata.json").write_text(
        json.dumps(metadata, indent=2), encoding="utf-8"
    )

  def _merge(self, tmp_path: Path, site_dir: Path, dbo_config: dict):
    yaml_path = tmp_path / "building_config.yaml"
    yaml_path.write_text(
        yaml.safe_dump(dbo_config, sort_keys=False), encoding="utf-8"
    )
    DBO_MERGE.merge_dbo_config(yaml_path, site_dir)

  def test_virtual_devices_get_bdns_ids_and_facilities_preserved(self):
    with tempfile.TemporaryDirectory() as tmp:
      tmp_path = Path(tmp)
      site_dir = tmp_path / "site_model"
      dbo_config = {
          "guid-bldg": {
              "code": "ZZ-TRI-FECTA",
              "type": "FACILITIES/BUILDING",
          },
          "guid-floor": {
              "code": "ZZ-TRI-FECTA-1",
              "type": ["FACILITIES/FLOOR"],
          },
          "guid-ashp-2": {
              "code": "ZZ-TRI-FECTA:ASHP:HP 2-1B",
              "type": "HVAC/ASHP_SS_HP2SWDSC",
          },
          "guid-ashp-1": {
              "code": "ZZ-TRI-FECTA:ASHP:HP 1-1A",
              "type": "HVAC/ASHP_SS_HP2SWDSC",
          },
          "guid-chws": {
              "code": "ZZ-TRI-FECTA:CHWS:CHW_SYSTEM",
          },
          "guid-typeless": {
              "code": "device2720802",
              "type": "UNTYPED/TYPELESS",
          },
          "guid-list-type": {
              "code": "ZZ-TRI-FECTA:VAV:VAVCO 1-1",
              "type": ["HVAC/VAV_SD_CSP", "HVAC/DMP"],
          },
      }

      self._merge(tmp_path, site_dir, dbo_config)

      device_dirs = sorted(p.name for p in (site_dir / "devices").iterdir())
      self.assertEqual(
          device_dirs, ["ASHP-1", "ASHP-2", "CHWS-1", "DEV-1", "VAV-1"]
      )
      for dev_id in device_dirs:
        self.assertRegex(dev_id, BDNS_RE)

      # Deterministic sort by code assigns HP 1-1A -> ASHP-1, HP 2-1B -> ASHP-2
      ashp1 = json.loads(
          (site_dir / "devices" / "ASHP-1" / "metadata.json").read_text(
              encoding="utf-8"
          )
      )
      self.assertEqual(ashp1["system"]["guid"], "guid-ashp-1")
      self.assertEqual(
          ashp1["localnet"]["families"]["bacnet"]["name"],
          "ZZ-TRI-FECTA:ASHP:HP 1-1A",
      )

      # FACILITIES entities (string or list type) stay under externals
      self.assertTrue(
          (
              site_dir / "externals" / "facilities" / "ZZ-TRI-FECTA.json"
          ).is_file()
      )
      self.assertTrue(
          (
              site_dir / "externals" / "facilities" / "ZZ-TRI-FECTA-1.json"
          ).is_file()
      )
      self.assertNotIn("ZZ-TRI-FECTA", device_dirs)
      self.assertNotIn("ZZ-TRI-FECTA-1", device_dirs)

  def test_avoids_collision_with_existing_untracked_physical_devices(self):
    with tempfile.TemporaryDirectory() as tmp:
      tmp_path = Path(tmp)
      site_dir = tmp_path / "site_model"
      self._write_device(
          site_dir,
          "VAV-1",
          {"timestamp": "2026-01-01T00:00:00Z", "version": "1.5.3"},
      )
      dbo_config = {
          "guid-virt-vav": {
              "code": "ZZ-TRI-FECTA:VAV:VAVCO_1-1-51",
              "type": "HVAC/VAV_SD",
          }
      }

      self._merge(tmp_path, site_dir, dbo_config)

      device_dirs = sorted(p.name for p in (site_dir / "devices").iterdir())
      self.assertEqual(device_dirs, ["VAV-1", "VAV-2"])
      vav1 = json.loads(
          (site_dir / "devices" / "VAV-1" / "metadata.json").read_text(
              encoding="utf-8"
          )
      )
      self.assertNotIn("system", vav1)

  def test_idempotent_remerge_preserves_guid_and_legacy_ext_id(self):
    with tempfile.TemporaryDirectory() as tmp:
      tmp_path = Path(tmp)
      site_dir = tmp_path / "site_model"
      self._write_device(
          site_dir,
          "PMP-9",
          {
              "timestamp": "2026-01-01T00:00:00Z",
              "version": "1.5.3",
              "externals": {"dbo": {"ext_id": "guid-legacy"}},
          },
      )
      first_config = {
          "guid-b": {
              "code": "ZZ-TRI-FECTA:ASHP:HP 2-1B",
              "type": "HVAC/ASHP_SS",
          },
          "guid-legacy": {
              "code": "ZZ-TRI-FECTA:PMP:PCHWP_1",
              "type": "HVAC/PMP_SS",
          },
      }
      self._merge(tmp_path, site_dir, first_config)
      self.assertTrue((site_dir / "devices" / "ASHP-1").is_dir())
      self.assertTrue((site_dir / "devices" / "PMP-9").is_dir())
      self.assertFalse((site_dir / "devices" / "PMP-1").exists())

      # Adding an entity that sorts earlier must not rename guid-b from ASHP-1
      second_config = {
          "guid-a": {
              "code": "ZZ-TRI-FECTA:ASHP:HP 1-1A",
              "type": "HVAC/ASHP_SS",
          },
          "guid-b": {
              "code": "ZZ-TRI-FECTA:ASHP:HP 2-1B",
              "type": "HVAC/ASHP_SS",
          },
      }
      self._merge(tmp_path, site_dir, second_config)

      ashp1 = json.loads(
          (site_dir / "devices" / "ASHP-1" / "metadata.json").read_text(
              encoding="utf-8"
          )
      )
      ashp2 = json.loads(
          (site_dir / "devices" / "ASHP-2" / "metadata.json").read_text(
              encoding="utf-8"
          )
      )
      self.assertEqual(ashp1["system"]["guid"], "guid-b")
      self.assertEqual(ashp2["system"]["guid"], "guid-a")

  def test_edge_cases_null_system_corrupt_json_and_newline_code(self):
    with tempfile.TemporaryDirectory() as tmp:
      tmp_path = Path(tmp)
      site_dir = tmp_path / "site_model"
      self._write_device(
          site_dir,
          "VAV-1",
          {
              "timestamp": "2026-01-01T00:00:00Z",
              "version": "1.5.3",
              "system": {"guid": "guid-existing"},
          },
      )
      self._write_device(
          site_dir,
          "AHU-1",
          {"timestamp": "2026-01-01T00:00:00Z", "system": None},
      )
      corrupt_dir = site_dir / "devices" / "FCU-1"
      corrupt_dir.mkdir(parents=True, exist_ok=True)
      (corrupt_dir / "metadata.json").write_text("{not-json", encoding="utf-8")

      dbo_config = {
          "guid-other-vav": {"code": "VAV-1", "type": "HVAC/VAV_SD"},
          "guid-newline": {"code": "VAV-9\n", "type": "HVAC/VAV_SD"},
          "guid-fcu": {"code": "FCU-1", "type": "HVAC/FCU_SD"},
      }

      self._merge(tmp_path, site_dir, dbo_config)

      device_dirs = sorted(p.name for p in (site_dir / "devices").iterdir())
      self.assertEqual(
          device_dirs, ["AHU-1", "FCU-1", "FCU-2", "VAV-1", "VAV-2", "VAV-3"]
      )
      for dev_id in device_dirs:
        self.assertRegex(dev_id, BDNS_RE)


if __name__ == "__main__":
  unittest.main()
