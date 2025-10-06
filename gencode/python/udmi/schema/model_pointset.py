"""Generated class for model_pointset.json"""
from .model_pointset_point import PointPointsetModel
from .model_points_template import PointsetModel


class PointsetModel:
  """Generated schema class"""

  def __init__(self):
    self.points = None
    self.points_template = None
    self.exclude_units_from_config = None
    self.exclude_points_from_config = None
    self.sample_limit_sec = None
    self.sample_rate_sec = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointsetModel()
    result.points = PointPointsetModel.map_from(source.get('points'))
    result.points_template = PointsetModel.from_dict(source.get('points_template'))
    result.exclude_units_from_config = source.get('exclude_units_from_config')
    result.exclude_points_from_config = source.get('exclude_points_from_config')
    result.sample_limit_sec = source.get('sample_limit_sec')
    result.sample_rate_sec = source.get('sample_rate_sec')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointsetModel.from_dict(source[key])
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
      result['points'] = PointPointsetModel.expand_dict(self.points) # 2
    if self.points_template:
      result['points_template'] = self.points_template.to_dict() # 4
    if self.exclude_units_from_config:
      result['exclude_units_from_config'] = self.exclude_units_from_config # 5
    if self.exclude_points_from_config:
      result['exclude_points_from_config'] = self.exclude_points_from_config # 5
    if self.sample_limit_sec:
      result['sample_limit_sec'] = self.sample_limit_sec # 5
    if self.sample_rate_sec:
      result['sample_rate_sec'] = self.sample_rate_sec # 5
    return result
