"""Generated class for monitoring_metric.json"""
from .events_system import SystemEvents


class Monitoringmetric:
  """Generated schema class"""

  def __init__(self):
    self.system = None
    self.envelope = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Monitoringmetric()
    result.system = SystemEvents.from_dict(source.get('system'))
    result.envelope = source.get('envelope')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Monitoringmetric.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.system:
      result['system'] = self.system.to_dict() # 4
    if self.envelope:
      result['envelope'] = self.envelope # 5
    return result
