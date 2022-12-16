"""Generated class for config_blobset_blob.json"""


class BlobBlobsetConfig:
  """Generated schema class"""

  def __init__(self):
    self.phase = None
    self.url = None
    self.sha256 = None
    self.generation = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BlobBlobsetConfig()
    result.phase = source.get('phase')
    result.url = source.get('url')
    result.sha256 = source.get('sha256')
    result.generation = source.get('generation')
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
    if self.phase:
      result['phase'] = self.phase # 5
    if self.url:
      result['url'] = self.url # 5
    if self.sha256:
      result['sha256'] = self.sha256 # 5
    if self.generation:
      result['generation'] = self.generation # 5
    return result
