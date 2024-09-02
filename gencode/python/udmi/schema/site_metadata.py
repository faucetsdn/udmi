"""Generated class for site_metadata.json"""


class Object936F7B6D:
  """Generated schema class"""

  def __init__(self):
    self.address = None
    self.lat = None
    self.long = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object936F7B6D()
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
      result[key] = Object936F7B6D.from_dict(source[key])
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


class ObjectAEC3F93C:
  """Generated schema class"""

  def __init__(self):
    self.docs = None
    self.folder = None
    self.image = None
    self.repo = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectAEC3F93C()
    result.docs = source.get('docs')
    result.folder = source.get('folder')
    result.image = source.get('image')
    result.repo = source.get('repo')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectAEC3F93C.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.docs:
      result['docs'] = self.docs # 5
    if self.folder:
      result['folder'] = self.folder # 5
    if self.image:
      result['image'] = self.image # 5
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


class Object50EC8CBF:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object50EC8CBF()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object50EC8CBF.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result


class Object20F38E2A:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object20F38E2A()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object20F38E2A.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result


class Object952CABE2:
  """Generated schema class"""

  def __init__(self):
    self.net_occupied_area = None
    self.gross_internal_area = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object37781B36()
    result.net_occupied_area = Object50EC8CBF.from_dict(source.get('net_occupied_area'))
    result.gross_internal_area = Object20F38E2A.from_dict(source.get('gross_internal_area'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object37781B36.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.net_occupied_area:
      result['net_occupied_area'] = self.net_occupied_area.to_dict() # 4
    if self.gross_internal_area:
      result['gross_internal_area'] = self.gross_internal_area.to_dict() # 4
    return result


class Object5C910670:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object5C910670()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object5C910670.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result


class Object49115991:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object49115991()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object49115991.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result


class Object1A1F27A1:
  """Generated schema class"""

  def __init__(self):
    self.carbon_factor = None
    self.unit_cost = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectC1B53851()
    result.carbon_factor = Object5C910670.from_dict(source.get('carbon_factor'))
    result.unit_cost = Object49115991.from_dict(source.get('unit_cost'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectC1B53851.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.carbon_factor:
      result['carbon_factor'] = self.carbon_factor.to_dict() # 4
    if self.unit_cost:
      result['unit_cost'] = self.unit_cost.to_dict() # 4
    return result


class Object2723B780:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object2723B780()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object2723B780.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result


class Object3612E6C4:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object3612E6C4()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object3612E6C4.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result


class ObjectEBF51015:
  """Generated schema class"""

  def __init__(self):
    self.carbon_factor = None
    self.unit_cost = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectE0D2C919()
    result.carbon_factor = Object2723B780.from_dict(source.get('carbon_factor'))
    result.unit_cost = Object3612E6C4.from_dict(source.get('unit_cost'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectE0D2C919.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.carbon_factor:
      result['carbon_factor'] = self.carbon_factor.to_dict() # 4
    if self.unit_cost:
      result['unit_cost'] = self.unit_cost.to_dict() # 4
    return result


class ObjectD5EDEA73:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectD5EDEA73()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectD5EDEA73.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result


class Object812CE457:
  """Generated schema class"""

  def __init__(self):
    self.unit_cost = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectD9A728BA()
    result.unit_cost = ObjectD5EDEA73.from_dict(source.get('unit_cost'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectD9A728BA.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.unit_cost:
      result['unit_cost'] = self.unit_cost.to_dict() # 4
    return result


class ObjectE4D4B0DB:
  """Generated schema class"""

  def __init__(self):
    self.area = None
    self.electricity = None
    self.gas = None
    self.water = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object8E5DCD5C()
    result.area = Object37781B36.from_dict(source.get('area'))
    result.electricity = ObjectC1B53851.from_dict(source.get('electricity'))
    result.gas = ObjectE0D2C919.from_dict(source.get('gas'))
    result.water = ObjectD9A728BA.from_dict(source.get('water'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object8E5DCD5C.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.area:
      result['area'] = self.area.to_dict() # 4
    if self.electricity:
      result['electricity'] = self.electricity.to_dict() # 4
    if self.gas:
      result['gas'] = self.gas.to_dict() # 4
    if self.water:
      result['water'] = self.water.to_dict() # 4
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
    self.links = None
    self.counts = None
    self.parameters = None

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
    result.location = Object936F7B6D.from_dict(source.get('location'))
    result.links = ObjectAEC3F93C.from_dict(source.get('links'))
    result.counts = Object837C4A52.from_dict(source.get('counts'))
    result.parameters = Object8E5DCD5C.from_dict(source.get('parameters'))
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
    if self.links:
      result['links'] = self.links.to_dict() # 4
    if self.counts:
      result['counts'] = self.counts.to_dict() # 4
    if self.parameters:
      result['parameters'] = self.parameters.to_dict() # 4
    return result
