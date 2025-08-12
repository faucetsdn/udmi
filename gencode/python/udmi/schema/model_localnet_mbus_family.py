"""Generated class for model_localnet_mbus_family.json"""


class Object6DA56C20:
  """Generated schema class"""

  def __init__(self):
    self.serial_port = None
    self.bit_rate = None
    self.secondary_addr = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object6DA56C20()
    result.serial_port = source.get('serial_port')
    result.bit_rate = source.get('bit_rate')
    result.secondary_addr = source.get('secondary_addr')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object6DA56C20.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.serial_port:
      result['serial_port'] = self.serial_port # 5
    if self.bit_rate:
      result['bit_rate'] = self.bit_rate # 5
    if self.secondary_addr:
      result['secondary_addr'] = self.secondary_addr # 5
    return result


class MBusFamilyLocalnetModel:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.mbus_adjunct = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MBusFamilyLocalnetModel()
    result.addr = source.get('addr')
    result.mbus_adjunct = Object6DA56C20.from_dict(source.get('mbus_adjunct'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MBusFamilyLocalnetModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.addr:
      result['addr'] = self.addr # 5
    if self.mbus_adjunct:
      result['mbus_adjunct'] = self.mbus_adjunct.to_dict() # 4
    return result
