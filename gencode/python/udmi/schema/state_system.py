"""Generated class for state_system.json"""
from .common import Entry


class SystemState:
  """Generated schema class"""

  def __init__(self):
    self.last_config = None
    self.operational = None
    self.serial_no = None
    self.hardware = None
    self.software = None
    self.params = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemState()
    result.last_config = source.get('last_config')
    result.operational = source.get('operational')
    result.serial_no = source.get('serial_no')
    result.hardware = source.get('hardware')
    result.software = source.get('software')
    result.params = source.get('params')
    result.status = Entry.from_dict(source.get('status'))
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
    if self.last_config:
      result['last_config'] = self.last_config # 5
    if self.operational:
      result['operational'] = self.operational # 5
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.hardware:
      result['hardware'] = self.hardware # 1
    if self.software:
      result['software'] = self.software # 1
    if self.params:
      result['params'] = self.params # 1
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
