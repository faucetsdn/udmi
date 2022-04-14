"""Generated class for model_testing.json"""
from .model_testing_target import TargetTestingMetadata


class TestingMetadata:
  """Generated schema class"""

  def __init__(self):
    self.targets = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = TestingMetadata()
    result.targets = TargetTestingMetadata.map_from(source.get('targets'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = TestingMetadata.from_dict(source[key])
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
      result['targets'] = TargetTestingMetadata.expand_dict(self.targets) # 2
    return result
