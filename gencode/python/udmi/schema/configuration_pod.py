"""Generated class for configuration_pod.json"""
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration


class PodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.flow_defaults = None
    self.target_flow = None
    self.state_flow = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PodConfiguration()
    result.flow_defaults = EndpointConfiguration.from_dict(source.get('flow_defaults'))
    result.target_flow = EndpointConfiguration.from_dict(source.get('target_flow'))
    result.state_flow = EndpointConfiguration.from_dict(source.get('state_flow'))
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
    if self.target_flow:
      result['target_flow'] = self.target_flow.to_dict() # 4
    if self.state_flow:
      result['state_flow'] = self.state_flow.to_dict() # 4
    return result
