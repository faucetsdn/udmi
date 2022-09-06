"""Generated class for event_validation.json"""
from .common import Entry


class PointsetSummary:
  """Generated schema class"""

  def __init__(self):
    self.missing = None
    self.extra = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointsetSummary()
    result.missing = source.get('missing')
    result.extra = source.get('extra')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointsetSummary.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.missing:
      result['missing'] = self.missing # 1
    if self.extra:
      result['extra'] = self.extra # 1
    return result


class ValidationEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.sub_folder = None
    self.sub_type = None
    self.status = None
    self.pointset = None
    self.errors = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ValidationEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.sub_folder = source.get('sub_folder')
    result.sub_type = source.get('sub_type')
    result.status = Entry.from_dict(source.get('status'))
    result.pointset = PointsetSummary.from_dict(source.get('pointset'))
    result.errors = Entry.array_from(source.get('errors'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ValidationEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    if self.version:
      result['version'] = self.version # 5
    if self.sub_folder:
      result['sub_folder'] = self.sub_folder # 5
    if self.sub_type:
      result['sub_type'] = self.sub_type # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    if self.pointset:
      result['pointset'] = self.pointset.to_dict() # 4
    if self.errors:
      result['errors'] = self.errors.to_dict() # 3
    return result
