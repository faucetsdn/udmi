"""Generated class for model_localnet_subsystem.json"""


class SubsystemLocalnetMetadata:
  """Generated schema class"""

  def __init__(self):
    self.local_id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SubsystemLocalnetMetadata()
    result.local_id = source.get('local_id')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SubsystemLocalnetMetadata.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.local_id:
      result['local_id'] = self.local_id # 5
    return result
