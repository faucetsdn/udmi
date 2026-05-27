# Project Mantis: The Ultimate Bug Predator 🦗🎯

> **Mantis** is a ruthless, highly efficient predator of bugs—specifically designed to hunt down, capture, triage, and eliminate issues in the UDMI codebase.

Mantis is structured into independent, highly modular package submodules following the Unix philosophy. Their folder names are **alphabetically arranged** to naturally communicate the execution stages:

1. **`collect_stats` (Stage 1)** 🦗⚡: Triggers new workflow dispatches on GitHub Actions, pulls historical completed runs from a branch, or executes local sandbox loops, packaging outcomes into standard support packages.
2. **`evaluate_stability` (Stage 2)** 🦗🔍: A pure log and metric analyzer that ingests captured support packages, compares raw results against Golden Baselines, and computes overall stabilization reports.
3. **`diagnose` (Stage 3)** 🦗👁️: An AI-powered diagnostic triage agent that correlating distributed log streams, mines git log histories, and searches codebases to isolate failing breakpoints.

---

## Project Directory Structure

All output files, raw bundles, and triage reports are written self-containedly under **`mantis/out/`** for absolute modularity.

```
mantis/
├── README.md                 # Project Mantis Overview & Ecosystem
├── docs/
│   ├── evaluate_stability_spec.md  # Spec for evaluate_stability
│   └── diagnose_spec.md      # Spec for diagnose (AI Triage Agent)
├── bin/                      # Executable Bash wrappers
│   ├── collect_stats         # Launches mantis.collect_stats
│   ├── evaluate_stability    # Launches mantis.evaluate_stability
│   └── diagnose              # Launches mantis.diagnose
├── collect_stats/            # Submodule 1: Trigger & Run Loop (Stage 1)
│   ├── __init__.py
│   ├── README.md
│   └── main.py               # Collects runs (Local, CI dispatch, or CI historical search)
├── evaluate_stability/       # Submodule 2: Stability Evaluator (Stage 2)
│   ├── __init__.py
│   ├── README.md
│   ├── main.py               # Stability evaluator orchestrator
│   ├── analyzer.py           # Parses results under normalizations
│   └── reporter.py           # Formats Markdown reports and comparative deltas
└── diagnose/                 # Submodule 3: Triage Agent (Stage 3)
    ├── __init__.py
    ├── README.md
    ├── main.py               # Triage coordinator & context aggregator
    ├── agent.py              # AI harness and Gemini API client
    └── tools.py              # Exposes codebase and git tools to the agent
```

---

## 1. Statistics Collector (`collect_stats`) 🦗⚡

Triggers runs on GitHub Actions CI, searches/downloads historical completed CI runs from a branch, or executes local sandbox loops, packaging outcomes into standard support bundles.

```bash
# GitHub CI trigger (concurrency-safe default: 3 iterations, runs in background)
export GITHUB_TOKEN="your_github_personal_access_token"
mantis/bin/collect_stats --target //mqtt/localhost

# GitHub CI Historical Search (download the last 5 completed runs on main branch)
mantis/bin/collect_stats --github-search --branch main --limit 5 --target //mqtt/localhost

# Local Sandbox loops trigger (default: 10 iterations)
mantis/bin/collect_stats --local --target //mqtt/localhost
```

### Options:
- `--target`: Target project spec (default: `//mqtt/localhost`).
- `--local`: Switch from GitHub dispatches to **Local sandbox loop runs**.
- `--iterations`: Number of parallel CI runs (default: `3`) or local loop iterations (default: `10`).
- `--verbose`: Runs local execution in the foreground, displaying logs live.
- `--suite`: Test suites to execute locally: `sequencer`, `itemized`, or `both` (default: `both`).
- `--tests`: Selective sequencer tests to run locally (e.g. `valid_serial_no`).
- `--github-search`: Scan and download completed past CI workflow runs instead of dispatching new ones.
- `--branch`: Git branch to search completed CI runs (default: active branch).
- `--limit`: Maximum number of completed CI runs to download (default: `10`).
- `--output-dir`: Custom folder to save downloaded bundles (default: `mantis/out/test_bundles/`).

---

## 2. Stability Evaluator (`evaluate_stability`) 🦗🔍

Pure log and metric analyzer calculating stability indices and comparative deltas.

```bash
mantis/bin/evaluate_stability \
  --target <target_project> \
  --phase <before|after> \
  --bundles-dir mantis/out/test_bundles/<target_folder>/
```

### Options:
- `--bundles-dir`: Folder containing captured tgz/zip support bundles. **Required**.
- `--target`: Target project string (default: `//mqtt/localhost`).
- `--phase`: Stabilization exercise stage (`before` or `after`). Default: `before`.
- `--output-dir`: Output folder for aggregated results and reports (default: `mantis/out/`).

---

## 3. Diagnostics & Triage Agent (`diagnose`) 🦗👁️

AI-powered diagnostic triage agent correlating streams using execution timebounds and git history.

```bash
export GEMINI_API_KEY="your_gemini_api_key"

mantis/bin/diagnose \
  --target <target_project> \
  --run-dir mantis/out/test_bundles/<target_folder>/run_1/
```

### Reports Structure:
All triage reports are generated directly under `mantis/out/diagnose/`:
- **Detailed triage report per test**: `mantis/out/diagnose/<project_id>/<site_id>/<device_id>/<test_id>/triage_analysis.md`
- **Consolidated site triage summary**: `mantis/out/diagnose/<project_id>/<site_id>/triage_summary_report.md`
