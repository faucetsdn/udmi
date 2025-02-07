from dataclasses import dataclass


# ESULT pass discovery.scan periodic_scan ALPHA 5 Sequence complete
@dataclass
class TestResult:
  result: str = ''
  bucket: str = ''
  name: str = ''
  stage: str = ''
  score: int = 0
  message: str = ''

  def set_from_result_line(self, result_line):
    split = result_line.split(' ')
    self.result = split[1]
    self.bucket = split[2]
    self.name = split[3]
    self.stage = split[4]
    self.score = split[5]
    self.message = ' '.join(split[6:])
