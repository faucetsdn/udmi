"""Generated class for model_points_template.json"""
from .model_pointset_point import PointPointsetModel


class PointsTemplateModel:
  """Generated schema class"""

  def __init__(self):
    self.points = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointsTemplateModel()
    result.points = PointPointsetModel.map_from(source.get('points'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointsTemplateModel.from_dict(source[key])
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
    return result
