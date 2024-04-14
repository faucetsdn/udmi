"""Generated class for events.json"""
from .events_system import SystemEvents
from .events_pointset import PointsetEvents
from .events_discovery import DiscoveryEvents


class Events:
  """Generated schema class"""

  def __init__(self):
    self.system = None
    self.pointset = None
    self.discovery = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Events()
    result.system = SystemEvents.from_dict(source.get('system'))
    result.pointset = PointsetEvents.from_dict(source.get('pointset'))
    result.discovery = DiscoveryEvents.from_dict(source.get('discovery'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Events.from_dict(source[key])
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
