"""Generated class for event_pointset_point.json"""


class PointPointsetEvent:
  """Generated schema class"""

  def __init__(self):
    self.present_value = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetEvent()
    result.present_value = source.get('present_value')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointPointsetEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.present_value:
      result['present_value'] = self.present_value # 5
    return result
