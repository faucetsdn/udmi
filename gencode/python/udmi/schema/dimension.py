"""Generated class for dimension.json"""


class Dimension:
  """Generated schema class"""

  def __init__(self):
    self.value = None
    self.unit = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Dimension()
    result.value = source.get('value')
    result.unit = source.get('unit')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Dimension.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.value:
      result['value'] = self.value # 5
    if self.unit:
      result['unit'] = self.unit # 5
    return result
