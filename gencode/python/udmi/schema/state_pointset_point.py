"""Generated class for state_pointset_point.json"""
from .common import Entry


class PointPointsetState:
  """Generated schema class"""

  def __init__(self):
    self.units = None
    self.value_state = None
    self.status = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetState()
    result.units = source.get('units')
    result.value_state = source.get('value_state')
    result.status = Entry.from_dict(source.get('status'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointPointsetState.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.units:
      result['units'] = self.units # 5
    if self.value_state:
      result['value_state'] = self.value_state # 5
    if self.status:
      result['status'] = self.status.to_dict() # 4
    return result
