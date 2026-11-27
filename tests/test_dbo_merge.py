"""Unit tests for bin/dbo_merge point typing mutual-exclusivity handling."""

import importlib.machinery
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent


def _load_dbo_merge():
  script_path = REPO_ROOT / "bin" / "dbo_merge"
  loader = importlib.machinery.SourceFileLoader("dbo_merge", str(script_path))
  spec = importlib.util.spec_from_loader(loader.name, loader)
  module = importlib.util.module_from_spec(spec)
  loader.exec_module(module)
  return module


DBO_MERGE = _load_dbo_merge()


class DboMergePointTypingTest(unittest.TestCase):
  """Verify dbo_merge clears conflicting point-type specifiers."""

  def _run_merge(
      self,
      initial_points: dict,
      dbo_translation: dict,
      dbo_links: dict = None,
  ) -> dict:
    with tempfile.TemporaryDirectory() as tmp:
      tmp_path = Path(tmp)
      site_dir = tmp_path / "site_model"
      dev_dir = site_dir / "devices" / "DEV-1"
      dev_dir.mkdir(parents=True)

      metadata = {
          "version": "1.5.7",
          "timestamp": "2026-01-01T00:00:00Z",
          "system": {"guid": "guid-1"},
          "pointset": {"points": initial_points},
      }
      metadata_path = dev_dir / "metadata.json"
      metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

      entity = {
          "code": "DEV-1",
          "type": "HVAC/AHU",
          "translation": dbo_translation,
      }
      if dbo_links:
        entity["links"] = dbo_links
      dbo_config = {
          "guid-1": entity,
          "guid-remote": {"code": "DEV-2", "type": "HVAC/VAV"},
      }
      yaml_path = tmp_path / "building_config.yaml"
      yaml_path.write_text(yaml.safe_dump(dbo_config), encoding="utf-8")

      DBO_MERGE.merge_dbo_config(yaml_path, site_dir)
      merged = json.loads(metadata_path.read_text(encoding="utf-8"))
      return merged["pointset"]["points"]

  def test_states_clears_units_type_numeric_fields_and_invalid_baseline(self):
    initial_points = {
        "run_status": {
            "units": "no_units",
            "units_reported": "No-units",
            "type": "boolean",
            "range_min": 0,
            "range_max": 1,
            "baseline_value": 0,
            "baseline_tolerance": 0.1,
            "cov_increment": 1,
            "ref": "BI1",
        },
        "mode_status": {
            "units": "no_units",
            "baseline_value": "1.0",
        },
    }
    dbo_translation = {
        "run_status": {
            "present_value": "points.run_status.present_value",
            "states": {"OFF": "0.0", "ON": "1.0"},
        },
        "mode_status": {
            "present_value": "points.mode_status.present_value",
            "states": {"OFF": "0.0", "ON": "1.0"},
        },
    }

    points = self._run_merge(initial_points, dbo_translation)
    pt = points["run_status"]
    self.assertEqual(pt["values"], {"0.0": "OFF", "1.0": "ON"})
    self.assertEqual(pt["ref"], "BI1")
    for forbidden in (
        "units",
        "type",
        "units_reported",
        "range_min",
        "range_max",
        "baseline_value",
        "baseline_tolerance",
        "cov_increment",
    ):
      self.assertNotIn(forbidden, pt)

    # Valid string key baseline_value is preserved
    self.assertEqual(points["mode_status"]["baseline_value"], "1.0")
    self.assertNotIn("units", points["mode_status"])

  def test_units_clears_values_type_and_non_numeric_baseline(self):
    initial_points = {
        "zone_temp": {
            "values": {"0.0": "OFF", "1.0": "ON"},
            "type": "string",
            "units_reported": "degF",
            "baseline_value": "0.0",
        },
        "supply_temp": {
            "values": {"0.0": "OFF", "1.0": "ON"},
            "units_reported": "old_unit",
            "baseline_value": True,
        },
        "return_temp": {
            "values": {"0.0": "OFF", "1.0": "ON"},
            "baseline_value": 21.5,
        },
    }
    dbo_translation = {
        "zone_temp": {
            "present_value": "points.zone_temp.present_value",
            "units": {
                "key": "pointset.points.zone_temp.units",
                "values": {"degrees_celsius": "degrees_celsius"},
            },
        },
        "supply_temp": {
            "present_value": "points.supply_temp.present_value",
            "units": {
                "key": "pointset.points.supply_temp.units",
                "values": {"degrees_celsius": "degC"},
            },
        },
        "return_temp": {
            "present_value": "points.return_temp.present_value",
            "units": {
                "key": "pointset.points.return_temp.units",
                "values": {"degrees_celsius": "degrees_celsius"},
            },
        },
    }

    points = self._run_merge(initial_points, dbo_translation)

    zone = points["zone_temp"]
    self.assertEqual(zone["units"], "degrees_celsius")
    for forbidden in ("values", "type", "units_reported", "baseline_value"):
      self.assertNotIn(forbidden, zone)

    supply = points["supply_temp"]
    self.assertEqual(supply["units"], "degrees_celsius")
    self.assertEqual(supply["units_reported"], "degC")
    self.assertNotIn("values", supply)
    # Boolean baseline_value must be cleared on numeric transition
    self.assertNotIn("baseline_value", supply)

    # Numeric baseline_value is preserved
    self.assertEqual(points["return_temp"]["baseline_value"], 21.5)

  def test_missing_clears_disallowed_fields(self):
    initial_points = {
        "filter_alarm": {
            "units": "no_units",
            "units_reported": "No-units",
            "values": {"0.0": "OK", "1.0": "ALARM"},
            "ref": "BI5",
            "expr": "OTHER:filter_alarm",
            "writable": True,
            "baseline_value": 0,
            "range_min": 0,
            "range_max": 1,
            "baseline_tolerance": 0.1,
            "cov_increment": 1,
            "translation": {"foo": "bar"},
            "description": "Keep description",
        }
    }
    dbo_translation = {"filter_alarm": "MISSING"}

    points = self._run_merge(initial_points, dbo_translation)
    pt = points["filter_alarm"]
    self.assertEqual(pt["type"], "missing")
    self.assertEqual(pt["description"], "Keep description")
    for forbidden in (
        "units",
        "values",
        "ref",
        "expr",
        "writable",
        "baseline_value",
        "translation",
        "units_reported",
        "range_min",
        "range_max",
        "baseline_tolerance",
        "cov_increment",
    ):
      self.assertNotIn(forbidden, pt)

  def test_links_and_ref_clear_prior_missing_type(self):
    initial_points = {
        "linked_temp": {"type": "missing"},
        "restored_ref": {"type": "missing"},
        "restored_translation": {"type": "missing"},
    }
    dbo_translation = {
        "restored_ref": "AI2",
        "restored_translation": {
            "present_value": "points.restored_translation.present_value",
            "scale": 0.1,
        },
    }
    dbo_links = {"guid-remote": {"linked_temp": "supply_air_temp"}}

    points = self._run_merge(initial_points, dbo_translation, dbo_links)
    self.assertEqual(points["linked_temp"]["expr"], "DEV-2:supply_air_temp")
    self.assertNotIn("type", points["linked_temp"])
    self.assertEqual(points["restored_ref"]["ref"], "AI2")
    self.assertNotIn("type", points["restored_ref"])
    self.assertEqual(
        points["restored_translation"]["translation"], {"scale": 0.1}
    )
    self.assertNotIn("type", points["restored_translation"])

  def test_single_entry_states_and_blank_or_empty_units_are_ignored(self):
    initial_points = {
        "single_state": {"units": "no_units"},
        "empty_units": {"values": {"0.0": "OFF", "1.0": "ON"}},
        "blank_units": {"values": {"0.0": "OFF", "1.0": "ON"}},
    }
    dbo_translation = {
        "single_state": {
            "present_value": "points.single_state.present_value",
            "states": {"ON": "1.0"},
        },
        "empty_units": {
            "present_value": "points.empty_units.present_value",
            "units": {"key": "pointset.points.empty_units.units", "values": {}},
        },
        "blank_units": {
            "present_value": "points.blank_units.present_value",
            "units": {
                "key": "pointset.points.blank_units.units",
                "values": {"   ": "   "},
            },
        },
    }

    points = self._run_merge(initial_points, dbo_translation)
    self.assertEqual(points["single_state"]["units"], "no_units")
    self.assertNotIn("values", points["single_state"])
    self.assertEqual(
        points["empty_units"]["values"], {"0.0": "OFF", "1.0": "ON"}
    )
    self.assertNotIn("units", points["empty_units"])
    self.assertEqual(
        points["blank_units"]["values"], {"0.0": "OFF", "1.0": "ON"}
    )
    self.assertNotIn("units", points["blank_units"])

  def test_units_and_states_in_same_dbo_entry_prefers_states(self):
    initial_points = {
        "alarm_status": {
            "units": "no_units",
            "baseline_value": "1.0",
        }
    }
    dbo_translation = {
        "alarm_status": {
            "present_value": "points.alarm_status.present_value",
            "units": {
                "key": "pointset.points.alarm_status.units",
                "values": {"no_units": "No-units"},
            },
            "states": {"NORMAL": "0.0", "ALARM": "1.0"},
        }
    }

    points = self._run_merge(initial_points, dbo_translation)
    pt = points["alarm_status"]
    self.assertEqual(pt["values"], {"0.0": "NORMAL", "1.0": "ALARM"})
    self.assertEqual(pt["baseline_value"], "1.0")
    self.assertNotIn("units", pt)
    self.assertNotIn("units_reported", pt)

  def test_null_raw_unit_does_not_set_units_reported(self):
    dbo_translation = {
        "zone_temp": {
            "present_value": "points.zone_temp.present_value",
            "units": {
                "key": "pointset.points.zone_temp.units",
                "values": {"degrees_celsius": None},
            },
        }
    }

    points = self._run_merge(
        {"zone_temp": {"units_reported": "degF"}}, dbo_translation
    )
    self.assertEqual(points["zone_temp"]["units"], "degrees_celsius")
    self.assertNotIn("units_reported", points["zone_temp"])


if __name__ == "__main__":
  unittest.main()
