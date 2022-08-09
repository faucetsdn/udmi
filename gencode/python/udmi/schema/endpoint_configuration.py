"""Generated class for endpoint_configuration.json"""


class EndpointConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.bridgeHostname = None
    self.bridgePort = None
    self.cloudRegion = None
    self.projectId = None
    self.registryId = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = EndpointConfiguration()
    result.bridgeHostname = source.get('bridgeHostname')
    result.bridgePort = source.get('bridgePort')
    result.cloudRegion = source.get('cloudRegion')
    result.projectId = source.get('projectId')
    result.registryId = source.get('registryId')
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
    if self.bridgeHostname:
      result['bridgeHostname'] = self.bridgeHostname # 5
    if self.bridgePort:
      result['bridgePort'] = self.bridgePort # 5
    if self.cloudRegion:
      result['cloudRegion'] = self.cloudRegion # 5
    if self.projectId:
      result['projectId'] = self.projectId # 5
    if self.registryId:
      result['registryId'] = self.registryId # 5
    return result
