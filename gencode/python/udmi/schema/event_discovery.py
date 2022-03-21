"""Generated class for event_discovery.json"""
from .common import Entry
from .event_discovery_family import FamilyDiscoveryEvent
from .event_discovery_point import PointEnumerationEvent
from .event_discovery_blob import BlobEnumerationEvent


class DiscoveryEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.generation = None
    self.status = None
    self.families = None
    self.points = None
    self.blobs = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.generation = source.get('generation')
    result.status = Entry.from_dict(source.get('status'))
    result.families = FamilyDiscoveryEvent.map_from(source.get('families'))
    result.points = PointEnumerationEvent.map_from(source.get('points'))
    result.blobs = BlobEnumerationEvent.map_from(source.get('blobs'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveryEvent.from_dict(source[key])
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
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.families:
      result['families'] = FamilyDiscoveryEvent.expand_dict(self.families) # 2
    if self.points:
      result['points'] = PointEnumerationEvent.expand_dict(self.points) # 2
    if self.blobs:
      result['blobs'] = BlobEnumerationEvent.expand_dict(self.blobs) # 2
    return result
