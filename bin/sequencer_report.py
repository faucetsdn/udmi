"""Generate markdown report from sequencer results"""
import argparse
import copy
import json
import os
import sys
import re
from dataclasses import dataclass
import textwrap

import jinja2

TEMPLATE_PATH = "etc/sequencer_report.md.template"
OUT_FILE = "results.md"

# minimum set of schemas (incase device doesn't publish given message typ)
SCHEMAS = ["state_update", "event_pointset", "event_system"]

# stages for column headers
STAGES = ["stable", "beta", "preview", "alpha"]

# stages to consider for a pass

STAGES_FOR_PASS = ["stable", "beta"]
DEFAULT_SCORE = 1
REFERENCE_SEQUENCES_DIR = 'validator/sequences'
CHECKMARK = '✓'
CROSS = '✕'

@dataclass(unsafe_hash=True, eq=True, order=True)
class TestResult:
  """Container for test result"""

  bucket: str = ""
  name: str = ""
  result: str = ""
  stage: str = ""
  message: str = ""
  score: int = DEFAULT_SCORE

  def passed(self):
    if self.esult == "pass":
      return True
    if self.result == "fail":
      return False
    return None


class SequenceStep(list):
  pass


class Sequence:

  # The line number inside the sequence.md file where the first sequence starts
  START_LINE = 5

  # What exact raw text a single step prefixed with (used for matching and replacing)
  STEP_PREFIX = "1. "

  # Universal indent on each line
  PADDING = 4

  FILE_NAME = 'sequence.md'

  def __init__(self, test: TestResult, reference_dir: str, results_dir: str):
    self.sequence_name = test.name
    self.description = None
    self.test = test
    self.act = []
    self.ref = []

    self.act_path = os.path.join(results_dir, test.name, Sequence.FILE_NAME)
    self.ref_path = os.path.join(reference_dir, test.name, Sequence.FILE_NAME)

    with open(self.act_path, encoding="utf-8") as f:
      self.act_text = f.read()

    with open(self.ref_path, encoding="utf-8") as f:
      self.ref_text = f.read()
    
    self.act = Sequence.get_steps(self.act_text)
    self.ref = Sequence.get_steps(self.ref_text)

    self.formatted = Sequence.format(self.ref, self.act, test)

  def __str__(self):
    return self.formatted

  @classmethod
  def get_steps(cls, text: str) -> list[SequenceStep]:
    """ returns steps from the contents of a sequence.md file"""
    buffer = []
    steps = []
    sequence_lines = text.splitlines()[cls.START_LINE :]
    for line in reversed(sequence_lines):
      buffer.insert(0, line)
      if re.match(cls.STEP_PREFIX, line):
        steps.insert(0, SequenceStep(buffer.copy()))
        buffer.clear()
    return steps

  @staticmethod
  def indent(line: str, indent: int, prefix: str = "") -> str:
    """Adds a fixed width indent of length 'indent' and optionally a prefix to a line"""
    #return f"{{0: <{indent}}}{line}".format(prefix)
    return prefix.ljust(indent, ' ') + line

  @staticmethod
  def longest_line_length(lines: list[str]) -> int:
    """Returns the length of the longest string in a list of strings"""
    return max(len(l) for l in lines)

  @staticmethod
  def failing_step(
      ref: list[SequenceStep], act: list[SequenceStep], result: str = ""
  ) -> int:
    """Returns the index number of the step at which a sequence failed
    Or returns false if there is no failure step
    """
    # The text contents of a sequence may (though shouldn't) differ from the
    # reference spec, but a step is a step, so use lengths
    if result == "pass":
      return len(act)
    return len(act) - 1

  @staticmethod
  def classify_steps(
      ref: list[SequenceStep], act: list[SequenceStep], result=""
  ) -> (list[SequenceStep], SequenceStep, list[SequenceStep]):
    fail_index = Sequence.failing_step(ref, act, result)
    failing = []
    not_done = []

    completed = act[:fail_index]

    try:
      failing = ref[fail_index]
      not_done = ref[fail_index + 1 :]
    except IndexError:
      pass
    return completed, failing, not_done

  @staticmethod
  def format(ref: list[SequenceStep], act: list[SequenceStep], result: TestResult) -> str:
    f = []
    step_count = 0

    completed, failing, not_done = Sequence.classify_steps(ref, act, result.result)
    
    for step in completed:
      step_count += 1
      unnumbered_line = step.pop(0)[len(Sequence.STEP_PREFIX):]
      f.append(Sequence.indent(f"{step_count}. {unnumbered_line}", Sequence.PADDING, CHECKMARK))
      for l in step:
        f.append(Sequence.indent(l, Sequence.PADDING))
    
    if failing:
      step_count += 1
      longest_line_length = Sequence.longest_line_length(failing)
      # + 3 is for the additional " X"
      dash_width = longest_line_length + Sequence.PADDING + 3
      unnumbered_line = failing.pop(0).ljust(longest_line_length, ' ')[len(Sequence.STEP_PREFIX):]
      f.append('')
      f.append('-' * dash_width)
      f.append(Sequence.indent(f"{step_count}. {unnumbered_line} {CROSS}", 4, CROSS))
      for l in failing:
        f.append(f"{Sequence.indent(l.ljust(longest_line_length, ' '), 4, CROSS)} {CROSS}")
      f.append('-' * dash_width)
      f.append('')

    if not_done:
      for step in not_done:
        step_count += 1
        unnumbered_line = step.pop(0)[len(Sequence.STEP_PREFIX):]
        f.append(Sequence.indent(f"{step_count}. {unnumbered_line}", Sequence.PADDING))
        for l in step:
          f.append(Sequence.indent(l, Sequence.PADDING))

    return "\n".join(f)
      

