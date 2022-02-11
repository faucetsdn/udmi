"""Generated class for state_system.json"""


class ObjectBF5DA4FA:
  """Generated schema class"""

  def __init__(self):
    self.version = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectBF5DA4FA()
    result.version = source.get('version')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectBF5DA4FA.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.version:
      result['version'] = self.version # 5
    return result
from .common import Entry


class SystemState:
  """Generated schema class"""

  def __init__(self):
    self.make_model = None
    self.serial_no = None
    self.firmware = None
    self.last_config = None
    self.operational = None
    self.statuses = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemState()
    result.make_model = source.get('make_model')
    result.serial_no = source.get('serial_no')
    result.firmware = ObjectBF5DA4FA.from_dict(source.get('firmware'))
    result.last_config = source.get('last_config')
    result.operational = source.get('operational')
    result.statuses = Entry.map_from(source.get('statuses'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.make_model:
      result['make_model'] = self.make_model # 5
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.firmware:
      result['firmware'] = self.firmware.to_dict() # 4
    if self.last_config:
      result['last_config'] = self.last_config # 5
    if self.operational:
      result['operational'] = self.operational # 5
    if self.statuses:
      result['statuses'] = Entry.expand_dict(self.statuses) # 2
    return result
