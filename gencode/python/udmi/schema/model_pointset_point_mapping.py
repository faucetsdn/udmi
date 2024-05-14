"""Generated class for model_pointset_point_mapping.json"""


class PointMappingModel:
  """Generated schema class"""

  def __init__(self):
    self.name = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointMappingModel()
    result.name = source.get('name')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointMappingModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.name:
      result['name'] = self.name # 5
    return result
