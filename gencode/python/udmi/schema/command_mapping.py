"""Generated class for command_mapping.json"""


class MappingCommand:
  """Generated schema class"""

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MappingCommand()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MappingCommand.from_dict(source[key])
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
