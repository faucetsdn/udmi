"""Generated class for model_system.json"""


class Object550AB2BD:
  """Generated schema class"""

  def __init__(self):
    self.x = None
    self.y = None
    self.z = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object550AB2BD()
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
      result[key] = Object550AB2BD.from_dict(source[key])
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


class Object7706BE2B:
  """Generated schema class"""

  def __init__(self):
    self.lat = None
    self.long = None
    self.alt_m = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object7706BE2B()
    result.lat = source.get('lat')
    result.long = source.get('long')
    result.alt_m = source.get('alt_m')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object7706BE2B.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.lat:
      result['lat'] = self.lat # 5
    if self.long:
      result['long'] = self.long # 5
    if self.alt_m:
      result['alt_m'] = self.alt_m # 5
    return result


class Object1F5E34A2:
  """Generated schema class"""

  def __init__(self):
    self.site = None
    self.panel = None
    self.section = None
    self.room = None
    self.floor = None
    self.floor_seq = None
    self.position = None
    self.coordinates = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object124F4486()
    result.site = source.get('site')
    result.panel = source.get('panel')
    result.section = source.get('section')
    result.room = source.get('room')
    result.floor = source.get('floor')
    result.floor_seq = source.get('floor_seq')
    result.position = Object550AB2BD.from_dict(source.get('position'))
    result.coordinates = Object7706BE2B.from_dict(source.get('coordinates'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object124F4486.from_dict(source[key])
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
    if self.panel:
      result['panel'] = self.panel # 5
    if self.section:
      result['section'] = self.section # 5
    if self.room:
      result['room'] = self.room # 5
    if self.floor:
      result['floor'] = self.floor # 5
    if self.floor_seq:
      result['floor_seq'] = self.floor_seq # 5
    if self.position:
      result['position'] = self.position.to_dict() # 4
    if self.coordinates:
      result['coordinates'] = self.coordinates.to_dict() # 4
    return result
from .model_system_hardware import SystemHardware


class Object89CC9E24:
  """Generated schema class"""

  def __init__(self):
    self.guid = None
    self.site = None
    self.name = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object89CC9E24()
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
      result[key] = Object89CC9E24.from_dict(source[key])
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


class Object9C9C9D9B:
  """Generated schema class"""

  def __init__(self):
    self.asset = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object3960F11E()
    result.asset = Object89CC9E24.from_dict(source.get('asset'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object3960F11E.from_dict(source[key])
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


class SystemModel:
  """Generated schema class"""

  def __init__(self):
    self.name = None
    self.description = None
    self.device_version = None
    self.zone = None
    self.node_type = None
    self.tags = None
    self.location = None
    self.serial_no = None
    self.hardware = None
    self.software = None
    self.physical_tag = None
    self.adjunct = None
    self.min_loglevel = None
    self.metrics_rate_sec = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemModel()
    result.name = source.get('name')
    result.description = source.get('description')
    result.device_version = source.get('device_version')
    result.zone = source.get('zone')
    result.node_type = source.get('node_type')
    result.tags = source.get('tags')
    result.location = Object124F4486.from_dict(source.get('location'))
    result.serial_no = source.get('serial_no')
    result.hardware = SystemHardware.from_dict(source.get('hardware'))
    result.software = source.get('software')
    result.physical_tag = Object3960F11E.from_dict(source.get('physical_tag'))
    result.adjunct = source.get('adjunct')
    result.min_loglevel = source.get('min_loglevel')
    result.metrics_rate_sec = source.get('metrics_rate_sec')
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
    if self.name:
      result['name'] = self.name # 5
    if self.description:
      result['description'] = self.description # 5
    if self.device_version:
      result['device_version'] = self.device_version # 5
    if self.zone:
      result['zone'] = self.zone # 5
    if self.node_type:
      result['node_type'] = self.node_type # 5
    if self.tags:
      result['tags'] = self.tags # 1
    if self.location:
      result['location'] = self.location.to_dict() # 4
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.hardware:
      result['hardware'] = self.hardware.to_dict() # 4
    if self.software:
      result['software'] = self.software # 1
    if self.physical_tag:
      result['physical_tag'] = self.physical_tag.to_dict() # 4
    if self.adjunct:
      result['adjunct'] = self.adjunct # 1
    if self.min_loglevel:
      result['min_loglevel'] = self.min_loglevel # 5
    if self.metrics_rate_sec:
      result['metrics_rate_sec'] = self.metrics_rate_sec # 5
    return result
