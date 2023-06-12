"""Generated class for monitoring.json"""
from .monitoring_metric import Monitoringmetric


class Monitoring:
  """Generated schema class"""

  def __init__(self):
    self.metric = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Monitoring()
    result.metric = Monitoringmetric.from_dict(source.get('metric'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Monitoring.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.metric:
      result['metric'] = self.metric.to_dict() # 4
    return result
