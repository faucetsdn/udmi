import collections
import copy
import dataclasses
import datetime
import enum
import json
import udmi.schema.util
from typing import Any, List


@dataclasses.dataclass
class Status:
  category: str
  level: int
  message: str
  timestamp: datetime.datetime = dataclasses.field(
      default_factory=datetime.datetime.now
  )


@dataclasses.dataclass
class DiscoverySystemSoftware:
  firmware: str = None


@dataclasses.dataclass
class DiscoverySystemHardware:
  make: str | None = None
  model: str | None = None


@dataclasses.dataclass
class DiscoverySystem:
  hardware: DiscoverySystemHardware = dataclasses.field(
      default_factory=DiscoverySystemHardware
  )
  software: DiscoverySystemSoftware = dataclasses.field(
      default_factory=DiscoverySystemSoftware
  )
  serial_no: str | None = None
  ancillary: dict[str, Any] = dataclasses.field(default_factory=dict) 


@dataclasses.dataclass
class DiscoveryFamily:
  addr: str


@dataclasses.dataclass
class DiscoveryPoint:
  ref: str | None = None
  name: str | None = None
  description: str | None = None
  type: str | None = None
  units:  str | None = None
  possible_values: List[Any] | None = None
  ancillary: dict[str, Any] = dataclasses.field(default_factory=dict) 


@dataclasses.dataclass
class DiscoveryEvent:
  generation: str
  family: str
  addr: str | None = None

  version: str = "1.5.1"
  timestamp: datetime.datetime = dataclasses.field(
      default_factory=udmi.schema.util.current_time_utc
  )
  families: dict[str, DiscoveryFamily] = dataclasses.field(default_factory=dict)
  system: DiscoverySystem = dataclasses.field(default_factory=DiscoverySystem)
  refs: dict[str, DiscoveryPoint] = dataclasses.field(default_factory=dict)
  event_no: int | None = None
  status: Status | None = None


  def to_json(self) -> str:
    as_dict = dataclasses.asdict(self)
    as_dict["timestamp"] = datetime.datetime.now()
    
    for _ in range(3):
      as_dict = udmi.schema.util.deep_remove(
        copy.deepcopy(as_dict), None, [{}, None]
    )
    return json.dumps(
        as_dict, default=udmi.schema.util.json_serializer, indent=2
    )
