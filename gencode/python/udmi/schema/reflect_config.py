"""Generated class for reflect_config.json"""


class SetupReflectorConfig:
  """Generated schema class"""

  def __init__(self):
    self.functions_min = None
    self.functions_max = None
    self.udmi_version = None
    self.last_state = None
    self.deployed_at = None
    self.deployed_by = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SetupReflectorConfig()
    result.functions_min = source.get('functions_min')
    result.functions_max = source.get('functions_max')
    result.udmi_version = source.get('udmi_version')
    result.last_state = source.get('last_state')
    result.deployed_at = source.get('deployed_at')
    result.deployed_by = source.get('deployed_by')
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
    if self.functions_min:
      result['functions_min'] = self.functions_min # 5
    if self.functions_max:
      result['functions_max'] = self.functions_max # 5
    if self.udmi_version:
      result['udmi_version'] = self.udmi_version # 5
    if self.last_state:
      result['last_state'] = self.last_state # 5
    if self.deployed_at:
      result['deployed_at'] = self.deployed_at # 5
    if self.deployed_by:
      result['deployed_by'] = self.deployed_by # 5
    return result


class ReflectorConfig:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.udmis = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ReflectorConfig()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.udmis = SetupReflectorConfig.from_dict(source.get('udmis'))
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
    if self.udmis:
      result['udmis'] = self.udmis.to_dict() # 4
    return result
