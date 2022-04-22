"""Generated class for model_cloud.json"""


class CloudModel:
  """Generated schema class"""

  def __init__(self):
    self.auth_type = None
    self.device_key = None
    self.is_gateway = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CloudModel()
    result.auth_type = source.get('auth_type')
    result.device_key = source.get('device_key')
    result.is_gateway = source.get('is_gateway')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = CloudModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.auth_type:
      result['auth_type'] = self.auth_type # 5
    if self.device_key:
      result['device_key'] = self.device_key # 5
    if self.is_gateway:
      result['is_gateway'] = self.is_gateway # 5
    return result
