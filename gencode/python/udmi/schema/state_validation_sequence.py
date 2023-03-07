"""Generated class for state_validation_sequence.json"""
from .common import Entry


class SequenceValidationState:
  """Generated schema class"""

  def __init__(self):
    self.summary = None
    self.stage = None
    self.result = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SequenceValidationState()
    result.summary = source.get('summary')
    result.stage = source.get('stage')
    result.result = source.get('result')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SequenceValidationState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.summary:
      result['summary'] = self.summary # 5
    if self.stage:
      result['stage'] = self.stage # 5
    if self.result:
      result['result'] = self.result # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
