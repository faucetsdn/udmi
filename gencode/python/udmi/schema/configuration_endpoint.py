"""Generated class for configuration_endpoint.json"""


class ObjectF6CBF26A:
  """Generated schema class"""

  def __init__(self):
    self.basic = None
    self.jwt = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectF6CBF26A()
    result.basic = source.get('basic')
    result.jwt = source.get('jwt')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectF6CBF26A.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.basic:
      result['basic'] = self.basic # 5
    if self.jwt:
      result['jwt'] = self.jwt # 5
    return result


class EndpointConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.name = None
    self.protocol = None
    self.transport = None
    self.hostname = None
    self.payload = None
    self.error = None
    self.port = None
    self.config_sync_sec = None
    self.client_id = None
    self.topic_prefix = None
    self.recv_id = None
    self.send_id = None
    self.side_id = None
    self.gatewayId = None
    self.deviceId = None
    self.enabled = None
    self.noConfigAck = None
    self.capacity = None
    self.publish_delay_sec = None
    self.periodic_sec = None
    self.keyBytes = None
    self.algorithm = None
    self.auth_provider = None
    self.generation = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = EndpointConfiguration()
    result.name = source.get('name')
    result.protocol = source.get('protocol')
    result.transport = source.get('transport')
    result.hostname = source.get('hostname')
    result.payload = source.get('payload')
    result.error = source.get('error')
    result.port = source.get('port')
    result.config_sync_sec = source.get('config_sync_sec')
    result.client_id = source.get('client_id')
    result.topic_prefix = source.get('topic_prefix')
    result.recv_id = source.get('recv_id')
    result.send_id = source.get('send_id')
    result.side_id = source.get('side_id')
    result.gatewayId = source.get('gatewayId')
    result.deviceId = source.get('deviceId')
    result.enabled = source.get('enabled')
    result.noConfigAck = source.get('noConfigAck')
    result.capacity = source.get('capacity')
    result.publish_delay_sec = source.get('publish_delay_sec')
    result.periodic_sec = source.get('periodic_sec')
    result.keyBytes = source.get('keyBytes')
    result.algorithm = source.get('algorithm')
    result.auth_provider = ObjectF6CBF26A.from_dict(source.get('auth_provider'))
    result.generation = source.get('generation')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = EndpointConfiguration.from_dict(source[key])
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
    if self.protocol:
      result['protocol'] = self.protocol # 5
    if self.transport:
      result['transport'] = self.transport # 5
    if self.hostname:
      result['hostname'] = self.hostname # 5
    if self.payload:
      result['payload'] = self.payload # 5
    if self.error:
      result['error'] = self.error # 5
    if self.port:
      result['port'] = self.port # 5
    if self.config_sync_sec:
      result['config_sync_sec'] = self.config_sync_sec # 5
    if self.client_id:
      result['client_id'] = self.client_id # 5
    if self.topic_prefix:
      result['topic_prefix'] = self.topic_prefix # 5
    if self.recv_id:
      result['recv_id'] = self.recv_id # 5
    if self.send_id:
      result['send_id'] = self.send_id # 5
    if self.side_id:
      result['side_id'] = self.side_id # 5
    if self.gatewayId:
      result['gatewayId'] = self.gatewayId # 5
    if self.deviceId:
      result['deviceId'] = self.deviceId # 5
    if self.enabled:
      result['enabled'] = self.enabled # 5
    if self.noConfigAck:
      result['noConfigAck'] = self.noConfigAck # 5
    if self.capacity:
      result['capacity'] = self.capacity # 5
    if self.publish_delay_sec:
      result['publish_delay_sec'] = self.publish_delay_sec # 5
    if self.periodic_sec:
      result['periodic_sec'] = self.periodic_sec # 5
    if self.keyBytes:
      result['keyBytes'] = self.keyBytes # 5
    if self.algorithm:
      result['algorithm'] = self.algorithm # 5
    if self.auth_provider:
      result['auth_provider'] = self.auth_provider.to_dict() # 4
    if self.generation:
      result['generation'] = self.generation # 5
    return result
