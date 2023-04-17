"""Generated class for configuration_pod.json"""
from .configuration_message import MessageConfiguration
from .configuration_message import MessageConfiguration


class PodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.state_flow = None
    self.target_flow = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PodConfiguration()
    result.state_flow = MessageConfiguration.from_dict(source.get('state_flow'))
    result.target_flow = MessageConfiguration.from_dict(source.get('target_flow'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PodConfiguration.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.state_flow:
      result['state_flow'] = self.state_flow.to_dict() # 4
    if self.target_flow:
      result['target_flow'] = self.target_flow.to_dict() # 4
    return result
