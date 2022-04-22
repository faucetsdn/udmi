"""Generated class for model_testing_target.json"""


class TargetTestingModel:
  """Generated schema class"""

  def __init__(self):
    self.target_point = None
    self.target_value = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = TargetTestingModel()
    result.target_point = source.get('target_point')
    result.target_value = source.get('target_value')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = TargetTestingModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.target_point:
      result['target_point'] = self.target_point # 5
    if self.target_value:
      result['target_value'] = self.target_value # 5
    return result
