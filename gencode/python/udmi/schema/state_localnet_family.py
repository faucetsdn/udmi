"""Generated class for state_localnet_family.json"""
from .common import Entry


class FamilyLocalnetState:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyLocalnetState()
    result.addr = source.get('addr')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FamilyLocalnetState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.addr:
      result['addr'] = self.addr # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
