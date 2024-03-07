"""Generated class for common.json"""


class Common:
  """Generated schema class"""


  SystemMode = SystemMode
  IotProvider = IotProvider
  FeatureStage = FeatureStage
  BlobPhase = BlobPhase
  SystemBlobsets = SystemBlobsets

  def __init__(self):
    pass

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Common()
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Common.from_dict(source[key])
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
