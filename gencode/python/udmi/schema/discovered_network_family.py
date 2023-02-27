"""Generated class for discovered_network_family.json"""


class DiscoveredNetworkFamily:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.scope = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveredNetworkFamily()
    result.addr = source.get('addr')
    result.scope = source.get('scope')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveredNetworkFamily.from_dict(source[key])
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
