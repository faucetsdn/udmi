"""Generated class for model_localnet.json"""
from .model_localnet_family import FamilyLocalnetMetadata


class LocalnetMetadata:
  """Generated schema class"""

  def __init__(self):
    self.families = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LocalnetMetadata()
    result.families = FamilyLocalnetMetadata.map_from(source.get('families'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = LocalnetMetadata.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.families:
      result['families'] = FamilyLocalnetMetadata.expand_dict(self.families) # 2
    return result
