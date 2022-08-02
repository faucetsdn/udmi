"""Generated class for event_discovery.json"""
from .common import Entry
from .event_discovery_family import FamilyDiscoveryEvent
from .event_discovery_point import PointEnumerationEvent
from .ancillary_properties import AncillaryProperties
from .state_system_hardware import SystemHardware


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
    result.hardware = SystemHardware.from_dict(source.get('hardware'))
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
    self.scan_family = None
    self.scan_id = None
    self.families = None
    self.uniqs = None
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
    result.scan_family = source.get('scan_family')
    result.scan_id = source.get('scan_id')
    result.families = FamilyDiscoveryEvent.map_from(source.get('families'))
    result.uniqs = PointEnumerationEvent.map_from(source.get('uniqs'))
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
    if self.scan_family:
      result['scan_family'] = self.scan_family # 5
    if self.scan_id:
      result['scan_id'] = self.scan_id # 5
    if self.families:
      result['families'] = FamilyDiscoveryEvent.expand_dict(self.families) # 2
    if self.uniqs:
      result['uniqs'] = PointEnumerationEvent.expand_dict(self.uniqs) # 2
    if self.system:
      result['system'] = self.system.to_dict() # 4
    return result
