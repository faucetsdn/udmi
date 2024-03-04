"""Generated class for discovery_feature.json"""


class FeatureDiscovery:
  """Generated schema class"""

  def __init__(self):
    self.stage = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FeatureDiscovery()
    result.stage = source.get('stage')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FeatureDiscovery.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.stage:
      result['stage'] = self.stage # 5
    return result
