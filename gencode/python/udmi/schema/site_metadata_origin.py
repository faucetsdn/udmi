"""Generated class for site_metadata_origin.json"""


class SiteLocation:
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
    result = SiteLocation()
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
      result[key] = SiteLocation.from_dict(source[key])
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
