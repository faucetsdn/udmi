"""Generated class for config_pointset_point.json"""


class PointPointsetConfig:
  """Generated schema class"""

  def __init__(self):
    self.ref = None
    self.units = None
    self.set_value = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetConfig()
    result.ref = source.get('ref')
    result.units = source.get('units')
    result.set_value = source.get('set_value')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointPointsetConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.ref:
      result['ref'] = self.ref # 5
    if self.units:
      result['units'] = self.units # 5
    if self.set_value:
      result['set_value'] = self.set_value # 5
    return result
