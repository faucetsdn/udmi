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


class SiteMetadata:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.site_id = None
    self.name = None
    self.location = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SiteMetadata()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.site_id = source.get('site_id')
    result.name = source.get('name')
    result.location = Object7BC6817A.from_dict(source.get('location'))
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
    if self.site_id:
      result['site_id'] = self.site_id # 5
    if self.name:
      result['name'] = self.name # 5
    if self.location:
      result['location'] = self.location.to_dict() # 4
    return result
