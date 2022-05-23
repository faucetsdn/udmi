"""Generated class for reflect_state.json"""


class ReflectorState:
  """Generated schema class"""

  def __init__(self):
    self.version = None
    self.user = None
    self.timestamp = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ReflectorState()
    result.version = source.get('version')
    result.user = source.get('user')
    result.timestamp = source.get('timestamp')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ReflectorState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.version:
      result['version'] = self.version # 5
    if self.user:
      result['user'] = self.user # 5
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    return result
