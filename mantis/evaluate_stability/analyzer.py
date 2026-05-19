#!/usr/bin/env python3
import os
import re
import sys
from collections import defaultdict

def normalize_text(text):
    """Normalize dynamic variables (timestamps, message details) in output strings."""
    if not text:
        return ""
    # Replace ISO timestamps with "TIMESTAMP"
    text = re.sub(r'\b202\d-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z\b', 'TIMESTAMP', text)
    text = re.sub(r'202\d-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', 'TIMESTAMP', text)
    # Redact dynamic parts of pipeline event error messages
    text = re.sub(r'(Pipeline type event error: While processing message ).*', r'\1REDACTED', text)
    return text

class TestResult:
    def __init__(self, raw_line, is_itemized=False):
        self.raw_line = raw_line.strip()
        line = self.raw_line
        if not line:
            raise ValueError("Empty line")
            
        tokens = line.split()
        if is_itemized:
            # If the first token is a number (delta or marker), strip it.
            if tokens and (tokens[0].isdigit() or re.match(r'^\d+$', tokens[0])):
                tokens = tokens[1:]
                
        if len(tokens) < 6:
            raise ValueError(f"Invalid result line format: {line}")
            
        self.type = tokens[0]          # RESULT or CPBLTY
        self.outcome = tokens[1]       # pass, fail, skip
        self.category = tokens[2]      # e.g., system
        self.test_name = tokens[3]     # e.g., valid_serial_no
        self.stage = tokens[4]         # e.g., STABLE
        self.score = tokens[5]         # e.g., 10/10 or 0/10
        self.reason = normalize_text(" ".join(tokens[6:])) if len(tokens) > 6 else ""

    def key(self):
        return (self.type, self.category, self.test_name)

    def matches(self, other):
        """Compares this parsed result to another (e.g. golden baseline) under normalization."""
        if self.type != other.type:
            return False
        if self.category != other.category:
            return False
        if self.test_name != other.test_name:
            return False
        if self.outcome != other.outcome:
            return False
        if self.stage != other.stage:
            return False
        if self.score != other.score:
            return False
        # Reason string comparison under normalization
        return self.reason == other.reason

