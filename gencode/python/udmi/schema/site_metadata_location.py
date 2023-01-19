"""Generated class for site_metadata_location.json"""


class SiteMetadataLocation:
  """Generated schema class"""

  def __init__(self):
    self.address = None
    self.lat = None
    self.long = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SiteMetadataLocation()
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
      result[key] = SiteMetadataLocation.from_dict(source[key])
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
