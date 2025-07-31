"""Generated class for config_mapping.json"""
from .config_mapping_device import DeviceMappingConfig


class MappingConfig:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.devices = None
    self.extras_deletion_days = None
    self.devices_deletion_days = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MappingConfig()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.devices = DeviceMappingConfig.map_from(source.get('devices'))
    result.extras_deletion_days = source.get('extras_deletion_days')
    result.devices_deletion_days = source.get('devices_deletion_days')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MappingConfig.from_dict(source[key])
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
      result['devices'] = DeviceMappingConfig.expand_dict(self.devices) # 2
    if self.extras_deletion_days:
      result['extras_deletion_days'] = self.extras_deletion_days # 5
    if self.devices_deletion_days:
      result['devices_deletion_days'] = self.devices_deletion_days # 5
    return result
