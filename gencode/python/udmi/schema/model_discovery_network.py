"""Generated class for model_discovery_network.json"""


class NetworkDiscoveryTestingModel:
  """Generated schema class"""

  def __init__(self):
<<<<<<< HEAD:gencode/python/udmi/schema/model_discovery_network.py
    pass
=======
    self.addr = None
>>>>>>> master:gencode/python/udmi/schema/model_localnet_family.py

  @staticmethod
  def from_dict(source):
    if not source:
      return None
<<<<<<< HEAD:gencode/python/udmi/schema/model_discovery_network.py
    result = NetworkDiscoveryTestingModel()
=======
    result = FamilyLocalnetModel()
    result.addr = source.get('addr')
>>>>>>> master:gencode/python/udmi/schema/model_localnet_family.py
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = NetworkDiscoveryTestingModel.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
<<<<<<< HEAD:gencode/python/udmi/schema/model_discovery_network.py
=======
    if self.addr:
      result['addr'] = self.addr # 5
>>>>>>> master:gencode/python/udmi/schema/model_localnet_family.py
    return result
