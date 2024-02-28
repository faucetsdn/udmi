"""Generated class for model_localnet_family.json"""


class FamilyLocalnetModel:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.family = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyLocalnetModel()
    result.addr = source.get('addr')
    result.family = source.get('family')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FamilyLocalnetModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.addr:
      result['addr'] = self.addr # 5
    if self.family:
      result['family'] = self.family # 5
    return result
