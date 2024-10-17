"""Generated class for events_system.json"""


class Object9B20A16A:
  """Generated schema class"""

  def __init__(self):
    self.mem_total_mb = None
    self.mem_free_mb = None
    self.store_total_mb = None
    self.store_free_mb = None
    self.system_load = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = Object9B20A16A()
    result.mem_total_mb = source.get('mem_total_mb')
    result.mem_free_mb = source.get('mem_free_mb')
    result.store_total_mb = source.get('store_total_mb')
    result.store_free_mb = source.get('store_free_mb')
    result.system_load = source.get('system_load')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = Object9B20A16A.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.mem_total_mb:
      result['mem_total_mb'] = self.mem_total_mb # 5
    if self.mem_free_mb:
      result['mem_free_mb'] = self.mem_free_mb # 5
    if self.store_total_mb:
      result['store_total_mb'] = self.store_total_mb # 5
    if self.store_free_mb:
      result['store_free_mb'] = self.store_free_mb # 5
    if self.system_load:
      result['system_load'] = self.system_load # 5
    return result


class SystemEvents:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None
    self.version = None
    self.upgraded_from = None
    self.last_config = None
    self.logentries = None
    self.event_no = None
    self.metrics = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = SystemEvents()
    result.timestamp = source.get('timestamp')
    result.version = source.get('version')
    result.upgraded_from = source.get('upgraded_from')
    result.last_config = source.get('last_config')
    result.logentries = Entry.array_from(source.get('logentries'))
    result.event_no = source.get('event_no')
    result.metrics = Object9B20A16A.from_dict(source.get('metrics'))
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = SystemEvents.from_dict(source[key])
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
    if self.upgraded_from:
      result['upgraded_from'] = self.upgraded_from # 5
    if self.last_config:
      result['last_config'] = self.last_config # 5
    if self.logentries:
      result['logentries'] = self.logentries.to_dict() # 3
    if self.event_no:
      result['event_no'] = self.event_no # 5
    if self.metrics:
      result['metrics'] = self.metrics.to_dict() # 4
    return result
