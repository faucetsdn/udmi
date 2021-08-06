"""Generated class for event_discovery_family.json"""


class FaimilyDiscoveryEvent:
  """Generated schema class"""

  def __init__(self):
    self.id = None
    self.group = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FaimilyDiscoveryEvent()
    result.id = source.get('id')
    result.group = source.get('group')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FaimilyDiscoveryEvent.from_dict(source[key])
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
    if self.group:
      result['group'] = self.group # 5
    return result
