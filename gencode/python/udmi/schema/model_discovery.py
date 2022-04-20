"""Generated class for model_discovery.json"""


class DiscoveryModel:
  """Generated schema class"""

  def __init__(self):
    self.discovery = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = DiscoveryModel()
    result.discovery = source.get('discovery')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = DiscoveryModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.discovery:
      result['discovery'] = self.discovery # 5
    return result
