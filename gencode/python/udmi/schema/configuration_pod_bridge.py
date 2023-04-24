"""Generated class for configuration_pod_bridge.json"""
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration


class BridgePodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.yin = None
    self.yang = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BridgePodConfiguration()
    result.yin = EndpointConfiguration.from_dict(source.get('yin'))
    result.yang = EndpointConfiguration.from_dict(source.get('yang'))
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
    if self.yin:
      result['yin'] = self.yin.to_dict() # 4
    if self.yang:
      result['yang'] = self.yang.to_dict() # 4
    return result
