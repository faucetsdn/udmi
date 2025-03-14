"""Generated class for common.json"""


class Common:
  """Generated schema class"""


  SystemMode = SystemMode
  ProtocolFamily = ProtocolFamily
  IotProvider = IotProvider
  ModelOperation = ModelOperation
  FeatureStage = FeatureStage
  BlobPhase = BlobPhase
  SystemBlobsets = SystemBlobsets

  def __init__(self):
    self.family = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Common()
    result.family = source.get('family')
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
    if self.family:
      result['family'] = self.family # 5
    return result
