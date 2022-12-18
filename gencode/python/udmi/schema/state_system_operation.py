"""Generated class for state_system_operation.json"""


class StateSystemOperation:
  """Generated schema class"""

  def __init__(self):
    self.operational = None
    self.restart_count = None
    self.mode = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = StateSystemOperation()
    result.operational = source.get('operational')
    result.restart_count = source.get('restart_count')
    result.mode = source.get('mode')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = StateSystemOperation.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.operational:
      result['operational'] = self.operational # 5
    if self.restart_count:
      result['restart_count'] = self.restart_count # 5
    if self.mode:
      result['mode'] = self.mode # 5
    return result
