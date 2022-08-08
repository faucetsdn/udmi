"""Generated class for event_discovery_point.json"""
from .common import Entry
from .ancillary_properties import AncillaryProperties


class PointEnumerationEvent:
  """Generated schema class"""

  def __init__(self):
    self.possible_values = None
    self.units = None
    self.type = None
    self.ref = None
    self.writable = None
    self.description = None
    self.status = None
    self.ancillary = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointEnumerationEvent()
    result.possible_values = source.get('possible_values')
    result.units = source.get('units')
    result.type = source.get('type')
    result.ref = source.get('ref')
    result.writable = source.get('writable')
    result.description = source.get('description')
    result.status = Entry.from_dict(source.get('status'))
    result.ancillary = AncillaryProperties.from_dict(source.get('ancillary'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointEnumerationEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.possible_values:
      result['possible_values'] = self.possible_values # 1
    if self.units:
      result['units'] = self.units # 5
    if self.type:
      result['type'] = self.type # 5
    if self.ref:
      result['ref'] = self.ref # 5
    if self.writable:
      result['writable'] = self.writable # 5
    if self.description:
      result['description'] = self.description # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.ancillary:
      result['ancillary'] = self.ancillary.to_dict() # 4
    return result
