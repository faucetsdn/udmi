"""Generated class for access_iot.json"""


class IotAccess:
  """Generated schema class"""

  def __init__(self):
    self.name = None
    self.provider = None
    self.project_id = None
    self.profile_sec = None
    self.options = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = IotAccess()
    result.name = source.get('name')
    result.provider = source.get('provider')
    result.project_id = source.get('project_id')
    result.profile_sec = source.get('profile_sec')
    result.options = source.get('options')
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
    if self.name:
      result['name'] = self.name # 5
    if self.provider:
      result['provider'] = self.provider # 5
    if self.project_id:
      result['project_id'] = self.project_id # 5
    if self.profile_sec:
      result['profile_sec'] = self.profile_sec # 5
    if self.options:
      result['options'] = self.options # 5
    return result
