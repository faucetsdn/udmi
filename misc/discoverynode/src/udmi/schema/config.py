import dataclasses
import datetime
import enum
import json
import udmi.schema.util


@dataclasses.dataclass(frozen=True, eq=True)
class DiscoveryFamily:
  generation: str
  scan_interval_sec: int | None = None
  scan_duration_sec: int | None = None
  depth: str | None = None
