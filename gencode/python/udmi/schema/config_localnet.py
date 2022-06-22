"""Generated class for config_localnet.json"""


class ObjectC514033E:
  """Generated schema class"""

  def __init__(self):
    self.id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectC514033E()
    result.id = source.get('id')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectC514033E.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.id:
      result['id'] = self.id # 5
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
    result.families = ObjectC514033E.map_from(source.get('families'))
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
      result['families'] = ObjectC514033E.expand_dict(self.families) # 2
    return result
