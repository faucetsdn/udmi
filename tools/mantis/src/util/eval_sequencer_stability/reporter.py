#!/usr/bin/env python3
import json
import os
from datetime import datetime


class MantisReporter:
    def __init__(self, target, output_dir):
        self.target = target
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)

    def get_clean_target(self):
        return self.target.replace("/", "_").replace("+", "_").strip("_")

    def generate_report_markdown(self, aggregates, timestamp, run_dir_name):
        """Generates the standard flakiness report for a single bundles directory."""
        total_tests = len(aggregates)
        flaky_tests = [t for t in aggregates.values() if t['flaky']]
        failed_tests = [t for t in aggregates.values() if t['pass_rate'] == 0]
        passed_tests = [t for t in aggregates.values() if t['pass_rate'] == 100]

        # Overall stability percentage (average of all test case pass rates)
        avg_stability = sum(t['pass_rate'] for t in
                            aggregates.values()) / total_tests if total_tests > 0 else 0.0

        md = []
        md.append(f"# Mantis Stability & Flakiness Report")
        md.append(f"**Target System**: `{self.target}`  ")
        md.append(f"**Source Bundle Directory**: `{run_dir_name}`  ")
        md.append(
            f"**Evaluation Time**: `{timestamp.strftime('%Y-%m-%d %H:%M:%S UTC')}`  ")
        md.append(
            f"**Total Test Runs Evaluated**: `{next(iter(aggregates.values()))['total_runs'] if aggregates else 0}`")
        md.append("\n---")

        md.append("## Executive Summary")
        md.append("| Metric | Value |")
        md.append("| :--- | :--- |")
        md.append(f"| **Total Test Cases Checked** | `{total_tests}` |")
        md.append(
            f"| **Perfectly Stable Tests (100% Success)** | `{len(passed_tests)}` |")
        md.append(
            f"| **Flaky Tests (0% < Success < 100%)** | `{len(flaky_tests)}` |")
        md.append(
            f"| **Consistently Failing Tests (0% Success)** | `{len(failed_tests)}` |")
        md.append(
            f"| **Overall System Stability Score** | `**{avg_stability:.2f}%**` |")

        md.append("\n---")

        if flaky_tests:
            md.append("## ⚠️ Detected Flaky Tests (Prioritized Hunt List)")
            md.append(
                "> These tests show unstable behavior and fail randomly. Focus your stabilization efforts here!")
            md.append(
                "\n| Test Suite | Category | Test Case | Expected Outcome | Pass Rate | Raw (Pass / Fail / Skip) |")
            md.append("| :--- | :--- | :--- | :--- | :--- | :--- |")
            for t in sorted(flaky_tests, key=lambda x: x['pass_rate']):
                md.append(
                    f"| `{t['test_suite']}` | `{t['category']}` | `{t['test_name']}` | `{t['expected_outcome']}` | **{t['pass_rate']}%** | {t['raw_pass']} / {t['raw_fail']} / {t['raw_skip']} |")
        else:
            md.append("## 🎉 No Flaky Tests Detected!")
            md.append(
                "> Excellent! All executed tests behaved 100% consistently across all runs.")

        md.append("\n---")

        if failed_tests:
            md.append("## ❌ Consistently Failing Tests")
            md.append(
                "> These tests failed to match their expected outcome in 100% of the runs.")
            md.append(
                "\n| Test Suite | Category | Test Case | Expected Outcome | Pass Rate | Raw (Pass / Fail / Skip) |")
            md.append("| :--- | :--- | :--- | :--- | :--- | :--- |")
            for t in sorted(failed_tests,
                            key=lambda x: (x['test_suite'], x['category'],
                                           x['test_name'])):
                md.append(
                    f"| `{t['test_suite']}` | `{t['category']}` | `{t['test_name']}` | `{t['expected_outcome']}` | **{t['pass_rate']}%** | {t['raw_pass']} / {t['raw_fail']} / {t['raw_skip']} |")
            md.append("\n---")

        md.append("## 📋 Complete Test Case Results")
        md.append(
            "| Test Suite | Category | Test Case | Occurrence | Expected Outcome | Pass Rate | Status |")
        md.append("| :--- | :--- | :--- | :---: | :---: | :--- | :--- |")

        for t in sorted(aggregates.values(),
                        key=lambda x: (x['test_suite'], x['category'],
                                       x['test_name'], x['occurrence'])):
            status_str = "🟢 Stable" if t['pass_rate'] == 100 else (
                "🟡 Flaky" if t['flaky'] else "🔴 Consistent Fail")
            md.append(
                f"| `{t['test_suite']}` | `{t['category']}` | `{t['test_name']}` | `{t['occurrence']}` | `{t['expected_outcome']}` | **{t['pass_rate']}%** | {status_str} |")

        return "\n".join(md)

    def generate_iterative_comparison_markdown(self, datasets):
        """Generates a multi-run chronological comparative report.
        
        datasets is a list of dicts:
        [
           {
             'name': 'before_mqtt_localhost',
             'timestamp': datetime_obj,
             'aggregates': aggregates_dict
           },
           ...
        ]
        sorted chronologically.
        """
        # Ensure sorted chronologically
        datasets = sorted(datasets, key=lambda x: x['timestamp'])

        md = []
        md.append(f"# Mantis Multi-Checkpoint Stability Evolution Report")
        md.append(f"**Target System**: `{self.target}`  ")
        md.append(
            f"**Comparison Generated At**: `{datetime.now().strftime('%Y-%m-%d %H:%M:%S UTC')}`  ")
        md.append("\n---")

        md.append("## Stabilization Timeline Summary")

        # Headers
        header_cols = ["Metric"]
        sep_cols = [":---"]
        for d in datasets:
            display_name = f"{d['name']} ({d['timestamp'].strftime('%m/%d %H:%M')})"
            header_cols.append(display_name)
            sep_cols.append(":---:")
        header_cols.append("Total Progress Delta")
        sep_cols.append(":---:")

        md.append("| " + " | ".join(header_cols) + " |")
        md.append("| " + " | ".join(sep_cols) + " |")

        # Calculate overall score for each checkpoint
        stability_scores = []
        flaky_counts = []
        stable_counts = []

        for d in datasets:
            agg = d['aggregates']
            total = len(agg)
            if total > 0:
                score = sum(t['pass_rate'] for t in agg.values()) / total
                flaky = len([t for t in agg.values() if t['flaky']])
                stable = len([t for t in agg.values() if t['pass_rate'] == 100])
            else:
                score, flaky, stable = 0.0, 0, 0

            stability_scores.append(score)
            flaky_counts.append(flaky)
            stable_counts.append(stable)

        # Score Row
        score_row = ["**Overall System Stability**"]
        for s in stability_scores:
            score_row.append(f"{s:.2f}%")
        net_score_delta = stability_scores[-1] - stability_scores[0]
        score_row.append(f"**{net_score_delta:+.2f}%**")
        md.append("| " + " | ".join(score_row) + " |")

        # Flaky Row
        flaky_row = ["**Flaky Test Cases**"]
        for f in flaky_counts:
            flaky_row.append(f"`{f}`")
        net_flaky_delta = flaky_counts[-1] - flaky_counts[0]
        flaky_row.append(f"`{net_flaky_delta:+}`")
        md.append("| " + " | ".join(flaky_row) + " |")

        # Stable Row
        stable_row = ["**Perfectly Stable Test Cases**"]
        for s in stable_counts:
            stable_row.append(f"`{s}`")
        net_stable_delta = stable_counts[-1] - stable_counts[0]
        stable_row.append(f"`{net_stable_delta:+}`")
        md.append("| " + " | ".join(stable_row) + " |")

        md.append("\n---")

        md.append("## 🎯 Test Case Evolution Tracking")

        # Get union of all test case keys across all checkpoints
        all_keys = set()
        for d in datasets:
            all_keys.update(d['aggregates'].keys())

        # Table headers for test evolution
        test_headers = ["Test Case", "Expected"]
        test_seps = [":---", ":---:"]
        for d in datasets:
            test_headers.append(d['name'])
            test_seps.append(":---:")
        test_headers.extend(["Net Delta", "Stabilization Status"])
        test_seps.extend([":---:", ":---"])

        md.append("| " + " | ".join(test_headers) + " |")
        md.append("| " + " | ".join(test_seps) + " |")

        newly_stabilized = 0
        improved = 0
        regressed = 0
        stable = 0

        for key in sorted(all_keys):
            # Extract test info
            first_val = None
            last_val = None

            # Traverse chronologically to find first and last presence
            for d in datasets:
                if key in d['aggregates']:
                    if first_val is None:
                        first_val = d['aggregates'][key]
                    last_val = d['aggregates'][key]

            test_suite = (last_val or first_val)['test_suite']
            category = (last_val or first_val)['category']
            test_name = (last_val or first_val)['test_name']
            expected = (last_val or first_val)['expected_outcome']

            row_cols = [f"`{test_suite}/{category}/{test_name}`",
                        f"`{expected}`"]

            pass_rates = []
            for d in datasets:
                if key in d['aggregates']:
                    pass_rates.append(d['aggregates'][key]['pass_rate'])
                else:
                    pass_rates.append(None)

            for pr in pass_rates:
                if pr is None:
                    row_cols.append("-")
                else:
                    row_cols.append(f"{pr}%")

            # Calculate progress delta between first non-None and last non-None pass rate
            valid_prs = [pr for pr in pass_rates if pr is not None]
            if len(valid_prs) >= 2:
                delta = valid_prs[-1] - valid_prs[0]
                delta_str = f"{delta:+.1f}%"

                first_pr = valid_prs[0]
                last_pr = valid_prs[-1]

                if first_pr < 100 and last_pr == 100:
                    status = "**Stabilized!**"
                    newly_stabilized += 1
                elif first_pr < last_pr:
                    status = "Improved"
                    improved += 1
                elif first_pr > last_pr:
                    status = "**Regressed!**"
                    regressed += 1
                elif first_pr == 100:
                    status = "Stable"
                    stable += 1
                else:
                    status = "Unstable"
            else:
                delta_str = "-"
                status = "New/Removed"

            row_cols.extend([f"**{delta_str}**", status])
            md.append("| " + " | ".join(row_cols) + " |")

        md.append("\n---")
        md.append("### Stabilization Achievements Checklist")
        md.append(
            f"- `[x]` Overall stability change (First ➔ Last Checkpoint): **{net_score_delta:+.2f}%**")
        md.append(
            f"- `[x]` Newly stabilized test cases: **{newly_stabilized}**")
        md.append(f"- `[x]` Improved test cases: **{improved}**")
        md.append(f"- `[x]` Regressed test cases: **{regressed}**")

        return "\n".join(md)

    def save_single_report(self, aggregates, timestamp, run_dir_name, output_dir=None):
        """Saves the single run flakiness report."""
        clean_target = self.get_clean_target()
        out_dir = output_dir if output_dir is not None else self.output_dir

        # 1. Save aggregated metrics to JSON
        metrics_filename = f"metrics_{clean_target}_{timestamp.strftime('%Y%m%d_%H%M%S')}.json"
        metrics_filepath = os.path.join(out_dir, metrics_filename)
        with open(metrics_filepath, 'w') as f:
            json.dump(aggregates, f, indent=2)
        print(f"Saved metrics JSON to: {metrics_filepath}")

        # 2. Save report markdown
        report_filename = f"flakiness_report_{clean_target}_{timestamp.strftime('%Y%m%d_%H%M%S')}.md"
        report_filepath = os.path.join(out_dir, report_filename)
        report_md = self.generate_report_markdown(aggregates, timestamp,
                                                  run_dir_name)
        with open(report_filepath, 'w') as f:
            f.write(report_md)
        print(f"Generated flakiness report: {report_filepath}")
        return metrics_filepath

    def save_comparison_report(self, datasets, output_dir=None):
        """Saves the multi-run chronological comparison report."""
        clean_target = self.get_clean_target()
        out_dir = output_dir if output_dir is not None else self.output_dir

        comp_filename = f"stability_comparison_{clean_target}.md"
        comp_filepath = os.path.join(out_dir, comp_filename)

        comp_md = self.generate_iterative_comparison_markdown(datasets)
        with open(comp_filepath, 'w') as f:
            f.write(comp_md)
        print(
            f"\nGenerated comprehensive stability comparison report: {comp_filepath}")
