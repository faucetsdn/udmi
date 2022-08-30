"""Generated class for state_mapping.json"""
from .state_mapping_device import DeviceMappingState


class MappingState:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.devices = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MappingState()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.devices = DeviceMappingState.map_from(source.get('devices'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MappingState.from_dict(source[key])
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
    if self.devices:
      result['devices'] = DeviceMappingState.expand_dict(self.devices) # 2
    return result
