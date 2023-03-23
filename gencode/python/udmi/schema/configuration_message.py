"""Generated class for configuration_message.json"""


class MessageConfiguration:
  """Generated schema class"""

  def __init__(self):
    self.transport = None
    self.namespace = None
    self.source = None
    self.destination = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MessageConfiguration()
    result.transport = source.get('transport')
    result.namespace = source.get('namespace')
    result.source = source.get('source')
    result.destination = source.get('destination')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MessageConfiguration.from_dict(source[key])
    return result

  @staticmethod
  def expand_dict(input):
    result = {}
    for property in input:
      result[property] = input[property].to_dict() if input[property] else {}
    return result

  def to_dict(self):
    result = {}
    if self.transport:
      result['transport'] = self.transport # 5
    if self.namespace:
      result['namespace'] = self.namespace # 5
    if self.source:
      result['source'] = self.source # 5
    if self.destination:
      result['destination'] = self.destination # 5
    return result
