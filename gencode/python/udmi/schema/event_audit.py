"""Generated class for event_audit.json"""


class Audit:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Audit()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Audit.from_dict(source[key])
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
    return result
