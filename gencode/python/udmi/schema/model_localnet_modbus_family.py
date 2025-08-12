"""Generated class for model_localnet_modbus_family.json"""


class Object00138BED:
  """Generated schema class"""

  def __init__(self):
    self.serial_port = None
    self.bit_rate = None
    self.data_bits = None
    self.stop_bits = None
    self.parity = None
    self.encoding = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object00138BED()
    result.serial_port = source.get('serial_port')
    result.bit_rate = source.get('bit_rate')
    result.data_bits = source.get('data_bits')
    result.stop_bits = source.get('stop_bits')
    result.parity = source.get('parity')
    result.encoding = source.get('encoding')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object00138BED.from_dict(source[key])
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
    if self.data_bits:
      result['data_bits'] = self.data_bits # 5
    if self.stop_bits:
      result['stop_bits'] = self.stop_bits # 5
    if self.parity:
      result['parity'] = self.parity # 5
    if self.encoding:
      result['encoding'] = self.encoding # 5
    return result


class ModbusFamilyLocalnetModel:
  """Generated schema class"""

  def __init__(self):
    self.addr = None
    self.modbus_adjunct = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ModbusFamilyLocalnetModel()
    result.addr = source.get('addr')
    result.modbus_adjunct = Object00138BED.from_dict(source.get('modbus_adjunct'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ModbusFamilyLocalnetModel.from_dict(source[key])
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
    if self.modbus_adjunct:
      result['modbus_adjunct'] = self.modbus_adjunct.to_dict() # 4
    return result
