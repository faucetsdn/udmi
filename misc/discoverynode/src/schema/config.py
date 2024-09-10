import datetime
import enum
import schema.util
import dataclasses
import json

@dataclasses.dataclass
class DiscoveryFamily:
  generation: str
  scan_interval_sec: int | None = None
  scan_duration_sec: int | None = None
  depth: str | None = None
  