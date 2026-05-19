# 🦗 Oculus of Triage: Mantis Diagnose Technical Spec

This document serves as the technical specification and engineering source of truth for **Mantis Diagnose** (the AI-powered diagnostic triage agent of Project Mantis, Stage 3).

---

## 1. Goal & Overview

When **Mantis Evaluate Stability** aggregates flakiness metrics and detects actual test failures, **Mantis Diagnose** is called to diagnose the issue.

It operates as a tool-equipped AI agent that dynamically discovers context and analyzes execution traces to answer three core questions:
1. **Did the Device work as expected?**
2. **Did the Sequencer work as expected?**
3. **Did the Backend (UDMIS) work as expected?**

Diagnose is built to be completely **log-agnostic** and **highly adaptive**—automatically discovering whatever data streams are available at hand, slicing them cleanly, and using git history to perform best-effort diagnostics.

---

## 2. Dynamic Log-Agnostic Correlation Engine

Diagnose does not rely on rigid, pre-defined log combinations or expect the developer to select a specific diagnostic mode. Any of the log sources (Sequencer, UDMIS, Pubber, or historical logs) might be present or completely absent depending on the target platform and logging configurations.

### 2.1. Available Context Discovery
Before starting any analysis, Diagnose automatically scans the execution backup directory and compiles a live **Available Context Catalog** (logs present vs. missing). It passes this catalog directly to the Gemini model, instructing the AI:
> *"Analyze this failure. Here is the exact set of logs currently available for this run, alongside the logs retrieved from past successful runs. Reconstruct the execution timeline using whatever combination of streams is present, perform a best-effort triage, and isolate the breakpoint with evidence."*

### 2.2. Padded Timebound-Primary Correlation
Rather than strictly filtering by transaction IDs, Diagnose uses **padded execution timebounds** as the primary log slicer:
- Finds the starting and ending timestamps of the test case from the sequencer log.
- Adds a slack padding (default: $-5$ seconds before start, $+5$ seconds after end).
- Slices all global log entries (`pubber.log`, `udmis.log`) within this window.
- This guarantees that **surrounding system context** (e.g. broker crashes, database locks) is captured and analyzed alongside transaction-specific entries.

---

## 3. Submodule Directory Structure & Naming

The Diagnose component is implemented under its own dedicated submodule folder `mantis/diagnose/`:

```
mantis/diagnose/
├── __init__.py
├── main.py               # Triage coordinator & context aggregator
├── agent.py              # AI agent harness & Gemini API client
└── tools.py              # Dynamic tool belt (grep, read lines, git operations)
```

- **`main.py`**: Orchestrates the execution flow, maps folder trees, and outputs report documents under `mantis/out/diagnose/`.
- **`agent.py`**: Manages standard `google.genai` SDK sessions and queries `gemini-2.5-pro` utilizing registered Tools.
- **`tools.py`**: Defines Python execution methods exposed directly to Gemini.

---

## 4. Agentic Codebase & Git Discovery (Tools)

Diagnose is provided with a "Tool Belt" of Python-based functions that it can call dynamically during its diagnostic analysis loop:

### 4.1. Tool Belt APIs (defined in `tools.py`):
1. **`grep_codebase(pattern)`**:
   - Executes ripgrep/grep across the repository (`validator/`, `udmis/`, `pubber/`) matching the search pattern.
2. **`read_file_lines(filepath, start_line, end_line)`**:
   - Reads a contiguous chunk of lines from a file in the workspace.
3. **`git_read_operations(repo_path, git_command, git_args)`**:
   - Executes **safe, read-only git commands** (such as `git log`, `git show`, `git diff`, `git status`) in the specified repo.
   - **Security Guardrail**: The Python harness rejects any unsafe git subcommands (such as `checkout`, `reset`, `commit`, `push`, `clean`) instantly to prevent repository corruption.

### 4.2. Dynamic Git History Mining:
Diagnose leverages the `git_read_operations` tool to perform high-value diagnostics:
- **Successful Run Retrieval**: Since UDMI sequencer output files are checked in for every run in the site model repository, Diagnose runs `git log` and `git show` on the failed test's `sequence.log` file to fetch its exact log contents during the **last successful execution**.
- **Code Regression Identification**: Diagnose compares git logs of the main `udmi` repository between the commit of the previous successful run and the current failure, pinpointing the exact code diffs/commits that might have introduced the regression.

