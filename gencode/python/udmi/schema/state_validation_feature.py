"""Generated class for state_validation_feature.json"""
from .state_validation_sequence import SequenceValidationState


class FeatureValidationState:
  """Generated schema class"""

  def __init__(self):
    self.sequences = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FeatureValidationState()
    result.sequences = SequenceValidationState.map_from(source.get('sequences'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FeatureValidationState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.sequences:
      result['sequences'] = SequenceValidationState.expand_dict(self.sequences) # 2
    return result
