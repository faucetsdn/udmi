"""Generated class for model_localnet.json"""


class Object9E9EA944:
  """Generated schema class"""

  def __init__(self):
    self.id = None
    self.family = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object9E9EA944()
    result.id = source.get('id')
    result.family = source.get('family')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object9E9EA944.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.id:
      result['id'] = self.id # 5
    if self.family:
      result['family'] = self.family # 5
    return result
from .model_localnet_family import FamilyLocalnetModel


class LocalnetModel:
  """Generated schema class"""

  def __init__(self):
    self.parent = None
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LocalnetModel()
    result.parent = Object9E9EA944.from_dict(source.get('parent'))
    result.families = FamilyLocalnetModel.map_from(source.get('families'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = LocalnetModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.parent:
      result['parent'] = self.parent.to_dict() # 4
    if self.families:
      result['families'] = FamilyLocalnetModel.expand_dict(self.families) # 2
    return result
