"""Generated class for state_localnet_family.json"""


class FamilyLocalnetState:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.scope = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyLocalnetState()
    result.addr = source.get('addr')
    result.scope = source.get('scope')
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
    if self.scope:
      result['scope'] = self.scope # 5
    return result
