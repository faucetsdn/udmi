"""Generated class for site_metadata.json"""


class Object7BC6817A:
  """Generated schema class"""

  def __init__(self):
    self.address = None
    self.lat = None
    self.long = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object7BC6817A()
    result.address = source.get('address')
    result.lat = source.get('lat')
    result.long = source.get('long')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object7BC6817A.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.address:
      result['address'] = self.address # 5
    if self.lat:
      result['lat'] = self.lat # 5
    if self.long:
      result['long'] = self.long # 5
    return result


class Object482237BC:
  """Generated schema class"""

  def __init__(self):
    self.m2 = None
    self.sqf = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object482237BC()
    result.m2 = source.get('m2')
    result.sqf = source.get('sqf')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object482237BC.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.m2:
      result['m2'] = self.m2 # 5
    if self.sqf:
      result['sqf'] = self.sqf # 5
    return result


class SiteMetadata:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.site = None
    self.name = None
    self.tags = None
    self.location = None
    self.area = None
    self.site_folder = None
    self.source_repo = None
    self.device_count = None
    self.validated_count = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SiteMetadata()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.site = source.get('site')
    result.name = source.get('name')
    result.tags = source.get('tags')
    result.location = Object7BC6817A.from_dict(source.get('location'))
    result.area = Object482237BC.from_dict(source.get('area'))
    result.site_folder = source.get('site_folder')
    result.source_repo = source.get('source_repo')
    result.device_count = source.get('device_count')
    result.validated_count = source.get('validated_count')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SiteMetadata.from_dict(source[key])
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
    if self.site:
      result['site'] = self.site # 5
    if self.name:
      result['name'] = self.name # 5
    if self.tags:
      result['tags'] = self.tags # 1
    if self.location:
      result['location'] = self.location.to_dict() # 4
    if self.area:
      result['area'] = self.area.to_dict() # 4
    if self.site_folder:
      result['site_folder'] = self.site_folder # 5
    if self.source_repo:
      result['source_repo'] = self.source_repo # 5
    if self.device_count:
      result['device_count'] = self.device_count # 5
    if self.validated_count:
      result['validated_count'] = self.validated_count # 5
    return result
