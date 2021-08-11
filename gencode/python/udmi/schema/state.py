"""Generated class for state.json"""
from .state_system import SystemState
from .state_gateway import GatewayState
from .state_blobset import BlobsetState
from .state_pointset import PointsetState


class State:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.system = None
    self.gateway = None
    self.blobset = None
    self.pointset = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = State()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.system = SystemState.from_dict(source.get('system'))
    result.gateway = GatewayState.from_dict(source.get('gateway'))
    result.blobset = BlobsetState.from_dict(source.get('blobset'))
    result.pointset = PointsetState.from_dict(source.get('pointset'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = State.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    if self.version:
      result['version'] = self.version # 5
    if self.system:
      result['system'] = self.system.to_dict() # 4
    if self.gateway:
      result['gateway'] = self.gateway.to_dict() # 4
    if self.blobset:
      result['blobset'] = self.blobset.to_dict() # 4
    if self.pointset:
      result['pointset'] = self.pointset.to_dict() # 4
    return result
