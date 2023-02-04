"""Generated class for event_discovery.json"""
from .common import Entry
from .event_discovery_network import NetworkDiscoveryEvent
from .event_discovery_point import PointEnumerationEvent


class ObjectE6EEEF50:
  """Generated schema class"""

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectE6EEEF50()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectE6EEEF50.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    return result
from .ancillary_properties import AncillaryProperties
from .state_system_hardware import StateSystemHardware


class SystemDiscoveryEvent:
  """Generated schema class"""

  def __init__(self):
    self.serial_no = None
    self.ancillary = None
    self.hardware = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemDiscoveryEvent()
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
      result[key] = SystemDiscoveryEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.ancillary:
      result['ancillary'] = self.ancillary.to_dict() # 4
    if self.hardware:
      result['hardware'] = self.hardware.to_dict() # 4
    return result


class DiscoveryEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.generation = None
    self.status = None
    self.scan_network = None
    self.scan_id = None
    self.networks = None
    self.uniqs = None
    self.features = None
    self.system = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.generation = source.get('generation')
    result.status = Entry.from_dict(source.get('status'))
    result.scan_network = source.get('scan_network')
    result.scan_id = source.get('scan_id')
    result.networks = NetworkDiscoveryEvent.map_from(source.get('networks'))
    result.uniqs = PointEnumerationEvent.map_from(source.get('uniqs'))
    result.features = ObjectE6EEEF50.map_from(source.get('features'))
    result.system = SystemDiscoveryEvent.from_dict(source.get('system'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveryEvent.from_dict(source[key])
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
    if self.scan_network:
      result['scan_network'] = self.scan_network # 5
    if self.scan_id:
      result['scan_id'] = self.scan_id # 5
    if self.networks:
      result['networks'] = NetworkDiscoveryEvent.expand_dict(self.networks) # 2
    if self.uniqs:
      result['uniqs'] = PointEnumerationEvent.expand_dict(self.uniqs) # 2
    if self.features:
      result['features'] = ObjectE6EEEF50.expand_dict(self.features) # 2
    if self.system:
      result['system'] = self.system.to_dict() # 4
    return result
