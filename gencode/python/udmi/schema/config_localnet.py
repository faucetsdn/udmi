"""Generated class for config_localnet.json"""


class Object5D8DC840:
  """Generated schema class"""

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object5D8DC840()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object5D8DC840.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    return result


class LocalnetConfig:
  """Generated schema class"""

  def __init__(self):
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LocalnetConfig()
    result.families = Object5D8DC840.map_from(source.get('families'))
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
    if self.families:
      result['families'] = Object5D8DC840.expand_dict(self.families) # 2
    return result
