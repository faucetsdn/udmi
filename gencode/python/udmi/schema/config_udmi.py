"""Generated class for config_udmi.json"""
from .state_udmi import SetupUdmiState


class SetupUdmiConfig:
  """Generated schema class"""

  def __init__(self):
    self.hostname = None
    self.functions_min = None
    self.functions_max = None
    self.udmi_version = None
    self.udmi_ref = None
    self.udmi_timever = None
    self.built_at = None
    self.built_by = None
    self.deployed_at = None
    self.deployed_by = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SetupUdmiConfig()
    result.hostname = source.get('hostname')
    result.functions_min = source.get('functions_min')
    result.functions_max = source.get('functions_max')
    result.udmi_version = source.get('udmi_version')
    result.udmi_ref = source.get('udmi_ref')
    result.udmi_timever = source.get('udmi_timever')
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
    if self.hostname:
      result['hostname'] = self.hostname # 5
    if self.functions_min:
      result['functions_min'] = self.functions_min # 5
    if self.functions_max:
      result['functions_max'] = self.functions_max # 5
    if self.udmi_version:
      result['udmi_version'] = self.udmi_version # 5
    if self.udmi_ref:
      result['udmi_ref'] = self.udmi_ref # 5
    if self.udmi_timever:
      result['udmi_timever'] = self.udmi_timever # 5
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
    self.timestamp = None
    self.version = None
    self.last_state = None
    self.reply = None
    self.setup = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = UdmiConfig()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.last_state = source.get('last_state')
    result.reply = SetupUdmiState.from_dict(source.get('reply'))
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
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    if self.version:
      result['version'] = self.version # 5
    if self.last_state:
      result['last_state'] = self.last_state # 5
    if self.reply:
      result['reply'] = self.reply.to_dict() # 4
    if self.setup:
      result['setup'] = self.setup.to_dict() # 4
    return result
