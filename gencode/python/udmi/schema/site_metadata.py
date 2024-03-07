"""Generated class for site_metadata.json"""


class ObjectB7199391:
  """Generated schema class"""

  def __init__(self):
    self.address = None
    self.lat = None
    self.lon = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectB7199391()
    result.address = source.get('address')
    result.lat = source.get('lat')
    result.lon = source.get('lon')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectB7199391.from_dict(source[key])
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
    if self.lon:
      result['lon'] = self.lon # 5
    return result


class Object96DA52A1:
  """Generated schema class"""

  def __init__(self):
    self.lat = None
    self.long = None
    self.alt = None
    self.orientation = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object96DA52A1()
    result.lat = source.get('lat')
    result.long = source.get('long')
    result.alt = source.get('alt')
    result.orientation = source.get('orientation')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object96DA52A1.from_dict(source[key])
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
    if self.alt:
      result['alt'] = self.alt # 5
    if self.orientation:
      result['orientation'] = self.orientation # 5
    return result


class SiteMetadata:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.id = None
    self.name = None
    self.location = None
    self.origin = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SiteMetadata()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.id = source.get('id')
    result.name = source.get('name')
    result.location = ObjectB7199391.from_dict(source.get('location'))
    result.origin = Object96DA52A1.from_dict(source.get('origin'))
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
    if self.id:
      result['id'] = self.id # 5
    if self.name:
      result['name'] = self.name # 5
    if self.location:
      result['location'] = self.location.to_dict() # 4
    if self.origin:
      result['origin'] = self.origin.to_dict() # 4
    return result
