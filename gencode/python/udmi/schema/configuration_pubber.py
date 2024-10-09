"""Generated class for configuration_pubber.json"""
from .configuration_endpoint import EndpointConfiguration


class PubberConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.endpoint = None
    self.iotProject = None
    self.projectId = None
    self.registryId = None
    self.deviceId = None
    self.gatewayId = None
    self.sitePath = None
    self.keyFile = None
    self.algorithm = None
    self.serialNo = None
    self.macAddr = None
    self.keyBytes = None
    self.options = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PubberConfiguration()
    result.endpoint = EndpointConfiguration.from_dict(source.get('endpoint'))
    result.iotProject = source.get('iotProject')
    result.projectId = source.get('projectId')
    result.registryId = source.get('registryId')
    result.deviceId = source.get('deviceId')
    result.gatewayId = source.get('gatewayId')
    result.sitePath = source.get('sitePath')
    result.keyFile = source.get('keyFile')
    result.algorithm = source.get('algorithm')
    result.serialNo = source.get('serialNo')
    result.macAddr = source.get('macAddr')
    result.keyBytes = source.get('keyBytes')
    result.options = source.get('options')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PubberConfiguration.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.endpoint:
      result['endpoint'] = self.endpoint.to_dict() # 4
    if self.iotProject:
      result['iotProject'] = self.iotProject # 5
    if self.projectId:
      result['projectId'] = self.projectId # 5
    if self.registryId:
      result['registryId'] = self.registryId # 5
    if self.deviceId:
      result['deviceId'] = self.deviceId # 5
    if self.gatewayId:
      result['gatewayId'] = self.gatewayId # 5
    if self.sitePath:
      result['sitePath'] = self.sitePath # 5
    if self.keyFile:
      result['keyFile'] = self.keyFile # 5
    if self.algorithm:
      result['algorithm'] = self.algorithm # 5
    if self.serialNo:
      result['serialNo'] = self.serialNo # 5
    if self.macAddr:
      result['macAddr'] = self.macAddr # 5
    if self.keyBytes:
      result['keyBytes'] = self.keyBytes # 5
    if self.options:
      result['options'] = self.options # 5
    return result
