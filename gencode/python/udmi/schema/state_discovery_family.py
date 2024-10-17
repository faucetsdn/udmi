"""Generated class for state_discovery_family.json"""
from .entry import Entry


class FamilyDiscoveryState:
  """Generated schema class"""

  def __init__(self):
    self.generation = None
    self.phase = None
    self.active_count = None
    self.passive_count = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyDiscoveryState()
    result.generation = source.get('generation')
    result.phase = source.get('phase')
    result.active_count = source.get('active_count')
    result.passive_count = source.get('passive_count')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FamilyDiscoveryState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.generation:
      result['generation'] = self.generation # 5
    if self.phase:
      result['phase'] = self.phase # 5
    if self.active_count:
      result['active_count'] = self.active_count # 5
    if self.passive_count:
      result['passive_count'] = self.passive_count # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
