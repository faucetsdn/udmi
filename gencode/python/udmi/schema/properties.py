"""Generated class for properties.json"""


class Properties:
  """Generated schema class"""

  def __init__(self):
    self.key_type = None
    self.version = None
    self.connect = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Properties()
    result.key_type = source.get('key_type')
    result.version = source.get('version')
    result.connect = source.get('connect')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Properties.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.key_type:
      result['key_type'] = self.key_type # 5
    if self.version:
      result['version'] = self.version # 5
    if self.connect:
      result['connect'] = self.connect # 5
    return result
