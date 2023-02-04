"""Generated class for config_discovery.json"""


class ObjectFE47166E:
  """Generated schema class"""

  def __init__(self):
    self.networks = None
    self.uniqs = None
    self.features = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectFE47166E()
    result.networks = source.get('networks')
    result.uniqs = source.get('uniqs')
    result.features = source.get('features')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectFE47166E.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.networks:
      result['networks'] = self.networks # 5
    if self.uniqs:
      result['uniqs'] = self.uniqs # 5
    if self.features:
      result['features'] = self.features # 5
    return result
from .config_discovery_network import NetworkDiscoveryConfig


class DiscoveryConfig:
  """Generated schema class"""

  def __init__(self):
    self.generation = None
    self.enumerate = None
    self.networks = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryConfig()
    result.generation = source.get('generation')
    result.enumerate = ObjectFE47166E.from_dict(source.get('enumerate'))
    result.networks = NetworkDiscoveryConfig.map_from(source.get('networks'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveryConfig.from_dict(source[key])
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
    if self.enumerate:
      result['enumerate'] = self.enumerate.to_dict() # 4
    if self.networks:
      result['networks'] = NetworkDiscoveryConfig.expand_dict(self.networks) # 2
    return result
