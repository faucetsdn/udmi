"""Generated class for state_validation_capability.json"""
from .entry import Entry


class CapabilityValidationState:
  """Generated schema class"""

  def __init__(self):
    self.summary = None
    self.stage = None
    self.result = None
    self.status = None
    self.score = None
    self.total = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CapabilityValidationState()
    result.summary = source.get('summary')
    result.stage = source.get('stage')
    result.result = source.get('result')
    result.status = Entry.from_dict(source.get('status'))
    result.score = source.get('score')
    result.total = source.get('total')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = CapabilityValidationState.from_dict(source[key])
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
    if self.score:
      result['score'] = self.score # 5
    if self.total:
      result['total'] = self.total # 5
    return result
