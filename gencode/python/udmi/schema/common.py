"""Generated class for common.json"""


class Entry:
  """Generated schema class"""

  def __init__(self):
    self.message = None
    self.detail = None
    self.category = None
    self.timestamp = None
    self.level = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Entry()
    result.message = source.get('message')
    result.detail = source.get('detail')
    result.category = source.get('category')
    result.timestamp = source.get('timestamp')
    result.level = source.get('level')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Entry.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.message:
      result['message'] = self.message # 5
    if self.detail:
      result['detail'] = self.detail # 5
    if self.category:
      result['category'] = self.category # 5
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    if self.level:
      result['level'] = self.level # 5
    return result


class Common:
  """Generated schema class"""


  SystemMode = SystemMode
  SystemBlobsets = SystemBlobsets
  Entry = Entry
  EndpointConfiguration = EndpointConfiguration

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Common()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Common.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    return result
