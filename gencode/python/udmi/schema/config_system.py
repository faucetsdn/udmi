"""Generated class for config_system.json"""


class SystemConfig:
  """Generated schema class"""

  def __init__(self):
    self.min_loglevel = None
    self.metrics_rate_sec = None
    self.mode = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemConfig()
    result.min_loglevel = source.get('min_loglevel')
    result.metrics_rate_sec = source.get('metrics_rate_sec')
    result.mode = source.get('mode')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.min_loglevel:
      result['min_loglevel'] = self.min_loglevel # 5
    if self.metrics_rate_sec:
      result['metrics_rate_sec'] = self.metrics_rate_sec # 5
    if self.mode:
      result['mode'] = self.mode # 5
    return result
