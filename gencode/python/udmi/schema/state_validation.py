"""Generated class for state_validation.json"""
from .config_udmi import SetupUdmiConfig
from .entry import Entry
from .state_validation_feature import FeatureValidationState
from .state_validation_schema import SchemaValidationState


class ValidationSummary:
  """Generated schema class"""

  def __init__(self):
    self.correct_devices = None
    self.extra_devices = None
    self.missing_devices = None
    self.error_devices = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ValidationSummary()
    result.correct_devices = source.get('correct_devices')
    result.extra_devices = source.get('extra_devices')
    result.missing_devices = source.get('missing_devices')
    result.error_devices = source.get('error_devices')
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
    if self.correct_devices:
      result['correct_devices'] = self.correct_devices # 1
    if self.extra_devices:
      result['extra_devices'] = self.extra_devices # 1
    if self.missing_devices:
      result['missing_devices'] = self.missing_devices # 1
    if self.error_devices:
      result['error_devices'] = self.error_devices # 1
    return result
from .events_validation_device import DeviceValidationEvents


class ValidationState:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.cloud_version = None
    self.udmi_version = None
    self.last_updated = None
    self.start_time = None
    self.status = None
    self.features = None
    self.schemas = None
    self.summary = None
    self.devices = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ValidationState()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.cloud_version = SetupUdmiConfig.from_dict(source.get('cloud_version'))
    result.udmi_version = source.get('udmi_version')
    result.last_updated = source.get('last_updated')
    result.start_time = source.get('start_time')
    result.status = Entry.from_dict(source.get('status'))
    result.features = FeatureValidationState.map_from(source.get('features'))
    result.schemas = SchemaValidationState.map_from(source.get('schemas'))
    result.summary = ValidationSummary.from_dict(source.get('summary'))
    result.devices = DeviceValidationEvents.map_from(source.get('devices'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ValidationState.from_dict(source[key])
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
    if self.cloud_version:
      result['cloud_version'] = self.cloud_version.to_dict() # 4
    if self.udmi_version:
      result['udmi_version'] = self.udmi_version # 5
    if self.last_updated:
      result['last_updated'] = self.last_updated # 5
    if self.start_time:
      result['start_time'] = self.start_time # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.features:
      result['features'] = FeatureValidationState.expand_dict(self.features) # 2
    if self.schemas:
      result['schemas'] = SchemaValidationState.expand_dict(self.schemas) # 2
    if self.summary:
      result['summary'] = self.summary.to_dict() # 4
    if self.devices:
      result['devices'] = DeviceValidationEvents.expand_dict(self.devices) # 2
    return result
