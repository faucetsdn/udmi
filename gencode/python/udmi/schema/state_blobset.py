"""Generated class for state_blobset.json"""
from .state_blobset_blob import BlobBlobsetState


class BlobsetState:
  """Generated schema class"""

  def __init__(self):
    self.state_etag = None
    self.blobs = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BlobsetState()
    result.state_etag = source.get('state_etag')
    result.blobs = BlobBlobsetState.map_from(source.get('blobs'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BlobsetState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.state_etag:
      result['state_etag'] = self.state_etag # 5
    if self.blobs:
      result['blobs'] = BlobBlobsetState.expand_dict(self.blobs) # 2
    return result
