# Project Mantis: The Ultimate Bug Predator 🦗🎯

> **Mantis** is a ruthless, highly efficient predator of bugs—specifically designed to hunt down, capture, triage, and eliminate issues in the UDMI codebase.

Mantis is structured into independent, highly modular components following the Unix philosophy:

1. **GitHub Hunter (`hunter`)**: An automated utility to trigger parallel workflow dispatches on GitHub CI, poll their status live, and download the resulting consolidated support packages.
2. **Raptorial Claws (`measure`)**: A stability and flakiness metric calculator that parses test runs (either direct local runs or sharded CI packages inside an imported test bundles folder) and generates comparative MD reports.
3. **Oculus of Triage (`triage`)**: An AI-powered diagnostic triage agent that correlates distributed logs, analyzes failures against past successful reference runs, and isolates breakpoints with precise evidence.

---

## Project Directory Structure

```
mantis/
├── README.md                 # Project Mantis Overview & Ecosystem
├── docs/
│   ├── raptorial_claws_spec.md  # Technical Spec for Claws of Stability (Tracker)
│   └── oculus_spec.md        # Technical Spec for Oculus of Triage (AI Diagnostic)
├── bin/
│   ├── hunter                # Standalone GitHub Runner launcher
│   ├── measure               # Executable launcher for Raptorial Claws
│   └── triage                # Executable launcher for Oculus (AI Diagnostic)
└── src/
    ├── github_hunter.py      # Trigger, poll, and download CI test bundles
    ├── orchestrator.py       # Handles loops, local execution, & bundle imports
    ├── analyzer.py           # Parses outputs under strict normalizations
    ├── reporter.py           # Formats Markdown reports and before/after deltas
    └── triage.py             # Correlates logs and triggers Gemini diagnostics
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
  [--verbose]
```

*Refer to the detailed [Mantis Raptorial Claws Spec](file:///usr/local/google/home/heykhyati/Projects/udmi_clone/udmi/mantis/docs/raptorial_claws_spec.md) for complete usage details.*

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

*Refer to the detailed [Mantis Raptorial Claws Spec](file:///usr/local/google/home/heykhyati/Projects/udmi_clone/udmi/mantis/docs/raptorial_claws_spec.md) for complete measurement details.*

---

## 3. Oculus of Triage (`triage`) 🦗👁️

When a test failure is detected, **Oculus** automatically correlates sharded and global log streams using Session Base Transaction IDs (`RC:XXXXXX`) and execution timestamps to pinpoint exactly where the UDMI configuration-state loop was broken.

```bash
export GEMINI_API_KEY="your_gemini_api_key"

mantis/bin/triage \
  --run-dir <path_to_iteration_backup> \
  --test <test_name>
```

### Key Diagnostics Answered:
- **Did the Device (Pubber) work as expected?** (Verifies config reception, slow writebacks, state publications).
- **Did the Sequencer work as expected?** (Verifies validation timings, asserts, and waits).
- **Did the Backend (UDMIS) work as expected?** (Verifies reflect processing and routing envelopes).

*Refer to the detailed [Mantis Oculus Spec](file:///usr/local/google/home/heykhyati/Projects/udmi_clone/udmi/mantis/docs/oculus_spec.md) for complete prompt design, correlation heuristics, and CI reporting integrations.*
