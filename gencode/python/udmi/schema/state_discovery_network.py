"""Generated class for state_discovery_network.json"""
from .common import Entry


class NetworkDiscoveryState:
  """Generated schema class"""

  def __init__(self):
    self.generation = None
    self.active = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = NetworkDiscoveryState()
    result.generation = source.get('generation')
    result.active = source.get('active')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = NetworkDiscoveryState.from_dict(source[key])
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
    if self.active:
      result['active'] = self.active # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
