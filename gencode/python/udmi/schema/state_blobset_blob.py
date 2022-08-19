"""Generated class for state_blobset_blob.json"""
from .common import Entry


class BlobBlobsetState:
  """Generated schema class"""

  def __init__(self):
    self.phase = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BlobBlobsetState()
    result.phase = source.get('phase')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BlobBlobsetState.from_dict(source[key])
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
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
