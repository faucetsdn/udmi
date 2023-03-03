"""Generated class for event_discovery_feature.json"""


class Object88890188:
  """Generated schema class"""

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object88890188()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object88890188.from_dict(source[key])
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
    self.stage = None
    self.features = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FeatureEnumerationEvent()
    result.stage = source.get('stage')
    result.features = Object88890188.from_dict(source.get('features'))
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
    if self.stage:
      result['stage'] = self.stage # 5
    if self.features:
      result['features'] = self.features.to_dict() # 4
    return result
