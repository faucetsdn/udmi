"""Generated class for event_mapping.json"""
from .common import Entry
from .event_mapping_entities import MappingEventEntities


class MappingEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.status = None
    self.entities = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MappingEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.status = Entry.from_dict(source.get('status'))
    result.entities = MappingEventEntity.map_from(source.get('entities'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MappingEvent.from_dict(source[key])
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
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.entities:
      result['entities'] = MappingEventEntity.expand_dict(self.entities) # 2
    return result
