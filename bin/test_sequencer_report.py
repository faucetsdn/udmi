import unittest
import json
from  sequencer_report import Sequence, SequenceStep
from dataclasses import dataclass



SAMPLE_SEQUENCE = """
## extra_config (BETA)

Check that the device correctly handles an extra out-of-schema field

1. Step 1:
    * Substep 1
    * Substep 2
1. Step 2
1. Step `3`
"""

SAMPLE_PARTIAL_SEQUENCE = """
## extra_config (BETA)

Check that the device correctly handles an extra out-of-schema field

1. Step 1:
    * Substep 1
    * Substep 2
1. Step 2
"""

SAMPLE_EXPECTED = """✓   1. Step 1
✓   2. Multistep 2
      * line a
      * line b

----------------
✕   3. Step 3 ✕
----------------

    4. Step 4"""

REAL_SEQUENCE = """
## empty_enumeration (PREVIEW)

check enumeration of nothing at all

1. Update config before enumeration not active:
    * Add `discovery` = { "enumerate": {  } }
1. Wait for enumeration not active
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration
1. Check that no feature enumeration
1. Check that no point enumeration
"""

REAL_SEQUENCE_PARTIAL = """
## empty_enumeration (PREVIEW)

check enumeration of nothing at all

1. Update config before enumeration not active:
    * Add `discovery` = { "enumerate": {  } }
1. Wait for enumeration not active
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
"""

@dataclass
class SampleTestResult:
  result: str = 'fail'
  name: str = 'extra_config'

class TestSequencerReport(unittest.TestCase):

  def test_sequence_get_steps(self):
    steps = Sequence.get_steps(SAMPLE_SEQUENCE)
    #print(json.dumps(steps))
    self.assertEqual(steps[0][1], "    * Substep 1")
    self.assertEqual(steps[2][0], "1. Step `3`")

  def test_indent_with_prefix(self):
    formatted = Sequence.indent("some string", 5, '>')
    self.assertEqual(formatted, ">    some string")

  def test_longest_line(self):
    lines = ['a', 'abc', 'abcdef']
    self.assertEqual(Sequence.longest_line_length(lines), 6)

  def test_failing_step(self):
    # method doesn't actually care what the contents of the list
    # so just use a fixed list
    ref = ['Step 1', 'Step 2', 'Step 3', ' Step 4']
    act = ['Step 1', 'Step 2']
    failing_step = Sequence.failing_step(ref, act)
    self.assertEqual(failing_step, 1)

    passed_all = Sequence.failing_step(ref, ref)
    # if non failed is the index of the last step
    self.assertEqual(passed_all, 3)
  
  def test_classed_steps(self):

    ref = ['Step 1', 'Step 2', 'Step 3', ' Step 4']
    act = ['Step 1', 'Step 2']

    done, fail, todo = Sequence.classify_steps(ref, act)
    self.assertEqual(len(done), 1)
    self.assertEqual(len(todo), 2)

    done, fail, todo = Sequence.classify_steps(ref, ref, 'pass')
    self.assertEqual(len(done), 4)
    self.assertEqual(len(todo), 0)

    done, fail, todo = Sequence.classify_steps(ref, ref, 'fail')
    self.assertEqual(len(done), 3)

  def test_class_single(self):
    ref = [['Step 1'], ['Step 2'], ['Step 3'], [' Step 4']]
    done, fail, todo = Sequence.classify_steps(ref, ref[0], 'fail')
    print(done)
    print(fail)
    print(todo)

  def test_formatter(self):
    ref = [['1. Step 1'], ['1. Multistep 2', '  * line a', '  * line b'], ['1. Step 3'], ['1. Step 4']]
    act = [['1. Step 1'], ['1. Multistep 2', '  * line a', '  * line b']]

    formatted = "\n".join(Sequence.format(ref, act, SampleTestResult()))
    self.assertEqual(formatted, SAMPLE_EXPECTED)

  def test_formatter_real(self):
    ref = Sequence.get_steps(REAL_SEQUENCE)
    act = Sequence.get_steps(REAL_SEQUENCE_PARTIAL)

    a = Sequence.format(ref, act, SampleTestResult())
    
    #print('\n'.join(a))
        
  def test_sequence(self):
    seq = Sequence(SampleTestResult, 'validator/sequences', 'sites/udmi_site_model/out/devices/AHU-1/tests')
    print(str(seq))


  def test_single_step(self):
      steps = Sequence.get_steps(SAMPLE_SEQUENCE)
      #print(json.dumps(steps))
      self.assertEqual(steps[0][1], "    * Substep 1")
      self.assertEqual(steps[2][0], "1. Step `3`")
    
if __name__ == "__main__":
  unittest.main()
