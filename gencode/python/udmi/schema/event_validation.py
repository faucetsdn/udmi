"""Generated class for event_validation.json"""
from .common import Entry


class ValidationSummary:
  """Generated schema class"""

  def __init__(self):
    self.extra_devices = None
    self.missing_devices = None
    self.pointset_devices = None
    self.base64_devices = None
    self.error_devices = None
    self.expected_devices = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ValidationSummary()
    result.extra_devices = source.get('extra_devices')
    result.missing_devices = source.get('missing_devices')
    result.pointset_devices = source.get('pointset_devices')
    result.base64_devices = source.get('base64_devices')
    result.error_devices = source.get('error_devices')
    result.expected_devices = source.get('expected_devices')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ValidationSummary.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.extra_devices:
      result['extra_devices'] = self.extra_devices # 1
    if self.missing_devices:
      result['missing_devices'] = self.missing_devices # 1
    if self.pointset_devices:
      result['pointset_devices'] = self.pointset_devices # 1
    if self.base64_devices:
      result['base64_devices'] = self.base64_devices # 1
    if self.error_devices:
      result['error_devices'] = self.error_devices # 1
    if self.expected_devices:
      result['expected_devices'] = self.expected_devices # 1
    return result
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
    self.summary = None
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
    result.summary = ValidationSummary.from_dict(source.get('summary'))
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
    if self.summary:
      result['summary'] = self.summary.to_dict() # 4
    if self.devices:
      result['devices'] = DeviceValidationEvent.expand_dict(self.devices) # 2
    return result
