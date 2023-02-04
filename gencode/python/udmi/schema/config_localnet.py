"""Generated class for config_localnet.json"""


class Object5F740D48:
  """Generated schema class"""

  def __init__(self):
    self.id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object5F740D48()
    result.id = source.get('id')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object5F740D48.from_dict(source[key])
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
    self.networks = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LocalnetConfig()
    result.networks = Object5F740D48.map_from(source.get('networks'))
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
    if self.networks:
      result['networks'] = Object5F740D48.expand_dict(self.networks) # 2
    return result
