"""Generated class for config_pointset.json"""
from .config_pointset_point import PointPointsetConfig


class PointsetConfig:
  """Generated schema class"""

  def __init__(self):
    self.state_etag = None
    self.set_value_expiry = None
    self.sample_limit_sec = None
    self.sample_rate_sec = None
    self.points = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointsetConfig()
    result.state_etag = source.get('state_etag')
    result.set_value_expiry = source.get('set_value_expiry')
    result.sample_limit_sec = source.get('sample_limit_sec')
    result.sample_rate_sec = source.get('sample_rate_sec')
    result.points = PointPointsetConfig.map_from(source.get('points'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointsetConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.state_etag:
      result['state_etag'] = self.state_etag # 5
    if self.set_value_expiry:
      result['set_value_expiry'] = self.set_value_expiry # 5
    if self.sample_limit_sec:
      result['sample_limit_sec'] = self.sample_limit_sec # 5
    if self.sample_rate_sec:
      result['sample_rate_sec'] = self.sample_rate_sec # 5
    if self.points:
      result['points'] = PointPointsetConfig.expand_dict(self.points) # 2
    return result
