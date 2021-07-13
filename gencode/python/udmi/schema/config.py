"""Generated class for config.json"""
from .config_system import SystemConfig
from .config_gateway import GatewayConfig
from .config_localnet import LocalnetConfig
from .config_blobset import BlobsetConfig
from .config_pointset import PointsetConfig


class Config:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.system = None
    self.gateway = None
    self.localnet = None
    self.blobset = None
    self.pointset = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Config()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.system = SystemConfig.from_dict(source.get('system'))
    result.gateway = GatewayConfig.from_dict(source.get('gateway'))
    result.localnet = LocalnetConfig.from_dict(source.get('localnet'))
    result.blobset = BlobsetConfig.from_dict(source.get('blobset'))
    result.pointset = PointsetConfig.from_dict(source.get('pointset'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Config.from_dict(source[key])
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
    if self.localnet:
      result['localnet'] = self.localnet.to_dict() # 4
    if self.blobset:
      result['blobset'] = self.blobset.to_dict() # 4
    if self.pointset:
      result['pointset'] = self.pointset.to_dict() # 4
    return result
