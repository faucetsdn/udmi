"""Generated class for options_pubber.json"""


class PubberOptions:
  """Generated schema class"""

  def __init__(self):
    self.fixedSampleRate = None
    self.noHardware = None
    self.noConfigAck = None
    self.noPersist = None
    self.noLastStart = None
    self.noLastConfig = None
    self.badCategory = None
    self.badVersion = None
    self.badAddr = None
    self.noProxy = None
    self.extraDevice = None
    self.barfConfig = None
    self.messageTrace = None
    self.extraPoint = None
    self.configStateDelay = None
    self.missingPoint = None
    self.extraField = None
    self.emptyMissing = None
    self.redirectRegistry = None
    self.msTimestamp = None
    self.smokeCheck = None
    self.skewClock = None
    self.noPointState = None
    self.noState = None
    self.noStatus = None
    self.noFolder = None
    self.badLevel = None
    self.spamState = None
    self.tweakState = None
    self.badState = None
    self.baseState = None
    self.dupeState = None
    self.noLog = None
    self.featureEnableSwap = None
    self.disableWriteback = None
    self.noWriteback = None
    self.fixedLogLevel = None
    self.fastWrite = None
    self.delayWrite = None
    self.softwareFirmwareValue = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PubberOptions()
    result.fixedSampleRate = source.get('fixedSampleRate')
    result.noHardware = source.get('noHardware')
    result.noConfigAck = source.get('noConfigAck')
    result.noPersist = source.get('noPersist')
    result.noLastStart = source.get('noLastStart')
    result.noLastConfig = source.get('noLastConfig')
    result.badCategory = source.get('badCategory')
    result.badVersion = source.get('badVersion')
    result.badAddr = source.get('badAddr')
    result.noProxy = source.get('noProxy')
    result.extraDevice = source.get('extraDevice')
    result.barfConfig = source.get('barfConfig')
    result.messageTrace = source.get('messageTrace')
    result.extraPoint = source.get('extraPoint')
    result.configStateDelay = source.get('configStateDelay')
    result.missingPoint = source.get('missingPoint')
    result.extraField = source.get('extraField')
    result.emptyMissing = source.get('emptyMissing')
    result.redirectRegistry = source.get('redirectRegistry')
    result.msTimestamp = source.get('msTimestamp')
    result.smokeCheck = source.get('smokeCheck')
    result.skewClock = source.get('skewClock')
    result.noPointState = source.get('noPointState')
    result.noState = source.get('noState')
    result.noStatus = source.get('noStatus')
    result.noFolder = source.get('noFolder')
    result.badLevel = source.get('badLevel')
    result.spamState = source.get('spamState')
    result.tweakState = source.get('tweakState')
    result.badState = source.get('badState')
    result.baseState = source.get('baseState')
    result.dupeState = source.get('dupeState')
    result.noLog = source.get('noLog')
    result.featureEnableSwap = source.get('featureEnableSwap')
    result.disableWriteback = source.get('disableWriteback')
    result.noWriteback = source.get('noWriteback')
    result.fixedLogLevel = source.get('fixedLogLevel')
    result.fastWrite = source.get('fastWrite')
    result.delayWrite = source.get('delayWrite')
    result.softwareFirmwareValue = source.get('softwareFirmwareValue')
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
    if self.fixedSampleRate:
      result['fixedSampleRate'] = self.fixedSampleRate # 5
    if self.noHardware:
      result['noHardware'] = self.noHardware # 5
    if self.noConfigAck:
      result['noConfigAck'] = self.noConfigAck # 5
    if self.noPersist:
      result['noPersist'] = self.noPersist # 5
    if self.noLastStart:
      result['noLastStart'] = self.noLastStart # 5
    if self.noLastConfig:
      result['noLastConfig'] = self.noLastConfig # 5
    if self.badCategory:
      result['badCategory'] = self.badCategory # 5
    if self.badVersion:
      result['badVersion'] = self.badVersion # 5
    if self.badAddr:
      result['badAddr'] = self.badAddr # 5
    if self.noProxy:
      result['noProxy'] = self.noProxy # 5
    if self.extraDevice:
      result['extraDevice'] = self.extraDevice # 5
    if self.barfConfig:
      result['barfConfig'] = self.barfConfig # 5
    if self.messageTrace:
      result['messageTrace'] = self.messageTrace # 5
    if self.extraPoint:
      result['extraPoint'] = self.extraPoint # 5
    if self.configStateDelay:
      result['configStateDelay'] = self.configStateDelay # 5
    if self.missingPoint:
      result['missingPoint'] = self.missingPoint # 5
    if self.extraField:
      result['extraField'] = self.extraField # 5
    if self.emptyMissing:
      result['emptyMissing'] = self.emptyMissing # 5
    if self.redirectRegistry:
      result['redirectRegistry'] = self.redirectRegistry # 5
    if self.msTimestamp:
      result['msTimestamp'] = self.msTimestamp # 5
    if self.smokeCheck:
      result['smokeCheck'] = self.smokeCheck # 5
    if self.skewClock:
      result['skewClock'] = self.skewClock # 5
    if self.noPointState:
      result['noPointState'] = self.noPointState # 5
    if self.noState:
      result['noState'] = self.noState # 5
    if self.noStatus:
      result['noStatus'] = self.noStatus # 5
    if self.noFolder:
      result['noFolder'] = self.noFolder # 5
    if self.badLevel:
      result['badLevel'] = self.badLevel # 5
    if self.spamState:
      result['spamState'] = self.spamState # 5
    if self.tweakState:
      result['tweakState'] = self.tweakState # 5
    if self.badState:
      result['badState'] = self.badState # 5
    if self.baseState:
      result['baseState'] = self.baseState # 5
    if self.dupeState:
      result['dupeState'] = self.dupeState # 5
    if self.noLog:
      result['noLog'] = self.noLog # 5
    if self.featureEnableSwap:
      result['featureEnableSwap'] = self.featureEnableSwap # 5
    if self.disableWriteback:
      result['disableWriteback'] = self.disableWriteback # 5
    if self.noWriteback:
      result['noWriteback'] = self.noWriteback # 5
    if self.fixedLogLevel:
      result['fixedLogLevel'] = self.fixedLogLevel # 5
    if self.fastWrite:
      result['fastWrite'] = self.fastWrite # 5
    if self.delayWrite:
      result['delayWrite'] = self.delayWrite # 5
    if self.softwareFirmwareValue:
      result['softwareFirmwareValue'] = self.softwareFirmwareValue # 5
    return result
