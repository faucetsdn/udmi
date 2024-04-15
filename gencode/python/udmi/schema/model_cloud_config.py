"""Generated class for model_cloud_config.json"""


class CloudConfigModel:
  """Generated schema class"""

  def __init__(self):
    self.static_file = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CloudConfigModel()
    result.static_file = source.get('static_file')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = CloudConfigModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.static_file:
      result['static_file'] = self.static_file # 5
    return result
