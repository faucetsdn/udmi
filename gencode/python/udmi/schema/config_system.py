"""Generated class for config_system.json"""


class ObjectF030B035:
  """Generated schema class"""

  def __init__(self):
    self.private = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectF030B035()
    result.private = source.get('private')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectF030B035.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.private:
      result['private'] = self.private # 5
    return result


class SystemConfig:
  """Generated schema class"""

  def __init__(self):
    self.min_loglevel = None
    self.auth_key = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemConfig()
    result.min_loglevel = source.get('min_loglevel')
    result.auth_key = ObjectF030B035.from_dict(source.get('auth_key'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.min_loglevel:
      result['min_loglevel'] = self.min_loglevel # 5
    if self.auth_key:
      result['auth_key'] = self.auth_key.to_dict() # 4
    return result
