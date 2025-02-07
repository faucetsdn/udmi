from datetime import datetime

COMMIT_BG_1 = ''
COMMIT_BG_2 = 'table-dark'


class TemplateHelper:
  last_commit_hash = ''
  commit_colour = COMMIT_BG_1

  @classmethod
  def commit_background(cls, hash):
    print(f'{hash} was {cls.last_commit_hash}')
    if hash != cls.last_commit_hash:
      cls.commit_colour = (
          COMMIT_BG_2 if cls.commit_colour == COMMIT_BG_1 else COMMIT_BG_1
      )
    cls.last_commit_hash = hash
    return cls.commit_colour

  @staticmethod
  def result_background(result):
    """https://getbootstrap.com/docs/5.0/content/tables/#variants"""
    if result == 'pass':
      return 'table-success'
    if result == 'fail':
      return 'table-danger'
    if result == 'skip':
      return 'table-warning'

    return ''

  @staticmethod
  def header_date(timestamp):
    return datetime.utcfromtimestamp(timestamp).strftime('%d-%b')