class GoldenBaseline:
    def __init__(self, filepath, is_itemized=False):
        self.filepath = filepath
        self.is_itemized = is_itemized
        # Maps key -> list of expected TestResult objects in order
        self.expected = defaultdict(list)
        self.load()

    def load(self):
        if not os.path.exists(self.filepath):
            print(f"Warning: Golden baseline file not found at {self.filepath}", file=sys.stderr)
            return
            
        with open(self.filepath, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                try:
                    res = TestResult(line, is_itemized=self.is_itemized)
                    self.expected[res.key()].append(res)
                except Exception as e:
                    # Ignore lines that don't conform (e.g. generic headers or comments)
                    continue

    def get_expected_result(self, key, occurrence_index):
        results = self.expected.get(key, [])
        if occurrence_index < len(results):
            return results[occurrence_index]
        # If we run more times than expected, default to matching the last expected entry,
        # or if list is empty, return None.
        return results[-1] if results else None

class RunAnalyzer:
    def __init__(self, udmi_root="."):
        self.udmi_root = udmi_root
        self.sequencer_baseline = GoldenBaseline(os.path.join(udmi_root, "etc/sequencer.out"), is_itemized=False)
        self.itemized_baseline = GoldenBaseline(os.path.join(udmi_root, "etc/test_itemized.out"), is_itemized=True)

    def parse_results_file(self, filepath, is_itemized=False):
        """Parses a single results file into a list of TestResult objects."""
        results = []
        if not os.path.exists(filepath):
            return results
            
        with open(filepath, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                try:
                    res = TestResult(line, is_itemized=is_itemized)
                    results.append(res)
                except Exception:
                    continue
        return results

    def analyze_run(self, sequencer_out_path=None, itemized_out_path=None):
        """
        Analyzes a single run (iteration)'s results.
        Returns a dictionary mapping test_key -> dict with outcome details:
        {
           'outcome': 'pass' | 'fail' (actual status vs golden status),
           'raw_outcome': str,       # pass/fail/skip
           'raw_reason': str,
           'expected_outcome': str,
           'expected_reason': str,
           'matched': bool
         }
        """
        analysis = {}
        
        # Track occurrence count of each test case key separately for each suite
        seq_occurrences = defaultdict(int)
        item_occurrences = defaultdict(int)

        # 1. Analyze standard sequencer results
        if sequencer_out_path and os.path.exists(sequencer_out_path):
            run_results = self.parse_results_file(sequencer_out_path, is_itemized=False)
            for res in run_results:
                key = res.key()
                idx = seq_occurrences[key]
                seq_occurrences[key] += 1
                
                expected = self.sequencer_baseline.get_expected_result(key, idx)
                matched = False
                if expected:
                    matched = res.matches(expected)
                else:
                    # If not in baseline, standard rules: pass = matched/successful
                    matched = (res.outcome == 'pass')

                unique_key = f"sequencer:{key[0]}:{key[1]}:{key[2]}:{idx}"
                analysis[unique_key] = {
                    'test_suite': 'sequencer',
                    'type': res.type,
                    'category': res.category,
                    'test_name': res.test_name,
                    'occurrence': idx,
                    'outcome': 'pass' if matched else 'fail',
                    'raw_outcome': res.outcome,
                    'raw_reason': res.reason,
                    'expected_outcome': expected.outcome if expected else 'pass',
                    'expected_reason': expected.reason if expected else '',
                    'matched': matched
                }

        # 2. Analyze itemized results
        if itemized_out_path and os.path.exists(itemized_out_path):
            run_results = self.parse_results_file(itemized_out_path, is_itemized=True)
            for res in run_results:
                key = res.key()
                idx = item_occurrences[key]
                item_occurrences[key] += 1
                
                expected = self.itemized_baseline.get_expected_result(key, idx)
                matched = False
                if expected:
                    matched = res.matches(expected)
                else:
                    matched = (res.outcome == 'pass')

                unique_key = f"itemized:{key[0]}:{key[1]}:{key[2]}:{idx}"
                analysis[unique_key] = {
                    'test_suite': 'itemized',
                    'type': res.type,
                    'category': res.category,
                    'test_name': res.test_name,
                    'occurrence': idx,
                    'outcome': 'pass' if matched else 'fail',
                    'raw_outcome': res.outcome,
                    'raw_reason': res.reason,
                    'expected_outcome': expected.outcome if expected else 'pass',
                    'expected_reason': expected.reason if expected else '',
                    'matched': matched
                }

        return analysis

    def aggregate_runs(self, runs_analyses):
        """
        Aggregates results across multiple run analyses.
        runs_analyses: list of dictionaries returned by self.analyze_run.
        Returns a dictionary mapping unique_key -> aggregated stats:
        {
           'test_suite': str,
           'type': str,
           'category': str,
           'test_name': str,
           'occurrence': int,
           'total_runs': int,
           'pass_count': int,          # Matched expected
           'fail_count': int,          # Mismatched expected (actual failures)
           'raw_pass': int,            # pass outcome count
           'raw_fail': int,            # fail outcome count
           'raw_skip': int,            # skip outcome count
           'pass_rate': float,         # pass_count / total_runs * 100
           'flaky': bool
        }
        """
        aggregates = {}
        total_runs = len(runs_analyses)
        if total_runs == 0:
            return aggregates

        # Collect all unique keys across all runs
        all_keys = set()
        for analysis in runs_analyses:
            all_keys.update(analysis.keys())

        for ukey in all_keys:
            pass_count = 0
            fail_count = 0
            raw_pass = 0
            raw_fail = 0
            raw_skip = 0
            
            first_val = None
            for run in runs_analyses:
                if ukey in run:
                    val = run[ukey]
                    if not first_val:
                        first_val = val
                    
                    if val['matched']:
                        pass_count += 1
                    else:
                        fail_count += 1

                    if val['raw_outcome'] == 'pass':
                        raw_pass += 1
                    elif val['raw_outcome'] == 'fail':
                        raw_fail += 1
                    elif val['raw_outcome'] == 'skip':
                        raw_skip += 1

            if not first_val:
                continue

            # Pass rate measures stability (how often it behaved exactly as expected)
            pass_rate = (pass_count / total_runs) * 100
            flaky = (0 < pass_rate < 100)

            aggregates[ukey] = {
                'test_suite': first_val['test_suite'],
                'type': first_val['type'],
                'category': first_val['category'],
                'test_name': first_val['test_name'],
                'occurrence': first_val['occurrence'],
                'total_runs': total_runs,
                'pass_count': pass_count,
                'fail_count': fail_count,
                'raw_pass': raw_pass,
                'raw_fail': raw_fail,
                'raw_skip': raw_skip,
                'pass_rate': round(pass_rate, 2),
                'flaky': flaky,
                'expected_outcome': first_val['expected_outcome'],
                'expected_reason': first_val['expected_reason']
            }

        return aggregates
