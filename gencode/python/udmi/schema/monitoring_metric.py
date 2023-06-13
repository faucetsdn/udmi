"""Generated class for monitoring_metric.json"""
from .event_system import SystemEvent


class Monitoringmetric:
  """Generated schema class"""

  def __init__(self):
    self.event_system = None
    self.envelope = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Monitoringmetric()
    result.event_system = SystemEvent.from_dict(source.get('event_system'))
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
    if self.event_system:
      result['event_system'] = self.event_system.to_dict() # 4
    if self.envelope:
      result['envelope'] = self.envelope # 5
    return result
