import copy
import dataclasses
import datetime
import enum
import json
import udmi.schema.util


@dataclasses.dataclass
class Status:
  category: str
  level: int
  message: str
  timestamp: datetime.datetime = dataclasses.field(
      default_factory=datetime.datetime.now
  )


class Phase(enum.StrEnum):
  pending = 'pending'
  active = 'active'
  stopped = 'stopped'
  done = 'done'


@dataclasses.dataclass
class Discovery:
  generation: str | None = None
  phase: Phase | None = None
  status: Status | None = None


@dataclasses.dataclass
class StateDiscovery:
  families: dict[str, Discovery] = dataclasses.field(default_factory=dict)


@dataclasses.dataclass
class StateSystemSoftware:
  version: str | None = None


@dataclasses.dataclass
class StateSystem:
  last_config: str | None = None
  software: StateSystemSoftware = dataclasses.field(
      default_factory=StateSystemSoftware
  )


@dataclasses.dataclass
class LocalnetFamily:
  """State of the localnet."""

  addr: str | None = None


@dataclasses.dataclass
class StateLocalnet:
  """State of the localnet."""

  families: dict[str, LocalnetFamily] = dataclasses.field(default_factory=dict)


@dataclasses.dataclass
class StateLocalnet:
  """State of the localnet."""

  families: dict[str, LocalnetFamily] = dataclasses.field(default_factory=dict)


@dataclasses.dataclass
class State:
  """State of the device."""

  timestamp = None
  version: str = '1.5.1'
  system: StateSystem = dataclasses.field(default_factory=StateSystem)
  localnet: StateLocalnet = dataclasses.field(default_factory=StateLocalnet)
  discovery: StateDiscovery = dataclasses.field(default_factory=StateDiscovery)

  def to_json(self, purge_empty: bool = True) -> str:
    as_dict = dataclasses.asdict(self)
    as_dict['timestamp'] = datetime.datetime.now()

    # Hacky fix because UDMI doesn't like empty fields
    if purge_empty:
      for _ in range(1):
        as_dict = udmi.schema.util.deep_remove(
            copy.deepcopy(as_dict), None, [{}, None]
        )
    return json.dumps(as_dict, default=udmi.schema.util.json_serializer)

  def get_hash(self) -> int:
    return hash(str(self))
