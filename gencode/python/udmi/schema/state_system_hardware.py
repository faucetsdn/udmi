"""Generated class for state_system_hardware.json"""


class SystemHardware:
  """Generated schema class"""

  def __init__(self):
    self.make = None
    self.model = None
    self.sku = None
    self.rev = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemHardware()
    result.make = source.get('make')
    result.model = source.get('model')
    result.sku = source.get('sku')
    result.rev = source.get('rev')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemHardware.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.make:
      result['make'] = self.make # 5
    if self.model:
      result['model'] = self.model # 5
    if self.sku:
      result['sku'] = self.sku # 5
    if self.rev:
      result['rev'] = self.rev # 5
    return result
