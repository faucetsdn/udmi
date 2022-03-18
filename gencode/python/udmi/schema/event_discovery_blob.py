"""Generated class for event_discovery_blob.json"""


class BlobEnumerationEvent:
  """Generated schema class"""

  def __init__(self):
    self.description = None
    self.firmware_set = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BlobEnumerationEvent()
    result.description = source.get('description')
    result.firmware_set = source.get('firmware_set')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BlobEnumerationEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.description:
      result['description'] = self.description # 5
    if self.firmware_set:
      result['firmware_set'] = self.firmware_set # 5
    return result
