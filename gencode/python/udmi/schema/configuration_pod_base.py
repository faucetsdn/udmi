"""Generated class for configuration_pod_base.json"""


class BasePodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.udmi_prefix = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = BasePodConfiguration()
    result.udmi_prefix = source.get('udmi_prefix')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = BasePodConfiguration.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.udmi_prefix:
      result['udmi_prefix'] = self.udmi_prefix # 5
    return result
