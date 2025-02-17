"""Generated class for site_metadata.json"""


class SiteLocation:
  """Generated schema class"""

  def __init__(self):
    self.address = None
    self.lat = None
    self.long = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SiteLocation()
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
    if self.address:
      result['address'] = self.address # 5
    if self.lat:
      result['lat'] = self.lat # 5
    if self.long:
      result['long'] = self.long # 5
    return result


class SiteLinks:
  """Generated schema class"""

  def __init__(self):
    self.dashboard = None
    self.docs = None
    self.folder = None
    self.image = None
    self.repo = None
    self.sheet = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SiteLinks()
    result.dashboard = source.get('dashboard')
    result.docs = source.get('docs')
    result.folder = source.get('folder')
    result.image = source.get('image')
    result.repo = source.get('repo')
    result.sheet = source.get('sheet')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SiteLinks.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.dashboard:
      result['dashboard'] = self.dashboard # 5
    if self.docs:
      result['docs'] = self.docs # 5
    if self.folder:
      result['folder'] = self.folder # 5
    if self.image:
      result['image'] = self.image # 5
    if self.repo:
      result['repo'] = self.repo # 5
    if self.sheet:
      result['sheet'] = self.sheet # 5
    return result
from .dimension import Dimension
from .dimension import Dimension


class Object124EB07C:
  """Generated schema class"""

  def __init__(self):
    self.net_occupied_area = None
    self.gross_internal_area = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object124EB07C()
    result.net_occupied_area = Dimension.from_dict(source.get('net_occupied_area'))
    result.gross_internal_area = Dimension.from_dict(source.get('gross_internal_area'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object124EB07C.from_dict(source[key])
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
from .dimension import Dimension
from .dimension import Dimension


class Object7DEA8A3A:
  """Generated schema class"""

  def __init__(self):
    self.carbon_factor = None
    self.unit_cost = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object7DEA8A3A()
    result.carbon_factor = Dimension.from_dict(source.get('carbon_factor'))
    result.unit_cost = Dimension.from_dict(source.get('unit_cost'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object7DEA8A3A.from_dict(source[key])
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
from .dimension import Dimension
from .dimension import Dimension


class ObjectE4FBC3D1:
  """Generated schema class"""

  def __init__(self):
    self.carbon_factor = None
    self.unit_cost = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectE4FBC3D1()
    result.carbon_factor = Dimension.from_dict(source.get('carbon_factor'))
    result.unit_cost = Dimension.from_dict(source.get('unit_cost'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectE4FBC3D1.from_dict(source[key])
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
from .dimension import Dimension


class Object1917D71C:
  """Generated schema class"""

  def __init__(self):
    self.unit_cost = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object1917D71C()
    result.unit_cost = Dimension.from_dict(source.get('unit_cost'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object1917D71C.from_dict(source[key])
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


class Object100792F9:
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
    result = ObjectBB16A108()
    result.area = Object124EB07C.from_dict(source.get('area'))
    result.electricity = Object7DEA8A3A.from_dict(source.get('electricity'))
    result.gas = ObjectE4FBC3D1.from_dict(source.get('gas'))
    result.water = Object1917D71C.from_dict(source.get('water'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectBB16A108.from_dict(source[key])
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
    self.strict_warnings = None
    self.location = None
    self.links = None
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
    result.strict_warnings = source.get('strict_warnings')
    result.location = SiteLocation.from_dict(source.get('location'))
    result.links = SiteLinks.from_dict(source.get('links'))
    result.parameters = ObjectBB16A108.from_dict(source.get('parameters'))
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
    if self.strict_warnings:
      result['strict_warnings'] = self.strict_warnings # 5
    if self.location:
      result['location'] = self.location.to_dict() # 4
    if self.links:
      result['links'] = self.links.to_dict() # 4
    if self.parameters:
      result['parameters'] = self.parameters.to_dict() # 4
    return result
