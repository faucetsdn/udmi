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


class Object1CD88248:
  """Generated schema class"""

  def __init__(self):
    self.m2 = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object1CD88248()
    result.m2 = source.get('m2')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object1CD88248.from_dict(source[key])
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
    return result


class ObjectE4340764:
  """Generated schema class"""

  def __init__(self):
    self.folder = None
    self.repo = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectE4340764()
    result.folder = source.get('folder')
    result.repo = source.get('repo')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectE4340764.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.folder:
      result['folder'] = self.folder # 5
    if self.repo:
      result['repo'] = self.repo # 5
    return result


class Object837C4A52:
  """Generated schema class"""

  def __init__(self):
    self.modeled = None
    self.validated = None
    self.registered = None
    self.discovered = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object837C4A52()
    result.modeled = source.get('modeled')
    result.validated = source.get('validated')
    result.registered = source.get('registered')
    result.discovered = source.get('discovered')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object837C4A52.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.modeled:
      result['modeled'] = self.modeled # 5
    if self.validated:
      result['validated'] = self.validated # 5
    if self.registered:
      result['registered'] = self.registered # 5
    if self.discovered:
      result['discovered'] = self.discovered # 5
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
    self.source = None
    self.count = None

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
    result.area = Object1CD88248.from_dict(source.get('area'))
    result.source = ObjectE4340764.from_dict(source.get('source'))
    result.count = Object837C4A52.from_dict(source.get('count'))
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
    if self.source:
      result['source'] = self.source.to_dict() # 4
    if self.count:
      result['count'] = self.count.to_dict() # 4
    return result
