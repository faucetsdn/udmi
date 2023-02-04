"""Generated class for event_discovery_network.json"""


class NetworkDiscoveryEvent:
  """Generated schema class"""

  def __init__(self):
    self.id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = NetworkDiscoveryEvent()
    result.id = source.get('id')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = NetworkDiscoveryEvent.from_dict(source[key])
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
