"""Generated class for config_gateway.json"""
from .model_localnet_family import FamilyLocalnetModel


class GatewayConfig:
  """Generated schema class"""

  def __init__(self):
    self.proxy_ids = None
    self.target = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = GatewayConfig()
    result.proxy_ids = source.get('proxy_ids')
    result.target = FamilyLocalnetModel.from_dict(source.get('target'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = GatewayConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.proxy_ids:
      result['proxy_ids'] = self.proxy_ids # 1
    if self.target:
      result['target'] = self.target.to_dict() # 4
    return result
