# Project Mantis: The Ultimate Bug Predator 🦗🎯

> **Mantis** is a ruthless, highly efficient predator of bugs—specifically designed to hunt down, capture, triage, and eliminate issues in the UDMI codebase.

Mantis is structured into independent, highly modular components following the Unix philosophy:

1. **GitHub Hunter (`hunter`)**: An automated utility to trigger parallel workflow dispatches on GitHub CI, poll their status live, and download the resulting consolidated support packages.
2. **Raptorial Claws (`measure`)**: A stability and flakiness metric calculator that parses test runs (either direct local runs or sharded CI packages inside an imported test bundles folder) and generates comparative MD reports.
3. **AI Triage Agent (Upcoming)**: An advanced agentic debugger to root-cause failed executions by correlating event transactions across distributed components.

---

## Project Directory Structure

```
mantis/
├── README.md                 # Project Mantis Overview & Ecosystem
├── docs/
│   └── raptorial_claws_spec.md  # Technical Spec for Claws of Stability
├── bin/
│   ├── hunter                # Standalone GitHub Runner launcher
│   └── measure               # Executable launcher for Raptorial Claws
└── src/
    ├── github_hunter.py      # Trigger, poll, and download CI test bundles
    ├── orchestrator.py       # Handles loops, local execution, & bundle imports
    ├── analyzer.py           # Parses outputs under strict normalizations
    └── reporter.py           # Formats Markdown tables & comparative deltas
```

---

## 1. GitHub Hunter (`hunter`) 🦗⚡

Triggers parallel runs on GitHub CI, monitors progress, and downloads completed test archives automatically.

```bash
export GITHUB_TOKEN="your_github_personal_access_token"

mantis/bin/hunter \
  [--target <target_project>] \
  [--iterations <num>] \
  [--output-dir <path>] \
  [--background]
```

### Options:
- `--target`: The target project specification passed as manual dispatch input (default: `//mqtt/localhost`).
- `--iterations`: Number of parallel workflows to run on GitHub (default: `10`).
- `--output-dir`: Custom folder to save downloaded bundles. If not specified, a unique, non-overlapping timestamped directory is automatically generated:  
  `out/mantis/test_bundles/<target_clean>_%Y%m%d_%H%M%S/`
- `--background`: Launches the hunter in the background using `nohup` with unbuffered logs redirected to `hunter.log` inside the bundle directory. Use this option to safely close your terminal window or prevent SSH disconnections during 30-40 minute runs.

---

## 2. Raptorial Claws Tracker (`measure`) 🦗🔍

Calculates stability indices and compares flakiness before and after code modifications.

```bash
mantis/bin/measure \
  --target <target_project> \
  [--iterations <num>] \
  [--phase <before|after>] \
  [--suite <sequencer|itemized|both>] \
  [--tests <test_list>] \
  [--bundles-dir <path>] \
  [--output-dir <path>]
```

### Options:
- `--target`: Target project string (e.g. `//mqtt/localhost`). **Required**.
- `--iterations`: Number of local loops to execute (default: `10`). Ignored if `--bundles-dir` is specified.
- `--phase`: Stabilization exercise stage (`before` or `after`). Default: `before`.
- `--suite`: Test suites to evaluate: `sequencer`, `itemized`, or `both` (default: `both`).
- `--tests`: Comma-separated list of specific sequencer tests to execute locally (e.g. `valid_serial_no`).
- `--bundles-dir`: Folder containing the sharded zip/tgz bundles (downloaded by the hunter).
- `--output-dir`: Output folder for raw runs and final reports (default: `out/mantis`).

---

## 3. Complete Automated CI Capture Workflow

To run a 10-iteration flakiness measurement on GitHub Actions:

### Foreground (Interactive Console)
```bash
# Step 1: Set token
export GITHUB_TOKEN="ghp_yourSecureTokenHere"

# Step 2: Trigger, poll, and download
mantis/bin/hunter --iterations 10

# Step 3: Measure flakiness of the downloaded timestamped bundles
mantis/bin/measure --target //mqtt/localhost --phase before --bundles-dir out/mantis/test_bundles/mqtt_localhost_20260518_144405/
```

### Background (Safe Against Disconnections)
```bash
export GITHUB_TOKEN="ghp_yourSecureTokenHere"

# Launch in the background
mantis/bin/hunter --iterations 10 --background
```
Mantis will print:
```
=============================================================
🦗 Mantis GitHub Hunter is launching in the BACKGROUND!
=============================================================
  PID       : 123456
  Log File  : out/mantis/test_bundles/mqtt_localhost_20260518_150252/hunter.log
  Bundle Dir: out/mantis/test_bundles/mqtt_localhost_20260518_150252
=============================================================
You can safely close this terminal window or lose connection.
To monitor the progress live, run:
  tail -f out/mantis/test_bundles/mqtt_localhost_20260518_150252/hunter.log
```

*Refer to the detailed [Mantis Raptorial Claws Spec](file:///usr/local/google/home/heykhyati/Projects/udmi_clone/udmi/mantis/docs/raptorial_claws_spec.md) for complete architectural and normalization details.*
