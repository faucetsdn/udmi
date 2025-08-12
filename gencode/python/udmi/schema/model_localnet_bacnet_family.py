"""Generated class for model_localnet_bacnet_family.json"""


class ObjectDC14E5DB:
  """Generated schema class"""

  def __init__(self):
    self.name = None
    self.description = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectDC14E5DB()
    result.name = source.get('name')
    result.description = source.get('description')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectDC14E5DB.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.name:
      result['name'] = self.name # 5
    if self.description:
      result['description'] = self.description # 5
    return result


class BACnetFamilyLocalnetModel:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.bacnet_adjunct = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BACnetFamilyLocalnetModel()
    result.addr = source.get('addr')
    result.bacnet_adjunct = ObjectDC14E5DB.from_dict(source.get('bacnet_adjunct'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BACnetFamilyLocalnetModel.from_dict(source[key])
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
    if self.bacnet_adjunct:
      result['bacnet_adjunct'] = self.bacnet_adjunct.to_dict() # 4
    return result
