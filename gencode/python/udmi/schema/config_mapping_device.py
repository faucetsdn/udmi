"""Generated class for config_mapping_device.json"""
from .common import Entry


class DeviceMappingConfig:
  """Generated schema class"""

  def __init__(self):
    self.guid = None
    self.applied = None
    self.requested = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DeviceMappingConfig()
    result.guid = source.get('guid')
    result.applied = source.get('applied')
    result.requested = source.get('requested')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DeviceMappingConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.guid:
      result['guid'] = self.guid # 5
    if self.applied:
      result['applied'] = self.applied # 5
    if self.requested:
      result['requested'] = self.requested # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
