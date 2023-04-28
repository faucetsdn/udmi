"""Generated class for configuration_pod.json"""
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration
from .configuration_pod_bridge import BridgePodConfiguration


class PodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.flow_defaults = None
    self.flows = None
    self.bridges = None
    self.iot_access = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PodConfiguration()
    result.flow_defaults = EndpointConfiguration.from_dict(source.get('flow_defaults'))
    result.flows = EndpointConfiguration.map_from(source.get('flows'))
    result.bridges = BridgePodConfiguration.map_from(source.get('bridges'))
    result.iot_access = source.get('iot_access')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PodConfiguration.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.flow_defaults:
      result['flow_defaults'] = self.flow_defaults.to_dict() # 4
    if self.flows:
      result['flows'] = EndpointConfiguration.expand_dict(self.flows) # 2
    if self.bridges:
      result['bridges'] = BridgePodConfiguration.expand_dict(self.bridges) # 2
    if self.iot_access:
      result['iot_access'] = self.iot_access # 5
    return result
