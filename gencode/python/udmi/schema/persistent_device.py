"""Generated class for persistent_device.json"""
from .configuration_endpoint import EndpointConfiguration


class DevicePersistent:
  """Generated schema class"""

  def __init__(self):
    self.endpoint = None
    self.restarts = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DevicePersistent()
    result.endpoint = EndpointConfiguration.from_dict(source.get('endpoint'))
    result.restarts = source.get('restarts')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DevicePersistent.from_dict(source[key])
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
    if self.restarts:
      result['restarts'] = self.restarts # 5
    return result