class TemplateHelper:
  """Collection of helper functions made available within Jinja template"""

  @staticmethod
  def pretty_dict(thing: dict):
    """Pretty print dictionary, e.g. key1: value1, key2: value2"""
    if not isinstance(thing, dict):
      return ""
    return ", ".join([": ".join([k, v]) for k, v in thing.items()])

  @staticmethod
  def md_table_header(cols: int):
    """Generate md table headers ( |---|--...) based on no. of cols"""
    return " --- ".join(["|"] * (cols + 1))

  @staticmethod
  def result_icon(result):
    """Textural representation of Boolean or None values"""
    if result is True:
      return CHECKMARK
    if result is False:
      return CROSS
    return "-"


@dataclass
class FeatureStageScore:
  """Container for scored points and total points for a feature and stage"""

  scored: int = 0
  total: int = 0
  stage: str = ""

  def add(self, result, score):
    self.total += score
    if result == "pass":
      self.scored += score

  def has(self):
    """Did the sequencer results have this feature & stage combination?"""
    return self.total > 0

  # TODO This is logic, needs to reside within sequencere itself
  def passed(self):
    """Was the feature & stage combination a pass (full marks)"""
    return self.total > 0 and self.scored == self.total


@dataclass
class SchemaResult:
  """Container for Schema results"""

  schema: str = ""
  stage: str = ""
  test_name: str = ""
  result: str = ""

  def passed(self):
    if self.result == "pass":
      return True
    if self.result == "fail":
      return False
    return None


