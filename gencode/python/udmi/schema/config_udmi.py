"""Generated class for config_udmi.json"""


class SetupUdmiConfig:
  """Generated schema class"""

  def __init__(self):
    self.functions_min = None
    self.functions_max = None
    self.udmi_version = None
    self.udmi_source = None
    self.udmi_functions = None
    self.built_at = None
    self.built_by = None
    self.deployed_at = None
    self.deployed_by = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SetupUdmiConfig()
    result.functions_min = source.get('functions_min')
    result.functions_max = source.get('functions_max')
    result.udmi_version = source.get('udmi_version')
    result.udmi_source = source.get('udmi_source')
    result.udmi_functions = source.get('udmi_functions')
    result.built_at = source.get('built_at')
    result.built_by = source.get('built_by')
    result.deployed_at = source.get('deployed_at')
    result.deployed_by = source.get('deployed_by')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SetupUdmiConfig.from_dict(source[key])
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
    if self.udmi_source:
      result['udmi_source'] = self.udmi_source # 5
    if self.udmi_functions:
      result['udmi_functions'] = self.udmi_functions # 5
    if self.built_at:
      result['built_at'] = self.built_at # 5
    if self.built_by:
      result['built_by'] = self.built_by # 5
    if self.deployed_at:
      result['deployed_at'] = self.deployed_at # 5
    if self.deployed_by:
      result['deployed_by'] = self.deployed_by # 5
    return result


class UdmiConfig:
  """Generated schema class"""

  def __init__(self):
    self.last_state = None
    self.setup = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = UdmiConfig()
    result.last_state = source.get('last_state')
    result.setup = SetupUdmiConfig.from_dict(source.get('setup'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = UdmiConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.last_state:
      result['last_state'] = self.last_state # 5
    if self.setup:
      result['setup'] = self.setup.to_dict() # 4
    return result
