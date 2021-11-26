"""Generated class for event.json"""
from .event_system import SystemEvent
from .event_pointset import PointsetEvent
from .event_discovery import Discovery


class Event:
  """Generated schema class"""

  def __init__(self):
    self.system = None
    self.pointset = None
    self.discovery = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Event()
    result.system = SystemEvent.from_dict(source.get('system'))
    result.pointset = PointsetEvent.from_dict(source.get('pointset'))
    result.discovery = Discovery.from_dict(source.get('discovery'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Event.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.system:
      result['system'] = self.system.to_dict() # 4
    if self.pointset:
      result['pointset'] = self.pointset.to_dict() # 4
    if self.discovery:
      result['discovery'] = self.discovery.to_dict() # 4
    return result
