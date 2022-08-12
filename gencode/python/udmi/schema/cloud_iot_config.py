"""Generated class for cloud_iot_config.json"""


class CloudIotConfig:
  """Generated schema class"""

  def __init__(self):
    self.registry_id = None
    self.cloud_region = None
    self.site_name = None
    self.update_topic = None
    self.reflect_region = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = CloudIotConfig()
    result.registry_id = source.get('registry_id')
    result.cloud_region = source.get('cloud_region')
    result.site_name = source.get('site_name')
    result.update_topic = source.get('update_topic')
    result.reflect_region = source.get('reflect_region')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = CloudIotConfig.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.registry_id:
      result['registry_id'] = self.registry_id # 5
    if self.cloud_region:
      result['cloud_region'] = self.cloud_region # 5
    if self.site_name:
      result['site_name'] = self.site_name # 5
    if self.update_topic:
      result['update_topic'] = self.update_topic # 5
    if self.reflect_region:
      result['reflect_region'] = self.reflect_region # 5
    return result
