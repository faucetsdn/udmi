# Structured Triage JSON Integration Specification

To enable automated remediation engines, deployment rollbacks, and ticketing systems (e.g., Jira, GitHub Issues) to parse Mantis diagnostic outcomes programmatically, Mantis outputs a structured `triage_analysis.json` file in the output directory.

---

## 1. JSON Report Schema

The JSON output conforms strictly to the following schema format:

```json
{
  "target_id": "test_failure_run_481a",
  "timestamp": "2026-06-12T11:40:00Z",
  "status": "SUCCESS",
  "verdict": "VERIFIED_DEFECT",
  "summary": "Port binding collision on pubber startup.",
  "hypotheses_evaluated": [
    {
      "title": "Broker server shutdown",
      "status": "DISPROVED",
      "evidence": "MQTT localhost broker logs show active listen threads on startup."
    },
    {
      "title": "Port collision",
      "status": "VERIFIED",
      "evidence": "pubber.log displays Address already in use error on socket bind."
    }
  ],
  "root_cause_analysis": {
    "culprit_file": "pubber/src/main/java/pubber/Pubber.java",
    "culprit_line_range": "L145-L155",
    "explanation": "Socket connection does not cleanly release port on system interrupts."
  }
}
```

---

## 2. Field Definitions

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `target_id` | `string` | Unique identifier matching the analyzed run. |
| `timestamp` | `string` | ISO 8601 UTC compilation timestamp. |
| `status` | `string` | Triage state: `SUCCESS`, `PARTIAL_FAIL_OPEN` (rate limit timeout hit), or `FAILED` (no logs found). |
| `verdict` | `string` | Final verdict: `VERIFIED_DEFECT`, `FLAKY_ENVIRONMENT`, or `UNKNOWN`. |
| `summary` | `string` | Dense root-cause diagnostic synthesis summary. |
| `hypotheses_evaluated` | `array[object]` | List of hypotheses tested containing `title`, `status`, and `evidence`. |
| `root_cause_analysis` | `object` | Root cause details containing `culprit_file`, `culprit_line_range`, and `explanation`. |

---

## 3. Automation Hook Integration Example (Python)

Integration bots can load and query outcomes directly:

```python
import json
from pathlib import Path

def process_triage_result(output_dir: str):
    json_path = Path(output_dir) / "triage_analysis.json"
    if not json_path.exists():
        return

    report = json.loads(json_path.read_text(encoding="utf-8"))
    
    # If the triage failed or hit rate timeouts, route to manual review
    if report["status"] != "SUCCESS":
        route_to_human_oncall(report)
        return

    # Automatically file bug on detected defect
    if report["verdict"] == "VERIFIED_DEFECT" and report["root_cause_analysis"]:
        rc = report["root_cause_analysis"]
        create_ticketing_issue(
            title=f"[Mantis Triage] {report['summary']}",
            description=f"Explanation: {rc['explanation']}\n\nCulprit File: {rc['culprit_file']} ({rc['culprit_line_range']})"
        )
        return
```
