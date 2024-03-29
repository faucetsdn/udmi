"""Generated class for data_template.json"""


class MessageTemplateData:
  """Generated schema class"""

  def __init__(self):
    self.timestamp = None

  @staticmethod
  def from_dict(source):
    if not source:
      return None
    result = MessageTemplateData()
    result.timestamp = source.get('timestamp')
    return result

  @staticmethod
  def map_from(source):
    if not source:
      return None
    result = {}
    for key in source:
      result[key] = MessageTemplateData.from_dict(source[key])
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
    return result
