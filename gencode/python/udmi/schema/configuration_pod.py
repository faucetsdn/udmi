"""Generated class for configuration_pod.json"""
from .configuration_message import MessageConfiguration


class PodConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.udmis_flow = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PodConfiguration()
    result.udmis_flow = MessageConfiguration.from_dict(source.get('udmis_flow'))
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
    if self.udmis_flow:
      result['udmis_flow'] = self.udmis_flow.to_dict() # 4
    return result
