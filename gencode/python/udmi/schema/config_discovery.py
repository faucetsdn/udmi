"""Generated class for config_discovery.json"""


class ObjectFFD6F28C:
  """Generated schema class"""

  def __init__(self):
    self.families = None
    self.uniqs = None
    self.features = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectFFD6F28C()
    result.families = source.get('families')
    result.uniqs = source.get('uniqs')
    result.features = source.get('features')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectFFD6F28C.from_dict(source[key])
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
    if self.uniqs:
      result['uniqs'] = self.uniqs # 5
    if self.features:
      result['features'] = self.features # 5
    return result
from .config_discovery_family import FamilyDiscoveryConfig


class DiscoveryConfig:
  """Generated schema class"""

  def __init__(self):
    self.generation = None
    self.enumerate = None
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryConfig()
    result.generation = source.get('generation')
    result.enumerate = ObjectFFD6F28C.from_dict(source.get('enumerate'))
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
    if self.enumerate:
      result['enumerate'] = self.enumerate.to_dict() # 4
    if self.families:
      result['families'] = FamilyDiscoveryConfig.expand_dict(self.families) # 2
    return result