class SequencerReport:
  """Results of a single sequencer run"""

  def __init__(self, site_path: str, device_id: str):
    self.site_path = site_path
    self.device_id = device_id
    self.results_path = os.path.join(site_path, f"out/sequencer_{device_id}.json")
    self.sequences_path = os.path.join(site_path, f"out/{device_id}/tests")

    with open(self.results_path, encoding="utf-8") as f:
      self.results_json = json.load(f)

    self.start_time = self.results_json["start_time"]
    self.end_time = self.results_json["status"]["timestamp"]
    self.status_message = self.results_json["status"]["message"]

    self._load_test_results()
    self._load_schema_results()
    self._load_device_metadata()
    self._load_sequences()

  def _load_sequences(self):
    self.sequences = {feature: Sequence(feature, REFERENCE_SEQUENCES_DIR, self.sequences_path) for feature in self.features}
    for k,v in enumerate(self.sequences):
      print(k)
      print(str(v))
      
  def _load_schema_results(self):
    """Loads and processes schema validation results"""
    self.schema_results = []
    for schema, value in self.results_json["schemas"].items():
      for test_name, result in value["sequences"].items():
        self.schema_results.append(
            SchemaResult(schema, result["stage"], test_name, result["result"])
        )

    # Ensure all results include at least items from SCHEMAS set of schemas
    schemas = SCHEMAS[:]
    schemas.extend(x.schema for x in self.schema_results if x.schema not in schemas)

    self.schema_results_by_schema = {
        k: all_or_none([x.passed() for x in self.schema_results if x.schema == k])
        for k in schemas
    }

    self.schema_results_by_stage = {
        k: all_or_none([x.passed() for x in self.schema_results if x.stage == k])
        for k in self.stages
    }

  def _load_device_metadata(self):
    """Loads device metadata.json and sets used attributes"""
    self.metadata_path = os.path.join(
        self.site_path, f"devices/{self.device_id}/metadata.json"
    )
    try:
      with open(self.metadata_path, encoding="utf-8") as f:
        self.metadata = json.load(f)
    except Exception as e:
      raise e

    self.device_make = (
        self.metadata.get("system", {}).get("hardware", {}).get("make", "")
    )
    self.device_model = (
        self.metadata.get("system", {}).get("hardware", {}).get("model", "")
    )
    self.device_software = self.metadata.get("system", {}).get("software")

    self.cloud_iot_config_path = os.path.join(self.site_path, "cloud_iot_config.json")

  def has_stage(self, stage: str):
    """Checks if sequencer report has any tests at given stage"""
    all_features = [x[stage] for x in self.features.values()]
    return any(x.total > 0 for x in all_features)

  def _load_test_results(self):
    """Load sequencer test results and features"""
    results = {}
    features = {}
    stages_template = {s: FeatureStageScore(stage=s) for s in STAGES}
    for feature, sequences in self.results_json["features"].items():
      features[feature] = copy.deepcopy(stages_template)
      for name, result in sequences["sequences"].items():
        results[name] = TestResult(
            feature,
            name,
            result["result"],
            result["stage"],
            result["status"]["message"],
        )
        features[feature][result["stage"]].add(result["result"], DEFAULT_SCORE)

    self.results = {
        x: results[x]
        for x in sorted(
            results.keys(), key=lambda k: (results[k].bucket, results[k].name)
        )
    }

    self.features = dict(sorted(features.items()))

    # overall result for feature (pass, fail, n/a)
    self.overall_features = {
        k: all_or_none(
            [
                results.passed()
                for stage, results in features.items()
                if stage in STAGES_FOR_PASS and results.has()
            ]
        )
        for k, features in self.features.items()
    }

    self.stages = [x for x in STAGES if self.has_stage(x)]

  def __repr__(self):
    return str(self.results)


def all_or_none(results: list):
  """Passes list to all, or return None if list is empty"""
  if len(results) == 0:
    return None
  return all(results)


def parse_command_line_args():
  parser = argparse.ArgumentParser()
  parser.add_argument("site_model", type=str, help="path to site model")
  parser.add_argument("device_id", type=str, help="device_path")
  return parser.parse_args()


def main():
  args = parse_command_line_args()
  site_model = args.site_model
  device_id = args.device_id

  report = SequencerReport(site_model, device_id)

  environment = jinja2.Environment()
  environment.filters["pretty_dict"] = TemplateHelper.pretty_dict
  environment.filters["md_table_header"] = TemplateHelper.md_table_header
  environment.filters["result_icon"] = TemplateHelper.result_icon

  template_file_path = os.path.join(sys.path[0], "../", TEMPLATE_PATH)
  with open(template_file_path, encoding="utf-8") as f:
    template = environment.from_string(f.read())

  output = template.render(report=report)

  output_file = os.path.join(site_model, f"out/devices/{device_id}", OUT_FILE)

  with open(output_file, "w", encoding="utf-8") as f:
    f.write(output)

  print(f"Report saved to: {output_file}")


if __name__ == "__main__":
  main()
