"""Generated class for metadata_pointset_point.json"""


class PointPointsetMetadata:
  """Generated schema class"""

  def __init__(self):
    self.units = None
    self.writeable = None
    self.baseline_value = None
    self.baseline_tolerance = None
    self.baseline_state = None
    self.ref = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetMetadata()
    result.units = source.get('units')
    result.writeable = source.get('writeable')
    result.baseline_value = source.get('baseline_value')
    result.baseline_tolerance = source.get('baseline_tolerance')
    result.baseline_state = source.get('baseline_state')
    result.ref = source.get('ref')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointPointsetMetadata.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.units:
      result['units'] = self.units # 5
    if self.writeable:
      result['writeable'] = self.writeable # 5
    if self.baseline_value:
      result['baseline_value'] = self.baseline_value # 5
    if self.baseline_tolerance:
      result['baseline_tolerance'] = self.baseline_tolerance # 5
    if self.baseline_state:
      result['baseline_state'] = self.baseline_state # 5
    if self.ref:
      result['ref'] = self.ref # 5
    return result
