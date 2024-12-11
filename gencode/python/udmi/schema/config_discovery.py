"""Generated class for config_discovery.json"""


class Object2826EC2D:
  """Generated schema class"""

  def __init__(self):
    self.families = None
    self.devices = None
    self.points = None
    self.features = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object2826EC2D()
    result.families = source.get('families')
    result.devices = source.get('devices')
    result.points = source.get('points')
    result.features = source.get('features')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object2826EC2D.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.families:
      result['families'] = self.families # 5
    if self.devices:
      result['devices'] = self.devices # 5
    if self.points:
      result['points'] = self.points # 5
    if self.features:
      result['features'] = self.features # 5
    return result
from .config_discovery_family import FamilyDiscoveryConfig


class DiscoveryConfig:
  """Generated schema class"""

  def __init__(self):
    self.generation = None
    self.enumerations = None
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryConfig()
    result.generation = source.get('generation')
    result.enumerations = Object2826EC2D.from_dict(source.get('enumerations'))
    result.families = FamilyDiscoveryConfig.map_from(source.get('families'))
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
    if self.enumerations:
      result['enumerations'] = self.enumerations.to_dict() # 4
    if self.families:
      result['families'] = FamilyDiscoveryConfig.expand_dict(self.families) # 2
    return result
