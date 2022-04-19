"""Generated class for metadata_pointset.json"""
from .metadata_pointset_point import PointPointsetMetadata


class PointsetMetadata:
  """Generated schema class"""

  def __init__(self):
    self.points = None
    self.sample_limit_sec = None
    self.sample_rate_sec = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointsetMetadata()
    result.points = PointPointsetMetadata.map_from(source.get('points'))
    result.sample_limit_sec = source.get('sample_limit_sec')
    result.sample_rate_sec = source.get('sample_rate_sec')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointsetMetadata.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.points:
      result['points'] = PointPointsetMetadata.expand_dict(self.points) # 2
    if self.sample_limit_sec:
      result['sample_limit_sec'] = self.sample_limit_sec # 5
    if self.sample_rate_sec:
      result['sample_rate_sec'] = self.sample_rate_sec # 5
    return result
