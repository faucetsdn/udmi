"""Generated class for envelope.json"""


class Envelope:
  """Generated schema class"""

  def __init__(self):
    self.deviceId = None
    self.deviceNumId = None
    self.deviceRegistryId = None
    self.deviceRegistryLocation = None
    self.projectId = None
    self.payload = None
    self.subFolder = None
    self.subType = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Envelope()
    result.deviceId = source.get('deviceId')
    result.deviceNumId = source.get('deviceNumId')
    result.deviceRegistryId = source.get('deviceRegistryId')
    result.deviceRegistryLocation = source.get('deviceRegistryLocation')
    result.projectId = source.get('projectId')
    result.payload = source.get('payload')
    result.subFolder = source.get('subFolder')
    result.subType = source.get('subType')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Envelope.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.deviceId:
      result['deviceId'] = self.deviceId # 5
    if self.deviceNumId:
      result['deviceNumId'] = self.deviceNumId # 5
    if self.deviceRegistryId:
      result['deviceRegistryId'] = self.deviceRegistryId # 5
    if self.deviceRegistryLocation:
      result['deviceRegistryLocation'] = self.deviceRegistryLocation # 5
    if self.projectId:
      result['projectId'] = self.projectId # 5
    if self.payload:
      result['payload'] = self.payload # 5
    if self.subFolder:
      result['subFolder'] = self.subFolder # 5
    if self.subType:
      result['subType'] = self.subType # 5
    return result
