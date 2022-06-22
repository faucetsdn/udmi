"""Generated class for model_system.json"""


class ObjectCC5CED9E:
  """Generated schema class"""

  def __init__(self):
    self.x = None
    self.y = None
    self.z = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectCC5CED9E()
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
      result[key] = ObjectCC5CED9E.from_dict(source[key])
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


class ObjectF3010191:
  """Generated schema class"""

  def __init__(self):
    self.site = None
    self.section = None
    self.position = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object1F7AA959()
    result.site = source.get('site')
    result.section = source.get('section')
    result.position = ObjectCC5CED9E.from_dict(source.get('position'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object1F7AA959.from_dict(source[key])
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
    if self.section:
      result['section'] = self.section # 5
    if self.position:
      result['position'] = self.position.to_dict() # 4
    return result


class Object40669492:
  """Generated schema class"""

  def __init__(self):
    self.guid = None
    self.site = None
    self.name = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object40669492()
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
      result[key] = Object40669492.from_dict(source[key])
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


class Object3ACE9067:
  """Generated schema class"""

  def __init__(self):
    self.asset = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object6FD34535()
    result.asset = Object40669492.from_dict(source.get('asset'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object6FD34535.from_dict(source[key])
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


class ObjectDEF80C95:
  """Generated schema class"""

  def __init__(self):
    self.suffix = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectDEF80C95()
    result.suffix = source.get('suffix')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectDEF80C95.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.suffix:
      result['suffix'] = self.suffix # 5
    return result


class SystemModel:
  """Generated schema class"""

  def __init__(self):
    self.location = None
    self.physical_tag = None
    self.aux = None
    self.min_loglevel = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemModel()
    result.location = Object1F7AA959.from_dict(source.get('location'))
    result.physical_tag = Object6FD34535.from_dict(source.get('physical_tag'))
    result.aux = ObjectDEF80C95.from_dict(source.get('aux'))
    result.min_loglevel = source.get('min_loglevel')
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
    if self.location:
      result['location'] = self.location.to_dict() # 4
    if self.physical_tag:
      result['physical_tag'] = self.physical_tag.to_dict() # 4
    if self.aux:
      result['aux'] = self.aux.to_dict() # 4
    if self.min_loglevel:
      result['min_loglevel'] = self.min_loglevel # 5
    return result
