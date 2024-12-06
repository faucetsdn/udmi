"""Generated class for depth.json"""


class Depth:
  """Generated schema class"""

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Depth()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Depth.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    return result
