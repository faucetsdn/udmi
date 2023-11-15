"""Generated class for configuration_pod_bridge.json"""
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration


class BridgePodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.enabled = None
    self.from = None
    self.to = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BridgePodConfiguration()
    result.enabled = source.get('enabled')
    result.from = EndpointConfiguration.from_dict(source.get('from'))
    result.to = EndpointConfiguration.from_dict(source.get('to'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BridgePodConfiguration.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.enabled:
      result['enabled'] = self.enabled # 5
    if self.from:
      result['from'] = self.from.to_dict() # 4
    if self.to:
      result['to'] = self.to.to_dict() # 4
    return result
