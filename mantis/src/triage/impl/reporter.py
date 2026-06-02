import os
import re
import json
from datetime import datetime, timezone
from typing import Any, Dict, List

class UDMITriageReporter:
    """
    Consolidates diagnostic results, compiles markdown reports,
    clusters failure signatures, and formats pull request snippets.
    """

    def __init__(self, target: str, site_id: str, out_dir: str):
        self.target = target
        self.site_id = site_id
        self.out_dir = out_dir
        self.clean_target = target.replace("/", "_").replace("+", "_").strip("_")

    def compile_summary_report(self, triage_summaries: List[Dict[str, Any]]) -> str:
        """Generates the standard consolidated Mantis AI Triage Summary Report."""
        total_checked = len(triage_summaries)
        insufficient_count = len([s for s in triage_summaries if s.get('insufficient', False)])
        diagnosed_count = total_checked - insufficient_count
        
        resolution_rate = int((diagnosed_count / total_checked) * 100) if total_checked else 0
        abort_rate = int((insufficient_count / total_checked) * 100) if total_checked else 0

        md = []
        md.append("# Mantis AI Diagnostics: Triage Summary Report\n")
        
        # Metadata Table
        md.append("| Execution Metadata | Value |")
        md.append("| :--- | :--- |")
        md.append(f"| **Target Project** | `{self.target}` |")
        md.append(f"| **Site ID** | `{self.site_id}` |")
        md.append(f"| **Triage Timestamp** | `{datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}` |")
        md.append("")

        # Premium Dashboard Card
        md.append("> [!NOTE]")
        md.append("> ### Diagnostic Performance Metrics")
        md.append(f"> * **Total Failures Evaluated:** `{total_checked}`")
        md.append(f"> * **Diagnostic Resolution Rate:** `{resolution_rate}%` ({diagnosed_count} successfully isolated)")
        md.append(f"> * **Data Limitation Abort Rate:** `{abort_rate}%` ({insufficient_count} incomplete cases)")
        md.append("")

        # Failures Breakdown Table
        md.append("## Failed Test Diagnostics Breakdown")
        md.append("| Test Case | Suite | Category | Breakpoint & Root Cause Isolation Summary | Link to Analysis |")
        md.append("| :--- | :--- | :--- | :--- | :--- |")
        for s in triage_summaries:
            md.append(
                f"| `{s['test_id']}` | `{s['suite']}` | `{s['category']}` | {s['breakpoint']} | [View Analysis]({s['report_link']}) |"
            )

        md.append("\n---")

        # Clustering of signatures
        md.append("## Failure Signature Clustering")
        md.append("Failure clusters group individual regressions sharing similar root-cause patterns or breakpoint profiles.\n")

        clusters = {}
        for s in triage_summaries:
            sig = "General Unclassified Regression Signature"
            bp_lower = s['breakpoint'].lower()
            if "timed out" in bp_lower or "timeout" in bp_lower:
                sig = "Sync Wait Timeout (Component latency / missing acknowledgments)"
            elif "schema" in bp_lower or "validation failed" in bp_lower:
                sig = "Telemetry Schema Violation (Malformed JSON payload envelope)"
            elif "insufficient" in bp_lower or s.get('insufficient', False):
                sig = "Missing Log Streams (Context insufficient for triage)"

            if sig not in clusters:
                clusters[sig] = []
            clusters[sig].append(s)

        for sig, tests in clusters.items():
            md.append(f"### {sig} (Affecting {len(tests)} Tests)")
            for t in tests:
                md.append(f"- `{t['test_id']}` ([Triage Details]({t['report_link']}))")

        md.append("\n---")

        # PR Comment Snippet
        md.append("## Pull Request Comment Snippet")
        md.append("```markdown")
        md.append(f"### Mantis AI Debugger isolated {total_checked} regressions in this test run:")
        for s in triage_summaries:
            status_indicator = "⚠️" if s.get('insufficient', False) else "❌"
            md.append(f"- {status_indicator} **{s['test_id']}**: {s['breakpoint']}")
        md.append("```")

        return "\n".join(md)

    def save_report(self, triage_summaries: List[Dict[str, Any]]) -> str:
        """Compiles and persistently writes the summary report to the out dir."""
        report_content = self.compile_summary_report(triage_summaries)
        
        root_report_path = os.path.join(
            self.out_dir, 
            "diagnose", 
            self.clean_target,
            self.site_id, 
            "triage_summary_report.md"
        )
        os.makedirs(os.path.dirname(root_report_path), exist_ok=True)
        
        with open(root_report_path, 'w', encoding='utf-8') as fs:
            fs.write(report_content)
            
        return root_report_path
