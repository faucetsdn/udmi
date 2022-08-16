"""Generated class for pubber_options.json"""


class PubberOptions:
  """Generated schema class"""

  def __init__(self):
    self.noHardware = None
    self.noConfigAck = None
    self.messageTrace = None
    self.extraPoint = None
    self.missingPoint = None
    self.extraField = None
    self.redirectRegistry = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PubberOptions()
    result.noHardware = source.get('noHardware')
    result.noConfigAck = source.get('noConfigAck')
    result.messageTrace = source.get('messageTrace')
    result.extraPoint = source.get('extraPoint')
    result.missingPoint = source.get('missingPoint')
    result.extraField = source.get('extraField')
    result.redirectRegistry = source.get('redirectRegistry')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PubberOptions.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.noHardware:
      result['noHardware'] = self.noHardware # 5
    if self.noConfigAck:
      result['noConfigAck'] = self.noConfigAck # 5
    if self.messageTrace:
      result['messageTrace'] = self.messageTrace # 5
    if self.extraPoint:
      result['extraPoint'] = self.extraPoint # 5
    if self.missingPoint:
      result['missingPoint'] = self.missingPoint # 5
    if self.extraField:
      result['extraField'] = self.extraField # 5
    if self.redirectRegistry:
      result['redirectRegistry'] = self.redirectRegistry # 5
    return result
