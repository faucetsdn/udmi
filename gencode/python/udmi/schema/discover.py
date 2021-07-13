"""Generated class for discover.json"""


class Discover:
  """Generated schema class"""



  def __init__(self):
    self.timestamp = None
    self.version = None
    self.protocol = None
    self.local_id = None
    self.points = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Discover()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.protocol = source.get('protocol')
    result.local_id = source.get('local_id')
    result.points = source.get('points')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Discover.from_dict(source[key])
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
    if self.protocol:
      result['protocol'] = self.protocol # 5
    if self.local_id:
      result['local_id'] = self.local_id # 5
    if self.points:
      result['points'] = self.points # 1
    return result
