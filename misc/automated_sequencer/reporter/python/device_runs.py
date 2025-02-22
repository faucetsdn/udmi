import itertools
import json
import os
from pathlib import Path
from run_results import RunResult
from test_result import TestResult

METADATA_FILE = 'version.json'
NO_RESULT = ''


class DeviceRuns:
  """All sequencer runs for a given device loaded from directory"""

  def __init__(self, results_dir):
    """Load results

    Arguments: results_dir path to directory of results, where each directory
    must be named by a timestamp
    """
    self.run_dates = []
    self.encountered_test = []  # list of all tests encountered
    self.test_matrix = {}
    self.runs = {}

    self.results_dir = results_dir
    self.load_results(results_dir)
    self.create_test_matrix()

  def load_results(self, path):
    """Load results from given path to directory of results direcotires

    IMPORTANT! This method must load the results in the correct order!
    """
    results_dir = Path(path)
    results_subdirs = [int(x.stem) for x in results_dir.iterdir() if x.is_dir()]
    results_subdirs.sort()
    # Show only latest 30 results to avoid horizontal scroll
    for rdir in results_subdirs[-30:]:
      full_path = os.path.join(path, str(rdir))
      self.run_dates.append(rdir)
      print(f'{full_path} is full path, {rdir} is rdir')
      results = RunResult(rdir, full_path)
      self.runs[f'{rdir}'] = results

  def create_test_matrix(self):
    # load all unique test names (rows)
    self.all_results = [
        x for x in itertools.chain(*[a.results for a in self.runs.values()])
    ]
    encountered_tests = set([t.name for t in self.all_results])
    self.encountered_tests = list(encountered_tests)
    self.encountered_tests.sort()

    for test in self.encountered_tests:
      self.test_matrix[test] = []
      for run in self.runs.values():
        result = run.tests[test].result if test in run.tests else NO_RESULT
        self.test_matrix[test].append(result)
