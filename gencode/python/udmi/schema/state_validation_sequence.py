"""Generated class for state_validation_sequence.json"""
from .state_validation_capability import CapabilityValidationState
from .entry import Entry


class ObjectB831B99F:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.total = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectB831B99F()
    result.value = source.get('value')
    result.total = source.get('total')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectB831B99F.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.total:
      result['total'] = self.total # 5
    return result


class SequenceValidationState:
  """Generated schema class"""

  def __init__(self):
    self.summary = None
    self.stage = None
    self.capabilities = None
    self.result = None
    self.status = None
    self.scoring = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SequenceValidationState()
    result.summary = source.get('summary')
    result.stage = source.get('stage')
    result.capabilities = CapabilityValidationState.map_from(source.get('capabilities'))
    result.result = source.get('result')
    result.status = Entry.from_dict(source.get('status'))
    result.scoring = ObjectB831B99F.from_dict(source.get('scoring'))
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
    if self.capabilities:
      result['capabilities'] = CapabilityValidationState.expand_dict(self.capabilities) # 2
    if self.result:
      result['result'] = self.result # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.scoring:
      result['scoring'] = self.scoring.to_dict() # 4
    return result
