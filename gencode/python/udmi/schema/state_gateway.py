"""Generated class for state_gateway.json"""


class GatewayState:
  """Generated schema class"""

  def __init__(self):
    self.error_ids = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = GatewayState()
    result.error_ids = source.get('error_ids')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = GatewayState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.error_ids:
      result['error_ids'] = self.error_ids # 1
    return result
