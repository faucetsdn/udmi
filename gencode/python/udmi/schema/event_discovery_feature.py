"""Generated class for event_discovery_feature.json"""


class Object844AA406:
  """Generated schema class"""

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object844AA406()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object844AA406.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    return result


class FeatureEnumerationEvent:
  """Generated schema class"""

  def __init__(self):
    self.networks = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FeatureEnumerationEvent()
    result.networks = Object844AA406.from_dict(source.get('networks'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FeatureEnumerationEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.networks:
      result['networks'] = self.networks.to_dict() # 4
    return result