---

## 5. Information Sufficiency Guardrails

Diagnose is designed to be highly reliable and will **never speculate or hallucinate** when logs are severely limited. 

- **The Sufficiency Rule**: If the available logs, git context, and code checks are insufficient to definitively isolate the failure breakpoint, Diagnose must explicitly declare `INSUFFICIENT_INFO`.
- **Report Requirements**: In this scenario, Diagnose will:
  1. Declare that the root cause cannot be identified with the current data.
  2. List the exact missing logs or parameters required (e.g., "Missing UDMIS reflection logs").
  3. Propose step-by-step diagnostic next steps for the developer (e.g., "Please set UDMIS logging to VERBOSE and execute the test again to capture config reflection traces.").

---

## 6. Structured Output Specifications

### 6.1. Standardized Nested Directory Tree
Diagnose saves localized reports under the self-contained `mantis/out/` hierarchy:
`mantis/out/diagnose/<project_id>/<site_id>/<device_id>/<test_id>/triage_analysis.md`

### 6.2. Structured Diagnostic Report (`triage_analysis.md`)
Every localized report must conform to the following structured markdown format:

```markdown
# Mantis Diagnose Diagnostic Analysis — <test_id>

## 1. Metadata
- **Project ID**: `<project_id>`
- **Site ID**: `<site_id>`
- **Device ID**: `<device_id>`
- **Test ID**: `<test_id>`
- **Available Context Catalog**:
  - Current Run Logs Present: `[e.g. Sequencer=True, UDMIS=True, Pubber=False]`
  - Historical Logs Present: `[e.g. Sequencer=True, UDMIS=False, Pubber=True]`
- **Base Session Transaction ID**: `<RC:XXXXXX | None>`

## 2. Breakpoint Summary
> Highlight exactly which step in the UDMI Config-State loop failed, which component caused it, and the exact time of the failure.
> If information was insufficient, display a high-visibility "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE" warning instead.

## 3. Time-Aligned Sequence of Events
| Timestamp | Component | Event / Log Message | Transaction ID | Source File / Line |
| :--- | :--- | :--- | :---: | :--- |
| 14:15:05Z | Sequencer | `NOTICE Starting test valid_serial_no` | `RC:df8a27` | `SequenceBase.java:145` |
| 14:15:06Z | UDMIS | `ReflectProcessor Processing reflection...` | `RC:df8a27.00000001` | `ReflectProcessor.java:34` |

## 4. Successful vs Failed Run Divergence
> If successful reference runs are found in git history:
> Detail the exact event/log index where the current failing run diverged from the successful history.

## 5. Code Regression Analysis
> List the commits or code diff changes made between the last successful run and the current failure that are relevant to the breakpoint component.

## 6. Root Cause & Evidence Isolation
> Present concrete evidence (traceback, log exceptions, code logic mismatches) proving the root cause of the failure.
> If insufficient info, specify the exact missing data and how the developer can enable/capture it.

## 7. Actionable Fix Recommendations
> Detailed step-by-step code or configuration adjustments to resolve the issue.
```

---

## 7. Site Triage Summary Report (`triage_summary_report.md`)

The consolidated summary report compiled at `mantis/out/diagnose/<project_id>/<site_id>/triage_summary_report.md` delivers site-level engineering value:

- **Triage Dashboard**: Overall metrics (Total Checked, Total Failed, Success Rates, Failure Clusters).
- **Relative Links:** Provides hyperlinked references using relative paths (e.g., `./<device_id>/<test_id>/triage_analysis.md`) for secure local viewing.
- **Failure Clustering**: Groups identical failure profiles together:
  ```markdown
  ### ⚠️ Cluster 1: Config Ack Timeout (Affecting 4 Tests)
  - **Root Cause**: Pubber Emulator delayed config acknowledgments exceeding sequencer wait limits.
  - **Affected Tests**:
    - `valid_serial_no` ([View Details](./AHU-1/valid_serial_no/triage_analysis.md))
    - `pointset_publish` ([View Details](./AHU-1/pointset_publish/triage_analysis.md))
  ```
- **CI/CD PR Warning Block**: A highly optimized markdown summary designed to be posted directly as a PR comment, highlighting newly introduced regressions versus known flaky tests.
