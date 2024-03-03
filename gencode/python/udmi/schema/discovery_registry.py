"""Generated class for discovery_registry.json"""


class RegistryDiscovery:
  """Generated schema class"""

  def __init__(self):
    self.last_seen = None
    self.last_update = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = RegistryDiscovery()
    result.last_seen = source.get('last_seen')
    result.last_update = source.get('last_update')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = RegistryDiscovery.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.last_seen:
      result['last_seen'] = self.last_seen # 5
    if self.last_update:
      result['last_update'] = self.last_update # 5
    return result
