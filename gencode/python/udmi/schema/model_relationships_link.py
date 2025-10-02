"""Generated class for model_relationships_link.json"""


class LinkRelationshipsModel:
  """Generated schema class"""

  def __init__(self):
    self.kind = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LinkRelationshipsModel()
    result.kind = source.get('kind')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = LinkRelationshipsModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.kind:
      result['kind'] = self.kind # 5
    return result
