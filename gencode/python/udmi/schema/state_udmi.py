"""Generated class for state_udmi.json"""


class SetupUdmiState:
  """Generated schema class"""

  def __init__(self):
    self.user = None
    self.udmi_version = None
    self.udmi_commit = None
    self.udmi_ref = None
    self.udmi_timever = None
    self.msg_source = None
    self.update_to = None
    self.tool_name = None
    self.transaction_id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SetupUdmiState()
    result.user = source.get('user')
    result.udmi_version = source.get('udmi_version')
    result.udmi_commit = source.get('udmi_commit')
    result.udmi_ref = source.get('udmi_ref')
    result.udmi_timever = source.get('udmi_timever')
    result.msg_source = source.get('msg_source')
    result.update_to = source.get('update_to')
    result.tool_name = source.get('tool_name')
    result.transaction_id = source.get('transaction_id')
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
    if self.udmi_version:
      result['udmi_version'] = self.udmi_version # 5
    if self.udmi_commit:
      result['udmi_commit'] = self.udmi_commit # 5
    if self.udmi_ref:
      result['udmi_ref'] = self.udmi_ref # 5
    if self.udmi_timever:
      result['udmi_timever'] = self.udmi_timever # 5
    if self.msg_source:
      result['msg_source'] = self.msg_source # 5
    if self.update_to:
      result['update_to'] = self.update_to # 5
    if self.tool_name:
      result['tool_name'] = self.tool_name # 5
    if self.transaction_id:
      result['transaction_id'] = self.transaction_id # 5
    return result


class UdmiState:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.source = None
    self.regions = None
    self.setup = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = UdmiState()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.source = source.get('source')
    result.regions = source.get('regions')
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
    if self.source:
      result['source'] = self.source # 5
    if self.regions:
      result['regions'] = self.regions # 1
    if self.setup:
      result['setup'] = self.setup.to_dict() # 4
    return result
