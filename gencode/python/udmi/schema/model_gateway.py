"""Generated class for model_gateway.json"""
from .model_localnet_family import FamilyLocalnetModel


class GatewayModel:
  """Generated schema class"""

  def __init__(self):
    self.gateway_id = None
    self.target = None
    self.proxy_ids = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = GatewayModel()
    result.gateway_id = source.get('gateway_id')
    result.target = FamilyLocalnetModel.from_dict(source.get('target'))
    result.proxy_ids = source.get('proxy_ids')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = GatewayModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.gateway_id:
      result['gateway_id'] = self.gateway_id # 5
    if self.target:
      result['target'] = self.target.to_dict() # 4
    if self.proxy_ids:
      result['proxy_ids'] = self.proxy_ids # 1
    return result
