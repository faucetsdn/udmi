"""Generated class for config_system_testing.json"""


class TestingSystemConfig:
  """Generated schema class"""

  def __init__(self):
    self.sequence_name = None
    self.transaction_id = None
    self.config_base = None
    self.endpoint_type = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = TestingSystemConfig()
    result.sequence_name = source.get('sequence_name')
    result.transaction_id = source.get('transaction_id')
    result.config_base = source.get('config_base')
    result.endpoint_type = source.get('endpoint_type')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = TestingSystemConfig.from_dict(source[key])
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
    if self.transaction_id:
      result['transaction_id'] = self.transaction_id # 5
    if self.config_base:
      result['config_base'] = self.config_base # 5
    if self.endpoint_type:
      result['endpoint_type'] = self.endpoint_type # 5
    return result
