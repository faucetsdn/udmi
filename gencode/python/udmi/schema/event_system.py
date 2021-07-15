"""Generated class for event_system.json"""


class SystemEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.logentries = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.logentries = Entry.array_from(source.get('logentries'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemEvent.from_dict(source[key])
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
    if self.logentries:
      result['logentries'] = self.logentries.to_dict() # 3
    return result
