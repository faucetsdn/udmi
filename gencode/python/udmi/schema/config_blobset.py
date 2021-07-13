"""Generated class for config_blobset.json"""
from .config_blobset_blob import BlobBlobsetConfig


class BlobsetConfig:
  """Generated schema class"""

  def __init__(self):
    self.blobs = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BlobsetConfig()
    result.blobs = BlobBlobsetConfig.map_from(source.get('blobs'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BlobsetConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.blobs:
      result['blobs'] = BlobBlobsetConfig.expand_dict(self.blobs) # 2
    return result
