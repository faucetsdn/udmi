"""Generated class for state_udmi.json"""


class SetupUdmiState:
  """Generated schema class"""

  def __init__(self):
    self.user = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SetupUdmiState()
    result.user = source.get('user')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SetupUdmiState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.user:
      result['user'] = self.user # 5
    return result


class UdmiState:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.setup = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = UdmiState()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.setup = SetupUdmiState.from_dict(source.get('setup'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = UdmiState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    if self.version:
      result['version'] = self.version # 5
    if self.setup:
      result['setup'] = self.setup.to_dict() # 4
    return result
