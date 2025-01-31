"""Generated class for model_pointset_point.json"""
from .discovery_ref import RefDiscovery


class PointPointsetModel:
  """Generated schema class"""

  def __init__(self):
    self.units = None
    self.description = None
    self.writable = None
    self.baseline_value = None
    self.baseline_tolerance = None
    self.baseline_state = None
    self.range_min = None
    self.range_max = None
    self.cov_increment = None
    self.ref = None
    self.tags = None
    self.structure = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetModel()
    result.units = source.get('units')
    result.description = source.get('description')
    result.writable = source.get('writable')
    result.baseline_value = source.get('baseline_value')
    result.baseline_tolerance = source.get('baseline_tolerance')
    result.baseline_state = source.get('baseline_state')
    result.range_min = source.get('range_min')
    result.range_max = source.get('range_max')
    result.cov_increment = source.get('cov_increment')
    result.ref = source.get('ref')
    result.tags = source.get('tags')
    result.structure = RefDiscovery.map_from(source.get('structure'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = PointPointsetModel.from_dict(source[key])
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
    if self.description:
      result['description'] = self.description # 5
    if self.writable:
      result['writable'] = self.writable # 5
    if self.baseline_value:
      result['baseline_value'] = self.baseline_value # 5
    if self.baseline_tolerance:
      result['baseline_tolerance'] = self.baseline_tolerance # 5
    if self.baseline_state:
      result['baseline_state'] = self.baseline_state # 5
    if self.range_min:
      result['range_min'] = self.range_min # 5
    if self.range_max:
      result['range_max'] = self.range_max # 5
    if self.cov_increment:
      result['cov_increment'] = self.cov_increment # 5
    if self.ref:
      result['ref'] = self.ref # 5
    if self.tags:
      result['tags'] = self.tags # 1
    if self.structure:
      result['structure'] = RefDiscovery.expand_dict(self.structure) # 2
    return result
