"""Generated class for config_localnet.json"""


class ObjectE04A6D6A:
  """Generated schema class"""

  def __init__(self):
    self.local_id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectE04A6D6A()
    result.local_id = source.get('local_id')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectE04A6D6A.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.local_id:
      result['local_id'] = self.local_id # 5
    return result


class LocalnetConfig:
  """Generated schema class"""

  def __init__(self):
    self.subsystem = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LocalnetConfig()
    result.subsystem = ObjectE04A6D6A.map_from(source.get('subsystem'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = LocalnetConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.subsystem:
      result['subsystem'] = ObjectE04A6D6A.expand_dict(self.subsystem) # 2
    return result
