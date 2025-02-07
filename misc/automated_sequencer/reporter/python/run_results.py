import json
import os
from test_result import TestResult

RESULTS_FILE = 'RESULT.log'
METADATA_FILE = 'version.json'


class RunResult:
  """Results of a single sequencer run"""

  def __init__(self, timestamp, dir):
    self.dir = dir
    self.path = os.path.join(dir, RESULTS_FILE)
    self.results = []
    self.tests = {}
    self.udmi_hash = ''
    self.timestamp = timestamp

    try:
      with open(os.path.join(dir, METADATA_FILE)) as f:
        metadata = json.load(f)
        self.udmi_hash = metadata.get('version')
    except Exception as e:
      print(e)

    try:
      with open(self.path) as f:
        results = f.read().splitlines()
        for result_line in results:
          action = result_line.split(' ')[0]
          if action != 'RESULT':
            continue
          result = TestResult()
          result.set_from_result_line(result_line)
          # Ignore schema results
          if result.bucket == 'schemas':
            continue
          self.results.append(result)
          self.tests[result.name] = result
    except Exception as e:
      # Every sequencer run attempt has a directory
      # Error = empty directory = no results
      print(e)

  def __repr__(self):
    return str(self.results)


# results = RunResult('results/1')
# print(results.tests)
