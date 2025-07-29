"""Generated class for config_mapping_service.json"""


class MappingServiceConfig:
  """Generated schema class"""

  def __init__(self):
    self.extras_deletion_days = None
    self.devices_deletion_days = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MappingServiceConfig()
    result.extras_deletion_days = source.get('extras_deletion_days')
    result.devices_deletion_days = source.get('devices_deletion_days')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MappingServiceConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.extras_deletion_days:
      result['extras_deletion_days'] = self.extras_deletion_days # 5
    if self.devices_deletion_days:
      result['devices_deletion_days'] = self.devices_deletion_days # 5
    return result
