"""Generated class for events_pointset.json"""
from .events_pointset_point import PointPointsetEvents


class PointsetEvents:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.upgraded_from = None
    self.partial_update = None
    self.points = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointsetEvents()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.upgraded_from = source.get('upgraded_from')
    result.partial_update = source.get('partial_update')
    result.points = PointPointsetEvents.map_from(source.get('points'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointsetEvents.from_dict(source[key])
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
    if self.upgraded_from:
      result['upgraded_from'] = self.upgraded_from # 5
    if self.partial_update:
      result['partial_update'] = self.partial_update # 5
    if self.points:
      result['points'] = PointPointsetEvents.expand_dict(self.points) # 2
    return result
