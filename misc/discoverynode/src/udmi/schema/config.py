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
    ValueError: if `generation` is missing
  """
  ignored = sorted(set(raw) - _DISCOVERY_FAMILY_FIELDS)
  if ignored:
    logging.warning("[%s] ignoring unsupported scan field(s) %s", source, ignored)
  if "generation" not in raw:
    raise ValueError(f"{source}: generation is required")
  return DiscoveryFamily(
      **{k: v for k, v in raw.items() if k in _DISCOVERY_FAMILY_FIELDS}
  )
