"""Generated class for config_blobset_blob.json"""


class BlobBlobsetConfig:
  """Generated schema class"""

  def __init__(self):
    self.stage = None
    self.target = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BlobBlobsetConfig()
    result.stage = source.get('stage')
    result.target = source.get('target')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BlobBlobsetConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.stage:
      result['stage'] = self.stage # 5
    if self.target:
      result['target'] = self.target # 5
    return result
