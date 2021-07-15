"""Generated class for metadata_localnet.json"""


class LocalnetMetadata:
  """Generated schema class"""

  def __init__(self):
    self.subsystem = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LocalnetMetadata()
    result.subsystem = source.get('subsystem')
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
    if self.subsystem:
      result['subsystem'] = self.subsystem # 1
    return result
