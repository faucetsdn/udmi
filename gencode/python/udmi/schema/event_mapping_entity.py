"""Generated class for event_mapping_entity.json"""
from .virtual_links import VirtualEquipmentLinks
from .equipment_translation import BuildingConfig


class MappingEventEntity:
  """Generated schema class"""

  def __init__(self):
    self.code = None
    self.type = None
    self.cloud_device_id = None
    self.connections = None
    self.links = None
    self.translation = None
    self.missing_telemetry_fields = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MappingEventEntity()
    result.code = source.get('code')
    result.type = source.get('type')
    result.cloud_device_id = source.get('cloud_device_id')
    result.connections = source.get('connections')
    result.links = Object96DF83D1.map_from(source.get('links'))
    result.translation = BuildingTranslation.map_from(source.get('translation'))
    result.missing_telemetry_fields = source.get('missing_telemetry_fields')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MappingEventEntity.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.code:
      result['code'] = self.code # 5
    if self.type:
      result['type'] = self.type # 5
    if self.cloud_device_id:
      result['cloud_device_id'] = self.cloud_device_id # 5
    if self.connections:
      result['connections'] = self.connections # 1
    if self.links:
      result['links'] = Object96DF83D1.expand_dict(self.links) # 2
    if self.translation:
      result['translation'] = BuildingTranslation.expand_dict(self.translation) # 2
    if self.missing_telemetry_fields:
      result['missing_telemetry_fields'] = self.missing_telemetry_fields # 1
    return result
