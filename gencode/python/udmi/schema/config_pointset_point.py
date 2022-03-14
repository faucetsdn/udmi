"""Generated class for config_pointset_point.json"""


class PointPointsetConfig:
  """Generated schema class"""

  def __init__(self):
    self.ref = None
    self.units = None
    self.set_value = None
    self.min_loglevel = None
    self.sample_limit_sec = None
    self.sample_rate_sec = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetConfig()
    result.ref = source.get('ref')
    result.units = source.get('units')
    result.set_value = source.get('set_value')
    result.min_loglevel = source.get('min_loglevel')
    result.sample_limit_sec = source.get('sample_limit_sec')
    result.sample_rate_sec = source.get('sample_rate_sec')
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
    if self.min_loglevel:
      result['min_loglevel'] = self.min_loglevel # 5
    if self.sample_limit_sec:
      result['sample_limit_sec'] = self.sample_limit_sec # 5
    if self.sample_rate_sec:
      result['sample_rate_sec'] = self.sample_rate_sec # 5
    return result
