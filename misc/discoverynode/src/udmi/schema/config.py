import dataclasses
import datetime
import enum
import json
import logging
import re
import udmi.schema.util


# Naming rule for `discovery.scans.<name>` keys, matching the UDMI schema's
# pattern for discovery family keys.
SCAN_NAME_PATTERN = re.compile(r"^[a-z][a-z0-9]*(_[a-z0-9]+)*$")


@dataclasses.dataclass(frozen=True, eq=True)
class DiscoveryFamily:
  generation: str
  scan_interval_sec: int | None = None
  scan_duration_sec: int | None = None
  depth: str | None = None
  addrs: list[str] | None = None


_DISCOVERY_FAMILY_FIELDS = frozenset(
    f.name for f in dataclasses.fields(DiscoveryFamily)
)


def discovery_family_from_dict(source: str, raw: dict) -> DiscoveryFamily:
  """Builds a DiscoveryFamily from a raw config block.

  Keys which are not part of the DiscoveryFamily model (e.g. `mode`,
  `passive_sec`, `networks`) are dropped with a warning.

  Args:
    source: dotted path used in log messages, e.g. "scans.nightly_passive_sweep"
    raw: the raw JSON block (already stripped of `family` for named scans)

  Raises:
    ValueError: if `generation` is missing or any scan field is invalid
  """
  ignored = sorted(set(raw) - _DISCOVERY_FAMILY_FIELDS)
  if ignored:
    logging.warning("[%s] ignoring unsupported scan field(s) %s", source, ignored)
  if "generation" not in raw or not isinstance(raw["generation"], str):
    raise ValueError(f"{source}: generation is required and must be a string")
  try:
    udmi.schema.util.datetime_from_iso_timestamp(raw["generation"])
  except ValueError as err:
    raise ValueError(f"{source}: invalid generation {raw['generation']!r}: {err}") from err

  for field_name in ("scan_interval_sec", "scan_duration_sec"):
    val = raw.get(field_name)
    if val is not None:
      if isinstance(val, bool) or not isinstance(val, int) or val < 0:
        raise ValueError(f"{source}: {field_name} must be a non-negative integer")

  duration = raw.get("scan_duration_sec")
  interval = raw.get("scan_interval_sec")
  if duration is not None and interval is not None and duration > interval:
    raise ValueError(
        f"{source}: scan_duration_sec ({duration}) cannot exceed scan_interval_sec ({interval})"
    )

  addrs = raw.get("addrs")
  if addrs is not None:
    if not isinstance(addrs, list) or not all(isinstance(a, str) for a in addrs):
      raise ValueError(f"{source}: addrs must be a list of strings")

  return DiscoveryFamily(
      **{k: v for k, v in raw.items() if k in _DISCOVERY_FAMILY_FIELDS}
  )
