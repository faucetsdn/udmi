"""Generated class for event_validation_device.json"""


class DeviceValidationEvent:
  """Generated schema class"""

  def __init__(self):
    self.last_seen = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DeviceValidationEvent()
    result.last_seen = source.get('last_seen')
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
    return result
