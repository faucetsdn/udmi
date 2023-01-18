"""Generated class for configuration_execution.json"""


class ExecutionConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.registry_id = None
    self.cloud_region = None
    self.site_name = None
    self.update_topic = None
    self.reflect_region = None
    self.site_model = None
    self.device_id = None
    self.project_id = None
    self.key_file = None
    self.serial_no = None
    self.log_level = None
    self.udmi_version = None
    self.udmi_root = None
    self.alt_project = None
    self.alt_registry = None
    self.block_unknown = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ExecutionConfiguration()
    result.registry_id = source.get('registry_id')
    result.cloud_region = source.get('cloud_region')
    result.site_name = source.get('site_name')
    result.update_topic = source.get('update_topic')
    result.reflect_region = source.get('reflect_region')
    result.site_model = source.get('site_model')
    result.device_id = source.get('device_id')
    result.project_id = source.get('project_id')
    result.key_file = source.get('key_file')
    result.serial_no = source.get('serial_no')
    result.log_level = source.get('log_level')
    result.udmi_version = source.get('udmi_version')
    result.udmi_root = source.get('udmi_root')
    result.alt_project = source.get('alt_project')
    result.alt_registry = source.get('alt_registry')
    result.block_unknown = source.get('block_unknown')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ExecutionConfiguration.from_dict(source[key])
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
    if self.site_model:
      result['site_model'] = self.site_model # 5
    if self.device_id:
      result['device_id'] = self.device_id # 5
    if self.project_id:
      result['project_id'] = self.project_id # 5
    if self.key_file:
      result['key_file'] = self.key_file # 5
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.log_level:
      result['log_level'] = self.log_level # 5
    if self.udmi_version:
      result['udmi_version'] = self.udmi_version # 5
    if self.udmi_root:
      result['udmi_root'] = self.udmi_root # 5
    if self.alt_project:
      result['alt_project'] = self.alt_project # 5
    if self.alt_registry:
      result['alt_registry'] = self.alt_registry # 5
    if self.block_unknown:
      result['block_unknown'] = self.block_unknown # 5
    return result
