"""Generated class for state_discovery.json"""
from .state_discovery_family import FamilyDiscoveryState


class DiscoveryState:
  """Generated schema class"""

  def __init__(self):
    self.generation = None
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryState()
    result.generation = source.get('generation')
    result.families = FamilyDiscoveryState.map_from(source.get('families'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveryState.from_dict(source[key])
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
    if self.families:
      result['families'] = FamilyDiscoveryState.expand_dict(self.families) # 2
    return result
