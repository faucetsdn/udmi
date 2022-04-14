"""Generated class for model_pointset_point.json"""


class PointPointsetMetadata:
  """Generated schema class"""

  def __init__(self):
    self.units = None
    self.writable = None
    self.baseline_value = None
    self.baseline_tolerance = None
    self.baseline_state = None
    self.cov_increment = None
    self.ref = None
    self.min_loglevel = None
    self.sample_limit_sec = None
    self.sample_rate_sec = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = PointPointsetMetadata()
    result.units = source.get('units')
    result.writable = source.get('writable')
    result.baseline_value = source.get('baseline_value')
    result.baseline_tolerance = source.get('baseline_tolerance')
    result.baseline_state = source.get('baseline_state')
    result.cov_increment = source.get('cov_increment')
    result.ref = source.get('ref')
    result.min_loglevel = source.get('min_loglevel')
    result.sample_limit_sec = source.get('sample_limit_sec')
    result.sample_rate_sec = source.get('sample_rate_sec')
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
    if self.writable:
      result['writable'] = self.writable # 5
    if self.baseline_value:
      result['baseline_value'] = self.baseline_value # 5
    if self.baseline_tolerance:
      result['baseline_tolerance'] = self.baseline_tolerance # 5
    if self.baseline_state:
      result['baseline_state'] = self.baseline_state # 5
    if self.cov_increment:
      result['cov_increment'] = self.cov_increment # 5
    if self.ref:
      result['ref'] = self.ref # 5
    if self.min_loglevel:
      result['min_loglevel'] = self.min_loglevel # 5
    if self.sample_limit_sec:
      result['sample_limit_sec'] = self.sample_limit_sec # 5
    if self.sample_rate_sec:
      result['sample_rate_sec'] = self.sample_rate_sec # 5
    return result
