"""Generated class for building_config_translation.json"""


class BuildingConfigTranslation:
  """Generated schema class"""

  def __init__(self):
    self.present_value = None
    self.units = None
    self.states = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BuildingConfigTranslation()
    result.present_value = source.get('present_value')
    result.units = source.get('units')
    result.states = source.get('states')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BuildingConfigTranslation.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.present_value:
      result['present_value'] = self.present_value # 5
    if self.units:
      result['units'] = self.units # 5
    if self.states:
      result['states'] = self.states # 5
    return result
