"""Generated class for state_mapping_device.json"""
from .common import Entry


class DeviceMappingState:
  """Generated schema class"""

  def __init__(self):
    self.guid = None
    self.imported = None
    self.discovered = None
    self.predicted = None
    self.promoted = None
    self.exported = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DeviceMappingState()
    result.guid = source.get('guid')
    result.imported = source.get('imported')
    result.discovered = source.get('discovered')
    result.predicted = source.get('predicted')
    result.promoted = source.get('promoted')
    result.exported = source.get('exported')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DeviceMappingState.from_dict(source[key])
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
    if self.imported:
      result['imported'] = self.imported # 5
    if self.discovered:
      result['discovered'] = self.discovered # 5
    if self.predicted:
      result['predicted'] = self.predicted # 5
    if self.promoted:
      result['promoted'] = self.promoted # 5
    if self.exported:
      result['exported'] = self.exported # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
