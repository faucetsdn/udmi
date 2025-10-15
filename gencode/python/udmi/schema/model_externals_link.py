"""Generated class for model_externals_link.json"""


class LinkExternalsModel:
  """Generated schema class"""

  def __init__(self):
    self.entity_id = None
    self.entity_type = None
    self.etag = None
    self.description = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LinkExternalsModel()
    result.entity_id = source.get('entity_id')
    result.entity_type = source.get('entity_type')
    result.etag = source.get('etag')
    result.description = source.get('description')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = LinkExternalsModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.entity_id:
      result['entity_id'] = self.entity_id # 5
    if self.entity_type:
      result['entity_type'] = self.entity_type # 5
    if self.etag:
      result['etag'] = self.etag # 5
    if self.description:
      result['description'] = self.description # 5
    return result
