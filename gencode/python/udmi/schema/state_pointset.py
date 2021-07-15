"""Generated class for state_pointset.json"""
from .state_pointset_point import PointPointsetState


class PointsetState:
  """Generated schema class"""

  def __init__(self):
    self.state_etag = None
    self.points = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointsetState()
    result.state_etag = source.get('state_etag')
    result.points = PointPointsetState.map_from(source.get('points'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointsetState.from_dict(source[key])
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
    if self.points:
      result['points'] = PointPointsetState.expand_dict(self.points) # 2
    return result
