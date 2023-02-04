"""Generated class for model_discovery.json"""
from .model_discovery_network import NetworkDiscoveryTestingModel


class DiscoveryModel:
  """Generated schema class"""

  def __init__(self):
    self.networks = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryModel()
    result.networks = NetworkDiscoveryTestingModel.map_from(source.get('networks'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveryModel.from_dict(source[key])
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
      result['networks'] = NetworkDiscoveryTestingModel.expand_dict(self.networks) # 2
    return result
