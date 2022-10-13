"""Generated class for event_system.json"""


class ObjectD1FC597A:
  """Generated schema class"""

  def __init__(self):
    self.restart_count = None
    self.mem_total_mb = None
    self.mem_free_mb = None
    self.store_total_mb = None
    self.store_free_mb = None
    self.cpu_temp_c = None
    self.cpu_load_5m = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = ObjectD1FC597A()
    result.restart_count = source.get('restart_count')
    result.mem_total_mb = source.get('mem_total_mb')
    result.mem_free_mb = source.get('mem_free_mb')
    result.store_total_mb = source.get('store_total_mb')
    result.store_free_mb = source.get('store_free_mb')
    result.cpu_temp_c = source.get('cpu_temp_c')
    result.cpu_load_5m = source.get('cpu_load_5m')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = ObjectD1FC597A.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.restart_count:
      result['restart_count'] = self.restart_count # 5
    if self.mem_total_mb:
      result['mem_total_mb'] = self.mem_total_mb # 5
    if self.mem_free_mb:
      result['mem_free_mb'] = self.mem_free_mb # 5
    if self.store_total_mb:
      result['store_total_mb'] = self.store_total_mb # 5
    if self.store_free_mb:
      result['store_free_mb'] = self.store_free_mb # 5
    if self.cpu_temp_c:
      result['cpu_temp_c'] = self.cpu_temp_c # 5
    if self.cpu_load_5m:
      result['cpu_load_5m'] = self.cpu_load_5m # 5
    return result


class SystemEvent:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.last_config = None
    self.logentries = None
    self.metrics = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemEvent()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.last_config = source.get('last_config')
    result.logentries = Entry.array_from(source.get('logentries'))
    result.metrics = ObjectD1FC597A.from_dict(source.get('metrics'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemEvent.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.timestamp:
      result['timestamp'] = self.timestamp # 5
    if self.version:
      result['version'] = self.version # 5
    if self.last_config:
      result['last_config'] = self.last_config # 5
    if self.logentries:
      result['logentries'] = self.logentries.to_dict() # 3
    if self.metrics:
      result['metrics'] = self.metrics.to_dict() # 4
    return result
