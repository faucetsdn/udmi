"""Generated class for configuration_endpoint.json"""


class EndpointConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.hostname = None
    self.port = None
    self.client_id = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = EndpointConfiguration()
    result.hostname = source.get('hostname')
    result.port = source.get('port')
    result.client_id = source.get('client_id')
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
    if self.hostname:
      result['hostname'] = self.hostname # 5
    if self.port:
      result['port'] = self.port # 5
    if self.client_id:
      result['client_id'] = self.client_id # 5
    return result
