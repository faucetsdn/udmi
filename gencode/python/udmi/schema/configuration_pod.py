"""Generated class for configuration_pod.json"""
from .configuration_pod_base import BasePodConfiguration
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration
from .configuration_pod_bridge import BridgePodConfiguration
from .access_iot import IotAccess
from .configuration_endpoint import EndpointConfiguration


class PodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.base = None
    self.flow_defaults = None
    self.crons = None
    self.flows = None
    self.bridges = None
    self.iot_access = None
    self.distributors = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PodConfiguration()
    result.base = BasePodConfiguration.from_dict(source.get('base'))
    result.flow_defaults = EndpointConfiguration.from_dict(source.get('flow_defaults'))
    result.crons = EndpointConfiguration.map_from(source.get('crons'))
    result.flows = EndpointConfiguration.map_from(source.get('flows'))
    result.bridges = BridgePodConfiguration.map_from(source.get('bridges'))
    result.iot_access = IotAccess.map_from(source.get('iot_access'))
    result.distributors = EndpointConfiguration.map_from(source.get('distributors'))
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
    if self.base:
      result['base'] = self.base.to_dict() # 4
    if self.flow_defaults:
      result['flow_defaults'] = self.flow_defaults.to_dict() # 4
    if self.crons:
      result['crons'] = EndpointConfiguration.expand_dict(self.crons) # 2
    if self.flows:
      result['flows'] = EndpointConfiguration.expand_dict(self.flows) # 2
    if self.bridges:
      result['bridges'] = BridgePodConfiguration.expand_dict(self.bridges) # 2
    if self.iot_access:
      result['iot_access'] = IotAccess.expand_dict(self.iot_access) # 2
    if self.distributors:
      result['distributors'] = EndpointConfiguration.expand_dict(self.distributors) # 2
    return result
