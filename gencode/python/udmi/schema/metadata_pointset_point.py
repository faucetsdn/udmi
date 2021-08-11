"""Generated class for metadata_pointset_point.json"""


class PointPointsetMetadata:
  """Generated schema class"""

  def __init__(self):
    self.units = None
    self.ref = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetMetadata()
    result.units = source.get('units')
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
    if self.ref:
      result['ref'] = self.ref # 5
    return result
