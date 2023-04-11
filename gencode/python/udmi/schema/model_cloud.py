"""Generated class for model_cloud.json"""


class Object18ECC5EE:
  """Generated schema class"""

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object18ECC5EE()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object18ECC5EE.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    return result


class CloudModel:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.auth_type = None
    self.device_key = None
    self.is_gateway = None
    self.blocked = None
    self.credentials = None
    self.last_event_time = None
    self.num_id = None
    self.operation = None
    self.metadata = None
    self.device_ids = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CloudModel()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.auth_type = source.get('auth_type')
    result.device_key = source.get('device_key')
    result.is_gateway = source.get('is_gateway')
    result.blocked = source.get('blocked')
    result.credentials = source.get('credentials')
    result.last_event_time = source.get('last_event_time')
    result.num_id = source.get('num_id')
    result.operation = source.get('operation')
    result.metadata = source.get('metadata')
    result.device_ids = Object18ECC5EE.map_from(source.get('device_ids'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = CloudModel.from_dict(source[key])
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
    if self.auth_type:
      result['auth_type'] = self.auth_type # 5
    if self.device_key:
      result['device_key'] = self.device_key # 5
    if self.is_gateway:
      result['is_gateway'] = self.is_gateway # 5
    if self.blocked:
      result['blocked'] = self.blocked # 5
    if self.credentials:
      result['credentials'] = self.credentials # 1
    if self.last_event_time:
      result['last_event_time'] = self.last_event_time # 5
    if self.num_id:
      result['num_id'] = self.num_id # 5
    if self.operation:
      result['operation'] = self.operation # 5
    if self.metadata:
      result['metadata'] = self.metadata # 1
    if self.device_ids:
      result['device_ids'] = Object18ECC5EE.expand_dict(self.device_ids) # 2
    return result
