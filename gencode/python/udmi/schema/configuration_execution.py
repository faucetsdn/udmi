"""Generated class for configuration_execution.json"""
from .configuration_endpoint import EndpointConfiguration
from .configuration_endpoint import EndpointConfiguration
from .config_mapping import MappingConfig


class ExecutionConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.registry_id = None
    self.cloud_region = None
    self.site_name = None
    self.update_topic = None
    self.feed_name = None
    self.reflect_region = None
    self.site_model = None
    self.src_file = None
    self.registry_suffix = None
    self.shard_count = None
    self.shard_index = None
    self.device_id = None
    self.iot_provider = None
    self.reflector_endpoint = None
    self.device_endpoint = None
    self.project_id = None
    self.user_name = None
    self.udmi_namespace = None
    self.bridge_host = None
    self.key_file = None
    self.serial_no = None
    self.log_level = None
    self.min_stage = None
    self.udmi_version = None
    self.udmi_commit = None
    self.udmi_ref = None
    self.udmi_timever = None
    self.enforce_version = None
    self.udmi_root = None
    self.update_to = None
    self.alt_project = None
    self.alt_registry = None
    self.block_unknown = None
    self.sequences = None
    self.mapping_configuration = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ExecutionConfiguration()
    result.registry_id = source.get('registry_id')
    result.cloud_region = source.get('cloud_region')
    result.site_name = source.get('site_name')
    result.update_topic = source.get('update_topic')
    result.feed_name = source.get('feed_name')
    result.reflect_region = source.get('reflect_region')
    result.site_model = source.get('site_model')
    result.src_file = source.get('src_file')
    result.registry_suffix = source.get('registry_suffix')
    result.shard_count = source.get('shard_count')
    result.shard_index = source.get('shard_index')
    result.device_id = source.get('device_id')
    result.iot_provider = source.get('iot_provider')
    result.reflector_endpoint = EndpointConfiguration.from_dict(source.get('reflector_endpoint'))
    result.device_endpoint = EndpointConfiguration.from_dict(source.get('device_endpoint'))
    result.project_id = source.get('project_id')
    result.user_name = source.get('user_name')
    result.udmi_namespace = source.get('udmi_namespace')
    result.bridge_host = source.get('bridge_host')
    result.key_file = source.get('key_file')
    result.serial_no = source.get('serial_no')
    result.log_level = source.get('log_level')
    result.min_stage = source.get('min_stage')
    result.udmi_version = source.get('udmi_version')
    result.udmi_commit = source.get('udmi_commit')
    result.udmi_ref = source.get('udmi_ref')
    result.udmi_timever = source.get('udmi_timever')
    result.enforce_version = source.get('enforce_version')
    result.udmi_root = source.get('udmi_root')
    result.update_to = source.get('update_to')
    result.alt_project = source.get('alt_project')
    result.alt_registry = source.get('alt_registry')
    result.block_unknown = source.get('block_unknown')
    result.sequences = source.get('sequences')
    result.mapping_configuration = MappingConfig.from_dict(source.get('mapping_configuration'))
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
    if self.feed_name:
      result['feed_name'] = self.feed_name # 5
    if self.reflect_region:
      result['reflect_region'] = self.reflect_region # 5
    if self.site_model:
      result['site_model'] = self.site_model # 5
    if self.src_file:
      result['src_file'] = self.src_file # 5
    if self.registry_suffix:
      result['registry_suffix'] = self.registry_suffix # 5
    if self.shard_count:
      result['shard_count'] = self.shard_count # 5
    if self.shard_index:
      result['shard_index'] = self.shard_index # 5
    if self.device_id:
      result['device_id'] = self.device_id # 5
    if self.iot_provider:
      result['iot_provider'] = self.iot_provider # 5
    if self.reflector_endpoint:
      result['reflector_endpoint'] = self.reflector_endpoint.to_dict() # 4
    if self.device_endpoint:
      result['device_endpoint'] = self.device_endpoint.to_dict() # 4
    if self.project_id:
      result['project_id'] = self.project_id # 5
    if self.user_name:
      result['user_name'] = self.user_name # 5
    if self.udmi_namespace:
      result['udmi_namespace'] = self.udmi_namespace # 5
    if self.bridge_host:
      result['bridge_host'] = self.bridge_host # 5
    if self.key_file:
      result['key_file'] = self.key_file # 5
    if self.serial_no:
      result['serial_no'] = self.serial_no # 5
    if self.log_level:
      result['log_level'] = self.log_level # 5
    if self.min_stage:
      result['min_stage'] = self.min_stage # 5
    if self.udmi_version:
      result['udmi_version'] = self.udmi_version # 5
    if self.udmi_commit:
      result['udmi_commit'] = self.udmi_commit # 5
    if self.udmi_ref:
      result['udmi_ref'] = self.udmi_ref # 5
    if self.udmi_timever:
      result['udmi_timever'] = self.udmi_timever # 5
    if self.enforce_version:
      result['enforce_version'] = self.enforce_version # 5
    if self.udmi_root:
      result['udmi_root'] = self.udmi_root # 5
    if self.update_to:
      result['update_to'] = self.update_to # 5
    if self.alt_project:
      result['alt_project'] = self.alt_project # 5
    if self.alt_registry:
      result['alt_registry'] = self.alt_registry # 5
    if self.block_unknown:
      result['block_unknown'] = self.block_unknown # 5
    if self.sequences:
      result['sequences'] = self.sequences # 1
    if self.mapping_configuration:
      result['mapping_configuration'] = self.mapping_configuration.to_dict() # 4
    return result
