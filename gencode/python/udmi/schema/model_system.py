"""Generated class for model_system.json"""


class Object11D8FD30:
  """Generated schema class"""

  def __init__(self):
    self.x = None
    self.y = None
    self.z = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object11D8FD30()
    result.x = source.get('x')
    result.y = source.get('y')
    result.z = source.get('z')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object11D8FD30.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.x:
      result['x'] = self.x # 5
    if self.y:
      result['y'] = self.y # 5
    if self.z:
      result['z'] = self.z # 5
    return result


class Object9CBC741A:
  """Generated schema class"""

  def __init__(self):
    self.site = None
    self.section = None
    self.position = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object327C415D()
    result.site = source.get('site')
    result.section = source.get('section')
    result.position = Object11D8FD30.from_dict(source.get('position'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object327C415D.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.site:
      result['site'] = self.site # 5
    if self.section:
      result['section'] = self.section # 5
    if self.position:
      result['position'] = self.position.to_dict() # 4
    return result
from .model_system_hardware import SystemHardware


class Object0EA01FC6:
  """Generated schema class"""

  def __init__(self):
    self.guid = None
    self.site = None
    self.name = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object0EA01FC6()
    result.guid = source.get('guid')
    result.site = source.get('site')
    result.name = source.get('name')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object0EA01FC6.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.guid:
      result['guid'] = self.guid # 5
    if self.site:
      result['site'] = self.site # 5
    if self.name:
      result['name'] = self.name # 5
    return result


class ObjectA51C8B52:
  """Generated schema class"""

  def __init__(self):
    self.asset = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object8FD736D9()
    result.asset = Object0EA01FC6.from_dict(source.get('asset'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object8FD736D9.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.asset:
      result['asset'] = self.asset.to_dict() # 4
    return result


class Object734A44BA:
  """Generated schema class"""

  def __init__(self):
    self.suffix = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object734A44BA()
    result.suffix = source.get('suffix')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object734A44BA.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.suffix:
      result['suffix'] = self.suffix # 5
    return result


class SystemModel:
  """Generated schema class"""

  def __init__(self):
    self.location = None
    self.hardware = None
    self.software = None
    self.serial_no = None
    self.physical_tag = None
    self.aux = None
    self.min_loglevel = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemModel()
    result.location = Object327C415D.from_dict(source.get('location'))
    result.hardware = SystemHardware.from_dict(source.get('hardware'))
    result.software = source.get('software')
    result.serial_no = source.get('serial_no')
    result.physical_tag = Object8FD736D9.from_dict(source.get('physical_tag'))
    result.aux = Object734A44BA.from_dict(source.get('aux'))
    result.min_loglevel = source.get('min_loglevel')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.location:
      result['location'] = self.location.to_dict() # 4
    if self.hardware:
      result['hardware'] = self.hardware.to_dict() # 4
    if self.software:
      result['software'] = self.software # 1
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.physical_tag:
      result['physical_tag'] = self.physical_tag.to_dict() # 4
    if self.aux:
      result['aux'] = self.aux.to_dict() # 4
    if self.min_loglevel:
      result['min_loglevel'] = self.min_loglevel # 5
    return result
