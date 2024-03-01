"""Generated class for model_cloud_config.json"""


class CloudModel:
  """Generated schema class"""

  def __init__(self):
    self.file = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CloudModel()
    result.file = source.get('file')
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
    if self.file:
      result['file'] = self.file # 5
    return result
