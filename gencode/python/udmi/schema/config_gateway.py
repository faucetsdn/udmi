"""Generated class for config_gateway.json"""


class GatewayConfig:
  """Generated schema class"""

  def __init__(self):
    self.proxy_ids = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = GatewayConfig()
    result.proxy_ids = source.get('proxy_ids')
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
    return result
