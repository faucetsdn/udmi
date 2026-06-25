# Mantis Diagnostics & Triage Agent (Stage 3) 🦗👁️

The **Diagnostics & Triage Agent (Diagnose)** component is the third stage of Project Mantis. It is an AI-powered diagnostic agent that investigates failed sequencer executions by correlating distributed log streams within padded test execution windows, mining git history, and evaluating codebase logic using playbook pipelines and GenAI skills.

It is executed via the launcher:

```bash
export GEMINI_API_KEY="your_gemini_api_key"

# Run triage in Manifest Mode
mantis/bin/diagnose -m out/mantis/<target_folder>/triage_manifest.json
```

All diagnostic results, stage timeline outputs, and triage summary reports are saved directly under the active test bundle folder:

* **Failure Triage Analysis Report**:
  `<active_bundle_dir>/diagnose/<project_id>/<site_id>/<device_id>/<test_id>/triage_analysis.md`
* **Consolidated Triage Summary**:
  `<active_bundle_dir>/diagnose/<project_id>/<site_id>/triage_summary_report.md`
* **Triage Persistent Log**:
  `<active_bundle_dir>/diagnose.log`

## Submodule Layout

* `main.py`: Main CLI launcher wrapper. Activates Manifest Mode or direct log mode.
* `cli.py`: Declares argument parser options and short flags (e.g., `-m`, `-i`, `-t`, `-c`).
* `runner.py`: Coordinates the sharded runs failures, invokes log resolvers, slices global logs by execution timebounds, and generates site reports.
* `agent.py`: Connects to Gemini API, initiates semantic caching, and runs playbooks sequentially (Timeline, Intent, Analysis, Critique).
* `resolver.py`: Path resolver utility to trace local/alternate sharded console logs.
* `tools.py`: Implements codebase searching (`grep_codebase`), file reader (`read_file_lines`), and safe read-only `git_read_operations`.
* `playbook.yaml`: Declarative Playbook defining stage pipelines, models, and analyst rules.
