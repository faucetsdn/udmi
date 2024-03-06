"""Generated class for query_cloud.json"""


class CloudQuery:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.generation = None
    self.depth = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CloudQuery()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.generation = source.get('generation')
    result.depth = source.get('depth')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = CloudQuery.from_dict(source[key])
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
    if self.generation:
      result['generation'] = self.generation # 5
    if self.depth:
      result['depth'] = self.depth # 5
    return result
