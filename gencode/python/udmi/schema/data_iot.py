"""Generated class for data_iot.json"""


class IotData:
  """Generated schema class"""

  def __init__(self):
    self.name = None
    self.provider = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = IotData()
    result.name = source.get('name')
    result.provider = source.get('provider')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = IotData.from_dict(source[key])
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
    if self.provider:
      result['provider'] = self.provider # 5
    return result
