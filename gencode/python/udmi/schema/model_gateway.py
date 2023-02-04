"""Generated class for model_gateway.json"""


class GatewayModel:
  """Generated schema class"""

  def __init__(self):
    self.gateway_id = None
    self.network = None
    self.proxy_ids = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = GatewayModel()
    result.gateway_id = source.get('gateway_id')
    result.network = source.get('network')
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
    if self.network:
      result['network'] = self.network # 5
    if self.proxy_ids:
      result['proxy_ids'] = self.proxy_ids # 1
    return result
