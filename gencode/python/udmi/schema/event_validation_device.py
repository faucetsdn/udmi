"""Generated class for event_validation_device.json"""
from .common import Entry


class DeviceValidationEvent:
  """Generated schema class"""

  def __init__(self):
    self.last_seen = None
    self.oldest_mark = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DeviceValidationEvent()
    result.last_seen = source.get('last_seen')
    result.oldest_mark = source.get('oldest_mark')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DeviceValidationEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.last_seen:
      result['last_seen'] = self.last_seen # 5
    if self.oldest_mark:
      result['oldest_mark'] = self.oldest_mark # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
