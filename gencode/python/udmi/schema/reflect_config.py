"""Generated class for reflect_config.json"""


class SetupReflectorConfig:
  """Generated schema class"""

  def __init__(self):
    self.functions = None
    self.last_state = None
    self.deployed_at = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SetupReflectorConfig()
    result.functions = source.get('functions')
    result.last_state = source.get('last_state')
    result.deployed_at = source.get('deployed_at')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SetupReflectorConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.functions:
      result['functions'] = self.functions # 5
    if self.last_state:
      result['last_state'] = self.last_state # 5
    if self.deployed_at:
      result['deployed_at'] = self.deployed_at # 5
    return result


class ReflectorConfig:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.setup = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ReflectorConfig()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.setup = SetupReflectorConfig.from_dict(source.get('setup'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ReflectorConfig.from_dict(source[key])
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
