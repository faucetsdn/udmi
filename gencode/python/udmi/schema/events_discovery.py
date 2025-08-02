"""Generated class for events_discovery.json"""
from .entry import Entry
from .discovery_family import FamilyDiscovery
from .model_cloud import CloudModel
from .model_cloud import CloudModel
from .model_pointset_point import PointPointsetModel
from .discovery_ref import RefDiscovery
from .discovery_feature import FeatureDiscovery
from .model_cloud import CloudModel
from .ancillary_properties import AncillaryProperties
from .state_system_hardware import StateSystemHardware


class SystemDiscoveryData:
  """Generated schema class"""

  def __init__(self):
    self.description = None
    self.name = None
    self.serial_no = None
    self.ancillary = None
    self.hardware = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemDiscoveryData()
    result.description = source.get('description')
    result.name = source.get('name')
    result.serial_no = source.get('serial_no')
    result.ancillary = AncillaryProperties.from_dict(source.get('ancillary'))
    result.hardware = StateSystemHardware.from_dict(source.get('hardware'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemDiscoveryData.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.description:
      result['description'] = self.description # 5
    if self.name:
      result['name'] = self.name # 5
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.ancillary:
      result['ancillary'] = self.ancillary.to_dict() # 4
    if self.hardware:
      result['hardware'] = self.hardware.to_dict() # 4
    return result


class DiscoveryEvents:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.generation = None
    self.status = None
    self.family = None
    self.addr = None
    self.network = None
    self.event_no = None
    self.families = None
    self.registries = None
    self.devices = None
    self.points = None
    self.refs = None
    self.features = None
    self.cloud_model = None
    self.system = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryEvents()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.generation = source.get('generation')
    result.status = Entry.from_dict(source.get('status'))
    result.family = source.get('family')
    result.addr = source.get('addr')
    result.network = source.get('network')
    result.event_no = source.get('event_no')
    result.families = FamilyDiscovery.map_from(source.get('families'))
    result.registries = CloudModel.map_from(source.get('registries'))
    result.devices = CloudModel.map_from(source.get('devices'))
    result.points = PointPointsetModel.map_from(source.get('points'))
    result.refs = RefDiscovery.map_from(source.get('refs'))
    result.features = FeatureDiscovery.map_from(source.get('features'))
    result.cloud_model = CloudModel.from_dict(source.get('cloud_model'))
    result.system = SystemDiscoveryData.from_dict(source.get('system'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveryEvents.from_dict(source[key])
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
    if self.generation:
      result['generation'] = self.generation # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.family:
      result['family'] = self.family # 5
    if self.addr:
      result['addr'] = self.addr # 5
    if self.network:
      result['network'] = self.network # 5
    if self.event_no:
      result['event_no'] = self.event_no # 5
    if self.families:
      result['families'] = FamilyDiscovery.expand_dict(self.families) # 2
    if self.registries:
      result['registries'] = CloudModel.expand_dict(self.registries) # 2
    if self.devices:
      result['devices'] = CloudModel.expand_dict(self.devices) # 2
    if self.points:
      result['points'] = PointPointsetModel.expand_dict(self.points) # 2
    if self.refs:
      result['refs'] = RefDiscovery.expand_dict(self.refs) # 2
    if self.features:
      result['features'] = FeatureDiscovery.expand_dict(self.features) # 2
    if self.cloud_model:
      result['cloud_model'] = self.cloud_model.to_dict() # 4
    if self.system:
      result['system'] = self.system.to_dict() # 4
    return result
