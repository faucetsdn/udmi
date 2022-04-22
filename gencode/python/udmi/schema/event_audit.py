"""Generated class for event_audit.json"""


class Object0C54FB6D:
  """Generated schema class"""

  def __init__(self):
    self.subFolder = None
    self.subType = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object0C54FB6D()
    result.subFolder = source.get('subFolder')
    result.subType = source.get('subType')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object0C54FB6D.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.subFolder:
      result['subFolder'] = self.subFolder # 5
    if self.subType:
      result['subType'] = self.subType # 5
    return result
from .common import Entry


class AuditEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.target = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = AuditEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.target = Object0C54FB6D.from_dict(source.get('target'))
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = AuditEvent.from_dict(source[key])
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
    if self.target:
      result['target'] = self.target.to_dict() # 4
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
