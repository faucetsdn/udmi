"""Generated class for config_discovery_family.json"""


class FamilyDiscoveryConfig:
  """Generated schema class"""

  def __init__(self):
    self.generation = None
    self.scan_interval_sec = None
    self.scan_duration_sec = None
    self.addrs = None
    self.passive_sec = None
    self.depth = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = FamilyDiscoveryConfig()
    result.generation = source.get('generation')
    result.scan_interval_sec = source.get('scan_interval_sec')
    result.scan_duration_sec = source.get('scan_duration_sec')
    result.addrs = source.get('addrs')
    result.passive_sec = source.get('passive_sec')
    result.depth = source.get('depth')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = FamilyDiscoveryConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.generation:
      result['generation'] = self.generation # 5
    if self.scan_interval_sec:
      result['scan_interval_sec'] = self.scan_interval_sec # 5
    if self.scan_duration_sec:
      result['scan_duration_sec'] = self.scan_duration_sec # 5
    if self.addrs:
      result['addrs'] = self.addrs # 1
    if self.passive_sec:
      result['passive_sec'] = self.passive_sec # 5
    if self.depth:
      result['depth'] = self.depth # 5
    return result
