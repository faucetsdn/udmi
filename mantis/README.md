# Project Mantis: The Ultimate Bug Predator 🦗🎯

> **Mantis** is a ruthless, highly efficient predator of bugs—specifically designed to hunt down, capture, triage, and eliminate issues in the UDMI codebase.

Rather than just a single tool, **Mantis** is a comprehensive developer ecosystem consisting of two powerful components:

1. **Raptorial Claws (Stability & Flakiness Measurement)**: Grasps and analyzes test suite outputs over multiple runs (locally or from sharded CI artifacts) to establish exact pass/fail rates, highlighting flaky or unstable behavior.
2. **AI Triage Agent (Upcoming)**: An advanced agentic debugger that consumes logs, correlates events across components, identifies root causes, and recommends fixes for failed runs.

---

## Project Directory Structure

```
mantis/
├── README.md                 # Project Mantis Overview & Ecosystem
├── docs/
│   └── raptorial_claws_spec.md  # Technical Spec for Stability Tracker
├── bin/
│   └── measure               # Executable launcher for Raptorial Claws
└── src/
    ├── orchestrator.py       # Controls loops, local execution, & CI imports
    ├── analyzer.py           # Normalizes logs, parses outcomes, matches golden baseline
    └── reporter.py           # Formats Markdown reports and before/after deltas
```

---

## Component 1: Raptorial Claws 🦗🔍

The **Raptorial Claws** tracker allows developers to capture baseline flakiness and measure stabilization improvements.

### Intended vs. Actual Failures
In UDMI, some test cases are explicitly designed to fail (e.g. testing error validation). These are **intended failures** and are marked as `fail` in the golden files.
An **actual failure** is any unexpected outcome deviating from the baseline (measured under strict ISO timestamp normalization).

### CLI Usage

Run the tracker launcher from the repo root:

```bash
mantis/bin/measure \
  --target <target_project> \
  [--iterations <num>] \
  [--phase <before|after>] \
  [--suite <sequencer|itemized|both>] \
  [--tests <test_list>] \
  [--github-dir <path>] \
  [--output-dir <path>]
```

*Refer to the detailed [Mantis Raptorial Claws Spec](file:///usr/local/google/home/heykhyati/Projects/udmi_clone/udmi/mantis/docs/raptorial_claws_spec.md) for complete engineering details.*

---

## Component 2: AI Triage Agent (Vision) 🤖🛠️

Failed stability checks captured by the **Raptorial Claws** will feed directly into the **AI Triage Agent**. 
The triage agent will:
- Automatically read raw output logs (`out/pubber.log`, `out/udmis.log`, `out/sequencer.log`) from any failed iteration.
- Use advanced event-correlation heuristics (like base session transaction IDs `RC:XXXXXX`) to trace failures across distributed Pubber and UDMIS processes.
- Pinpoint the exact step where the failure occurred and identify the root cause (e.g., race condition, unexpected config sync delay).
- Propose or automatically implement corrective code changes.
