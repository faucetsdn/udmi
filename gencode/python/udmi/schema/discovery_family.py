"""Generated class for discovery_family.json"""


class FamilyDiscovery:
  """Generated schema class"""

  def __init__(self):
    self.addr = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyDiscovery()
    result.addr = source.get('addr')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FamilyDiscovery.from_dict(source[key])
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
    return result
