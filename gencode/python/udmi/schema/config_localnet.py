"""Generated class for config_localnet.json"""


class ObjectAAF5FDC4:
  """Generated schema class"""

  def __init__(self):
    self.addr = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectAAF5FDC4()
    result.addr = source.get('addr')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectAAF5FDC4.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.addr:
      result['addr'] = self.addr # 5
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
    result.networks = ObjectAAF5FDC4.map_from(source.get('networks'))
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
      result['networks'] = ObjectAAF5FDC4.expand_dict(self.networks) # 2
    return result
