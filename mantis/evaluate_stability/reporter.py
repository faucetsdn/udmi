#!/usr/bin/env python3
import json
import os
from datetime import datetime

class MantisReporter:
    def __init__(self, target, phase, output_dir):
        self.target = target
        self.phase = phase
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)

    def get_clean_target(self):
        return self.target.replace("/", "_").replace("+", "_").strip("_")

    def generate_report_markdown(self, aggregates):
        """Generates the standard phase report."""
        total_tests = len(aggregates)
        flaky_tests = [t for t in aggregates.values() if t['flaky']]
        failed_tests = [t for t in aggregates.values() if t['pass_rate'] == 0]
        passed_tests = [t for t in aggregates.values() if t['pass_rate'] == 100]

        # Overall stability percentage (average of all test case pass rates)
        avg_stability = sum(t['pass_rate'] for t in aggregates.values()) / total_tests if total_tests > 0 else 0.0

        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S UTC")

        md = []
        md.append(f"# Mantis Stability & Flakiness Report — Phase `{self.phase.upper()}`")
        md.append(f"**Target System**: `{self.target}`  ")
        md.append(f"**Generated At**: `{timestamp}`  ")
        md.append(f"**Total Test Runs Evaluated**: `{next(iter(aggregates.values()))['total_runs'] if aggregates else 0}`")
        md.append("\n---")
        
        md.append("## Executive Summary")
        md.append("| Metric | Value |")
        md.append("| :--- | :--- |")
        md.append(f"| **Total Test Cases Checked** | `{total_tests}` |")
        md.append(f"| **Perfectly Stable Tests (100% Success)** | `{len(passed_tests)}` |")
        md.append(f"| **Flaky Tests (0% < Success < 100%)** | `{len(flaky_tests)}` |")
        md.append(f"| **Consistently Failing Tests (0% Success)** | `{len(failed_tests)}` |")
        md.append(f"| **Overall System Stability Score** | `**{avg_stability:.2f}%**` |")
        
        md.append("\n---")

        # Flaky tests details
        if flaky_tests:
            md.append("## ⚠️ Detected Flaky Tests (Prioritized Hunt List)")
            md.append("> These tests show unstable behavior and fail randomly. Focus your stabilization efforts here!")
            md.append("\n| Test Suite | Category | Test Case | Expected Outcome | Pass Rate | Raw (Pass / Fail / Skip) |")
            md.append("| :--- | :--- | :--- | :--- | :--- | :--- |")
            # Sort flaky tests by lowest pass rate (most flaky/unstable first)
            for t in sorted(flaky_tests, key=lambda x: x['pass_rate']):
                md.append(f"| `{t['test_suite']}` | `{t['category']}` | `{t['test_name']}` | `{t['expected_outcome']}` | **{t['pass_rate']}%** | {t['raw_pass']} / {t['raw_fail']} / {t['raw_skip']} |")
        else:
            md.append("## 🎉 No Flaky Tests Detected!")
            md.append("> Excellent! All executed tests behaved 100% consistently across all runs.")

        md.append("\n---")

        # Consistently failing tests details
        if failed_tests:
            md.append("## ❌ Consistently Failing Tests")
            md.append("> These tests failed to match their expected outcome in 100% of the runs.")
            md.append("\n| Test Suite | Category | Test Case | Expected Outcome | Pass Rate | Raw (Pass / Fail / Skip) |")
            md.append("| :--- | :--- | :--- | :--- | :--- | :--- |")
            for t in sorted(failed_tests, key=lambda x: (x['test_suite'], x['category'], x['test_name'])):
                md.append(f"| `{t['test_suite']}` | `{t['category']}` | `{t['test_name']}` | `{t['expected_outcome']}` | **{t['pass_rate']}%** | {t['raw_pass']} / {t['raw_fail']} / {t['raw_skip']} |")
            md.append("\n---")

        # Full details table
        md.append("## 📋 Complete Test Case Results")
        md.append("| Test Suite | Category | Test Case | Occurrence | Expected Outcome | Pass Rate | Status |")
        md.append("| :--- | :--- | :--- | :---: | :---: | :--- | :--- |")
        
        for t in sorted(aggregates.values(), key=lambda x: (x['test_suite'], x['category'], x['test_name'], x['occurrence'])):
            status_str = "🟢 Stable" if t['pass_rate'] == 100 else ("🟡 Flaky" if t['flaky'] else "🔴 Consistent Fail")
            md.append(f"| `{t['test_suite']}` | `{t['category']}` | `{t['test_name']}` | `{t['occurrence']}` | `{t['expected_outcome']}` | **{t['pass_rate']}%** | {status_str} |")

        return "\n".join(md)

    def generate_comparison_markdown(self, before_aggregates, after_aggregates):
        """Generates a high-impact comparison report comparing BEFORE and AFTER phases."""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S UTC")
        
        md = []
        md.append(f"# Mantis Stabilization Comparison Report 🦗🎯")
        md.append(f"**Target System**: `{self.target}`  ")
        md.append(f"**Comparison Generated At**: `{timestamp}`  ")
        md.append("\n---")
        
        md.append("## 📊 Stabilization Summary")
        
        before_stability = sum(t['pass_rate'] for t in before_aggregates.values()) / len(before_aggregates) if before_aggregates else 0.0
        after_stability = sum(t['pass_rate'] for t in after_aggregates.values()) / len(after_aggregates) if after_aggregates else 0.0
        net_delta = after_stability - before_stability
        
        md.append("| Metric | Before Phase | After Phase | Net Delta |")
        md.append("| :--- | :---: | :---: | :---: |")
        md.append(f"| **Overall System Stability** | {before_stability:.2f}% | {after_stability:.2f}% | **{'+' if net_delta >= 0 else ''}{net_delta:.2f}%** |")
        md.append(f"| **Flaky Test Cases** | `{len([t for t in before_aggregates.values() if t['flaky']])}` | `{len([t for t in after_aggregates.values() if t['flaky']])}` | `{len([t for t in after_aggregates.values() if t['flaky']]) - len([t for t in before_aggregates.values() if t['flaky']]):+}` |")
        md.append(f"| **Perfectly Stable Test Cases** | `{len([t for t in before_aggregates.values() if t['pass_rate'] == 100])}` | `{len([t for t in after_aggregates.values() if t['pass_rate'] == 100])}` | `{len([t for t in after_aggregates.values() if t['pass_rate'] == 100]) - len([t for t in before_aggregates.values() if t['pass_rate'] == 100]):+}` |")
        
        md.append("\n---")
        
        md.append("## 🎯 Individual Test Case Evolution")
        md.append("| Test Suite | Category | Test Case | Occurrence | Before Pass Rate | After Pass Rate | Delta | Stabilization Status |")
        md.append("| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- |")

        # Gather all keys from both runs
        all_keys = set(before_aggregates.keys()).union(after_aggregates.keys())

        newly_stabilized_count = 0
        improved_count = 0
        regressed_count = 0
        stable_count = 0

        for key in sorted(all_keys):
            before_t = before_aggregates.get(key)
            after_t = after_aggregates.get(key)

            test_suite = (after_t or before_t)['test_suite']
            category = (after_t or before_t)['category']
            test_name = (after_t or before_t)['test_name']
            occurrence = (after_t or before_t)['occurrence']

            before_pr = before_t['pass_rate'] if before_t else None
            after_pr = after_t['pass_rate'] if after_t else None

            if before_pr is None:
                delta_str = "New"
                status = "🟢 New Test (100% Stable)" if after_pr == 100 else "🟡 New Test (Flaky)"
            elif after_pr is None:
                delta_str = "Removed"
                status = "⚪ Test Removed"
            else:
                delta = after_pr - before_pr
                delta_str = f"{'+' if delta >= 0 else ''}{delta:.1f}%"
                
                if before_pr < 100 and after_pr == 100:
                    status = "🏆 **Stabilized!**"
                    newly_stabilized_count += 1
                elif before_pr < after_pr:
                    status = "📈 Improved"
                    improved_count += 1
                elif before_pr > after_pr:
                    status = "🚨 **Regressed!**"
                    regressed_count += 1
                elif before_pr == 100:
                    status = "🟢 Stable"
                    stable_count += 1
                else:
                    status = "🟡 Unstable"

            before_pr_str = f"{before_pr}%" if before_pr is not None else "-"
            after_pr_str = f"{after_pr}%" if after_pr is not None else "-"

            md.append(f"| `{test_suite}` | `{category}` | `{test_name}` | `{occurrence}` | {before_pr_str} | {after_pr_str} | **{delta_str}** | {status} |")

        md.append("\n---")
        
        md.append("### Stabilization Achievements Checklist")
        md.append(f"- `[x]` Overall system stability change: **{'+' if net_delta >= 0 else ''}{net_delta:.2f}%**")
        md.append(f"- `[x]` Newly stabilized test cases: **{newly_stabilized_count}**")
        md.append(f"- `[x]` Improved test cases: **{improved_count}**")
        md.append(f"- `[x]` Regressed test cases: **{regressed_count}**")

        return "\n".join(md)

    def save_report(self, aggregates):
        """Saves both the metrics JSON and the Markdown report."""
        clean_target = self.get_clean_target()
        
        # 1. Save aggregated metrics to JSON (used for comparison later)
        metrics_filename = f"metrics_{clean_target}_{self.phase}.json"
        metrics_filepath = os.path.join(self.output_dir, metrics_filename)
        with open(metrics_filepath, 'w') as f:
            json.dump(aggregates, f, indent=2)
        print(f"Saved serialized metrics to: {metrics_filepath}")

        # 2. Save markdown report
        report_filename = f"flakiness_report_{self.phase}_{clean_target}.md"
        report_filepath = os.path.join(self.output_dir, report_filename)
        report_md = self.generate_report_markdown(aggregates)
        with open(report_filepath, 'w') as f:
            f.write(report_md)
        print(f"Generated stability report: {report_filepath}")

        # 3. Check for 'before' metrics to auto-generate comparison report
        if self.phase == 'after':
            before_filename = f"metrics_{clean_target}_before.json"
            before_filepath = os.path.join(self.output_dir, before_filename)
            if os.path.exists(before_filepath):
                print(f"Found previous 'before' phase metrics. Generating comparative report...")
                with open(before_filepath, 'r') as f:
                    before_aggregates = json.load(f)
                
                comp_md = self.generate_comparison_markdown(before_aggregates, aggregates)
                comp_filename = f"stability_comparison_{clean_target}.md"
                comp_filepath = os.path.join(self.output_dir, comp_filename)
                with open(comp_filepath, 'w') as f:
                    f.write(comp_md)
                print(f"Generated comparative stability report: {comp_filepath}")
            else:
                print(f"Info: Previous 'before' metrics not found at {before_filepath}. Skipped generating comparison report.")
