"""Generated class for model_testing.json"""
from .model_testing_target import TargetTestingModel


class TestingModel:
  """Generated schema class"""

  def __init__(self):
    self.targets = None
    self.discovery = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = TestingModel()
    result.targets = TargetTestingModel.map_from(source.get('targets'))
    result.discovery = source.get('discovery')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = TestingModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.targets:
      result['targets'] = TargetTestingModel.expand_dict(self.targets) # 2
    if self.discovery:
      result['discovery'] = self.discovery # 5
    return result
