"""Generated class for config_localnet.json"""


<<<<<<< HEAD
class ObjectAAF5FDC4:
=======
class ObjectABD2578D:
>>>>>>> master
  """Generated schema class"""

  def __init__(self):
    self.addr = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
<<<<<<< HEAD
    result = ObjectAAF5FDC4()
=======
    result = ObjectABD2578D()
>>>>>>> master
    result.addr = source.get('addr')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
<<<<<<< HEAD
      result[key] = ObjectAAF5FDC4.from_dict(source[key])
=======
      result[key] = ObjectABD2578D.from_dict(source[key])
>>>>>>> master
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
    return result


class LocalnetConfig:
  """Generated schema class"""

  def __init__(self):
    self.networks = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = LocalnetConfig()
<<<<<<< HEAD
    result.networks = ObjectAAF5FDC4.map_from(source.get('networks'))
=======
    result.families = ObjectABD2578D.map_from(source.get('families'))
>>>>>>> master
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = LocalnetConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
<<<<<<< HEAD
    if self.networks:
      result['networks'] = ObjectAAF5FDC4.expand_dict(self.networks) # 2
=======
    if self.families:
      result['families'] = ObjectABD2578D.expand_dict(self.families) # 2
>>>>>>> master
    return result
