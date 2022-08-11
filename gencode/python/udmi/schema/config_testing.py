"""Generated class for config_testing.json"""


class TestingConfig:
  """Generated schema class"""

  def __init__(self):
    self.sequence_name = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = TestingConfig()
    result.sequence_name = source.get('sequence_name')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = TestingConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.sequence_name:
      result['sequence_name'] = self.sequence_name # 5
    return result
