"""Generated class for access_iot.json"""


class IotAccess:
  """Generated schema class"""

  def __init__(self):
    self.provider = None
    self.project_id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = IotAccess()
    result.provider = source.get('provider')
    result.project_id = source.get('project_id')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = IotAccess.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.provider:
      result['provider'] = self.provider # 5
    if self.project_id:
      result['project_id'] = self.project_id # 5
    return result
