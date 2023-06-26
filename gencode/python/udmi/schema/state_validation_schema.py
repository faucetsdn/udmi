"""Generated class for state_validation_schema.json"""
from .state_validation_sequence import SequenceValidationState


class SchemaValidationState:
  """Generated schema class"""

  def __init__(self):
    self.stages = None
    self.sequences = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SchemaValidationState()
    result.stages = source.get('stages')
    result.sequences = SequenceValidationState.map_from(source.get('sequences'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SchemaValidationState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.stages:
      result['stages'] = self.stages # 1
    if self.sequences:
      result['sequences'] = SequenceValidationState.expand_dict(self.sequences) # 2
    return result
