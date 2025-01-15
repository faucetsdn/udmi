"""Generated class for model_cloud.json"""
from .model_gateway import GatewayModel
from .model_cloud_config import CloudConfigModel


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
    self.req_version = None
    self.connection_type = None
    self.auth_type = None
    self.device_key = None
    self.resource_type = None
    self.gateway = None
    self.config = None
    self.blocked = None
    self.detail = None
    self.credentials = None
    self.updated_time = None
    self.last_event_time = None
    self.last_state_time = None
    self.last_config_time = None
    self.last_error_time = None
    self.last_config_ack = None
    self.num_id = None
    self.operation = None
    self.metadata = None
    self.metadata_str = None
    self.device_ids = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CloudModel()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.req_version = source.get('req_version')
    result.connection_type = source.get('connection_type')
    result.auth_type = source.get('auth_type')
    result.device_key = source.get('device_key')
    result.resource_type = source.get('resource_type')
    result.gateway = GatewayModel.from_dict(source.get('gateway'))
    result.config = CloudConfigModel.from_dict(source.get('config'))
    result.blocked = source.get('blocked')
    result.detail = source.get('detail')
    result.credentials = source.get('credentials')
    result.updated_time = source.get('updated_time')
    result.last_event_time = source.get('last_event_time')
    result.last_state_time = source.get('last_state_time')
    result.last_config_time = source.get('last_config_time')
    result.last_error_time = source.get('last_error_time')
    result.last_config_ack = source.get('last_config_ack')
    result.num_id = source.get('num_id')
    result.operation = source.get('operation')
    result.metadata = source.get('metadata')
    result.metadata_str = source.get('metadata_str')
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
    if self.req_version:
      result['req_version'] = self.req_version # 5
    if self.connection_type:
      result['connection_type'] = self.connection_type # 5
    if self.auth_type:
      result['auth_type'] = self.auth_type # 5
    if self.device_key:
      result['device_key'] = self.device_key # 5
    if self.resource_type:
      result['resource_type'] = self.resource_type # 5
    if self.gateway:
      result['gateway'] = self.gateway.to_dict() # 4
    if self.config:
      result['config'] = self.config.to_dict() # 4
    if self.blocked:
      result['blocked'] = self.blocked # 5
    if self.detail:
      result['detail'] = self.detail # 5
    if self.credentials:
      result['credentials'] = self.credentials # 1
    if self.updated_time:
      result['updated_time'] = self.updated_time # 5
    if self.last_event_time:
      result['last_event_time'] = self.last_event_time # 5
    if self.last_state_time:
      result['last_state_time'] = self.last_state_time # 5
    if self.last_config_time:
      result['last_config_time'] = self.last_config_time # 5
    if self.last_error_time:
      result['last_error_time'] = self.last_error_time # 5
    if self.last_config_ack:
      result['last_config_ack'] = self.last_config_ack # 5
    if self.num_id:
      result['num_id'] = self.num_id # 5
    if self.operation:
      result['operation'] = self.operation # 5
    if self.metadata:
      result['metadata'] = self.metadata # 1
    if self.metadata_str:
      result['metadata_str'] = self.metadata_str # 5
    if self.device_ids:
      result['device_ids'] = Object18ECC5EE.expand_dict(self.device_ids) # 2
    return result
