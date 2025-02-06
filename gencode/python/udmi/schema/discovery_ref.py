"""Generated class for discovery_ref.json"""
from .entry import Entry
from .ancillary_properties import AncillaryProperties
from .discovery_family import FamilyDiscovery
from .discovery_family import FamilyDiscovery


class Object9F2E28DD:
  """Generated schema class"""

  def __init__(self):
    self.point = None
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object9F2E28DD()
    result.point = FamilyDiscovery.from_dict(source.get('point'))
    result.families = FamilyDiscovery.map_from(source.get('families'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object9F2E28DD.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.point:
      result['point'] = self.point.to_dict() # 4
    if self.families:
      result['families'] = FamilyDiscovery.expand_dict(self.families) # 2
    return result


class RefDiscovery:
  """Generated schema class"""

  def __init__(self):
    self.point = None
    self.name = None
    self.possible_values = None
    self.units = None
    self.ref = None
    self.type = None
    self.writable = None
    self.description = None
    self.status = None
    self.ancillary = None
    self.adjunct = None
    self.structure = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = RefDiscovery()
    result.point = source.get('point')
    result.name = source.get('name')
    result.possible_values = source.get('possible_values')
    result.units = source.get('units')
    result.ref = source.get('ref')
    result.type = source.get('type')
    result.writable = source.get('writable')
    result.description = source.get('description')
    result.status = Entry.from_dict(source.get('status'))
    result.ancillary = AncillaryProperties.from_dict(source.get('ancillary'))
    result.adjunct = source.get('adjunct')
    result.structure = Object9F2E28DD.from_dict(source.get('structure'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = RefDiscovery.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.point:
      result['point'] = self.point # 5
    if self.name:
      result['name'] = self.name # 5
    if self.possible_values:
      result['possible_values'] = self.possible_values # 1
    if self.units:
      result['units'] = self.units # 5
    if self.ref:
      result['ref'] = self.ref # 5
    if self.type:
      result['type'] = self.type # 5
    if self.writable:
      result['writable'] = self.writable # 5
    if self.description:
      result['description'] = self.description # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.ancillary:
      result['ancillary'] = self.ancillary.to_dict() # 4
    if self.adjunct:
      result['adjunct'] = self.adjunct # 1
    if self.structure:
      result['structure'] = self.structure.to_dict() # 4
    return result
