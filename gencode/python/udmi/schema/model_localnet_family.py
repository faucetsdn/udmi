"""Generated class for model_localnet_family.json"""


class FamilyLocalnetModel:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.network = None
    self.adjunct = None
    self.shadow_id = None
    self.parent_id = None
    self.family = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyLocalnetModel()
    result.addr = source.get('addr')
    result.network = source.get('network')
    result.adjunct = source.get('adjunct')
    result.shadow_id = source.get('shadow_id')
    result.parent_id = source.get('parent_id')
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
    if self.network:
      result['network'] = self.network # 5
    if self.adjunct:
      result['adjunct'] = self.adjunct # 1
    if self.shadow_id:
      result['shadow_id'] = self.shadow_id # 5
    if self.parent_id:
      result['parent_id'] = self.parent_id # 5
    if self.family:
      result['family'] = self.family # 5
    return result
