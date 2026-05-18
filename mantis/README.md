# Project Mantis: The Ultimate Bug Predator 🦗🎯

> **Mantis** is a ruthless, highly efficient predator of bugs—specifically designed to hunt down, capture, triage, and eliminate issues in the UDMI codebase.

Mantis is structured into independent, highly modular package submodules following the Unix philosophy. Their folder names are **alphabetically and chronologically arranged** to naturally communicate the execution stages:

1. **`capture` (Stage 1)** 🦗⚡: Triggers parallel workflow dispatches on GitHub Actions, or executes local isolated test loops in the sandbox, packaging raw consolidated results into standardized support packages.
2. **`grasp` (Stage 2)** 🦗🔍: A pure log and metric analyzer that ingests captured support packages, compares raw results against Golden Baselines, and computes overall stabilization reports.
3. **`inspect` (Stage 3)** 🦗👁️: An AI-powered diagnostic triage agent that correlating distributed log streams, mines git log histories, and searches codebases to isolate failing breakpoints.

---

## Project Directory Structure

```
mantis/
├── README.md                 # Project Mantis Overview & Ecosystem
├── docs/
│   ├── grasp_spec.md         # Spec for grasp (Pure Log & Metric Analyzer)
│   └── inspect_spec.md       # Spec for inspect (AI Triage Agent)
├── bin/                      # Executable Bash wrappers
│   ├── capture               # Launches mantis.capture
│   ├── grasp                 # Launches mantis.grasp
│   └── inspect               # Launches mantis.inspect
├── capture/                  # Submodule 1: Trigger & Run Loop (Stage 1)
│   ├── __init__.py
│   ├── README.md
│   └── main.py               # Triggers runs (Local loop or GitHub CI) & bundles outputs
├── grasp/                    # Submodule 2: Grasp Metric Analyzer (Stage 2)
│   ├── __init__.py
│   ├── README.md
│   ├── main.py               # Pure log aggregator & consolidated metrics
│   ├── analyzer.py           # Parses results under normalizations
│   └── reporter.py           # Formats Markdown reports and comparative deltas
└── inspect/                  # Submodule 3: Inspect AI Triage Agent (Stage 3)
    ├── __init__.py
    ├── README.md
    ├── main.py               # Triage coordinator & context aggregator
    ├── agent.py              # AI harness and Gemini API client (google.genai)
    └── tools.py              # Exposes grep_codebase & git_read_operations to the agent
```

---

## 1. Capture Component (`capture`) 🦗⚡

Triggers parallel runs on GitHub CI or executes local sandbox loops, packaging raw outputs into standard zip bundles.

```bash
# GitHub CI trigger (concurrency-safe default: 3 iterations, runs in background)
export GITHUB_TOKEN="your_github_personal_access_token"
mantis/bin/capture --target //mqtt/localhost

# Local Sandbox loops trigger (default: 10 iterations)
mantis/bin/capture --local --target //mqtt/localhost
```

### Options:
- `--target`: Target project spec (default: `//mqtt/localhost`).
- `--local`: Switch from GitHub dispatches to **Local sandbox loop runs**.
- `--iterations`: Number of parallel CI runs (default: `3`) or local loop iterations (default: `10`).
- `--verbose`: Runs execution in the foreground, displaying polling progress live.
- `--suite`: Test suites to execute locally: `sequencer`, `itemized`, or `both` (default: `both`).
- `--tests`: Selective sequencer tests to run locally (e.g. `valid_serial_no`).
- `--output-dir`: Custom folder to save downloaded bundles.

---

## 2. Grasp Component (`grasp`) 🦗🔍

Pure log and metric analyzer calculating stability indices and comparative deltas.

```bash
mantis/bin/grasp \
  --target <target_project> \
  --phase <before|after> \
  --bundles-dir <path_to_captured_bundles>
```

### Options:
- `--bundles-dir`: Folder containing captured zip/tgz bundles. **Required**.
- `--target`: Target project string (default: `//mqtt/localhost`).
- `--phase`: Stabilization exercise stage (`before` or `after`). Default: `before`.
- `--output-dir`: Output folder for raw runs and final reports (default: `out/mantis`).

---

## 3. Inspect Component (`inspect`) 🦗👁️

 AI-powered diagnostic triage agent correlating streams using execution timebounds and registered tools.

```bash
export GEMINI_API_KEY="your_gemini_api_key"

mantis/bin/inspect \
  --target <target_project> \
  --run-dir <path_to_iteration_backup> \
  --test <test_name>
```

*Refer to the detailed [Mantis Inspect Spec](file:///usr/local/google/home/heykhyati/Projects/udmi_clone/udmi/mantis/docs/inspect_spec.md) for complete details.*
