# Mantis Diagnostics & Triage Agent (Stage 3) 🦗👁️

The **Diagnostics & Triage Agent (Diagnose)** component is the chronological third stage of Project Mantis. It is an AI-powered diagnostic agent that investigates failed sequencer executions by correlating distributed log streams within padded test execution windows, mining git history for regression code diffs, and evaluating codebase logic using Gemini registered tools.

It is executed via the wrapper launcher:
```bash
export GEMINI_API_KEY="your_api_key"
mantis/bin/diagnose --target //mqtt/localhost --run-dir mantis/out/test_bundles/<target_folder>/run_1/
```

All diagnostic results and triage summary reports are saved self-containedly under `mantis/out/diagnose/`:
- **Detailed analysis report per test case**: `mantis/out/diagnose/<project_id>/<site_id>/<device_id>/<test_id>/triage_analysis.md`
- **Consolidated project/site triage summary**: `mantis/out/diagnose/<project_id>/<site_id>/triage_summary_report.md`

## Submodule Files
- `__init__.py`: Package module marker.
- `main.py`: Scans run outcomes, extracts padded execution timebounds, slices pubber/udmis logs, compiles context catalogs, and generates comparative summaries with relative links.
- `agent.py`: Connects to Gemini API (`gemini-2.5-pro`) and configures tool bindings.
- `tools.py`: Implements code search `grep_codebase`, file reader `read_file_lines`, and safe read-only `git_read_operations` (with security guardrails).
