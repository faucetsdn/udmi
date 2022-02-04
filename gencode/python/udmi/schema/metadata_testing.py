"""Generated class for metadata_testing.json"""


class TestingMetadata:
  """Generated schema class"""

  def __init__(self):
    self.targets = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = TestingMetadata()
    result.targets = source.get('targets')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = TestingMetadata.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.targets:
      result['targets'] = self.targets # 5
    return result
