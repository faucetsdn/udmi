"""Generated class for model_system.json"""


class Object2CCE4FC0:
  """Generated schema class"""

  def __init__(self):
    self.x = None
    self.y = None
    self.z = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object2CCE4FC0()
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
      result[key] = Object2CCE4FC0.from_dict(source[key])
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


class Object683EF6A1:
  """Generated schema class"""

  def __init__(self):
    self.site = None
    self.section = None
    self.position = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object683EF6A1()
    result.site = source.get('site')
    result.section = source.get('section')
    result.position = Object2CCE4FC0.from_dict(source.get('position'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object683EF6A1.from_dict(source[key])
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


class ObjectA9E984A3:
  """Generated schema class"""

  def __init__(self):
    self.guid = None
    self.site = None
    self.name = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectA9E984A3()
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
      result[key] = ObjectA9E984A3.from_dict(source[key])
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


class Object45E20BB3:
  """Generated schema class"""

  def __init__(self):
    self.asset = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object45E20BB3()
    result.asset = ObjectA9E984A3.from_dict(source.get('asset'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object45E20BB3.from_dict(source[key])
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


class ObjectCA9644FB:
  """Generated schema class"""

  def __init__(self):
    self.suffix = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectCA9644FB()
    result.suffix = source.get('suffix')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectCA9644FB.from_dict(source[key])
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

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemModel()
    result.location = Object683EF6A1.from_dict(source.get('location'))
    result.physical_tag = Object45E20BB3.from_dict(source.get('physical_tag'))
    result.aux = ObjectCA9644FB.from_dict(source.get('aux'))
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
    return result
