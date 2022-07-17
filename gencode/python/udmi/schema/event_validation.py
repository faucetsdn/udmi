"""Generated class for event_validation.json"""
from .common import Entry
from .event_validation_device import DeviceValidationEvent


class ValidationEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.last_updated = None
    self.site_id = None
    self.device_id = None
    self.subfolder = None
    self.subtype = None
    self.status = None
    self.extra_devices = None
    self.devices = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ValidationEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.last_updated = source.get('last_updated')
    result.site_id = source.get('site_id')
    result.device_id = source.get('device_id')
    result.subfolder = source.get('subfolder')
    result.subtype = source.get('subtype')
    result.status = Entry.from_dict(source.get('status'))
    result.extra_devices = source.get('extra_devices')
    result.devices = DeviceValidationEvent.map_from(source.get('devices'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ValidationEvent.from_dict(source[key])
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
    if self.last_updated:
      result['last_updated'] = self.last_updated # 5
    if self.site_id:
      result['site_id'] = self.site_id # 5
    if self.device_id:
      result['device_id'] = self.device_id # 5
    if self.subfolder:
      result['subfolder'] = self.subfolder # 5
    if self.subtype:
      result['subtype'] = self.subtype # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.extra_devices:
      result['extra_devices'] = self.extra_devices # 1
    if self.devices:
      result['devices'] = DeviceValidationEvent.expand_dict(self.devices) # 2
    return result
