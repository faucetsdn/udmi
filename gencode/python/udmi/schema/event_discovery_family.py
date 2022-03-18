"""Generated class for event_discovery_family.json"""


class FamilyDiscoveryEvent:
  """Generated schema class"""

  def __init__(self):
    self.id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyDiscoveryEvent()
    result.id = source.get('id')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FamilyDiscoveryEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.id:
      result['id'] = self.id # 5
    return result
