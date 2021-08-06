"""Generated class for event_discovery.json"""
from .event_discovery_family import FaimilyDiscoveryEvent


class Discovery:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Discovery()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.families = FaimilyDiscoveryEvent.map_from(source.get('families'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Discovery.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    if self.version:
      result['version'] = self.version # 5
    if self.families:
      result['families'] = FaimilyDiscoveryEvent.expand_dict(self.families) # 2
    return result
