"""Generated class for model_system_connection.json"""


class SystemConnection:
  """Generated schema class"""

  def __init__(self):
    self.type = None
    self.model = None
    self.vlan_name = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemConnection()
    result.type = source.get('type')
    result.model = source.get('model')
    result.vlan_name = source.get('vlan_name')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemConnection.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.type:
      result['type'] = self.type # 5
    if self.model:
      result['model'] = self.model # 5
    if self.vlan_name:
      result['vlan_name'] = self.vlan_name # 5
    return result
